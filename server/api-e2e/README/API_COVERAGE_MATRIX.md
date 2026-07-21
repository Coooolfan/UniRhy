# API 覆盖矩阵

> 此文件由 `:api-e2e:generateCoverageMatrix` 自动生成，请勿手改。

- 生成命令：`cd server && ./gradlew :api-e2e:generateCoverageMatrix`
- 校验命令：`cd server && ./gradlew :api-e2e:test`
- 统计口径：按 `HTTP 方法 + Path + headers 条件` 计数（媒体 Range/非Range 分开）。
- 当前接口总数：`83`

| # | 覆盖级别 | HTTP | Path | 条件 | 需登录 | Controller#method | 用例引用 | 备注 |
|---|---|---|---|---|---|---|---|---|
| 1 | FULL | GET | /api/accounts | - | N | AccountController#list | com.unirhy.e2e.AccountPlaylistContentE2eTest#accounts should support create list me update delete with permission boundary | - |
| 2 | FULL | POST | /api/accounts | - | N | AccountController#create | com.unirhy.e2e.AccountPlaylistContentE2eTest#accounts should support create list me update delete with permission boundary | - |
| 3 | FULL | GET | /api/accounts/me | - | Y | AccountController#me | com.unirhy.e2e.AccountPlaylistContentE2eTest#accounts should support create list me update delete with permission boundary | - |
| 4 | FULL | DELETE | /api/accounts/{id} | - | N | AccountController#delete | com.unirhy.e2e.AccountPlaylistContentE2eTest#accounts should support create list me update delete with permission boundary | - |
| 5 | FULL | PUT | /api/accounts/{id} | - | Y | AccountController#update | com.unirhy.e2e.AccountPlaylistContentE2eTest#accounts should support create list me update delete with permission boundary | permission: non-admin cannot update admin |
| 6 | TODO | PUT | /api/accounts/{id}/credentials | - | Y | AccountController#updateCredentials | - | - |
| 7 | FULL | GET | /api/albums | - | Y | AlbumController#listAlbums | com.unirhy.e2e.TaskContentReadE2eTest#works and albums should support read random and delete flow | - |
| 8 | FULL | GET | /api/albums/search-results | - | Y | AlbumController#getAlbumByName | com.unirhy.e2e.AccountPlaylistContentE2eTest#content endpoints should support search update recording update and merge flow | validation: unknown keyword returns empty array |
| 9 | FULL | GET | /api/albums/{id} | - | Y | AlbumController#getAlbum | com.unirhy.e2e.TaskContentReadE2eTest#works and albums should support read random and delete flow | - |
| 10 | FULL | PUT | /api/albums/{id} | - | Y | AlbumController#updateAlbum | com.unirhy.e2e.AccountPlaylistContentE2eTest#album update should modify scalar fields and return updated detail | - |
| 11 | FULL | PUT | /api/albums/{id}/recording-order | - | Y | AlbumController#reorderAlbumRecordings | com.unirhy.e2e.AccountPlaylistContentE2eTest#album reorder should update sort order and validate input | validation: duplicate missing extra recording ids and unknown album fail |
| 12 | FULL | GET | /api/artists | - | Y | ArtistController#listArtists | com.unirhy.e2e.AccountPlaylistContentE2eTest#artists should support list search create update and merge flow | validation: malformed page index returns 400 |
| 13 | FULL | POST | /api/artists | - | Y | ArtistController#createArtist | com.unirhy.e2e.AccountPlaylistContentE2eTest#artists should support list search create update and merge flow | validation: missing display name returns 400 |
| 14 | FULL | POST | /api/artists/merge-requests | - | Y | ArtistController#mergeArtists | com.unirhy.e2e.AccountPlaylistContentE2eTest#artists should support list search create update and merge flow | error: unknown target returns 404 |
| 15 | FULL | GET | /api/artists/search-results | - | Y | ArtistController#getArtistByName | com.unirhy.e2e.AccountPlaylistContentE2eTest#artists should support list search create update and merge flow | validation: unknown keyword returns empty array |
| 16 | FULL | PUT | /api/artists/{id} | - | Y | ArtistController#updateArtist | com.unirhy.e2e.AccountPlaylistContentE2eTest#artists should support list search create update and merge flow | validation: malformed id returns 400 |
| 17 | FULL | GET | /api/media-files/{id} | !Range | N | MediaFileController#getMedia | com.unirhy.e2e.TaskContentReadE2eTest#media endpoint should support full range head and error branches | error: unknown id returns 404 |
| 18 | FULL | GET | /api/media-files/{id} | Range | N | MediaFileController#getMediaWithRange | com.unirhy.e2e.TaskContentReadE2eTest#media endpoint should support full range head and error branches | error: invalid range returns 416 |
| 19 | FULL | HEAD | /api/media-files/{id} | !Range | N | MediaFileController#headMedia | com.unirhy.e2e.TaskContentReadE2eTest#media endpoint should support full range head and error branches | - |
| 20 | FULL | GET | /api/playback-queues/current | - | Y | PlaybackQueueController#getCurrentQueue | com.unirhy.e2e.PlaybackQueueE2eTest#playback queue should support full mutation flow and stable conflict branches | - |
| 21 | FULL | PUT | /api/playback-queues/current | - | Y | PlaybackQueueController#replaceCurrentQueue | com.unirhy.e2e.PlaybackQueueE2eTest#playback queue should support full mutation flow and stable conflict branches | conflict: stale version and unknown recording return 409 |
| 22 | FULL | POST | /api/playback-queues/current/clear-requests | - | Y | PlaybackQueueController#clearCurrentQueue | com.unirhy.e2e.PlaybackQueueE2eTest#playback queue should support full mutation flow and stable conflict branches | - |
| 23 | FULL | PUT | /api/playback-queues/current/current-index | - | Y | PlaybackQueueController#setCurrentIndex | com.unirhy.e2e.PlaybackQueueE2eTest#playback queue should support full mutation flow and stable conflict branches | - |
| 24 | FULL | PUT | /api/playback-queues/current/item-order | - | Y | PlaybackQueueController#reorderCurrentQueue | com.unirhy.e2e.PlaybackQueueE2eTest#playback queue should support full mutation flow and stable conflict branches | - |
| 25 | FULL | POST | /api/playback-queues/current/item-removals | - | Y | PlaybackQueueController#removeCurrentQueueEntry | com.unirhy.e2e.PlaybackQueueE2eTest#playback queue should support full mutation flow and stable conflict branches | - |
| 26 | FULL | POST | /api/playback-queues/current/items | - | Y | PlaybackQueueController#appendToCurrentQueue | com.unirhy.e2e.PlaybackQueueE2eTest#playback queue should support full mutation flow and stable conflict branches | conflict: stale version returns 409 |
| 27 | FULL | POST | /api/playback-queues/current/next-navigation-requests | - | Y | PlaybackQueueController#playNextInCurrentQueue | com.unirhy.e2e.PlaybackQueueE2eTest#playback queue should support full mutation flow and stable conflict branches | - |
| 28 | FULL | POST | /api/playback-queues/current/previous-navigation-requests | - | Y | PlaybackQueueController#playPreviousInCurrentQueue | com.unirhy.e2e.PlaybackQueueE2eTest#playback queue should support full mutation flow and stable conflict branches | - |
| 29 | FULL | PUT | /api/playback-queues/current/strategies | - | Y | PlaybackQueueController#updateCurrentQueueStrategy | com.unirhy.e2e.PlaybackQueueE2eTest#playback queue should support full mutation flow and stable conflict branches | - |
| 30 | FULL | GET | /api/playlists | - | Y | PlaylistController#listPlaylists | com.unirhy.e2e.AccountPlaylistContentE2eTest#playlists should support owner scoped crud and recording association | - |
| 31 | FULL | POST | /api/playlists | - | Y | PlaylistController#createPlaylist | com.unirhy.e2e.AccountPlaylistContentE2eTest#playlists should support owner scoped crud and recording association | - |
| 32 | FULL | DELETE | /api/playlists/{id} | - | Y | PlaylistController#deletePlaylist | com.unirhy.e2e.AccountPlaylistContentE2eTest#playlists should support owner scoped crud and recording association | ownership: non-owner returns 404 |
| 33 | FULL | GET | /api/playlists/{id} | - | Y | PlaylistController#getPlaylist | com.unirhy.e2e.AccountPlaylistContentE2eTest#playlists should support owner scoped crud and recording association | ownership: non-owner returns 404 |
| 34 | FULL | PUT | /api/playlists/{id} | - | Y | PlaylistController#updatePlaylist | com.unirhy.e2e.AccountPlaylistContentE2eTest#playlists should support owner scoped crud and recording association | ownership: non-owner returns 404 |
| 35 | FULL | PUT | /api/playlists/{id}/recording-order | - | Y | PlaylistController#reorderPlaylistRecordings | com.unirhy.e2e.AccountPlaylistContentE2eTest#playlist reorder should update sort order and validate input | ownership and validation branches covered |
| 36 | FULL | DELETE | /api/playlists/{id}/recordings/{recordingId} | - | Y | PlaylistController#removeRecordingFromPlaylist | com.unirhy.e2e.AccountPlaylistContentE2eTest#playlists should support owner scoped crud and recording association | idempotent remove; ownership: non-owner returns 404 |
| 37 | FULL | PUT | /api/playlists/{id}/recordings/{recordingId} | - | Y | PlaylistController#addRecordingToPlaylist | com.unirhy.e2e.AccountPlaylistContentE2eTest#playlists should support owner scoped crud and recording association | idempotent add; ownership: non-owner returns 404 |
| 38 | FULL | GET | /api/plugins | - | Y | PluginController#listPlugins | com.unirhy.e2e.PluginE2eTest#plugin lifecycle should support upload list download enable submit disable and delete | - |
| 39 | FULL | POST | /api/plugins | - | Y | PluginController#upload | com.unirhy.e2e.PluginE2eTest#plugin lifecycle should support upload list download enable submit disable and delete | validation: com.unirhy.e2e.PluginE2eTest#plugin upload should reject invalid archives |
| 40 | FULL | DELETE | /api/plugins/{id} | - | Y | PluginController#delete | com.unirhy.e2e.PluginE2eTest#plugin lifecycle should support upload list download enable submit disable and delete | error: missing plugin returns 404 |
| 41 | FULL | PUT | /api/plugins/{id}/concurrency | - | Y | PluginController#updateConcurrency | com.unirhy.e2e.PluginE2eTest#plugin lifecycle should support upload list download enable submit disable and delete | validation: non-positive concurrency returns 400 |
| 42 | FULL | PUT | /api/plugins/{id}/enabled-state | - | Y | PluginController#setEnabled | com.unirhy.e2e.PluginE2eTest#plugin lifecycle should support upload list download enable submit disable and delete | error: missing plugin returns 404 |
| 43 | FULL | GET | /api/plugins/{id}/package | - | Y | PluginController#download | com.unirhy.e2e.PluginE2eTest#plugin lifecycle should support upload list download enable submit disable and delete | error: deleted plugin returns 404 |
| 44 | FULL | POST | /api/recordings/merge-requests | - | Y | RecordingController#mergeRecording | com.unirhy.e2e.AccountPlaylistContentE2eTest#content endpoints should support search update recording update and merge flow | merge: source recording removed and relations moved to target |
| 45 | FULL | GET | /api/recordings/{id} | - | Y | RecordingController#getRecording | com.unirhy.e2e.AccountPlaylistContentE2eTest#content endpoints should support search update recording update and merge flow | missing resource returns 404 |
| 46 | FULL | PUT | /api/recordings/{id} | - | Y | RecordingController#updateRecording | com.unirhy.e2e.AccountPlaylistContentE2eTest#content endpoints should support search update recording update and merge flow | - |
| 47 | FULL | GET | /api/storage/file-system-nodes | - | Y | FileSystemStorageController#list | com.unirhy.e2e.StorageConfigE2eTest#file system storage should support create get update list delete flow | - |
| 48 | FULL | POST | /api/storage/file-system-nodes | - | Y | FileSystemStorageController#create | com.unirhy.e2e.StorageConfigE2eTest#file system storage should support create get update list delete flow | - |
| 49 | FULL | DELETE | /api/storage/file-system-nodes/{id} | - | Y | FileSystemStorageController#delete | com.unirhy.e2e.StorageConfigE2eTest#file system storage should support create get update list delete flow | linkage: com.unirhy.e2e.StorageConfigE2eTest#system config should enforce storage linkage constraints |
| 50 | FULL | GET | /api/storage/file-system-nodes/{id} | - | Y | FileSystemStorageController#get | com.unirhy.e2e.StorageConfigE2eTest#file system storage should support create get update list delete flow | - |
| 51 | FULL | PUT | /api/storage/file-system-nodes/{id} | - | Y | FileSystemStorageController#update | com.unirhy.e2e.StorageConfigE2eTest#file system storage should support create get update list delete flow | - |
| 52 | FULL | GET | /api/storage/oss-nodes | - | Y | OssStorageController#list | com.unirhy.e2e.StorageConfigE2eTest#oss storage should support create get update list delete flow | - |
| 53 | FULL | POST | /api/storage/oss-nodes | - | Y | OssStorageController#create | com.unirhy.e2e.StorageConfigE2eTest#oss storage should support create get update list delete flow | - |
| 54 | FULL | DELETE | /api/storage/oss-nodes/{id} | - | Y | OssStorageController#delete | com.unirhy.e2e.StorageConfigE2eTest#oss storage should support create get update list delete flow | - |
| 55 | FULL | GET | /api/storage/oss-nodes/{id} | - | Y | OssStorageController#get | com.unirhy.e2e.StorageConfigE2eTest#oss storage should support create get update list delete flow | - |
| 56 | FULL | PUT | /api/storage/oss-nodes/{id} | - | Y | OssStorageController#update | com.unirhy.e2e.StorageConfigE2eTest#oss storage should support create get update list delete flow | - |
| 57 | FULL | GET | /api/system-config | - | Y | SystemConfigController#get | com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow | - |
| 58 | FULL | POST | /api/system-config | - | N | SystemConfigController#create | com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow | - |
| 59 | FULL | PUT | /api/system-config | - | N | SystemConfigController#update | com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow | - |
| 60 | FULL | GET | /api/system-config/status | - | N | SystemConfigController#isInitialized | com.unirhy.e2e.SystemAuthE2eTest#status and protected endpoints require authentication | - |
| 61 | TODO | GET | /api/task-definitions | - | Y | TaskDefinitionController#listTaskDefinitions | - | - |
| 62 | TODO | GET | /api/task-definitions/{namespace}/{taskType} | - | Y | TaskDefinitionController#getTaskDefinition | - | - |
| 63 | FULL | GET | /api/task-statistics | - | Y | TaskStatisticsController#getTaskStatistics | com.unirhy.e2e.TaskContentReadE2eTest#scan submission should report metadata parse stats and accept incremental duplicate submission | stats: per task key submission/task status counts, transcode drain covered by com.unirhy.e2e.TaskContentReadE2eTest#transcode task should complete successfully and write opus files |
| 64 | TODO | GET | /api/task-submissions | - | Y | TaskSubmissionController#listSubmissions | - | - |
| 65 | TODO | PATCH | /api/task-submissions | - | Y | TaskSubmissionController#patchSubmissions | - | - |
| 66 | FULL | POST | /api/task-submissions | - | Y | TaskSubmissionController#createSubmission | com.unirhy.e2e.TaskContentReadE2eTest#scan submission should report metadata parse stats and accept incremental duplicate submission | duplicate: repeated submission returns 202 and active payload dedup keeps task count stable; plugin: com.unirhy.e2e.PluginE2eTest#plugin lifecycle should support upload list download enable submit disable and delete covers plugin task keys, disabled plugin returns 409, unknown key returns 404, invalid key returns 400 |
| 67 | TODO | DELETE | /api/task-submissions/{id} | - | Y | TaskSubmissionController#deleteSubmission | - | - |
| 68 | FULL | GET | /api/task-submissions/{id} | - | Y | TaskSubmissionController#getSubmission | com.unirhy.e2e.PluginE2eTest#plugin lifecycle should support upload list download enable submit disable and delete | polling: submission reaches COMPLETED after async planning |
| 69 | TODO | PATCH | /api/task-submissions/{id} | - | Y | TaskSubmissionController#patchSubmission | - | - |
| 70 | TODO | GET | /api/task-submissions/{id}/tasks | - | Y | TaskSubmissionController#listSubmissionTasks | - | - |
| 71 | TODO | GET | /api/tasks | - | Y | TaskController#listTasks | - | - |
| 72 | TODO | PATCH | /api/tasks | - | Y | TaskController#patchTasks | - | - |
| 73 | TODO | GET | /api/tasks/{id} | - | Y | TaskController#getTask | - | - |
| 74 | TODO | PATCH | /api/tasks/{id} | - | Y | TaskController#patchTask | - | - |
| 75 | FULL | POST | /api/tokens | - | N | TokenController#login | com.unirhy.e2e.SystemAuthE2eTest#duplicate init and wrong login return stable business errors | - |
| 76 | FULL | DELETE | /api/tokens/current | - | N | TokenController#logout | com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow | - |
| 77 | FULL | GET | /api/works | - | Y | WorkController#listWork | com.unirhy.e2e.TaskContentReadE2eTest#works and albums should support read random and delete flow | - |
| 78 | FULL | POST | /api/works/merge-requests | - | Y | WorkController#mergeWork | com.unirhy.e2e.AccountPlaylistContentE2eTest#content endpoints should support search update recording update and merge flow | merge: source work removed and recordings moved to target |
| 79 | FULL | GET | /api/works/random-selection | - | Y | WorkController#randomWork | com.unirhy.e2e.TaskContentReadE2eTest#works and albums should support read random and delete flow | validation: length<=0 returns 400 |
| 80 | FULL | GET | /api/works/search-results | - | Y | WorkController#getWorkByName | com.unirhy.e2e.AccountPlaylistContentE2eTest#content endpoints should support search update recording update and merge flow | validation: unknown keyword returns empty array |
| 81 | FULL | DELETE | /api/works/{id} | - | Y | WorkController#deleteWork | com.unirhy.e2e.TaskContentReadE2eTest#works and albums should support read random and delete flow | - |
| 82 | FULL | GET | /api/works/{id} | - | Y | WorkController#getWorkById | com.unirhy.e2e.TaskContentReadE2eTest#works and albums should support read random and delete flow | - |
| 83 | FULL | PUT | /api/works/{id} | - | Y | WorkController#updateWork | com.unirhy.e2e.AccountPlaylistContentE2eTest#content endpoints should support search update recording update and merge flow | - |
