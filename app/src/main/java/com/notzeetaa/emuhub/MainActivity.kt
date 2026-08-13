package com.notzeetaa.emuhub

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.notzeetaa.emuhub.ui.theme.EmuHubTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppScreen {
    HOME,
    DOWNLOADS,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        DownloadsManager.init(applicationContext)
        SettingsManager.init(applicationContext)

        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            "unknown"
        }

        setContent {
            var themeMode by remember { mutableStateOf(SettingsManager.getThemeMode()) }
            var colorTheme by remember { mutableStateOf(SettingsManager.getColorTheme()) }

            EmuHubTheme(themeMode = themeMode, colorTheme = colorTheme) {
                val downloadScope = rememberCoroutineScope()
                val appContext = applicationContext

                var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
                var refreshTrigger by remember { mutableIntStateOf(0) }
                var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
                var isLoading by remember { mutableStateOf(true) }

                var sourceCatalog by remember { mutableStateOf(SourceCatalogRepository.builtInCatalog()) }
                var turnipSourceId by remember { mutableStateOf(SettingsManager.getTurnipSource()) }
                var qualcommSourceId by remember { mutableStateOf(SettingsManager.getQualcommSource()) }
                var selectedSection by remember { mutableStateOf(SettingsManager.getSelectedDownloadSection()) }

                var turnipReleases by remember { mutableStateOf<List<GithubRelease>>(emptyList()) }
                var qualcommReleases by remember { mutableStateOf<List<GithubRelease>>(emptyList()) }
                var componentCatalogs by remember {
                    mutableStateOf<Map<String, Map<String, List<Component>>>>(emptyMap())
                }

                val activeCount by remember { derivedStateOf { DownloadsManager.activeDownloads.size } }

                // The catalog itself is remote. Driver/component files stay on their upstream
                // repositories; EmuHub only reads their APIs/manifests and therefore sees new
                // releases without shipping a new APK.
                LaunchedEffect(refreshTrigger, turnipSourceId, qualcommSourceId) {
                    isLoading = true

                    val result = withContext(Dispatchers.IO) {
                        val info = DeviceInfo.collect(this@MainActivity)
                        val catalog = SourceCatalogRepository.load()
                        val compatibleSources = catalog.compatibleTurnipSources(info.adrenoSeries)

                        val selectedSource = compatibleSources.firstOrNull { source ->
                            source.id.equals(turnipSourceId, ignoreCase = true) ||
                                source.name.equals(turnipSourceId, ignoreCase = true)
                        } ?: compatibleSources.firstOrNull()
                            ?: catalog.turnipSources.first()

                        val releases = fetchTurnipReleases(selectedSource, info.adrenoSeries)

                        val selectedQualcommSource = catalog.qualcommSources.firstOrNull { source ->
                            source.id.equals(qualcommSourceId, ignoreCase = true) ||
                                source.name.equals(qualcommSourceId, ignoreCase = true)
                        } ?: catalog.qualcommSources.first()
                        val qualcomm = fetchQualcommReleases(selectedQualcommSource)

                        val componentMaps = coroutineScope {
                            catalog.componentSources.map { source ->
                                async {
                                    source.id to fetchComponentsFromUrl(source.manifestUrl)
                                }
                            }.awaitAll().toMap()
                        }

                        AppLoadResult(
                            deviceInfo = info,
                            catalog = catalog,
                            selectedTurnipSource = selectedSource,
                            selectedQualcommSource = selectedQualcommSource,
                            turnipReleases = releases,
                            qualcommReleases = qualcomm,
                            componentCatalogs = componentMaps
                        )
                    }

                    deviceInfo = result.deviceInfo
                    sourceCatalog = result.catalog
                    turnipReleases = result.turnipReleases
                    qualcommReleases = result.qualcommReleases
                    componentCatalogs = result.componentCatalogs

                    if (turnipSourceId != result.selectedTurnipSource.id) {
                        turnipSourceId = result.selectedTurnipSource.id
                        SettingsManager.setTurnipSource(result.selectedTurnipSource.id)
                    }
                    if (qualcommSourceId != result.selectedQualcommSource.id) {
                        qualcommSourceId = result.selectedQualcommSource.id
                        SettingsManager.setQualcommSource(result.selectedQualcommSource.id)
                    }

                    isLoading = false
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        modifier = Modifier.fillMaxSize(),
                        label = "main-navigation",
                        transitionSpec = {
                            val enterSpec = tween<Float>(durationMillis = 240)
                            val exitSpec = tween<Float>(durationMillis = 170)
                            val slideSpec = tween<IntOffset>(durationMillis = 280)

                            if (targetState == AppScreen.HOME) {
                                (slideInHorizontally(slideSpec) { -it / 10 } + fadeIn(enterSpec)) togetherWith
                                    (slideOutHorizontally(slideSpec) { it / 14 } + fadeOut(exitSpec))
                            } else {
                                (slideInHorizontally(slideSpec) { it / 10 } + fadeIn(enterSpec)) togetherWith
                                    (slideOutHorizontally(slideSpec) { -it / 14 } + fadeOut(exitSpec))
                            }
                        }
                    ) { screen ->
                        when (screen) {
                            AppScreen.DOWNLOADS -> DownloadsScreen(
                                onBack = { currentScreen = AppScreen.HOME }
                            )

                            AppScreen.SETTINGS -> SettingsScreen(
                                onBack = { currentScreen = AppScreen.HOME },
                                themeMode = themeMode,
                                colorTheme = colorTheme,
                                onThemeModeChange = { mode ->
                                    SettingsManager.setThemeMode(mode)
                                    themeMode = mode
                                },
                                onColorThemeChange = { theme ->
                                    SettingsManager.setColorTheme(theme)
                                    colorTheme = theme
                                },
                                onSourceCatalogChanged = { refreshTrigger++ }
                            )

                            AppScreen.HOME -> {
                                Scaffold(
                                    modifier = Modifier.fillMaxSize(),
                                    topBar = {
                                        TopAppBar(
                                            title = {
                                                Column {
                                                    Text("EmuHub")
                                                    Text(
                                                        text = "Alpha v$appVersion",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            actions = {
                                                var isRefreshingLocal by remember { mutableStateOf(false) }
                                                val scope = rememberCoroutineScope()

                                                IconButton(
                                                    onClick = {
                                                        if (!isRefreshingLocal) {
                                                            scope.launch {
                                                                isRefreshingLocal = true
                                                                refreshTrigger++
                                                                delay(600)
                                                                isRefreshingLocal = false
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    if (isRefreshingLocal) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(22.dp),
                                                            strokeWidth = 2.dp
                                                        )
                                                    } else {
                                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        startActivity(
                                                            Intent(
                                                                Intent.ACTION_VIEW,
                                                                Uri.parse("https://notzeetaa.github.io/Donate-NotZeetaa/")
                                                            )
                                                        )
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Favorite, contentDescription = "Donate")
                                                }

                                                BadgedBox(
                                                    badge = {
                                                        if (activeCount > 0) Badge { Text(activeCount.toString()) }
                                                    }
                                                ) {
                                                    IconButton(onClick = { currentScreen = AppScreen.DOWNLOADS }) {
                                                        Icon(Icons.Default.Download, contentDescription = "Downloads")
                                                    }
                                                }

                                                IconButton(onClick = { currentScreen = AppScreen.SETTINGS }) {
                                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                                }
                                            }
                                        )
                                    }
                                ) { innerPadding ->
                                    DriverHubScreen(
                                        modifier = Modifier.padding(innerPadding),
                                        deviceInfo = deviceInfo,
                                        isLoading = isLoading,
                                        turnipSourceId = turnipSourceId,
                                        turnipSources = sourceCatalog.compatibleTurnipSources(deviceInfo?.adrenoSeries),
                                        turnipReleases = turnipReleases,
                                        qualcommSourceId = qualcommSourceId,
                                        qualcommSources = sourceCatalog.qualcommSources,
                                        qualcommReleases = qualcommReleases,
                                        componentSources = sourceCatalog.componentSources,
                                        componentCatalogs = componentCatalogs,
                                        sourceCatalogRemote = sourceCatalog.isRemote,
                                        selectedSection = selectedSection,
                                        onSelectedSectionChange = { section ->
                                            selectedSection = section
                                            SettingsManager.setSelectedDownloadSection(section)
                                        },
                                        onTurnipSourceChange = { sourceId ->
                                            turnipSourceId = sourceId
                                            SettingsManager.setTurnipSource(sourceId)
                                        },
                                        onQualcommSourceChange = { sourceId ->
                                            qualcommSourceId = sourceId
                                            SettingsManager.setQualcommSource(sourceId)
                                        },
                                        onDownloadAsset = { release, asset ->
                                            downloadScope.launch {
                                                downloadAsset(appContext, release, asset)
                                            }
                                        },
                                        onDownloadComponent = { component ->
                                            downloadScope.launch {
                                                downloadComponent(appContext, component)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class AppLoadResult(
    val deviceInfo: DeviceInfo,
    val catalog: SourceCatalog,
    val selectedTurnipSource: TurnipSource,
    val selectedQualcommSource: QualcommSource,
    val turnipReleases: List<GithubRelease>,
    val qualcommReleases: List<GithubRelease>,
    val componentCatalogs: Map<String, Map<String, List<Component>>>
)
