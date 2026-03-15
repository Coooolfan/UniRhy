import {
    computed,
    inject,
    onMounted,
    onUnmounted,
    provide,
    ref,
    type ComputedRef,
    type InjectionKey,
    type Ref,
} from 'vue'

type DashboardLayoutContext = {
    isDesktopViewport: Ref<boolean>
    isDesktopSidebarCollapsed: Ref<boolean>
    isMobileSidebarOpen: Ref<boolean>
    isSidebarVisible: ComputedRef<boolean>
    openSidebar: () => void
    closeSidebar: () => void
    closeMobileSidebar: () => void
    toggleSidebar: () => void
}

const DASHBOARD_LAYOUT_KEY: InjectionKey<DashboardLayoutContext> = Symbol('dashboard-layout')
const DESKTOP_MEDIA_QUERY = '(min-width: 768px)'

export const provideDashboardLayout = () => {
    const isDesktopViewport = ref(false)
    const isDesktopSidebarCollapsed = ref(false)
    const isMobileSidebarOpen = ref(false)
    const isSidebarVisible = computed(() =>
        isDesktopViewport.value ? !isDesktopSidebarCollapsed.value : isMobileSidebarOpen.value,
    )

    let mediaQueryList: MediaQueryList | null = null

    const detectDesktopViewport = () => {
        if (typeof window === 'undefined') {
            return true
        }

        return window.matchMedia(DESKTOP_MEDIA_QUERY).matches
    }

    const syncViewport = () => {
        const isDesktop = detectDesktopViewport()
        isDesktopViewport.value = isDesktop

        if (isDesktop) {
            isDesktopSidebarCollapsed.value = false
            isMobileSidebarOpen.value = false
        }
    }

    const openSidebar = () => {
        const isDesktop = detectDesktopViewport()
        isDesktopViewport.value = isDesktop

        if (isDesktop) {
            isDesktopSidebarCollapsed.value = false
            return
        }

        isMobileSidebarOpen.value = true
    }

    const closeMobileSidebar = () => {
        isMobileSidebarOpen.value = false
    }

    const closeSidebar = () => {
        const isDesktop = detectDesktopViewport()
        isDesktopViewport.value = isDesktop

        if (isDesktop) {
            return
        }

        closeMobileSidebar()
    }

    const toggleSidebar = () => {
        const isDesktop = detectDesktopViewport()
        isDesktopViewport.value = isDesktop

        if (isDesktop) {
            return
        }

        isMobileSidebarOpen.value = !isMobileSidebarOpen.value
    }

    onMounted(() => {
        mediaQueryList = window.matchMedia(DESKTOP_MEDIA_QUERY)
        syncViewport()
        mediaQueryList.addEventListener('change', syncViewport)
    })

    onUnmounted(() => {
        mediaQueryList?.removeEventListener('change', syncViewport)
    })

    const context: DashboardLayoutContext = {
        isDesktopViewport,
        isDesktopSidebarCollapsed,
        isMobileSidebarOpen,
        isSidebarVisible,
        openSidebar,
        closeSidebar,
        closeMobileSidebar,
        toggleSidebar,
    }

    provide(DASHBOARD_LAYOUT_KEY, context)

    return context
}

export const useDashboardLayout = () => {
    const context = inject(DASHBOARD_LAYOUT_KEY)

    if (!context) {
        throw new Error('Dashboard layout context is not available')
    }

    return context
}
