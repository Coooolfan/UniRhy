# 扫码登录（客户端）

原设备展示二维码、新设备扫码，把已登录会话交接到新设备。本文描述客户端这一半：载荷编解码、各呈现介质的编码选择、扫码能力的接入方式，以及组件的状态流转。

协议、数据库模型与 HTTP 接口见 [server/README/LOGIN_TRANSFER.md](../../server/README/LOGIN_TRANSFER.md)。

## 角色与流程

| 角色   | 界面                                                            | 凭据                      |
| ------ | --------------------------------------------------------------- | ------------------------- |
| 原设备 | `components/dashboard/LoginTransferPanel.vue`（个人资料弹窗内） | 账号 JWT                  |
| 新设备 | `components/login/QrLoginPanel.vue`（移动端登录页）             | 二维码密钥 → 认领访问令牌 |

1. 原设备创建交接，拿到 `id` 与一次性 `secret`，用当前服务端地址与 `secret` 生成二维码。
2. 新设备扫码得到目标实例地址与 `secret`，切换并持久化后端地址，调 `POST /api/login-transfers/claims` 认领，换回**认领访问令牌**。
3. 两侧各自以 1 Hz 轮询同一条交接：原设备等待认领并做出审批，新设备等待审批结果。
4. 审批通过后新设备调 `POST /api/login-transfers/{id}/tokens` 换取 JWT，交接进入 `COMPLETED`。

二维码只授予「发起登录请求」的能力，真正登录仍需原设备点击允许。

## 载荷编解码

模块：`services/loginTransferQr.ts`。

载荷的**规范形式是一段紧凑二进制**，各呈现介质在其上选择自己的编码，而不是共用一个文本 URI：

```text
flags(1B) | 地址 | port(2B) | secret(10B)
```

| 字段     | 说明                                                                                        |
| -------- | ------------------------------------------------------------------------------------------- |
| `flags`  | bit0-2 协议版本（当前 `1`）；bit3 scheme（0=http，1=https）；bit4-5 地址类型                |
| 地址     | 类型 `0`：IPv4 四字节。类型 `1`：后缀字典索引 1B + 标签长度 1B + 标签。类型 `2` 保留给 IPv6 |
| `port`   | 大端无符号 16 位                                                                            |
| `secret` | 10 字节安全随机数                                                                           |

交接 UUID **不进入载荷**。服务端在 `qr_secret_hash` 上建了唯一索引，密钥同时充当查询键与凭据，因此认领接口不需要路径参数。这一项就省下 128 bit，比压缩密钥本身收益更大。

### 各介质的编码

| 介质              | 编码                     | 入口                                                      |
| ----------------- | ------------------------ | --------------------------------------------------------- |
| 二维码、深链接    | `unirhy://t/<base64url>` | `encodeLoginTransferUri` / `decodeLoginTransferUri`       |
| NFC、音频（预留） | 纯十进制数字             | `encodeLoginTransferDigits` / `decodeLoginTransferDigits` |
| 蓝牙广播          | 原始字节                 | `packLoginTransferPayload` / `unpackLoginTransferPayload` |

二维码用 URI 而非更紧凑的数字编码，是因为只有标准 URI 才能被系统扫码组件识别为链接并拉起 App（见「深链接接收」）；屏幕展示、近距离扫描的场景对码密度不敏感，为此牺牲通用性不值得。数字编码（3.32 bit/字符，几乎无浪费）是载荷最紧凑的文本形式，预留给 NFC、音频这类超低带宽介质，扫码入口对它保留兼容（`decodeLoginTransferScan`）。17 字节的原始形式可直接放进 BLE 广播包的 31 字节限额。

### 域名后缀字典

地址类型 `1` 用一张后缀字典把常见域名后缀压成一个索引，索引 `0` 表示未命中、标签即完整主机名。命中 `.duckdns.org`、`.tailscale.net` 这类多段后缀时收益最明显（约 40% 字节）。

