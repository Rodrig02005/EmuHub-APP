package com.notzeetaa.emuhub

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Downloads")
                        Text(
                            "Your download library",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                                Text("Download library", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "Everything you download from EmuHub in one place.",
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
                                label = "Active",
                                modifier = Modifier.weight(1f)
                            )
                            DownloadStatCard(
                                icon = Icons.Default.Inventory2,
                                value = completed.size.toString(),
                                label = "Saved",
                                modifier = Modifier.weight(1f)
                            )
                            DownloadStatCard(
                                icon = Icons.Default.Storage,
                                value = formatBytes(completedBytes),
                                label = "Stored",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (active.isNotEmpty()) {
                item(key = "active_header") {
                    DownloadListHeader(
                        title = "Downloading now",
                        subtitle = "${active.size} active download${if (active.size == 1) "" else "s"}",
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
                                            when {
                                                download.totalBytes > 0L -> "${download.progress}% complete"
                                                download.downloadedBytes > 0L -> "Downloading…"
                                                else -> "Connecting…"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        label = { Text("Active") }
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
                                        if (download.totalBytes > 0L) formatBytes(download.totalBytes) else "Size pending",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (completed.isNotEmpty()) {
                item(key = "completed_header") {
                    DownloadListHeader(
                        title = "Downloaded files",
                        subtitle = "Ready to open, share or manage",
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
                                        contentDescription = "Downloaded",
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
                                            context.startActivity(Intent.createChooser(intent, "Open with"))
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                                        Spacer(Modifier.width(7.dp))
                                        Text("Open")
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
                                            context.startActivity(Intent.createChooser(shareIntent, "Share"))
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null)
                                        Spacer(Modifier.width(7.dp))
                                        Text("Share")
                                    }

                                    FilledTonalIconButton(
                                        onClick = { showDeleteDialog = true },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                                    }
                                }
                            }
                        }

                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                title = { Text("Delete file") },
                                text = { Text("Are you sure you want to delete ${file.fileName}?") },
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
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) {
                                        Text("Cancel")
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
                            Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Downloads started from the hub will appear here.",
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
                Toast.makeText(context, "Error deleting: ${e.message}", Toast.LENGTH_LONG).show()
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
    onSourceCatalogChanged: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
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

    LaunchedEffect(currentFolderUri) {
        displayPath = if (currentFolderUri != null) {
            val uri = Uri.parse(currentFolderUri)
            DocumentFile.fromTreeUri(context, uri)?.name ?: uri.path
        } else {
            "Downloads (default)"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                    title = "Appearance",
                    subtitle = "Personalize EmuHub with Material 3 themes."
                )
            }

            item {
                SettingsCard(title = "Theme mode") {
                    Text(
                        text = "Choose how light and dark mode are applied.",
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
                SettingsCard(title = "Color theme") {
                    Text(
                        text = "Dynamic uses your Android wallpaper colors when supported.",
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
                    title = "Sources",
                    subtitle = "Manage the remote catalog that tells EmuHub where to fetch releases."
                )
            }

            item {
                SettingsCard(title = "Source catalog") {
                    Text(
                        text = "EmuHub refreshes this JSON automatically. Driver files stay on the original upstream repositories, so new releases appear without rebuilding the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = sourceCatalogUrl,
                        onValueChange = { sourceCatalogUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Catalog URL") },
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
                                    Toast.makeText(context, "Source catalog saved", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Enter a valid http(s) URL", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save")
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
                            Text("Default")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (sourceCatalogUrl == DEFAULT_SOURCE_CATALOG_URL) {
                            "Using EmuHub managed catalog"
                        } else {
                            "Using custom catalog"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                SettingsSectionHeader(
                    icon = Icons.Default.Download,
                    title = "Downloads",
                    subtitle = "Choose where downloaded components are saved."
                )
            }

            item {
                SettingsCard(title = "Download folder") {
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
                                text = displayPath ?: "Downloads (default)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (currentFolderUri == null) "System Downloads folder" else "Custom folder",
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
                        Text("Choose folder")
                    }

                    if (currentFolderUri != null) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                SettingsManager.clearDownloadFolder()
                                currentFolderUri = null
                                displayPath = "Downloads (default)"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reset to default")
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

@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit
) {
    val choices = listOf(
        ThemeMode.SYSTEM to Pair("System", Icons.Default.SettingsBrightness),
        ThemeMode.LIGHT to Pair("Light", Icons.Default.LightMode),
        ThemeMode.DARK to Pair("Dark", Icons.Default.DarkMode),
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
        ColorTheme.DYNAMIC to "Dynamic",
        ColorTheme.EMUHUB to "EmuHub",
        ColorTheme.BLUE to "Blue",
        ColorTheme.PURPLE to "Purple",
        ColorTheme.ORANGE to "Orange"
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
                    subtitle = "Mesa GPU driver",
                    latest = turnipReleases.firstOrNull()?.tagName ?: "—",
                    source = currentTurnipSource?.name ?: "Unknown",
                    icon = Icons.Default.Eco
                )
            )
        }
        if (showQualcomm) {
            add(
                HubSection(
                    id = "qualcomm",
                    title = "Qualcomm",
                    subtitle = "Official GPU driver",
                    latest = qualcommReleases.firstOrNull()?.tagName ?: "—",
                    source = currentQualcommSource?.name ?: "Unknown",
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
                        source = source?.name ?: "Unknown",
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
                    Text("Download hub", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Choose a category to open its downloads.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text(if (sourceCatalogRemote) "Live sources" else "Fallback sources") },
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
                        Text("Fetching the latest drivers, components and source catalog…")
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
                    Text("Selected", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedInfo?.let { info ->
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text(info.source) },
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
                                title = "Qualcomm Driver",
                                description = "Qualcomm proprietary graphics-driver packages. Try these when a game behaves better with the vendor driver.",
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
                text = "Versions are fetched directly from the selected upstream source. Change sources at any time; EmuHub remembers one source per category.",
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
                    Text("Your device", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (isLoading) "Detecting hardware…" else deviceInfo?.gpuRenderer ?: "Hardware unavailable",
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
                        label = "Android",
                        value = deviceInfo.androidVersion,
                        modifier = Modifier.weight(1f)
                    )
                    DeviceStat(
                        icon = Icons.Default.Memory,
                        label = "GPU",
                        value = "Adreno ${deviceInfo.adrenoSeries}",
                        modifier = Modifier.weight(1f)
                    )
                    DeviceStat(
                        icon = Icons.Default.Storage,
                        label = "RAM",
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
                        contentDescription = "Selected",
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
                "Source: ${section.source}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                "Latest: ${section.latest}",
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

private fun componentSubtitle(type: String): String = when (type) {
    "Wine" -> "Windows compatibility layer"
    "Proton" -> "Gaming compatibility layer"
    "Box64" -> "x86_64 translation"
    "WOWBox64" -> "WoW64 translation"
    "DXVK" -> "Direct3D 8–11 to Vulkan"
    "FEXCore" -> "x86/x64 translation"
    "VKD3D" -> "Direct3D 12 to Vulkan"
    "D7VK" -> "Direct3D 7 to Vulkan"
    else -> "Runtime component"
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
                title = "Turnip Driver",
                description = when (adrenoSeries) {
                    "8xx" -> "Turnip builds for Adreno 8xx / Gen8 devices."
                    "6xx", "7xx" -> "Mesa Turnip builds for Adreno 6xx and 7xx devices."
                    else -> "Mesa Turnip graphics drivers for Adreno GPUs."
                }
            )

            if (currentSource != null) {
                SourcePickerCard(
                    title = "Driver source",
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
                buttonLabel = "Download Turnip"
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
                                label = { Text("Experimental") }
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
                Text(if (options.size > 1) "Change source" else "Only source available")
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
                Text("Choose source", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "EmuHub downloads directly from the selected upstream provider.",
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
                                            "Experimental",
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
                    title = "Driver source",
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
                buttonLabel = "Download $title"
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
                "No compatible releases found.",
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
                label = { Text("Latest") },
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
            label = { Text("Version") },
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
                label = { Text("File") },
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
                Toast.makeText(context, "Select a version and file", Toast.LENGTH_SHORT).show()
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
                title = "$type source",
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
                    selected?.verName ?: "No version",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (selected?.verName == latestComponent?.verName && selected != null) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Latest") },
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
                    label = { Text("Version") },
                    supportingText = { Text("From ${currentSource.name}") },
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
                                        if (index == 0) "${component.verName} • Latest" else component.verName
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
                    } ?: Toast.makeText(context, "Select a version", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Download $type")
            }
        }
    }
}

