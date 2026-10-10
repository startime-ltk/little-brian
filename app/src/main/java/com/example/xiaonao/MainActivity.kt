package com.example.xiaonao

import android.Manifest
import android.app.DatePickerDialog
import android.app.AlertDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import com.example.xiaonao.data.Category
import com.example.xiaonao.data.RepeatRule
import com.example.xiaonao.data.Todo
import com.example.xiaonao.widget.TodoWidgetProvider
import com.example.xiaonao.widget.WidgetConfigActivity
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 主界面：Compose 写的极简待办列表。
 * 支持：添加、勾选、删除、滑动删除+撤销、编辑、分类筛选、搜索、置顶、导出/导入备份。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启动后重新排一遍未来的到期提醒（覆盖系统重启/进程被杀的情况）
        lifecycleScope.launch {
            com.example.xiaonao.TodoRepository.of(this@MainActivity).rescheduleAll()
        }

        // Android 13+ 需要运行时申请通知权限，否则提醒通知发不出来
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        setContent { XiaoNaoApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XiaoNaoApp(vm: TodoViewModel = viewModel()) {
    val context = LocalContext.current
    val todos by vm.todos.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(Category.NONE) }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    // 添加时的重复周期（每天 / 每周 / 每月）
    var repeat by remember { mutableStateOf(RepeatRule.NONE) }

    // App 内分类筛选：默认「全部」
    var filterCategory by remember { mutableStateOf(Category.NONE) }
    // 搜索关键字
    var query by remember { mutableStateOf("") }

    // 正在编辑的待办（非 null 时弹出编辑框）
    var editing by remember { mutableStateOf<Todo?>(null) }
    // 右上角「更多」菜单
    var menuExpanded by remember { mutableStateOf(false) }
    // 批量导入弹窗开关
    var batchImportOpen by remember { mutableStateOf(false) }
    // 小组件设置弹窗开关（配置每个桌面组件显示哪一类）
    var widgetSettingsOpen by remember { mutableStateOf(false) }
    // 当前被滑开（露出删除按钮）的待办 id；用于「点其它地方自动收起」
    var openItemId by remember { mutableStateOf<Long?>(null) }
    // 底部视图切换：0=列表 1=时间轴 2=统计
    var tab by remember { mutableStateOf(0) }
    // 应用内更新相关状态
    var updateInfo by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun onDelete(todo: Todo) {
        vm.delete(todo.id)
        openItemId = null
        scope.launch {
            val res = snackbarHostState.showSnackbar(
                message = "已删除「${todo.title}」",
                actionLabel = "撤销",
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) {
                vm.restore(todo)
            }
        }
    }

    // 检查更新：拉 latest.json，有更高版本就弹提示；manual=true 时没更新也给个 toast
    fun checkForUpdate(manual: Boolean) {
        scope.launch {
            AppUpdater.markChecked(context)
            val info = AppUpdater.fetchLatest()
            if (AppUpdater.hasUpdate(info)) {
                updateInfo = info
            } else if (manual) {
                Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 启动后按节流自动查一次更新（默认每天最多一次）
    LaunchedEffect(Unit) {
        if (AppUpdater.shouldAutoCheck(context)) checkForUpdate(manual = false)
    }

    // 导出：生成 JSON 文件后用系统分享面板发出（微信/蓝牙/其他 App 都能接）
    val exportFile: (File?) -> Unit = { file ->
        if (file != null) {
            try {
                val uri = FileProvider.getUriForFile(
                    context, context.packageName + ".fileprovider", file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "分享备份文件"))
                Toast.makeText(context, "已导出：${file.name}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    // 导入：用系统文件选择器挑一个 .json 备份
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            vm.import(context, uri) { n ->
                Toast.makeText(context, "已导入 $n 条待办", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val shown = remember(todos, filterCategory, query) {
        todos.filter { t ->
            (filterCategory == Category.NONE || t.category == filterCategory.name) &&
                (query.isBlank() || t.title.contains(query, ignoreCase = true))
        }
    }

    MaterialTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("小脑") },
                    actions = {
                        TextButton(onClick = { tryAddWidgetToHome(context) }) {
                            Text("＋桌面")
                        }
                        TextButton(onClick = { vm.clearDone() }) {
                            Text("清空已完成")
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Text("⋮", fontSize = 22.sp)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("导出备份") },
                                    onClick = {
                                        menuExpanded = false
                                        vm.export(context, exportFile)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("导入备份") },
                                    onClick = {
                                        menuExpanded = false
                                        importLauncher.launch(arrayOf("application/json"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("批量导入") },
                                    onClick = {
                                        menuExpanded = false
                                        batchImportOpen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("小组件设置") },
                                    onClick = {
                                        menuExpanded = false
                                        widgetSettingsOpen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("导出列表") },
                                    onClick = {
                                        menuExpanded = false
                                        vm.exportText(context) { file ->
                                            if (file != null) {
                                                try {
                                                    val uri = FileProvider.getUriForFile(
                                                        context, context.packageName + ".fileprovider", file
                                                    )
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "分享待办清单"))
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("检查更新") },
                                    onClick = {
                                        menuExpanded = false
                                        checkForUpdate(manual = true)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .clickable { if (openItemId != null) openItemId = null }
            ) {
                // 顶部视图切换：列表 / 时间轴 / 统计
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("列表") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("时间轴") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("统计") })
                }

                Spacer(Modifier.height(12.dp))

                if (tab == 0) {
                // 搜索框
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索待办…") },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("添加待办…") },
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        vm.add(text, dueDate, selectedCategory.name, repeat.name)
                        text = ""
                        dueDate = null
                        selectedCategory = Category.NONE
                        repeat = RepeatRule.NONE
                    }) { Text("添加") }
                }

                Spacer(Modifier.height(8.dp))

                // 分类选择（添加时）
                CategoryChips(selected = selectedCategory, onSelect = { selectedCategory = it })

                Spacer(Modifier.height(8.dp))

                // 到期选择
                DueDateRow(dueDate = dueDate) { dueDate = it }

                Spacer(Modifier.height(8.dp))

                // 重复周期（每天 / 每周 / 每月）
                RepeatChips(selected = repeat, onSelect = { repeat = it })

                Spacer(Modifier.height(12.dp))

                // App 内「按分类看」筛选条
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("筛选：", fontSize = 13.sp, color = Color.Gray)
                    FilterChip(
                        selected = filterCategory == Category.NONE,
                        onClick = { filterCategory = Category.NONE },
                        label = { Text("全部") }
                    )
                    Category.all.drop(1).forEach { cat ->
                        FilterChip(
                            selected = filterCategory == cat,
                            onClick = { filterCategory = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (shown.isEmpty()) {
                    Text(
                        when {
                            todos.isEmpty() -> "还没有待办，添加一个吧～"
                            query.isNotBlank() -> "没有匹配「$query」的待办"
                            else -> "「${filterCategory.label}」分类下暂无待办"
                        },
                        Modifier.padding(top = 24.dp)
                    )
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { todo ->
                        // 滑开不立即删除：停在「打开」状态露出删除按钮，点按钮才删
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                // 滑到删除位时保持在打开状态（返回 true），不自动删除；
                                // 其它情况（回正）返回 false 让它归位
                                value == SwipeToDismissBoxValue.EndToStart
                            }
                        )

                        // 本行滑到打开态 → 记为当前打开项；滑回原位 → 清除
                        LaunchedEffect(dismissState.currentValue) {
                            when (dismissState.currentValue) {
                                SwipeToDismissBoxValue.EndToStart -> openItemId = todo.id
                                SwipeToDismissBoxValue.Settled ->
                                    if (openItemId == todo.id) openItemId = null
                                else -> {}
                            }
                        }
                        // 别的行打开时，把这一条（若还开着）自动收起
                        LaunchedEffect(openItemId) {
                            if (openItemId != null && openItemId != todo.id &&
                                dismissState.currentValue != SwipeToDismissBoxValue.Settled
                            ) {
                                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                            }
                        }
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFFE53935))
                                        .clickable(
                                            enabled = dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
                                        ) { onDelete(todo) }
                                        .padding(end = 24.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text("🗑 点击删除", color = Color.White)
                                }
                            }
                        ) {
                            TodoRow(
                                todo = todo,
                                onToggle = {
                                    // 周期性待办勾完成时会顺延到下一周期，这里提示新的到期日
                                    vm.toggle(todo.id) { next ->
                                        Toast.makeText(
                                            context,
                                            "已完成，顺延至 ${formatDate(next)}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onEdit = {
                                    // 有行处于打开态时，点任意行先把它收起还原；再点一次才编辑
                                    if (openItemId != null) openItemId = null else editing = todo
                                },
                                onTogglePin = { vm.togglePin(todo.id) },
                                onCopy = { copyTodo(context, todo.title) }
                            )
                        }
                    }
                }
                }
                if (tab == 1) TimelineView(todos = todos, onEdit = { editing = it })
                if (tab == 2) StatsView(todos = todos)
            }
        }

        // 编辑弹窗
        editing?.let { todo ->
            EditTodoDialog(
                todo = todo,
                onDismiss = { editing = null },
                onSave = { updated ->
                    vm.update(updated)
                    editing = null
                }
            )
        }

        // 批量导入弹窗
        if (batchImportOpen) {
            BatchImportDialog(
                onDismiss = { batchImportOpen = false },
                onImport = { lines, cat, rep ->
                    val n = lines.map { it.trim() }.count { it.isNotBlank() }
                    vm.batchAdd(lines, cat, rep)
                    batchImportOpen = false
                    Toast.makeText(context, "已导入 $n 条待办", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 小组件设置弹窗：配置每个桌面组件显示哪一类待办
        if (widgetSettingsOpen) {
            WidgetSettingsDialog(
                onDismiss = { widgetSettingsOpen = false },
                onSaved = {
                    widgetSettingsOpen = false
                    Toast.makeText(context, "小组件设置已保存", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 发现新版本：提示对话框
        updateInfo?.let { info ->
            AlertDialog(
                onDismissRequest = { updateInfo = null },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            if (!AppUpdater.canInstallPackages(context)) {
                                Toast.makeText(
                                    context,
                                    "请先在设置里开启「允许安装未知应用」",
                                    Toast.LENGTH_LONG
                                ).show()
                                AppUpdater.openInstallSettings(context)
                                return@launch
                            }
                            updating = true
                            downloadProgress = 0
                            try {
                                val file = AppUpdater.downloadApk(context, info.apkUrl) {
                                    downloadProgress = it
                                }
                                updating = false
                                updateInfo = null
                                AppUpdater.installApk(context, file)
                            } catch (e: Exception) {
                                updating = false
                                Toast.makeText(
                                    context,
                                    "下载失败：${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }) { Text("立即更新") }
                },
                dismissButton = {
                    TextButton(onClick = { updateInfo = null }) { Text("稍后") }
                },
                title = { Text("发现新版本 v${info.versionName}") },
                text = {
                    if (info.note.isNotBlank()) Text(info.note)
                    else Text("有新版本可用，是否现在更新？")
                }
            )
        }

        // 下载中：进度对话框（不可取消，避免误关）
        if (updating) {
            AlertDialog(
                onDismissRequest = { },
                confirmButton = { },
                title = { Text("正在下载更新…") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(progress = { downloadProgress / 100f })
                        Text("${downloadProgress}%", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            )
        }
    }
}

/** 添加/编辑共用的分类选择条 */
@Composable
fun CategoryChips(selected: Category, onSelect: (Category) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Category.all.forEach { cat ->
            FilterChip(
                selected = selected == cat,
                onClick = { onSelect(cat) },
                label = { Text(cat.label) }
            )
        }
    }
}

/** 添加/编辑共用的重复周期选择条（每天 / 每周 / 每月） */
@Composable
fun RepeatChips(selected: RepeatRule, onSelect: (RepeatRule) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("重复：", fontSize = 13.sp, color = Color.Gray)
        RepeatRule.all.forEach { rule ->
            FilterChip(
                selected = selected == rule,
                onClick = { onSelect(rule) },
                label = { Text(rule.label) }
            )
        }
    }
}

@Composable
fun DueDateRow(dueDate: Long?, onPick: (Long?) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            val cal = Calendar.getInstance().apply {
                dueDate?.let { timeInMillis = it }
            }
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    cal.set(y, m, d, 9, 0)
                    onPick(cal.timeInMillis)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }) {
            Text(if (dueDate != null) "🔔 到期：${formatDate(dueDate)}" else "设置到期（到点提醒）")
        }
        if (dueDate != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onPick(null) }) { Text("清除") }
            Text(
                "⏰ 到点会通过系统通知提醒你",
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
fun TodoRow(
    todo: Todo,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onCopy: () -> Unit
) {
    val cat = Category.from(todo.category)
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onTogglePin() })
            }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = todo.done, onCheckedChange = { onToggle() })

            // 分类色点
            Box(
                Modifier
                    .size(10.dp)
                    .background(colorResource(cat.colorRes), CircleShape)
            )

            // 标题（置顶项加 📌）+ 其下方的备注小字
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    (if (todo.pinned) "📌 " else "") + todo.title,
                    textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (todo.done) Color.Gray else Color.Unspecified,
                    fontWeight = if (todo.pinned || cat != Category.NONE) FontWeight.Bold else FontWeight.Normal
                )
                if (todo.note.isNotBlank()) {
                    Text(
                        todo.note,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 2
                    )
                }
            }

            // 到期时间（次要文字）；设了到期就会到点提醒，逾期未完成标红
            if (todo.dueDate != null) {
                val overdue = todo.dueDate < System.currentTimeMillis() && !todo.done
                Text(
                    (if (overdue) "⏰ " else "🔔 ") + formatDate(todo.dueDate),
                    Modifier.padding(start = 8.dp),
                    fontSize = 12.sp,
                    color = if (overdue) Color(0xFFE53935) else Color.Gray
                )
            }

            // 周期标记（每天 / 每周 / 每月）
            val rule = RepeatRule.from(todo.repeat)
            if (rule != RepeatRule.NONE) {
                Text(
                    "🔁${rule.label}",
                    Modifier.padding(start = 8.dp),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // 编辑按钮：点一下直接改这条（不用靠「点整行」才发现能编辑）
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(40.dp)
            ) {
                Text("✏️", fontSize = 18.sp)
            }

            // 复制按钮：点一下把这条标题复制到剪贴板
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(40.dp).padding(start = 4.dp)
            ) {
                Text("📋", fontSize = 18.sp)
            }
        }
    }
}

/** 编辑已存在待办的弹窗：可改标题 / 备注 / 分类 / 到期 / 重复 / 置顶 */
@Composable
fun EditTodoDialog(todo: Todo, onDismiss: () -> Unit, onSave: (Todo) -> Unit) {
    var title by remember { mutableStateOf(todo.title) }
    var category by remember { mutableStateOf(Category.from(todo.category)) }
    var dueDate by remember { mutableStateOf(todo.dueDate) }
    var repeat by remember { mutableStateOf(RepeatRule.from(todo.repeat)) }
    var note by remember { mutableStateOf(todo.note) }
    var pinned by remember { mutableStateOf(todo.pinned) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    todo.copy(
                        title = title.trim().ifBlank { todo.title },
                        category = category.name,
                        dueDate = dueDate,
                        repeat = repeat.name,
                        note = note.trim(),
                        pinned = pinned
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("编辑待办") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("内容") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    placeholder = { Text("补充说明、链接、步骤…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    singleLine = false
                )
                CategoryChips(selected = category, onSelect = { category = it })
                DueDateRow(dueDate = dueDate) { dueDate = it }
                RepeatChips(selected = repeat, onSelect = { repeat = it })
                if (repeat != RepeatRule.NONE && dueDate == null) {
                    Text(
                        "提示：设了重复但没设到期时间，首次勾选后会按「今天」推算下一周期。",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                    Text("置顶（排在最前）")
                }
            }
        }
    )
}

fun formatDate(ts: Long): String {
    return SimpleDateFormat("MM-dd", Locale.CHINA).format(ts)
}

/**
 * 在 App 内一键把桌面小组件钉到桌面（Android 8.0+ 的 App Widget Pinning）。
 *
 * 很多国产桌面（小米/华为/OPPO/vivo）对标准 pinning API 支持不完整：要么直接忽略，
 * 要么调用了却什么都不弹。所以这里用 successCallback + 3 秒超时兜底：
 *  - 系统真的接受并弹出确认框 → 回调触发，万事大吉；
 *  - 系统静默忽略（无回调）→ 超时后弹出「手动添加」引导，不再无反应。
 */
fun tryAddWidgetToHome(context: Context) {
    val am = AppWidgetManager.getInstance(context)

    // 不支持 pinning、或 pinning 被静默忽略时，展示手动添加引导
    fun showGuide() {
        AlertDialog.Builder(context)
            .setTitle("添加桌面小组件")
            .setMessage(
                "当前桌面暂不支持一键添加，请手动操作：\n\n" +
                "1. 长按手机桌面空白处\n" +
                "2. 选「小组件 / 微件」\n" +
                "3. 找到「小脑」拖到桌面\n" +
                "4. 在桌面点小组件里的 ＋ 即可快速记待办"
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && am.isRequestPinAppWidgetSupported) {
        val provider = ComponentName(context, TodoWidgetProvider::class.java)
        val action = "com.example.xiaonao.WIDGET_PINNED"
        val callbackIntent = Intent(action).setPackage(context.packageName)

        // 监听系统是否真的接受了添加请求
        var receiver: BroadcastReceiver? = null
        val handler = Handler(Looper.getMainLooper())
        val timeout = Runnable {
            try { receiver?.let { context.unregisterReceiver(it) } } catch (_: Exception) {}
            showGuide()
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                // 系统接受了（会弹确认框或直接添加），取消超时兜底
                handler.removeCallbacks(timeout)
                try { context.unregisterReceiver(this) } catch (_: Exception) {}
            }
        }

        // Android 13+ 动态注册需要显式指定 exported 标志
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(action))
        }
        handler.postDelayed(timeout, 3000)

        try {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_IMMUTABLE else 0
            val successPending = PendingIntent.getBroadcast(context, 0, callbackIntent, flags)
            am.requestPinAppWidget(provider, null, successPending)
        } catch (e: Exception) {
            handler.removeCallbacks(timeout)
            try { receiver.let { context.unregisterReceiver(it) } } catch (_: Exception) {}
            showGuide()
        }
    } else {
        showGuide()
    }
}

/** 复制一条待办的标题到系统剪贴板 */
fun copyTodo(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("待办", text))
    Toast.makeText(context, "已复制：$text", Toast.LENGTH_SHORT).show()
}

