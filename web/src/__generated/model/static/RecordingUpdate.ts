export interface RecordingUpdate {
    readonly label: ReadonlyArray<string>;
    readonly title?: string | undefined;
    readonly comment: string;
    readonly defaultInWork?: boolean | undefined;
}
