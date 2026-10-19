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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 主界面：Compose 写的极简待办列表。
 * 支持：添加、勾选、删除、滑动删除+撤销、编辑、分类筛选、搜索、置顶、导出/导入备份。
 *
 * 国际化：所有用户可见文案走 strings.xml（values 中文 / values-en 英文），
 * 跟随系统语言自动切换；日期格式同样跟随系统 Locale。
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
    // 回收站内容（软删除的待办，可恢复/彻底删除）
    val trash by vm.trash.collectAsStateWithLifecycle()
    // 搜索是否展开（默认折叠为小🔍图标，点击展开输入框）
    var searchActive by remember { mutableStateOf(false) }
    // 添加待办弹窗开关（由右下角圆形＋按钮触发）
    var addOpen by remember { mutableStateOf(false) }

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
    // 关于弹窗开关
    var aboutOpen by remember { mutableStateOf(false) }
    // 当前被滑开（露出删除按钮）的待办 id；用于「点其它地方自动收起」
    var openItemId by remember { mutableStateOf<Long?>(null) }
    // 点了滑出的「删除」按钮、等待确认的那条待办（非 null 时弹确认框）
    var pendingDelete by remember { mutableStateOf<Todo?>(null) }
    // 底部视图切换：0=列表 1=时间轴 2=统计 3=回收站
    var tab by remember { mutableStateOf(0) }
    // 应用内更新相关状态
    var updateInfo by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    // 下载网速（KB/s），用于进度框实时显示
    var downloadSpeed by remember { mutableStateOf(0) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun onDelete(todo: Todo) {
        vm.delete(todo.id)
        openItemId = null
        scope.launch {
            val res = snackbarHostState.showSnackbar(
                message = context.getString(R.string.toast_moved_to_trash, todo.title),
                actionLabel = context.getString(R.string.action_undo),
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
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_latest_version),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 启动后按节流自动查一次更新（默认每天最多一次）；同时清理回收站里超 30 天的内容
    LaunchedEffect(Unit) {
        if (AppUpdater.shouldAutoCheck(context)) checkForUpdate(manual = false)
        vm.cleanOldTrash()
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
                context.startActivity(
                    Intent.createChooser(intent, context.getString(R.string.chooser_share_backup))
                )
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_exported, file.name),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_export_failed_detail, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.toast_export_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 导入：用系统文件选择器挑一个 .json 备份
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            vm.import(context, uri) { n ->
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_imported, n),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val shown = remember(todos, filterCategory, query) {
        todos.filter { t ->
            (filterCategory == Category.NONE || t.category == filterCategory.name) &&
                (query.isBlank() || t.title.contains(query, ignoreCase = true))
        }
    }

    XiaoNaoTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val pulseTrans = rememberInfiniteTransition(label = "pulse")
                            val pulse by pulseTrans.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.12f,
                                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                                label = "pulse"
                            )
                            Text(
                                "🧠",
                                fontSize = 20.sp,
                                modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.app_title),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { tryAddWidgetToHome(context) }) {
                            Text(stringResource(R.string.btn_add_widget))
                        }
                        TextButton(onClick = { vm.clearDone() }) {
                            Text(stringResource(R.string.clear_done))
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
                                    text = { Text(stringResource(R.string.menu_export)) },
                                    onClick = {
                                        menuExpanded = false
                                        vm.export(context, exportFile)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_import)) },
                                    onClick = {
                                        menuExpanded = false
                                        importLauncher.launch(arrayOf("application/json"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_batch_import)) },
                                    onClick = {
                                        menuExpanded = false
                                        batchImportOpen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_widget_settings)) },
                                    onClick = {
                                        menuExpanded = false
                                        widgetSettingsOpen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_export_text)) },
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
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            intent,
                                                            context.getString(R.string.chooser_share_list)
                                                        )
                                                    )
                                                } catch (e: Exception) {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(
                                                            R.string.toast_export_failed_detail,
                                                            e.message ?: ""
                                                        ),
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.toast_export_failed),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_check_update)) },
                                    onClick = {
                                        menuExpanded = false
                                        checkForUpdate(manual = true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_about)) },
                                    onClick = {
                                        menuExpanded = false
                                        aboutOpen = true
                                    }
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                val addFabDesc = stringResource(R.string.a11y_add_todo)
                FloatingActionButton(
                    onClick = { addOpen = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.semantics { contentDescription = addFabDesc }
                ) {
                    Text("＋", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
        ) { padding ->
            val isDark = isSystemInDarkTheme()
            val pageBrush = Brush.verticalGradient(
                colors = if (isDark) listOf(Color(0xFF0F1220), Color(0xFF141A2E))
                else listOf(Color(0xFFF6F8FF), Color(0xFFEAF0FF))
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .background(pageBrush)
                    .padding(padding)
                    .padding(16.dp)
                    .padding(bottom = 72.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            if (openItemId != null) openItemId = null
                        })
                    }
            ) {
                // 顶部视图切换：列表 / 时间轴 / 统计 / 回收站（自定义滑动分段控件，更灵动）
                SegmentedTabs(
                    selected = tab,
                    onSelect = { tab = it },
                    tabs = listOf(
                        stringResource(R.string.tab_list),
                        stringResource(R.string.tab_timeline),
                        stringResource(R.string.tab_stats),
                        stringResource(R.string.tab_trash)
                    )
                )

                Spacer(Modifier.height(12.dp))

                if (tab == 0) {
                // 搜索：默认折叠为小🔍图标，点击展开输入框（位于分段 Tab 下方）
                Row(Modifier.fillMaxWidth()) {
                    if (searchActive) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            singleLine = true,
                            leadingIcon = { Text("🔍", fontSize = 18.sp) },
                            trailingIcon = {
                                if (query.isNotBlank())
                                    IconButton(onClick = { query = "" }) {
                                        Text("✕", fontSize = 16.sp)
                                    }
                            }
                        )
                        IconButton(onClick = { searchActive = false; query = "" }) {
                            Text("✕", fontSize = 18.sp)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { searchActive = true }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔍", fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.search_placeholder),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // App 内「按分类看」筛选条
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.filter_label), fontSize = 13.sp, color = Color.Gray)
                    FilterChip(
                        selected = filterCategory == Category.NONE,
                        onClick = { filterCategory = Category.NONE },
                        label = { Text(stringResource(R.string.chip_all)) }
                    )
                    Category.all.drop(1).forEach { cat ->
                        FilterChip(
                            selected = filterCategory == cat,
                            onClick = { filterCategory = cat },
                            label = { Text(stringResource(cat.labelRes)) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (shown.isEmpty()) {
                    val tip = when {
                        todos.isEmpty() -> stringResource(R.string.empty_hint)
                        query.isNotBlank() -> stringResource(R.string.empty_no_match, query)
                        else -> stringResource(
                            R.string.empty_no_category,
                            stringResource(filterCategory.labelRes)
                        )
                    }
                    val icon = when {
                        todos.isEmpty() -> "🌱"
                        query.isNotBlank() -> "🔍"
                        else -> "📂"
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val floatTrans = rememberInfiniteTransition(label = "float")
                        val floatY by floatTrans.animateFloat(
                            initialValue = 0f,
                            targetValue = -8f,
                            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                            label = "floatY"
                        )
                        Text(
                            icon,
                            fontSize = 40.sp,
                            modifier = Modifier.offset { IntOffset(0, floatY.roundToInt()) }
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            tip,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(shown, key = { _, it -> it.id }) { index, todo ->
                        // 每条待办入场：轻微上滑 + 淡入，错峰出现更有节奏
                        var appeared by remember { mutableStateOf(false) }
                        LaunchedEffect(todo.id) { appeared = true }
                        AnimatedVisibility(
                            visible = appeared,
                            enter = fadeIn(tween(260, delayMillis = (index % 12) * 35)) +
                                slideInVertically(
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    initialOffsetY = { it / 3 }
                                )
                        ) {
                        // 滑开不立即删除：停在「打开」状态露出删除按钮，点按钮才删
                        // 提高滑动阈值：要滑出更明显的横向距离才进入「打开」态，
                        // 避免上下浏览列表时手一抖就误触删除
                        val openThresholdPx = with(LocalDensity.current) { 170.dp.toPx() }
                        val dismissState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { openThresholdPx },
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
                        // 别的行打开 / 点空白处 / 取消删除时，把这一条（若还开着）自动收起
                        LaunchedEffect(openItemId) {
                            if (openItemId != todo.id &&
                                dismissState.currentValue != SwipeToDismissBoxValue.Settled
                            ) {
                                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                            }
                        }
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                // 只露出右侧窄窄一条删除区（不再整行铺满红色）
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                                    Box(
                                        Modifier
                                            .width(88.dp)
                                            .fillMaxHeight()
                                            .background(Color(0xFFE53935))
                                            .clickable(
                                                enabled = dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
                                            ) { pendingDelete = todo },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("🗑", fontSize = 20.sp)
                                            Text(
                                                stringResource(R.string.swipe_delete),
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
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
                                            context.getString(
                                                R.string.toast_repeat_next,
                                                formatDate(next)
                                            ),
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
                        }  // AnimatedVisibility
                    }
                }
                }
                if (tab == 1) TimelineView(todos = todos, onEdit = { editing = it })
                if (tab == 2) StatsView(todos = todos)
                if (tab == 3) TrashView(
                    trash = trash,
                    onRestore = { vm.restoreFromTrash(it.id) },
                    onDeleteForever = { vm.deleteForever(it.id) },
                    onClearAll = { vm.clearTrash() }
                )
            }
        }

        // 删除确认弹窗：点滑出的「删除」按钮后先问一句，确认才真删
        pendingDelete?.let { todo ->
            AlertDialog(
                onDismissRequest = {
                    pendingDelete = null
                    openItemId = null
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDelete = null
                        openItemId = null
                        onDelete(todo)
                    }) {
                        Text(
                            stringResource(R.string.delete),
                            color = Color(0xFFE53935)
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingDelete = null
                        openItemId = null
                    }) { Text(stringResource(R.string.cancel)) }
                },
                title = { Text(stringResource(R.string.dlg_delete_title)) },
                text = {
                    Text(stringResource(R.string.dlg_delete_text, todo.title))
                }
            )
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

        // 添加待办弹窗（右下角圆形＋按钮触发）
        if (addOpen) {
            AddTodoDialog(
                onDismiss = { addOpen = false },
                onAdd = { t, cat, due, rep, note ->
                    vm.add(t, due, cat.name, rep.name, note)
                    addOpen = false
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
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_imported, n),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        // 小组件设置弹窗：配置每个桌面组件显示哪一类待办
        if (widgetSettingsOpen) {
            WidgetSettingsDialog(
                onDismiss = { widgetSettingsOpen = false },
                onSaved = {
                    widgetSettingsOpen = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_widget_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        // 关于弹窗：版本、作者主页、检查更新、功能介绍、使用说明
        if (aboutOpen) {
            AboutDialog(
                onDismiss = { aboutOpen = false },
                onCheckUpdate = {
                    aboutOpen = false
                    checkForUpdate(manual = true)
                }
            )
        }

        // 发现新版本：提示对话框
        updateInfo?.let { info ->
            AlertDialog(
                onDismissRequest = { updateInfo = null },
                confirmButton = {
                    TextButton(onClick = {
                        downloadJob = scope.launch {
                            if (!AppUpdater.canInstallPackages(context)) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_need_install_permission),
                                    Toast.LENGTH_LONG
                                ).show()
                                AppUpdater.openInstallSettings(context)
                                return@launch
                            }
                            updating = true
                            downloadProgress = 0
                            downloadSpeed = 0
                            try {
                                val file = AppUpdater.downloadApk(
                                    context,
                                    info.apkUrl,
                                    info.versionName
                                ) { pct, speed ->
                                    downloadProgress = pct
                                    downloadSpeed = speed
                                }
                                updating = false
                                updateInfo = null
                                AppUpdater.installApk(context, file)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                updating = false
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_update_cancelled),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                updating = false
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.toast_download_failed,
                                        e.message ?: ""
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }) { Text(stringResource(R.string.btn_update_now)) }
                },
                dismissButton = {
                    TextButton(onClick = { updateInfo = null }) {
                        Text(stringResource(R.string.btn_later))
                    }
                },
                title = { Text(stringResource(R.string.dlg_update_title, info.versionName)) },
                text = {
                    if (info.note.isNotBlank()) Text(info.note)
                    else Text(stringResource(R.string.dlg_update_text))
                }
            )
        }

        // 下载中：进度对话框（带取消按钮，网络卡住时可退出）
        if (updating) {
            AlertDialog(
                onDismissRequest = { },
                confirmButton = {
                    TextButton(onClick = {
                        downloadJob?.cancel()
                        updating = false
                    }) { Text(stringResource(R.string.cancel)) }
                },
                title = { Text(stringResource(R.string.dlg_downloading_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(progress = { downloadProgress / 100f })
                        Text(
                            when {
                                downloadProgress == 0 ->
                                    stringResource(R.string.dlg_downloading_probing)
                                downloadSpeed >= 1024 ->
                                    stringResource(
                                        R.string.dlg_downloading_speed_mb,
                                        downloadProgress,
                                        downloadSpeed / 1024.0
                                    )
                                else ->
                                    stringResource(
                                        R.string.dlg_downloading_speed_kb,
                                        downloadProgress,
                                        downloadSpeed
                                    )
                            },
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
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
                label = { Text(stringResource(cat.labelRes)) }
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
        Text(stringResource(R.string.repeat_label), fontSize = 13.sp, color = Color.Gray)
        RepeatRule.all.forEach { rule ->
            FilterChip(
                selected = selected == rule,
                onClick = { onSelect(rule) },
                label = { Text(stringResource(rule.labelRes)) }
            )
        }
    }
}

/**
 * 顶部视图切换：自定义「滑动分段控件」。
 * 选中项背后有一块脑感蓝 pill 滑块，切换时平滑滑动（弹簧感），比默认 Tab 更灵动。
 */
@Composable
fun SegmentedTabs(selected: Int, onSelect: (Int) -> Unit, tabs: List<String>) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val onTrack = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(track, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        val segWidth = (maxWidth - 8.dp) / tabs.size
        val offset by animateDpAsState(
            targetValue = segWidth * selected,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "segOffset"
        )
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(segWidth)
                .fillMaxHeight()
                .background(primary, RoundedCornerShape(9.dp))
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (i == selected) onPrimary else onTrack,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
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
            Text(
                if (dueDate != null) stringResource(R.string.due_with_date, formatDate(dueDate))
                else stringResource(R.string.due_set_with_reminder)
            )
        }
        if (dueDate != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.due_clear)) }
            Text(
                stringResource(R.string.due_reminder_hint),
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
    // 完成态：卡片底色淡出为浅灰蓝，文字变灰；用动画过渡，勾选更有反馈感
    val containerColor by animateColorAsState(
        targetValue = if (todo.done) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(280),
        label = "rowBg"
    )
    // 勾选时卡片轻微缩放，像「按下去」的反馈
    val scale by animateFloatAsState(
        targetValue = if (todo.done) 0.985f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "rowScale"
    )
    Card(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                // 长按置顶；单击不再进编辑（避免浏览时误触弹编辑框），编辑走行尾 ✏️ 或长按
                detectTapGestures(onLongPress = { onTogglePin() })
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (todo.done) 0.dp else 1.5.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = todo.done, onCheckedChange = { onToggle() })

            // 分类色点（外圈带一圈淡色光晕，视觉更柔和）
            Box(
                Modifier
                    .size(16.dp)
                    .background(colorResource(cat.colorRes).copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .background(colorResource(cat.colorRes), CircleShape)
                )
            }

            // 标题（置顶项加 📌）+ 其下方的备注小字
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    (if (todo.pinned) "📌 " else "") + todo.title,
                    textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (todo.done) Color.Gray else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (todo.pinned || cat != Category.NONE) FontWeight.Bold else FontWeight.Normal
                )
                if (todo.note.isNotBlank()) {
                    Text(
                        todo.note,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = if (overdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 周期标记（每天 / 每周 / 每月）
            val rule = RepeatRule.from(todo.repeat)
            if (rule != RepeatRule.NONE) {
                Text(
                    stringResource(R.string.repeat_badge, stringResource(rule.labelRes)),
                    Modifier.padding(start = 8.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 编辑按钮：点一下直接改这条（不用靠「点整行」才发现能编辑）
            val editDesc = stringResource(R.string.a11y_edit)
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = editDesc }
            ) {
                Text("✏️", fontSize = 18.sp)
            }

            // 复制按钮：点一下把这条标题复制到剪贴板
            val copyDesc = stringResource(R.string.a11y_copy)
            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .size(40.dp)
                    .padding(start = 4.dp)
                    .semantics { contentDescription = copyDesc }
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
            }) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        title = { Text(stringResource(R.string.dlg_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.field_content)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.field_note_optional)) },
                    placeholder = { Text(stringResource(R.string.field_note_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    singleLine = false
                )
                CategoryChips(selected = category, onSelect = { category = it })
                DueDateRow(dueDate = dueDate) { dueDate = it }
                RepeatChips(selected = repeat, onSelect = { repeat = it })
                if (repeat != RepeatRule.NONE && dueDate == null) {
                    Text(
                        stringResource(R.string.hint_repeat_no_due),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                    Text(stringResource(R.string.pin_label))
                }
            }
        }
    )
}

/**
 * 添加待办弹窗：点右下角圆形＋按钮弹出。
 * 收集 内容 / 备注 / 分类 / 到期 / 重复，确认后回调 onAdd。
 */
@Composable
fun AddTodoDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Category, Long?, RepeatRule, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.NONE) }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var repeat by remember { mutableStateOf(RepeatRule.NONE) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = title.trim().isNotBlank(),
                onClick = {
                    onAdd(title.trim(), category, dueDate, repeat, note.trim())
                }
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.dlg_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.field_content)) },
                    placeholder = { Text(stringResource(R.string.add_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.field_note_optional)) },
                    placeholder = { Text(stringResource(R.string.field_note_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    singleLine = false
                )
                CategoryChips(selected = category, onSelect = { category = it })
                DueDateRow(dueDate = dueDate) { dueDate = it }
                RepeatChips(selected = repeat, onSelect = { repeat = it })
            }
        }
    )
}

fun formatDate(ts: Long): String {
    return SimpleDateFormat("MM-dd", Locale.getDefault()).format(ts)
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
            .setTitle(context.getString(R.string.dlg_widget_guide_title))
            .setMessage(context.getString(R.string.dlg_widget_guide_text))
            .setPositiveButton(context.getString(R.string.btn_got_it), null)
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
    cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clip_label), text))
    Toast.makeText(
        context,
        context.getString(R.string.toast_copied, text),
        Toast.LENGTH_SHORT
    ).show()
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
            }) { Text(stringResource(R.string.btn_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.dlg_batch_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.batch_import_hint),
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.field_batch_content)) },
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
            }) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.dlg_widget_settings_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.widget_settings_desc),
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Text(stringResource(R.string.widget_default_label), fontWeight = FontWeight.Bold)
                WidgetFilterChips(selected = defaultFilter, onSelect = { defaultFilter = it })

                if (ids.isEmpty()) {
                    Text(
                        stringResource(R.string.widget_none_hint),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                } else {
                    Text(
                        stringResource(R.string.widget_added_label, ids.size),
                        fontWeight = FontWeight.Bold
                    )
                    ids.forEachIndexed { index, id ->
                        Column {
                            Text(
                                stringResource(R.string.widget_item_label, index + 1),
                                fontSize = 13.sp
                            )
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
            label = { Text(stringResource(R.string.chip_all)) }
        )
        // drop(1)：去掉「无」分类——它在 App 内已被当作「全部」的语义，单独筛它没有意义
        Category.all.drop(1).forEach { cat ->
            FilterChip(
                selected = selected == cat.name,
                onClick = { onSelect(cat.name) },
                label = { Text(stringResource(cat.labelRes)) }
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🗓️", fontSize = 40.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.timeline_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    // 日期格式跟随系统语言：中文「10月19日」，英文「Oct 19」
    val isZh = Locale.getDefault().language.startsWith("zh")
    val fmtDay = if (isZh) SimpleDateFormat("M月d日", Locale.CHINA)
                 else SimpleDateFormat("MMM d", Locale.US)
    val fmtWeek = SimpleDateFormat("EEEE", Locale.getDefault())
    val withDate = todos.filter { it.dueDate != null }.sortedByDescending { it.dueDate }
    val noDate = todos.filter { it.dueDate == null }
    val groups = withDate.groupBy { dayStart(it.dueDate!!) }
        .toSortedMap(compareByDescending { it })
        .map { (day, items) -> (Triple(day, fmtDay.format(day), fmtWeek.format(day)) to items) }
        .toMutableList()
    if (noDate.isNotEmpty()) {
        groups.add(Triple(-1L, stringResource(R.string.timeline_unscheduled), "") to noDate)
    }

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
                        Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
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
            .clickable { onEdit(todo) },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .background(colorResource(cat.colorRes).copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(9.dp).background(colorResource(cat.colorRes), CircleShape))
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    (if (todo.pinned) "📌 " else "") + todo.title,
                    textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (todo.done) Color.Gray else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (todo.pinned) FontWeight.Bold else FontWeight.Normal
                )
                if (todo.note.isNotBlank())
                    Text(todo.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            val rule = RepeatRule.from(todo.repeat)
            if (rule != RepeatRule.NONE)
                Text(
                    stringResource(R.string.repeat_badge, stringResource(rule.labelRes)),
                    Modifier.padding(start = 8.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

    // 数字与圆环做「滚上去」的动画：数据变化时平滑过渡，更有生命感
    val rateAnim by animateFloatAsState(
        targetValue = rate,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "rate"
    )
    val totalAnim by animateIntAsState(total, tween(500), label = "total")
    val doneAnim by animateIntAsState(done, tween(500), label = "done")
    val activeAnim by animateIntAsState(active, tween(500), label = "active")
    val overdueAnim by animateIntAsState(overdue, tween(500), label = "overdue")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 总完成率圆环
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                    // 底环（浅色轨道）
                    CircularProgressIndicator(
                        progress = { 1f },
                        Modifier.size(110.dp),
                        strokeWidth = 10.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                    CircularProgressIndicator(
                        progress = { rateAnim },
                        Modifier.size(110.dp),
                        strokeWidth = 10.dp,
                        color = MaterialTheme.colorScheme.primary,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(rateAnim * 100).toInt()}%",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(R.string.stats_rate),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatLine(stringResource(R.string.stats_total, totalAnim), MaterialTheme.colorScheme.onSurface)
                    StatLine(stringResource(R.string.stats_done, doneAnim), Color(0xFF3AA6A0))
                    StatLine(stringResource(R.string.stats_active, activeAnim), Color(0xFFF2994A))
                    if (overdueAnim > 0)
                        StatLine(
                            stringResource(R.string.stats_overdue, overdueAnim),
                            MaterialTheme.colorScheme.error
                        )
                }
            }
        }

        Text(stringResource(R.string.stats_by_category), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        byCat.forEach { (cat, d, n) ->
            val r = if (n == 0) 0f else d.toFloat() / n
            val rAnim by animateFloatAsState(
                targetValue = r,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                label = "catRate"
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .background(colorResource(cat.colorRes).copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(9.dp).background(colorResource(cat.colorRes), CircleShape))
                }
                Text(
                    stringResource(cat.labelRes),
                    Modifier.width(56.dp).padding(start = 8.dp),
                    fontSize = 13.sp
                )
                LinearProgressIndicator(
                    progress = { rAnim },
                    Modifier.weight(1f).height(8.dp),
                    color = colorResource(cat.colorRes),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Text(
                    "$d/$n",
                    Modifier.padding(start = 8.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 统计页的一行数字，统一字号 */
@Composable
private fun StatLine(text: String, color: Color) {
    Text(text, fontSize = 14.sp, color = color)
}

/**
 * 回收站视图：展示软删除的待办，支持单条恢复 / 彻底删除、一键清空。
 * 内容保留 30 天，超期在 App 启动时自动物理清理。
 */
@Composable
fun TrashView(
    trash: List<Todo>,
    onRestore: (Todo) -> Unit,
    onDeleteForever: (Todo) -> Unit,
    onClearAll: () -> Unit
) {
    // 等待确认「彻底删除」的那条；等待确认「清空回收站」
    var pendingForever by remember { mutableStateOf<Todo?>(null) }
    var clearConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // 顶部说明 + 清空按钮
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.trash_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    stringResource(R.string.trash_count, trash.size),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trash.isNotEmpty()) {
                TextButton(onClick = { clearConfirm = true }) {
                    Text(stringResource(R.string.btn_clear), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (trash.isEmpty()) {
            // 空状态：和主列表一致的浮动小动画
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val floatTrans = rememberInfiniteTransition(label = "trashFloat")
                val floatY by floatTrans.animateFloat(
                    initialValue = 0f,
                    targetValue = -8f,
                    animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                    label = "trashFloatY"
                )
                Text(
                    "🗑",
                    fontSize = 40.sp,
                    modifier = Modifier.offset { IntOffset(0, floatY.roundToInt()) }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.trash_empty),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.trash_empty_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(trash, key = { _, it -> it.id }) { index, todo ->
                    var appeared by remember { mutableStateOf(false) }
                    LaunchedEffect(todo.id) { appeared = true }
                    AnimatedVisibility(
                        visible = appeared,
                        enter = fadeIn(tween(260, delayMillis = (index % 12) * 35)) +
                            slideInVertically(
                                spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                initialOffsetY = { it / 3 }
                            )
                    ) {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    todo.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                                    color = if (todo.done)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    stringResource(
                                        R.string.trash_deleted_at,
                                        formatDateTime(todo.deletedAt ?: 0L)
                                    ),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onRestore(todo) }) {
                                        Text(stringResource(R.string.btn_restore))
                                    }
                                    TextButton(onClick = { pendingForever = todo }) {
                                        Text(
                                            stringResource(R.string.btn_delete_forever),
                                            color = MaterialTheme.colorScheme.error
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

    // 彻底删除单条前确认
    pendingForever?.let { todo ->
        AlertDialog(
            onDismissRequest = { pendingForever = null },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteForever(todo)
                    pendingForever = null
                }) {
                    Text(
                        stringResource(R.string.btn_delete_forever),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingForever = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.dlg_delete_forever_title)) },
            text = { Text(stringResource(R.string.dlg_delete_forever_text, todo.title)) }
        )
    }

    // 清空回收站前确认
    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    clearConfirm = false
                }) {
                    Text(
                        stringResource(R.string.btn_clear_all),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.dlg_clear_trash_title)) },
            text = { Text(stringResource(R.string.dlg_clear_trash_text, trash.size)) }
        )
    }
}

/** 带时分的时间格式（回收站「删除于」用） */
fun formatDateTime(ts: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(ts)
}

/**
 * 关于弹窗：当前版本、作者 GitHub 主页、检查更新、功能介绍、使用说明。
 * 用「大标题 + 可滚动正文」结构，滚动区里分段展示，手机上体验舒服。
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit, onCheckUpdate: () -> Unit) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_open_url_failed, e.message ?: ""),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) }
        },
        dismissButton = {
            TextButton(onClick = onCheckUpdate) { Text(stringResource(R.string.menu_check_update)) }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧠", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.about_title), fontWeight = FontWeight.Bold)
                    Text(
                        "v${AppUpdater.currentVersionName()}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 作者主页
                Text(
                    stringResource(R.string.about_author_label),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    stringResource(R.string.about_author_link),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { openUrl(AppUpdater.GITHUB_HOME) }
                )
                Text(
                    stringResource(R.string.about_repo_label),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    stringResource(R.string.about_repo_link),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { openUrl(AppUpdater.GITHUB_REPO) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 功能介绍
                Text(
                    stringResource(R.string.about_features_label),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    stringResource(R.string.about_features_text),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 使用说明
                Text(
                    stringResource(R.string.about_usage_label),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    stringResource(R.string.about_usage_text),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    stringResource(R.string.about_privacy),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
