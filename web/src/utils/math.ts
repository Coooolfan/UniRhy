export const average = (values: readonly number[]) => {
    if (values.length === 0) {
        return null
    }

    let total = 0
    for (const value of values) {
        total += value
    }
    return total / values.length
}
