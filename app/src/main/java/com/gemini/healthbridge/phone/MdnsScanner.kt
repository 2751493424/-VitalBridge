package com.gemini.healthbridge.phone

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.math.min

/**
 * **自己发 mDNS 查询**（UDP 5353），用来补上 `NsdManager` 在某些 ROM 上"什么都不返回"的问题。
 *
 * ## 为什么不能只靠 `NsdManager`
 *
 * 实测（用户真机，ColorOS）：手表无线调试明明开着、同网段可达，但
 * `NsdManager.discoverServices("_adb-tls-connect._tcp")` **一次都没回调**——
 * 既不 `onServiceFound` 也不 `onStartDiscoveryFailed`，就是静默无结果。
 * 这类"系统 mDNS 服务被裁/被限"的机器只能自己发查询。
 *
 * ## 做法
 *
 * - 往 `224.0.0.251:5353` 发 **PTR 查询**，QTYPE=12，并且置 **QU 位**（请求单播应答）
 *   ⇒ 应答会回到我们这个临时端口，不需要占 5353（那口被系统 mdnsd 占着，抢不到）。
 * - 解析应答里的 PTR → SRV(端口, 目标) → A(IPv4)，拼出 `ip:port`。
 * - 每 2 秒重发一次，直到 [stop]（手表可能后开无线调试）。
 *
 * ## ⚠ 判断"到底是没广播还是没收到"
 *
 * [onDiag] 会把"收到几个包、解析出几条"报上去。全是 0 ⇒ 网络层就没通（AP 隔离/关多播），
 * 这时候只能手动填 IP——不要再去怀疑手表没开无线调试。
 */
