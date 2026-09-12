package com.example.wakeorwait

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.wakeorwait.ui.screens.AlarmHistoryScreen
import com.example.wakeorwait.ui.screens.HomeScreen
import com.example.wakeorwait.ui.screens.SetAlarmScreen
import com.example.wakeorwait.ui.screens.SettingsScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(HomeNavKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<HomeNavKey> {
                HomeScreen(
                    onNavigateToSetAlarm = { alarmId ->
                        backStack.add(SetAlarmNavKey(alarmId))
                    },
                    onNavigateToHistory = {
                        backStack.add(AlarmHistoryNavKey)
                    },
                    onNavigateToSettings = {
                        backStack.add(SettingsNavKey)
                    }
                )
            }
            entry<SetAlarmNavKey> { key ->
                SetAlarmScreen(
                    alarmIdToEdit = key.alarmId,
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<AlarmHistoryNavKey> {
                AlarmHistoryScreen(
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<SettingsNavKey> {
                SettingsScreen(
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
        }
    )
}
