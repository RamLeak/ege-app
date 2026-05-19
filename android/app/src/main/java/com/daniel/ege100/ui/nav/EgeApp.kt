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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.daniel.ege100.data.UserStatsStore
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
import com.daniel.ege100.ui.wordblank.WordBlankTrainerScreen
import kotlinx.coroutines.launch

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
        LiquidGlassTab("home", "Главная", "🏠", HomeStubRoute),
        LiquidGlassTab("catalog", "Решать", "📚", CatalogRoute),
        LiquidGlassTab("journal", "Журнал", "📊", JournalStubRoute),
        LiquidGlassTab("profile", "Профиль", "👤", ProfileRoute),
    )
    val selectedKey = tabs.firstOrNull { tab -> currentDest?.matchesRoot(tab.route) == true }?.key

    // Phase 4 Stage P4-D6 (Convention #89) — Box overlay вместо Scaffold.bottomBar.
    // NavHost занимает весь экран, LiquidGlassBottomNav рисуется поверх в нижней
    // части. Каждый экран получает `contentPadding` с bottom = высота nav + nav-bar
    // inset, чтобы контент не залазил под капсулу. RenderEffect blur (Android 12+)
    // видит размытый scroll-контент позади себя — это даёт настоящий «iOS-glass».
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 16dp вертикальный padding контейнера (8 сверху + 8 снизу) + 64dp капсула + nav bar.
    val liquidGlassHeight = 80.dp + navBarsBottom
    val padding = PaddingValues(bottom = liquidGlassHeight)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
    ) {
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
                        onAttachedTrainerClick = { route ->
                            // Phase 4 Stage P4-D4 (Convention #80) — навигация на любой
                            // тренажёр через type-safe route из TrainerCatalogMapping.
                            navController.navigate(route)
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
            // ============================================================
            // Phase 4 Stage P4-D + P4-D4 — 8 новых тренажёров (Conventions #71, #74).
            // AllTrainersRoute удалён в P4-D4 (Convention #81) — тренажёры только
            // через каталог (TrainerCatalogMapping → SubtypesScreen).
            // ============================================================
            composable<ParonymTrainerRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    var showCongrats by remember { mutableStateOf<Int?>(null) }
                    com.daniel.ege100.ui.trainer.ParonymTrainerScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onCompleted = { count ->
                            scope.launch { UserStatsStore.markTrainerCompleted(context, "paronym") }
                            showCongrats = count
                        },
                        contentPadding = padding,
                    )
                    showCongrats?.let { count ->
                        com.daniel.ege100.ui.trainer.CongratulationDialog(
                            trainerName = "Паронимы (№5)",
                            wordsCount = count,
                            onClose = { showCongrats = null; navController.popBackStack() },
                            onAgain = { showCongrats = null },
                        )
                    }
                }
            }
            composable<PleonasmTrainerRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    var showCongrats by remember { mutableStateOf<Int?>(null) }
                    com.daniel.ege100.ui.trainer.PleonasmTrainerScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onCompleted = { count ->
                            scope.launch { UserStatsStore.markTrainerCompleted(context, "pleonasm") }
                            showCongrats = count
                        },
                        contentPadding = padding,
                    )
                    showCongrats?.let { count ->
                        com.daniel.ege100.ui.trainer.CongratulationDialog(
                            trainerName = "Плеоназмы (№6)",
                            wordsCount = count,
                            onClose = { showCongrats = null; navController.popBackStack() },
                            onAgain = { showCongrats = null },
                        )
                    }
                }
            }
            // Phase 4 Stage P4-D6 (Convention #90) — composable<GrammarTrainerRoute>
            // удалён вместе с UI multi-choice №8. №8 в каталоге теперь без тренажёра.
            // Phase 4 Stage P4-D5 (Convention #83) — правильный №7: словосочетания
            // с двухшаговой логикой (выбор фразы + ввод правильной формы).
            composable<CollocationTrainerRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    var showCongrats by remember { mutableStateOf<Int?>(null) }
                    com.daniel.ege100.ui.trainer.WordCollocationTrainerScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onCompleted = { count ->
                            scope.launch { UserStatsStore.markTrainerCompleted(context, "rus_collocation") }
                            showCongrats = count
                        },
                        contentPadding = padding,
                    )
                    showCongrats?.let { count ->
                        com.daniel.ege100.ui.trainer.CongratulationDialog(
                            trainerName = "Словосочетания (№7)",
                            wordsCount = count,
                            onClose = { showCongrats = null; navController.popBackStack() },
                            onAgain = { showCongrats = null },
                        )
                    }
                }
            }
            composable<TrigTrainerRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    var showCongrats by remember { mutableStateOf<Int?>(null) }
                    com.daniel.ege100.ui.trainer.TrigTrainerScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onCompleted = { count ->
                            scope.launch { UserStatsStore.markTrainerCompleted(context, "math_trig") }
                            showCongrats = count
                        },
                        contentPadding = padding,
                    )
                    showCongrats?.let { count ->
                        com.daniel.ege100.ui.trainer.CongratulationDialog(
                            trainerName = "Тригонометрия",
                            wordsCount = count,
                            onClose = { showCongrats = null; navController.popBackStack() },
                            onAgain = { showCongrats = null },
                        )
                    }
                }
            }
            composable<ShortMultTrainerRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    var showCongrats by remember { mutableStateOf<Int?>(null) }
                    com.daniel.ege100.ui.trainer.ShortMultTrainerScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onCompleted = { count ->
                            scope.launch { UserStatsStore.markTrainerCompleted(context, "math_shortmult") }
                            showCongrats = count
                        },
                        contentPadding = padding,
                    )
                    showCongrats?.let { count ->
                        com.daniel.ege100.ui.trainer.CongratulationDialog(
                            trainerName = "Сокращённое умножение",
                            wordsCount = count,
                            onClose = { showCongrats = null; navController.popBackStack() },
                            onAgain = { showCongrats = null },
                        )
                    }
                }
            }
            composable<LogPowerTrainerRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    var showCongrats by remember { mutableStateOf<Int?>(null) }
                    com.daniel.ege100.ui.trainer.LogPowerTrainerScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onCompleted = { count ->
                            scope.launch { UserStatsStore.markTrainerCompleted(context, "math_logpower") }
                            showCongrats = count
                        },
                        contentPadding = padding,
                    )
                    showCongrats?.let { count ->
                        com.daniel.ege100.ui.trainer.CongratulationDialog(
                            trainerName = "Логарифмы и степени",
                            wordsCount = count,
                            onClose = { showCongrats = null; navController.popBackStack() },
                            onAgain = { showCongrats = null },
                        )
                    }
                }
            }
            composable<DerivativesTrainerRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    var showCongrats by remember { mutableStateOf<Int?>(null) }
                    com.daniel.ege100.ui.trainer.DerivativesTrainerScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onCompleted = { count ->
                            scope.launch { UserStatsStore.markTrainerCompleted(context, "math_derivatives") }
                            showCongrats = count
                        },
                        contentPadding = padding,
                    )
                    showCongrats?.let { count ->
                        com.daniel.ege100.ui.trainer.CongratulationDialog(
                            trainerName = "Производные",
                            wordsCount = count,
                            onClose = { showCongrats = null; navController.popBackStack() },
                            onAgain = { showCongrats = null },
                        )
                    }
                }
            }
            composable<GeometryTrainerRoute> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    var showCongrats by remember { mutableStateOf<Int?>(null) }
                    com.daniel.ege100.ui.trainer.GeometryTrainerScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onCompleted = { count ->
                            scope.launch { UserStatsStore.markTrainerCompleted(context, "math_geometry") }
                            showCongrats = count
                        },
                        contentPadding = padding,
                    )
                    showCongrats?.let { count ->
                        com.daniel.ege100.ui.trainer.CongratulationDialog(
                            trainerName = "Геометрические формулы",
                            wordsCount = count,
                            onClose = { showCongrats = null; navController.popBackStack() },
                            onAgain = { showCongrats = null },
                        )
                    }
                }
            }
        }

        // Phase 4 Stage P4-D6 (Convention #89) — Liquid Glass bottom nav поверх NavHost.
        LiquidGlassBottomNav(
            items = tabs,
            selectedKey = selectedKey,
            onTabClick = { tab ->
                navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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

// Phase 4 Stage P4-D6 (Convention #89) — старые BottomTabBar + BottomTabItem
// удалены, заменены на LiquidGlassBottomNav (см. ui/nav/LiquidGlassBottomNav.kt).

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
