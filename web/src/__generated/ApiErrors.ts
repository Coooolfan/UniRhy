export type AllErrors = {
        family: 'COMMON', 
        code: 'FORBIDDEN'
    } | {
        family: 'COMMON', 
        code: 'AUTHENTICATION_FAILED'
    } | {
        family: 'PLUGIN', 
        code: 'UNKNOWN_TASK_TYPE', 
        taskType: string
    } | {
        family: 'PLUGIN', 
        code: 'INVALID_TASK_PARAMS'
    } | {
        family: 'SYSTEM', 
        code: 'SYSTEM_ALREADY_INITIALIZED'
    } | {
        family: 'SYSTEM', 
        code: 'SYSTEM_STORAGE_PROVIDER_MUST_BE_SINGLE'
    } | {
        family: 'SYSTEM', 
        code: 'SYSTEM_STORAGE_PROVIDER_CANNOT_BE_READONLY'
    } | {
        family: 'COMMON', 
        code: 'NOT_FOUND'
    } | {
        family: 'SYSTEM', 
        code: 'SYSTEM_STORAGE_PROVIDER_CANNOT_BE_DELETED'
    };
export type ApiErrors = {
    'accountController': {
        'list': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'delete': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'create': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'update': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'updateCredentials': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'COMMON', 
                code: 'AUTHENTICATION_FAILED', 
                readonly [key:string]: any
            })
    }, 
    'albumController': {
        'updateAlbum': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'reorderAlbumRecordings': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            })
    }, 
    'artistController': {
        'createArtist': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'updateArtist': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'mergeArtists': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            })
    }, 
    'mediaFileController': {
    }, 
    'playbackQueueController': {
    }, 
    'playlistController': {
    }, 
    'pluginController': {
        'upload': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'setEnabled': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'delete': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'download': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'submitPluginTask': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'UNKNOWN_TASK_TYPE', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'INVALID_TASK_PARAMS', 
                readonly [key:string]: any
            })
    }, 
    'recordingController': {
        'updateRecording': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'mergeRecording': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            })
    }, 
    'systemConfigController': {
        'get': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'create': AllErrors & ({
                family: 'SYSTEM', 
                code: 'SYSTEM_ALREADY_INITIALIZED', 
                readonly [key:string]: any
            }), 
        'update': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'SYSTEM', 
                code: 'SYSTEM_STORAGE_PROVIDER_MUST_BE_SINGLE', 
                readonly [key:string]: any
            } | {
                family: 'SYSTEM', 
                code: 'SYSTEM_STORAGE_PROVIDER_CANNOT_BE_READONLY', 
                readonly [key:string]: any
            })
    }, 
    'taskController': {
        'executeScanTask': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'executeTranscodeTask': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'resetTaskLogs': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            })
    }, 
    'tokenController': {
        'login': AllErrors & ({
                family: 'COMMON', 
                code: 'AUTHENTICATION_FAILED', 
                readonly [key:string]: any
            })
    }, 
    'workController': {
        'randomWork': AllErrors & ({
                family: 'COMMON', 
                code: 'NOT_FOUND', 
                readonly [key:string]: any
            }), 
        'deleteWork': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'updateWork': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'mergeWork': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            })
    }, 
    'fileSystemStorageController': {
        'list': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'get': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'create': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'update': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'delete': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'SYSTEM', 
                code: 'SYSTEM_STORAGE_PROVIDER_CANNOT_BE_DELETED', 
                readonly [key:string]: any
            })
    }, 
    'ossStorageController': {
        'list': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'get': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'create': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'update': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'delete': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'SYSTEM', 
                code: 'SYSTEM_STORAGE_PROVIDER_CANNOT_BE_DELETED', 
                readonly [key:string]: any
            })
    }
};
