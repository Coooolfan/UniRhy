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
| 6 | TODO | GET | /api/albums | - | Y | AlbumController#listAlbums | - | - |
| 7 | TODO | GET | /api/albums/{id} | - | Y | AlbumController#getAlbum | - | - |
| 8 | TODO | GET | /api/media/{id} | !Range | Y | MediaFileController#getMedia | - | - |
| 9 | TODO | GET | /api/media/{id} | Range | Y | MediaFileController#getMediaWithRange | - | - |
| 10 | TODO | HEAD | /api/media/{id} | !Range | Y | MediaFileController#headMedia | - | - |
| 11 | TODO | GET | /api/playlists | - | Y | PlaylistController#listPlaylists | - | - |
| 12 | TODO | POST | /api/playlists | - | Y | PlaylistController#createPlaylist | - | - |
| 13 | TODO | DELETE | /api/playlists/{id} | - | Y | PlaylistController#deletePlaylist | - | - |
| 14 | TODO | GET | /api/playlists/{id} | - | Y | PlaylistController#getPlaylist | - | - |
| 15 | TODO | PUT | /api/playlists/{id} | - | Y | PlaylistController#updatePlaylist | - | - |
| 16 | TODO | DELETE | /api/playlists/{id}/recordings/{recordingId} | - | Y | PlaylistController#removeRecordingFromPlaylist | - | - |
| 17 | TODO | PUT | /api/playlists/{id}/recordings/{recordingId} | - | Y | PlaylistController#addRecordingToPlaylist | - | - |
| 18 | TODO | GET | /api/storage/fs | - | Y | FileSystemStorageController#list | - | - |
| 19 | TODO | POST | /api/storage/fs | - | Y | FileSystemStorageController#create | - | - |
| 20 | TODO | DELETE | /api/storage/fs/{id} | - | Y | FileSystemStorageController#delete | - | - |
| 21 | TODO | GET | /api/storage/fs/{id} | - | Y | FileSystemStorageController#get | - | - |
| 22 | TODO | PUT | /api/storage/fs/{id} | - | Y | FileSystemStorageController#update | - | - |
| 23 | TODO | GET | /api/storage/oss | - | Y | OssStorageController#list | - | - |
| 24 | TODO | POST | /api/storage/oss | - | Y | OssStorageController#create | - | - |
| 25 | TODO | DELETE | /api/storage/oss/{id} | - | Y | OssStorageController#delete | - | - |
| 26 | TODO | GET | /api/storage/oss/{id} | - | Y | OssStorageController#get | - | - |
| 27 | TODO | PUT | /api/storage/oss/{id} | - | Y | OssStorageController#update | - | - |
| 28 | FULL | GET | /api/system/config | - | Y | SystemConfigController#get | com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow | - |
| 29 | FULL | POST | /api/system/config | - | N | SystemConfigController#create | com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow | - |
| 30 | FULL | PUT | /api/system/config | - | Y | SystemConfigController#update | com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow | - |
| 31 | FULL | GET | /api/system/config/status | - | N | SystemConfigController#isInitialized | com.unirhy.e2e.SystemAuthE2eTest#status and protected endpoints require authentication | - |
| 32 | TODO | GET | /api/task/running | - | Y | TaskController#listRunningTasks | - | - |
| 33 | TODO | POST | /api/task/scan | - | Y | TaskController#executeScanTask | - | - |
| 34 | FULL | POST | /api/tokens | - | N | TokenController#login | com.unirhy.e2e.SystemAuthE2eTest#duplicate init and wrong login return stable business errors | - |
| 35 | FULL | DELETE | /api/tokens/current | - | Y | TokenController#logout | com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow | - |
| 36 | TODO | GET | /api/works | - | Y | WorkController#listWork | - | - |
| 37 | TODO | GET | /api/works/random | - | Y | WorkController#randomWork | - | - |
| 38 | TODO | DELETE | /api/works/{id} | - | Y | WorkController#deleteWork | - | - |
| 39 | TODO | GET | /api/works/{id} | - | Y | WorkController#getWorkById | - | - |
