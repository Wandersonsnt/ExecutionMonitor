package com.example.monitordeexecucao

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ======================================================================
// CONTROLE DE SINCRONISMO
// ======================================================================
private const val FREQUENCIA_ONDA = 1.5f
private const val TOLERANCIA_DESVIO = 0.3f
private const val VELOCIDADE_MIN = 0.0f
private const val VELOCIDADE_MAX = 2.5f

// ======================================================================
// CONFIGURAÇÕES DA GAMIFICAÇÃO (PONTUAÇÃO E LIMITES)
// ======================================================================
private const val PONTO_PICO_Y = 1.0f   // Exige chegar a 90% da amplitude superior
private const val PONTO_VALE_Y = 0.00f   // Exige chegar a 10% da amplitude inferior
private const val PONTO_MEIO_Y = 0.50f   // Ponto central de transição
private const val MARGEM_ACERTO = 0.15f  // Tolerância de 15% (acima ou abaixo) para coletar
private const val PONTOS_POR_ACERTO = 10 // Quantidade de pontos ganhos por cada acerto
// ======================================================================

@Composable
fun MainScreen(viewModel: TagViewModel, bleManager: TagBleManager) {
    // animatedY suaviza o pulo mecânico e entrega fluidez visual
    val animatedY by animateFloatAsState(
        targetValue = if (viewModel.isTracking) viewModel.normalizedPosition else 0f,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "ballPosition"
    )

    val isExercising = viewModel.exerciseState != ExerciseState.IDLE
    val density = LocalDensity.current

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (viewModel.isConnected) "Conectado" else "Desconectado",
                color = if (viewModel.isConnected) Color(0xFF00C853) else Color.Red,
                style = MaterialTheme.typography.titleLarge
            )

            Button(onClick = {
                bleManager.startScan()
                viewModel.showDeviceDialog = true
            }) {
                Text(if (viewModel.isConnected) "Trocar TAG" else "Buscar TAG")
            }
        }

        if (viewModel.showDeviceDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.showDeviceDialog = false },
                title = { Text("Tags Encontradas") },
                text = {
                    if (viewModel.scannedDevices.isEmpty()) {
                        Text("Buscando...", modifier = Modifier.padding(16.dp))
                    } else {
                        LazyColumn {
                            items(viewModel.scannedDevices) { device ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            bleManager.connectToAddress(device.address)
                                            viewModel.showDeviceDialog = false
                                        }
                                        .padding(16.dp)
                                ) {
                                    Text(device.name, fontWeight = FontWeight.Bold)
                                    Text(device.address, fontSize = 12.sp, color = Color.Gray)
                                }
                                Divider()
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { viewModel.showDeviceDialog = false }) { Text("Cancelar") } }
            )
        }

        // --- PAINEL DE PONTUAÇÃO (GAMIFICAÇÃO) ---
        if (isExercising) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = "REPS: ${viewModel.repCount}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2980B9)
                )
                Text(
                    text = "PTS: ${viewModel.pontuacao}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF27AE60)
                )
            }
        } else {
            Spacer(modifier = Modifier.height(40.dp))
        }

        Text(text = viewModel.dominantAxisName, color = Color.Gray)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFF2C3E50)),
            contentAlignment = Alignment.CenterStart
        ) {
            val maxWidthPx = with(density) { maxWidth.toPx() }

            // O centro da bolinha está em 40.dp (offset do box) + 20.dp (raio) = 60.dp
            val ballCenterXPx = with(density) { 60.dp.toPx() }
            val normalizedX = if (maxWidthPx > 0) ballCenterXPx / maxWidthPx else 0f

            var wavePhase by remember { mutableFloatStateOf(0f) }
            var lastCheckedK by remember { mutableIntStateOf(0) }

            LaunchedEffect(viewModel.isTracking, viewModel.waveDurationMillis, maxWidthPx) {
                if (viewModel.isTracking && maxWidthPx > 0f) {
                    var lastFrame = withFrameNanos { it }

                    // Inicializa o verificador de colisão garantindo que só contará as bolinhas futuras
                    val initialBallAngle = (normalizedX * 2.0 * Math.PI * FREQUENCIA_ONDA) + wavePhase
                    lastCheckedK = Math.floor(initialBallAngle / (Math.PI / 2)).toInt()

                    while (isActive) {
                        val currentFrame = withFrameNanos { it }
                        val dtMillis = (currentFrame - lastFrame) / 1_000_000f
                        lastFrame = currentFrame

                        val baseAngle = (normalizedX * 2.0 * Math.PI * FREQUENCIA_ONDA).toFloat()
                        val currentAngle = baseAngle + wavePhase
                        // A onda desenha com +sin para baixo, logo -sin é para cima.
                        // Então a "Altura" normalizada da onda para comparação é:
                        val waveNormY = 0.5f - 0.5f * sin(currentAngle)

                        // Derivada matemática da altura. Se for > 0, a onda está subindo fisicamente.
                        val waveDerivative = -0.5f * cos(currentAngle)
                        val isWaveGoingUp = waveDerivative > 0f

                        val signedError = animatedY - waveNormY
                        // Se a onda sobe e o erro é negativo (Y da bolinha menor), a bolinha está acima (adiantada).
                        // Se a onda desce e o erro é positivo (Y da bolinha maior), a bolinha está abaixo (adiantada).
                        val isBallAhead = if (isWaveGoingUp) signedError > 0f else signedError < 0f
                        val absError = kotlin.math.abs(signedError)

                        var speedMult = 1f
                        if (isBallAhead) {
                            // Bolinha fugiu: Onda ACELERA para alcançar
                            speedMult = 1f + (absError / TOLERANCIA_DESVIO) * (VELOCIDADE_MAX - 1f)
                            speedMult = speedMult.coerceAtMost(VELOCIDADE_MAX)
                        } else {
                            // Bolinha atrasou: Onda FREIA para esperar
                            speedMult = 1f - (absError / TOLERANCIA_DESVIO)
                            speedMult = speedMult.coerceAtLeast(VELOCIDADE_MIN)
                        }

                        val baseDuration = viewModel.waveDurationMillis.toFloat()
                        val phaseIncrement = if (baseDuration > 0) (dtMillis / baseDuration) * (2f * Math.PI.toFloat()) else 0f

                        // Incrementa a onda infinitamente
                        wavePhase += phaseIncrement * speedMult

                        // ========================================================
                        // MOTOR DE COLISÃO E PONTUAÇÃO
                        // ========================================================
                        val ballAngle = (normalizedX * 2.0 * Math.PI * FREQUENCIA_ONDA) + wavePhase
                        val currentK = Math.floor(ballAngle / (Math.PI / 2)).toInt()

                        // Se cruzamos um ângulo múltiplo de 90 graus (k), temos um coletável!
                        if (currentK > lastCheckedK) {
                            for (k in (lastCheckedK + 1)..currentK) {
                                // O módulo 4 nos diz em qual fase da onda estamos
                                val mod = (k % 4 + 4) % 4
                                val targetY = when(mod) {
                                    1 -> PONTO_VALE_Y
                                    3 -> PONTO_PICO_Y
                                    else -> PONTO_MEIO_Y
                                }

                                // Verifica se o usuário chegou na altura configurada
                                if (Math.abs(animatedY - targetY) <= MARGEM_ACERTO) {
                                    viewModel.dotStates[k] = DotStatus.HIT
                                    viewModel.pontuacao += PONTOS_POR_ACERTO
                                } else {
                                    viewModel.dotStates[k] = DotStatus.MISSED
                                }
                            }
                            lastCheckedK = currentK
                        }
                    }
                } else {
                    wavePhase = 0f
                }
            }

            if (viewModel.isTracking) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    val ballSizePx = 40.dp.toPx()
                    val usableHeight = h - ballSizePx
                    val amplitude = usableHeight / 2f
                    val centerY = h / 2f

                    // 1. Desenha a Trilha Senoidal
                    val path = Path()
                    for (x in 0..w.toInt() step 5) {
                        val normX = x / w
                        val angle = (normX * 2 * PI * FREQUENCIA_ONDA) + wavePhase
                        val y = centerY + (sin(angle).toFloat() * amplitude)

                        if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y)
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFF3498DB).copy(alpha = 0.5f),
                        style = Stroke(width = 80f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // 2. Desenha os "Coletáveis" (Dots)
                    // Calcula quais pontos estão visíveis na tela no momento
                    val minK = Math.floor(wavePhase / (Math.PI / 2)).toInt()
                    val maxK = Math.ceil((wavePhase + 2 * Math.PI * FREQUENCIA_ONDA) / (Math.PI / 2)).toInt()

                    for (k in minK..maxK) {
                        val dotAngle = k * (Math.PI / 2).toFloat()
                        val dotNormX = (dotAngle - wavePhase) / (2 * Math.PI * FREQUENCIA_ONDA).toFloat()
                        val cx = dotNormX * w

                        val mod = (k % 4 + 4) % 4
                        val targetY = when(mod) {
                            1 -> PONTO_VALE_Y
                            3 -> PONTO_PICO_Y
                            else -> PONTO_MEIO_Y
                        }

                        // Coloca a bolinha exatamente no limite (targetY) visual da tela
                        val cy = (1f - targetY) * usableHeight + (ballSizePx / 2f)

                        val status = viewModel.dotStates[k] ?: DotStatus.IDLE
                        val dotColor = when(status) {
                            DotStatus.IDLE -> Color.White
                            DotStatus.HIT -> Color.Green
                            DotStatus.MISSED -> Color.Red
                        }

                        // A bolinha brilha ligeiramente maior se ainda não foi cruzada
                        drawCircle(
                            color = dotColor,
                            radius = if (status == DotStatus.IDLE) 12f else 18f,
                            center = Offset(cx, cy)
                        )
                    }
                }
            }

            val ballSize = 40.dp
            val usableHeightDp = maxHeight.value - 40f
            val yOffsetDp = ((1f - animatedY) * usableHeightDp) - (usableHeightDp / 2f)

            Box(
                modifier = Modifier
                    .offset(x = 40.dp, y = yOffsetDp.dp)
                    .size(ballSize)
                    .background(if (viewModel.isTracking) Color(0xFFF1C40F) else Color.Gray, CircleShape)
            )
        }

        Button(
            onClick = { viewModel.toggleExercise() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isExercising) Color.Red else Color(0xFF2980B9)
            ),
            modifier = Modifier.fillMaxWidth().height(60.dp),
            enabled = viewModel.isConnected
        ) {
            Text(
                text = if (isExercising) "Finalizar Treino" else "Iniciar Treino",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}