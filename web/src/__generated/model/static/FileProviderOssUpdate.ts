export interface FileProviderOssUpdate {
    readonly name?: string | undefined;
    readonly host?: string | undefined;
    readonly bucket?: string | undefined;
    readonly accessKey?: string | undefined;
    readonly secretKey?: string | undefined;
    readonly parentPath?: string | undefined;
    readonly readonly: boolean;
}
