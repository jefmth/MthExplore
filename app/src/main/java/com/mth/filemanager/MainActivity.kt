package com.mth.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MthFileManagerApp() }
    }
}

@Composable
private fun MthFileManagerApp() {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF008F83),
            secondary = androidx.compose.ui.graphics.Color(0xFF008F83),
            background = androidx.compose.ui.graphics.Color(0xFF101817),
            surface = androidx.compose.ui.graphics.Color(0xFF17211F),
            primaryContainer = androidx.compose.ui.graphics.Color(0xFFBDECE5),
            onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF003731),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEAF2F0),
            onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF52615E)
        )
    } else {
        lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF008F83),
            secondary = androidx.compose.ui.graphics.Color(0xFF008F83),
            background = androidx.compose.ui.graphics.Color(0xFFF3F7F6),
            surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
            primaryContainer = androidx.compose.ui.graphics.Color(0xFFDDF3EF),
            onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF123B37),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
            onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF68777B)
        )
    }
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            FileManagerHome()
        }
    }
}

private data class Category(val title: String, val subtitle: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileManagerHome() {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("mth_file_manager", android.content.Context.MODE_PRIVATE)
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var treeUriText by remember { mutableStateOf(preferences.getString("tree_uri", null)) }
    var currentFolder by remember { mutableStateOf<DocumentFile?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<DocumentFile?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val rootFolder = remember(treeUriText) {
        treeUriText?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }
    }

    LaunchedEffect(treeUriText) {
        if (rootFolder != null) currentFolder = rootFolder
    }

    // Na primeira abertura, apresenta automaticamente o seletor oficial do Android.
    // O usuário escolhe o armazenamento uma única vez; a autorização fica salva.
    LaunchedEffect(Unit) {
        if (treeUriText == null) folderPicker.launch(null)
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                preferences.edit().putString("tree_uri", uri.toString()).apply()
                treeUriText = uri.toString()
                currentFolder = DocumentFile.fromTreeUri(context, uri)
                selectedTab = 1
            } catch (_: Exception) {
                Toast.makeText(context, "Não foi possível autorizar esta pasta.", Toast.LENGTH_LONG).show()
            }
        }
    }

    val categories = listOf(
        Category("Imagens", "Fotos e imagens", Icons.Default.Image),
        Category("Vídeos", "Vídeos do dispositivo", Icons.Default.VideoLibrary),
        Category("Áudios", "Músicas e gravações", Icons.Default.MusicNote),
        Category("Documentos", "PDF, TXT e outros", Icons.Default.InsertDriveFile)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MthExplore", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            if (selectedTab == 1) currentFolder?.name ?: "Arquivos" else "Gerenciador de arquivos",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (selectedTab == 1 && currentFolder != null && rootFolder != null &&
                        currentFolder?.uri != rootFolder.uri
                    ) {
                        IconButton(onClick = {
                            currentFolder = currentFolder?.parentFile ?: rootFolder
                            refreshKey++
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    if (selectedTab == 1 && currentFolder != null) {
                        IconButton(onClick = { showCreateFolder = true }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "Criar pasta")
                        }
                    }
                    IconButton(onClick = { selectedTab = 2 }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selectedTab == 0, { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, null) }, label = { Text("Início") })
                NavigationBarItem(selectedTab == 1, { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Arquivos") })
                NavigationBarItem(selectedTab == 2, { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Ajustes") })
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> HomeContent(
                padding, categories, rootFolder != null,
                { folderPicker.launch(null) }, { selectedTab = 1 }
            )
            1 -> FileBrowserContent(
                padding, currentFolder, refreshKey,
                { folderPicker.launch(null) },
                { currentFolder = it },
                { openFile(context, it) },
                {
                    selectedItem = it
                    renameValue = it.name ?: ""
                }
            )
            else -> PlaceholderContent(
                padding, "Configurações",
                "O tema claro ou escuro acompanha a configuração do Android."
            )
        }
    }

    if (showCreateFolder) {
        AlertDialog(
            onDismissRequest = { showCreateFolder = false },
            title = { Text("Nova pasta") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Nome da pasta") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newFolderName.trim()
                    if (name.isNotEmpty()) {
                        val created = currentFolder?.createDirectory(name)
                        Toast.makeText(
                            context,
                            if (created != null) "Pasta criada." else "Não foi possível criar a pasta.",
                            Toast.LENGTH_SHORT
                        ).show()
                        refreshKey++
                    }
                    newFolderName = ""
                    showCreateFolder = false
                }) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolder = false }) { Text("Cancelar") }
            }
        )
    }

    selectedItem?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text(item.name ?: "Item selecionado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        label = { Text("Novo nome") },
                        singleLine = true
                    )
                    TextButton(onClick = {
                        val ok = renameValue.trim().isNotEmpty() && item.renameTo(renameValue.trim())
                        Toast.makeText(context, if (ok) "Item renomeado." else "Não foi possível renomear.", Toast.LENGTH_SHORT).show()
                        selectedItem = null
                        refreshKey++
                    }) { Text("Renomear") }
                    TextButton(onClick = { showDeleteConfirmation = true }) {
                        Text("Excluir", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedItem = null }) { Text("Fechar") }
            }
        )
    }

    if (showDeleteConfirmation && selectedItem != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Excluir item?") },
            text = { Text("O item será removido do armazenamento selecionado. Esta ação não poderá ser desfeita pelo aplicativo.") },
            confirmButton = {
                TextButton(onClick = {
                    val deleted = selectedItem?.delete() == true
                    Toast.makeText(context, if (deleted) "Item excluído." else "Não foi possível excluir.", Toast.LENGTH_SHORT).show()
                    showDeleteConfirmation = false
                    selectedItem = null
                    refreshKey++
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun HomeContent(
    padding: PaddingValues,
    categories: List<Category>,
    hasStorage: Boolean,
    onChooseStorage: () -> Unit,
    onOpenFiles: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(2.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Storage, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Seus arquivos", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    if (hasStorage) "Armazenamento conectado. Explore suas pastas e arquivos."
                    else "Escolha o armazenamento interno ou cartão SD para começar a explorar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                OutlinedButton(onClick = onChooseStorage) {
                    Text(if (hasStorage) "Adicionar outro local" else "Explorar armazenamento")
                }
                if (hasStorage) TextButton(onClick = onOpenFiles) { Text("Abrir arquivos") }
            }
        }
        Text("Explorar por categoria", style = MaterialTheme.typography.titleLarge)
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(categories.size) { index ->
                val category = categories[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(category.title, style = MaterialTheme.typography.titleMedium)
                        Text(category.subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileBrowserContent(
    padding: PaddingValues,
    folder: DocumentFile?,
    refreshKey: Int,
    onChooseStorage: () -> Unit,
    onOpenFolder: (DocumentFile) -> Unit,
    onOpenFile: (DocumentFile) -> Unit,
    onItemMenu: (DocumentFile) -> Unit
) {
    val files = remember(folder?.uri, refreshKey) {
        folder?.listFiles()?.sortedWith(
            compareBy<DocumentFile> { !it.isDirectory }
                .thenBy { it.name?.lowercase(Locale.getDefault()) ?: "" }
        ) ?: emptyList()
    }

    if (folder == null) {
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Storage, contentDescription = null)
            Spacer(Modifier.height(12.dp))
            Text("Nenhum armazenamento selecionado", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Escolha uma pasta do armazenamento interno ou do cartão SD.")
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onChooseStorage) { Text("Selecionar pasta") }
        }
    } else if (files.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(Modifier.height(8.dp))
            Text("Esta pasta está vazia", style = MaterialTheme.typography.titleMedium)
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(files, key = { it.uri.toString() }) { file ->
                FileRow(
                    file,
                    onClick = { if (file.isDirectory) onOpenFolder(file) else onOpenFile(file) },
                    onMenu = { onItemMenu(file) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileRow(file: DocumentFile, onClick: () -> Unit, onMenu: () -> Unit) {
    val icon = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile
    val detail = if (file.isDirectory) "Pasta" else formatSize(file.length()) + " • " + (file.type ?: "Arquivo")

    ListItem(
        headlineContent = { Text(file.name ?: "Sem nome", maxLines = 1) },
        supportingContent = { Text(detail, maxLines = 1) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}

private fun openFile(context: android.content.Context, file: DocumentFile) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri, file.type ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Não há aplicativo compatível para abrir este arquivo.", Toast.LENGTH_LONG).show()
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return bytes.toString() + " B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
}

@Composable
private fun PlaceholderContent(padding: PaddingValues, title: String, description: String) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}
