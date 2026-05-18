package com.daniel.ege100.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.daniel.ege100.ui.accent.AccentCategoriesScreen
import com.daniel.ege100.ui.accent.AccentTrainerScreen
import com.daniel.ege100.ui.wordblank.WordBlankTrainerScreen
import com.daniel.ege100.ui.catalog.CatalogScreen
import com.daniel.ege100.ui.catalog.HomeStubScreen
import com.daniel.ege100.ui.catalog.ProblemDetailScreen
import com.daniel.ege100.ui.catalog.ProblemListScreen
import com.daniel.ege100.ui.catalog.SubtypesScreen
import com.daniel.ege100.ui.catalog.TypesScreen
import com.daniel.ege100.ui.journal.FavoritesScreen
import com.daniel.ege100.ui.journal.JournalScreen
import com.daniel.ege100.ui.modifiers.edgeSwipeBack
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SeparatorHairline
import com.daniel.ege100.ui.theme.SystemBlue

private data class TabSpec(
    val route: Any,
    val label: String,
    val icon: String,
)

// ---- Анимации перехода ----
// Spring smooth (no bounce) для slide-стек навигации iOS-style. Параллакс-
// эффект: уходящий экран сдвигается на 1/3 ширины, а не на полную.

private val NAV_SPRING = spring<androidx.compose.ui.unit.IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

private fun forwardEnter(): EnterTransition =
    slideInHorizontally(animationSpec = NAV_SPRING) { it } +
        fadeIn(animationSpec = tween(280))

private fun forwardExit(): ExitTransition =
    slideOutHorizontally(animationSpec = NAV_SPRING) { -it / 3 } +
        fadeOut(animationSpec = tween(280))

private fun backEnter(): EnterTransition =
    slideInHorizontally(animationSpec = NAV_SPRING) { -it / 3 } +
        fadeIn(animationSpec = tween(280))

private fun backExit(): ExitTransition =
    slideOutHorizontally(animationSpec = NAV_SPRING) { it } +
        fadeOut(animationSpec = tween(280))

// Tab switching — БЕЗ slide, fade only.
private fun tabFadeEnter(): EnterTransition = fadeIn(tween(200))
private fun tabFadeExit(): ExitTransition = fadeOut(tween(200))

/**
 * Проверка: переход касается tab-роута (HomeStub / Catalog / JournalStub)?
 * При переключении табов используем fade, иначе обычный slide.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val from = initialState.destination.route.orEmpty()
    val to = targetState.destination.route.orEmpty()
    return from.isTabRoot() && to.isTabRoot()
}

private fun String.isTabRoot(): Boolean =
    endsWith("HomeStubRoute") ||
        endsWith("CatalogRoute") ||
        endsWith("JournalStubRoute")

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
        containerColor = Bg,
        bottomBar = {
            BottomTabBar(
                tabs = tabs,
                currentDest = currentDest,
                onTabClick = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = CatalogRoute,
            modifier = Modifier
                .fillMaxSize()
                .edgeSwipeBack(
                    onSwipeBack = {
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        }
                    },
                ),
            enterTransition = { if (isTabSwitch()) tabFadeEnter() else forwardEnter() },
            exitTransition = { if (isTabSwitch()) tabFadeExit() else forwardExit() },
            popEnterTransition = { if (isTabSwitch()) tabFadeEnter() else backEnter() },
            popExitTransition = { if (isTabSwitch()) tabFadeExit() else backExit() },
        ) {
            composable<HomeStubRoute> { HomeStubScreen(padding) }
            composable<JournalStubRoute> {
                JournalScreen(
                    contentPadding = padding,
                    onFavoritesClick = { navController.navigate(FavoritesRoute) },
                )
            }
            composable<FavoritesRoute> {
                FavoritesScreen(
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                    onProblemClick = { pid, tId, sId ->
                        navController.navigate(
                            ProblemDetailRoute(
                                problemId = pid,
                                typeId = tId,
                                subtypeId = sId,
                            ),
                        )
                    },
                )
            }

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
                    onAccentTrainerClick = { navController.navigate(AccentCategoriesRoute) },
                    onWordBlankTrainerClick = { typeNumber ->
                        navController.navigate(WordBlankTrainerRoute(typeNumber))
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
                    onProblemClick = { pid ->
                        navController.navigate(
                            ProblemDetailRoute(
                                problemId = pid,
                                typeId = args.typeId,
                                subtypeId = args.subtypeId,
                            ),
                        )
                    },
                )
            }
            composable<ProblemDetailRoute> { entry ->
                val args = entry.toRoute<ProblemDetailRoute>()
                ProblemDetailScreen(
                    problemId = args.problemId,
                    typeId = args.typeId,
                    subtypeId = args.subtypeId,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<AccentCategoriesRoute> {
                AccentCategoriesScreen(
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                    onCategoryClick = { catId, order ->
                        navController.navigate(AccentTrainerRoute(catId, order))
                    },
                )
            }
            composable<AccentTrainerRoute> { entry ->
                val args = entry.toRoute<AccentTrainerRoute>()
                AccentTrainerScreen(
                    categoryId = args.categoryId,
                    defaultOrder = args.defaultOrder,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<WordBlankTrainerRoute> { entry ->
                val args = entry.toRoute<WordBlankTrainerRoute>()
                WordBlankTrainerScreen(
                    typeNumber = args.typeNumber,
                    defaultOrder = args.defaultOrder,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun BottomTabBar(
    tabs: List<TabSpec>,
    currentDest: NavDestination?,
    onTabClick: (Any) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SeparatorHairline),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Bg)
                .height(84.dp)
                .padding(horizontal = 8.dp),
        ) {
            tabs.forEach { tab ->
                val selected = currentDest?.matchesRoot(tab.route) == true
                BottomTabItem(
                    icon = tab.icon,
                    label = tab.label,
                    selected = selected,
                    onClick = { onTabClick(tab.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Bounce при активации: selected → лёгкое увеличение с spring.
    val scaleTarget = when {
        pressed -> 0.92f
        selected -> 1.05f
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "tab-scale",
    )
    val haptic = LocalHapticFeedback.current
    val color = if (selected) SystemBlue else LabelTertiary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .scale(scale)
            .fillMaxSize()
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
    ) {
        Text(text = icon, fontSize = 26.sp, color = color)
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun NavDestination.matchesRoot(root: Any): Boolean {
    if (hasRoute(root::class)) return true
    val r = this.route.orEmpty()
    if (root is CatalogRoute) {
        return r.startsWith("com.daniel.ege100.ui.nav.TypesRoute") ||
            r.startsWith("com.daniel.ege100.ui.nav.SubtypesRoute") ||
            r.startsWith("com.daniel.ege100.ui.nav.ProblemListRoute") ||
            r.startsWith("com.daniel.ege100.ui.nav.ProblemDetailRoute") ||
            r.startsWith("com.daniel.ege100.ui.nav.AccentCategoriesRoute") ||
            r.startsWith("com.daniel.ege100.ui.nav.AccentTrainerRoute") ||
            r.startsWith("com.daniel.ege100.ui.nav.WordBlankTrainerRoute")
    }
    if (root is JournalStubRoute) {
        return r.startsWith("com.daniel.ege100.ui.nav.FavoritesRoute")
    }
    return false
}
