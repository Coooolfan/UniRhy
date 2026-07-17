# Tauri 运行时通过字符串反射加载插件入口；Tauri 官方 consumer rules
# 只 keep 它自己的类（proguard-tauri.pro 仅一行 TauriActivity），插件类必须自 keep。
# @InvokeArg 参数类由 tauri-android 官方 consumer rules 自动 keep，无需重复声明。
-keep class app.unirhy.playback.UnirhyPlaybackPlugin { *; }

# Tauri 官方规则只 keep 带 @TauriPlugin 注解的类"自身声明"的 @Command 方法，
# 基类 Plugin 里的 registerListener/removeListener 等命令方法不在覆盖范围内，
# 被 R8 重命名后 JS addPluginListener 反射查找失败，事件面（trigger→JS）全断
-keep class app.tauri.plugin.Plugin { *; }

# Jackson (Kotlin 模块) 反射读写播放同步协议 DTO；R8 一旦把字段/getter 改成
# a/b/c，服务端解析 HELLO / SNAPSHOT / SCHEDULED_ACTION 全部失效。
# { *; } 已覆盖构造器与 Kotlin 合成的 $default 桥接方法。
-keep class app.unirhy.playback.sync.** { *; }
-keepclassmembers enum app.unirhy.playback.sync.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# jackson-module-kotlin 官方 README 要求：保留 Kotlin 元数据供 kotlin-reflect
# 读主构造函数参数名与默认值。kotlin-reflect 1.4+ 自带内嵌 R8 规则，无需额外 keep。
-keep class kotlin.Metadata { *; }

# Jackson 反射依赖泛型签名与运行时可见注解（R8 full mode 下仅对被 keep 的类生效）
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes Signature, InnerClasses, EnclosingMethod
