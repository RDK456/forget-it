package app.forgetit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.forgetit.AppContainer
import app.forgetit.data.OwnerType
import app.forgetit.ui.calendar.CalendarScreen
import app.forgetit.ui.edit.EditScreen
import app.forgetit.ui.insights.InsightsScreen
import app.forgetit.ui.settings.SettingsScreen
import app.forgetit.ui.photos.PhotoSection
import app.forgetit.ui.edit.EditViewModel
import app.forgetit.ui.overview.OverviewScreen
import app.forgetit.ui.subscriptions.SubscriptionsScreen
import java.time.LocalDate

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("overview", "Overview", Icons.Filled.Home),
    Tab("subs", "Subs", Icons.Filled.Refresh),
    Tab("loans", "Loans", Icons.AutoMirrored.Filled.List),
    Tab("stock", "Stock", Icons.Filled.ShoppingCart),
    Tab("more", "More", Icons.Filled.Menu),
)

@Composable
fun ForgetItRoot(container: AppContainer) {
    val vm: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(container) } })
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val today = LocalDate.now(container.clock)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (route in TABS.map { it.route }) {
                NavigationBar {
                    TABS.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "overview", modifier = Modifier.padding(pad)) {
            composable("overview") { OverviewScreen(vm, today) }
            composable("subs") {
                SubscriptionsScreen(vm, today, onAdd = { nav.navigate("edit/0") }, onOpen = { nav.navigate("edit/$it") })
            }
            composable("loans") { ComingSoon("Loans") }
            composable("stock") { ComingSoon("Stock") }
            composable("more") { MoreScreen(nav) }
            composable("calendar") { CalendarScreen(vm, today, onBack = { nav.popBackStack() }) }
            composable("insights") { InsightsScreen(vm, today, onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
            composable("edit/{id}") { e -> EditRoute(e.arguments?.getString("id")?.toLongOrNull() ?: 0L, container, nav) }
        }
    }
}

@Composable
fun ComingSoon(title: String, onBack: (() -> Unit)? = null) {
    ScreenScaffold(title, onBack) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Text("Coming soon") }
    }
}

@Composable
private fun MoreScreen(nav: NavController) {
    ScreenScaffold("More", onBack = null) { pad ->
        androidx.compose.foundation.layout.Column(Modifier.padding(pad)) {
            listOf(
                Triple("calendar", "Calendar", Icons.Filled.DateRange),
                Triple("insights", "Insights", Icons.Filled.Info),
                Triple("settings", "Settings", Icons.Filled.Settings),
            ).forEach { (r, label, icon) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { Icon(icon, contentDescription = null) },
                    modifier = Modifier.clickable { nav.navigate(r) },
                )
            }
        }
    }
}

@Composable
private fun EditRoute(id: Long, container: AppContainer, nav: NavController) {
    val evm: EditViewModel = viewModel(key = "edit$id", factory = viewModelFactory { initializer { EditViewModel(container, id) } })
    val ctx = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    EditScreen(
        evm, onDone = { nav.popBackStack() },
        onSaved = {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            nav.popBackStack()
        },
        photos = { PhotoSection(container.photos, OwnerType.SUBSCRIPTION, id) })
}
