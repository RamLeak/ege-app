package com.daniel.ege100.ui.nav

import android.widget.Toast
import com.daniel.ege100.ui.common.SwipeBackContainer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
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
import com.daniel.ege100.data.BackupRepository
import com.daniel.ege100.data.BackupShare
import com.daniel.ege100.data.BackupSnapshot
import com.daniel.ege100.data.BreadcrumbLog
import com.daniel.ege100.data.CsvExporter
import com.daniel.ege100.data.ImportResult
import com.daniel.ege100.ui.accent.AccentCategoriesScreen
import com.daniel.ege100.ui.accent.AccentTrainerScreen
import com.daniel.ege100.ui.catalog.CatalogScreen
import com.daniel.ege100.ui.catalog.ProblemDetailScreen
import com.daniel.ege100.ui.catalog.ProblemListScreen
import com.daniel.ege100.ui.catalog.SubtypesScreen
import com.daniel.ege100.ui.catalog.TypesScreen
import com.daniel.ege100.ui.home.HomeScreen
import com.daniel.ege100.ui.journal.ErrorsListScreen
import com.daniel.ege100.ui.journal.FavoritesScreen
import com.daniel.ege100.ui.journal.JournalScreen
import com.daniel.ege100.ui.journal.StatsScreen
import com.daniel.ege100.ui.mock.FipiVariantsScreen
import com.daniel.ege100.ui.mock.MockExamCalendarScreen
import com.daniel.ege100.ui.mock.MockExamDetailScreen
import com.daniel.ege100.ui.mock.MockExamHistoryScreen
import com.daniel.ege100.ui.mock.MockExamRunnerScreen
import com.daniel.ege100.ui.profile.ImportConfirmBottomSheet
import com.daniel.ege100.ui.profile.ProfileScreen
import com.daniel.ege100.ui.profile.ResetProgressBottomSheet
import com.daniel.ege100.ui.profile.SettingsScreen
import com.daniel.ege100.ui.quick.QuickTrainerScreen
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SeparatorHairline
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.wordblank.WordBlankTrainerScreen
import kotlinx.coroutines.launch

private data class TabSpec(
    val route: Any,
    val label: String,
    val icon: String,
)

// ---- Анимации перехода ----
// Spring slide-стек навигации iOS-style. Параллакс-эффект: уходящий экран
// сдвигается на 1/3 ширины, а не на полную.
//
// Phase 4 Stage P4-C part Г1 (Convention #51) — `dampingRatio = 0.85f`
// вместо `NoBouncy` (=1.0). Лёгкая пружина даёт ощущение «попружинило и
// встало» — это и есть iOS-look. NoBouncy выглядел как линейный slide.

private val NAV_SPRING = spring<androidx.compose.ui.unit.IntOffset>(
    dampingRatio = 0.85f,
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

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val from = initialState.destination.route.orEmpty()
    val to = targetState.destination.route.orEmpty()
    return from.isTabRoot() && to.isTabRoot()
}

private fun String.isTabRoot(): Boolean =
    endsWith("HomeStubRoute") ||
        endsWith("CatalogRoute") ||
        endsWith("JournalStubRoute") ||
        endsWith("ProfileRoute")

