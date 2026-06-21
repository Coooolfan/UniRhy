package com.coooolfan.unirhy.service.storage

import com.coooolfan.unirhy.model.MediaFile
import com.coooolfan.unirhy.model.MediaFileDraft
import com.coooolfan.unirhy.model.SystemConfig
import com.coooolfan.unirhy.model.storage.FileProviderType

fun MediaFile.resolveStorageNode(storageObjects: StorageNodeObjectService): StorageNode {
    fsProvider?.let { return storageObjects.resolve(FileProviderType.FILE_SYSTEM, it.id) }
    ossProvider?.let { return storageObjects.resolve(FileProviderType.OSS, it.id) }
    error("Media file has no storage provider")
}

fun SystemConfig.resolveWriteableStorageNode(storageObjects: StorageNodeObjectService): StorageNode {
    fsProvider?.let { return storageObjects.resolve(FileProviderType.FILE_SYSTEM, it.id) }
    ossProvider?.let { return storageObjects.resolve(FileProviderType.OSS, it.id) }
    error("System storage provider is not configured")
}

fun MediaFileDraft.bindProvider(provider: StorageNode) {
    when (provider) {
        is FileSystemStorageNode -> {
            ossProvider = null
            fsProvider = provider.provider
        }

        is OssStorageNode -> {
            ossProvider = provider.provider
            fsProvider = null
        }
    }
}
