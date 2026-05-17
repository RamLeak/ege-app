package com.daniel.ege100.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.daniel.ege100.ui.catalog.CatalogScreen
import com.daniel.ege100.ui.catalog.HomeStubScreen
import com.daniel.ege100.ui.catalog.JournalStubScreen
import com.daniel.ege100.ui.catalog.ProblemDetailScreen
import com.daniel.ege100.ui.catalog.ProblemListScreen
import com.daniel.ege100.ui.catalog.SubtypesScreen
import com.daniel.ege100.ui.catalog.TypesScreen

private data class TabSpec<T : Any>(
    val route: T,
    val label: String,
    val icon: String,
)

@Composable
fun EgeApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDest: NavDestination? = backStack?.destination

    val tabs = listOf(
        TabSpec(HomeStubRoute, "Главная", "🏠"),
        TabSpec(CatalogRoute, "Решать", "📚"),
        TabSpec(JournalStubRoute, "Журнал", "📊"),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                tabs.forEach { tab ->
                    val selected = currentDest?.matchesRoot(tab.route) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(tab.icon, fontSize = 22.sp) },
                        label = { Text(tab.label, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = CatalogRoute,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<HomeStubRoute> { HomeStubScreen(padding) }
            composable<JournalStubRoute> { JournalStubScreen(padding) }

            composable<CatalogRoute> {
                CatalogScreen(
                    contentPadding = padding,
                    onSubjectClick = { id -> navController.navigate(TypesRoute(id)) },
                )
            }
            composable<TypesRoute> { entry ->
                val args = entry.toRoute<TypesRoute>()
                TypesScreen(
                    subjectId = args.subjectId,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                    onTypeClick = { typeId -> navController.navigate(SubtypesRoute(typeId)) },
                )
            }
            composable<SubtypesRoute> { entry ->
                val args = entry.toRoute<SubtypesRoute>()
                SubtypesScreen(
                    typeId = args.typeId,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                    onTrainerClick = { tId ->
                        navController.navigate(ProblemListRoute(typeId = tId, subtypeId = null))
                    },
                    onSubtypeClick = { sId, tId ->
                        navController.navigate(ProblemListRoute(typeId = tId, subtypeId = sId))
                    },
                )
            }
            composable<ProblemListRoute> { entry ->
                val args = entry.toRoute<ProblemListRoute>()
                ProblemListScreen(
                    typeId = args.typeId,
                    subtypeId = args.subtypeId,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                    onProblemClick = { pid -> navController.navigate(ProblemDetailRoute(pid)) },
                )
            }
            composable<ProblemDetailRoute> { entry ->
                val args = entry.toRoute<ProblemDetailRoute>()
                ProblemDetailScreen(
                    problemId = args.problemId,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * Подсветка таба «Решать», когда мы либо на CatalogRoute, либо где-то в его
 * каталог-стеке (Types/Subtypes/ProblemList/ProblemDetail). Для «Главная» и
 * «Журнал» — простой матч по корню.
 */
private fun NavDestination.matchesRoot(root: Any): Boolean {
    if (hasRoute(root::class)) return true
    if (root !is CatalogRoute) return false
    val r = this.route.orEmpty()
    return r.startsWith("com.daniel.ege100.ui.nav.TypesRoute") ||
        r.startsWith("com.daniel.ege100.ui.nav.SubtypesRoute") ||
        r.startsWith("com.daniel.ege100.ui.nav.ProblemListRoute") ||
        r.startsWith("com.daniel.ege100.ui.nav.ProblemDetailRoute")
}
