package com.example.monitordeexecucao

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(viewModel: TagViewModel) {
    // Animação suave para a bolinha não pular bruscamente
    val animatedY by animateFloatAsState(
        targetValue = viewModel.normalizedPosition,
        animationSpec = tween(durationMillis = 100)
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(30.dp)
    ) {
        Text(
            text = if (viewModel.isConnected) "Conectado" else "Aguardando TAG...",
            color = if (viewModel.isConnected) Color.Green else Color.Red,
            style = MaterialTheme.typography.titleLarge
        )

        Text(text = viewModel.dominantAxisName, color = Color.Gray)

        // A área da Trilha e da Bolinha
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(300.dp)
                .background(Color.Blue.copy(alpha = 0.2f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    // Multiplica a posição normalizada pela altura útil (300 - 40 = 260)
                    .offset(y = (-animatedY * 260).dp)
                    .background(Color.Yellow, CircleShape)
            )
        }

        Button(
            onClick = { viewModel.toggleCalibration() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (viewModel.isCalibrating) Color.Red else Color.Blue
            ),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (viewModel.isCalibrating) "Finalizar Calibração" else "Iniciar Calibração")
        }
    }
}