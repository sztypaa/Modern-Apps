package com.vayunmathur.clock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import com.vayunmathur.clock.MAIN_PAGES
import com.vayunmathur.clock.Route
import com.vayunmathur.clock.data.Timer
import com.vayunmathur.library.ui.IconAdd
import com.vayunmathur.library.ui.IconDelete
import com.vayunmathur.library.ui.IconPause
import com.vayunmathur.library.ui.IconPlay
import com.vayunmathur.library.ui.ListPage
import com.vayunmathur.library.util.BottomNavBar
import com.vayunmathur.library.util.DatabaseViewModel
import com.vayunmathur.library.util.nowState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerPage(backStack: NavBackStack<Route>, viewModel: DatabaseViewModel) {
    val now by nowState()
    val timers by viewModel.data<Timer>().collectAsState()
    Scaffold(topBar = {
        TopAppBar({Text("Timer")})
    }, bottomBar = {
        BottomNavBar(backStack, MAIN_PAGES, Route.Timer)
    }, floatingActionButton = {
        FloatingActionButton({
            backStack.add(Route.NewTimerDialog)
        }) {
            IconAdd()
        }
    }) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(timers) {timer ->
                    TimerCard(timer, now, viewModel)
                }
            }
        }
    }
}

@Composable
fun TimerCard(timer: Timer, now: Instant, viewModel: DatabaseViewModel) {
    // Calculate actual remaining time for the UI
    val realRemainingTime = if (timer.isRunning) {
        timer.remainingLength - (now - timer.remainingStartTime)
    } else {
        timer.remainingLength
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- Left: Circular Progress & Time ---
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                // Circular Track
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color.Gray, style = Stroke(4f), alpha = 0.2f)
                }
                // Progress Arc
                val sweep = (realRemainingTime.inWholeMilliseconds.toFloat() /
                        timer.totalLength.inWholeMilliseconds.toFloat()) * 360f
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(
                        color = Color.LightGray,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(timer.name, style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = formatDuration(realRemainingTime),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // --- Right: Controls ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                FilledTonalButton(
                    onClick = {
                        viewModel.delete(timer)
                    }
                ) {
                    IconDelete()
                }
                Row {
                    // +1:00 Button
                    FilledTonalButton(onClick = {
                        viewModel.upsert(timer.copy(remainingLength = timer.remainingLength + 1.minutes))
                    }) {
                        Text("+ 1:00")
                    }
                    Spacer(Modifier.width(8.dp))

                    // Start/Stop Toggle
                    FilledTonalButton(
                        onClick = {
                            if (timer.isRunning) {
                                viewModel.upsert(timer.stopped())
                            } else {
                                viewModel.upsert(timer.started())
                            }
                        }
                    ) {
                        if (timer.isRunning) IconPause() else IconPlay()
                    }
                }
            }
        }
    }
}