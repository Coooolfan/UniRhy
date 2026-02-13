export type FileProviderOssDto = {
    'OssStorageController/DEFAULT_OSS_FETCHER': {
        readonly id: number;
        readonly name: string;
        readonly host: string;
        readonly bucket: string;
        readonly accessKey: string;
        readonly parentPath?: string | undefined;
        readonly readonly: boolean;
    }
}
