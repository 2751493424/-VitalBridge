package com.gemini.healthbridge.phone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.gemini.healthbridge.core.Announcement
import com.gemini.healthbridge.core.AnnouncementHttp
import java.io.File

/**
 * 公告配图的加载与缓存（2026-10-05 加）。
 *
 * ## 为什么自己写，而不引 Coil / Glide
 *
 * 需要的只有三件事：多源下载（已有 [AnnouncementHttp]）、磁盘缓存、按尺寸降采样。
 * 为这三件事引一个图片库（连带 OkHttp/okio）不值得 —— 而且本项目**只有公告与更新检查两处联网**，
 * 复用它已有的那份 HTTP 实现，连"第二个网络栈"都不会出现。
 *
 * ## 缓存分两层，各自解决一个问题
 *
 * | 层 | 解决 | 何时失效 |
 * |---|---|---|
 * | 内存 `LruCache` | 列表/弹窗反复进出时**不重复解码** | 进程结束 |
 * | 磁盘 `cacheDir/announce_images/` | 图**只下载一次**，之后离线也能看 | 系统清缓存 / 卸载 |
 *
 * ⚠⚠ **缓存键是图片的相对路径（= 文件名）**，所以**改图必须改文件名**。
 *   这与 jsDelivr 的 CDN 缓存是同一个道理（那边改了图内容、名字不变，也可能几小时不更新）。
 *   ⇒ 约定写进 `announcement.json` 的 `_fields`：换图就换名（`promo-v2.png`）。
 *   ⚠ 不用"内容哈希"当键：那得先下载才知道哈希，而缓存的全部意义正是**不下载**。
 *
 * ## 失败一律是 `null`，静默
 *
 * 公告是附加信息。图加载不出来时**不留破图、不弹提示**（与"联网失败绝不能让用户看到报错"
 * 是同一条纪律）—— 用户看到的是一条正常的纯文字公告，而不是一个错误。
 */
object AnnouncementImages {

    /**
     * 内存缓存上限：**按字节数算**（不是条数）。
     *
     * ⚠ 按条数算会算错：一张 2000×2000 的位图是 16 MB，而一张缩略图可能只有 40 KB。
     *   限条数等于"允许 8 条 16 MB 的图占 128 MB"。
     */
    private val memory = object : android.util.LruCache<String, Bitmap>(
        // 取进程可用内存的 1/8，但不超过 8 MB —— 公告图只是锦上添花，不该和主界面抢内存
        minOf(8 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 8).toInt())
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** 磁盘缓存目录（`cacheDir` ⇒ 系统在空间紧张时可以自己清掉，不需要我们管）。 */
    private const val DISK_DIR = "announce_images"

    /**
     * 解码后的最长边上限（像素）。
     *
     * ⚠ 公告弹窗的显示宽度约 300dp：在 3x 屏上是 900px。
     *   取 1080 留一点余量，同时挡住"原图 4000px 直接进内存"（那会 OOM，
     *   而 OOM 在这个 App 里是不可接受的 —— 弹窗只是附加信息）。
     */
    private const val MAX_DIM = 1080

    /**
     * 取一张公告配图。**永不抛异常**：失败返回 `null`，调用方据此不显示图片区。
     *
     * @param image [com.gemini.healthbridge.core.AnnouncementItem.image]（**相对路径**，
     *   已由 [Announcement.isValidImagePath] 校验过）。
     * @param jsonUrls 公告 JSON 的地址表 —— 由调用方传入（而不是在这里读
     *   `AnnouncementRepository.URLS`）：图片的基址必须与 JSON **同一套**，
     *   而"同一套"这件事只有调用点看得见。
     */
    suspend fun load(context: Context, image: String, jsonUrls: List<String>): Bitmap? {
        if (image.isEmpty()) return null

        memory.get(image)?.let { return it }

        // ⚠ 先查磁盘再联网：图只该下载一次
        val disk = diskFile(context, image)
        if (disk.isFile) {
            decodeCapped(disk.readBytes())?.let {
                memory.put(image, it)
                return it
            }
            // 盘上那份解不出来（下了一半、被清过、或根本不是图）⇒ 删掉重下。
            // ⚠ 不删的话它会被**永久**当成缓存命中，而用户永远看不到图。
            runCatching { disk.delete() }
        }

        val candidates = Announcement.imageCandidates(image, jsonUrls)
        if (candidates.isEmpty()) return null

        val (vn, vc) = versionInfo(context)
        val res = runCatching {
            AnnouncementHttp.fetchBytes(
                urls = candidates,
                userAgent = AnnouncementHttp.userAgent(vn, vc),
            )
        }.getOrNull() as? AnnouncementHttp.BytesResult.Ok ?: return null

        // ⚠ **先解码成功再写盘**：写盘了却解不出来，下次就会一直命中一份坏缓存。
        val bmp = decodeCapped(res.bytes) ?: return null
        runCatching {
            disk.parentFile?.mkdirs()
            disk.writeBytes(res.bytes)
        }
        memory.put(image, bmp)
        return bmp
    }

    /** 磁盘缓存文件。⚠ 文件名里的 `/` 换成 `_` —— 路径分隔符不能出现在单个文件名里。 */
    private fun diskFile(context: Context, image: String): File =
        File(File(context.cacheDir, DISK_DIR), image.replace('/', '_'))

    /**
     * 按最长边 [MAX_DIM] 做 2 的幂次降采样后解码。
     *
     * ⚠ **必须先用 `inJustDecodeBounds` 探尺寸**：直接 `decodeByteArray` 一张 4000×3000
     *   的图会先按原尺寸分配 48 MB 内存，`inSampleSize` 就来不及起作用了。
     * ⚠ 返回 `null` 也表示"这不是一张能用的图片"（解不出宽高）—— 调用方按失败处理。
     */
    private fun decodeCapped(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_DIM ||
            bounds.outHeight / (sample * 2) >= MAX_DIM
        ) {
            sample *= 2
        }
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()

    /**
     * 请求头里的版本信息（与公告 JSON 的请求保持一致，便于托管方排查）。
     * ⚠ 用 `Pair` 而不是 `Array<Any>` + 展开：后者与 `userAgent(String, Int)` 的类型对不上，
     *   是**编译期**就能发现的问题，但写的时候很容易顺手写成 `arrayOf(...)`。
     */
    private fun versionInfo(context: Context): Pair<String, Int> = runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        (pi.versionName ?: "?") to pi.longVersionCode.toInt()
    }.getOrDefault("?" to 0)
}
