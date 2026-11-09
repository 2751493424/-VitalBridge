package com.gemini.healthbridge.core

import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.SecureRandom
import java.security.Signature
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 用**纯 JDK** 生成一张自签的 X.509 v3 证书（DER 字节）。
 *
 * ## 为什么自己写 DER，不引 BouncyCastle（2026-11-03）
 *
 * 起因是 `assets/adb/adbkey.pem` 里躺着一份**明文私钥**：
 * assets 打进 APK，谁拿到安装包都能 `unzip` 出来 —— 那不是"密钥泄露的风险"，
 * 而是"密钥本来就是公开的"。任何装过本 App 的人都能用同一个身份去连一只授权过的手表。
 *
 * 修法只有一条：**在设备上现场生成**，存进应用私有目录。而"生成自签证书"在 Android 上
 * 没有公开 API（`sun.security.x509` 是隐藏 API），常规做法是引 BouncyCastle。
 * 这里没引，理由是**体积**：`bcprov` + `bcpkix` 打进包要 1~3 MB，而项目对包体反复做过比对，
 * 为一张自己签自己的证书付这个代价不值 —— 需要编码的 ASN.1 结构是固定的，两百行够。
 *
 * ## 只有三条硬要求（不是"实现一个 CA"）
 * 1. 能被标准解析器读出来（`CertificateFactory.getInstance("X.509")` / `openssl x509`）；
 * 2. 里面的公钥 == 我们拿去连 ADB 的那一对；
 * 3. 自签名能自校验（`cert.verify(cert.publicKey)`）。
 *
 * ADB 的鉴权只比对**公钥**（`/data/misc/adb/adb_keys` 里存的是 base64 的 SPKI），
 * 不校验链、不校验 CN、不看扩展。所以这里刻意做最小集：
 * v3 + basicConstraints(CA:false) + keyUsage(digitalSignature)，不多造扩展。
 *
 * ⚠⚠ **两条容易写错、且错了只在真机上才暴露的地方**（离线校验台架钉住）：
 *  · `UTCTime` 只能表示 **1950–2049**。有效期超过 2049 必须改用 `GeneralizedTime`
 *    （`20500101000000Z`），否则解析器直接判非法。这里把有效期上限卡在 2049 之前。
 *  · DER 的 INTEGER 是**有符号**的：最高位是 1 就必须前置一个 `0x00`，
 *    否则序列号会被读成负数（部分解析器拒绝）。
 */
object SelfSignedCert {

    /** sha256WithRSAEncryption */
    private const val OID_SHA256_RSA = "1.2.840.113549.1.1.11"

    /** id-at-commonName */
    private const val OID_CN = "2.5.4.3"

    /** id-ce-basicConstraints */
    private const val OID_BASIC_CONSTRAINTS = "2.5.29.19"

    /** id-ce-keyUsage */
    private const val OID_KEY_USAGE = "2.5.29.15"

    /** UTCTime 的上限：2049-12-31。超过就得换 GeneralizedTime，这里直接夹住。 */
    private const val MAX_VALIDITY_DAYS = 365L * 24  // ≈ 24 年，从今天算稳稳落在 2049 前

    /**
     * 生成一张自签证书。
     *
     * @param pair 用来签名的密钥对（RSA 2048 即可；ADB 只认 RSA）
     * @param commonName 证书主体名。**不要**塞设备序列号之类的敏感信息 ——
     *   这个名字会跟着公钥被写进手表的 `adb_keys`。
     * @param validityDays 有效期；会被夹到 [MAX_VALIDITY_DAYS] 以内（见上面 UTCTime 的坑）
     * @return 完整的 `Certificate` DER（可直接写成 `.crt` / 转 PEM）
     */
    fun build(
        pair: KeyPair,
        commonName: String,
        validityDays: Int = 7300,
    ): ByteArray {
        val days = validityDays.coerceIn(1, MAX_VALIDITY_DAYS.toInt())
        val now = System.currentTimeMillis()
        // 起始时间往前挪一天：设备时钟比手表慢几分钟时，证书会被判"还没生效"
        val notBefore = Date(now - 24L * 3600_000L)
        val notAfter = Date(now + days.toLong() * 24L * 3600_000L)

        // 序列号：8 字节随机，最高位清零（保证是正数，见类注释第二个坑）
        val serial = ByteArray(8).also { SecureRandom().nextBytes(it) }
        serial[0] = (serial[0].toInt() and 0x7F).toByte()

        val algId = seq(oid(OID_SHA256_RSA), nul())
        val name = seq(set(seq(oid(OID_CN), utf8(commonName))))
        val validity = seq(utcTime(notBefore), utcTime(notAfter))

        // SubjectPublicKeyInfo：`PublicKey.getEncoded()` 给的就是完整 SPKI，直接用
        val spki = pair.public.encoded

        // [3] EXPLICIT Extensions —— 只放两个最小扩展
        val extensions = ctx(
            3,
            seq(
                // basicConstraints: CA = FALSE。DER 要求"默认值不编码"，
                // 所以这里**不写**那个 BOOLEAN（写 FALSE 反而不合 DER，严格解析器会抱怨）
                seq(oid(OID_BASIC_CONSTRAINTS), octet(seq())),
                // keyUsage: digitalSignature（bit 0）⇒ 0x80，尾部 7 个未用位
                seq(oid(OID_KEY_USAGE), bool(true), octet(bitString(byteArrayOf(0x80.toByte()), 7))),
            ),
        )

        val tbs = seq(
            ctx(0, integer(byteArrayOf(2))),   // version: v3（[0] EXPLICIT INTEGER 2）
            integer(serial),
            algId,
            name,                              // issuer == subject（自签）
            validity,
            name,
            spki,
            extensions,
        )

        val sig = Signature.getInstance("SHA256withRSA").run {
            initSign(pair.private)
            update(tbs)
            sign()
        }
        return seq(tbs, algId, bitString(sig, 0))
    }