/** 批量导入弹窗：粘贴多行文本（每行一条）+ 选分类，一键批量建待办 */
@Composable
fun BatchImportDialog(onDismiss: () -> Unit, onImport: (List<String>, String, String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.NONE) }
    var repeat by remember { mutableStateOf(RepeatRule.NONE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onImport(text.split("\n", "\r\n"), category.name, repeat.name)
            }) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("批量导入") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("每行一条，粘贴后一键建多条待办。", fontSize = 13.sp, color = Color.Gray)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("待办内容（每行一条）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    singleLine = false
                )
                CategoryChips(selected = category, onSelect = { category = it })
                RepeatChips(selected = repeat, onSelect = { repeat = it })
            }
        }
    )
}

/**
 * 小组件设置弹窗：配置每个已添加的桌面组件显示哪一类待办。
 *
 * 刻意不走系统的 android:configure 配置页——国产桌面（小米/华为/OPPO 等）对它支持很差，
 * 常出现不启动或直接「无法添加小部件」。改在 App 内设置，不依赖桌面实现，稳定可靠。
 */
@Composable
fun WidgetSettingsDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val am = remember { AppWidgetManager.getInstance(context) }
    val ids = remember {
        am.getAppWidgetIds(ComponentName(context, TodoWidgetProvider::class.java)).toList()
    }

    // 新组件 / 没单独设过的组件用这个默认分类
    var defaultFilter by remember { mutableStateOf(WidgetConfigActivity.defaultFilterOf(context)) }
    // 每个已添加组件各自的分类
    val perWidget = remember {
        mutableStateMapOf<Int, String>().apply {
            ids.forEach { put(it, WidgetConfigActivity.filterOf(context, it)) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                WidgetConfigActivity.saveDefaultFilter(context, defaultFilter)
                ids.forEach { id ->
                    WidgetConfigActivity.saveFilter(
                        context, id, perWidget[id] ?: WidgetConfigActivity.FILTER_ALL
                    )
                }
                // 完整重绘：标题与列表都按新设置刷新
                TodoWidgetProvider.refreshAll(context)
                onSaved()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("小组件设置") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "设置每个桌面小组件显示哪一类待办；组件标题会带上分类名（如「小脑 · 学习」）。",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Text("新添加的小组件默认显示：", fontWeight = FontWeight.Bold)
                WidgetFilterChips(selected = defaultFilter, onSelect = { defaultFilter = it })

                if (ids.isEmpty()) {
                    Text(
                        "你还没有添加桌面小组件。先在桌面添加一个" +
                            "（长按桌面 → 小组件 → 小脑），再来这里设置。",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                } else {
                    Text("已添加的组件（${ids.size}）：", fontWeight = FontWeight.Bold)
                    ids.forEachIndexed { index, id ->
                        Column {
                            Text("小组件 ${index + 1}", fontSize = 13.sp)
                            WidgetFilterChips(
                                selected = perWidget[id] ?: WidgetConfigActivity.FILTER_ALL,
                                onSelect = { perWidget[id] = it }
                            )
                        }
                    }
                }
            }
        }
    )
}

