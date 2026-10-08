package app.forgetit.ui

import android.Manifest
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.graphics.graphicsLayer
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
import app.forgetit.ui.loans.LoanDetailScreen
import app.forgetit.ui.loans.LoanEditScreen
import app.forgetit.ui.loans.LoanEditViewModel
import app.forgetit.ui.loans.LoansScreen
import app.forgetit.ui.settings.SettingsScreen
import app.forgetit.ui.txn.ShareImportScreen
import app.forgetit.ui.txn.TransactionsScreen
import app.forgetit.ui.stock.StockDetailScreen
import app.forgetit.ui.stock.StockEditScreen
import app.forgetit.ui.stock.StockEditViewModel
import app.forgetit.ui.stock.StockScreen
import app.forgetit.ui.photos.PhotoSection
import app.forgetit.ui.edit.EditViewModel
import app.forgetit.ui.overview.OverviewScreen
import app.forgetit.ui.subscriptions.SubscriptionsScreen
import java.time.LocalDate

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("overview", "Overview", AppIcons.Home),
    Tab("subs", "Subs", AppIcons.Subscriptions),
    Tab("loans", "Loans", AppIcons.Loans),
    Tab("stock", "Stock", AppIcons.Stock),
    Tab("more", "More", AppIcons.More),
)

@Composable
fun ForgetItRoot(container: AppContainer, sharedText: String? = null, onSharedConsumed: () -> Unit = {}) {
    val vm: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(container) } })
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val today = LocalDate.now(container.clock)
    androidx.compose.runtime.LaunchedEffect(sharedText) { if (sharedText != null) nav.navigate("share") }

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
                            icon = { val sc = bounceScale(route == t.route); Icon(t.icon, contentDescription = null, modifier = Modifier.graphicsLayer { scaleX = sc; scaleY = sc }) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            nav, startDestination = "overview", modifier = Modifier.padding(pad),
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 14 } },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 14 } },
        ) {
            composable("overview") { OverviewScreen(vm, today) }
            composable("subs") {
                SubscriptionsScreen(vm, today, onAdd = { nav.navigate("edit/0") }, onOpen = { nav.navigate("edit/$it") })
            }
            composable("loans") { LoansScreen(vm, today, onAdd = { nav.navigate("loanedit/0") }, onOpen = { nav.navigate("loan/$it") }) }
            composable("loan/{id}") { e -> LoanRoute(e.arguments?.getString("id")?.toLongOrNull() ?: 0L, vm, today, container, nav) }
            composable("loanedit/{id}") { e -> LoanEditRoute(e.arguments?.getString("id")?.toLongOrNull() ?: 0L, container, nav) }
            composable("stock") { StockScreen(vm, today, onAdd = { nav.navigate("stockedit/0") }, onOpen = { nav.navigate("stockitem/$it") }) }
            composable("stockitem/{id}") { e -> StockRoute(e.arguments?.getString("id")?.toLongOrNull() ?: 0L, vm, today, container, nav) }
            composable("stockedit/{id}") { e -> StockEditRoute(e.arguments?.getString("id")?.toLongOrNull() ?: 0L, container, nav) }
            composable("more") { MoreScreen(nav) }
            composable("calendar") { CalendarScreen(vm, today, onBack = { nav.popBackStack() }) }
            composable("insights") { InsightsScreen(vm, today, onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
            composable("transactions") { TransactionsScreen(vm, today, onBack = { nav.popBackStack() }) }
            composable("share") { ShareImportScreen(vm, sharedText.orEmpty(), today, onDone = { onSharedConsumed(); nav.popBackStack() }) }
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
                Triple("calendar", "Calendar", AppIcons.Calendar),
                Triple("insights", "Insights", AppIcons.Insights),
                Triple("transactions", "Transactions", AppIcons.Transactions),
                Triple("settings", "Settings", AppIcons.Settings),
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

/** Returns a function that asks for the notification permission when it is not granted yet (Android 13+). */
@Composable
private fun rememberNotificationAsker(): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun LoanRoute(id: Long, vm: MainViewModel, today: LocalDate, container: AppContainer, nav: NavController) {
    LoanDetailScreen(
        vm, id, today, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("loanedit/$id") },
        photos = { PhotoSection(container.photos, OwnerType.LOAN, id) },
    )
}

@Composable
private fun LoanEditRoute(id: Long, container: AppContainer, nav: NavController) {
    val evm: LoanEditViewModel = viewModel(key = "loanedit$id", factory = viewModelFactory { initializer { LoanEditViewModel(container, id) } })
    val ask = rememberNotificationAsker()
    LoanEditScreen(
        evm, onDone = { nav.popBackStack() }, onSaved = { ask(); nav.popBackStack() },
        photos = { PhotoSection(container.photos, OwnerType.LOAN, id) },
    )
}

@Composable
private fun StockRoute(id: Long, vm: MainViewModel, today: LocalDate, container: AppContainer, nav: NavController) {
    StockDetailScreen(
        vm, id, today, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("stockedit/$id") },
        photos = { PhotoSection(container.photos, OwnerType.STOCK_ITEM, id) },
    )
}

@Composable
private fun StockEditRoute(id: Long, container: AppContainer, nav: NavController) {
    val evm: StockEditViewModel = viewModel(key = "stockedit$id", factory = viewModelFactory { initializer { StockEditViewModel(container, id) } })
    val ask = rememberNotificationAsker()
    StockEditScreen(
        evm, onDone = { nav.popBackStack() }, onSaved = { ask(); nav.popBackStack() },
        photos = { PhotoSection(container.photos, OwnerType.STOCK_ITEM, id) },
    )
}