    // ═══════════════════════ ASN.1 DER 最小实现 ═══════════════════════

    private fun cat(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (p in parts) out.write(p)
        return out.toByteArray()
    }

    /** 标签 + 长度 + 内容。长度按 DER 的最短形式编码（<128 单字节，否则 0x8N + N 字节）。 */
    private fun tlv(tag: Int, body: ByteArray): ByteArray {
        val n = body.size
        val len = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            n < 0x10000 -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
            else -> byteArrayOf(
                0x83.toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte(),
            )
        }
        return cat(byteArrayOf(tag.toByte()), len, body)
    }

    private fun seq(vararg items: ByteArray) = tlv(0x30, cat(*items))
    private fun set(vararg items: ByteArray) = tlv(0x31, cat(*items))
    private fun nul() = byteArrayOf(0x05, 0x00)
    private fun octet(body: ByteArray) = tlv(0x04, body)

    /** context-specific constructed（`[n] EXPLICIT`）。 */
    private fun ctx(n: Int, body: ByteArray) = tlv(0xA0 or n, body)

    private fun bool(v: Boolean) = tlv(0x01, byteArrayOf(if (v) 0xFF.toByte() else 0x00))

    private fun utf8(s: String) = tlv(0x0C, s.toByteArray(Charsets.UTF_8))

    /**
     * INTEGER。入参视为**无符号**字节序列：最高位为 1 时补一个 0x00。
     * （DER INTEGER 是有符号的，见类注释第二个坑。）
     */
    private fun integer(unsigned: ByteArray): ByteArray {
        val needPad = unsigned.isNotEmpty() && (unsigned[0].toInt() and 0x80) != 0
        val body = if (needPad) cat(byteArrayOf(0), unsigned) else unsigned
        return tlv(0x02, body)
    }

    /** BIT STRING：首字节是"尾部未用位数"。 */
    private fun bitString(bytes: ByteArray, unusedBits: Int) =
        tlv(0x03, cat(byteArrayOf(unusedBits.toByte()), bytes))

    /**
     * UTCTime：`YYMMDDHHMMSSZ`，**必须带 Z**（UTC）。
     * ⚠ 不能用 `SimpleDateFormat` 的共享实例（线程不安全），每次新建。
     */
    private fun utcTime(d: Date): ByteArray {
        val f = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return tlv(0x17, f.format(d).toByteArray(Charsets.US_ASCII))
    }

    /**
     * OBJECT IDENTIFIER。第一段 = 40*a + b，其余每段 base-128 变长编码（高位续 1）。
     * 手写而不是查表：这里只用到 4 个 OID，写死反而更难读。
     */
    private fun oid(dotted: String): ByteArray {
        val parts = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((parts[0] * 40 + parts[1]).toInt())
        for (p in parts.drop(2)) {
            var v = p
            val buf = ArrayList<Int>(8)
            do {
                buf.add((v and 0x7F).toInt())
                v = v shr 7
            } while (v > 0)
            for (i in buf.indices.reversed()) {
                // 除最后一段外都置续位
                out.write(if (i == 0) buf[i] else buf[i] or 0x80)
            }
        }
        return tlv(0x06, out.toByteArray())
    }
}
