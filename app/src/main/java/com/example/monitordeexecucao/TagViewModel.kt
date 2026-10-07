package com.example.monitordeexecucao // Mantenha o seu pacote!

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import kotlin.math.max

data class BleDevice(val name: String, val address: String)

enum class DotStatus { IDLE, HIT, MISSED }
enum class ExerciseState {
    IDLE, ADVANCING_REP1, RETURNING_REP1, TRACKING
}

class TagViewModel : ViewModel() {
    var isConnected by mutableStateOf(false)
    var exerciseState by mutableStateOf(ExerciseState.IDLE)

    // Controla a exibição da onda e da bolinha
    var isTracking by mutableStateOf(false)
    var waveDurationMillis by mutableStateOf(4000)

    var normalizedPosition by mutableStateOf(0.0f)
    var dominantAxisName by mutableStateOf("Aguardando início...")

    // Contadores de Gamificação
    var repCount by mutableStateOf(0)
    var pontuacao by mutableStateOf(0)
    val dotStates = mutableStateMapOf<Int, DotStatus>()

    private var minAngles = floatArrayOf(1000f, 1000f, 1000f)
    private var maxAngles = floatArrayOf(-1000f, -1000f, -1000f)
    private var lastAngles = floatArrayOf(0f, 0f, 0f)
    private var startAngles = floatArrayOf(0f, 0f, 0f)
    private var peakAngles = floatArrayOf(0f, 0f, 0f)

    // Rastreadores de amplitude para detectar reversão
    private var maxDistanceFromStart = 0f
    private var maxDistanceFromPeak = 0f
    private var returnStartTime = 0L

    private var activeAxisIndex: Int? = null
    private var isInverted: Boolean = false
    private var isAtPeak = false

    // Histerese: 8 graus de recuo confirmam que o usuário mudou a direção do peso
    private val REVERSAL_THRESHOLD = 8.0f

    fun toggleExercise() {
        if (exerciseState == ExerciseState.IDLE) {
            // INÍCIO DO TREINO (Calibração Oculta)
            exerciseState = ExerciseState.ADVANCING_REP1
            minAngles = floatArrayOf(1000f, 1000f, 1000f)
            maxAngles = floatArrayOf(-1000f, -1000f, -1000f)
            startAngles = lastAngles.copyOf()
            maxDistanceFromStart = 0f
            maxDistanceFromPeak = 0f
            activeAxisIndex = null
            normalizedPosition = 0.0f
            isTracking = false

            // Zera os contadores e limpa as bolinhas coletadas do treino anterior
            repCount = 0
            pontuacao = 0
            dotStates.clear()

            isAtPeak = false
            dominantAxisName = "Faça a 1ª repetição (Avanço)..."
        } else {
            // FIM DO TREINO
            exerciseState = ExerciseState.IDLE
            isTracking = false
            dominantAxisName = "Treino Finalizado. Repetições: $repCount"
        }
    }

    // NOVAS VARIÁVEIS DO MENU BLUETOOTH
    var showDeviceDialog by mutableStateOf(false)
    val scannedDevices = androidx.compose.runtime.mutableStateListOf<BleDevice>()

    fun addScannedDevice(name: String, address: String) {
        if (scannedDevices.none { it.address == address }) {
            scannedDevices.add(BleDevice(name, address))
        }
    }

    fun processIncomingData(csvData: String) {
        val values = csvData.split(",")
        // Valida se o pacote contém Posição + Quatérnio + Euler[cite: 2]
        if (values.size == 10) {
            try {
                val currentAngles = floatArrayOf(
                    values[7].toFloat(), values[8].toFloat(), values[9].toFloat()
                )
                lastAngles = currentAngles

                if (exerciseState == ExerciseState.IDLE) return

                // 1. Atualiza os limites de amplitude o tempo todo
                for (i in 0..2) {
                    if (currentAngles[i] < minAngles[i]) minAngles[i] = currentAngles[i]
                    if (currentAngles[i] > maxAngles[i]) maxAngles[i] = currentAngles[i]
                }

                // 2. Elege o eixo ativamente logo no início do movimento
                if (activeAxisIndex == null) {
                    var maxDelta = 0.0f
                    var chosenIndex = -1
                    for (i in 0..2) {
                        val delta = maxAngles[i] - minAngles[i]
                        if (delta > maxDelta) {
                            maxDelta = delta
                            chosenIndex = i
                        }
                    }
                    // Trava o eixo assim que um deslocamento considerável (15º) é detectado
                    if (chosenIndex != -1 && maxDelta > 15.0f) {
                        activeAxisIndex = chosenIndex
                    }
                }

                activeAxisIndex?.let { activeIdx ->
                    val currentVal = currentAngles[activeIdx]

                    when (exerciseState) {
                        ExerciseState.ADVANCING_REP1 -> {
                            val startVal = startAngles[activeIdx]
                            val dist = kotlin.math.abs(currentVal - startVal)

                            if (dist > maxDistanceFromStart) {
                                maxDistanceFromStart = dist
                                peakAngles = currentAngles.copyOf()
                            } else if (maxDistanceFromStart > 15.0f && dist < maxDistanceFromStart - REVERSAL_THRESHOLD) {
                                // DETECÇÃO: Chegou na extensão máxima do exercício e começou a voltar
                                exerciseState = ExerciseState.RETURNING_REP1
                                returnStartTime = System.currentTimeMillis()
                                maxDistanceFromPeak = 0f
                                dominantAxisName = "Retornando (Termine a repetição)..."

                                // Lógica de inversão automática
                                isInverted = startAngles[activeIdx] > peakAngles[activeIdx]
                            }
                        }
                        ExerciseState.RETURNING_REP1 -> {
                            val peakVal = peakAngles[activeIdx]
                            val dist = kotlin.math.abs(currentVal - peakVal)

                            if (dist > maxDistanceFromPeak) {
                                maxDistanceFromPeak = dist
                            } else if (dist < maxDistanceFromPeak - REVERSAL_THRESHOLD) {
                                // DETECÇÃO: Chegou na posição inicial! Início da 2ª Repetição.
                                exerciseState = ExerciseState.TRACKING
                                val returnEndTime = System.currentTimeMillis()

                                // Define o ritmo do jogo com o dobro do tempo do retorno
                                waveDurationMillis = ((returnEndTime - returnStartTime) * 2).toInt().coerceIn(1000, 10000)

                                repCount = 1 // Conta a primeira e inicia a segunda
                                isTracking = true
                                isAtPeak = false

                                val axisNames = arrayOf("Roll", "Pitch", "Yaw")
                                dominantAxisName = "Eixo: ${axisNames[activeIdx]} | Ritmo: ${waveDurationMillis / 1000.0}s"
                            }
                        }
                        ExerciseState.TRACKING -> {
                            val minVal = minAngles[activeIdx]
                            val maxVal = maxAngles[activeIdx]

                            if (maxVal - minVal > 0.1f) {
                                var rawNorm = (currentVal - minVal) / (maxVal - minVal)
                                if (isInverted) rawNorm = 1.0f - rawNorm
                                normalizedPosition = max(0.0f, kotlin.math.min(1.0f, rawNorm))

                                // Contador contínuo de repetições (Passou de 80% do topo e voltou para 20% da base)
                                if (normalizedPosition > 0.80f) isAtPeak = true
                                if (isAtPeak && normalizedPosition < 0.20f) {
                                    repCount++
                                    isAtPeak = false
                                }
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) { }
        }
    }
}