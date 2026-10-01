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

    // CORREÇÃO 2: Controla quando a onda senoidal aparece
    var isTracking by mutableStateOf(false)

    // CORREÇÃO 5: Guarda o tempo da repetição para ditar a velocidade do jogo
    var waveDurationMillis by mutableStateOf(4000)

    var normalizedPosition by mutableStateOf(0.0f)
    var dominantAxisName by mutableStateOf("Aguardando Calibração...")

    private var minAngles = floatArrayOf(1000f, 1000f, 1000f)
    private var maxAngles = floatArrayOf(-1000f, -1000f, -1000f)
    private var lastAngles = floatArrayOf(0f, 0f, 0f)

    // Rastreadores de tempo (em milissegundos) para cada extremo
    private var minTime = longArrayOf(0, 0, 0)
    private var maxTime = longArrayOf(0, 0, 0)

    private var activeAxisIndex: Int? = null
    private var isInverted: Boolean = false

    fun toggleCalibration() {
        if (isCountingDown) return

        if (isCalibrating) {
            // FIM DA CALIBRAÇÃO
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

            val minVal = minAngles[chosenIndex]
            val maxVal = maxAngles[chosenIndex]
            val currentVal = lastAngles[chosenIndex]
            isInverted = (maxVal - currentVal) < (currentVal - minVal)

            // CORREÇÃO 5: Calcula o tempo gasto entre o pico mínimo e máximo
            val timeDiff = kotlin.math.abs(maxTime[chosenIndex] - minTime[chosenIndex])
            // O tempo do ciclo da onda é o dobro do tempo da meia-repetição (limitado entre 1s e 10s por segurança)
            waveDurationMillis = (timeDiff * 2).toInt().coerceIn(1000, 10000)

            // CORREÇÃO 1: Contagem regressiva AQUI, após finalizar a calibração
            viewModelScope.launch {
                isCountingDown = true
                for (i in 3 downTo 1) {
                    dominantAxisName = "Prepare-se: $i..."
                    delay(1000)
                }
                isCountingDown = false
                isTracking = true // Dispara o aparecimento da onda animada
                dominantAxisName = "Eixo: ${axisNames[chosenIndex]} | Ritmo: ${waveDurationMillis / 1000.0}s"
            }

        } else {
            // INÍCIO DA CALIBRAÇÃO
            minAngles = floatArrayOf(1000f, 1000f, 1000f)
            maxAngles = floatArrayOf(-1000f, -1000f, -1000f)
            minTime = longArrayOf(0, 0, 0)
            maxTime = longArrayOf(0, 0, 0)
            activeAxisIndex = null
            normalizedPosition = 0.0f
            isTracking = false // Esconde a onda enquanto calibra
            dominantAxisName = "Calibrando (Faça 1 repetição com calma)..."
            isCalibrating = true
        }
    }

    fun processIncomingData(csvData: String) {
        val values = csvData.split(",")
        // Recebe os 10 valores do Projeto LTM[cite: 2]
        if (values.size == 10) {
            try {
                val currentAngles = floatArrayOf(values[7].toFloat(), values[8].toFloat(), values[9].toFloat())
                lastAngles = currentAngles

                if (isCalibrating) {
                    val now = System.currentTimeMillis()
                    for (i in 0..2) {
                        if (currentAngles[i] < minAngles[i]) {
                            minAngles[i] = currentAngles[i]
                            minTime[i] = now // Registra o instante do vale
                        }
                        if (currentAngles[i] > maxAngles[i]) {
                            maxAngles[i] = currentAngles[i]
                            maxTime[i] = now // Registra o instante do pico
                        }
                    }
                } else {
                    activeAxisIndex?.let { activeIdx ->
                        val minVal = minAngles[activeIdx]
                        val maxVal = maxAngles[activeIdx]
                        val currentVal = currentAngles[activeIdx]

                        if (maxVal - minVal > 0.1f) {
                            var rawNorm = (currentVal - minVal) / (maxVal - minVal)
                            if (isInverted) rawNorm = 1.0f - rawNorm
                            normalizedPosition = max(0.0f, kotlin.math.min(1.0f, rawNorm))
                        }
                    }
                }
            } catch (e: Exception) { }
        }
    }
}