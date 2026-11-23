package com.jpntech.bizassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jpntech.bizassistant.ui.confirm.ConfirmScreen
import com.jpntech.bizassistant.ui.confirm.ConfirmViewModel
import com.jpntech.bizassistant.ui.input.ManualInputScreen
import com.jpntech.bizassistant.ui.orgs.OrgsScreen
import com.jpntech.bizassistant.ui.settings.SettingsScreen
import com.jpntech.bizassistant.ui.theme.BizTheme
import com.jpntech.bizassistant.ui.tickets.TicketsScreen
import com.jpntech.bizassistant.ui.today.TodayScreen

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Dest("today", "오늘", Icons.Outlined.CalendarToday),
    Dest("orgs", "거래처", Icons.Outlined.Business),
    Dest("tickets", "장애", Icons.Outlined.ReportProblem),
    Dest("settings", "설정", Icons.Outlined.Settings),
)

class MainActivity : ComponentActivity() {
    private val confirmVm: ConfirmViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ConfirmViewModel(BizApp.from(this@MainActivity).repository) as T
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val sizeClass = calculateWindowSizeClass(this)
            val expanded = sizeClass.widthSizeClass != WindowWidthSizeClass.Compact
            BizTheme { MainScaffold(expanded = expanded, confirmVm = confirmVm) }
        }
    }
}

@Composable
private fun MainScaffold(expanded: Boolean, confirmVm: ConfirmViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showChrome = current in destinations.map { it.route }

    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            if (showChrome) {
                NavigationRail {
                    destinations.forEach { d ->
                        NavigationRailItem(selected = current == d.route, onClick = { navTo(nav, d.route) }, icon = { Icon(d.icon, contentDescription = d.label) }, label = { Text(d.label) })
                    }
                }
            }
            Column(Modifier.weight(1f)) { AppNavHost(nav, expanded, confirmVm) }
        }
    } else {
        Scaffold(
            bottomBar = {
                if (showChrome) {
                    NavigationBar {
                        destinations.forEach { d ->
                            NavigationBarItem(selected = current == d.route, onClick = { navTo(nav, d.route) }, icon = { Icon(d.icon, contentDescription = d.label) }, label = { Text(d.label) })
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) { AppNavHost(nav, expanded, confirmVm) }
        }
    }
}

private fun navTo(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo("today") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, expanded: Boolean, confirmVm: ConfirmViewModel) {
    NavHost(navController = nav, startDestination = "today") {
        composable("today") { TodayScreen(expanded = expanded, onManualInput = { nav.navigate("input") }, onOpenTickets = { navTo(nav, "tickets") }) }
        composable("orgs") { OrgsScreen(expanded = expanded) }
        composable("tickets") { TicketsScreen(expanded = expanded) }
        composable("settings") { SettingsScreen() }
        composable("input") {
            ManualInputScreen(onAnalyze = { text, source ->
                confirmVm.start(text, source)
                nav.navigate("confirm")
            }, onBack = { nav.popBackStack() })
        }
        composable("confirm") {
            ConfirmScreen(vm = confirmVm, onFinished = {
                confirmVm.reset()
                nav.popBackStack("today", inclusive = false)
            })
        }
    }
}
