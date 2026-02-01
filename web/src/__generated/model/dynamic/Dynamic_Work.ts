import type {Dynamic_Recording} from './';

export interface Dynamic_Work {
    readonly id?: number;
    readonly title?: string;
    readonly recordings?: ReadonlyArray<Dynamic_Recording>;
}
