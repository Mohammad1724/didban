@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SftpScreen(t: Str) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val servers = remember { Prefs.loadServers(ctx) }
    var selectedServerIndex by remember { mutableStateOf(0) }
    var sshPassword by remember { mutableStateOf("") }

    var currentPath by remember { mutableStateOf("/etc") }
    var fileList by remember { mutableStateOf<List<SftpFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Editor state
    var editingFile by remember { mutableStateOf<SftpFileItem?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var isReadingFile by remember { mutableStateOf(false) }

    val quickPaths = remember {
        listOf(
            "/etc/nginx",
            "/etc/x-ui",
            "/etc/gost",
            "/etc/systemd/system",
            "/var/log",
            "/opt",
            "/root"
        )
    }

    fun loadDirectory(path: String) {
        if (servers.isEmpty() || sshPassword.isBlank()) return
        val server = servers.getOrNull(selectedServerIndex) ?: return

        isLoading = true
        errorMsg = null
        currentPath = path

        scope.launch {
            try {
                fileList = SftpEngine.listFiles(server.host, 22, "root", sshPassword, path)
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
                fileContent = SftpEngine.readFile(server.host, 22, "root", sshPassword, item.path)
                editingFile = item
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "خطا در باز کردن فایل", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(ctx, "✅ فایل با موفقیت روی سرور ذخیره شد!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "خطا در ذخیره", Toast.LENGTH_SHORT).show()
            } finally {
                isSaving = false
            }
        }
    }

    fun deleteItem(item: SftpFileItem) {
        val server = servers.getOrNull(selectedServerIndex) ?: return
        scope.launch {
            try {
                SftpEngine.deleteItem(server.host, 22, "root", sshPassword, item.path, item.isDirectory)
                Toast.makeText(ctx, "حذف انجام شد", Toast.LENGTH_SHORT).show()
                loadDirectory(currentPath)
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "خطا در حذف", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // BackHandler for Editor
    BackHandler(enabled = editingFile != null) {
        editingFile = null
    }

    if (editingFile != null) {
        // Fullscreen In-App Config Editor
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleIconButton(
                        icon = Icons.AutoMirrored.rounded.ArrowBack,
                        contentDescription = "بازگشت",
                        onClick = { editingFile = null }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            editingFile?.name ?: "ویرایش فایل",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary
                        )
                        Text(
                            editingFile?.path ?: "",
                            fontSize = 10.5.sp,
                            color = Ds.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                PrimaryButton(
                    text = if (isSaving) "..." else "💾 ذخیره",
                    onClick = { saveCurrentFile() },
                    enabled = !isSaving
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ds.surfaceLow)
                    .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                InputField(
                    value = fileContent,
                    onValueChange = { fileContent = it },
                    label = "",
                    placeholder = "محتوای فایل کانفیگ...",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        // SFTP Explorer File Browser
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Hero Bento
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
                                "مرورگر فایل و ادیتور SFTP",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ds.textPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "مرور پوشه‌های لینوکس و ویرایش مستقیم فایل‌های کانفیگ با ذخیره آنی",
                                fontSize = 11.sp,
                                color = Ds.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Server & Credentials Card
            item {
                ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                    if (servers.isEmpty()) {
                        Text("هیچ سروری برای مرور SFTP یافت نشد.", fontSize = 12.sp, color = Ds.warn)
                    } else {
                        Text("انتخاب سرور مقصد:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
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

                        Spacer(Modifier.height(12.dp))

                        PrimaryButton(
                            text = if (isLoading) "در حال اتصال به SFTP..." else "📁 اتصال و مرور فایل‌ها",
                            onClick = { loadDirectory(currentPath) },
                            enabled = !isLoading && sshPassword.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Quick Path Jumps
            item {
                Text("مسیرهای پرکاربرد سرور:", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPaths.forEach { qp ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Ds.surfaceElevated)
                                .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(8.dp))
                                .clickable { loadDirectory(qp) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(qp, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Ds.accent)
                        }
                    }
                }
            }

            // Current Path & Breadcrumb
            item {
                ModernCard(padding = 12.dp, cornerRadius = 16.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("مسیر فعلی:", fontSize = 11.sp, color = Ds.textTertiary)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                currentPath,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Ds.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (currentPath != "/" && currentPath.contains("/")) {
                            SoftButton(
                                text = "⬆ پوشه قبل",
                                onClick = {
                                    val parent = currentPath.substringBeforeLast("/").ifBlank { "/" }
                                    loadDirectory(parent)
                                }
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
            if (fileList.isNotEmpty()) {
                items(fileList) { item ->
                    ModernCard(padding = 12.dp, cornerRadius = 16.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (item.isDirectory) {
                                        loadDirectory(item.path)
                                    } else {
                                        openFile(item)
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    if (item.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = if (item.isDirectory) Ds.warn else Ds.accent,
                                    modifier = Modifier.size(22.dp)
                                )
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
                                    Text(
                                        "${item.formattedSize} · ${item.permissions}",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Ds.textTertiary
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (!item.isDirectory) {
                                    SoftButton(
                                        text = "✏️ ویرایش",
                                        onClick = { openFile(item) }
                                    )
                                }
                                SoftButton(
                                    text = "🗑️",
                                    onClick = { deleteItem(item) }
                                )
                            }
                        }
                    }
                }
            } else if (!isLoading && errorMsg == null && sshPassword.isNotBlank()) {
                item {
                    Text(
                        "این پوشه خالی است.",
                        fontSize = 12.sp,
                        color = Ds.textTertiary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(90.dp)) }
        }
    }
}
