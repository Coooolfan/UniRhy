# Tauri 运行时通过字符串反射加载插件入口
-keep class app.unirhy.playback.UnirhyPlaybackPlugin { *; }

# Tauri @InvokeArg 命令参数经反射填充（Kotlin var 会生成 getter/setter/field，
# Jackson-kotlin 通过主构造反序列化）；任一名称被最小化都会静默丢参
-keep @app.tauri.annotation.InvokeArg class * { *; }
# 复合参数的嵌套元素类型（如 LocalQueueItemArg 作为 items 列表元素）
# 未必总带 @InvokeArg，按命名兜底
-keep class app.unirhy.playback.*Args { *; }
-keep class app.unirhy.playback.*Arg  { *; }

# Jackson (Kotlin 模块) 反射读写播放同步协议 DTO；R8 一旦把字段/getter 改成
# a/b/c，服务端解析 HELLO / SNAPSHOT / SCHEDULED_ACTION 全部失效
-keep class app.unirhy.playback.sync.** { *; }
-keepclassmembers enum app.unirhy.playback.sync.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Jackson 反射依赖 Kotlin 元数据、泛型签名与运行时可见注解
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes Signature, InnerClasses, EnclosingMethod
