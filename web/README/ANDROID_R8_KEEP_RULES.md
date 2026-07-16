# Android R8 反射保护规则

Android release 包会开启 R8 minify（`isMinifyEnabled = true`），把类名、字段名、方法名（包括 Kotlin 编译期生成的合成方法）改成 `a`/`b`/`c`。所有走反射的类都必须在插件的 `consumer-rules.pro` 里显式 keep，否则 release 包会静默失败（大概率不 crash、只是某处 null 或 Promise reject）。debug 包不 minify，所以问题只在正式包上暴露，且下游客户端排查成本很高。

规则文件：`web/src-tauri/vendor/tauri-plugin-unirhy-playback/android/consumer-rules.pro`。它以 `consumerProguardFiles` 声明，会随插件自动传播到宿主 APK 的 R8 pass。

## 需要 keep 的类别

### 1. Tauri 插件入口

Tauri 通过字符串反射加载插件：

```proguard
-keep class app.unirhy.playback.UnirhyPlaybackPlugin { *; }
```

### 2. `@InvokeArg` 命令参数类

Tauri 反射填充参数字段。Kotlin `var` 会生成 field + getter + setter，任一名字被改都会导致命令入参为空、返回值缺字段。类里的 name、复合参数的嵌套元素（如 `LocalQueueItemArg` 作为 `LocalSetQueueArgs.items` 元素）都要 keep：

```proguard
-keep @app.tauri.annotation.InvokeArg class * { *; }
-keep class app.unirhy.playback.*Args { *; }
-keep class app.unirhy.playback.*Arg  { *; }
```

### 3. Jackson 反射序列化/反序列化的 DTO

原生播放同步协议全部走 `PlaybackSyncJson.mapper`（jackson-module-kotlin）。整个 `sync.**` 包 keep 全成员：

```proguard
-keep class app.unirhy.playback.sync.** { *; }
-keepclassmembers enum app.unirhy.playback.sync.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
```

### 4. Kotlin data class 的合成默认参数方法（**最容易漏**）

带默认值的构造函数（如 `HelloPayload(val deviceId: String, val clientVersion: String? = null, val token: String? = null)`）会由编译器生成 `HelloPayload$default(...)` 合成桥接方法，jackson-module-kotlin 的 `ConstructorValueCreator` 通过反射调用它读默认值。R8 只 keep 类和构造函数不够——synthetic method 会被改名，反射崩溃、被 `runCatching` 静默吞掉：

```proguard
-keepclassmembers class app.unirhy.playback.sync.** {
    <init>(...);
    public synthetic <methods>;
}
```

### 5. Kotlin 元数据与 jackson-kotlin 反射依赖链

jackson-module-kotlin 通过 kotlin-reflect 读主构造函数参数名与默认值，链路上任何一环被 minify 都会失败：

```proguard
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.jvm.internal.DefaultConstructorMarker { *; }
-keep class com.fasterxml.jackson.module.kotlin.** { *; }
```

### 6. 反射依赖的属性

```proguard
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes Signature, InnerClasses, EnclosingMethod
```

## 常见症状与定位

- **正式包点播放无反应、通知栏也不出现**：多半是插件入口/`@InvokeArg`/DTO 字段名/synthetic method 之一被 minify。
- **WS 卡在 `phase STOPPED -> CONNECTING` 后不再前进**：服务端第一条 SNAPSHOT 反序列化失败被静默吞掉，看 `UnirhyPlaybackProtocol` tag 是否有 `parse treeToValue failed type=...` 日志。
- **debug 包完全正常**：不能作为正式包的证据，debug 不 minify。

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

- 落在 `app.unirhy.playback.sync.**` 包下，或加显式 keep。
- 如果带默认参数值：已被 `public synthetic <methods>` 覆盖，不用额外操作。
- 如果新增 `@InvokeArg` 类：命名带 `Args`/`Arg` 后缀，或依赖 `@InvokeArg` 注解匹配规则。
- 构建后 grep 一次 mapping.txt 确认 getter 保留原名。
