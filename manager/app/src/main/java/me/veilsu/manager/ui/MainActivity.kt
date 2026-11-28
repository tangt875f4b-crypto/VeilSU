package me.veilsu.manager.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import me.veilsu.manager.ui.component.splash.SplashScreen
import me.veilsu.manager.ui.theme.LocalAppBackgroundBackdrop
import me.veilsu.manager.ui.theme.LocalAppSettings
import me.veilsu.manager.ui.theme.MaterialExpressivePalette
import me.veilsu.manager.ui.theme.LocalBackgroundImagePath
import me.veilsu.manager.ui.theme.LocalDynamicWallpaperActive
import me.veilsu.manager.ui.theme.LocalDynamicWallpaperFrameBackdrop
import me.veilsu.manager.ui.theme.LocalCardBackgroundAlpha
import me.veilsu.manager.ui.theme.appearanceImageEffects
import java.io.File
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults.flingBehavior
import androidx.compose.foundation.pager.PagerDefaults.pageNestedScrollConnection
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import me.veilsu.manager.Natives
import me.veilsu.manager.ui.component.background.DynamicWallpaperBackground
import me.veilsu.manager.ui.component.background.DynamicWallpaperFit
import me.veilsu.manager.ui.component.background.DynamicWallpaperTransform
import me.veilsu.manager.ui.component.bottombar.BottomBar
import me.veilsu.manager.ui.component.bottombar.MainPagerState
import me.veilsu.manager.ui.component.bottombar.NavigationBadgeState
import me.veilsu.manager.ui.component.bottombar.SideRail
import me.veilsu.manager.ui.component.bottombar.rememberMainPagerState
import me.veilsu.manager.ui.component.bottombar.useNavigationRail
import me.veilsu.manager.ui.kernelFlash.KernelFlashScreen
import me.veilsu.manager.ui.navigation3.IntentDispatcher
import me.veilsu.manager.ui.navigation3.LocalNavigator
import me.veilsu.manager.ui.navigation3.Navigator
import me.veilsu.manager.ui.navigation3.Route
import me.veilsu.manager.ui.navigation3.rememberNavigator
import me.veilsu.manager.ui.screen.about.AboutScreen
import me.veilsu.manager.ui.screen.updatesources.UpdateSourcesScreen
import me.veilsu.manager.ai.api.AiFeatureBridge
import me.veilsu.manager.ai.host.ManagerAiTarget
import me.veilsu.manager.ui.screen.appicon.AppIconScreen
import me.veilsu.manager.ui.screen.customtext.CustomTextScreen
import me.veilsu.manager.ui.screen.account.AccountScreen
import me.veilsu.manager.ui.screen.themestore.ThemeStoreScreen
import me.veilsu.manager.ui.screen.themestore.ThemeStoreSourcesScreen
import me.veilsu.manager.ui.util.LocalCustomTexts
import me.veilsu.manager.ui.screen.appprofile.AppProfileScreen
import me.veilsu.manager.ui.screen.backgroundcrop.BackgroundCropScreen
import me.veilsu.manager.ui.screen.dynamicwallpaper.DynamicWallpaperScreen
import me.veilsu.manager.ui.screen.colorpalette.AdaptiveThemeBuilderScreen
import me.veilsu.manager.ui.screen.colorpalette.ColorPaletteScreen
import me.veilsu.manager.ui.screen.appearanceoverlay.AppearanceOverlayScreen
import me.veilsu.manager.ui.screen.dynamicmanagers.DynamicManagersScreen
import me.veilsu.manager.ui.screen.extensions.ExtensionsScreen
import me.veilsu.manager.ui.screen.extensions.CpuSpoofScreen
import me.veilsu.manager.ui.screen.extensions.VersionSpoofScreen
import me.veilsu.manager.ui.screen.executemoduleaction.ExecuteModuleActionScreen
import me.veilsu.manager.ui.screen.flash.FlashScreen
import me.veilsu.manager.ui.screen.home.HomePager
import me.veilsu.manager.ui.screen.install.InstallScreen
import me.veilsu.manager.ui.screen.kernelmodules.KernelModulesScreen
import me.veilsu.manager.ui.screen.kpm.KpmScreen
import me.veilsu.manager.ui.screen.module.ModulePager
import me.veilsu.manager.ui.screen.modulerepo.ModuleRepoDetailScreen
import me.veilsu.manager.ui.screen.modulerepo.ModuleRepoScreen
import me.veilsu.manager.ui.screen.scriptrun.ScriptRunScreen
import me.veilsu.manager.ui.screen.settings.SettingPager
import me.veilsu.manager.ui.screen.settings.tools.ToolsScreen
import me.veilsu.manager.ui.screen.sulog.SulogScreen
import me.veilsu.manager.ui.screen.superuser.SuperUserPager
import me.veilsu.manager.ui.screen.susfs.SuSFSScreen
import me.veilsu.manager.ui.screen.template.AppProfileTemplateScreen
import me.veilsu.manager.ui.screen.templateeditor.TemplateEditorScreen
import me.veilsu.manager.ui.screen.umountmanager.UmountManagerScreen
import me.veilsu.manager.ui.theme.AiAssistantMaterialTheme
import me.veilsu.manager.ui.theme.KernelSUTheme
import me.veilsu.manager.ui.theme.LocalColorMode
import me.veilsu.manager.ui.theme.LocalEnableBlur
import me.veilsu.manager.ui.theme.LocalEnableFloatingBottomBar
import me.veilsu.manager.ui.theme.LocalEnableFloatingBottomBarBlur
import me.veilsu.manager.ui.theme.LocalEnableNavigationBadge
import me.veilsu.manager.ui.theme.LocalEnableProgressiveBlur
import me.veilsu.manager.ui.theme.LocalModuleDescriptionMaxLines
import me.veilsu.manager.ui.theme.LocalTopBarPillFade
import me.veilsu.manager.ui.theme.LocalTopBarStyle
import me.veilsu.manager.ui.theme.TopBarStyle
import me.veilsu.manager.ui.util.BackgroundNavTransition
import me.veilsu.manager.ui.util.ImageStorage
import me.veilsu.manager.ui.util.LocaleHelper
import me.veilsu.manager.ui.util.ThemeTrialManager
import me.veilsu.manager.ui.util.getSuperuserCount
import me.veilsu.manager.ui.util.install
import me.veilsu.manager.ui.util.rememberBlurBackdrop
import me.veilsu.manager.ui.util.rememberContentReady
import me.veilsu.manager.ui.viewmodel.MainActivityViewModel
import me.veilsu.manager.ui.viewmodel.MainPagerConfig
import me.veilsu.manager.ui.viewmodel.ModuleViewModel
import me.veilsu.manager.ui.viewmodel.SettingsViewModel
import me.veilsu.manager.ui.viewmodel.SuperUserViewModel
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride
import androidx.core.graphics.drawable.toDrawable
import me.veilsu.manager.R


