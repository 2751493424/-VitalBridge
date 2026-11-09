package com.gemini.healthbridge.phone

import android.content.Context
import android.util.Log
import com.gemini.healthbridge.core.SelfSignedCert
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * 无线 ADB 的**设备身份**：一对 RSA 密钥 + 一张自签证书。
 *
 * ## 为什么不再用内置的 `assets` 私钥（2026-11-03 改）
 *
 * ⚠ 别在这个文件里写「目录 + 斜杠 + 星号」那种通配路径（本文档原来的写法就踩了）：
 *   **Kotlin 的块注释是可以嵌套的**，KDoc 里出现「斜杠 + 星号」会另开一层注释，
 *   结果是整个文件报 `Syntax error: Unclosed comment`，而报错行指向文件末尾 ——
 *   完全看不出是注释里那两个字符的问题。
 *
 * 那份 pem 是打包进 APK 的**明文私钥** —— 任何拿到安装包的人都能 `unzip` 出来，
 * 于是"所有安装共用同一个 ADB 身份"。对自用无所谓，但它意味着：
 * 谁装过本 App，谁就能用那个身份去连**任何一只曾授权过它的手表**。
 * 这不是"密钥可能泄露"，而是"密钥天生是公开的"。
 *
 * ⇒ 现在改成**首次使用时在本机生成**，私钥只落在 `filesDir/adb/`（应用私有目录，
 * 非 root 读不到，也不进备份：`allowBackup` 虽为 true，但这个目录不在
 * `BackupProtocol` 的清单里，跨设备恢复时会被跳过）。
 *
 * ## ⚠ 代价必须说清：升级会**换身份**
 * 手表记的是**上一次配对时那把公钥**。换了密钥之后，旧的授权不再有效 ——
 * 用户需要重新走一次「无线调试 → 使用配对码配对设备 → 填配对码」。
 * 这不是 bug，是"不再共用公开私钥"的必然结果。
 * 好在 `WatchAdb.human()` 对 `AdbAuthenticationFailedException` 已经有明确指引，
 * 用户不会看到一句"连不上"就没下文。
 *
 * ## 为什么不加密存储
 * 加一层 AES（密钥放 AndroidKeyStore）能再挡一层"能读 filesDir 的攻击者"，
 * 但能读 `filesDir` 的前提通常是 root/调试后门 —— 那个前提下 KeyStore 也不安全，
 * 多一层只是复杂。**这里选的是"不再公开"，不是"抗物理提取"**，别把注释读成后者。
 */
object AdbIdentity {

    private const val TAG = "AdbIdentity"

    private const val DIR = "adb"
    private const val KEY_FILE = "adb.key"   // PKCS#8 DER
    private const val CRT_FILE = "adb.crt"   // X.509 DER

    /**
     * 证书主体名。**固定**，不拼设备型号/序列号：
     * 这个名字会跟着公钥被写进手表的 `adb_keys`（`CN=...` 注释行），
     * 往里塞设备信息等于把身份信息交给对方。
     */
    private const val COMMON_NAME = "VitalBridge ADB"

    data class Id(val key: PrivateKey, val cert: X509Certificate)

    @Volatile
    private var cached: Id? = null

    /**
     * 读已有的身份；没有（或文件坏了）就**生成一份新的**。
     *
     * ⚠ 解析失败要**重建**而不是抛异常：坏掉的 key 文件会让"安装手表 App"永久失败，
     *   而错误表现是 TLS 握手失败（`Connection failed`），完全看不出是本地文件坏了。
     *   重建的代价只是重新配对一次，比"功能永久不可用"好。
     */
    @Synchronized
    fun loadOrCreate(ctx: Context): Id {
        cached?.let { return it }
        val dir = File(ctx.applicationContext.filesDir, DIR).apply { mkdirs() }
        val keyFile = File(dir, KEY_FILE)
        val crtFile = File(dir, CRT_FILE)

        if (keyFile.isFile && crtFile.isFile) {
            runCatching {
                val id = Id(readKey(keyFile), readCert(crtFile))
                cached = id
                return id
            }.onFailure {
                Log.w(TAG, "已存的 ADB 身份读不出来，重建一份（需要重新配对）", it)
                runCatching { keyFile.delete() }
                runCatching { crtFile.delete() }
            }
        }

        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val der = SelfSignedCert.build(pair, COMMON_NAME)
        // ⚠ 先写证书再写私钥：中途被杀时，剩下的"只有证书没有私钥"那半套
        //   会在下次启动因为 `keyFile.isFile` 为假而整体重建，不会留下坏状态。
        writePrivate(keyFile, pair.private.encoded)
        writePrivate(crtFile, der)
        val id = Id(pair.private, readCert(crtFile))
        cached = id
        Log.i(TAG, "已生成本机的 ADB 身份（首次使用 / 重建）")
        return id
    }

    /** 换一份新身份（排错用：手表不认密钥时，让用户能主动重来一次）。 */
    @Synchronized
    fun reset(ctx: Context) {
        cached = null
        val dir = File(ctx.applicationContext.filesDir, DIR)
        runCatching { File(dir, KEY_FILE).delete() }
        runCatching { File(dir, CRT_FILE).delete() }
    }

    private fun readKey(f: File): PrivateKey =
        KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(f.readBytes()))

    private fun readCert(f: File): X509Certificate =
        f.inputStream().use { ins ->
            CertificateFactory.getInstance("X.509").generateCertificate(ins) as X509Certificate
        }

    /**
     * 写到私有目录：**先写 `.tmp` 再改名**，并收紧到"仅本应用可读写"。
     *
     * ⚠ 不先写 tmp 的话，写到一半被杀会留下半个 DER —— 它同样"文件存在但解析不了"，
     *   于是每次启动都要走一遍重建 + 重新配对。
     */
    private fun writePrivate(dst: File, bytes: ByteArray) {
        val tmp = File(dst.parentFile, dst.name + ".tmp")
        tmp.outputStream().use { it.write(bytes) }
        // 所有者可读写、其他人全禁（父目录默认已是 0700，这里是第二道）
        runCatching { tmp.setReadable(false, false); tmp.setReadable(true, true) }
        runCatching { tmp.setWritable(false, false); tmp.setWritable(true, true) }
        if (dst.exists()) dst.delete()
        if (!tmp.renameTo(dst)) {
            tmp.copyTo(dst, overwrite = true)
            tmp.delete()
        }
    }
}
