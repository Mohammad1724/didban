@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun SftpScreen(t: Str) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val servers = remember { Prefs.loadServers(ctx) }
    var selectedServerIndex by remember { mutableIntStateOf(0) }
    var sshPassword by remember { mutableStateOf("") }

    var currentPath by remember { mutableStateOf("/etc") }
    var fileList by remember { mutableStateOf<List<SftpFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Search & Options
    var searchQuery by remember { mutableStateOf("") }
    var showHiddenFiles by remember { mutableStateOf(true) }
    var sortMode by remember { mutableStateOf(SftpSortMode.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Transfer Progress
    var transferTitle by remember { mutableStateOf<String?>(null) }
    var transferProgress by remember { mutableFloatStateOf(0f) }

    // Editor state
    var editingFile by remember { mutableStateOf<SftpFileItem?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var originalFileContent by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var isReadingFile by remember { mutableStateOf(false) }

    // Modals & Action Dialogs
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var itemToRename by remember { mutableStateOf<SftpFileItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }

    var itemToDelete by remember { mutableStateOf<SftpFileItem?>(null) }

    var itemForChmod by remember { mutableStateOf<SftpFileItem?>(null) }
    var chmodOwnerR by remember { mutableStateOf(true) }
    var chmodOwnerW by remember { mutableStateOf(true) }
    var chmodOwnerX by remember { mutableStateOf(false) }
    var chmodGroupR by remember { mutableStateOf(true) }
    var chmodGroupW by remember { mutableStateOf(false) }
    var chmodGroupX by remember { mutableStateOf(false) }
    var chmodOtherR by remember { mutableStateOf(true) }
    var chmodOtherW by remember { mutableStateOf(false) }
    var chmodOtherX by remember { mutableStateOf(false) }

    var itemForInfo by remember { mutableStateOf<SftpFileItem?>(null) }

    val quickPaths = remember {
        listOf(
            "🌐 /etc/nginx" to "/etc/nginx",
            "⚡ /etc/x-ui" to "/etc/x-ui",
            "🔄 /etc/gost" to "/etc/gost",
            "⚙️ /etc/systemd" to "/etc/systemd/system",
            "📜 /var/log" to "/var/log",
            "📦 /opt" to "/opt",
            "🏠 /root" to "/root",
            "🔒 /etc/ssl" to "/etc/ssl/certs",
            "🐳 /var/lib/docker" to "/var/lib/docker",
            "🌐 /etc/caddy" to "/etc/caddy"
        )
    }

    // File Upload Picker
    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val server = servers.getOrNull(selectedServerIndex) ?: return@rememberLauncherForActivityResult
            scope.launch {
                try {
                    val contentResolver = ctx.contentResolver
                    var displayName = "uploaded_file"
                    var fileSize = -1L

                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIndex != -1) displayName = cursor.getString(nameIndex)
                            if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                        }
                    }

                    val targetRemotePath = if (currentPath == "/") "/$displayName" else "$currentPath/$displayName"
                    val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("خطا در باز کردن فایل محلی")

                    transferTitle = "در حال آپلود $displayName..."
                    transferProgress = 0f

                    SftpEngine.uploadStream(
                        host = server.host,
                        port = 22,
                        user = "root",
                        pass = sshPassword,
                        remotePath = targetRemotePath,
                        inputStream = inputStream,
                        totalBytes = if (fileSize > 0) fileSize else 1024L * 1024L
                    ) { written, total ->
                        if (total > 0) transferProgress = (written.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    }

                    Toast.makeText(ctx, "✅ فایل $displayName با موفقیت آپلود شد", Toast.LENGTH_SHORT).show()
                    loadDirectoryInternal(server.host, sshPassword, currentPath, showHiddenFiles, sortMode) { list, err ->
                        fileList = list
                        errorMsg = err
                    }
                } catch (e: Exception) {
                    Toast.makeText(ctx, "❌ خطا در آپلود: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    transferTitle = null
                    transferProgress = 0f
                }
            }
        }
    }

    fun loadDirectory(path: String) {
        if (servers.isEmpty() || sshPassword.isBlank()) return
        val server = servers.getOrNull(selectedServerIndex) ?: return

        isLoading = true
        errorMsg = null
        currentPath = path

        scope.launch {
            try {
                fileList = SftpEngine.listFiles(server.host, 22, "root", sshPassword, path, showHiddenFiles, sortMode)
            } catch (e: Exception) {
                errorMsg = e.message
            } finally {
                isLoading = false
            }
        }
    }

    fun openFile(item: SftpFileItem) {
        val server = servers.getOrNull(selectedServerIndex) ?: return
        isReadingFile = true

        scope.launch {
            try {
                val content = SftpEngine.readFile(server.host, 22, "root", sshPassword, item.path)
                fileContent = content
                originalFileContent = content
                editingFile = item
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "خطا در باز کردن فایل", Toast.LENGTH_LONG).show()
            } finally {
                isReadingFile = false
            }
        }
    }

    fun saveCurrentFile() {
        val file = editingFile ?: return
        val server = servers.getOrNull(selectedServerIndex) ?: return
        isSaving = true

        scope.launch {
            try {
                SftpEngine.saveFile(server.host, 22, "root", sshPassword, file.path, fileContent)
                originalFileContent = fileContent
                Toast.makeText(ctx, "✅ فایل با موفقیت روی سرور ذخیره شد!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "خطا در ذخیره", Toast.LENGTH_LONG).show()
            } finally {
                isSaving = false
            }
        }
    }

    fun createNewFile(name: String) {
        val server = servers.getOrNull(selectedServerIndex) ?: return
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val path = if (currentPath == "/") "/$cleanName" else "$currentPath/$cleanName"

        scope.launch {
            try {
                SftpEngine.createFile(server.host, 22, "root", sshPassword, path)
                Toast.makeText(ctx, "فایل ایجاد شد", Toast.LENGTH_SHORT).show()
                showNewFileDialog = false
                newFileName = ""
                loadDirectory(currentPath)
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "خطا در ساخت فایل", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun createNewFolder(name: String) {
        val server = servers.getOrNull(selectedServerIndex) ?: return
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val path = if (currentPath == "/") "/$cleanName" else "$currentPath/$cleanName"

        scope.launch {
            try {
                SftpEngine.createDirectory(server.host, 22, "root", sshPassword, path)
                Toast.makeText(ctx, "پوشه ایجاد شد", Toast.LENGTH_SHORT).show()
                showNewFolderDialog = false
                newFolderName = ""
                loadDirectory(currentPath)
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "خطا در ساخت پوشه", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun renameItem(item: SftpFileItem, newName: String) {
        val server = servers.getOrNull(selectedServerIndex) ?: return
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return
        val parent = item.path.substringBeforeLast("/").ifBlank { "/" }
        val newPath = if (parent == "/") "/$cleanName" else "$parent/$cleanName"

        scope.launch {
            try {
                SftpEngine.renameItem(server.host, 22, "root", sshPassword, item.path, newPath)
                Toast.makeText(ctx, "تغییر نام انجام شد", Toast.LENGTH_SHORT).show()
                itemToRename = null
                renameNewName = ""
                loadDirectory(currentPath)
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "خطا در تغییر نام", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun applyChmod(item: SftpFileItem) {
        val server = servers.getOrNull(selectedServerIndex) ?: return
        val ownerVal = (if (chmodOwnerR) 4 else 0) + (if (chmodOwnerW) 2 else 0) + (if (chmodOwnerX) 1 else 0)
        val groupVal = (if (chmodGroupR) 4 else 0) + (if (chmodGroupW) 2 else 0) + (if (chmodGroupX) 1 else 0)
        val otherVal = (if (chmodOtherR) 4 else 0) + (if (chmodOtherW) 2 else 0) + (if (chmodOtherX) 1 else 0)
        val octalString = "$ownerVal$groupVal$otherVal"
        val octalInt = Integer.parseInt(octalString, 8)

        scope.launch {
            try {
                SftpEngine.chmodItem(server.host, 22, "root", sshPassword, item.path, octalInt)
                Toast.makeText(ctx, "✅ سطح دسترسی به $octalString تغییر یافت", Toast.LENGTH_SHORT).show()
                itemForChmod = null
                loadDirectory(currentPath)
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "خطا در تغییر دسترسی", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun downloadItemToPhone(item: SftpFileItem) {
        val server = servers.getOrNull(selectedServerIndex) ?: return
        scope.launch {
            try {
                transferTitle = "در حال دانلود ${item.name}..."
                transferProgress = 0f

                val downloadsDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.cacheDir
                val localFile = File(downloadsDir, item.name)
                val outStream = FileOutputStream(localFile)

                SftpEngine.downloadStream(
                    host = server.host,
                    port = 22,
                    user = "root",
                    pass = sshPassword,
                    remotePath = item.path,
                    outputStream = outStream
                ) { downloaded, total ->
                    if (total > 0) transferProgress = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                }

                Toast.makeText(ctx, "✅ دانلود کامل شد: ${localFile.name}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "❌ خطا در دانلود: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                transferTitle = null
                transferProgress = 0f
            }
        }
    }

    fun deleteItem(item: SftpFileItem) {
        val server = servers.getOrNull(selectedServerIndex) ?: return
        scope.launch {
            try {
                SftpEngine.deleteItem(server.host, 22, "root", sshPassword, item.path, item.isDirectory)
                Toast.makeText(ctx, "حذف با موفقیت انجام شد", Toast.LENGTH_SHORT).show()
                itemToDelete = null
                loadDirectory(currentPath)
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "خطا در حذف", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Filter file list by search query
    val filteredList = remember(fileList, searchQuery) {
        if (searchQuery.isBlank()) fileList
        else fileList.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Handle Editor Screen or Explorer Screen
    if (editingFile != null) {
        SftpEditorView(
            file = editingFile!!,
            content = fileContent,
            originalContent = originalFileContent,
            isSaving = isSaving,
            onContentChange = { fileContent = it },
            onSave = { saveCurrentFile() },
            onReload = { openFile(editingFile!!) },
            onClose = { editingFile = null }
        )
    } else {
        // Main SFTP Explorer View
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Hero Bento Header
            item {
                ModernCard(padding = 16.dp, cornerRadius = 22.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            icon = Icons.Rounded.FolderOpen,
                            tint = Ds.accent,
                            background = Ds.accentDim,
                            size = 40.dp,
                            iconSize = 22.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "مرورگر فایل و ادیتور فوق‌حرفه‌ای SFTP",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ds.textPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "مدیریت فایل، تغییر دسترسی (Chmod)، آپلود و دانلود و ویرایشگر پیشرفته کانفیگ با سینتکس کد",
                                fontSize = 11.sp,
                                color = Ds.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Server Selector & SSH Password Card
            item {
                ModernCard(padding = 14.dp, cornerRadius = 20.dp) {
                    if (servers.isEmpty()) {
                        Text("هیچ سروری برای مرور SFTP یافت نشد.", fontSize = 12.sp, color = Ds.warn)
                    } else {
                        Text("سرور هدف:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                        Spacer(Modifier.height(8.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(servers.indices.toList()) { idx ->
                                val s = servers[idx]
                                val isSelected = selectedServerIndex == idx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Ds.accent.copy(alpha = 0.18f) else Ds.surfaceLow)
                                        .border(BorderStroke(1.dp, if (isSelected) Ds.accent else Ds.hairline), RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedServerIndex = idx
                                            loadDirectory(currentPath)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(s.name, fontSize = 12.sp, color = if (isSelected) Ds.accent else Ds.textPrimary)
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        InputField(
                            value = sshPassword,
                            onValueChange = { sshPassword = it },
                            label = "رمز عبور SSH / SFTP:",
                            placeholder = "Root Password",
                            isPassword = true
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton(
                                text = if (isLoading) "در حال اتصال..." else "📁 اتصال و رفرش",
                                onClick = { loadDirectory(currentPath) },
                                enabled = !isLoading && sshPassword.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            )
                            SoftButton(
                                text = "⬆️ آپلود فایل",
                                onClick = { uploadLauncher.launch("*/*") },
                                enabled = !isLoading && sshPassword.isNotBlank()
                            )
                        }
                    }
                }
            }

            // Quick Pinned Server Paths
            item {
                Column {
                    Text("مسیرهای پرکاربرد سرور:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickPaths.forEach { (label, qp) ->
                            val isActive = currentPath == qp
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isActive) Ds.accent.copy(alpha = 0.2f) else Ds.surfaceElevated)
                                    .border(BorderStroke(1.dp, if (isActive) Ds.accent else Ds.hairline), RoundedCornerShape(8.dp))
                                    .clickable { loadDirectory(qp) }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = if (isActive) Ds.accent else Ds.textPrimary)
                            }
                        }
                    }
                }
            }

            // Interactive Breadcrumb & Explorer Toolbar
            item {
                ModernCard(padding = 12.dp, cornerRadius = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Interactive Clickable Breadcrumb
                        BreadcrumbBar(
                            currentPath = currentPath,
                            onNavigate = { loadDirectory(it) }
                        )

                        HorizontalDivider(color = Ds.hairline, thickness = 0.5.dp)

                        // Action Bar: Up, New File, New Folder, Sort, Hidden toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Up button
                                CircleIconButton(
                                    icon = Icons.Rounded.ArrowUpward,
                                    contentDescription = "پوشه بالا",
                                    onClick = {
                                        val parent = currentPath.substringBeforeLast("/").ifBlank { "/" }
                                        loadDirectory(parent)
                                    },
                                    size = 32.dp
                                )

                                // Refresh
                                CircleIconButton(
                                    icon = Icons.Rounded.Refresh,
                                    contentDescription = "رفرش",
                                    onClick = { loadDirectory(currentPath) },
                                    size = 32.dp
                                )

                                // New File
                                CircleIconButton(
                                    icon = Icons.Rounded.NoteAdd,
                                    contentDescription = "فایل جدید",
                                    onClick = { showNewFileDialog = true },
                                    tint = Ds.accent,
                                    size = 32.dp
                                )

                                // New Folder
                                CircleIconButton(
                                    icon = Icons.Rounded.CreateNewFolder,
                                    contentDescription = "پوشه جدید",
                                    onClick = { showNewFolderDialog = true },
                                    tint = Ds.warn,
                                    size = 32.dp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Toggle hidden files
                                CircleIconButton(
                                    icon = if (showHiddenFiles) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = "فایل‌های مخفی",
                                    onClick = {
                                        showHiddenFiles = !showHiddenFiles
                                        loadDirectory(currentPath)
                                    },
                                    tint = if (showHiddenFiles) Ds.accent else Ds.textTertiary,
                                    size = 32.dp
                                )

                                // Sort selector
                                Box {
                                    CircleIconButton(
                                        icon = Icons.Rounded.Sort,
                                        contentDescription = "مرتب‌سازی",
                                        onClick = { showSortMenu = true },
                                        size = 32.dp
                                    )

                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false },
                                        modifier = Modifier.background(Ds.surfaceElevated)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("نام (الفبا A-Z)", fontSize = 12.sp, color = Ds.textPrimary) },
                                            onClick = {
                                                sortMode = SftpSortMode.NAME_ASC
                                                showSortMenu = false
                                                loadDirectory(currentPath)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("حجم (بزرگ به کوچک)", fontSize = 12.sp, color = Ds.textPrimary) },
                                            onClick = {
                                                sortMode = SftpSortMode.SIZE_DESC
                                                showSortMenu = false
                                                loadDirectory(currentPath)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("تاریخ (جدیدترین)", fontSize = 12.sp, color = Ds.textPrimary) },
                                            onClick = {
                                                sortMode = SftpSortMode.DATE_DESC
                                                showSortMenu = false
                                                loadDirectory(currentPath)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Search Field in Current Folder
                        InputField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = "",
                            placeholder = "🔍 جستجو در این پوشه..."
                        )
                    }
                }
            }

            // Transfer Progress Banner (Upload / Download)
            transferTitle?.let { title ->
                item {
                    ModernCard(padding = 12.dp, cornerRadius = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                                Text("${(transferProgress * 100).toInt()}%", fontSize = 11.sp, color = Ds.textSecondary)
                            }
                            LinearProgressIndicator(
                                progress = { transferProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = Ds.accent,
                                trackColor = Ds.surfaceLow,
                            )
                        }
                    }
                }
            }

            // Error Banner
            errorMsg?.let { err ->
                item { BannerCard(text = err, tone = BannerTone.Danger) }
            }

            // Files & Folders List
            if (filteredList.isNotEmpty()) {
                items(filteredList) { item ->
                    SftpFileRowItem(
                        item = item,
                        onClick = {
                            if (item.isDirectory) loadDirectory(item.path)
                            else openFile(item)
                        },
                        onEdit = { openFile(item) },
                        onDownload = { downloadItemToPhone(item) },
                        onRename = {
                            itemToRename = item
                            renameNewName = item.name
                        },
                        onChmod = {
                            itemForChmod = item
                            // parse permissions
                            val p = item.permissions
                            chmodOwnerR = p.getOrNull(1) == 'r'
                            chmodOwnerW = p.getOrNull(2) == 'w'
                            chmodOwnerX = p.getOrNull(3) == 'x'
                            chmodGroupR = p.getOrNull(4) == 'r'
                            chmodGroupW = p.getOrNull(5) == 'w'
                            chmodGroupX = p.getOrNull(6) == 'x'
                            chmodOtherR = p.getOrNull(7) == 'r'
                            chmodOtherW = p.getOrNull(8) == 'w'
                            chmodOtherX = p.getOrNull(9) == 'x'
                        },
                        onInfo = { itemForInfo = item },
                        onDelete = { itemToDelete = item }
                    )
                }
            } else if (!isLoading && errorMsg == null && sshPassword.isNotBlank()) {
                item {
                    ModernCard(padding = 24.dp, cornerRadius = 16.dp) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = Ds.textTertiary, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (searchQuery.isNotBlank()) "فایلی با عبارت «$searchQuery» یافت نشد." else "این پوشه خالی است.",
                                fontSize = 12.5.sp,
                                color = Ds.textTertiary
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(90.dp)) }
        }
    }

    // --- DIALOGS ---

    // 1. Create File Dialog
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("ساخت فایل جدید", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary) },
            text = {
                Column {
                    Text("نام فایل را وارد کنید (مثال: config.json یا .env):", fontSize = 12.sp, color = Ds.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    InputField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        placeholder = "new_file.conf",
                        label = ""
                    )
                }
            },
            confirmButton = {
                PrimaryButton(text = "ساخت", onClick = { createNewFile(newFileName) }, enabled = newFileName.isNotBlank())
            },
            dismissButton = {
                SoftButton(text = "انصراف", onClick = { showNewFileDialog = false })
            },
            containerColor = Ds.surfaceElevated
        )
    }

    // 2. Create Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("ساخت پوشه جدید", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary) },
            text = {
                Column {
                    Text("نام پوشه را وارد کنید:", fontSize = 12.sp, color = Ds.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    InputField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = "my_folder",
                        label = ""
                    )
                }
            },
            confirmButton = {
                PrimaryButton(text = "ساخت", onClick = { createNewFolder(newFolderName) }, enabled = newFolderName.isNotBlank())
            },
            dismissButton = {
                SoftButton(text = "انصراف", onClick = { showNewFolderDialog = false })
            },
            containerColor = Ds.surfaceElevated
        )
    }

    // 3. Rename Dialog
    itemToRename?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("تغییر نام / جابجایی", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary) },
            text = {
                Column {
                    Text("نام جدید را وارد نمایید:", fontSize = 12.sp, color = Ds.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    InputField(
                        value = renameNewName,
                        onValueChange = { renameNewName = it },
                        placeholder = item.name,
                        label = ""
                    )
                }
            },
            confirmButton = {
                PrimaryButton(text = "تغییر نام", onClick = { renameItem(item, renameNewName) }, enabled = renameNewName.isNotBlank())
            },
            dismissButton = {
                SoftButton(text = "انصراف", onClick = { itemToRename = null })
            },
            containerColor = Ds.surfaceElevated
        )
    }

    // 4. Delete Confirm Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("تأیید حذف", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ds.danger) },
            text = {
                Column {
                    Text("آیا از حذف این ${if (item.isDirectory) "پوشه" else "فایل"} اطمینان دارید؟", fontSize = 12.5.sp, color = Ds.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(item.path, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Ds.textTertiary)
                    if (!item.isDirectory) {
                        Text("حجم: ${item.formattedSize}", fontSize = 11.sp, color = Ds.textSecondary)
                    }
                }
            },
            confirmButton = {
                SoftButton(text = "حذف قطعی", onClick = { deleteItem(item) }, tone = Ds.danger)
            },
            dismissButton = {
                SoftButton(text = "انصراف", onClick = { itemToDelete = null })
            },
            containerColor = Ds.surfaceElevated
        )
    }

    // 5. Chmod / Permissions Dialog
    itemForChmod?.let { item ->
        val ownerVal = (if (chmodOwnerR) 4 else 0) + (if (chmodOwnerW) 2 else 0) + (if (chmodOwnerX) 1 else 0)
        val groupVal = (if (chmodGroupR) 4 else 0) + (if (chmodGroupW) 2 else 0) + (if (chmodGroupX) 1 else 0)
        val otherVal = (if (chmodOtherR) 4 else 0) + (if (chmodOtherW) 2 else 0) + (if (chmodOtherX) 1 else 0)
        val octalPreview = "0$ownerVal$groupVal$otherVal"

        AlertDialog(
            onDismissRequest = { itemForChmod = null },
            title = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("سطح دسترسی (Chmod)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                    Text(octalPreview, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Ds.accent)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.name, fontSize = 11.sp, color = Ds.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)

                    // Presets
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SoftButton(text = "644 فایل", onClick = {
                            chmodOwnerR = true; chmodOwnerW = true; chmodOwnerX = false
                            chmodGroupR = true; chmodGroupW = false; chmodGroupX = false
                            chmodOtherR = true; chmodOtherW = false; chmodOtherX = false
                        })
                        SoftButton(text = "755 اجرا/پوشه", onClick = {
                            chmodOwnerR = true; chmodOwnerW = true; chmodOwnerX = true
                            chmodGroupR = true; chmodGroupW = false; chmodGroupX = true
                            chmodOtherR = true; chmodOtherW = false; chmodOtherX = true
                        })
                        SoftButton(text = "600 کلید امن", onClick = {
                            chmodOwnerR = true; chmodOwnerW = true; chmodOwnerX = false
                            chmodGroupR = false; chmodGroupW = false; chmodGroupX = false
                            chmodOtherR = false; chmodOtherW = false; chmodOtherX = false
                        })
                        SoftButton(text = "777 کامل", onClick = {
                            chmodOwnerR = true; chmodOwnerW = true; chmodOwnerX = true
                            chmodGroupR = true; chmodGroupW = true; chmodGroupX = true
                            chmodOtherR = true; chmodOtherW = true; chmodOtherX = true
                        })
                    }

                    HorizontalDivider(color = Ds.hairline)

                    // Owner Row
                    Text("مالک (Owner):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ChmodCheckbox(label = "خواندنی (R)", checked = chmodOwnerR, onCheckedChange = { chmodOwnerR = it })
                        ChmodCheckbox(label = "نوشتنی (W)", checked = chmodOwnerW, onCheckedChange = { chmodOwnerW = it })
                        ChmodCheckbox(label = "اجرایی (X)", checked = chmodOwnerX, onCheckedChange = { chmodOwnerX = it })
                    }

                    // Group Row
                    Text("گروه (Group):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ChmodCheckbox(label = "خواندنی (R)", checked = chmodGroupR, onCheckedChange = { chmodGroupR = it })
                        ChmodCheckbox(label = "نوشتنی (W)", checked = chmodGroupW, onCheckedChange = { chmodGroupW = it })
                        ChmodCheckbox(label = "اجرایی (X)", checked = chmodGroupX, onCheckedChange = { chmodGroupX = it })
                    }

                    // Others Row
                    Text("دیگران (Others):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ChmodCheckbox(label = "خواندنی (R)", checked = chmodOtherR, onCheckedChange = { chmodOtherR = it })
                        ChmodCheckbox(label = "نوشتنی (W)", checked = chmodOtherW, onCheckedChange = { chmodOtherW = it })
                        ChmodCheckbox(label = "اجرایی (X)", checked = chmodOtherX, onCheckedChange = { chmodOtherX = it })
                    }
                }
            },
            confirmButton = {
                PrimaryButton(text = "اعمال Chmod", onClick = { applyChmod(item) })
            },
            dismissButton = {
                SoftButton(text = "انصراف", onClick = { itemForChmod = null })
            },
            containerColor = Ds.surfaceElevated
        )
    }

    // 6. File Properties / Info Dialog
    itemForInfo?.let { item ->
        AlertDialog(
            onDismissRequest = { itemForInfo = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = Ds.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("مشخصات فایل", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow(label = "نام:", value = item.name)
                    InfoRow(label = "مسیر کامل:", value = item.path, isMonospace = true)
                    InfoRow(label = "نوع:", value = if (item.isDirectory) "پوشه" else item.category.name)
                    InfoRow(label = "حجم:", value = if (item.isDirectory) "-" else "${item.formattedSize} (${item.size} bytes)")
                    InfoRow(label = "دسترسی:", value = "${item.permissionsOctal} (${item.permissions})", isMonospace = true)
                    InfoRow(label = "UID / GID:", value = "${item.uid} / ${item.gid}", isMonospace = true)
                    InfoRow(label = "آخرین تغییر:", value = item.formattedDate)
                }
            },
            confirmButton = {
                PrimaryButton(text = "بستن", onClick = { itemForInfo = null })
            },
            containerColor = Ds.surfaceElevated
        )
    }
}

private suspend fun loadDirectoryInternal(
    host: String,
    pass: String,
    path: String,
    showHidden: Boolean,
    sortMode: SftpSortMode,
    onResult: (List<SftpFileItem>, String?) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val list = SftpEngine.listFiles(host, 22, "root", pass, path, showHidden, sortMode)
            withContext(Dispatchers.Main) { onResult(list, null) }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(emptyList(), e.message) }
        }
    }
}

