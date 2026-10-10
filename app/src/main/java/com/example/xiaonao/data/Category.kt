package com.example.xiaonao.data

import com.example.xiaonao.R

/**
 * 待办分类。
 * 用 name（字符串）存进数据库，widget（RemoteViews）靠 colorRes 取颜色。
 */
enum class Category(val label: String, val colorRes: Int) {
    NONE("无", R.color.cat_none),
    WORK("工作", R.color.cat_work),
    STUDY("学习", R.color.cat_study),
    LIFE("生活", R.color.cat_life);

    companion object {
        fun from(name: String?): Category =
            values().firstOrNull { it.name == name } ?: NONE

        val all: List<Category> = values().toList()
    }
}
