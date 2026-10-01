package com.example.monitordeexecucao

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.math.max

class TagViewModel : ViewModel() {
    var isConnected by mutableStateOf(false)
    var isCalibrating by mutableStateOf(false)
    var normalizedPosition by mutableStateOf(0.0f) // Vai de 0.0 a 1.0
    var dominantAxisName by mutableStateOf("Aguardando Calibração...")

    private var minAngles = floatArrayOf(1000f, 1000f, 1000f) // Roll, Pitch, Yaw
    private var maxAngles = floatArrayOf(-1000f, -1000f, -1000f)
    private var activeAxisIndex: Int? = null

    fun toggleCalibration() {
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
            dominantAxisName = "Eixo: ${axisNames[chosenIndex]} (Delta: ${maxDelta.toInt()}°)"
        } else {
            // INÍCIO DA CALIBRAÇÃO
            minAngles = floatArrayOf(1000f, 1000f, 1000f)
            maxAngles = floatArrayOf(-1000f, -1000f, -1000f)
            activeAxisIndex = null
            normalizedPosition = 0.0f
            dominantAxisName = "Calibrando (Faça o movimento)..."
            isCalibrating = true
        }
    }

    // Função chamada toda vez que o Bluetooth recebe uma nova linha (ex: "0.1, 0.2, ...")
    fun processIncomingData(csvData: String) {
        val values = csvData.split(",")
        // Verifica se recebemos os 10 valores do Projeto LTM
        if (values.size == 10) {
            try {
                // Índices 7, 8 e 9 correspondem a Roll, Pitch e Yaw
                val currentAngles = floatArrayOf(
                    values[7].toFloat(),
                    values[8].toFloat(),
                    values[9].toFloat()
                )

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
                            val rawNorm = (currentVal - minVal) / (maxVal - minVal)
                            // Trava o valor entre 0 e 1 para a bolinha não sair da tela
                            normalizedPosition = max(0.0f, kotlin.math.min(1.0f, rawNorm))
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignora pacotes corrompidos durante a transmissão pelo ar
            }
        }
    }
}