**字典只允许在末尾追加，不得重排或删除。** 二维码只活 120 秒，不存在历史码兼容问题，但生成端（桌面浏览器，随服务端更新）与扫描端（已安装的移动端 App，可能滞后）版本可能不一致。扫描端遇到未知索引会抛 `UnsupportedPayloadError`，界面应提示升级客户端；协议本身的不兼容变更走 `flags` 里的版本位。

### 尺寸

URI 形式为 `unirhy://t/`（11 字符）加 Base64URL 载荷（17–28B → 23–38 字符），共 34–49 字符，字节模式 EC-L 下统一落在 **v3 29×29**（容量 53 字符）。比数字码大约一到两档版本，屏幕展示无影响。

数字编码仍是密度参照：以 EC-L 实测，裸 IPv4 地址（17B / 41 位）可进 v1 21×21——v1 数字模式上限约 136 bit，任何域名至少还要索引、标签长度与标签本身。这也是把它留给超低带宽介质的原因。

### 回环地址

`assertReachableServerUrl` 拒绝 `localhost`、`127.x.x.x`、`0.0.0.0`、`[::1]`，抛 `LoopbackServerUrlError`。`packLoginTransferPayload` 内部无条件调用它，因此不存在绕过校验产出不可用二维码的入口；面板另在创建交接**之前**调用一次做预检，避免为注定失败的地址先建一条交接。

浏览器里服务端地址取 `window.location.origin`，所以本机开发时必须用局域网地址访问前端才能生成二维码。

## 扫码能力

模块：`runtime/barcodeScanner.ts`，封装 `@tauri-apps/plugin-barcode-scanner`。

扫码在页面内完成，不切换原生全屏界面：插件以 `windowed: true` 启动，相机预览铺在 WebView **后方**并把 WebView 背景设为透明，可见区域由前端决定。`components/login/QrScanner.vue` Teleport 一层取景 UI 到 body：box-shadow 背板遮住全屏，中央透明镂空露出相机；扫码期间 `html.qr-scan-windowed` 隐藏 `#app`（见 `style/main.css`），镂空处因此只剩相机画面。识别成功或取消后插件自动拆除预览并恢复 WebView 背景，运行时随之摘掉样式类。

- `scanQrCodeWindowed()` 先 `ensureCameraPermission()`：`checkPermissions` 之后仅在用户尚未表态时才 `requestPermissions`，已被拒绝则不再打扰并抛 `CameraPermissionDeniedError`；界面据此展示「前往设置开启相机权限」（`openCameraSettings()`），与电池优化插件的引导方式一致。
- 插件的 `cancel()` 会以 `cancelled` 拒绝挂起的 `scan()`，运行时把它统一映射为用户取消（返回 `null`）；组件卸载时调 `cancelQrScan()` 中止扫码并释放相机。
- 原生能力按仓库惯例接入：Rust 侧依赖按 `cfg(target_os = "android"/"ios")` 门控，插件在 `#[cfg(mobile)]` 下注册，权限在 `src-tauri/capabilities/mobile.json` 声明。
- windowed 模式在 iOS 上存在未修复的上游缺陷（`scan()` 永不返回，tauri-apps/plugins-workspace#3348）；当前移动端仅支持 Android，不受影响。

插件会在自己的清单里声明 `android.hardware.camera` 与 `android.hardware.camera.any`，后者**不带 `android:required`，缺省即 `true`**。本仓库支持 AndroidTV（leanback），因此 `gen/android/app/src/main/AndroidManifest.xml` 用 `tools:replace="android:required"` 把两者都改回 `false`，避免应用市场把无摄像头的设备过滤掉。改动扫码插件版本后，值得复查一次合并产物：

```bash
# 构建后检查合并结果，三条都应为 required="false"
grep -oE '<uses-feature[^>]*>' \
  src-tauri/gen/android/app/build/intermediates/packaged_manifests/*/*/AndroidManifest.xml
```