class MainActivity : ComponentActivity() {
    private val intentChannel = Channel<Intent>(capacity = Channel.BUFFERED)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (ThemeTrialManager.isActive(this)) {
            ViewModelProvider(this)[SettingsViewModel::class.java]
                .restoreThemeTrial(this, taskId)
        } else {
            ThemeTrialManager.cleanupOrphan(this)
        }

        val isManager = Natives.isManager
        if (isManager && Natives.kernelUAPIVersion == Natives.managerUAPIVersion) install()

        if (savedInstanceState == null) intent?.let { intentChannel.trySend(it) }

        setContent {
            val viewModel = viewModel<MainActivityViewModel>()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val selectedMainPage by viewModel.selectedMainPage.collectAsStateWithLifecycle()
            val appSettings = uiState.appSettings
            val uiMode = uiState.uiMode
            val darkMode = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && isSystemInDarkTheme())

            // Pick up launcher wallpaper changes while the "use system wallpaper" option is on.
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemWallpaper()
                // Also covers a mode change that happened while the app was in the background.
                viewModel.syncAdaptiveTheme(darkMode)
            }

            // Adaptive theme packages carry a light and a dark half; swap them with the effective
            // light/dark mode (system-following or a forced app mode).
            LaunchedEffect(darkMode) {
                viewModel.syncAdaptiveTheme(darkMode)
            }

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose { }
            }

            LaunchedEffect(darkMode) {
                window.setBackgroundDrawable(
                    getColor(if (darkMode) android.R.color.background_dark else android.R.color.background_light).toDrawable()
                )
            }

            val navigator = rememberNavigator(Route.Main)
            val context = LocalContext.current
            val uriHandler = LocalUriHandler.current
            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, uiState.pageScale) {
                Density(systemDensity.density * uiState.pageScale, systemDensity.fontScale)
            }
            // Bars sample the page backdrop, which is transparent wherever the page paints nothing.
            // Blurring that reads as black, so over a custom background they also need the wallpaper:
            // capture it here, where only the background image is drawn, and combine it underneath.
            val appBackgroundBackdrop = rememberLayerBackdrop()
            // The progressive blur covers both shells and works regardless of the plain bar blur.
            val progressiveBlurActive = uiState.enableProgressiveBlur && isRuntimeShaderSupported()
            // The dynamic renderer can fail (codec or GPU); fall back to the static poster
            // until the user picks another file.
            var dynamicWallpaperFailed by remember(
                uiState.dynamicWallpaperPath,
                uiState.dynamicWallpaperUpdatedAt,
            ) {
                mutableStateOf(false)
            }
            val hasDynamicWallpaper = uiState.dynamicWallpaperPath.isNotEmpty() && !dynamicWallpaperFailed
            // Capture only a still custom background for the bar blur. A live WebView must never
            // enter a Compose backdrop layer: some MIUI/HyperOS WebViews crash while their surface
            // is recorded (before the first frame), and because the background is persisted the app
            // restarts in a loop. A dynamic wallpaper is handled through frame snapshots instead
            // (see frameBackdropActive below), not through this capture.
            val captureAppBackground = uiState.customBackgroundUri.isNotEmpty() &&
                !hasDynamicWallpaper &&
                ((uiMode == UiMode.Miuix && uiState.enableBlur) || progressiveBlurActive)
            // A live dynamic WebView can never enter a backdrop layer (vendor WebViews crash
            // while their surface is recorded), so the renderer exports small frame snapshots.
            // Bars sample those from a record-only layer that paints nothing on screen.
            val frameBackdropActive = hasDynamicWallpaper &&
                ((uiMode == UiMode.Miuix && uiState.enableBlur) || progressiveBlurActive)
            val dynamicFrame = remember { mutableStateOf<ImageBitmap?>(null) }
            val hasDynamicFrame by remember {
                derivedStateOf { dynamicFrame.value != null }
            }
            val dynamicFrameScrim = when (val scrimPercent = uiState.customBackgroundScrimPercent) {
                0 -> null
                in Int.MIN_VALUE..-1 -> Color.Black.copy(alpha = -scrimPercent / 100f)
                else -> Color.White.copy(alpha = scrimPercent / 100f)
            }
            val dynamicFrameScrimState = rememberUpdatedState(dynamicFrameScrim)
            val dynamicFrameOnDraw = remember<ContentDrawScope.() -> Unit> {
                {
                    val frame = dynamicFrame.value
                    val width = size.width.roundToInt()
                    val height = size.height.roundToInt()
                    if (frame != null && width > 0 && height > 0) {
                        // The snapshot has the renderer canvas aspect (the full window), so
                        // stretching it over the layer reproduces what sits behind the bars.
                        drawImage(
                            image = frame,
                            dstSize = IntSize(width, height),
                        )
                        dynamicFrameScrimState.value?.let { scrim -> drawRect(scrim) }
                    }
                }
            }
            val dynamicFrameBackdrop = rememberLayerBackdrop(onDraw = dynamicFrameOnDraw)
            // A replaced wallpaper must not keep showing the previous video's frozen frame.
            LaunchedEffect(uiState.dynamicWallpaperPath, uiState.dynamicWallpaperUpdatedAt) {
                dynamicFrame.value = null
            }
            // Re-enabling the blur must not reuse a frame captured minutes ago.
            LaunchedEffect(frameBackdropActive) {
                if (!frameBackdropActive) dynamicFrame.value = null
            }
            val effectiveBackgroundPath = uiState.customBackgroundUri.ifEmpty {
                uiState.dynamicWallpaperPath
            }
            // A GPU context loss or a transient decoder failure should not disable the
            // wallpaper for the rest of the process lifetime.
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                if (dynamicWallpaperFailed) dynamicWallpaperFailed = false
            }

            val themedAppSettings = remember(appSettings, uiState.keyColorOverride) {
                uiState.keyColorOverride?.let { appSettings.copy(keyColor = it) } ?: appSettings
            }
            CompositionLocalProvider(
                LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) {
                        try {
                            uriHandler.openUri(uri)
                        } catch (_: IllegalArgumentException) {
                            Toast.makeText(context, R.string.external_link_open_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                LocalNavigator provides navigator,
                LocalDensity provides density,
                LocalColorMode provides appSettings.colorMode.value,
                LocalAppSettings provides themedAppSettings,
                LocalEnableBlur provides uiState.enableBlur,
                LocalEnableProgressiveBlur provides uiState.enableProgressiveBlur,
                LocalEnableFloatingBottomBar provides uiState.enableFloatingBottomBar,
                LocalEnableFloatingBottomBarBlur provides uiState.enableFloatingBottomBarBlur,
                LocalTopBarStyle provides TopBarStyle.fromValue(uiState.topBarStyle),
                LocalTopBarPillFade provides uiState.topBarPillFade,
                LocalEnableNavigationBadge provides uiState.enableNavigationBadge,
                LocalModuleDescriptionMaxLines provides uiState.moduleDescriptionMaxLines,
                LocalBackgroundImagePath provides effectiveBackgroundPath,
                LocalDynamicWallpaperActive provides hasDynamicWallpaper,
                LocalAppBackgroundBackdrop provides appBackgroundBackdrop.takeIf { captureAppBackground },
                LocalDynamicWallpaperFrameBackdrop provides dynamicFrameBackdrop.takeIf {
                    frameBackdropActive && hasDynamicFrame
                },
                LocalCardBackgroundAlpha provides (uiState.cardBackgroundAlphaPercent / 100f),
                LocalUiMode provides uiMode,
                LocalCustomTexts provides uiState.customTexts,
            ) {
                KernelSUTheme(appSettings = themedAppSettings, uiMode = uiMode) {
                    IntentDispatcher(intentChannel = intentChannel)
                    val swipeDismiss = if (uiState.enableSwipeDismiss) {
                        if (LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) {
                            NavSwipeDirection.RightToLeft
                        } else {
                            NavSwipeDirection.LeftToRight
                        }
                    } else {
                        NavSwipeDirection.None
                    }
                    val flashRoute = navigator.current() as? Route.Flash
                    var flashBackEnabled by remember(flashRoute) { mutableStateOf(false) }
                    val actionRoute = navigator.current() as? Route.ExecuteModuleAction
                    var actionBackEnabled by remember(actionRoute) { mutableStateOf(false) }
                    val scriptRunRoute = navigator.current() as? Route.ScriptRun
                    var scriptRunBackEnabled by remember(scriptRunRoute) { mutableStateOf(true) }
                    val kernelFlashRoute = navigator.current() as? Route.KernelFlash
                    var kernelFlashBackEnabled by remember(kernelFlashRoute) { mutableStateOf(false) }
                    // Read here, not inside the NavDisplay builder: NavDisplay only rebuilds its entries
                    // (and their swipeDismiss) when the builder lambda changes, so it must capture the value.
                    val actionSwipeDismiss = if (actionBackEnabled) swipeDismiss else NavSwipeDirection.None
                    val scriptRunSwipeDismiss = if (scriptRunBackEnabled) swipeDismiss else NavSwipeDirection.None
                    val kernelFlashSwipeDismiss = if (kernelFlashBackEnabled) swipeDismiss else NavSwipeDirection.None
                    val mainScreenEntry = @Composable {
                        MainScreen(
                            initialPage = selectedMainPage,
                            pagerInterceptionMode = uiState.pagerInterceptionMode,
                            onPageChanged = viewModel::setSelectedMainPage,
                        )
                    }

                    val navDisplay = @Composable {
                        val backgroundActive = uiState.customBackgroundUri.isNotEmpty() ||
                            uiState.dynamicWallpaperPath.isNotEmpty()
                        NavDisplay(
                            backStack = navigator.backStack,
                            transition = if (backgroundActive) {
                                BackgroundNavTransition
                            } else {
                                NavTransitions.MiuixDefault
                            },
                            effects = NavDisplayEffects(
                                cornerClipRadius = rememberNavSystemCornerRadius(),
                                // The dim scrim sits under the top destination even when idle; an
                                // opaque page hid it, but a transparent one would stay darkened.
                                dimAmount = if (backgroundActive) 0f else 0.5f,
                            ),
                            onBack = {
                                when (val top = navigator.current()) {
                                    is Route.TemplateEditor -> {
                                        if (!top.readOnly) {
                                            navigator.setResult("template_edit", true)
                                        } else {
                                            navigator.pop()
                                        }
                                    }

                                    else -> navigator.pop()
                                }
                            }) {
                            entry<Route.Main>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                            entry<Route.About>(swipeDismiss = swipeDismiss) { AboutScreen() }
                            entry<Route.UpdateSources>(swipeDismiss = swipeDismiss) {
                                MaterialExpressivePalette { UpdateSourcesScreen() }
                            }
                            entry<Route.Sulog>(swipeDismiss = swipeDismiss) { SulogScreen() }
                            entry<Route.ColorPalette>(swipeDismiss = swipeDismiss) { key -> ColorPaletteScreen(key.section, key.importUri) }
                            entry<Route.AppearanceOverlay>(swipeDismiss = swipeDismiss) {
                                AppearanceOverlayScreen(onBack = { navigator.pop() })
                            }
                            entry<Route.CustomTexts>(swipeDismiss = swipeDismiss) { CustomTextScreen() }
                            entry<Route.ThemeStore>(swipeDismiss = swipeDismiss) { key ->
                                MaterialExpressivePalette {
                                    ThemeStoreScreen(initialThemeId = key.themeId, initialSourceBaseUrl = key.sourceBaseUrl)
                                }
                            }
                            entry<Route.ThemeStoreAdaptiveUpload>(swipeDismiss = swipeDismiss) {
                                MaterialExpressivePalette {
                                    ThemeStoreScreen(startAdaptiveUpload = true)
                                }
                            }
                            entry<Route.ThemeStoreCurrentUpload>(swipeDismiss = swipeDismiss) {
                                MaterialExpressivePalette {
                                    ThemeStoreScreen(startCurrentUpload = true)
                                }
                            }
                            entry<Route.ThemeStoreSources>(swipeDismiss = swipeDismiss) {
                                MaterialExpressivePalette { ThemeStoreSourcesScreen() }
                            }
                            entry<Route.ThemeAccount>(swipeDismiss = swipeDismiss) {
                                MaterialExpressivePalette { AccountScreen() }
                            }
                            entry<Route.AdaptiveThemeBuilder>(swipeDismiss = swipeDismiss) {
                                MaterialExpressivePalette { AdaptiveThemeBuilderScreen() }
                            }
                            entry<Route.AppIcon>(swipeDismiss = swipeDismiss) { AppIconScreen() }
                            entry<Route.BackgroundCrop>(swipeDismiss = NavSwipeDirection.None) { key ->
                                BackgroundCropScreen(key.sourcePath)
                            }
                            entry<Route.DynamicWallpaper>(swipeDismiss = NavSwipeDirection.None) { key ->
                                DynamicWallpaperScreen(card = key.card)
                            }
                            entry<Route.AppProfileTemplate>(swipeDismiss = swipeDismiss) { AppProfileTemplateScreen() }
                            entry<Route.TemplateEditor>(swipeDismiss = swipeDismiss) { key -> TemplateEditorScreen(key.template, key.readOnly) }
                            entry<Route.AppProfile>(swipeDismiss = swipeDismiss) { key -> AppProfileScreen(key.uid) }
                            entry<Route.ModuleRepo>(swipeDismiss = swipeDismiss) { ModuleRepoScreen() }
                            entry<Route.ModuleRepoDetail>(swipeDismiss = swipeDismiss) { key -> ModuleRepoDetailScreen(key.module) }
                            entry<Route.Install>(swipeDismiss = swipeDismiss) { key -> InstallScreen(preselectedKernelUri = key.preselectedKernelUri) }
                            entry<Route.Flash>(swipeDismiss = if (flashBackEnabled) swipeDismiss else NavSwipeDirection.None) { key ->
                                FlashScreen(
                                    key.flashIt,
                                    onBackEnabledChange = { enabled ->
                                        if (navigator.current() == key) flashBackEnabled = enabled
                                    },
                                )
                            }
                            entry<Route.ExecuteModuleAction>(swipeDismiss = actionSwipeDismiss) { key ->
                                ExecuteModuleActionScreen(
                                    key.moduleId,
                                    key.fromShortcut,
                                    onBackEnabledChange = { enabled ->
                                        if (navigator.current() == key) actionBackEnabled = enabled
                                    },
                                )
                            }
                            entry<Route.ScriptRun>(swipeDismiss = scriptRunSwipeDismiss) { key ->
                                ScriptRunScreen(
                                    key.arg,
                                    onBackEnabledChange = { enabled ->
                                        if (navigator.current() == key) scriptRunBackEnabled = enabled
                                    },
                                )
                            }
                            entry<Route.Home>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                            entry<Route.SuperUser>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                            entry<Route.Module>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                            entry<Route.Settings>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                            entry<Route.KernelFlash>(swipeDismiss = kernelFlashSwipeDismiss)  { key ->
                                    KernelFlashScreen(
                                        key.kernelUri,
                                        key.selectedSlot,
                                        key.kpmPatchEnabled,
                                        key.kpmUndoPatch,
                                        onBackEnabledChange = { enabled ->
                                            if (navigator.current() == key) kernelFlashBackEnabled = enabled
                                        },
                                    )
                                }
                                entry<Route.Kpm>(swipeDismiss = swipeDismiss)  { KpmScreen() }
                                entry<Route.SuSFS>(swipeDismiss = swipeDismiss)  { key -> SuSFSScreen(key.tab) }
                                entry<Route.Tool>(swipeDismiss = swipeDismiss)  { ToolsScreen() }
                                entry<Route.UmountManager>(swipeDismiss = swipeDismiss)  { UmountManagerScreen() }
                                entry<Route.KernelModules>(swipeDismiss = swipeDismiss)  { KernelModulesScreen() }
                                entry<Route.DynamicManagers>(swipeDismiss = swipeDismiss) { DynamicManagersScreen() }
                                entry<Route.Extensions>(swipeDismiss = swipeDismiss) { ExtensionsScreen() }
                                entry<Route.CpuSpoof>(swipeDismiss = swipeDismiss) { CpuSpoofScreen() }
                                entry<Route.VersionSpoof>(swipeDismiss = swipeDismiss) { VersionSpoofScreen() }
                                entry<Route.AiAssistant>(swipeDismiss = swipeDismiss) {
                                    if (!AiFeatureBridge.isAvailable) {
                                        LaunchedEffect(Unit) { navigator.pop() }
                                    } else {
                                        // The assistant is always MD3; inside the Miuix shell it needs
                                        // the MD3 colour scheme of the current appearance settings.
                                        AiAssistantMaterialTheme {
                                            AiFeatureBridge.ChatScreen(
                                                onClose = {
                                                    if (navigator.current() == Route.AiAssistant) navigator.pop()
                                                },
                                                onOpenSettings = { navigator.push(Route.AiSettings) },
                                                onNavigate = { target ->
                                                    when (target) {
                                                        is ManagerAiTarget.Tab -> {
                                                            viewModel.openMainPage(target.pageIndex, target.section)
                                                            navigator.popUntil { it is Route.Main }
                                                        }

                                                        is ManagerAiTarget.Push -> navigator.navigateTo(target.route)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                                entry<Route.AiSettings>(swipeDismiss = swipeDismiss) {
                                    if (!AiFeatureBridge.isAvailable) {
                                        LaunchedEffect(Unit) { navigator.pop() }
                                    } else {
                                        AiAssistantMaterialTheme {
                                            AiFeatureBridge.SettingsScreen(
                                                onBack = { navigator.pop() },
                                            )
                                        }
                                    }
                                }
                        }
                    }

                    var appContentReady by remember { mutableStateOf(false) }
                    var splashDismissed by remember { mutableStateOf(false) }
                    // The preview page runs its own renderer for the candidate file. Mute the app
                    // renderer while it is open so two video soundtracks never play at once.
                    val dynamicPreviewOpen = navigator.current() is Route.DynamicWallpaper

                    SideEffect {
                        if (!appContentReady) appContentReady = true
                    }

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val hasStaticBackground = uiState.customBackgroundUri.isNotEmpty()
                        val hasCustomBackground = hasStaticBackground || hasDynamicWallpaper
                        val backgroundBlurRadius = (
                            ImageStorage.BACKGROUND_BLUR_MAX_RADIUS_DP * uiState.customBackgroundBlurPercent / 100f
                        ).dp
                        val backgroundEffects = themedAppSettings.appearance?.background
                        // A dynamic video covers the still poster; skip its full-screen blur and
                        // shader effects. They would only add GPU layers (and driver pressure)
                        // underneath a live WebView, which is exactly what vendor WebViews are
                        // fragile about. The dynamic renderer keeps its own blurPercent.
                        val staticImageEffects = !hasDynamicWallpaper
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Records the latest dynamic-wallpaper frame into a layer without
                            // painting it here. Reading the frame in draw invalidates this node
                            // whenever a new snapshot arrives, so the layer re-records offscreen.
                            if (frameBackdropActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .drawWithContent {
                                            dynamicFrame.value
                                            dynamicFrameScrimState.value
                                            drawContent()
                                        }
                                        .layerBackdrop(dynamicFrameBackdrop)
                                )
                            }
                            // The still custom background can be recorded for the bar blur; a
                            // dynamic wallpaper WebView must stay outside every recorded layer.
                            // The scrim has to live inside this Box too: the bars sample it as the
                            // pixels behind them, so leaving it out makes the blurred edge far
                            // brighter than the darkened background it sits on.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (captureAppBackground) {
                                            Modifier.layerBackdrop(appBackgroundBackdrop)
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                Crossfade(
                                    targetState = uiState.customBackgroundUri to uiState.customBackgroundUpdatedAt,
                                    label = "global_background",
                                ) { (path, _) ->
                                    if (path.isNotEmpty()) {
                                        val context = LocalContext.current
                                        val stamp = File(path).lastModified()
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(File(path))
                                                .crossfade(true)
                                                .memoryCacheKey("$path@$stamp")
                                                .diskCacheKey("$path@$stamp")
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .then(
                                                    if (staticImageEffects && backgroundBlurRadius > 0.dp) {
                                                        Modifier.blur(backgroundBlurRadius)
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                // Sharpen after a blur would just fight the blur, so only
                                                // apply it while the background is unblurred.
                                                .appearanceImageEffects(
                                                    if (staticImageEffects) backgroundEffects else null,
                                                    allowSharpen = staticImageEffects &&
                                                        backgroundBlurRadius <= 0.dp,
                                                )
                                        )
                                    }
                                }
                                // Delay the WebView until the splash is gone: it avoids fighting the
                                // splash for the first frame and keeps startup lighter.
                                if (hasDynamicWallpaper && splashDismissed) {
                                    key(
                                        uiState.dynamicWallpaperPath,
                                        uiState.dynamicWallpaperUpdatedAt,
                                    ) {
                                        DynamicWallpaperBackground(
                                            path = uiState.dynamicWallpaperPath,
                                            muted = uiState.dynamicWallpaperMuted || dynamicPreviewOpen,
                                            transform = DynamicWallpaperTransform(
                                                fit = DynamicWallpaperFit.fromValue(
                                                    uiState.dynamicWallpaperFit
                                                ),
                                                zoom = uiState.dynamicWallpaperZoom,
                                                offsetX = uiState.dynamicWallpaperOffsetX,
                                                offsetY = uiState.dynamicWallpaperOffsetY,
                                            ).sanitized(),
                                            fps = uiState.dynamicWallpaperFps,
                                            speed = uiState.dynamicWallpaperSpeed,
                                            blurPercent = uiState.customBackgroundBlurPercent,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .zIndex(0f),
                                            onError = { reason ->
                                                android.util.Log.w("DynamicWallpaper", "background failed: $reason")
                                                dynamicWallpaperFailed = true
                                            },
                                            onFrameSnapshot = if (frameBackdropActive) {
                                                { frame -> dynamicFrame.value = frame.asImageBitmap() }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                }
                                val scrimPercent = uiState.customBackgroundScrimPercent
                                if (hasCustomBackground && scrimPercent != 0) {
                                    val scrimColor = if (scrimPercent < 0) {
                                        Color.Black.copy(alpha = -scrimPercent / 100f)
                                    } else {
                                        Color.White.copy(alpha = scrimPercent / 100f)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .zIndex(1f)
                                            .background(scrimColor)
                                    )
                                }
                            }
                        }

                        when (uiMode) {
                            UiMode.Material -> androidx.compose.material3.Scaffold(
                                containerColor = if (hasCustomBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer
                            ) { navDisplay() }

                            UiMode.Miuix -> Scaffold(
                                containerColor = if (hasCustomBackground) Color.Transparent else MiuixTheme.colorScheme.surface
                            ) { navDisplay() }
                        }

                        if (!splashDismissed) {
                            SplashScreen(
                                isReady = appContentReady,
                                onDismissed = { splashDismissed = true }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentChannel.trySend(intent)
    }
}

val LocalMainPagerState = staticCompositionLocalOf<MainPagerState> { error("LocalMainPagerState not provided") }

@RequiresApi(Build.VERSION_CODES.Q)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(
    initialPage: Int = 0,
    pagerInterceptionMode: Int = PagerInterceptionMode.CrossAxisInterceptor.ordinal,
    onPageChanged: (Int) -> Unit = {},
) {
    val navController = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val enableProgressiveBlur = LocalEnableProgressiveBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val useNavigationRail = useNavigationRail(enableFloatingBottomBar)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { MainPagerConfig.PAGE_COUNT })
    val mainPagerState = rememberMainPagerState(
        pagerState = pagerState,
        animatePageChanges = !useNavigationRail,
    )
    // The AI assistant can request a tab from outside MainScreen; follow the
    // selected page when it changes without a pager gesture.
    LaunchedEffect(initialPage) {
        if (pagerState.currentPage != initialPage) {
            mainPagerState.animateToPage(initialPage)
        }
    }
    val isFullFeatured = Natives.isFullFeatured()
    val pagerMode = PagerInterceptionMode.entries.getOrElse(pagerInterceptionMode) {
        PagerInterceptionMode.Native
    }
    val interceptPagerGestures = pagerMode == PagerInterceptionMode.CrossAxisInterceptor
    var userScrollEnabled by remember(isFullFeatured) { mutableStateOf(isFullFeatured) }

    val enableNavigationBadge = LocalEnableNavigationBadge.current
    val badgeEnabled = enableNavigationBadge && isFullFeatured
    val moduleViewModel = viewModel<ModuleViewModel>()
    val moduleUiState by moduleViewModel.uiState.collectAsStateWithLifecycle()

    val superUserViewModel = viewModel<SuperUserViewModel>()
    val grantedUidCount by remember(superUserViewModel) {
        superUserViewModel.uiState
            .map { state -> state.groupedApps.count { it.anyAllowSu } }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(0)

    var startupPreloadStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isFullFeatured) {
        if (!isFullFeatured || startupPreloadStarted) {
            return@LaunchedEffect
        }

        moduleViewModel.initializePreferences()
        val moduleState = moduleViewModel.uiState.value
        if (!moduleState.hasLoaded) {
            if (!moduleState.isRefreshing) moduleViewModel.fetchModuleList()
            moduleViewModel.uiState.first { it.hasLoaded }
        }
        moduleViewModel.syncModuleUpdateInfo(moduleViewModel.uiState.value.modules)

        val superUserState = superUserViewModel.uiState.value
        if (!superUserState.hasLoaded) {
            superUserViewModel.initializePreferences()
            if (superUserState.isRefreshing) {
                superUserViewModel.uiState.first { it.hasLoaded }
            } else {
                superUserViewModel.loadAppList().join()
            }
        }

        startupPreloadStarted = true
    }

    // Loading the app list just for a badge is too expensive; read the kernel allowlist instead.
    var superuserCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(badgeEnabled, grantedUidCount) {
        superuserCount = if (badgeEnabled) withContext(Dispatchers.IO) { getSuperuserCount() } else 0
    }

    val navigationBadge = if (badgeEnabled) {
        NavigationBadgeState(
            superuserCount = superuserCount,
            moduleEnabledCount = moduleUiState.modules.count { it.enabled },
            moduleUpdatableCount = moduleUiState.updateInfo.count { it.value.downloadUrl.isNotBlank() },
        )
    } else {
        NavigationBadgeState()
    }
    val uiMode = LocalUiMode.current
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val cardAlpha = LocalCardBackgroundAlpha.current
    val surfaceColor = when (uiMode) {
        UiMode.Material -> MaterialTheme.colorScheme.surfaceContainer // Matches the Material page background behind the glass bar
        UiMode.Miuix -> MiuixTheme.colorScheme.surface
    }
    // This backdrop only feeds the standard Miuix navigation bar. The floating bars use
    // `backdrop` for their liquid glass or progressive band, so the pager is only recorded into
    // a full-screen layer when one of those readers is active.
    val needsStandardMiuixBarBackdrop = uiMode == UiMode.Miuix && !enableFloatingBottomBar &&
        !useNavigationRail
    val blurBackdrop = rememberBlurBackdrop(
        enableBlur = enableBlur && needsStandardMiuixBarBackdrop,
        allowProgressive = needsStandardMiuixBarBackdrop,
    )
    // The floating pills (Material bar, Miuix floating bar) sample this pager layer for their
    // liquid glass and/or progressive blur. Rail layouts show no bottom bar at all.
    val needsBottomBarBackdrop = !useNavigationRail &&
        (uiMode == UiMode.Material || enableFloatingBottomBar) &&
        (enableFloatingBottomBarBlur || enableProgressiveBlur)

    val backdrop = rememberLayerBackdrop {
        if (!hasCustomBackground) {
            drawRect(surfaceColor)
        } else {
            drawRect(surfaceColor.copy(alpha = cardAlpha))
        }
        drawContent()
    }

    val settledPage = mainPagerState.pagerState.settledPage
    LaunchedEffect(settledPage) {
        onPageChanged(settledPage)
    }

    val currentPage = mainPagerState.pagerState.currentPage
    LaunchedEffect(currentPage) {
        mainPagerState.syncPage()
    }

    MainScreenBackHandler(mainPagerState, navController)

    CompositionLocalProvider(
        LocalMainPagerState provides mainPagerState
    ) {
        val contentReady = rememberContentReady()
        val pagerContent = @Composable { bottomInnerPadding: Dp ->
            Box(modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
                HorizontalPager(
                    modifier = Modifier
                        .pagerGestureOverride(
                            pagerState = mainPagerState.pagerState,
                            mode = pagerMode,
                            enabled = userScrollEnabled,
                        )
                        // Material always floats its bar, so only the glass/progressive band matters there.
                        .then(if (needsBottomBarBackdrop) Modifier.layerBackdrop(backdrop) else Modifier),
                    state = mainPagerState.pagerState,
                    beyondViewportPageCount = if (contentReady) 3 else 0,
                    overscrollEffect = null,
                    userScrollEnabled = userScrollEnabled && !interceptPagerGestures,
                    pageNestedScrollConnection = if (interceptPagerGestures) {
                        PagerGestureNestedScrollConnection
                    } else {
                        pageNestedScrollConnection(
                            state = mainPagerState.pagerState,
                            orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        )
                    },
                    flingBehavior = flingBehavior(
                        state = mainPagerState.pagerState,
                        snapAnimationSpec = PagerNavigationSpringSpec,
                    ),
                ) { page ->
                    val isCurrentPage = page == settledPage
                    when (page) {
                        0 -> if (contentReady || isCurrentPage) HomePager(navController, bottomInnerPadding, isCurrentPage)
                        1 -> if (contentReady || isCurrentPage) SuperUserPager(navController, bottomInnerPadding, isCurrentPage)
                        2 -> if (contentReady || isCurrentPage) ModulePager(bottomInnerPadding, isCurrentPage)
                        3 -> if (contentReady || isCurrentPage) SettingPager(navController, bottomInnerPadding, isCurrentPage)
                    }
                }
            }
        }

        if (useNavigationRail) {
            val startInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Start)
            val navBarBottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

            when (uiMode) {
                UiMode.Material -> androidx.compose.material3.Scaffold(
                    containerColor = if (hasCustomBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row {
                        SideRail(navigationBadge)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }

                UiMode.Miuix -> Scaffold(
                    containerColor = if (hasCustomBackground) Color.Transparent else MiuixTheme.colorScheme.surface
                ) { _ ->
                    Row {
                        SideRail(navigationBadge)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }
            }
        } else {
            val bottomBar = @Composable {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BottomBar(
                        blurBackdrop = blurBackdrop,
                        backdrop = backdrop,
                        navigationBadge = navigationBadge,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            when (uiMode) {
                UiMode.Material -> androidx.compose.material3.Scaffold(
                    bottomBar = bottomBar,
                    containerColor = if (hasCustomBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer
                ) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }

                UiMode.Miuix -> Scaffold(
                    bottomBar = bottomBar,
                    containerColor = if (hasCustomBackground) Color.Transparent else MiuixTheme.colorScheme.surface
                ) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }
            }
        }
    }
}

@Composable
private fun MainScreenBackHandler(
    mainState: MainPagerState,
    navController: Navigator,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navController.current() is Route.Main && navController.backStackSize() == 1 && mainState.selectedPage != 0
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = {
            mainState.animateToPage(0)
        }
    )
}
