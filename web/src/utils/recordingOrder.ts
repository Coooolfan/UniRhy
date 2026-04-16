export type ReorderPosition = 'before' | 'after'

export type ReorderPayload = {
    draggedId: number
    targetId: number
    position: ReorderPosition
}

const RECORDING_ORDER_STORAGE_PREFIX = 'unirhy.recording-order'

export const buildRecordingOrderStorageKey = (
    scope: 'album' | 'work',
    entityId: number,
): string => {
    return `${RECORDING_ORDER_STORAGE_PREFIX}.${scope}.${entityId}`
}

export const moveItemById = <T extends { id: number }>(
    items: readonly T[],
    payload: ReorderPayload,
): T[] => {
    const sourceIndex = items.findIndex((item) => item.id === payload.draggedId)
    const targetIndex = items.findIndex((item) => item.id === payload.targetId)

    if (
        sourceIndex === -1 ||
        targetIndex === -1 ||
        sourceIndex === targetIndex ||
        payload.draggedId === payload.targetId
    ) {
        return [...items]
    }

    const nextItems = [...items]
    const [draggedItem] = nextItems.splice(sourceIndex, 1)

    if (!draggedItem) {
        return [...items]
    }

    const adjustedTargetIndex = nextItems.findIndex((item) => item.id === payload.targetId)
    if (adjustedTargetIndex === -1) {
        return [...items]
    }

    const insertIndex = payload.position === 'after' ? adjustedTargetIndex + 1 : adjustedTargetIndex

    nextItems.splice(insertIndex, 0, draggedItem)
    return nextItems
}

export const applyStoredItemOrder = <T extends { id: number }>(
    items: readonly T[],
    orderedIds: readonly number[],
): T[] => {
    if (items.length <= 1 || orderedIds.length === 0) {
        return [...items]
    }

    const itemById = new Map(items.map((item) => [item.id, item]))
    const seenIds = new Set<number>()
    const orderedItems: T[] = []

    for (const id of orderedIds) {
        const item = itemById.get(id)
        if (!item || seenIds.has(id)) {
            continue
        }
        orderedItems.push(item)
        seenIds.add(id)
    }

    for (const item of items) {
        if (!seenIds.has(item.id)) {
            orderedItems.push(item)
        }
    }

    return orderedItems
}

export const hasSameItemOrder = <T extends { id: number }>(
    left: readonly T[],
    right: readonly T[],
): boolean => {
    return left.length === right.length && left.every((item, index) => item.id === right[index]?.id)
}

export const loadStoredItemOrder = (storageKey: string): number[] => {
    if (typeof window === 'undefined') {
        return []
    }

    const raw = window.localStorage.getItem(storageKey)
    if (!raw) {
        return []
    }

    try {
        const parsed = JSON.parse(raw)
        if (!Array.isArray(parsed)) {
            return []
        }
        return parsed.filter((value): value is number => Number.isInteger(value))
    } catch {
        return []
    }
}

export const saveStoredItemOrder = (storageKey: string, orderedIds: readonly number[]) => {
    if (typeof window === 'undefined') {
        return
    }

    window.localStorage.setItem(storageKey, JSON.stringify(orderedIds))
}
