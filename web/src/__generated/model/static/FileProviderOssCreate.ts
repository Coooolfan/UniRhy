export interface FileProviderOssCreate {
    readonly name: string;
    readonly host: string;
    readonly bucket: string;
    readonly accessKey: string;
    readonly secretKey: string;
    readonly parentPath?: string | undefined;
    readonly readonly: boolean;
}
