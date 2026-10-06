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
// PARÂMETROS CONFIGURÁVEIS DO JOGO (CONTROLE DE SINCRONISMO)
// ======================================================================
private const val FREQUENCIA_ONDA = 1.0f    // Quantidade de "picos" visíveis na tela
private const val TOLERANCIA_DESVIO = 0.3f  // Margem de erro (0 a 1) para atingir velocidade mínima/máxima
private const val VELOCIDADE_MIN = 0.0f     // 0.0x = A onda para completamente para esperar a bolinha
private const val VELOCIDADE_MAX = 2.5f     // 2.5x = A onda acelera para tentar alcançar a bolinha
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

        // --- CABEÇALHO BLUETOOTH E STATUS ---
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
                confirmButton = {
                    TextButton(onClick = { viewModel.showDeviceDialog = false }) { Text("Cancelar") }
                }
            )
        }

        // --- PAINEL DE INFORMAÇÕES DO EXERCÍCIO ---
        if (isExercising) {
            Text(
                text = "REPS: ${viewModel.repCount}",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2980B9)
            )
        } else {
            Spacer(modifier = Modifier.height(56.dp))
        }

        Text(text = viewModel.dominantAxisName, color = Color.Gray)

        // --- TELA DE GAMIFICAÇÃO (MOTOR DE FÍSICA) ---
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFF2C3E50)),
            contentAlignment = Alignment.CenterStart
        ) {
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val ballXPx = with(density) { 40.dp.toPx() }
            val normalizedX = if (maxWidthPx > 0) ballXPx / maxWidthPx else 0f

            var wavePhase by remember { mutableFloatStateOf(0f) }

            // Lógica de Malha Fechada: Sincroniza a onda com a bolinha em tempo real
            LaunchedEffect(viewModel.isTracking, viewModel.waveDurationMillis, maxWidthPx) {
                if (viewModel.isTracking && maxWidthPx > 0f) {
                    var lastFrame = withFrameNanos { it }
                    while (isActive) {
                        val currentFrame = withFrameNanos { it }
                        val dtMillis = (currentFrame - lastFrame) / 1_000_000f
                        lastFrame = currentFrame

                        val baseAngle = (normalizedX * 2.0 * Math.PI * FREQUENCIA_ONDA).toFloat()
                        val currentAngle = baseAngle + wavePhase

                        // Descobre a posição Y teórica da onda onde a bolinha está
                        val waveNormY = 0.5f - 0.5f * sin(currentAngle)

                        // Derivada matemática: Se for > 0, a onda está subindo na tela
                        val waveDerivative = -0.5f * cos(currentAngle)
                        val isWaveGoingUp = waveDerivative > 0f

                        // Calcula a diferença e define se o usuário está Adiantado ou Atrasado
                        val signedError = animatedY - waveNormY
                        val isBallAhead = if (isWaveGoingUp) signedError > 0f else signedError < 0f
                        val absError = kotlin.math.abs(signedError)

                        // Modula a velocidade da onda de forma proporcional ao erro
                        var speedMult = 1f
                        if (isBallAhead) {
                            speedMult = 1f + (absError / TOLERANCIA_DESVIO) * (VELOCIDADE_MAX - 1f)
                            speedMult = speedMult.coerceAtMost(VELOCIDADE_MAX)
                        } else {
                            speedMult = 1f - (absError / TOLERANCIA_DESVIO)
                            speedMult = speedMult.coerceAtLeast(VELOCIDADE_MIN)
                        }

                        // Avança a animação
                        val baseDuration = viewModel.waveDurationMillis.toFloat()
                        val phaseIncrement = if (baseDuration > 0) (dtMillis / baseDuration) * (2f * Math.PI.toFloat()) else 0f

                        wavePhase = (wavePhase + phaseIncrement * speedMult) % (2f * Math.PI.toFloat())
                    }
                } else {
                    wavePhase = 0f
                }
            }

            // Renderização visual ajustada para 100% da caixa
            if (viewModel.isTracking) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // CORREÇÃO: A amplitude preenche exatamente o espaço até as bordas
                    val ballSizePx = 40.dp.toPx()
                    val usableHeight = h - ballSizePx
                    val amplitude = usableHeight / 2f
                    val centerY = h / 2f

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
                }
            }

            // Posição final da bolinha
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