export const nowClientMs = () => {
    if (typeof performance !== 'undefined') {
        return performance.timeOrigin + performance.now()
    }
    return Date.now()
}
