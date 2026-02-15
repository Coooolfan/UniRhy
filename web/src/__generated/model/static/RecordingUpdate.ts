export interface RecordingUpdate {
    readonly kind: string;
    readonly label?: string | undefined;
    readonly title?: string | undefined;
    readonly comment: string;
    readonly defaultInWork: boolean;
}
