let lockedBodyOverflow = ''
let bodyScrollLockCount = 0

export const lockBodyScroll = () => {
    if (typeof document === 'undefined') {
        return
    }

    if (bodyScrollLockCount === 0) {
        lockedBodyOverflow = document.body.style.overflow
        document.body.style.overflow = 'hidden'
    }

    bodyScrollLockCount += 1
}

export const unlockBodyScroll = () => {
    if (typeof document === 'undefined' || bodyScrollLockCount === 0) {
        return
    }

    bodyScrollLockCount -= 1
    if (bodyScrollLockCount === 0) {
        document.body.style.overflow = lockedBodyOverflow
    }
}
