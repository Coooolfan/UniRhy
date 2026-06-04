export interface SystemStatus {
    readonly initialized: boolean;
    readonly version?: string | undefined;
    readonly buildTime?: string | undefined;
    readonly gitBranch?: string | undefined;
    readonly gitCommit?: string | undefined;
    readonly gitUrl?: string | undefined;
}
