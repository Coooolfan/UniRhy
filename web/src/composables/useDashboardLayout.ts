import {
    computed,
    inject,
    onMounted,
    onUnmounted,
    provide,
    ref,
    watch,
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
const SIDEBAR_COLLAPSED_STORAGE_KEY = 'unirhy:web:dashboard-sidebar-collapsed'

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
            isDesktopSidebarCollapsed.value = true
            return
        }

        closeMobileSidebar()
    }

    const toggleSidebar = () => {
        const isDesktop = detectDesktopViewport()
        isDesktopViewport.value = isDesktop

        if (isDesktop) {
            isDesktopSidebarCollapsed.value = !isDesktopSidebarCollapsed.value
            return
        }

        isMobileSidebarOpen.value = !isMobileSidebarOpen.value
    }

    onMounted(() => {
        const storedValue = window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY)
        isDesktopSidebarCollapsed.value = storedValue === '1'

        mediaQueryList = window.matchMedia(DESKTOP_MEDIA_QUERY)
        syncViewport()
        mediaQueryList.addEventListener('change', syncViewport)
    })

    onUnmounted(() => {
        mediaQueryList?.removeEventListener('change', syncViewport)
    })

    watch(isDesktopSidebarCollapsed, (collapsed) => {
        if (typeof window === 'undefined') {
            return
        }

        window.localStorage.setItem(SIDEBAR_COLLAPSED_STORAGE_KEY, collapsed ? '1' : '0')
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
