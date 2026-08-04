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
 * 调起原生扫码界面并返回识别到的二维码内容；用户主动取消时返回 `null`。
 *
 * 仅在移动端 Tauri 壳中可用：条码扫描由官方插件实现，浏览器与桌面端不提供。
 */
export async function scanQrCode(): Promise<string | null> {
    if (!isTauri()) {
        throw new Error('Barcode scanning requires the UniRhy mobile app')
    }
    const { scan, Format, checkPermissions, requestPermissions } = await loadScanner()

    let permission = await checkPermissions()
    // 尚未表态（prompt / prompt-with-rationale）时才弹窗，已被拒绝则不再打扰
    if (permission !== 'granted' && permission !== 'denied') {
        permission = await requestPermissions()
    }
    if (permission !== 'granted') {
        throw new CameraPermissionDeniedError()
    }

    const scanned = await scan({ windowed: false, formats: [Format.QRCode] })
    return scanned.content.length > 0 ? scanned.content : null
}

/** 中止仍在进行的扫码，用于离开界面时释放相机。 */
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
