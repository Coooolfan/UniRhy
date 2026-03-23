/**
 * pgvector 向量的包装类型，用于绕过 Jimmer 对数组类型的限制
 */
export interface Embedding {
    readonly values: ReadonlyArray<number>;
}
