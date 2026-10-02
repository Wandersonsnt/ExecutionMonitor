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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun MainScreen(viewModel: TagViewModel, bleManager: TagBleManager) {
    val animatedY by animateFloatAsState(
        targetValue = if (viewModel.isTracking) viewModel.normalizedPosition else 0f,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "ballPosition"
    )

    val isExercising = viewModel.exerciseState != ExerciseState.IDLE

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
                confirmButton = {
                    TextButton(onClick = { viewModel.showDeviceDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

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

        // Substituição do Box fixo por um BoxWithConstraints dinâmico com proporção de peso
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Força o quadro a ocupar apenas o espaço que sobrar livre na tela
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFF2C3E50)),
            contentAlignment = Alignment.CenterStart
        ) {

            // A onda só surge após a 1ª repetição invisível
            if (viewModel.isTracking) {
                val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
                val wavePhase by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = (2f * PI).toFloat(),
                    animationSpec = infiniteRepeatable(
                        animation = tween(viewModel.waveDurationMillis, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "wavePhase"
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
                        val angle = (normalizedX * 2 * PI * frequency) + wavePhase
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

            // A Bolinha do Usuário
            val ballSize = 40.dp
            // O cálculo da bolinha agora consulta dinamicamente a altura livre (maxHeight) que o Android entregou
            val usableHeight = maxHeight.value - 40f
            val yOffset = ((1f - animatedY) * usableHeight) - (usableHeight / 2f)

            Box(
                modifier = Modifier
                    .offset(x = 40.dp, y = yOffset.dp)
                    .size(ballSize)
                    .background(if (viewModel.isTracking) Color(0xFFF1C40F) else Color.Gray, CircleShape)
            )
        }

        // O 'Spacer(weight=1f)' foi apagado daqui, pois o BoxWithConstraints acima já faz esse trabalho

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