// --- FILE ROW ITEM COMPONENT ---
@Composable
private fun SftpFileRowItem(
    item: SftpFileItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onChmod: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val icon: ImageVector
    val iconTint: Color

    if (item.isDirectory) {
        icon = Icons.Rounded.Folder
        iconTint = Color(0xFFF59E0B) // Warm Amber
    } else {
        when (item.category) {
            SftpFileCategory.CONFIG -> {
                icon = Icons.Rounded.Tune
                iconTint = Color(0xFF10B981) // Emerald
            }
            SftpFileCategory.STRUCTURED -> {
                icon = Icons.Rounded.Code
                iconTint = Color(0xFF06B6D4) // Cyan
            }
            SftpFileCategory.CODE -> {
                icon = Icons.Rounded.Code
                iconTint = Color(0xFF8B5CF6) // Purple
            }
            SftpFileCategory.LOG_OR_TEXT -> {
                icon = Icons.Rounded.Description
                iconTint = Color(0xFFFB923C) // Orange
            }
            SftpFileCategory.CERTIFICATE -> {
                icon = Icons.Rounded.Security
                iconTint = Color(0xFFF43F5E) // Rose
            }
            SftpFileCategory.ARCHIVE -> {
                icon = Icons.Rounded.FolderZip
                iconTint = Color(0xFFA855F7) // Violet
            }
            SftpFileCategory.IMAGE -> {
                icon = Icons.Rounded.Image
                iconTint = Color(0xFF38BDF8) // Sky
            }
            SftpFileCategory.DATABASE -> {
                icon = Icons.Rounded.Storage
                iconTint = Color(0xFFEAB308) // Yellow
            }
            else -> {
                icon = Icons.Rounded.Description
                iconTint = Ds.accent
            }
        }
    }

    ModernCard(padding = 10.dp, cornerRadius = 16.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ds.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            item.formattedSize,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Ds.textSecondary
                        )
                        Text("•", fontSize = 9.sp, color = Ds.textTertiary)
                        Text(
                            item.permissionsOctal,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Ds.accent
                        )
                        if (item.formattedDate.isNotBlank()) {
                            Text("•", fontSize = 9.sp, color = Ds.textTertiary)
                            Text(
                                item.formattedDate.substringBefore(" "),
                                fontSize = 9.5.sp,
                                color = Ds.textTertiary
                            )
                        }
                    }
                }
            }

            // Quick Actions & 3-dots Menu
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!item.isDirectory) {
                    CircleIconButton(
                        icon = Icons.Rounded.Tune,
                        contentDescription = "ویرایش",
                        onClick = onEdit,
                        tint = Ds.accent,
                        size = 30.dp
                    )
                }

                Box {
                    CircleIconButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "بیشتر",
                        onClick = { showMenu = true },
                        size = 30.dp
                    )

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Ds.surfaceElevated)
                    ) {
                        if (!item.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("✏️ ویرایش فایل", fontSize = 12.sp, color = Ds.textPrimary) },
                                onClick = { showMenu = false; onEdit() }
                            )
                            DropdownMenuItem(
                                text = { Text("⬇️ دانلود در گوشی", fontSize = 12.sp, color = Ds.textPrimary) },
                                onClick = { showMenu = false; onDownload() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("🏷️ تغییر نام", fontSize = 12.sp, color = Ds.textPrimary) },
                            onClick = { showMenu = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("🔐 سطح دسترسی (Chmod)", fontSize = 12.sp, color = Ds.textPrimary) },
                            onClick = { showMenu = false; onChmod() }
                        )
                        DropdownMenuItem(
                            text = { Text("ℹ️ مشخصات فایل", fontSize = 12.sp, color = Ds.textPrimary) },
                            onClick = { showMenu = false; onInfo() }
                        )
                        HorizontalDivider(color = Ds.hairline)
                        DropdownMenuItem(
                            text = { Text("🗑️ حذف", fontSize = 12.sp, color = Ds.danger) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

// --- BREADCRUMB BAR ---
@Composable
private fun BreadcrumbBar(
    currentPath: String,
    onNavigate: (String) -> Unit
) {
    val segments = remember(currentPath) {
        val parts = currentPath.split("/").filter { it.isNotBlank() }
        val list = mutableListOf<Pair<String, String>>()
        list.add("root" to "/")
        var accumulated = ""
        for (p in parts) {
            accumulated += "/$p"
            list.add(p to accumulated)
        }
        list
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        segments.forEachIndexed { index, (name, path) ->
            val isLast = index == segments.size - 1
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isLast) Ds.accent.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onNavigate(path) }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = name,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                    color = if (isLast) Ds.accent else Ds.textSecondary
                )
            }

            if (!isLast) {
                Icon(
                    Icons.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Ds.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// --- FULLSCREEN ADVANCED CODE & CONFIG EDITOR ---
@Composable
private fun SftpEditorView(
    file: SftpFileItem,
    content: String,
    originalContent: String,
    isSaving: Boolean,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var fontSize by remember { mutableFloatStateOf(12.5f) }
    var isReadOnly by remember { mutableStateOf(false) }
    var showSearchRow by remember { mutableStateOf(false) }
    var editorSearchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val hasUnsavedChanges = content != originalContent

    // BackHandler guard
    BackHandler(enabled = true) {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onClose()
        }
    }

    val quickSymbols = remember {
        listOf("    ", "{", "}", "[", "]", "(", ")", "\"", "'", ":", ";", "=", "/", "\\", "_", "-", "$", "#", "|", "~", "&")
    }

    val lineCount = remember(content) {
        if (content.isEmpty()) 1 else content.count { it == '\n' } + 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Editor Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "بازگشت",
                    onClick = {
                        if (hasUnsavedChanges) showUnsavedDialog = true
                        else onClose()
                    }
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            file.name,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hasUnsavedChanges) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Ds.warn)
                            )
                        }
                    }
                    Text(
                        "${file.formattedSize} · $lineCount خطوط · ${content.length} نویسه",
                        fontSize = 10.sp,
                        color = Ds.textTertiary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(
                    icon = if (isReadOnly) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = "حالت فقط خواندنی",
                    onClick = { isReadOnly = !isReadOnly },
                    tint = if (isReadOnly) Ds.warn else Ds.textSecondary,
                    size = 32.dp
                )

                CircleIconButton(
                    icon = Icons.Rounded.FindReplace,
                    contentDescription = "جستجو و جایگزینی",
                    onClick = { showSearchRow = !showSearchRow },
                    tint = if (showSearchRow) Ds.accent else Ds.textSecondary,
                    size = 32.dp
                )

                PrimaryButton(
                    text = if (isSaving) "..." else "💾 ذخیره",
                    onClick = onSave,
                    enabled = !isSaving && !isReadOnly
                )
            }
        }

        // Search & Replace Sub-Bar
        AnimatedVisibility(visible = showSearchRow) {
            ModernCard(padding = 10.dp, cornerRadius = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        InputField(
                            value = editorSearchQuery,
                            onValueChange = { editorSearchQuery = it },
                            placeholder = "عبارت جستجو...",
                            label = "",
                            modifier = Modifier.weight(1f)
                        )
                        InputField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            placeholder = "جایگزین با...",
                            label = "",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val matches = if (editorSearchQuery.isNotBlank()) {
                            content.split(editorSearchQuery).size - 1
                        } else 0

                        Text(
                            if (editorSearchQuery.isNotBlank()) "$matches مورد یافت شد" else "",
                            fontSize = 11.sp,
                            color = Ds.accent
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SoftButton(
                                text = "جایگزینی همه",
                                onClick = {
                                    if (editorSearchQuery.isNotBlank()) {
                                        val newText = content.replace(editorSearchQuery, replaceQuery)
                                        onContentChange(newText)
                                        Toast.makeText(ctx, "جایگزین شد", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = editorSearchQuery.isNotBlank() && !isReadOnly
                            )
                        }
                    }
                }
            }
        }

        // Quick Coding Symbols Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Font zoom buttons
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Ds.surfaceElevated)
                    .clickable { if (fontSize > 9f) fontSize -= 1.5f }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("A-", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Ds.surfaceElevated)
                    .clickable { if (fontSize < 20f) fontSize += 1.5f }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("A+", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
            }

            // Copy all
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Ds.surfaceElevated)
                    .clickable {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Config", content))
                        Toast.makeText(ctx, "کل متن کپی شد", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("📋 کپی", fontSize = 11.sp, color = Ds.textSecondary)
            }

            quickSymbols.forEach { sym ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Ds.surfaceElevated)
                        .clickable {
                            if (!isReadOnly) {
                                onContentChange(content + sym)
                            }
                        }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(if (sym == "    ") "Tab ⇥" else sym, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Ds.accent)
                }
            }
        }

        // Main Monospace Text Editor Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0D1117)) // Modern Dark Code Canvas
                .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            val codeScrollState = rememberScrollState()
            BasicTextField(
                value = content,
                onValueChange = { if (!isReadOnly) onContentChange(it) },
                readOnly = isReadOnly,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    color = Ds.textPrimary,
                    lineHeight = (fontSize * 1.4f).sp
                ),
                cursorBrush = SolidColor(Ds.accent),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(codeScrollState)
            )
        }
    }

    // Unsaved changes alert dialog
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("تغییرات ذخیره نشده", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ds.warn) },
            text = { Text("شما تغییراتی در فایل ایجاد کرده‌اید که هنوز ذخیره نشده‌اند. آیا می‌خواهید خارج شوید؟", fontSize = 12.sp, color = Ds.textPrimary) },
            confirmButton = {
                SoftButton(text = "خروج بدون ذخیره", onClick = {
                    showUnsavedDialog = false
                    onClose()
                }, tone = Ds.danger)
            },
            dismissButton = {
                PrimaryButton(text = "ذخیره و خروج", onClick = {
                    showUnsavedDialog = false
                    onSave()
                    onClose()
                })
            },
            containerColor = Ds.surfaceElevated
        )
    }
}

// --- HELPER DIALOG ROWS & CHECKBOXES ---
@Composable
private fun ChmodCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = Ds.accent, uncheckedColor = Ds.textTertiary)
        )
        Text(label, fontSize = 11.sp, color = Ds.textSecondary)
    }
}

@Composable
private fun InfoRow(label: String, value: String, isMonospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.5.sp, color = Ds.textTertiary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            fontWeight = FontWeight.Medium,
            color = Ds.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
    }
}
