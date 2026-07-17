# Android R8 反射保护规则

Android release 包会开启 R8 minify（`isMinifyEnabled = true`，AGP 8.0 起默认 full mode），把类名、字段名、方法名改成 `a`/`b`/`c`。所有走反射的类都必须被 keep 规则覆盖，否则 release 包会静默失败（大概率不 crash、只是某处 null 或 Promise reject）。debug 包不 minify，所以问题只在正式包上暴露，且下游客户端排查成本很高。

规则文件：`web/src-tauri/vendor/tauri-plugin-unirhy-playback/android/consumer-rules.pro`。它以 `consumerProguardFiles` 声明，会随插件自动传播到宿主 APK 的 R8 pass。

## 规则分工：Tauri 官方已覆盖的部分

tauri-android 库（`~/.cargo/registry/src/*/tauri-2.x/mobile/android/proguard-rules.pro`）自带 consumer rules，已自动 keep：

- `@TauriPlugin public class *` 的 `@Command` 方法与 public 构造器
- `@InvokeArg public class * { *; }`（含字段、getter/setter、构造器）
- `JSObject` / `JSArray`、JNI/native 入口、Jackson 自定义序列化器

因此插件侧**不需要**重复声明 `@InvokeArg` 规则。前提：所有命令参数类（包括复合参数的嵌套元素类型，如 `LocalQueueItemArg`）都必须标注 `@InvokeArg` 且为 public。

Tauri **不会** keep 插件类本身（`proguard-tauri.pro` 只 keep `TauriActivity.getPluginManager()`），插件入口必须自 keep。

## 插件侧需要 keep 的类别

### 1. Tauri 插件入口

Tauri Rust 端通过字符串类名反射加载插件：

```proguard
-keep class app.unirhy.playback.UnirhyPlaybackPlugin { *; }
```

### 1.1 Tauri 基类 Plugin 的命令方法（官方规则的空洞）

官方规则 `-keep @TauriPlugin public class * { @Command <methods>; ... }` 只匹配注解类**自身声明**的方法，覆盖不到基类 `app.tauri.plugin.Plugin` 里的 `registerListener` / `removeListener`（它们也是 `@Command`，由 JS `addPluginListener` 按名反射调用）。R8 会把它们重命名/合并，导致**事件面（Kotlin `trigger` → JS）整体断掉**：命令都正常、音频也在播，但 JS 收不到任何事件、UI 永不更新。

```proguard
-keep class app.tauri.plugin.Plugin { *; }
```

### 2. Jackson 反射序列化/反序列化的 DTO

原生播放同步协议全部走 `PlaybackSyncJson.mapper`（jackson-module-kotlin）。整个 `sync.**` 包 keep 全成员——`{ *; }` 已覆盖构造器与 Kotlin 编译器生成的 `$default` 合成桥接方法，无需额外的 `keepclassmembers`：

```proguard
-keep class app.unirhy.playback.sync.** { *; }
-keepclassmembers enum app.unirhy.playback.sync.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
```

### 3. Kotlin 元数据

jackson-module-kotlin 通过 kotlin-reflect 读主构造函数参数名与默认值，官方 README（ProGuard 段）要求保留 `kotlin.Metadata`：

```proguard
-keep class kotlin.Metadata { *; }
```

官方 README 的三级策略：`kotlin.Metadata`（必需）→ `kotlin.reflect.**`（**仅当**出现 `ExceptionInInitializerError` 时）→ keep 具体模型类。kotlin-reflect 1.4+ 自带内嵌 R8 规则，正常情况下不需要 `-keep class kotlin.reflect.** { *; }`，也不需要 keep `com.fasterxml.jackson.module.kotlin.**` 或 `DefaultConstructorMarker`——这些全局 keep 无官方依据，只会增大包体积并干扰排查。

### 4. 反射依赖的属性

```proguard
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes Signature, InnerClasses, EnclosingMethod
```

注意 R8 full mode 下这些属性**只**保留在被 keep 规则命中的类上，`-keepattributes` 本身不阻止类被删除或改名。

## 常见症状与定位

- **正式包点播放无反应、通知栏也不出现**：多半是插件入口/`@InvokeArg`/DTO 字段名之一被 minify。
- **音频在播（系统 MediaSession 为 PLAYING）但 App UI 不更新**：基类 `Plugin.registerListener` 被 minify，事件面断掉，见 1.1。
- **JS 侧错误在 release 完全不可见**：Tauri/wry 的 Logger 在 `BuildConfig.DEBUG=false` 时全静默（不是被 R8 删的），WebView console 也不转发。前端可用 `reportNativeJsLog`（`js_log` 命令，tag `UnirhyJs`）把关键错误落 logcat；`nativePlaybackBridge` 已对所有 invoke 失败与全局未捕获错误自动上报。
- **WS 卡在 `phase STOPPED -> CONNECTING` 后不再前进**：服务端第一条 SNAPSHOT 反序列化失败被静默吞掉，看 `UnirhyPlaybackProtocol` tag 是否有 `parse treeToValue failed type=...` 日志。
- **完全没有任何 `UnirhyPlayback*` 日志 ≠ 插件没初始化**：Tauri 的 `PluginManager.dispatchPluginMessage` 会捕获命令内部一切异常并只走 `invoke.reject(...)`——`configure()` 第一行 `parseArgs` 失败时不 crash、不打业务 tag。排查时抓 `Tauri` / `Plugin` / `RustStdoutStderr` tag，并在 JS 侧给 `invoke` 挂 `.catch` 记录。
- **debug 包完全正常**：不能作为正式包的证据，debug 不 minify。

## 版本兼容

jackson-module-kotlin 各版本线有明确的 Kotlin 支持范围（见其 README 兼容性表，如 2.15.x 仅支持 Kotlin 1.5–1.8）。升级 Kotlin Gradle Plugin 时需核对 jackson-module-kotlin 版本是否覆盖。

## 验证方法

跑一次不签名的 release 构建就够，不必等 CD：

```sh
cd web
JAVA_HOME=~/Library/Java/JavaVirtualMachines/temurin-21.0.11/Contents/Home \
    yarn tauri android build --target aarch64
```

看 `web/src-tauri/gen/android/app/build/outputs/mapping/universalRelease/mapping.txt`，关键 DTO 的 getter 应保留原名，例如：

```
app.unirhy.playback.sync.HelloPayload -> app.unirhy.playback.sync.HelloPayload:
    1:3:java.lang.String getDeviceId():34:34 -> getDeviceId
    1:3:java.lang.String getClientVersion():35:35 -> getClientVersion
    1:3:java.lang.String getToken():36:36 -> getToken
```

如果 getter 变成了 `-> a` / `-> b`，规则就有漏。

## 新增反射类型的 checklist

- Jackson DTO：落在 `app.unirhy.playback.sync.**` 包下，或加显式 keep。
- 新增 `@InvokeArg` 类：标注 `@InvokeArg` 且保持 public（含嵌套元素类型），Tauri 官方 consumer rules 会自动 keep。
- 构建后 grep 一次 mapping.txt 确认 getter 保留原名。
