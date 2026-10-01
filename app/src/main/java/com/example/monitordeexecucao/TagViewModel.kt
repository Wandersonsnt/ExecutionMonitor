package com.example.monitordeexecucao

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class TagViewModel : ViewModel() {
    var isConnected by mutableStateOf(false)
    var isCalibrating by mutableStateOf(false)
    var isCountingDown by mutableStateOf(false)
    var normalizedPosition by mutableStateOf(0.0f)
    var dominantAxisName by mutableStateOf("Aguardando Calibração...")

    private var minAngles = floatArrayOf(1000f, 1000f, 1000f)
    private var maxAngles = floatArrayOf(-1000f, -1000f, -1000f)
    private var lastAngles = floatArrayOf(0f, 0f, 0f) // Salva a última posição conhecida

    private var activeAxisIndex: Int? = null
    private var isInverted: Boolean = false // Define se o movimento precisa ser espelhado

    fun toggleCalibration() {
        if (isCountingDown) return // Bloqueia cliques duplos durante a contagem

        if (isCalibrating) {
            // FIM DA CALIBRAÇÃO: Descobrir qual eixo moveu mais
            isCalibrating = false
            var maxDelta = 0.0f
            var chosenIndex = 0
            val axisNames = arrayOf("Roll", "Pitch", "Yaw")

            for (i in 0..2) {
                val delta = maxAngles[i] - minAngles[i]
                if (delta > maxDelta) {
                    maxDelta = delta
                    chosenIndex = i
                }
            }
            activeAxisIndex = chosenIndex

            // LÓGICA DE INVERSÃO: Verifica onde a TAG parou no final da calibração
            val minVal = minAngles[chosenIndex]
            val maxVal = maxAngles[chosenIndex]
            val currentVal = lastAngles[chosenIndex]

            // Se o repouso atual está mais próximo do Max do que do Min, invertemos a tela
            isInverted = (maxVal - currentVal) < (currentVal - minVal)

            dominantAxisName = "Eixo: ${axisNames[chosenIndex]} (Invertido: $isInverted)"

        } else {
            // INÍCIO DA CALIBRAÇÃO COM CONTAGEM REGRESSIVA
            viewModelScope.launch {
                isCountingDown = true
                for (i in 3 downTo 1) {
                    dominantAxisName = "Prepare-se: $i..."
                    delay(1000)
                }
                isCountingDown = false

                minAngles = floatArrayOf(1000f, 1000f, 1000f)
                maxAngles = floatArrayOf(-1000f, -1000f, -1000f)
                activeAxisIndex = null
                normalizedPosition = 0.0f
                dominantAxisName = "Calibrando (Faça 1 repetição)..."
                isCalibrating = true
            }
        }
    }

    fun processIncomingData(csvData: String) {
        val values = csvData.split(",")
        if (values.size == 10) {
            try {
                val currentAngles = floatArrayOf(
                    values[7].toFloat(), values[8].toFloat(), values[9].toFloat()
                )
                lastAngles = currentAngles

                if (isCalibrating) {
                    for (i in 0..2) {
                        if (currentAngles[i] < minAngles[i]) minAngles[i] = currentAngles[i]
                        if (currentAngles[i] > maxAngles[i]) maxAngles[i] = currentAngles[i]
                    }
                } else {
                    activeAxisIndex?.let { activeIdx ->
                        val minVal = minAngles[activeIdx]
                        val maxVal = maxAngles[activeIdx]
                        val currentVal = currentAngles[activeIdx]

                        if (maxVal - minVal > 0.1f) {
                            var rawNorm = (currentVal - minVal) / (maxVal - minVal)

                            // Aplica a inversão dinâmica detectada na calibração
                            if (isInverted) rawNorm = 1.0f - rawNorm

                            normalizedPosition = max(0.0f, kotlin.math.min(1.0f, rawNorm))
                        }
                    }
                }
            } catch (e: Exception) { }
        }
    }
}