# 小脑 - 本地待办 + 桌面小组件
# v1.23 起 release 开启 R8（minifyEnabled + shrinkResources），此处补稳妥 keep 规则。

# ---- Room ----
# Room 通过注解处理器生成实现代码，实体/DAO/数据库按名引用，保留防误伤
-keep class com.example.xiaonao.data.** { *; }

# ---- 枚举 ----
# Category / RepeatRule 等枚举用 name 互转（from()），保留 valueOf/values
-keepclassmembers enum com.example.xiaonao.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Compose / AndroidX ----
# Compose、AndroidX 库的 AAR 自带 consumer rules，这里只兜底保留运行时入口
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ---- 小组件 ----
# RemoteViews 相关组件由系统实例化，manifest 引用默认已保留；显式保留双保险
-keep class com.example.xiaonao.widget.** { *; }

# ---- 保留行号便于崩溃分析（体积代价极小）----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
