package com.bambuprinterlan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.bambuprinterlan.app.ui.CommandBar
import com.bambuprinterlan.app.ui.FidgetLabScreen
import com.bambuprinterlan.app.ui.HahaGate
import com.bambuprinterlan.app.ui.Onboarding
import com.bambuprinterlan.app.ui.OnboardingScreen
import com.bambuprinterlan.app.ui.StartupIntro
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bambuprinterlan.app.nav.Dest
import com.bambuprinterlan.app.ui.AssistantScreen
import com.bambuprinterlan.app.ui.AutoFixScreen
import com.bambuprinterlan.app.ui.CalibrationScreen
import com.bambuprinterlan.app.ui.FilamentScreen
import com.bambuprinterlan.app.ui.HistoryScreen
import com.bambuprinterlan.app.ui.ModelEditScreen
import com.bambuprinterlan.app.ui.BatchSenderScreen
import com.bambuprinterlan.app.ui.DeviceScreen
import com.bambuprinterlan.app.ui.FileHubScreen
import com.bambuprinterlan.app.ui.PrepareScreen
import com.bambuprinterlan.app.ui.PreviewScreen
import com.bambuprinterlan.app.ui.SettingsScreen
import com.bambuprinterlan.app.ui.ToolsScreen
import com.bambuprinterlan.core.design.BambuPrinterLanTheme

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ModelEditStore.init(applicationContext)
        PrintHistoryStore.init(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // Startup important-event notification (gated by discord_notify_startup / HA).
        lifecycleScope.launch {
            EventNotifier.fire(
                applicationContext, "BambuPrinterLan started  已啟動", "",
                "startup", com.bambuprinterlan.net.bambu.integrations.DiscordClient.Category.STARTUP,
            )
        }
        setContent {
            BambuPrinterLanTheme {
                var introDone by rememberSaveable { mutableStateOf(false) }
                var showOnboarding by rememberSaveable {
                    mutableStateOf(Onboarding.shouldShow(applicationContext))
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        BambuPrinterLanApp()
                        HahaGate()
                        if (!introDone) StartupIntro(onDone = { introDone = true })
                        if (showOnboarding) OnboardingScreen(onDone = {
                            Onboarding.markSeen(applicationContext)
                            showOnboarding = false
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun BambuPrinterLanApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        topBar = { CommandBar() },
        bottomBar = {
            NavigationBar {
                Dest.bottomBar.forEach { dest ->
                    val selected = current?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label.en) },
                        // Bilingual nav label: English over Cantonese.
                        label = { Text(dest.label.inline, textAlign = TextAlign.Center) },
                    )
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = Dest.Prepare.route,
            modifier = Modifier.fillMaxSize().padding(inner),
        ) {
            composable(Dest.Prepare.route) {
                PrepareScreen(
                    onOpenHub = { navController.navigate("filehub") },
                    onOpenPreview = { navController.navigate(Dest.Preview.route) },
                    onOpenEditor = { navController.navigate("modeledit") },
                )
            }
            composable(Dest.Preview.route) {
                PreviewScreen(onOpenDevice = { navController.navigate(Dest.Device.route) })
            }
            composable(Dest.Device.route) { DeviceScreen() }
            composable(Dest.Tools.route) {
                ToolsScreen(
                    onOpenBatch = { navController.navigate("batch") },
                    onOpenFidget = { navController.navigate("fidget") },
                    onOpenAssistant = { navController.navigate("assistant") },
                    onOpenAutoFix = { navController.navigate("autofix") },
                    onOpenModelEdit = { navController.navigate("modeledit") },
                    onOpenFilament = { navController.navigate("filament") },
                    onOpenCalibration = { navController.navigate("calibration") },
                    onOpenHistory = { navController.navigate("history") },
                )
            }
            composable("fidget") {
                FidgetLabScreen(onBack = { navController.popBackStack() })
            }
            composable("assistant") {
                AssistantScreen(onBack = { navController.popBackStack() })
            }
            composable("modeledit") {
                ModelEditScreen(onBack = { navController.popBackStack() })
            }
            composable("filament") {
                FilamentScreen(onBack = { navController.popBackStack() })
            }
            composable("calibration") {
                CalibrationScreen(onBack = { navController.popBackStack() })
            }
            composable("history") {
                HistoryScreen(onBack = { navController.popBackStack() })
            }
            composable("autofix") {
                AutoFixScreen(onBack = { navController.popBackStack() })
            }
            composable(Dest.Settings.route) { SettingsScreen() }
            composable("batch") {
                BatchSenderScreen(onBack = { navController.popBackStack() })
            }
            composable("filehub") {
                FileHubScreen(
                    onBack = { navController.popBackStack() },
                    onImport = {
                        navController.navigate(Dest.Prepare.route) {
                            popUpTo(Dest.Prepare.route) { inclusive = true }
                        }
                    },
                    onSettings = {
                        navController.navigate(Dest.Settings.route) { launchSingleTop = true }
                    },
                )
            }
        }
    }
}
