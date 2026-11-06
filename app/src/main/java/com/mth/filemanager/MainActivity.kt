package com.mth.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.graphics.BitmapFactory
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.Locale

private val Teal = Color(0xFF008F83)
private val Page = Color(0xFFF3F7F6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(0, 143, 131)
        window.navigationBarColor = android.graphics.Color.rgb(0, 143, 131)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent { MthExploreApp() }
    }
}

@Composable
private fun MthExploreApp() {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Teal, secondary = Teal, background = Page, surface = Color.White,
        surfaceVariant = Color.White, onSurface = Color(0xFF1C2B32),
        onSurfaceVariant = Color(0xFF68777B)
    )) {
        Surface(Modifier.fillMaxSize(), color = Page) { ExplorerScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ExplorerScreen() {
    val context = LocalContext.current
    val activity = context as MainActivity
    val owner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(hasAccess()) }
    var folder by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var refresh by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var menuFile by remember { mutableStateOf<File?>(null) }
    var deleteFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFiles by remember { mutableStateOf<Set<File>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(context.getSharedPreferences("mth_explore", 0).getBoolean("show_hidden", false)) }
    var extensionFilter by remember { mutableStateOf("Tudo") }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = hasAccess()
                refresh++
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    BackHandler(folder.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
        folder.parentFile?.let { folder = it; refresh++ }
    }

    var mediaFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var mediaLoading by remember { mutableStateOf(false) }
    var deviceSearchResults by remember { mutableStateOf<List<File>>(emptyList()) }
    var deviceSearchLoading by remember { mutableStateOf(false) }
    val isMediaCategory = extensionFilter in listOf("Imagens", "Vídeos", "Áudios")

    LaunchedEffect(allowed, extensionFilter, refresh, search, showHidden) {
        if (allowed && extensionFilter in listOf("Imagens", "Vídeos", "Áudios")) {
            mediaLoading = true
            mediaFiles = emptyList()
            mediaFiles = withContext(Dispatchers.IO) {
                queryMediaFiles(context, extensionFilter, search, showHidden)
            }
            mediaLoading = false
        } else {
            mediaLoading = false
        }
    }

    val folderFiles = remember(folder.absolutePath, refresh, search, allowed, showHidden, extensionFilter) {
        if (!allowed || isMediaCategory) emptyList() else folder.listFiles()
            ?.filter { showHidden || !it.name.startsWith(".") }
            ?.filter { it.name.contains(search, ignoreCase = true) || it.extension.contains(search.removePrefix("."), ignoreCase = true) }
            ?.filter { extensionFilter == "Tudo" || (it.isFile && extensionCategory(it) == extensionFilter) }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.getDefault()) })
            ?: emptyList()
    }
    val files = if (isMediaCategory) mediaFiles else folderFiles
    val toggleSelectedFile: (File) -> Unit = { file ->
        val updated = if (file in selectedFiles) selectedFiles - file else selectedFiles + file
        selectedFiles = updated
        selectionMode = updated.isNotEmpty()
    }
    val requestDeleteSelected = {
        deleteFiles = selectedFiles.toList()
        selectedFiles = emptySet()
        selectionMode = false
    }

    LaunchedEffect(tab, searching, search, allowed, refresh, showHidden) {
        if (tab == 0 && searching && search.isNotBlank() && allowed) {
            deviceSearchLoading = true
            delay(250)
            deviceSearchResults = withContext(Dispatchers.IO) {
                queryDeviceFiles(context, search, showHidden)
            }
            deviceSearchLoading = false
        } else {
            deviceSearchResults = emptyList()
            deviceSearchLoading = false
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MthExplore", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF1C2B32))
                        Text(if (folder.absolutePath == Environment.getExternalStorageDirectory().absolutePath)
                            "Explore seus arquivos" else folder.name,
                            style = MaterialTheme.typography.titleMedium, color = Color(0xFF68777B))
                    }
                    IconButton(onClick = { searching = !searching; search = "" }) {
                        Icon(Icons.Default.Search, "Pesquisar", tint = Teal)
                    }
                }
                if (searching) OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(if (isMediaCategory) "Pesquisar no dispositivo" else "Pesquisar nesta pasta") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Limpar") } }
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(tab == 0, { tab = 0; folder = Environment.getExternalStorageDirectory(); refresh++ },
                    icon = { Icon(Icons.Default.Home, null) }, label = { Text("Início") })
                NavigationBarItem(tab == 1, { tab = 1; refresh++ },
                    icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Arquivos") })
                NavigationBarItem(tab == 2, { tab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Ajustes") })
            }
        },
        containerColor = Page
    ) { padding ->
        when {
            !allowed -> Column(
                Modifier.fillMaxSize().padding(padding).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.FolderOpen, null, tint = Teal, modifier = Modifier.size(64.dp))
                Text("Acesso aos arquivos", style = MaterialTheme.typography.headlineSmall)
                Text("Para explorar o conteúdo do celular sem escolher uma pasta, autorize o acesso a todos os arquivos nas configurações do Android.")
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + context.packageName))
                    try { activity.startActivity(intent) }
                    catch (_: Exception) { activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                }) { Text("Autorizar acesso aos arquivos") }
            }
            tab == 2 -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
                Text("Acesso aos arquivos do dispositivo autorizado pelo Android.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mostrar arquivos ocultos", style = MaterialTheme.typography.titleMedium)
                        Text("Exibe arquivos e pastas cujo nome começa com ponto.", color = Color.Gray)
                    }
                    Switch(checked = showHidden, onCheckedChange = {
                        showHidden = it
                        context.getSharedPreferences("mth_explore", 0).edit().putBoolean("show_hidden", it).apply()
                        refresh++
                    })
                }
            }
            tab == 0 -> Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (searching && search.isNotBlank()) {
                    Button(onClick = { tab = 1; folder = Environment.getExternalStorageDirectory(); refresh++ }) {
                        Icon(Icons.Default.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir arquivos do dispositivo")
                    }
                    Text("Resultados da pesquisa", style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (selectionMode) Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${selectedFiles.size} selecionados", modifier = Modifier.weight(1f),
                            color = Color(0xFF68777B))
                        TextButton(onClick = {
                            selectionMode = false
                            selectedFiles = emptySet()
                        }) { Text("Cancelar") }
                        IconButton(onClick = requestDeleteSelected, enabled = selectedFiles.isNotEmpty()) {
                            Icon(Icons.Default.Delete, "Excluir selecionados", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    when {
                        deviceSearchLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        deviceSearchResults.isEmpty() -> Box(
                            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Nenhum arquivo encontrado para \"$search\".", color = Color.Gray,
                                textAlign = TextAlign.Center, softWrap = true)
                        }
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(deviceSearchResults, key = { it.absolutePath }) { file ->
                                Card(
                                    Modifier.fillMaxWidth().combinedClickable(
                                        onClick = { openFile(context, file) },
                                        onDoubleClick = {
                                            if (file.isFile) toggleSelectedFile(file)
                                        },
                                        onLongClick = {
                                            if (file.isFile) toggleSelectedFile(file)
                                        }
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (file in selectedFiles) Color(0xFFE4F4F1) else Color.White
                                    )
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (file in selectedFiles) Icons.Default.CheckCircle else iconFor(file), null,
                                            tint = Teal, modifier = Modifier.size(30.dp))
                                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.titleSmall)
                                            Text(file.parent ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                color = Color(0xFF68777B), style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(sizeText(file.length()), color = Color(0xFF68777B),
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            tab = 1
                            folder = Environment.getExternalStorageDirectory()
                            refresh++
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Teal)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, null, tint = Color.White, modifier = Modifier.size(38.dp))
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Text("Armazenamento interno", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                Text("Toque para explorar seus arquivos", color = Color.White)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Color.White)
                        }
                    }
                    StorageInfoCard()
                    Text("Acesso rápido", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { tab = 1; folder = Environment.getExternalStorageDirectory(); refresh++ }) {
                        Icon(Icons.Default.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir arquivos do dispositivo")
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isMediaCategory) "$extensionFilter do dispositivo" else if (folder.absolutePath == Environment.getExternalStorageDirectory().absolutePath)
                        "Pastas do dispositivo" else folder.name,
                        Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    if (folder.absolutePath != Environment.getExternalStorageDirectory().absolutePath)
                        IconButton(onClick = { folder.parentFile?.let { folder = it }; refresh++ }) {
                            Icon(Icons.Default.ArrowUpward, "Pasta anterior")
                        }
                }
                if (selectionMode) Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedFiles.size} selecionados", modifier = Modifier.weight(1f),
                        color = Color(0xFF68777B))
                    TextButton(onClick = {
                        selectionMode = false
                        selectedFiles = emptySet()
                    }) { Text("Cancelar") }
                    IconButton(onClick = requestDeleteSelected, enabled = selectedFiles.isNotEmpty()) {
                        Icon(Icons.Default.Delete, "Excluir selecionados", tint = MaterialTheme.colorScheme.error)
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("Tudo", "Imagens", "Vídeos", "Áudios", "Documentos").forEach { category ->
                        FilterChip(selected = extensionFilter == category, onClick = { extensionFilter = category },
                            label = { Text(category, maxLines = 1) })
                    }
                }
                if (mediaLoading && isMediaCategory) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                } else if (files.isEmpty()) Box(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isMediaCategory) "Nenhum arquivo desta categoria foi encontrado no dispositivo."
                        else "Nenhum arquivo encontrado nesta pasta.", color = Color.Gray,
                        textAlign = TextAlign.Center, softWrap = true)
                } else LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(files, key = { it.absolutePath }) { file ->
                        Card(Modifier.fillMaxWidth().combinedClickable(
                            onClick = {
                                if (file.isDirectory) { folder = file; tab = 1; search = ""; extensionFilter = "Tudo"; refresh++ }
                                else openFile(context, file)
                            },
                            onDoubleClick = {
                                if (file.isFile) toggleSelectedFile(file)
                            },
                            onLongClick = {
                                if (file.isFile) toggleSelectedFile(file)
                            }
                        ), shape = RoundedCornerShape(17.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (file in selectedFiles) Color(0xFFE4F4F1) else Color.White
                            ),
                            elevation = CardDefaults.cardElevation(1.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                if (file in selectedFiles) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Teal, modifier = Modifier.size(34.dp))
                                } else if (!file.isDirectory && isImageFile(file)) {
                                    ImageThumbnail(file)
                                } else {
                                    Icon(if (file.isDirectory) Icons.Default.Folder else iconFor(file),
                                        null, tint = Teal, modifier = Modifier.size(34.dp))
                                }
                                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                    Text(file.name, style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (file.isDirectory) "Pasta" else sizeText(file.length()),
                                        color = Color(0xFF68777B))
                                }
                                IconButton(onClick = { menuFile = file }) {
                                    Icon(Icons.Default.MoreVert, "Mais opções")
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }

    menuFile?.let { file ->
        AlertDialog(onDismissRequest = { menuFile = null }, title = { Text(file.name) },
            text = { Text(if (file.isDirectory) "Pasta" else sizeText(file.length())) },
            confirmButton = { TextButton(onClick = {
                if (file.isFile) shareFile(context, file)
                else Toast.makeText(context, "Selecione um arquivo para compartilhar.", Toast.LENGTH_SHORT).show()
                menuFile = null
            }) { Text("Compartilhar") } },
            dismissButton = {
                Row {
                    if (file.isFile) TextButton(onClick = {
                        deleteFiles = listOf(file)
                        menuFile = null
                    }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { menuFile = null }) { Text("Fechar") }
                }
            })
    }

    if (deleteFiles.isNotEmpty()) {
        val filesToDelete = deleteFiles
        AlertDialog(
            onDismissRequest = { deleteFiles = emptyList() },
            title = { Text(if (filesToDelete.size == 1) "Excluir arquivo?" else "Excluir ${filesToDelete.size} arquivos?") },
            text = { Text(if (filesToDelete.size == 1)
                "O arquivo \"${filesToDelete.first().name}\" será excluído permanentemente."
                else "Os ${filesToDelete.size} arquivos selecionados serão excluídos permanentemente.") },
            confirmButton = {
                TextButton(onClick = {
                    val deletedCount = filesToDelete.count { file ->
                        runCatching { file.isFile && file.delete() }.getOrDefault(false)
                    }
                    deleteFiles = emptyList()
                    selectedFiles = emptySet()
                    selectionMode = false
                    refresh++
                    val message = if (deletedCount == filesToDelete.size) {
                        if (deletedCount == 1) "Arquivo excluído." else "$deletedCount arquivos excluídos."
                    } else "$deletedCount de ${filesToDelete.size} arquivos excluídos."
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteFiles = emptyList() }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun StorageInfoCard() {
    val stat = remember { StatFs(Environment.getExternalStorageDirectory().absolutePath) }
    val total = stat.totalBytes
    val free = stat.availableBytes
    val used = (total - free).coerceAtLeast(0L)
    Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Armazenamento", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { if (total > 0) used.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth(), color = Teal)
            Text("Usado: " + sizeText(used) + " • Livre: " + sizeText(free) + " • Total: " + sizeText(total),
                style = MaterialTheme.typography.bodySmall, color = Color(0xFF68777B))
        }
    }
}


private fun queryDeviceFiles(context: android.content.Context, search: String, showHidden: Boolean): List<File> {
    val normalized = search.trim().removePrefix(".")
    if (normalized.isBlank()) return emptyList()
    val results = mutableListOf<File>()
    val collection = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(MediaStore.MediaColumns.DATA)
    runCatching {
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val column = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (column >= 0) while (cursor.moveToNext() && results.size < 200) {
                val path = cursor.getString(column) ?: continue
                val file = File(path)
                if (file.isFile && (showHidden || !file.name.startsWith(".")) &&
                    (file.name.contains(search.trim(), ignoreCase = true) ||
                        file.extension.contains(normalized, ignoreCase = true))) {
                    results.add(file)
                }
            }
        }
    }
    return results
}

private fun queryMediaFiles(context: android.content.Context, category: String, search: String, showHidden: Boolean): List<File> {
    val collection = when (category) {
        "Imagens" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        "Vídeos" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        "Áudios" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> return emptyList()
    }
    val result = mutableListOf<File>()
    val projection = arrayOf(MediaStore.MediaColumns.DATA)
    runCatching {
        context.contentResolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE ASC")?.use { cursor ->
            val column = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (column >= 0) while (cursor.moveToNext()) {
                val path = cursor.getString(column) ?: continue
                val file = File(path)
                if (file.isFile && (showHidden || !file.name.startsWith(".")) &&
                    (search.isBlank() || file.name.contains(search, ignoreCase = true) ||
                        file.extension.contains(search.removePrefix("."), ignoreCase = true))) {
                    result.add(file)
                }
            }
        }
    }
    return result
}

