import { executeApiRequest } from '@/ApiInstance'
import type { TaskStatus } from '@/__generated/model/enums/TaskStatus'

export type TaskStatusTransitionRequest = {
    namespace: string
    taskType: string
    sourceStatuses: TaskStatus[]
    targetStatus: TaskStatus
}

export type TaskStatusTransitionResponse = {
    transitioned: number
}

const isTransitionResponse = (value: unknown): value is TaskStatusTransitionResponse =>
    typeof value === 'object' &&
    value !== null &&
    'transitioned' in value &&
    typeof value.transitioned === 'number'

export const transitionTaskStatuses = async (
    request: TaskStatusTransitionRequest,
): Promise<TaskStatusTransitionResponse> => {
    const response: unknown = await executeApiRequest({
        uri: '/api/tasks/status-transitions',
        method: 'POST',
        body: request,
    })
    if (!isTransitionResponse(response)) {
        throw new Error('Invalid task status transition response')
    }
    return response
}
