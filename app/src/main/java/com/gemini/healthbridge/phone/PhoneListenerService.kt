package com.gemini.healthbridge.phone

import com.gemini.healthbridge.core.AckMessage
import com.gemini.healthbridge.core.HelloMessage
import com.gemini.healthbridge.core.HourStatsMessage
import com.gemini.healthbridge.core.SyncMessage
import com.gemini.healthbridge.core.SyncPaths
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** 后台接收手表消息：hello -> 自动发起同步；data -> 入库并回 ack；done -> 更新状态。 */
class PhoneListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onMessageReceived(event: MessageEvent) {
        val text = String(event.data)
        when (event.path) {
            SyncPaths.HELLO -> scope.launch {
                runCatching {
                    val hello = json.decodeFromString<HelloMessage>(text)
                    SyncRepository.onHello(hello)
                    // 推送三星健康当日小时统计（手表图表用）
                    SyncRepository.sendHourStats(applicationContext)
                    // 手表和手机同时打开时：自动发起一次全量同步
                    SyncRepository.requestSync(applicationContext)
                }
            }

            SyncPaths.DATA -> scope.launch {
                runCatching {
                    val msg = json.decodeFromString<SyncMessage>(text)
                    val inserted = SyncRepository.insertSync(msg)
                    // 手机中转：把手表测量数据写入三星健康（需已授权写权限；失败静默）
                    runCatching {
                        SamsungHealthReader.relayWrite(applicationContext, msg)
                    }
                    SyncRepository.sendAck(
                        applicationContext, event.sourceNodeId,
                        AckMessage(type = msg.type, upTo = msg.upTo, inserted = inserted),
                    )
                }
            }

            SyncPaths.DONE -> scope.launch {
                runCatching {
                    SyncRepository.onDone(json.decodeFromString<HelloMessage>(text))
                    // 同步完成后推送最新小时统计（含刚写入三星健康的数据）
                    SyncRepository.sendHourStats(applicationContext)
                }
            }
        }
    }
}
