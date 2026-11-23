package com.jpntech.bizassistant

import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import com.jpntech.bizassistant.recordings.EXTRA_PENDING_ID
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
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

private const val MAX_CLIPBOARD_ASKS = 3

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Dest("today", "오늘", Icons.Outlined.CalendarToday),
    Dest("orgs", "거래처", Icons.Outlined.Business),
    Dest("tickets", "장애", Icons.Outlined.ReportProblem),
    Dest("settings", "설정", Icons.Outlined.Settings),
)

class MainActivity : ComponentActivity() {
    /** 창이 앞으로 올 때 읽은 클립보드 글. 통화 요약을 복사해 오면 오늘 화면이 먼저 제안한다. */
    private val clipboardText = mutableStateOf<String?>(null)
    /** 알림을 눌러 들어온 대기함 id. 화면이 뜨면 바로 확인 카드로 연다. */
    private val pendingToOpen = mutableStateOf<Long?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readPendingExtra(intent)
    }

    private fun readPendingExtra(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_PENDING_ID, -1L) ?: -1L
        if (id > 0) pendingToOpen.value = id
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull()
            ?.takeIf { it.trim().length >= 20 }
        clipboardText.value = if (text != null && shouldOfferClipboard(text)) text else null
    }

    /**
     * 같은 글을 되풀이해 묻지 않는다.
     * 이미 정리했거나 "아니요"를 누른 글은 다시 묻지 않고, 그냥 지나친 글도 최대 3번까지만 묻는다.
     */
    private fun shouldOfferClipboard(text: String): Boolean {
        val prefs = getSharedPreferences("clipboard-offer", MODE_PRIVATE)
        val hash = text.trim().hashCode().toString()
        val handled = prefs.getStringSet("handled", emptySet()) ?: emptySet()
        if (hash in handled) return false
        val lastHash = prefs.getString("lastHash", null)
        val count = if (lastHash == hash) prefs.getInt("askCount", 0) else 0
        if (count >= MAX_CLIPBOARD_ASKS) return false
        if (clipboardText.value == text) return true // 이미 화면에 떠 있는 제안은 횟수를 더하지 않는다
        prefs.edit().putString("lastHash", hash).putInt("askCount", count + 1).apply()
        return true
    }

    /** 정리했거나 거절한 글은 기억해 둔다. 최근 20개만 보관한다. */
    private fun markClipboardHandled() {
        val text = clipboardText.value ?: return
        val prefs = getSharedPreferences("clipboard-offer", MODE_PRIVATE)
        val handled = LinkedHashSet(prefs.getStringSet("handled", emptySet()) ?: emptySet())
        handled += text.trim().hashCode().toString()
        while (handled.size > 20) handled.remove(handled.first())
        prefs.edit().putStringSet("handled", handled).apply()
        clipboardText.value = null
    }

    private val confirmVm: ConfirmViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ConfirmViewModel(BizApp.from(this@MainActivity).repository) as T
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readPendingExtra(intent)
        setContent {
            val sizeClass = calculateWindowSizeClass(this)
            val expanded = sizeClass.widthSizeClass != WindowWidthSizeClass.Compact
            val clip by clipboardText
            val pending by pendingToOpen
            BizTheme { MainScaffold(expanded = expanded, confirmVm = confirmVm, clipboard = clip, onClipboardHandled = { markClipboardHandled() }, pendingToOpen = pending, onPendingOpened = { pendingToOpen.value = null }) }
        }
    }
}

@Composable
private fun MainScaffold(expanded: Boolean, confirmVm: ConfirmViewModel, clipboard: String?, onClipboardHandled: () -> Unit, pendingToOpen: Long?, onPendingOpened: () -> Unit) {
    val nav = rememberNavController()
    LaunchedEffect(pendingToOpen) {
        if (pendingToOpen != null) {
            confirmVm.openPending(pendingToOpen)
            nav.navigate("confirm")
            onPendingOpened()
        }
    }
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
            Column(Modifier.weight(1f)) { AppNavHost(nav, expanded, confirmVm, clipboard, onClipboardHandled) }
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
            Column(Modifier.fillMaxSize().padding(padding)) { AppNavHost(nav, expanded, confirmVm, clipboard, onClipboardHandled) }
        }
    }
}

/** 복사해 온 글이 통화 요약인지 문자인지 어림한다. 확인 카드에서 바꿀 수 있다. */
fun guessSource(text: String): String = when {
    text.contains("통화") || text.contains("녹음") || text.contains("요약") || text.lines().size >= 6 -> "call"
    else -> "sms"
}

private fun navTo(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo("today") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, expanded: Boolean, confirmVm: ConfirmViewModel, clipboard: String?, onClipboardHandled: () -> Unit) {
    NavHost(navController = nav, startDestination = "today") {
        composable("today") {
            TodayScreen(
                expanded = expanded,
                onManualInput = { nav.navigate("input") },
                onOpenTickets = { navTo(nav, "tickets") },
                clipboard = clipboard,
                onUseClipboard = { text ->
                    onClipboardHandled()
                    confirmVm.start(text, guessSource(text))
                    nav.navigate("confirm")
                },
                onDismissClipboard = onClipboardHandled,
                onOpenPending = { id -> confirmVm.openPending(id); nav.navigate("confirm") },
            )
        }
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
