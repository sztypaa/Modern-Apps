package com.vayunmathur.clock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vayunmathur.library.ui.DynamicTheme

class AlarmActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // For modern Android to show over lockscreen
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            DynamicTheme {
                Box(Modifier.fillMaxSize()) {
                    Text("ALARM", Modifier.align(Alignment.Center))
                }
            }
        }
    }
}