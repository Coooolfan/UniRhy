export type WorkDto = {
    'WorkController/DEFAULT_WORK_FETCHER': {
        readonly id: number;
        readonly title: string;
        readonly recordings: ReadonlyArray<{
            readonly id: number;
            readonly kind: string;
            readonly label?: string | undefined;
            readonly title?: string | undefined;
            readonly comment: string;
            readonly assets: ReadonlyArray<{
                readonly id: number;
                readonly comment: string;
                readonly mediaFile: {
                    readonly id: number;
                };
            }>;
            readonly artists: ReadonlyArray<{
                readonly id: number;
            }>;
            readonly cover?: {
                readonly id: number;
            } | undefined;
        }>;
    }
}
