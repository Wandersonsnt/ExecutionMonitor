package com.example.monitordeexecucao

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun MainScreen(viewModel: TagViewModel) {
    val animatedY by animateFloatAsState(
        targetValue = viewModel.normalizedPosition,
        animationSpec = tween(durationMillis = 80)
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = if (viewModel.isConnected) "Conectado" else "Aguardando TAG...",
            color = if (viewModel.isConnected) Color(0xFF00C853) else Color.Red,
            style = MaterialTheme.typography.titleLarge
        )

        Text(text = viewModel.dominantAxisName, color = Color.Gray)

        // TELA DO JOGO
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .clip(RoundedCornerShape(15.dp)) // CORREÇÃO 3: Corta as "rebarbas" da onda para fora da caixa
                .background(Color(0xFF2C3E50)),
            contentAlignment = Alignment.CenterStart
        ) {

            // CORREÇÃO 2: A onda só será desenhada e animada se estiver no estado de Tracking
            if (viewModel.isTracking) {
                val infiniteTransition = rememberInfiniteTransition()
                val wavePhase by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = (2f * PI).toFloat(),
                    animationSpec = infiniteRepeatable(
                        // CORREÇÃO 5: Injeta dinamicamente a velocidade capturada pelo ViewModel
                        animation = tween(viewModel.waveDurationMillis, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val amplitude = h * 0.35f
                    val centerY = h / 2f
                    val frequency = 1.5f

                    val path = Path()
                    for (x in 0..w.toInt() step 5) {
                        val normalizedX = x / w
                        // CORREÇÃO 4: O sinal POSITIVO (+ wavePhase) faz a onda se deslocar da direita para a esquerda
                        val angle = (normalizedX * 2 * PI * frequency) + wavePhase
                        val y = centerY - (sin(angle).toFloat() * amplitude)

                        if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y)
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFF3498DB).copy(alpha = 0.5f),
                        style = Stroke(width = 80f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            // A Bolinha do Usuário (Sempre visível para acompanhamento instantâneo)
            val ballSize = 40.dp
            val usableHeight = 350f - 40f
            val yOffset = ((1f - animatedY) * usableHeight) - (usableHeight / 2f)

            Box(
                modifier = Modifier
                    .offset(x = 40.dp, y = yOffset.dp)
                    .size(ballSize)
                    .background(Color(0xFFF1C40F), CircleShape)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.toggleCalibration() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (viewModel.isCalibrating) Color.Red
                else if (viewModel.isCountingDown) Color.Gray
                else Color(0xFF2980B9)
            ),
            modifier = Modifier.fillMaxWidth().height(60.dp),
            enabled = !viewModel.isCountingDown
        ) {
            Text(
                text = if (viewModel.isCalibrating) "Finalizar Calibração"
                else if (viewModel.isCountingDown) "Aguarde..."
                else "Iniciar Calibração",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}