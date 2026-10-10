package com.example.xiaonao

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.example.xiaonao.TodoRepository
import com.example.xiaonao.data.Category
import com.example.xiaonao.widget.TodoWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 透明弹窗式「快速添加」。
 * 从桌面小组件的「+」按钮唤起，Activity 本身是透明全屏，
 * 视觉上就像浮在桌面之上的一张卡片；输入一条待办后保存并关闭，
 * 整个过程用户感觉「没离开桌面」就记好了一件事。
 */
class QuickAddActivity : ComponentActivity() {

    private var selectedCategory: Category = Category.NONE
    private var dueDate: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_add)

        val edit = findViewById<EditText>(R.id.quick_add_edit)
        val addBtn = findViewById<Button>(R.id.quick_add_btn)
        val cancelBtn = findViewById<View>(R.id.quick_add_cancel)
        val backdrop = findViewById<View>(R.id.quick_add_backdrop)
        val dueBtn = findViewById<Button>(R.id.quick_due_btn)

        val catViews = mapOf(
            Category.NONE to findViewById<TextView>(R.id.quick_cat_none),
            Category.WORK to findViewById(R.id.quick_cat_work),
            Category.STUDY to findViewById(R.id.quick_cat_study),
            Category.LIFE to findViewById(R.id.quick_cat_life)
        )

        fun refreshCatUi() {
            catViews.forEach { (cat, v) ->
                if (cat == selectedCategory) {
                    v.setBackgroundColor(ContextCompat.getColor(this, cat.colorRes))
                    v.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                } else {
                    v.setBackgroundResource(R.drawable.quick_cat_bg)
                    v.setTextColor(ContextCompat.getColor(this, R.color.widget_subtext))
                }
            }
        }
        catViews.forEach { (cat, v) -> v.setOnClickListener { selectedCategory = cat; refreshCatUi() } }
        refreshCatUi()

        fun refreshDueUi() {
            dueBtn.text = if (dueDate != null) "到期：" + formatDate(dueDate!!) else getString(R.string.due_none)
        }
        dueBtn.setOnClickListener {
            val cal = Calendar.getInstance().apply { dueDate?.let { t -> timeInMillis = t } }
            DatePickerDialog(
                this,
                { _: DatePicker, y, m, d ->
                    cal.set(y, m, d, 9, 0)
                    dueDate = cal.timeInMillis
                    refreshDueUi()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // 进入即聚焦并唤起软键盘，方便直接打字
        edit.requestFocus()
        edit.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }, 120)

        // 点遮罩空白处 = 放弃添加
        backdrop.setOnClickListener { finish() }
        // 点卡片本身不应误触关闭
        findViewById<View>(R.id.quick_add_card).setOnClickListener { }

        val save: () -> Unit = {
            val title = edit.text.toString()
            if (title.isNotBlank()) {
                val cat = selectedCategory.name
                val due = dueDate
                CoroutineScope(Dispatchers.IO).launch {
                    TodoRepository.of(this@QuickAddActivity).add(title.trim(), due, cat)
                    TodoWidgetProvider.notifyUpdate(this@QuickAddActivity)
                }
            }
            finish()
        }

        addBtn.setOnClickListener { save() }
        cancelBtn.setOnClickListener { finish() }

        // 键盘回车（actionDone）即保存
        edit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save()
                true
            } else false
        }
    }
}