/** 小组件分类选择条：全部 / 工作 / 学习 / 生活 */
@Composable
fun WidgetFilterChips(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = selected == WidgetConfigActivity.FILTER_ALL,
            onClick = { onSelect(WidgetConfigActivity.FILTER_ALL) },
            label = { Text("全部") }
        )
        // drop(1)：去掉「无」分类——它在 App 内已被当作「全部」的语义，单独筛它没有意义
        Category.all.drop(1).forEach { cat ->
            FilterChip(
                selected = selected == cat.name,
                onClick = { onSelect(cat.name) },
                label = { Text(cat.label) }
            )
        }
    }
}

/** 把时间戳归到当天 00:00，用于「日记时间轴」按天分组 */
fun dayStart(ts: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/**
 * 日记时间轴：按到期日期把待办串成一条竖向时间线（最近的日期在前）。
 * 没设到期日的待办单独归到「未排期」放最后。点击卡片可编辑。
 */
@Composable
fun TimelineView(todos: List<Todo>, onEdit: (Todo) -> Unit) {
    if (todos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有待办，去「列表」添加一条吧～", color = Color.Gray)
        }
        return
    }
    val fmtDay = SimpleDateFormat("M月d日", Locale.CHINA)
    val fmtWeek = SimpleDateFormat("EEEE", Locale.CHINA)
    val withDate = todos.filter { it.dueDate != null }.sortedByDescending { it.dueDate }
    val noDate = todos.filter { it.dueDate == null }
    val groups = withDate.groupBy { dayStart(it.dueDate!!) }
        .toSortedMap(compareByDescending { it })
        .map { (day, items) -> (Triple(day, fmtDay.format(day), fmtWeek.format(day)) to items) }
        .toMutableList()
    if (noDate.isNotEmpty()) groups.add(Triple(-1L, "未排期", "") to noDate)

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        groups.forEach { (key, items) ->
            item {
                Row(Modifier.fillMaxWidth()) {
                    // 左侧：日期节点（圆点 + 文字）+ 贯穿竖线
                    Column(
                        Modifier.width(64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(key.second, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (key.third.isNotBlank())
                            Text(key.third, fontSize = 11.sp, color = Color.Gray)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.size(12.dp).background(Color(0xFF6750A4), CircleShape))
                        Box(Modifier.width(2.dp).weight(1f).background(Color(0xFFE0E0E0)))
                    }
                    Spacer(Modifier.width(12.dp))
                    // 右侧：当天待办卡片列表
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items.forEach { TimelineCard(it, onEdit) }
                    }
                }
            }
        }
    }
}

