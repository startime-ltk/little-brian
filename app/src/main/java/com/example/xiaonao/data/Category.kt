package com.example.xiaonao.data

import androidx.annotation.StringRes
import com.example.xiaonao.R

/**
 * 待办分类。
 * 用 name（字符串）存进数据库，widget（RemoteViews）靠 colorRes 取颜色。
 */
enum class Category(@StringRes val labelRes: Int, val colorRes: Int) {
    NONE(R.string.cat_none, R.color.cat_none),
    WORK(R.string.cat_work, R.color.cat_work),
    STUDY(R.string.cat_study, R.color.cat_study),
    LIFE(R.string.cat_life, R.color.cat_life);

    companion object {
        fun from(name: String?): Category =
            values().firstOrNull { it.name == name } ?: NONE

        val all: List<Category> = values().toList()
    }
}
