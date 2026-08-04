import { isTauri } from './platform'

/**
 * 用户拒绝了相机权限。
 *
 * 与一般扫码失败区分开，界面据此引导用户去系统设置里手动开启，
 * 与电池优化插件的处理方式保持一致。
 */
export class CameraPermissionDeniedError extends Error {
    public constructor() {
        super('Camera permission was not granted')
        this.name = 'CameraPermissionDeniedError'
    }
}

const loadScanner = () => import('@tauri-apps/plugin-barcode-scanner')

/**
 * 确保相机权限已授予。
 *
 * 先 `checkPermissions`，仅在用户尚未表态（prompt / prompt-with-rationale）时才
 * `requestPermissions`；已被拒绝则不再重复打扰，直接抛 [CameraPermissionDeniedError]。
 */
export async function ensureCameraPermission(): Promise<void> {
    if (!isTauri()) {
        throw new Error('Barcode scanning requires the UniRhy mobile app')
    }
    const { checkPermissions, requestPermissions } = await loadScanner()
    let permission = await checkPermissions()
    if (permission !== 'granted' && permission !== 'denied') {
        permission = await requestPermissions()
    }
    if (permission !== 'granted') {
        throw new CameraPermissionDeniedError()
    }
}

/**
 * 页面内扫码并返回识别到的二维码内容；用户主动取消时返回 `null`。
 *
 * windowed 模式把相机预览铺在 WebView 后方并把 WebView 设为透明，
 * 相机的可见区域由界面用透明镂空决定（见 components/login/QrScanner.vue）。
 *
 * 仅在移动端 Tauri 壳中可用：条码扫描由官方插件实现，浏览器与桌面端不提供。
 */
export async function scanQrCodeWindowed(): Promise<string | null> {
    await ensureCameraPermission()
    const { scan, Format } = await loadScanner()
    try {
        const scanned = await scan({ windowed: true, formats: [Format.QRCode] })
        return scanned.content.length > 0 ? scanned.content : null
    } catch (error) {
        // 插件的 cancel() 会以 "cancelled" 拒绝挂起的 scan()，统一按用户取消处理
        if (error instanceof Error && error.message.includes('cancelled')) return null
        throw error
    }
}

/** 中止仍在进行的扫码：插件销毁相机并拒绝挂起的 scan()，后者按用户取消返回。 */
export async function cancelQrScan(): Promise<void> {
    if (!isTauri()) return
    const { cancel } = await loadScanner()
    await cancel().catch(() => undefined)
}

/** 打开系统设置页，供用户在拒绝授权后手动开启相机权限。 */
export async function openCameraSettings(): Promise<void> {
    if (!isTauri()) return
    const { openAppSettings } = await loadScanner()
    await openAppSettings().catch(() => undefined)
}