扫码只在移动端提供：`runtime/platform.shared.ts` 的 `isMobilePlatform()` 同时决定登录页是否显示扫码入口、以及认领时上报的平台值（`platform.toUpperCase()` 与后端枚举一一对应）。浏览器与桌面端没有扫码入口，也无法调用该插件。

登录页有两处扫码入口，共用 `QrScanner`：

- 登录表单的「扫描二维码快速登录」进入完整交接流程（`QrLoginPanel.vue`）；
- 服务端地址表单的「扫码识别服务端地址」只从码中取出实例地址填入输入框（`parseServerUrlFromScan`），登录交接载荷与纯 http(s) 地址两种内容都接受。

## 深链接接收

二维码内容是 `unirhy://t/...` 标准 URI，因此系统相机、扫码组件扫到后可直接拉起 App，不必先打开 App 再找扫码入口。接收端由 `@tauri-apps/plugin-deep-link` 实现：

- scheme 在 `tauri.conf.json` 的 `plugins.deep-link.mobile` 声明（`unirhy` + host `t`），插件构建期据此生成 Android intent-filter；权限在 `src-tauri/capabilities/mobile.json` 声明，插件与条码扫描一样仅移动端注册。
- `services/loginTransferDeepLink.ts` 在 App 启动时挂监听：热启动走 `onOpenUrl` 事件，冷启动的链接在 launch intent 里、用 `getCurrent()` 取。无法识别或已登录（那通常是把码扫回了原设备）的链接直接忽略。
- 合法链接暂存在 `pendingLoginTransferLink`，`LoginView` 侦听到后跳过扫码，把链接作为 `prefilledPayload` 交给 `QrLoginPanel` 直接进入认领流程，后续与扫码路径完全一致。

不追求 Android App Links / Universal Links：它们要求 https 域名与服务端验证文件，自建实例多为 `http://IP:端口`，不满足条件。

## 状态与轮询

`services/loginTransferStatus.ts` 导出 `ACTIVE_LOGIN_TRANSFER_STATUSES`（`WAITING` / `CLAIMED` / `AUTHORIZED`），两个面板共用，避免各自维护互补的字符串清单。类型取自生成的 `LoginTransferStatus`，枚举变动会在编译期暴露。

两个面板的轮询都是**自递归的单点续期**：卸载时置停止标志，在途请求的回调不会再重新武装定时器。原设备面板另有一个 1 Hz 倒计时定时器，在交接进入终态时随即停止。

轮询错误在两侧都**不致命**：请求失败只展示提示并保持 1 Hz 轮询，恢复成功后提示自动清除，移动网络抖动不会中断登录。

界面随状态收敛：二维码只在 `WAITING` 状态展示，被认领后随即收起；交接完成（`COMPLETED`）后原设备面板短暂展示成功状态并自动退回个人资料页；过期、被拒、取消等终态提供「生成新二维码」原地重建交接。新设备一侧收到终态则进入失败态，可「重新扫码」从头开始。关闭原设备面板只会取消 `WAITING` / `CLAIMED` 的交接——`AUTHORIZED` 之后新设备正在换取令牌，此时关窗不应打断登录。

查询接口对终态（含 `EXPIRED`）返回 `200` 与真实状态，`410` 只用于会改变状态的接口，因此客户端不需要用本地时钟合成过期状态。

## 演进约束

- 二维码密钥为 80 bit。它一次性、有效期以分钟计、只有在线攻击面（库中仅存 SHA-256 摘要且从不外露），穷举不可行。认领访问令牌仍为 256 bit——它只走 HTTP 头，不受载荷长度约束。
- 新增「仅原设备可见」的字段时，只需加进 `LoginTransferController.SOURCE_TRANSFER_FETCHER`；不显式加进 `CLAIM_TRANSFER_FETCHER` 就不会对新设备可见。
- 后缀字典追加即可，无需版本位；改变载荷结构才需要提升 `flags` 中的版本。
