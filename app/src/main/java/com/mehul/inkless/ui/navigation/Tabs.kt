package com.mehul.inkless.ui.navigation

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.mehul.inkless.R
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.ui.home.QuickAddRequests
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject
import kotlin.reflect.KClass

// Each tab is a small nested graph. Navigation keeps one back stack per graph, so switching tabs
// and coming back finds you where you left off.
@Serializable
object HomeTab

@Serializable
object SortTab

@Serializable
object FriendsTab

@Serializable
object MoneyTab

/** The four tabs along the bottom, with the graph each one opens and its first screen. */
enum class Tab(
    val label: String,
    @DrawableRes val icon: Int,
    val graph: Any,
    val root: KClass<*>,
    /** Pages opened from this tab that keep the bar, like a category's page under Home. */
    val pages: List<KClass<*>> = emptyList(),
) {
    Home("Home", R.drawable.icon_house, HomeTab, HomeRoute::class, pages = listOf(CategoryRoute::class)),
    Sort("To sort", R.drawable.icon_inbox, SortTab, ReviewRoute::class),
    Friends("Friends", R.drawable.icon_users, FriendsTab, FriendsRoute::class),
    Money("Money", R.drawable.icon_chart_trend, MoneyTab, MoneyRoute::class),
    ;

    companion object {
        /** The tab whose first screen [destination] is. */
        fun atRoot(destination: NavDestination?): Tab? =
            destination?.let { d -> entries.firstOrNull { d.hasRoute(it.root) } }

        /** The tab the bar shows as selected on [destination]; null where the bar hides. */
        fun withBar(destination: NavDestination?): Tab? =
            atRoot(destination) ?: destination?.let { d -> entries.firstOrNull { tab -> tab.pages.any { d.hasRoute(it) } } }
    }
}

/**
 * Opens [tab] the way bottom bars usually work: the tab you leave keeps its back stack, and the
 * one you open comes back as you left it.
 */
fun NavController.selectTab(tab: Tab) {
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** What the app shell needs: the count on the To sort tab, and the widget asking for Home. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    repository: LedgerRepository,
    quickAdd: QuickAddRequests,
) : ViewModel() {
    val toSort: StateFlow<Int> = repository.needsReview().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val quickAddRequested: StateFlow<Boolean> = quickAdd.pending
}
