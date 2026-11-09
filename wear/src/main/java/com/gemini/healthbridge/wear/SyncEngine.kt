package com.gemini.healthbridge.wear

import android.content.Context
import com.gemini.healthbridge.core.AckMessage
import com.gemini.healthbridge.core.HealthItemDto
import com.gemini.healthbridge.core.HelloMessage
import com.gemini.healthbridge.core.RecordType
import com.gemini.healthbridge.core.ResText
import com.gemini.healthbridge.core.SyncMessage
import com.gemini.healthbridge.core.SyncPaths
import com.gemini.healthbridge.core.db.AppDatabase
import com.gemini.healthbridge.core.db.toSyncDto
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * 手表端同步引擎：
 *  - 上线/能力变化时向手机发 hello（含待同步计数）
 *  - 收到手机的 sync_req 后按 hr/bp/ecg 逐批上传，每批等待 ack 后标记已同步
 */
class SyncEngine(
    private val context: Context,
    private val db: AppDatabase,
    private val scope: CoroutineScope,
) {
    private val messageClient = Wearable.getMessageClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _status = MutableStateFlow(ResText.of(R.string.s_c8f2d850))
    val status: StateFlow<ResText> = _status

    private val pendingAcks = mutableMapOf<String, CompletableDeferred<AckMessage>>()
    private var started = false

    private val listener = object : MessageClient.OnMessageReceivedListener {
        override fun onMessageReceived(event: MessageEvent) {
            scope.launch { onMessage(event) }
        }
    }

    private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { info ->
        scope.launch {
            if (info.nodes.any { it.isNearby }) sendHello()
        }
    }

    fun start() {
        if (started) return
        started = true
        messageClient.addListener(listener)
        capabilityClient.addListener(capabilityListener, SyncPaths.CAPABILITY)
        scope.launch { sendHello() }
    }

    fun stop() {
        if (!started) return
        started = false
        messageClient.removeListener(listener)
        capabilityClient.removeListener(capabilityListener)
    }

    private suspend fun onMessage(event: MessageEvent) {
        when (event.path) {
            SyncPaths.SYNC_REQUEST -> {
                _status.value = ResText.of(R.string.s_30da9173)
                sendHello()
                syncAll()
            }
            SyncPaths.ACK -> {
                val ack = runCatching {
                    json.decodeFromString<AckMessage>(String(event.data))
                }.getOrNull() ?: return
                pendingAcks.remove(key(ack.type, ack.upTo))?.complete(ack)
            }
        }
    }

    suspend fun sendHello() {
        val node = phoneNode()
        if (node == null) {
            val capCount = runCatching {
                capabilityClient
                    .getCapability(SyncPaths.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                    .await().nodes.size
            }.getOrElse { -1 }
            val nodeCount = runCatching {
                Wearable.getNodeClient(context).connectedNodes.await().size
            }.getOrElse { -2 }
            _status.value = ResText.of(R.string.s_5e0991be, capCount, nodeCount)
            return
        }
        val hello = HelloMessage(
            deviceId = android.os.Build.MODEL ?: "watch",
            deviceName = android.os.Build.MODEL ?: "watch",
            pendingHr = db.heartRateDao().unsyncedCount(),
            pendingBp = db.bloodPressureDao().unsyncedCount(),
            pendingEcg = db.ecgDao().unsyncedCount(),
            pendingSpo2 = db.spo2Dao().unsyncedCount(),
            pendingTemp = db.temperatureDao().unsyncedCount(),
            pendingBia = db.bodyCompositionDao().unsyncedCount(),
            pendingActivity = db.activityDao().unsyncedCount(),
            pendingPpg = db.ppgWaveDao().unsyncedCount(),
            pendingHrv = db.hrvDao().unsyncedCount(),
            pendingMf = db.mfBiaDao().unsyncedCount(),
        )
        runCatching {
            messageClient.sendMessage(
                node.id, SyncPaths.HELLO,
                json.encodeToString(HelloMessage.serializer(), hello).toByteArray(Charsets.UTF_8),
            ).await()
            _status.value = ResText.of(R.string.s_d556f063, node.displayName)
        }.onFailure { _status.value = ResText.of(R.string.s_0be9f8d0, it.message) }
    }

    /** 全量同步入口（也由 UI 的“同步到手机”按钮调用）。 */
    fun syncNow() {
        scope.launch {
            sendHello()
            syncAll()
        }
    }

    private suspend fun syncAll() {
        val node = phoneNode() ?: return
        var sent = 0
        for (type in RecordType.ALL) {
            while (true) {
                val batch = loadBatch(type, batchSizeFor(type)) ?: break
                if (batch.isEmpty()) break
                val upTo = batch.maxOf { it.recordedAt }
                val deferred = CompletableDeferred<AckMessage>()
                pendingAcks[key(type, upTo)] = deferred
                val msg = SyncMessage(type, android.os.Build.MODEL, batch, upTo)
                val ok = runCatching {
                    messageClient.sendMessage(
                        node.id, SyncPaths.DATA,
                        json.encodeToString(SyncMessage.serializer(), msg)
                            .toByteArray(Charsets.UTF_8),
                    ).await()
                }.isSuccess
                if (!ok) {
                    pendingAcks.remove(key(type, upTo))
                    _status.value = ResText.of(R.string.s_166b4f89)
                    return
                }
                val ack = withTimeoutOrNull(20_000) { deferred.await() }
                if (ack == null) {
                    pendingAcks.remove(key(type, upTo))
                    _status.value = ResText.of(R.string.s_b2fb374e)
                    return
                }
                markSyncedUpTo(type, upTo)
                sent += ack.inserted
                _status.value = ResText.of(R.string.s_c2b1e7ba, sent)
            }
        }
        _status.value = ResText.of(R.string.s_994b16b3, sent)
        val done = HelloMessage(
            deviceId = android.os.Build.MODEL,
            deviceName = android.os.Build.MODEL,
            pendingHr = db.heartRateDao().unsyncedCount(),
            pendingBp = db.bloodPressureDao().unsyncedCount(),
            pendingEcg = db.ecgDao().unsyncedCount(),
            pendingSpo2 = db.spo2Dao().unsyncedCount(),
            pendingTemp = db.temperatureDao().unsyncedCount(),
            pendingBia = db.bodyCompositionDao().unsyncedCount(),
            pendingActivity = db.activityDao().unsyncedCount(),
            pendingPpg = db.ppgWaveDao().unsyncedCount(),
            pendingHrv = db.hrvDao().unsyncedCount(),
            pendingMf = db.mfBiaDao().unsyncedCount(),
        )
        runCatching {
            messageClient.sendMessage(
                node.id, SyncPaths.DONE,
                json.encodeToString(HelloMessage.serializer(), done).toByteArray(Charsets.UTF_8),
            ).await()
        }
    }

    /** 取某类型待同步记录，转换为 DTO。 */
    private suspend fun loadBatch(type: String, limit: Int): List<HealthItemDto>? {
        return when (type) {
            RecordType.HR -> db.heartRateDao().unsynced(limit).map {
                HealthItemDto(recordedAt = it.measuredAt, bpm = it.bpm)
            }
            RecordType.BP -> db.bloodPressureDao().unsynced(limit).map {
                HealthItemDto(
                    recordedAt = it.recordedAt, systolic = it.systolic,
                    diastolic = it.diastolic, pulse = it.pulse, note = it.note,
                )
            }
            // 心电：结论来自 ecg_records，波形按同一时间戳从 ecg_waves 取出一并带上
            RecordType.ECG -> db.ecgDao().unsynced(limit).map { r ->
                val wave = runCatching {
                    db.ecgWaveDao().byTime(r.recordedAt, r.source)
                }.getOrNull()
                HealthItemDto(
                    recordedAt = r.recordedAt,
                    classification = r.classification,
                    bpm = r.heartRate?.toDouble(),
                    note = r.note,
                    durationMs = wave?.durationMs,
                    sampleRate = wave?.sampleRate,
                    regular = wave?.regular?.let { it != 0 },
                    beatCount = wave?.beatCount,
                    bandRatio = wave?.bandRatio,
                    wave = wave?.waveJson,
                )
            }
            RecordType.SPO2 -> db.spo2Dao().unsynced(limit).map {
                HealthItemDto(recordedAt = it.measuredAt, spo2 = it.percent)
            }
            RecordType.TEMP -> db.temperatureDao().unsynced(limit).map {
                HealthItemDto(recordedAt = it.measuredAt, tempC = it.celsius, ambientC = it.ambientC)
            }
            // 体成分一条记录只有十几个 Float，体积和血压一个量级，用默认批大小即可
            RecordType.BIA -> db.bodyCompositionDao().unsynced(limit).map {
                HealthItemDto(recordedAt = it.measuredAt, bia = it.toSyncDto())
            }
            // 活动片段：统计量 + 一条降采样包络，一条约 0.6KB（包络占大半），
            // 比心电波形小两个数量级，用默认批大小即可。
            RecordType.ACTIVITY -> db.activityDao().unsynced(limit).map {
                HealthItemDto(recordedAt = it.startedAt, activity = it.toSyncDto())
            }
            // PPG：一条要带三段波形（绿/红/红外，各最多 3000 点 × 8 字符 ≈ 24KB），
            // 比心电波形还大，见 batchSizeFor 的单条一批说明。
            RecordType.PPG -> db.ppgWaveDao().unsynced(limit).map {
                HealthItemDto(recordedAt = it.measuredAt, ppg = it.toSyncDto())
            }
            // HRV：一条只有约 35 个整数（逐拍间期），用默认批大小即可。
            // 带上的是**输入而不是指标**（指标两端现算），见 HrvSessionEntity。
            RecordType.HRV -> db.hrvDao().unsynced(limit).map {
                HealthItemDto(recordedAt = it.measuredAt, hrv = it.toSyncDto())
            }
            // 多频阻抗：一条只有 8 个 Float，用默认批大小即可（与 BluetoothSync 那处同步改）
            RecordType.MF_BIA -> db.mfBiaDao().unsynced(limit).map {
                HealthItemDto(recordedAt = it.measuredAt, mfBia = it.toSyncDto())
            }
            else -> null
        }
    }

    private suspend fun markSyncedUpTo(type: String, upTo: Long) {
        when (type) {
            RecordType.HR -> db.heartRateDao().markSyncedUpTo(upTo)
            RecordType.BP -> db.bloodPressureDao().markSyncedUpTo(upTo)
            RecordType.ECG -> db.ecgDao().markSyncedUpTo(upTo)
            RecordType.SPO2 -> db.spo2Dao().markSyncedUpTo(upTo)
            RecordType.TEMP -> db.temperatureDao().markSyncedUpTo(upTo)
            RecordType.BIA -> db.bodyCompositionDao().markSyncedUpTo(upTo)
            RecordType.ACTIVITY -> db.activityDao().markSyncedUpTo(upTo)
            RecordType.PPG -> db.ppgWaveDao().markSyncedUpTo(upTo)
            RecordType.HRV -> db.hrvDao().markSyncedUpTo(upTo)
            RecordType.MF_BIA -> db.mfBiaDao().markSyncedUpTo(upTo)
        }
    }

    private suspend fun phoneNode(): Node? {
        // 优先按能力发现（双方 App 都声明 gemini_health_sync），
        // 找不到再兜底用 NodeClient 直连节点，避免能力广播延迟导致握手失败
        val viaCap = runCatching {
            capabilityClient
                .getCapability(SyncPaths.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
                .minByOrNull { if (it.isNearby) 0 else 1 }
        }.getOrNull()
        if (viaCap != null) return viaCap
        return runCatching {
            Wearable.getNodeClient(context).connectedNodes.await()
                .minByOrNull { if (it.isNearby) 0 else 1 }
        }.getOrNull()
    }

    private fun key(type: String, upTo: Long) = "$type:$upTo"

    companion object {
        private const val BATCH_SIZE = 40

        /**
         * ECG 一条要携带约 16KB 波形文本；Wearable 单条消息上限 100KB，固定单条一批。
         * PPG 一条最多带三段波形（各 3000 点 × 8 字符 ≈ 24KB），同理固定单条一批。
         */
        private fun batchSizeFor(type: String): Int =
            if (type == RecordType.ECG || type == RecordType.PPG) 1 else BATCH_SIZE
    }
}
