package com.gemini.healthbridge.wear

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.SampleDataPoint
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.unregisterMeasureCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.gemini.healthbridge.core.ResText

/** 实时心率测量：基于 androidx.health Health Services 的 MeasureClient。 */
class HealthMeasurer(context: Context, private val scope: CoroutineScope) {

    data class HrState(
        val supported: Boolean = false,
        val measuring: Boolean = false,
        val bpm: Double? = null,
        val availability: ResText = ResText.core("s_1b3c1daa"),
    )

    private val measureClient = HealthServices.getClient(context).measureClient
    private var currentCallback: MeasureCallback? = null

    private val _state = MutableStateFlow(HrState())
    val state: StateFlow<HrState> = _state

    private val _samples = MutableSharedFlow<Double>(extraBufferCapacity = 64)
    /** 每个传感器样本（用于测量界面的会话统计）。 */
    val samples: SharedFlow<Double> = _samples

    /** 每个新样本回调（由 MainActivity 持久化入库）。 */
    var onSample: ((Double) -> Unit)? = null

    init {
        scope.launch {
            _state.value = _state.value.copy(
                supported = runCatching {
                    measureClient.getCapabilities()
                        .supportedDataTypesMeasure.contains(DataType.HEART_RATE_BPM)
                }.getOrDefault(false),
            )
        }
    }

    fun start() {
        if (currentCallback != null) return
        val callback = object : MeasureCallback {
            override fun onRegistered() {
                _state.value = _state.value.copy(
                    measuring = true, availability = ResText.core("s_c70ed011")
                )
            }

            override fun onRegistrationFailed(throwable: Throwable) {
                currentCallback = null
                _state.value = _state.value.copy(
                    measuring = false,
                    availability = ResText.core("s_e85ca052", throwable.javaClass.simpleName, throwable.message),
                )
            }

            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability,
            ) {
                _state.value = _state.value.copy(availability = ResText.raw(availability.toString()))
            }

            override fun onDataReceived(data: DataPointContainer) {
                val points: List<SampleDataPoint<Double>> =
                    data.getData(DataType.HEART_RATE_BPM)
                points.lastOrNull()?.value?.let { bpm ->
                    _state.value = _state.value.copy(bpm = bpm, availability = ResText.core("s_003751e1"))
                    _samples.tryEmit(bpm)
                    onSample?.invoke(bpm)
                }
            }
        }
        runCatching {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
            currentCallback = callback
            _state.value = _state.value.copy(measuring = true, availability = ResText.core("s_cf6b907d"))
        }.onFailure {
            _state.value = _state.value.copy(availability = ResText.core("s_75fe88f4", it.message))
        }
    }

    fun stop() {
        val cb = currentCallback ?: return
        currentCallback = null
        _state.value = _state.value.copy(measuring = false, availability = ResText.core("s_18c32df8"))
        scope.launch {
            runCatching {
                measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, cb)
            }
        }
    }
}