@Composable
fun EgeApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDest: NavDestination? = backStack?.destination
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Phase 4 Stage P4-D2 part Г (Convention #67) — breadcrumb навигации.
    // Логируем при смене текущей destination — попадает в crash report.
    LaunchedEffect(currentDest?.route) {
        val r = currentDest?.route
        if (r != null) {
            val short = r.substringAfterLast('.').substringBefore('?').substringBefore('/').take(80)
            BreadcrumbLog.add("Navigate to $short")
        }
    }

    // Phase 3 Stage A part Д — share/import handlers поднимаем сюда, на
    // уровень Scaffold, чтобы Profile и Settings экраны могли вызвать одно
    // и то же действие через onExport / onImport callback'и.
    var pendingImport: BackupSnapshot? by remember { mutableStateOf(null) }
    var importErrorMessage: String? by remember { mutableStateOf(null) }
    var showReset by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = BackupShare.readUriContent(context, uri).getOrNull()
            if (content == null) {
                Toast.makeText(context, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val parsed = BackupRepository.parseBackup(content)
            parsed.fold(
                onSuccess = { pendingImport = it },
                onFailure = { importErrorMessage = it.message ?: "Файл повреждён" },
            )
        }
    }

    fun triggerExport() {
        scope.launch {
            val json = BackupRepository.exportBackup(context)
            val intent = BackupShare.buildShareIntent(context, json)
            context.startActivity(intent)
        }
    }

    fun triggerImport() {
        importLauncher.launch("application/json")
    }

    fun triggerCsvExport() {
        scope.launch {
            val intent = CsvExporter.exportAttempts(context)
            context.startActivity(intent)
        }
    }

    val tabs = listOf(
        TabSpec(HomeStubRoute, "Главная", "🏠"),
        TabSpec(CatalogRoute, "Решать", "📚"),
        TabSpec(JournalStubRoute, "Журнал", "📊"),
        TabSpec(ProfileRoute, "Профиль", "👤"),
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
            // Phase 4 Stage P4-C3 part В1 (Convention #62) — global
            // Modifier.edgeSwipeBack удалён. Каждый detail-screen теперь
            // оборачивается в SwipeBackContainer с visual animatable
            // feedback (translation за пальцем + spring обратно).
            modifier = Modifier.fillMaxSize(),
            enterTransition = { if (isTabSwitch()) tabFadeEnter() else forwardEnter() },
            exitTransition = { if (isTabSwitch()) tabFadeExit() else forwardExit() },
            popEnterTransition = { if (isTabSwitch()) tabFadeEnter() else backEnter() },
            popExitTransition = { if (isTabSwitch()) tabFadeExit() else backExit() },
        ) {
            composable<HomeStubRoute> {
                HomeScreen(
                    contentPadding = padding,
                    onProfileClick = { navController.navigate(ProfileRoute) },
                    onSubtypeClick = { sId, tId ->
                        navController.navigate(
                            ProblemListRoute(typeId = tId, subtypeId = sId),
                        )
                    },
                    onQuickTrainerStart = { ids ->
                        navController.navigate(QuickTrainerRoute.of(ids))
                    },
                    onMockExamCalendar = { navController.navigate(MockExamCalendarRoute) },
                    onStartMockMath = {
                        navController.navigate(MockExamRunnerRoute(planIndex = -1, subject = "math", fipiVariantId = null))
                    },
                    onStartMockRus = {
                        navController.navigate(MockExamRunnerRoute(planIndex = -1, subject = "rus", fipiVariantId = null))
                    },
                    onFipiVariants = { navController.navigate(FipiVariantsRoute) },
                )
            }
            composable<JournalStubRoute> {
                JournalScreen(
                    contentPadding = padding,
                    onFavoritesClick = { navController.navigate(FavoritesRoute) },
                    onErrorsClick = { navController.navigate(ErrorsListRoute) },
                    onStatsClick = { navController.navigate(StatsRoute) },
                    onCsvExportClick = ::triggerCsvExport,
                )
            }
            composable<ErrorsListRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    ErrorsListScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                        onRetry = { pid, tId, sId ->
                            navController.navigate(
                                ProblemDetailRoute(
                                    problemId = pid,
                                    typeId = tId,
                                    subtypeId = sId,
                                    fromErrors = true,
                                ),
                            )
                        },
                    )
                }
            }
            composable<StatsRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    StatsScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable<FavoritesRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
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
            }
            composable<ProfileRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    ProfileScreen(
                        contentPadding = padding,
                        onSettingsClick = { navController.navigate(SettingsRoute) },
                        onExportClick = ::triggerExport,
                        onImportClick = ::triggerImport,
                    )
                }
            }
            composable<SettingsRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    SettingsScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                        onExportClick = ::triggerExport,
                        onImportClick = ::triggerImport,
                        onResetClick = { showReset = true },
                    )
                }
            }

            composable<CatalogRoute> {
                CatalogScreen(
                    contentPadding = padding,
                    onSubjectClick = { id -> navController.navigate(TypesRoute(id)) },
                )
            }
            composable<TypesRoute> { entry ->
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    val args = entry.toRoute<TypesRoute>()
                    TypesScreen(
                        subjectId = args.subjectId,
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                        onTypeClick = { typeId -> navController.navigate(SubtypesRoute(typeId)) },
                    )
                }
            }
            composable<SubtypesRoute> { entry ->
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
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
            }
            composable<ProblemListRoute> { entry ->
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
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
            }
            composable<ProblemDetailRoute> { entry ->
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    val args = entry.toRoute<ProblemDetailRoute>()
                    ProblemDetailScreen(
                        problemId = args.problemId,
                        typeId = args.typeId,
                        subtypeId = args.subtypeId,
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                        fromErrors = args.fromErrors,
                        onOpenAiSettings = { navController.navigate(SettingsRoute) },
                    )
                }
            }
            composable<AccentCategoriesRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    AccentCategoriesScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                        onCategoryClick = { catId, order ->
                            navController.navigate(AccentTrainerRoute(catId, order))
                        },
                    )
                }
            }
            composable<AccentTrainerRoute> { entry ->
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    val args = entry.toRoute<AccentTrainerRoute>()
                    AccentTrainerScreen(
                        categoryId = args.categoryId,
                        defaultOrder = args.defaultOrder,
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                        onOpenAiSettings = { navController.navigate(SettingsRoute) },
                    )
                }
            }
            composable<WordBlankTrainerRoute> { entry ->
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    val args = entry.toRoute<WordBlankTrainerRoute>()
                    WordBlankTrainerScreen(
                        typeNumber = args.typeNumber,
                        defaultOrder = args.defaultOrder,
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                        onOpenAiSettings = { navController.navigate(SettingsRoute) },
                    )
                }
            }
            composable<QuickTrainerRoute> { entry ->
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    val args = entry.toRoute<QuickTrainerRoute>()
                    QuickTrainerScreen(
                        problemIds = args.problemIds,
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                        onFinish = { navController.popBackStack() },
                    )
                }
            }
            composable<MockExamCalendarRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    MockExamCalendarScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                        onPlanClick = { idx -> navController.navigate(MockExamDetailRoute(idx)) },
                        onFipiVariantsClick = { navController.navigate(FipiVariantsRoute) },
                        onHistoryClick = { navController.navigate(MockExamHistoryRoute) },
                    )
                }
            }
            composable<MockExamDetailRoute> { entry ->
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    val args = entry.toRoute<MockExamDetailRoute>()
                    MockExamDetailScreen(
                        planIndex = args.planIndex,
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                        onStartMath = {
                            navController.navigate(MockExamRunnerRoute(args.planIndex, "math", null))
                        },
                        onStartRus = {
                            navController.navigate(MockExamRunnerRoute(args.planIndex, "rus", null))
                        },
                    )
                }
            }
            composable<MockExamRunnerRoute> { entry ->
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    val args = entry.toRoute<MockExamRunnerRoute>()
                    MockExamRunnerScreen(
                        planIndex = args.planIndex,
                        subject = args.subject,
                        fipiVariantId = args.fipiVariantId,
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                        onFinish = {
                            // После завершения — popBackStack() возвращает на Detail
                            // (которая перезагрузит result через LaunchedEffect).
                            navController.popBackStack()
                        },
                    )
                }
            }
            composable<FipiVariantsRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    FipiVariantsScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                        onVariantClick = { variant ->
                            navController.navigate(
                                MockExamRunnerRoute(
                                    planIndex = -1,
                                    subject = variant.subject,
                                    fipiVariantId = variant.id,
                                ),
                            )
                        },
                    )
                }
            }
            composable<MockExamHistoryRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    MockExamHistoryScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }

    // Import confirmation bottom sheet.
    val pi = pendingImport
    if (pi != null) {
        ImportConfirmBottomSheet(
            backupDate = pi.exportedAt.take(10),
            onConfirm = {
                scope.launch {
                    val result = BackupRepository.applyBackup(context, pi)
                    pendingImport = null
                    val msg = when (result) {
                        is ImportResult.Success -> "Прогресс восстановлен ✓"
                        is ImportResult.Error -> "Ошибка: ${result.message}"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { pendingImport = null },
        )
    }

    // Import error toast (one-shot).
    val err = importErrorMessage
    LaunchedEffect(err) {
        if (err != null) {
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            importErrorMessage = null
        }
    }

    // Reset confirmation bottom sheet.
    if (showReset) {
        ResetProgressBottomSheet(
            onConfirm = {
                scope.launch {
                    BackupRepository.resetProgress(context)
                    showReset = false
                    Toast.makeText(context, "Прогресс сброшен ✓", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showReset = false },
        )
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
                .padding(horizontal = 4.dp),
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
        // 4 таба — иконки 24sp (раньше 26sp), label 11sp.
        Text(text = icon, fontSize = 24.sp, color = color)
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
        return r.startsWith("com.daniel.ege100.ui.nav.FavoritesRoute") ||
            r.startsWith("com.daniel.ege100.ui.nav.ErrorsListRoute") ||
            r.startsWith("com.daniel.ege100.ui.nav.StatsRoute")
    }
    if (root is ProfileRoute) {
        return r.startsWith("com.daniel.ege100.ui.nav.SettingsRoute")
    }
    return false
}