class MdnsScanner(
    private val onFound: (host: String, port: Int, pairing: Boolean) -> Unit,
    private val onDiag: (String) -> Unit,
) {
    companion object {
        private const val TAG = "MdnsScanner"
        private const val GROUP = "224.0.0.251"
        private const val PORT = 5353
        private val CONNECT_TYPES = listOf(
            "_adb-tls-connect._tcp.local" to false,
            "_adb-tls-pairing._tcp.local" to true,
        )
    }

    @Volatile private var running = false
    private var thread: Thread? = null
    private var sock: DatagramSocket? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "mdns-scan").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        runCatching { sock?.close() }
        thread = null
    }

    private fun loop() {
        var packets = 0
        var services = 0
        try {
            val s = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(0))
                soTimeout = 1200
            }
            sock = s
            val group = InetAddress.getByName(GROUP)
            var round = 0
            while (running) {
                // 每 2 秒问一次（手表可能刚打开无线调试）
                for ((name, _) in CONNECT_TYPES) {
                    runCatching {
                        s.send(DatagramPacket(buildQuery(name), 0, buildQuery(name).size, group, PORT))
                    }
                }
                round++
                // 收 1.2 秒的应答
                val deadline = System.currentTimeMillis() + 1200
                val buf = ByteArray(4096)
                while (running && System.currentTimeMillis() < deadline) {
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        s.receive(p)
                    } catch (_: java.net.SocketTimeoutException) {
                        continue
                    }
                    packets++
                    val parsed = runCatching { parseResponse(buf, p.length) }.getOrElse {
                        Log.w(TAG, "解析失败：${it.javaClass.simpleName}")
                        emptyList()
                    }
                    for (r in parsed) {
                        services++
                        onFound(r.first, r.second, r.third)
                    }
                }
                if (round == 4) {
                    onDiag("mDNS: 收到 $packets 个包 / $services 条服务")
                }
            }
        } catch (e: Exception) {
            // ⚠ **主动 stop() 关掉 socket 会让 receive 抛 EBADF/SocketException** ——
            //   那不是"扫描失败"，别报（真机上就出现过「mDNS 扫描失败：recvfrom failed: EBADF」，
            //   用户以为功能坏了）。
            if (running) {
                Log.w(TAG, "扫描线程结束", e)
                onDiag("mDNS 扫描失败：${e.javaClass.simpleName} ${e.message ?: ""}")
            }
        } finally {
            runCatching { sock?.close() }
        }
    }

    // ── DNS 报文 ──────────────────────────────────────────────────────────

    /** 一条 PTR 查询（qclass 最高位置 1 = 请求单播应答）。 */
    private fun buildQuery(name: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0))            // id = 0
        out.write(byteArrayOf(0, 0))            // flags = 标准查询
        out.write(byteArrayOf(0, 1))            // qdcount = 1
        out.write(byteArrayOf(0, 0))            // ancount
        out.write(byteArrayOf(0, 0))            // nscount
        out.write(byteArrayOf(0, 0))            // arcount
        for (label in name.split('.')) {
            if (label.isEmpty()) continue
            val b = label.toByteArray(Charsets.UTF_8)
            out.write(b.size)
            out.write(b)
        }
        out.write(0)                            // 结束
        out.write(byteArrayOf(0, 12))           // QTYPE = PTR
        out.write(byteArrayOf(0x80.toByte(), 1))// QCLASS = IN | QU
        return out.toByteArray()
    }

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    /** 解析一条应答，返回 (ip, port, isPairing)。 */
    private fun parseResponse(b: ByteArray, len: Int): List<Triple<String, Int, Boolean>> {
        if (len < 12) return emptyList()
        val qd = u16(b, 4)
        var off = 12
        repeat(qd) { off = skipName(b, off) + 4 }      // 跳过 question 段

        val ptrTargets = mutableMapOf<String, Boolean>()  // instance -> isPairing
        val srvPort = mutableMapOf<String, Int>()
        val srvHost = mutableMapOf<String, String>()
        val aAddr = mutableMapOf<String, String>()

        val total = u16(b, 6) + u16(b, 8) + u16(b, 10)
        repeat(total) {
            if (off >= len) return@repeat
            val (name, next) = readName(b, off)
            off = next
            if (off + 10 > len) return@repeat
            val type = u16(b, off)
            val rdlen = u16(b, off + 8)
            val rdStart = off + 10
            off = rdStart + rdlen
            if (rdStart + rdlen > len) return@repeat
            when (type) {
                12 -> { // PTR
                    val (target, _) = readName(b, rdStart)
                    val lower = name.lowercase()
                    val pairing = lower.startsWith("_adb-tls-pairing")
                    if (lower.startsWith("_adb-tls-")) ptrTargets[target] = pairing
                }
                33 -> { // SRV
                    if (rdlen >= 6) {
                        val port = u16(b, rdStart + 4)
                        val (host, _) = readName(b, rdStart + 6)
                        srvPort[name] = port
                        srvHost[name] = host
                    }
                }
                1 -> { // A
                    if (rdlen == 4) {
                        aAddr[name] = "${b[rdStart].toInt() and 0xFF}.${b[rdStart + 1].toInt() and 0xFF}." +
                            "${b[rdStart + 2].toInt() and 0xFF}.${b[rdStart + 3].toInt() and 0xFF}"
                    }
                }
            }
        }

        val out = mutableListOf<Triple<String, Int, Boolean>>()
        for ((instance, pairing) in ptrTargets) {
            val port = srvPort[instance] ?: srvPort.values.firstOrNull() ?: continue
            val hostName = srvHost[instance]
            val ip = hostName?.let { aAddr[it] } ?: aAddr.values.firstOrNull() ?: continue
            out += Triple(ip, port, pairing)
        }
        return out
    }

    /** 读一个（可能被压缩的）域名，返回 (名字, 下一个偏移)。 */
    private fun readName(b: ByteArray, start: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var off = start
        var next = -1
        var hops = 0
        while (off < b.size) {
            val l = b[off].toInt() and 0xFF
            if (l == 0) { off++; break }
            if (l and 0xC0 == 0xC0) {          // 压缩指针
                if (off + 1 >= b.size) break
                val ptr = ((l and 0x3F) shl 8) or (b[off + 1].toInt() and 0xFF)
                if (next < 0) next = off + 2
                off = ptr
                if (++hops > 16) break
                continue
            }
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(b, off + 1, min(l, b.size - off - 1), Charsets.UTF_8))
            off += l + 1
        }
        return sb.toString() to (if (next >= 0) next else off)
    }

    private fun skipName(b: ByteArray, start: Int): Int {
        var off = start
        while (off < b.size) {
            val l = b[off].toInt() and 0xFF
            if (l == 0) return off + 1
            if (l and 0xC0 == 0xC0) return off + 2
            off += l + 1
        }
        return off
    }
}
