package com.vayunmathur.clock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import com.vayunmathur.clock.data.ClockDatabase
import com.vayunmathur.clock.data.Timer
import com.vayunmathur.clock.ui.AlarmPage
import com.vayunmathur.clock.ui.ClockPage
import com.vayunmathur.clock.ui.StopwatchPage
import com.vayunmathur.clock.ui.TimerPage
import com.vayunmathur.clock.ui.dialog.NewTimerDialog
import com.vayunmathur.clock.ui.dialog.SelectTimeZonesDialog
import com.vayunmathur.library.ui.DynamicTheme
import com.vayunmathur.library.ui.dialog.TimePickerDialogContent
import com.vayunmathur.library.util.BottomBarItem
import com.vayunmathur.library.util.DataStoreUtils
import com.vayunmathur.library.util.DatabaseViewModel
import com.vayunmathur.library.util.MainNavigation
import com.vayunmathur.library.util.buildDatabase
import com.vayunmathur.library.util.rememberNavBackStack
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val ds = DataStoreUtils.getInstance(this)
        val db = buildDatabase<ClockDatabase>()
        val viewModel = DatabaseViewModel(db, Timer::class to db.timerDao())
        setContent {
            DynamicTheme {
                Navigation(ds, viewModel)
            }
        }
    }
}

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Alarm: Route
    @Serializable
    data object Clock: Route
    @Serializable
    data object Timer: Route
    @Serializable
    data object Stopwatch: Route
    @Serializable
    data object SelectTimeZonesDialog: Route
    @Serializable
    data object NewTimerDialog: Route
}

val MAIN_PAGES = listOf(
    BottomBarItem("Alarm", Route.Alarm, R.drawable.baseline_access_alarm_24),
    BottomBarItem("Clock", Route.Clock, R.drawable.baseline_access_time_24),
    BottomBarItem("Timer", Route.Timer, R.drawable.baseline_hourglass_bottom_24),
    BottomBarItem("Stopwatch", Route.Stopwatch, R.drawable.outline_timer_24)
)

@Composable
fun Navigation(ds: DataStoreUtils, viewModel: DatabaseViewModel) {
    val backStack = rememberNavBackStack<Route>(Route.Alarm)
    MainNavigation(backStack) {
        entry<Route.Alarm> {
            AlarmPage(backStack)
        }
        entry<Route.Clock> {
            ClockPage(backStack, ds)
        }
        entry<Route.Timer> {
            TimerPage(backStack, viewModel)
        }
        entry<Route.Stopwatch> {
            StopwatchPage(backStack)
        }
        entry<Route.SelectTimeZonesDialog> {
            SelectTimeZonesDialog(backStack, ds)
        }
        entry<Route.NewTimerDialog> {
            NewTimerDialog(backStack, viewModel)
        }
    }
}