private fun extensionCategory(file: File): String = when (file.extension.lowercase()) {
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> "Imagens"
    "mp4", "mkv", "avi", "mov", "3gp" -> "Vídeos"
    "mp3", "wav", "m4a", "ogg", "aac", "flac" -> "Áudios"
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv" -> "Documentos"
    else -> "Outros"
}

private fun isImageFile(file: File) = extensionCategory(file) == "Imagens"

@Composable
private fun ImageThumbnail(file: File) {
    val bitmap = remember(file.absolutePath, file.lastModified()) {
        runCatching {
            val options = BitmapFactory.Options().apply { inSampleSize = 8 }
            BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = file.name, modifier = Modifier.size(42.dp), contentScale = ContentScale.Crop)
    } else {
        Icon(Icons.Default.Image, null, tint = Teal, modifier = Modifier.size(34.dp))
    }
}

private fun hasAccess() = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

private fun iconFor(file: File) = when (file.extension.lowercase()) {
    "jpg", "jpeg", "png", "gif", "webp", "bmp" -> Icons.Default.Image
    "mp4", "mkv", "avi", "mov" -> Icons.Default.VideoFile
    "mp3", "wav", "m4a", "ogg" -> Icons.Default.AudioFile
    "pdf" -> Icons.Default.PictureAsPdf
    else -> Icons.Default.InsertDriveFile
}

private fun sizeText(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun mime(file: File) = when (file.extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "pdf" -> "application/pdf"
    "txt" -> "text/plain"
    "mp4" -> "video/mp4"
    "mp3" -> "audio/mpeg"
    else -> "*/*"
}

private fun openFile(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime(file)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    } catch (_: Exception) {
        Toast.makeText(context, "Não foi possível abrir este arquivo.", Toast.LENGTH_LONG).show()
    }
}

private fun shareFile(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime(file); putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartilhar arquivo"))
    } catch (_: Exception) {
        Toast.makeText(context, "Não foi possível compartilhar este arquivo.", Toast.LENGTH_LONG).show()
    }
}