/** 时间轴里的单条待办卡片（只读展示，点击进编辑） */
@Composable
fun TimelineCard(todo: Todo, onEdit: (Todo) -> Unit) {
    val cat = Category.from(todo.category)
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onEdit(todo) }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).background(colorResource(cat.colorRes), CircleShape))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    (if (todo.pinned) "📌 " else "") + todo.title,
                    textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (todo.done) Color.Gray else Color.Unspecified,
                    fontWeight = if (todo.pinned) FontWeight.Bold else FontWeight.Normal
                )
                if (todo.note.isNotBlank())
                    Text(todo.note, fontSize = 12.sp, color = Color.Gray, maxLines = 2)
            }
            val rule = RepeatRule.from(todo.repeat)
            if (rule != RepeatRule.NONE)
                Text("🔁${rule.label}", Modifier.padding(start = 8.dp), fontSize = 12.sp, color = Color.Gray)
            Checkbox(checked = todo.done, onCheckedChange = { })
        }
    }
}

/**
 * 完成率统计：总完成率圆环 + 关键数字（总数/已完成/待完成/逾期）+ 各分类完成情况。
 */
@Composable
fun StatsView(todos: List<Todo>) {
    val total = todos.size
    val done = todos.count { it.done }
    val rate = if (total == 0) 0f else done.toFloat() / total
    val now = System.currentTimeMillis()
    val overdue = todos.count { !it.done && it.dueDate != null && it.dueDate < now }
    val active = todos.count { !it.done }

    val byCat = Category.all.filter { it != Category.NONE }.map { cat ->
        val items = todos.filter { Category.from(it.category) == cat }
        Triple(cat, items.count { it.done }, items.size)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 总完成率圆环
        Card(Modifier.fillMaxWidth()) {
                        Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { rate },
                        Modifier.size(110.dp),
                        strokeWidth = 10.dp,
                        color = Color(0xFF6750A4)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(rate * 100).toInt()}%", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("完成率", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("共 $total 条", fontSize = 14.sp)
                    Text("已完成 $done 条", fontSize = 14.sp, color = Color(0xFF00897B))
                    Text("待完成 $active 条", fontSize = 14.sp, color = Color(0xFFF57C00))
                    if (overdue > 0)
                        Text("已逾期 $overdue 条", fontSize = 14.sp, color = Color(0xFFE53935))
                }
            }
        }

        Text("分类完成情况", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        byCat.forEach { (cat, d, n) ->
            val r = if (n == 0) 0f else d.toFloat() / n
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(colorResource(cat.colorRes), CircleShape))
                Text(cat.label, Modifier.width(56.dp).padding(start = 8.dp), fontSize = 13.sp)
                LinearProgressIndicator(
                    progress = { r },
                    Modifier.weight(1f),
                    color = Color(0xFF6750A4)
                )
                Text("$d/$n", Modifier.padding(start = 8.dp), fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}
