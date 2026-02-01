export type AllErrors = {
        family: 'COMMON', 
        code: 'FORBIDDEN'
    } | {
        family: 'SYSTEM', 
        code: 'SYSTEM_ALREADY_INITIALIZED'
    } | {
        family: 'COMMON', 
        code: 'AUTHENTICATION_FAILED'
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
        'createFirst': AllErrors & ({
                family: 'SYSTEM', 
                code: 'SYSTEM_ALREADY_INITIALIZED', 
                readonly [key:string]: any
            })
    }, 
    'albumController': {
    }, 
    'mediaFileController': {
    }, 
    'systemConfigController': {
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
