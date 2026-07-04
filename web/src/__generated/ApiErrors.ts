export type AllErrors = {
        family: 'COMMON', 
        code: 'FORBIDDEN'
    } | {
        family: 'COMMON', 
        code: 'AUTHENTICATION_FAILED'
    } | {
        family: 'SYSTEM', 
        code: 'SYSTEM_ALREADY_INITIALIZED'
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
    }, 
    'artistController': {
    }, 
    'mediaFileController': {
    }, 
    'playbackQueueController': {
    }, 
    'playlistController': {
    }, 
    'pluginController': {
    }, 
    'recordingController': {
    }, 
    'systemConfigController': {
        'create': AllErrors & ({
                family: 'SYSTEM', 
                code: 'SYSTEM_ALREADY_INITIALIZED', 
                readonly [key:string]: any
            })
    }, 
    'taskController': {
    }, 
    'tokenController': {
        'login': AllErrors & ({
                family: 'COMMON', 
                code: 'AUTHENTICATION_FAILED', 
                readonly [key:string]: any
            })
    }, 
    'workController': {
    }, 
    'fileSystemStorageController': {
    }, 
    'ossStorageController': {
    }
};
