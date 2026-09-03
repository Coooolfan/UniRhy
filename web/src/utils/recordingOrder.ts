export type ReorderPosition = 'before' | 'after'

export type ReorderPayload = {
    draggedId: number
    targetId: number
    position: ReorderPosition
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

export const hasSameItemOrder = <T extends { id: number }>(
    left: readonly T[],
    right: readonly T[],
): boolean => {
    return left.length === right.length && left.every((item, index) => item.id === right[index]?.id)
}
