package com.example.xiaonao.data

import java.util.Calendar

/**
 * 待办的重复周期规则。
 *
 * 用于「周期性待办」（每天背单词 / 每周交周报 / 每月还花呗）：
 * 这类事项不适合「勾完就变已完成」，而是勾完自动顺延到下一个周期、保持未完成，
 * 列表里永远只有一条，干净不堆积。
 *
 * 存进数据库用的是 name（字符串）。
 */
enum class RepeatRule(val label: String) {
    NONE("不重复"),
    DAILY("每天"),
    WEEKLY("每周"),
    MONTHLY("每月");

    companion object {
        fun from(name: String?): RepeatRule =
            values().firstOrNull { it.name == name } ?: NONE

        val all: List<RepeatRule> = values().toList()

        /**
         * 计算下一次到期时间。
         *
         * @param base 基准时间（一般是当前到期时间；没有到期时间则用「现在」推算）
         * @param rule 周期规则
         * @param now  当前时间，用于把结果推进到「未来」
         *
         * 注意：若基准时间已远在过去（比如到期的上周的事今天才勾），
         * 单纯 +1 天仍会是过去时间，所以要循环推进直到晚于 now。
         */
        fun nextDue(base: Long, rule: RepeatRule, now: Long = System.currentTimeMillis()): Long {
            if (rule == NONE) return base
            var t = base
            val cal = Calendar.getInstance()
            var guard = 0
            while (t <= now && guard < 1000) {
                cal.timeInMillis = t
                when (rule) {
                    DAILY -> cal.add(Calendar.DAY_OF_MONTH, 1)
                    WEEKLY -> cal.add(Calendar.DAY_OF_MONTH, 7)
                    MONTHLY -> cal.add(Calendar.MONTH, 1)
                    NONE -> return t
                }
                t = cal.timeInMillis
                guard++
            }
            return t
        }
    }
}
