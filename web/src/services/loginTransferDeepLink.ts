import { ref } from 'vue'
import { getAuthToken } from '@/ApiInstance'
import { getPlatformRuntime } from '@/runtime/platform'
import { isMobilePlatform } from '@/runtime/platform.shared'
import { decodeLoginTransferUri } from '@/services/loginTransferQr'

/**
 * 等待消费的登录交接深链接。
 *
 * 系统扫码组件以 `unirhy://t/...` 拉起 App 时（冷/热启动），链接先暂存在这里，
 * 登录页侦听它并直接进入认领流程（见 views/LoginView.vue）。
 */
export const pendingLoginTransferLink = ref<string | null>(null)

const accept = (url: string) => {
    // 已登录的设备不是交接的目标（那通常是把码扫回了原设备），直接忽略
    const token = getAuthToken()
    if (token !== null && token.trim().length > 0) return
    try {
        // 只认登录交接链接，其它 unirhy:// 链接一律忽略
        decodeLoginTransferUri(url)
        pendingLoginTransferLink.value = url
    } catch {
        // 无法识别的链接直接忽略
    }
}

/**
 * 监听深链接：仅在移动端注册。
 *
 * 热启动走 `onOpenUrl` 事件；冷启动时链接在 launch intent 里，用 `getCurrent` 取。
 */
export async function initLoginTransferDeepLink(): Promise<void> {
    if (!isMobilePlatform(getPlatformRuntime().platform)) return
    const { onOpenUrl, getCurrent } = await import('@tauri-apps/plugin-deep-link')
    await onOpenUrl((urls) => {
        urls.forEach((url) => {
            accept(url)
        })
    })
    const current = await getCurrent().catch(() => null)
    current?.forEach((url) => {
        accept(url)
    })
}
