package com.notzeetaa.emuhub

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val active = DownloadsManager.activeDownloads
    val completed = DownloadsManager.completedDownloads
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val completedBytes = completed.sumOf { it.sizeBytes }
    val openWithLabel = appString(R.string.open_with)
    val shareChooserLabel = appString(R.string.share_chooser)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(appString(R.string.downloads))
                        Text(
                            appString(R.string.your_download_library),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = appString(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "download_summary") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(
                                    Icons.Default.DownloadDone,
                                    contentDescription = null,
                                    modifier = Modifier.padding(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(appString(R.string.download_library), style = MaterialTheme.typography.titleLarge)
                                Text(
                                    appString(R.string.download_library_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DownloadStatCard(
                                icon = Icons.Default.Downloading,
                                value = active.size.toString(),
                                label = appString(R.string.active),
                                modifier = Modifier.weight(1f)
                            )
                            DownloadStatCard(
                                icon = Icons.Default.Inventory2,
                                value = completed.size.toString(),
                                label = appString(R.string.saved),
                                modifier = Modifier.weight(1f)
                            )
                            DownloadStatCard(
                                icon = Icons.Default.Storage,
                                value = formatBytes(completedBytes),
                                label = appString(R.string.stored),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (active.isNotEmpty()) {
                item(key = "active_header") {
                    DownloadListHeader(
                        title = appString(R.string.downloading_now),
                        subtitle = appString(R.string.active_downloads_count, active.size),
                        icon = Icons.Default.Downloading
                    )
                }

                active.forEach { (name, download) ->
                    item(key = "active_$name") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.secondary
                                    ) {
                                        Icon(
                                            Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            modifier = Modifier.padding(10.dp),
                                            tint = MaterialTheme.colorScheme.onSecondary
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 2
                                        )
                                        Text(
                                            when (download.status) {
                                                DownloadStatus.PAUSED -> appString(R.string.paused_progress, download.progress)
                                                DownloadStatus.CANCELLING -> appString(R.string.cancelling)
                                                DownloadStatus.CONNECTING -> appString(R.string.connecting)
                                                DownloadStatus.DOWNLOADING -> when {
                                                    download.totalBytes > 0L -> appString(R.string.progress_complete, download.progress)
                                                    download.downloadedBytes > 0L -> appString(R.string.downloading)
                                                    else -> appString(R.string.starting)
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        label = {
                                            Text(
                                                when (download.status) {
                                                    DownloadStatus.PAUSED -> appString(R.string.paused)
                                                    DownloadStatus.CANCELLING -> appString(R.string.stopping)
                                                    DownloadStatus.CONNECTING -> appString(R.string.connecting_short)
                                                    DownloadStatus.DOWNLOADING -> appString(R.string.active)
                                                }
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                when (download.status) {
                                                    DownloadStatus.PAUSED -> Icons.Default.Pause
                                                    DownloadStatus.CANCELLING -> Icons.Default.Close
                                                    DownloadStatus.CONNECTING -> Icons.Default.Sync
                                                    DownloadStatus.DOWNLOADING -> Icons.Default.Downloading
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }

                                if (download.totalBytes > 0L) {
                                    LinearProgressIndicator(
                                        progress = download.progress / 100f,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        formatBytes(download.downloadedBytes),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        if (download.totalBytes > 0L) formatBytes(download.totalBytes) else appString(R.string.size_pending),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            if (download.status == DownloadStatus.PAUSED) {
                                                resumeActiveDownload(context, name)
                                            } else {
                                                pauseActiveDownload(name)
                                            }
                                        },
                                        enabled = download.status != DownloadStatus.CANCELLING,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(
                                            if (download.status == DownloadStatus.PAUSED) {
                                                Icons.Default.PlayArrow
                                            } else {
                                                Icons.Default.Pause
                                            },
                                            contentDescription = null
                                        )
                                        Spacer(Modifier.width(7.dp))
                                        Text(if (download.status == DownloadStatus.PAUSED) appString(R.string.resume) else appString(R.string.pause))
                                    }

                                    OutlinedButton(
                                        onClick = { cancelActiveDownload(context, name) },
                                        enabled = download.status != DownloadStatus.CANCELLING,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                        Spacer(Modifier.width(7.dp))
                                        Text(appString(R.string.cancel))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (completed.isNotEmpty()) {
                item(key = "completed_header") {
                    DownloadListHeader(
                        title = appString(R.string.downloaded_files),
                        subtitle = appString(R.string.ready_open_share_manage),
                        icon = Icons.Default.Folder
                    )
                }

                completed.forEach { file ->
                    item(key = "completed_${file.id}") {
                        var showDeleteDialog by remember { mutableStateOf(false) }
                        val displayPath = remember(file.filePath) { getFullPath(file.filePath, context) }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Icon(
                                            Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            modifier = Modifier.padding(11.dp),
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.fileName,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 2
                                        )
                                        Text(
                                            "${formatBytes(file.sizeBytes)} • ${formatDate(file.timestamp)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = appString(R.string.downloaded),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            displayPath,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            val uri = if (file.filePath.startsWith("content://")) {
                                                Uri.parse(file.filePath)
                                            } else {
                                                Uri.fromFile(File(file.filePath))
                                            }
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "application/octet-stream")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, openWithLabel))
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                                        Spacer(Modifier.width(7.dp))
                                        Text(appString(R.string.open))
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val uri = if (file.filePath.startsWith("content://")) {
                                                Uri.parse(file.filePath)
                                            } else {
                                                Uri.fromFile(File(file.filePath))
                                            }
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/octet-stream"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, shareChooserLabel))
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null)
                                        Spacer(Modifier.width(7.dp))
                                        Text(appString(R.string.share))
                                    }

                                    FilledTonalIconButton(
                                        onClick = { showDeleteDialog = true },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = appString(R.string.delete))
                                    }
                                }
                            }
                        }

                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                title = { Text(appString(R.string.delete_file)) },
                                text = { Text(appString(R.string.delete_confirm, file.fileName)) },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val deleted = deleteFile(context, file)
                                                if (deleted) DownloadsManager.removeCompleted(file.id)
                                                showDeleteDialog = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) {
                                        Text(appString(R.string.delete))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) {
                                        Text(appString(R.string.cancel))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (active.isEmpty() && completed.isEmpty()) {
                item(key = "empty_downloads") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(
                                    Icons.Default.DownloadForOffline,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(appString(R.string.no_downloads_yet), style = MaterialTheme.typography.titleMedium)
                            Text(
                                appString(R.string.downloads_started_here),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DownloadListHeader(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getFullPath(filePath: String, context: android.content.Context): String {
    if (!filePath.startsWith("content://")) {
        return filePath
    }

    val uri = Uri.parse(filePath)

    // Try to get folder using DocumentFile
    val docFile = DocumentFile.fromSingleUri(context, uri)
    if (docFile != null) {
        val parent = docFile.parentFile
        val folderName = parent?.name
        val fileName = docFile.name ?: "unknown"
        if (folderName != null) {
            return "$folderName/$fileName"
        }
    }

    // Manual parse of SAF URI path
    val path = uri.path ?: ""
    val treeIndex = path.indexOf("/tree/")
    val documentIndex = path.indexOf("/document/")

    if (treeIndex != -1 && documentIndex != -1 && documentIndex > treeIndex) {
        val folderEncoded = path.substring(treeIndex + "/tree/".length, documentIndex)
        val folderDecoded = Uri.decode(folderEncoded)
        val folderPath = folderDecoded.replace("primary:", "")

        val afterDocument = path.substring(documentIndex + "/document/".length)
        val fileNameEncoded = afterDocument.substringAfterLast('/')
        val fileName = Uri.decode(fileNameEncoded)

        return "$folderPath/$fileName"
    }

    // Ultimate fallback
    val fileName = uri.lastPathSegment?.let { Uri.decode(it) } ?: "file"
    return "Unknown/$fileName"
}

private suspend fun deleteFile(context: android.content.Context, file: DownloadsManager.CompletedDownload): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            if (file.filePath.startsWith("content://")) {
                val uri = Uri.parse(file.filePath)
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                    true
                } else if (DocumentsContract.isTreeUri(uri)) {
                    val docFile = DocumentFile.fromTreeUri(context, uri)
                    docFile?.delete() ?: false
                } else {
                    context.contentResolver.delete(uri, null, null) > 0
                }
            } else {
                val f = File(file.filePath)
                f.exists() && f.delete()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, appStringFor(context, SettingsManager.getAppLanguage(), R.string.error_deleting, e.message ?: "Unknown"), Toast.LENGTH_LONG).show()
            }
            false
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    themeMode: ThemeMode,
    colorTheme: ColorTheme,
    onThemeModeChange: (ThemeMode) -> Unit,
    onColorThemeChange: (ColorTheme) -> Unit,
    appLanguage: AppLanguage,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onSourceCatalogChanged: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val downloadsDefaultLabel = appString(R.string.downloads_default)
    val sourceCatalogSavedMessage = appString(R.string.source_catalog_saved)
    val invalidCatalogUrlMessage = appString(R.string.invalid_catalog_url)
    var currentFolderUri by remember { mutableStateOf(SettingsManager.getDownloadFolderUri()) }
    var displayPath by remember { mutableStateOf<String?>(null) }
    var sourceCatalogUrl by remember { mutableStateOf(SettingsManager.getSourceCatalogUrl()) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                SettingsManager.setDownloadFolderUri(uri.toString())
                currentFolderUri = uri.toString()
                val docFile = DocumentFile.fromTreeUri(context, uri)
                displayPath = docFile?.name ?: uri.path
            }
        }
    )

    LaunchedEffect(currentFolderUri, appLanguage) {
        displayPath = if (currentFolderUri != null) {
            val uri = Uri.parse(currentFolderUri)
            DocumentFile.fromTreeUri(context, uri)?.name ?: uri.path
        } else {
            downloadsDefaultLabel
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appString(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = appString(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSectionHeader(
                    icon = Icons.Default.Palette,
                    title = appString(R.string.appearance),
                    subtitle = appString(R.string.appearance_desc)
                )
            }

            item {
                SettingsCard(title = appString(R.string.app_language)) {
                    Text(
                        text = appString(R.string.language_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    LanguageSelector(
                        selected = appLanguage,
                        onSelected = onAppLanguageChange
                    )
                }
            }

            item {
                SettingsCard(title = appString(R.string.theme_mode)) {
                    Text(
                        text = appString(R.string.theme_mode_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    ThemeModeSelector(
                        selected = themeMode,
                        onSelected = onThemeModeChange
                    )
                }
            }

            item {
                SettingsCard(title = appString(R.string.color_theme)) {
                    Text(
                        text = appString(R.string.color_theme_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    ColorThemeSelector(
                        selected = colorTheme,
                        onSelected = onColorThemeChange
                    )
                }
            }

            item {
                SettingsSectionHeader(
                    icon = Icons.Default.Dns,
                    title = appString(R.string.sources),
                    subtitle = appString(R.string.sources_desc)
                )
            }

            item {
                SettingsCard(title = appString(R.string.source_catalog)) {
                    Text(
                        text = appString(R.string.source_catalog_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = sourceCatalogUrl,
                        onValueChange = { sourceCatalogUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(appString(R.string.catalog_url)) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val normalized = sourceCatalogUrl.trim()
                                if (normalized.startsWith("https://") || normalized.startsWith("http://")) {
                                    SettingsManager.setSourceCatalogUrl(normalized)
                                    sourceCatalogUrl = normalized
                                    onSourceCatalogChanged()
                                    Toast.makeText(context, sourceCatalogSavedMessage, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, invalidCatalogUrlMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(appString(R.string.save))
                        }
                        OutlinedButton(
                            onClick = {
                                SettingsManager.resetSourceCatalogUrl()
                                sourceCatalogUrl = DEFAULT_SOURCE_CATALOG_URL
                                onSourceCatalogChanged()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(appString(R.string.default_label))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (sourceCatalogUrl == DEFAULT_SOURCE_CATALOG_URL) {
                            appString(R.string.using_managed_catalog)
                        } else {
                            appString(R.string.using_custom_catalog)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                SettingsSectionHeader(
                    icon = Icons.Default.Download,
                    title = appString(R.string.downloads),
                    subtitle = appString(R.string.downloads_settings_desc)
                )
            }

            item {
                SettingsCard(title = appString(R.string.download_folder)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayPath ?: appString(R.string.downloads_default),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (currentFolderUri == null) appString(R.string.system_downloads_folder) else appString(R.string.custom_folder),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(appString(R.string.choose_folder))
                    }

                    if (currentFolderUri != null) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                SettingsManager.clearDownloadFolder()
                                currentFolderUri = null
                                displayPath = downloadsDefaultLabel
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(appString(R.string.reset_to_default))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelector(
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val choices = AppLanguage.values().toList()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = languageLabel(selected),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            label = { Text(appString(R.string.language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(18.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            choices.forEach { language ->
                DropdownMenuItem(
                    text = { Text(languageLabel(language)) },
                    leadingIcon = if (language == selected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        expanded = false
                        onSelected(language)
                    }
                )
            }
        }
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> appString(R.string.language_system)
    AppLanguage.ENGLISH -> appString(R.string.language_english)
    AppLanguage.PORTUGUESE_PORTUGAL -> appString(R.string.language_pt_pt)
    AppLanguage.PORTUGUESE_BRAZIL -> appString(R.string.language_pt_br)
    AppLanguage.SPANISH -> appString(R.string.language_spanish)
    AppLanguage.FRENCH -> appString(R.string.language_french)
    AppLanguage.GERMAN -> appString(R.string.language_german)
}

@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit
) {
    val choices = listOf(
        ThemeMode.SYSTEM to Pair(appString(R.string.theme_system), Icons.Default.SettingsBrightness),
        ThemeMode.LIGHT to Pair(appString(R.string.theme_light), Icons.Default.LightMode),
        ThemeMode.DARK to Pair(appString(R.string.theme_dark), Icons.Default.DarkMode),
        ThemeMode.AMOLED to Pair("AMOLED", Icons.Default.Contrast)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { (mode, data) ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { onSelected(mode) },
                        label = { Text(data.first) },
                        leadingIcon = {
                            Icon(
                                imageVector = data.second,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorThemeSelector(
    selected: ColorTheme,
    onSelected: (ColorTheme) -> Unit
) {
    val choices = listOf(
        ColorTheme.DYNAMIC to appString(R.string.theme_dynamic),
        ColorTheme.EMUHUB to "EmuHub",
        ColorTheme.BLUE to appString(R.string.theme_blue),
        ColorTheme.PURPLE to appString(R.string.theme_purple),
        ColorTheme.ORANGE to appString(R.string.theme_orange)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        choices.forEach { (theme, title) ->
            FilterChip(
                selected = selected == theme,
                onClick = { onSelected(theme) },
                label = { Text(title) },
                leadingIcon = if (selected == theme) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null
            )
        }
    }
}

// ---------- Component guide ----------
private data class GuideTopic(
    val id: String,
    @StringRes val categoryRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
    @StringRes val badgeRes: Int,
    @StringRes val whatItIsRes: Int,
    @StringRes val tryFirstRes: Int,
    @StringRes val bestForRes: Int,
    @StringRes val switchWhenRes: Int,
    val tipRes: List<Int>
)

private val componentGuideTopics = listOf(
    GuideTopic(
        id = "turnip",
        categoryRes = R.string.category_gpu_driver,
        titleRes = R.string.guide_turnip_title,
        subtitleRes = R.string.guide_turnip_subtitle,
        icon = Icons.Default.Eco,
        badgeRes = R.string.badge_recommended,
        whatItIsRes = R.string.guide_turnip_whatitis,
        tryFirstRes = R.string.guide_turnip_tryfirst,
        bestForRes = R.string.guide_turnip_bestfor,
        switchWhenRes = R.string.guide_turnip_switchwhen,
        tipRes = listOf(
            R.string.guide_turnip_tip1,
            R.string.guide_turnip_tip2,
            R.string.guide_turnip_tip3
        )
    ),
    GuideTopic(
        id = "qualcomm",
        categoryRes = R.string.category_gpu_driver,
        titleRes = R.string.guide_qualcomm_title,
        subtitleRes = R.string.guide_qualcomm_subtitle,
        icon = Icons.Default.Memory,
        badgeRes = R.string.badge_alternative,
        whatItIsRes = R.string.guide_qualcomm_whatitis,
        tryFirstRes = R.string.guide_qualcomm_tryfirst,
        bestForRes = R.string.guide_qualcomm_bestfor,
        switchWhenRes = R.string.guide_qualcomm_switchwhen,
        tipRes = listOf(
            R.string.guide_qualcomm_tip1,
            R.string.guide_qualcomm_tip2,
            R.string.guide_qualcomm_tip3
        )
    ),
    GuideTopic(
        id = "wine",
        categoryRes = R.string.category_windows,
        titleRes = R.string.guide_wine_title,
        subtitleRes = R.string.guide_wine_subtitle,
        icon = Icons.Default.WineBar,
        badgeRes = R.string.badge_base_runtime,
        whatItIsRes = R.string.guide_wine_whatitis,
        tryFirstRes = R.string.guide_wine_tryfirst,
        bestForRes = R.string.guide_wine_bestfor,
        switchWhenRes = R.string.guide_wine_switchwhen,
        tipRes = listOf(
            R.string.guide_wine_tip1,
            R.string.guide_wine_tip2,
            R.string.guide_wine_tip3
        )
    ),
    GuideTopic(
        id = "proton",
        categoryRes = R.string.category_windows,
        titleRes = R.string.guide_proton_title,
        subtitleRes = R.string.guide_proton_subtitle,
        icon = Icons.Default.Bolt,
        badgeRes = R.string.badge_games,
        whatItIsRes = R.string.guide_proton_whatitis,
        tryFirstRes = R.string.guide_proton_tryfirst,
        bestForRes = R.string.guide_proton_bestfor,
        switchWhenRes = R.string.guide_proton_switchwhen,
        tipRes = listOf(
            R.string.guide_proton_tip1,
            R.string.guide_proton_tip2,
            R.string.guide_proton_tip3
        )
    ),
    GuideTopic(
        id = "box64",
        categoryRes = R.string.category_cpu,
        titleRes = R.string.guide_box64_title,
        subtitleRes = R.string.guide_box64_subtitle,
        icon = Icons.Default.Inventory2,
        badgeRes = R.string.badge_common_default,
        whatItIsRes = R.string.guide_box64_whatitis,
        tryFirstRes = R.string.guide_box64_tryfirst,
        bestForRes = R.string.guide_box64_bestfor,
        switchWhenRes = R.string.guide_box64_switchwhen,
        tipRes = listOf(
            R.string.guide_box64_tip1,
            R.string.guide_box64_tip2,
            R.string.guide_box64_tip3
        )
    ),
    GuideTopic(
        id = "wowbox64",
        categoryRes = R.string.category_cpu,
        titleRes = R.string.guide_wowbox64_title,
        subtitleRes = R.string.guide_wowbox64_subtitle,
        icon = Icons.Default.AutoAwesome,
        badgeRes = R.string.badge_advanced,
        whatItIsRes = R.string.guide_wowbox64_whatitis,
        tryFirstRes = R.string.guide_wowbox64_tryfirst,
        bestForRes = R.string.guide_wowbox64_bestfor,
        switchWhenRes = R.string.guide_wowbox64_switchwhen,
        tipRes = listOf(
            R.string.guide_wowbox64_tip1,
            R.string.guide_wowbox64_tip2,
            R.string.guide_wowbox64_tip3
        )
    ),
    GuideTopic(
        id = "fexcore",
        categoryRes = R.string.category_cpu,
        titleRes = R.string.guide_fexcore_title,
        subtitleRes = R.string.guide_fexcore_subtitle,
        icon = Icons.Default.DeveloperBoard,
        badgeRes = R.string.badge_alternative,
        whatItIsRes = R.string.guide_fexcore_whatitis,
        tryFirstRes = R.string.guide_fexcore_tryfirst,
        bestForRes = R.string.guide_fexcore_bestfor,
        switchWhenRes = R.string.guide_fexcore_switchwhen,
        tipRes = listOf(
            R.string.guide_fexcore_tip1,
            R.string.guide_fexcore_tip2,
            R.string.guide_fexcore_tip3
        )
    ),
    GuideTopic(
        id = "dxvk",
        categoryRes = R.string.category_graphics,
        titleRes = R.string.guide_dxvk_title,
        subtitleRes = R.string.guide_dxvk_subtitle,
        icon = Icons.Default.SportsEsports,
        badgeRes = R.string.badge_dx8_11,
        whatItIsRes = R.string.guide_dxvk_whatitis,
        tryFirstRes = R.string.guide_dxvk_tryfirst,
        bestForRes = R.string.guide_dxvk_bestfor,
        switchWhenRes = R.string.guide_dxvk_switchwhen,
        tipRes = listOf(
            R.string.guide_dxvk_tip1,
            R.string.guide_dxvk_tip2,
            R.string.guide_dxvk_tip3
        )
    ),
    GuideTopic(
        id = "vkd3d",
        categoryRes = R.string.category_graphics,
        titleRes = R.string.guide_vkd3d_title,
        subtitleRes = R.string.guide_vkd3d_subtitle,
        icon = Icons.Default.ViewInAr,
        badgeRes = R.string.badge_dx12,
        whatItIsRes = R.string.guide_vkd3d_whatitis,
        tryFirstRes = R.string.guide_vkd3d_tryfirst,
        bestForRes = R.string.guide_vkd3d_bestfor,
        switchWhenRes = R.string.guide_vkd3d_switchwhen,
        tipRes = listOf(
            R.string.guide_vkd3d_tip1,
            R.string.guide_vkd3d_tip2,
            R.string.guide_vkd3d_tip3
        )
    ),
    GuideTopic(
        id = "d7vk",
        categoryRes = R.string.category_graphics,
        titleRes = R.string.guide_d7vk_title,
        subtitleRes = R.string.guide_d7vk_subtitle,
        icon = Icons.Default.Gamepad,
        badgeRes = R.string.badge_legacy_games,
        whatItIsRes = R.string.guide_d7vk_whatitis,
        tryFirstRes = R.string.guide_d7vk_tryfirst,
        bestForRes = R.string.guide_d7vk_bestfor,
        switchWhenRes = R.string.guide_d7vk_switchwhen,
        tipRes = listOf(
            R.string.guide_d7vk_tip1,
            R.string.guide_d7vk_tip2,
            R.string.guide_d7vk_tip3
        )
    )
)

private fun guideTopicIdForSection(sectionId: String): String = when {
    sectionId == "turnip" -> "turnip"
    sectionId == "qualcomm" -> "qualcomm"
    sectionId.startsWith("component:") -> sectionId.substringAfter("component:").lowercase(Locale.ROOT)
    else -> sectionId.lowercase(Locale.ROOT)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentGuideScreen(
    onBack: () -> Unit,
    adrenoSeries: String?,
    initialTopicId: String? = null
) {
    BackHandler { onBack() }

    val listState = rememberLazyListState()
    var expandedId by rememberSaveable { mutableStateOf(initialTopicId) }

    LaunchedEffect(initialTopicId) {
        if (initialTopicId != null) {
            val index = componentGuideTopics.indexOfFirst { it.id == initialTopicId }
            if (index >= 0) {
                expandedId = initialTopicId
                listState.animateScrollToItem(index + 3)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(appString(R.string.component_guide))
                        Text(
                            appString(R.string.what_each_download_does),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = appString(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "guide_intro") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(appString(R.string.choose_components_confidence), style = MaterialTheme.typography.titleLarge)
                            Text(
                                appString(R.string.guide_intro_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            item(key = "guide_quick_pick") {
                QuickPickGuideCard(adrenoSeries)
            }

            item(key = "guide_notice") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            appString(R.string.guide_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            componentGuideTopics.forEach { topic ->
                item(key = "guide_topic_${topic.id}") {
                    GuideTopicCard(
                        topic = topic,
                        expanded = expandedId == topic.id,
                        onClick = {
                            expandedId = if (expandedId == topic.id) null else topic.id
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPickGuideCard(adrenoSeries: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(appString(R.string.quick_pick), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (adrenoSeries != null) appString(R.string.starting_points_adreno, adrenoSeries) else appString(R.string.good_starting_points),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            QuickPickRow(appString(R.string.gpu_driver), appString(R.string.quick_gpu))
            QuickPickRow("DirectX 8–11", appString(R.string.quick_dx8_11))
            QuickPickRow("DirectX 12", appString(R.string.quick_dx12))
            QuickPickRow("Direct3D 3–7", appString(R.string.quick_d3d3_7))
            QuickPickRow(appString(R.string.windows_runtime), appString(R.string.quick_windows))
            QuickPickRow(appString(R.string.x86_64_arm64), appString(R.string.quick_cpu))
        }
    }
}

@Composable
private fun QuickPickRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier.width(112.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun GuideTopicCard(
    topic: GuideTopic,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(
                        topic.icon,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(appString(topic.titleRes), style = MaterialTheme.typography.titleMedium)
                    Text(
                        appString(topic.subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) appString(R.string.collapse) else appString(R.string.expand)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(onClick = {}, label = { Text(appString(topic.categoryRes)) })
                AssistChip(onClick = {}, label = { Text(appString(topic.badgeRes)) })
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HorizontalDivider()
                    GuideDetailBlock(appString(R.string.what_it_is), appString(topic.whatItIsRes), Icons.Default.Info)
                    GuideDetailBlock(appString(R.string.try_first), appString(topic.tryFirstRes), Icons.Default.PlayArrow)
                    GuideDetailBlock(appString(R.string.best_for), appString(topic.bestForRes), Icons.Default.CheckCircle)
                    GuideDetailBlock(appString(R.string.when_to_switch), appString(topic.switchWhenRes), Icons.Default.SwapHoriz)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            appString(R.string.useful_notes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        topic.tipRes.forEach { tipRes ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("•", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    appString(tipRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideDetailBlock(label: String, text: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------- Driver hub / index ----------
private data class HubSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val latest: String,
    val source: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverHubScreen(
    modifier: Modifier = Modifier,
    deviceInfo: DeviceInfo?,
    isLoading: Boolean,
    turnipSourceId: String,
    turnipSources: List<TurnipSource>,
    turnipReleases: List<GithubRelease>,
    qualcommSourceId: String,
    qualcommSources: List<QualcommSource>,
    qualcommReleases: List<GithubRelease>,
    componentSources: List<ComponentSource>,
    componentCatalogs: Map<String, Map<String, List<Component>>>,
    sourceCatalogRemote: Boolean,
    selectedSection: String,
    onSelectedSectionChange: (String) -> Unit,
    onTurnipSourceChange: (String) -> Unit,
    onQualcommSourceChange: (String) -> Unit,
    onOpenGuide: (String?) -> Unit,
    onDownloadAsset: (GithubRelease, GithubAsset) -> Unit,
    onDownloadComponent: (Component) -> Unit
) {
    val showQualcomm = qualcommReleases.isNotEmpty() &&
        (deviceInfo?.adrenoSeries == "6xx" || deviceInfo?.adrenoSeries == "7xx")

    val preferredComponentOrder = listOf("Wine", "Proton", "Box64", "WOWBox64", "DXVK", "FEXCore", "VKD3D", "D7VK")
    val discoveredComponentTypes = componentCatalogs.values
        .flatMap { it.keys }
        .distinct()
        .filterNot { it in preferredComponentOrder }
        .sorted()
    val componentOrder = preferredComponentOrder + discoveredComponentTypes
    val componentSourceSelections = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(componentSources, componentCatalogs) {
        componentOrder.forEach { type ->
            val saved = SettingsManager.getComponentSource(type)
            val resolved = componentSources.firstOrNull { source ->
                source.id == saved && componentCatalogs[source.id]?.get(type).orEmpty().isNotEmpty()
            } ?: componentSources.firstOrNull { source ->
                componentCatalogs[source.id]?.get(type).orEmpty().isNotEmpty()
            }

            if (resolved != null) {
                componentSourceSelections[type] = resolved.id
                if (saved != resolved.id) SettingsManager.setComponentSource(type, resolved.id)
            }
        }
    }

    fun currentComponentSource(type: String): ComponentSource? {
        val selectedId = componentSourceSelections[type] ?: SettingsManager.getComponentSource(type)
        return componentSources.firstOrNull { source ->
            source.id == selectedId && componentCatalogs[source.id]?.get(type).orEmpty().isNotEmpty()
        } ?: componentSources.firstOrNull { source ->
            componentCatalogs[source.id]?.get(type).orEmpty().isNotEmpty()
        }
    }

    val currentTurnipSource = turnipSources.firstOrNull { it.id == turnipSourceId }
        ?: turnipSources.firstOrNull()
    val currentQualcommSource = qualcommSources.firstOrNull { it.id == qualcommSourceId }
        ?: qualcommSources.firstOrNull()

    val sections = buildList {
        if (turnipReleases.isNotEmpty()) {
            add(
                HubSection(
                    id = "turnip",
                    title = "Turnip",
                    subtitle = appString(R.string.mesa_gpu_driver),
                    latest = turnipReleases.firstOrNull()?.tagName ?: "—",
                    source = currentTurnipSource?.name ?: appString(R.string.unknown),
                    icon = Icons.Default.Eco
                )
            )
        }
        if (showQualcomm) {
            add(
                HubSection(
                    id = "qualcomm",
                    title = "Qualcomm",
                    subtitle = appString(R.string.official_gpu_driver),
                    latest = qualcommReleases.firstOrNull()?.tagName ?: "—",
                    source = currentQualcommSource?.name ?: appString(R.string.unknown),
                    icon = Icons.Default.Memory
                )
            )
        }
        componentOrder.forEach { type ->
            val source = currentComponentSource(type)
            val list = source?.let { componentCatalogs[it.id]?.get(type).orEmpty() }.orEmpty()
            if (list.isNotEmpty()) {
                add(
                    HubSection(
                        id = "component:$type",
                        title = type,
                        subtitle = componentSubtitle(type),
                        latest = list.firstOrNull()?.verName ?: "—",
                        source = source?.name ?: appString(R.string.unknown),
                        icon = componentIcon(type)
                    )
                )
            }
        }
    }

    LaunchedEffect(sections.map { it.id }) {
        if (sections.isNotEmpty() && sections.none { it.id == selectedSection }) {
            onSelectedSectionChange(sections.first().id)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "device_summary") {
            DeviceSummaryCard(deviceInfo = deviceInfo, isLoading = isLoading)
        }

        item(key = "downloads_title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(appString(R.string.download_hub), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        appString(R.string.download_hub_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text(if (sourceCatalogRemote) appString(R.string.live_sources) else appString(R.string.fallback_sources)) },
                    icon = {
                        Icon(
                            if (sourceCatalogRemote) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        if (isLoading && sections.isEmpty()) {
            item(key = "loading") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Text(appString(R.string.fetching_latest))
                    }
                }
            }
        }

        sections.chunked(2).forEachIndexed { index, rowSections ->
            item(key = "index_row_$index") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowSections.forEach { section ->
                        DownloadIndexCard(
                            section = section,
                            selected = section.id == selectedSection,
                            onClick = { onSelectedSectionChange(section.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowSections.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (sections.isNotEmpty()) {
            val selectedInfo = sections.firstOrNull { it.id == selectedSection }
            item(key = "selected_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(appString(R.string.selected), style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        selectedInfo?.let { info ->
                            IconButton(
                                onClick = { onOpenGuide(guideTopicIdForSection(info.id)) }
                            ) {
                                Icon(Icons.Default.Info, contentDescription = appString(R.string.about_component, info.title))
                            }
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.widthIn(max = 150.dp),
                                label = { Text(info.source, maxLines = 1) },
                                leadingIcon = {
                                    Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                }
            }

            item(key = "selected_$selectedSection") {
                Crossfade(targetState = selectedSection, label = "download-section") { sectionId ->
                    when {
                        sectionId == "turnip" -> {
                            TurnipDriverSection(
                                adrenoSeries = deviceInfo?.adrenoSeries,
                                sources = turnipSources,
                                currentSourceId = turnipSourceId,
                                onSourceChange = onTurnipSourceChange,
                                releases = turnipReleases,
                                selectionKey = "turnip:$turnipSourceId",
                                onDownload = onDownloadAsset
                            )
                        }

                        sectionId == "qualcomm" && currentQualcommSource != null -> {
                            DriverCardDynamic(
                                title = appString(R.string.qualcomm_driver),
                                description = appString(R.string.qualcomm_driver_desc),
                                icon = Icons.Default.Memory,
                                sources = qualcommSources,
                                currentSourceId = qualcommSourceId,
                                onSourceChange = onQualcommSourceChange,
                                releases = qualcommReleases,
                                selectionKey = "qualcomm:$qualcommSourceId",
                                onDownload = onDownloadAsset
                            )
                        }

                        sectionId.startsWith("component:") -> {
                            val type = sectionId.substringAfter("component:")
                            val currentSource = currentComponentSource(type)
                            if (currentSource != null) {
                                val sourcesForType = componentSources.filter { source ->
                                    componentCatalogs[source.id]?.get(type).orEmpty().isNotEmpty()
                                }
                                ComponentSection(
                                    type = type,
                                    sources = sourcesForType,
                                    currentSource = currentSource,
                                    components = componentCatalogs[currentSource.id]?.get(type).orEmpty(),
                                    selectionKey = "component:$type:${currentSource.id}",
                                    onSourceChange = { sourceId ->
                                        componentSourceSelections[type] = sourceId
                                        SettingsManager.setComponentSource(type, sourceId)
                                    },
                                    onDownload = onDownloadComponent
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "footer") {
            Text(
                text = appString(R.string.versions_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DeviceSummaryCard(deviceInfo: DeviceInfo?, isLoading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(appString(R.string.your_device), style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (isLoading) appString(R.string.detecting_hardware) else deviceInfo?.gpuRenderer ?: appString(R.string.hardware_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2
                    )
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (deviceInfo != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeviceStat(
                        icon = Icons.Default.Android,
                        label = appString(R.string.android_label),
                        value = deviceInfo.androidVersion,
                        modifier = Modifier.weight(1f)
                    )
                    DeviceStat(
                        icon = Icons.Default.Memory,
                        label = appString(R.string.gpu_label),
                        value = "Adreno ${deviceInfo.adrenoSeries}",
                        modifier = Modifier.weight(1f)
                    )
                    DeviceStat(
                        icon = Icons.Default.Storage,
                        label = appString(R.string.ram_label),
                        value = deviceInfo.ram,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceStat(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
private fun DownloadIndexCard(
    section: HubSection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 142.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                ) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        modifier = Modifier.padding(9.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = appString(R.string.selected),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            Text(
                section.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                appString(R.string.source_format, section.source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                appString(R.string.latest_format, section.latest),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}

private fun componentIcon(type: String): ImageVector = when (type) {
    "Wine" -> Icons.Default.WineBar
    "Proton" -> Icons.Default.Bolt
    "Box64" -> Icons.Default.Inventory2
    "WOWBox64" -> Icons.Default.AutoAwesome
    "DXVK" -> Icons.Default.SportsEsports
    "FEXCore" -> Icons.Default.DeveloperBoard
    "VKD3D" -> Icons.Default.ViewInAr
    "D7VK" -> Icons.Default.Gamepad
    else -> Icons.Default.Extension
}

@Composable
private fun componentSubtitle(type: String): String = when (type) {
    "Wine" -> appString(R.string.component_wine_subtitle)
    "Proton" -> appString(R.string.component_proton_subtitle)
    "Box64" -> appString(R.string.component_box64_subtitle)
    "WOWBox64" -> appString(R.string.component_wowbox64_subtitle)
    "DXVK" -> appString(R.string.component_dxvk_subtitle)
    "FEXCore" -> appString(R.string.component_fex_subtitle)
    "VKD3D" -> appString(R.string.component_vkd3d_subtitle)
    "D7VK" -> appString(R.string.component_d7vk_subtitle)
    else -> appString(R.string.runtime_component)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnipDriverSection(
    adrenoSeries: String?,
    sources: List<TurnipSource>,
    currentSourceId: String,
    onSourceChange: (String) -> Unit,
    releases: List<GithubRelease>,
    selectionKey: String,
    onDownload: (GithubRelease, GithubAsset) -> Unit
) {
    val currentSource = sources.firstOrNull { it.id == currentSourceId } ?: sources.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DownloadPanelHeader(
                icon = Icons.Default.Eco,
                title = appString(R.string.turnip_driver),
                description = when (adrenoSeries) {
                    "8xx" -> appString(R.string.turnip_desc_8xx)
                    "6xx", "7xx" -> appString(R.string.turnip_desc_6_7)
                    else -> appString(R.string.turnip_desc_generic)
                }
            )

            if (currentSource != null) {
                SourcePickerCard(
                    title = appString(R.string.driver_source),
                    currentName = currentSource.name,
                    currentDescription = currentSource.description,
                    currentExperimental = currentSource.experimental,
                    options = sources.map { source ->
                        SourcePickerOption(
                            id = source.id,
                            name = source.name,
                            description = source.description,
                            experimental = source.experimental
                        )
                    },
                    onSelected = { sourceId ->
                        if (sourceId != currentSourceId) onSourceChange(sourceId)
                    }
                )
            }

            DriverReleasePicker(
                releases = releases,
                selectionKey = selectionKey,
                onDownload = onDownload,
                buttonLabel = appString(R.string.download_turnip)
            )
        }
    }
}

private data class SourcePickerOption(
    val id: String,
    val name: String,
    val description: String,
    val experimental: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePickerCard(
    title: String,
    currentName: String,
    currentDescription: String,
    currentExperimental: Boolean,
    options: List<SourcePickerOption>,
    onSelected: (String) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(currentName, style = MaterialTheme.typography.titleMedium)
                        if (currentExperimental) {
                            Spacer(Modifier.width(6.dp))
                            SuggestionChip(
                                onClick = {},
                                label = { Text(appString(R.string.experimental)) }
                            )
                        }
                    }
                    if (currentDescription.isNotBlank()) {
                        Text(
                            currentDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                            maxLines = 2
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = { showSheet = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = options.size > 1
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (options.size > 1) appString(R.string.change_source) else appString(R.string.only_source_available))
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(appString(R.string.choose_source), style = MaterialTheme.typography.headlineSmall)
                Text(
                    appString(R.string.emuhub_downloads_upstream),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                options.forEach { option ->
                    val selected = option.name == currentName
                    Card(
                        onClick = {
                            onSelected(option.id)
                            showSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ) {
                                Icon(
                                    if (selected) Icons.Default.Check else Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.padding(9.dp),
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(option.name, style = MaterialTheme.typography.titleMedium)
                                    if (option.experimental) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            appString(R.string.experimental),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                                if (option.description.isNotBlank()) {
                                    Text(
                                        option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun DownloadPanelHeader(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverCardDynamic(
    title: String,
    description: String,
    icon: ImageVector,
    sources: List<QualcommSource>,
    currentSourceId: String,
    onSourceChange: (String) -> Unit,
    releases: List<GithubRelease>,
    selectionKey: String,
    onDownload: (GithubRelease, GithubAsset) -> Unit
) {
    val currentSource = sources.firstOrNull { it.id == currentSourceId } ?: sources.firstOrNull()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DownloadPanelHeader(icon = icon, title = title, description = description)
            if (currentSource != null) {
                SourcePickerCard(
                    title = appString(R.string.driver_source),
                    currentName = currentSource.name,
                    currentDescription = currentSource.description,
                    currentExperimental = currentSource.experimental,
                    options = sources.map { source ->
                        SourcePickerOption(
                            id = source.id,
                            name = source.name,
                            description = source.description,
                            experimental = source.experimental
                        )
                    },
                    onSelected = { sourceId ->
                        if (sourceId != currentSourceId) onSourceChange(sourceId)
                    }
                )
            }
            DriverReleasePicker(
                releases = releases,
                selectionKey = selectionKey,
                onDownload = onDownload,
                buttonLabel = appString(R.string.download_type, title)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverReleasePicker(
    releases: List<GithubRelease>,
    selectionKey: String,
    onDownload: (GithubRelease, GithubAsset) -> Unit,
    buttonLabel: String
) {
    val context = LocalContext.current
    val selectVersionFileMessage = appString(R.string.select_version_file)
    var expandedRelease by remember { mutableStateOf(false) }
    var expandedAsset by remember { mutableStateOf(false) }
    var selectedTag by rememberSaveable(selectionKey) {
        mutableStateOf(SettingsManager.getSelectedReleaseTag(selectionKey))
    }

    val latestRelease = releases.firstOrNull()
    val selectedRelease = remember(releases, selectedTag) {
        releases.firstOrNull { it.tagName == selectedTag } ?: releases.firstOrNull()
    }

    // If a previously saved release disappeared, gracefully fall back to the
    // newest available one. User choices are never reset just by navigating.
    LaunchedEffect(selectionKey, releases) {
        val savedTag = SettingsManager.getSelectedReleaseTag(selectionKey)
        val resolved = releases.firstOrNull { it.tagName == savedTag } ?: releases.firstOrNull()
        if (resolved != null && selectedTag != resolved.tagName) {
            selectedTag = resolved.tagName
            SettingsManager.setSelectedReleaseTag(selectionKey, resolved.tagName)
        }
    }

    val currentReleaseTag = selectedRelease?.tagName.orEmpty()
    var selectedAssetName by rememberSaveable(selectionKey, currentReleaseTag) {
        mutableStateOf(
            if (currentReleaseTag.isNotEmpty()) {
                SettingsManager.getSelectedAssetName(selectionKey, currentReleaseTag)
            } else null
        )
    }
    val selectedAsset = remember(selectedRelease, selectedAssetName) {
        selectedRelease?.assets?.firstOrNull { it.name == selectedAssetName }
            ?: selectedRelease?.assets?.firstOrNull()
    }

    if (releases.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Text(
                appString(R.string.no_compatible_releases),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = selectedRelease?.tagName ?: "",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        if (selectedRelease?.tagName == latestRelease?.tagName) {
            SuggestionChip(
                onClick = {},
                label = { Text(appString(R.string.latest)) },
                icon = { Icon(Icons.Default.NewReleases, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
    }

    ExposedDropdownMenuBox(
        expanded = expandedRelease,
        onExpandedChange = { expandedRelease = !expandedRelease }
    ) {
        OutlinedTextField(
            value = selectedRelease?.let { "${it.tagName} — ${it.name}" } ?: "",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRelease) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text(appString(R.string.version)) },
            shape = RoundedCornerShape(18.dp),
            maxLines = 1
        )
        ExposedDropdownMenu(
            expanded = expandedRelease,
            onDismissRequest = { expandedRelease = false }
        ) {
            releases.forEachIndexed { index, release ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(release.tagName)
                            Text(
                                if (index == 0) "${release.name} • Latest" else release.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        selectedTag = release.tagName
                        SettingsManager.setSelectedReleaseTag(selectionKey, release.tagName)
                        expandedRelease = false
                    }
                )
            }
        }
    }

    if ((selectedRelease?.assets?.size ?: 0) > 1) {
        ExposedDropdownMenuBox(
            expanded = expandedAsset,
            onExpandedChange = { expandedAsset = !expandedAsset }
        ) {
            OutlinedTextField(
                value = selectedAsset?.name ?: "",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAsset) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                label = { Text(appString(R.string.file)) },
                shape = RoundedCornerShape(18.dp),
                maxLines = 1
            )
            ExposedDropdownMenu(
                expanded = expandedAsset,
                onDismissRequest = { expandedAsset = false }
            ) {
                selectedRelease?.assets?.forEach { asset ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(asset.name)
                                Text(
                                    formatBytes(asset.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            selectedAssetName = asset.name
                            selectedRelease.tagName.let { tag ->
                                SettingsManager.setSelectedAssetName(selectionKey, tag, asset.name)
                            }
                            expandedAsset = false
                        }
                    )
                }
            }
        }
    }

    Button(
        onClick = {
            val release = selectedRelease
            val asset = selectedAsset
            if (release != null && asset != null) {
                // Also persist the automatically selected first asset. This makes
                // the exact choice stable even when a release has multiple files.
                SettingsManager.setSelectedReleaseTag(selectionKey, release.tagName)
                SettingsManager.setSelectedAssetName(selectionKey, release.tagName, asset.name)
                onDownload(release, asset)
            } else {
                Toast.makeText(context, selectVersionFileMessage, Toast.LENGTH_SHORT).show()
            }
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Icon(Icons.Default.Download, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(buttonLabel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentSection(
    type: String,
    sources: List<ComponentSource>,
    currentSource: ComponentSource,
    components: List<Component>,
    selectionKey: String,
    onSourceChange: (String) -> Unit,
    onDownload: (Component) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedVersion by rememberSaveable(selectionKey) {
        mutableStateOf(SettingsManager.getSelectedComponentVersion(selectionKey))
    }
    val context = LocalContext.current
    val selectVersionMessage = appString(R.string.select_version)
    val latestComponent = components.firstOrNull()
    val selected = remember(components, selectedVersion) {
        components.firstOrNull { it.verName == selectedVersion } ?: components.firstOrNull()
    }

    LaunchedEffect(selectionKey, components) {
        val saved = SettingsManager.getSelectedComponentVersion(selectionKey)
        val resolved = components.firstOrNull { it.verName == saved } ?: components.firstOrNull()
        if (resolved != null && selectedVersion != resolved.verName) {
            selectedVersion = resolved.verName
            SettingsManager.setSelectedComponentVersion(selectionKey, resolved.verName)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DownloadPanelHeader(
                icon = componentIcon(type),
                title = type,
                description = componentSubtitle(type)
            )

            SourcePickerCard(
                title = appString(R.string.source_type, type),
                currentName = currentSource.name,
                currentDescription = currentSource.description,
                currentExperimental = currentSource.experimental,
                options = sources.map { source ->
                    SourcePickerOption(
                        id = source.id,
                        name = source.name,
                        description = source.description,
                        experimental = source.experimental
                    )
                },
                onSelected = onSourceChange
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selected?.verName ?: appString(R.string.no_version),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (selected?.verName == latestComponent?.verName && selected != null) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(appString(R.string.latest)) },
                        icon = { Icon(Icons.Default.NewReleases, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selected?.verName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    label = { Text(appString(R.string.version)) },
                    supportingText = { Text(appString(R.string.from_source, currentSource.name)) },
                    shape = RoundedCornerShape(18.dp)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    components.forEachIndexed { index, component ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        if (index == 0) appString(R.string.version_latest_format, component.verName) else component.verName
                                    )
                                    Text(
                                        currentSource.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedVersion = component.verName
                                SettingsManager.setSelectedComponentVersion(selectionKey, component.verName)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    selected?.let {
                        SettingsManager.setSelectedComponentVersion(selectionKey, it.verName)
                        onDownload(it)
                    } ?: Toast.makeText(context, selectVersionMessage, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(appString(R.string.download_type, type))
            }
        }
    }
}

