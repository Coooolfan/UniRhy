# API 覆盖矩阵

> 此文件由 `:api-e2e:generateCoverageMatrix` 自动生成，请勿手改。

- 生成命令：`cd server && ./gradlew :api-e2e:generateCoverageMatrix`
- 校验命令：`cd server && ./gradlew :api-e2e:test`
- 统计口径：按 `HTTP 方法 + Path + headers 条件` 计数（媒体 Range/非Range 分开）。
- 当前接口总数：`39`

| # | 覆盖级别 | HTTP | Path | 条件 | 需登录 | Controller#method | 用例引用 | 备注 |
|---|---|---|---|---|---|---|---|---|
| 1 | TODO | GET | /api/accounts | - | Y | AccountController#list | - | - |
| 2 | TODO | POST | /api/accounts | - | Y | AccountController#create | - | - |
| 3 | TODO | GET | /api/accounts/me | - | Y | AccountController#me | - | - |
| 4 | TODO | DELETE | /api/accounts/{id} | - | Y | AccountController#delete | - | - |
| 5 | TODO | PUT | /api/accounts/{id} | - | Y | AccountController#update | - | - |
| 6 | FULL | GET | /api/albums | - | Y | AlbumController#listAlbums | com.unirhy.e2e.TaskContentReadE2eTest#works and albums should support read random and delete flow | auth: com.unirhy.e2e.TaskContentReadE2eTest#task and content endpoints should reject unauthenticated access |
| 7 | FULL | GET | /api/albums/{id} | - | Y | AlbumController#getAlbum | com.unirhy.e2e.TaskContentReadE2eTest#works and albums should support read random and delete flow | auth: com.unirhy.e2e.TaskContentReadE2eTest#task and content endpoints should reject unauthenticated access |
| 8 | FULL | GET | /api/media/{id} | !Range | Y | MediaFileController#getMedia | com.unirhy.e2e.TaskContentReadE2eTest#media endpoint should support full range head and error branches | auth: com.unirhy.e2e.TaskContentReadE2eTest#task and content endpoints should reject unauthenticated access; error: unknown id returns 404 |
| 9 | FULL | GET | /api/media/{id} | Range | Y | MediaFileController#getMediaWithRange | com.unirhy.e2e.TaskContentReadE2eTest#media endpoint should support full range head and error branches | auth: com.unirhy.e2e.TaskContentReadE2eTest#task and content endpoints should reject unauthenticated access; error: invalid range returns 416 |
| 10 | FULL | HEAD | /api/media/{id} | !Range | Y | MediaFileController#headMedia | com.unirhy.e2e.TaskContentReadE2eTest#media endpoint should support full range head and error branches | auth: com.unirhy.e2e.TaskContentReadE2eTest#task and content endpoints should reject unauthenticated access |
| 11 | TODO | GET | /api/playlists | - | Y | PlaylistController#listPlaylists | - | - |
| 12 | TODO | POST | /api/playlists | - | Y | PlaylistController#createPlaylist | - | - |
| 13 | TODO | DELETE | /api/playlists/{id} | - | Y | PlaylistController#deletePlaylist | - | - |
| 14 | TODO | GET | /api/playlists/{id} | - | Y | PlaylistController#getPlaylist | - | - |
| 15 | TODO | PUT | /api/playlists/{id} | - | Y | PlaylistController#updatePlaylist | - | - |
| 16 | TODO | DELETE | /api/playlists/{id}/recordings/{recordingId} | - | Y | PlaylistController#removeRecordingFromPlaylist | - | - |
| 17 | TODO | PUT | /api/playlists/{id}/recordings/{recordingId} | - | Y | PlaylistController#addRecordingToPlaylist | - | - |
| 18 | FULL | GET | /api/storage/fs | - | Y | FileSystemStorageController#list | com.unirhy.e2e.StorageConfigE2eTest#file system storage should support create get update list delete flow | auth: com.unirhy.e2e.StorageConfigE2eTest#all storage endpoints should reject unauthenticated access |
| 19 | FULL | POST | /api/storage/fs | - | Y | FileSystemStorageController#create | com.unirhy.e2e.StorageConfigE2eTest#file system storage should support create get update list delete flow | auth: com.unirhy.e2e.StorageConfigE2eTest#all storage endpoints should reject unauthenticated access |
| 20 | FULL | DELETE | /api/storage/fs/{id} | - | Y | FileSystemStorageController#delete | com.unirhy.e2e.StorageConfigE2eTest#file system storage should support create get update list delete flow | auth: com.unirhy.e2e.StorageConfigE2eTest#all storage endpoints should reject unauthenticated access; linkage: com.unirhy.e2e.StorageConfigE2eTest#system config should enforce storage linkage constraints |
| 21 | FULL | GET | /api/storage/fs/{id} | - | Y | FileSystemStorageController#get | com.unirhy.e2e.StorageConfigE2eTest#file system storage should support create get update list delete flow | auth: com.unirhy.e2e.StorageConfigE2eTest#all storage endpoints should reject unauthenticated access |
| 22 | FULL | PUT | /api/storage/fs/{id} | - | Y | FileSystemStorageController#update | com.unirhy.e2e.StorageConfigE2eTest#file system storage should support create get update list delete flow | auth: com.unirhy.e2e.StorageConfigE2eTest#all storage endpoints should reject unauthenticated access |
| 23 | FULL | GET | /api/storage/oss | - | Y | OssStorageController#list | com.unirhy.e2e.StorageConfigE2eTest#oss storage should support create get update list delete flow | auth: com.unirhy.e2e.StorageConfigE2eTest#all storage endpoints should reject unauthenticated access |
| 24 | FULL | POST | /api/storage/oss | - | Y | OssStorageController#create | com.unirhy.e2e.StorageConfigE2eTest#oss storage should support create get update list delete flow | auth: com.unirhy.e2e.StorageConfigE2eTest#all storage endpoints should reject unauthenticated access |
| 25 | FULL | DELETE | /api/storage/oss/{id} | - | Y | OssStorageController#delete | com.unirhy.e2e.StorageConfigE2eTest#oss storage should support create get update list delete flow | auth: com.unirhy.e2e.StorageConfigE2eTest#all storage endpoints should reject unauthenticated access |
| 26 | FULL | GET | /api/storage/oss/{id} | - | Y | OssStorageController#get | com.unirhy.e2e.StorageConfigE2eTest#oss storage should support create get update list delete flow | auth: com.unirhy.e2e.StorageConfigE2eTest#all storage endpoints should reject unauthenticated access |
| 27 | FULL | PUT | /api/storage/oss/{id} | - | Y | OssStorageController#update | com.unirhy.e2e.StorageConfigE2eTest#oss storage should support create get update list delete flow | auth: com.unirhy.e2e.StorageConfigE2eTest#all storage endpoints should reject unauthenticated access |
| 28 | FULL | GET | /api/system/config | - | Y | SystemConfigController#get | com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow | - |
| 29 | FULL | POST | /api/system/config | - | N | SystemConfigController#create | com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow | - |
| 30 | FULL | PUT | /api/system/config | - | Y | SystemConfigController#update | com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow | - |
| 31 | FULL | GET | /api/system/config/status | - | N | SystemConfigController#isInitialized | com.unirhy.e2e.SystemAuthE2eTest#status and protected endpoints require authentication | - |
| 32 | FULL | GET | /api/task/running | - | Y | TaskController#listRunningTasks | com.unirhy.e2e.TaskContentReadE2eTest#scan task should expose running lifecycle and reject duplicate submission | auth: com.unirhy.e2e.TaskContentReadE2eTest#task and content endpoints should reject unauthenticated access |
| 33 | FULL | POST | /api/task/scan | - | Y | TaskController#executeScanTask | com.unirhy.e2e.TaskContentReadE2eTest#scan task should expose running lifecycle and reject duplicate submission | auth: com.unirhy.e2e.TaskContentReadE2eTest#task and content endpoints should reject unauthenticated access; conflict: duplicate submission returns 409 when running state is observable |
| 34 | FULL | POST | /api/tokens | - | N | TokenController#login | com.unirhy.e2e.SystemAuthE2eTest#duplicate init and wrong login return stable business errors | - |
| 35 | FULL | DELETE | /api/tokens/current | - | Y | TokenController#logout | com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow | - |
| 36 | FULL | GET | /api/works | - | Y | WorkController#listWork | com.unirhy.e2e.TaskContentReadE2eTest#works and albums should support read random and delete flow | auth: com.unirhy.e2e.TaskContentReadE2eTest#task and content endpoints should reject unauthenticated access |
| 37 | FULL | GET | /api/works/random | - | Y | WorkController#randomWork | com.unirhy.e2e.TaskContentReadE2eTest#works and albums should support read random and delete flow | auth: com.unirhy.e2e.TaskContentReadE2eTest#task and content endpoints should reject unauthenticated access; validation: length<=0 returns 400 |
| 38 | FULL | DELETE | /api/works/{id} | - | Y | WorkController#deleteWork | com.unirhy.e2e.TaskContentReadE2eTest#works and albums should support read random and delete flow | auth: com.unirhy.e2e.TaskContentReadE2eTest#task and content endpoints should reject unauthenticated access |
| 39 | FULL | GET | /api/works/{id} | - | Y | WorkController#getWorkById | com.unirhy.e2e.TaskContentReadE2eTest#works and albums should support read random and delete flow | auth: com.unirhy.e2e.TaskContentReadE2eTest#task and content endpoints should reject unauthenticated access |
