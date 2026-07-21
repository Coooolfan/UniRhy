export type AllErrors = {
        family: 'COMMON', 
        code: 'FORBIDDEN'
    } | {
        family: 'COMMON', 
        code: 'AUTHENTICATION_FAILED'
    } | {
        family: 'ACCOUNT', 
        code: 'CREDENTIAL_UPDATE_REQUIRED'
    } | {
        family: 'ALBUM', 
        code: 'NOT_FOUND'
    } | {
        family: 'ALBUM', 
        code: 'RECORDING_IDS_CONTAIN_DUPLICATES'
    } | {
        family: 'ALBUM', 
        code: 'RECORDING_IDS_MISMATCH'
    } | {
        family: 'ARTIST', 
        code: 'TARGET_NOT_FOUND'
    } | {
        family: 'ARTIST', 
        code: 'SOURCE_NOT_FOUND'
    } | {
        family: 'PLAYBACK_QUEUE', 
        code: 'EMPTY_QUEUE_INDEX_INVALID'
    } | {
        family: 'PLAYBACK_QUEUE', 
        code: 'CURRENT_INDEX_OUT_OF_RANGE'
    } | {
        family: 'PLAYBACK_QUEUE', 
        code: 'RECORDING_NOT_FOUND', 
        recordingId: number
    } | {
        family: 'PLAYBACK_QUEUE', 
        code: 'RECORDING_NOT_PLAYABLE', 
        recordingId: number
    } | {
        family: 'PLAYBACK_QUEUE', 
        code: 'VERSION_CONFLICT'
    } | {
        family: 'PLAYBACK_QUEUE', 
        code: 'RECORDING_IDS_EMPTY'
    } | {
        family: 'PLAYBACK_QUEUE', 
        code: 'INDEX_NOT_FOUND'
    } | {
        family: 'PLAYLIST', 
        code: 'NOT_FOUND'
    } | {
        family: 'PLAYLIST', 
        code: 'RECORDING_IDS_CONTAIN_DUPLICATES'
    } | {
        family: 'PLAYLIST', 
        code: 'RECORDING_IDS_MISMATCH'
    } | {
        family: 'PLUGIN', 
        code: 'PACKAGE_TOO_LARGE'
    } | {
        family: 'PLUGIN', 
        code: 'WASM_TOO_LARGE'
    } | {
        family: 'PLUGIN', 
        code: 'MANIFEST_MISSING'
    } | {
        family: 'PLUGIN', 
        code: 'WASM_MISSING'
    } | {
        family: 'PLUGIN', 
        code: 'INVALID_MANIFEST', 
        reason: string
    } | {
        family: 'PLUGIN', 
        code: 'UNSUPPORTED_RUNTIME'
    } | {
        family: 'PLUGIN', 
        code: 'UNSUPPORTED_ABI'
    } | {
        family: 'PLUGIN', 
        code: 'NOT_FOUND'
    } | {
        family: 'PLUGIN', 
        code: 'LOAD_FAILED', 
        reason: string
    } | {
        family: 'PLUGIN', 
        code: 'INVALID_CONCURRENCY'
    } | {
        family: 'PLUGIN', 
        code: 'DELETE_CONFLICT'
    } | {
        family: 'RECORDING', 
        code: 'NOT_FOUND'
    } | {
        family: 'RECORDING', 
        code: 'TARGET_NOT_FOUND'
    } | {
        family: 'RECORDING', 
        code: 'WORK_MISMATCH'
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
        family: 'TASK', 
        code: 'TASK_NOT_FOUND'
    } | {
        family: 'TASK', 
        code: 'STATUS_CONFLICT'
    } | {
        family: 'TASK', 
        code: 'PLUGIN_UNAVAILABLE'
    } | {
        family: 'TASK', 
        code: 'DEFINITION_NOT_FOUND'
    } | {
        family: 'TASK', 
        code: 'INVALID_TASK_KEY', 
        reason: string
    } | {
        family: 'TASK', 
        code: 'INVALID_PARAMS', 
        reason: string
    } | {
        family: 'TASK', 
        code: 'SUBMISSION_NOT_FOUND'
    } | {
        family: 'TASK', 
        code: 'DELETE_CONFLICT'
    } | {
        family: 'COMMON', 
        code: 'NOT_FOUND'
    } | {
        family: 'WORK', 
        code: 'INVALID_RANDOM_LENGTH'
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
            } | {
                family: 'ACCOUNT', 
                code: 'CREDENTIAL_UPDATE_REQUIRED', 
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
            } | {
                family: 'ALBUM', 
                code: 'NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'ALBUM', 
                code: 'RECORDING_IDS_CONTAIN_DUPLICATES', 
                readonly [key:string]: any
            } | {
                family: 'ALBUM', 
                code: 'RECORDING_IDS_MISMATCH', 
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
            } | {
                family: 'ARTIST', 
                code: 'TARGET_NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'ARTIST', 
                code: 'SOURCE_NOT_FOUND', 
                readonly [key:string]: any
            })
    }, 
    'mediaFileController': {
    }, 
    'playbackQueueController': {
        'replaceCurrentQueue': AllErrors & ({
                family: 'PLAYBACK_QUEUE', 
                code: 'EMPTY_QUEUE_INDEX_INVALID', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'CURRENT_INDEX_OUT_OF_RANGE', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'RECORDING_NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'RECORDING_NOT_PLAYABLE', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'VERSION_CONFLICT', 
                readonly [key:string]: any
            }), 
        'appendToCurrentQueue': AllErrors & ({
                family: 'PLAYBACK_QUEUE', 
                code: 'RECORDING_IDS_EMPTY', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'RECORDING_NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'RECORDING_NOT_PLAYABLE', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'VERSION_CONFLICT', 
                readonly [key:string]: any
            }), 
        'reorderCurrentQueue': AllErrors & ({
                family: 'PLAYBACK_QUEUE', 
                code: 'EMPTY_QUEUE_INDEX_INVALID', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'CURRENT_INDEX_OUT_OF_RANGE', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'RECORDING_NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'RECORDING_NOT_PLAYABLE', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'VERSION_CONFLICT', 
                readonly [key:string]: any
            }), 
        'setCurrentIndex': AllErrors & ({
                family: 'PLAYBACK_QUEUE', 
                code: 'CURRENT_INDEX_OUT_OF_RANGE', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'RECORDING_NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'RECORDING_NOT_PLAYABLE', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'VERSION_CONFLICT', 
                readonly [key:string]: any
            }), 
        'updateCurrentQueueStrategy': AllErrors & ({
                family: 'PLAYBACK_QUEUE', 
                code: 'VERSION_CONFLICT', 
                readonly [key:string]: any
            }), 
        'playNextInCurrentQueue': AllErrors & ({
                family: 'PLAYBACK_QUEUE', 
                code: 'VERSION_CONFLICT', 
                readonly [key:string]: any
            }), 
        'playPreviousInCurrentQueue': AllErrors & ({
                family: 'PLAYBACK_QUEUE', 
                code: 'VERSION_CONFLICT', 
                readonly [key:string]: any
            }), 
        'removeCurrentQueueEntry': AllErrors & ({
                family: 'PLAYBACK_QUEUE', 
                code: 'INDEX_NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'RECORDING_NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'RECORDING_NOT_PLAYABLE', 
                readonly [key:string]: any
            } | {
                family: 'PLAYBACK_QUEUE', 
                code: 'VERSION_CONFLICT', 
                readonly [key:string]: any
            }), 
        'clearCurrentQueue': AllErrors & ({
                family: 'PLAYBACK_QUEUE', 
                code: 'VERSION_CONFLICT', 
                readonly [key:string]: any
            })
    }, 
    'playlistController': {
        'getPlaylist': AllErrors & ({
                family: 'PLAYLIST', 
                code: 'NOT_FOUND', 
                readonly [key:string]: any
            }), 
        'updatePlaylist': AllErrors & ({
                family: 'PLAYLIST', 
                code: 'NOT_FOUND', 
                readonly [key:string]: any
            }), 
        'deletePlaylist': AllErrors & ({
                family: 'PLAYLIST', 
                code: 'NOT_FOUND', 
                readonly [key:string]: any
            }), 
        'addRecordingToPlaylist': AllErrors & ({
                family: 'PLAYLIST', 
                code: 'NOT_FOUND', 
                readonly [key:string]: any
            }), 
        'removeRecordingFromPlaylist': AllErrors & ({
                family: 'PLAYLIST', 
                code: 'NOT_FOUND', 
                readonly [key:string]: any
            }), 
        'reorderPlaylistRecordings': AllErrors & ({
                family: 'PLAYLIST', 
                code: 'NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'PLAYLIST', 
                code: 'RECORDING_IDS_CONTAIN_DUPLICATES', 
                readonly [key:string]: any
            } | {
                family: 'PLAYLIST', 
                code: 'RECORDING_IDS_MISMATCH', 
                readonly [key:string]: any
            })
    }, 
    'pluginController': {
        'upload': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'PACKAGE_TOO_LARGE', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'WASM_TOO_LARGE', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'MANIFEST_MISSING', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'WASM_MISSING', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'INVALID_MANIFEST', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'UNSUPPORTED_RUNTIME', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'UNSUPPORTED_ABI', 
                readonly [key:string]: any
            }), 
        'setEnabled': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'LOAD_FAILED', 
                readonly [key:string]: any
            }), 
        'updateConcurrency': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'INVALID_CONCURRENCY', 
                readonly [key:string]: any
            }), 
        'delete': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'DELETE_CONFLICT', 
                readonly [key:string]: any
            }), 
        'download': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'PLUGIN', 
                code: 'NOT_FOUND', 
                readonly [key:string]: any
            })
    }, 
    'recordingController': {
        'getRecording': AllErrors & ({
                family: 'RECORDING', 
                code: 'NOT_FOUND', 
                readonly [key:string]: any
            }), 
        'updateRecording': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            }), 
        'mergeRecording': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'RECORDING', 
                code: 'TARGET_NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'RECORDING', 
                code: 'WORK_MISMATCH', 
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
        'getTask': AllErrors & ({
                family: 'TASK', 
                code: 'TASK_NOT_FOUND', 
                readonly [key:string]: any
            }), 
        'patchTask': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'TASK_NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'STATUS_CONFLICT', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'PLUGIN_UNAVAILABLE', 
                readonly [key:string]: any
            }), 
        'patchTasks': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'STATUS_CONFLICT', 
                readonly [key:string]: any
            })
    }, 
    'taskDefinitionController': {
        'getTaskDefinition': AllErrors & ({
                family: 'TASK', 
                code: 'DEFINITION_NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'INVALID_TASK_KEY', 
                readonly [key:string]: any
            })
    }, 
    'taskStatisticsController': {
        'getTaskStatistics': AllErrors & ({
                family: 'TASK', 
                code: 'INVALID_TASK_KEY', 
                readonly [key:string]: any
            })
    }, 
    'taskSubmissionController': {
        'createSubmission': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'INVALID_TASK_KEY', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'INVALID_PARAMS', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'DEFINITION_NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'PLUGIN_UNAVAILABLE', 
                readonly [key:string]: any
            }), 
        'getSubmission': AllErrors & ({
                family: 'TASK', 
                code: 'SUBMISSION_NOT_FOUND', 
                readonly [key:string]: any
            }), 
        'listSubmissionTasks': AllErrors & ({
                family: 'TASK', 
                code: 'SUBMISSION_NOT_FOUND', 
                readonly [key:string]: any
            }), 
        'patchSubmission': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'SUBMISSION_NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'STATUS_CONFLICT', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'PLUGIN_UNAVAILABLE', 
                readonly [key:string]: any
            }), 
        'patchSubmissions': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'STATUS_CONFLICT', 
                readonly [key:string]: any
            }), 
        'deleteSubmission': AllErrors & ({
                family: 'COMMON', 
                code: 'FORBIDDEN', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'SUBMISSION_NOT_FOUND', 
                readonly [key:string]: any
            } | {
                family: 'TASK', 
                code: 'DELETE_CONFLICT', 
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
            } | {
                family: 'WORK', 
                code: 'INVALID_RANDOM_LENGTH', 
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
