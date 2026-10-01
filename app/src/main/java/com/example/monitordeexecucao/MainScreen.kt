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
    // Suaviza o movimento vertical da bolinha (Recebido do Hardware)
    val animatedY by animateFloatAsState(
        targetValue = viewModel.normalizedPosition,
        animationSpec = tween(durationMillis = 80)
    )

    // Animação infinita para deslocar a onda do fundo (Gamificação)
    val infiniteTransition = rememberInfiniteTransition()
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
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
                .background(Color(0xFF2C3E50), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            // Desenha a Trilha (Onda Senoidal)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val amplitude = h * 0.35f // A onda ocupa 70% da altura da caixa
                val centerY = h / 2f
                val frequency = 1.5f // Quantidade de picos na tela

                val path = Path()
                for (x in 0..w.toInt() step 5) {
                    val normalizedX = x / w
                    // Calcula a onda com deslocamento contínuo (wavePhase)
                    val angle = (normalizedX * 2 * PI * frequency) - wavePhase
                    val y = centerY - (sin(angle).toFloat() * amplitude)

                    if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y)
                }

                drawPath(
                    path = path,
                    color = Color(0xFF3498DB).copy(alpha = 0.5f),
                    style = Stroke(width = 80f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // A Bolinha do Usuário
            val ballSize = 40.dp
            val usableHeight = 350f - 40f // Altura da caixa menos a bolinha

            // O centro da tela é 0. O topo é -usableHeight/2, a base é +usableHeight/2.
            val yOffset = ((1f - animatedY) * usableHeight) - (usableHeight / 2f)

            Box(
                modifier = Modifier
                    .offset(x = 40.dp, y = yOffset.dp) // Trava a bolinha no eixo X esquerdo
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