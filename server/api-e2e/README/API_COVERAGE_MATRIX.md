# API 覆盖矩阵

> 此文件由 `:api-e2e:generateCoverageMatrix` 自动生成，请勿手改。

- 生成命令：`cd server && ./gradlew :api-e2e:generateCoverageMatrix`
- 校验命令：`cd server && ./gradlew :api-e2e:test`
- 统计口径：按 `HTTP 方法 + Path + headers 条件` 计数（媒体 Range/非Range 分开）。
- 当前接口总数：`37`

| # | 覆盖级别 | HTTP | Path | 条件 | 需登录 | Controller#method | 用例引用 | 备注 |
|---|---|---|---|---|---|---|---|---|
| 1 | TODO | GET | /api/account | - | Y | AccountController#list | - | - |
| 2 | TODO | POST | /api/account | - | Y | AccountController#create | - | - |
| 3 | TODO | GET | /api/account/me | - | Y | AccountController#me | - | - |
| 4 | TODO | DELETE | /api/account/{id} | - | Y | AccountController#delete | - | - |
| 5 | TODO | PUT | /api/account/{id} | - | Y | AccountController#update | - | - |
| 6 | TODO | GET | /api/album | - | Y | AlbumController#listAlbums | - | - |
| 7 | TODO | GET | /api/album/{id} | - | Y | AlbumController#getAlbum | - | - |
| 8 | TODO | GET | /api/media/{id} | !Range | Y | MediaFileController#getMedia | - | - |
| 9 | SMOKE | GET | /api/media/{id} | Range | Y | MediaFileController#getMediaWithRange | com.unirhy.e2e.SmokeTest#initialize login scan and stream media from real filesystem | - |
| 10 | TODO | GET | /api/playlist | - | Y | PlaylistController#listPlaylists | - | - |
| 11 | TODO | POST | /api/playlist | - | Y | PlaylistController#createPlaylist | - | - |
| 12 | TODO | DELETE | /api/playlist/{id} | - | Y | PlaylistController#deletePlaylist | - | - |
| 13 | TODO | GET | /api/playlist/{id} | - | Y | PlaylistController#getPlaylist | - | - |
| 14 | TODO | PUT | /api/playlist/{id} | - | Y | PlaylistController#updatePlaylist | - | - |
| 15 | TODO | PUT | /api/playlist/{id}/recordings/{recordingId} | - | Y | PlaylistController#addRecordingToPlaylist | - | - |
| 16 | TODO | GET | /api/storage/fs | - | Y | FileSystemStorageController#list | - | - |
| 17 | TODO | POST | /api/storage/fs | - | Y | FileSystemStorageController#create | - | - |
| 18 | TODO | DELETE | /api/storage/fs/{id} | - | Y | FileSystemStorageController#delete | - | - |
| 19 | TODO | GET | /api/storage/fs/{id} | - | Y | FileSystemStorageController#get | - | - |
| 20 | TODO | PUT | /api/storage/fs/{id} | - | Y | FileSystemStorageController#update | - | - |
| 21 | TODO | GET | /api/storage/oss | - | Y | OssStorageController#list | - | - |
| 22 | TODO | POST | /api/storage/oss | - | Y | OssStorageController#create | - | - |
| 23 | TODO | DELETE | /api/storage/oss/{id} | - | Y | OssStorageController#delete | - | - |
| 24 | TODO | GET | /api/storage/oss/{id} | - | Y | OssStorageController#get | - | - |
| 25 | TODO | PUT | /api/storage/oss/{id} | - | Y | OssStorageController#update | - | - |
| 26 | TODO | GET | /api/system/config | - | Y | SystemConfigController#get | - | - |
| 27 | SMOKE | POST | /api/system/config | - | N | SystemConfigController#create | com.unirhy.e2e.SmokeTest#initialize login scan and stream media from real filesystem | - |
| 28 | TODO | PUT | /api/system/config | - | Y | SystemConfigController#update | - | - |
| 29 | SMOKE | GET | /api/system/config/status | - | N | SystemConfigController#isInitialized | com.unirhy.e2e.SmokeTest#initialize login scan and stream media from real filesystem | - |
| 30 | SMOKE | GET | /api/task/running | - | Y | TaskController#listRunningTasks | com.unirhy.e2e.SmokeTest#initialize login scan and stream media from real filesystem | - |
| 31 | SMOKE | POST | /api/task/scan | - | Y | TaskController#executeScanTask | com.unirhy.e2e.SmokeTest#initialize login scan and stream media from real filesystem | - |
| 32 | TODO | DELETE | /api/token | - | Y | TokenController#logout | - | - |
| 33 | SMOKE | GET | /api/token | - | N | TokenController#login | com.unirhy.e2e.SmokeTest#initialize login scan and stream media from real filesystem | - |
| 34 | SMOKE | GET | /api/work | - | Y | WorkController#listWork | com.unirhy.e2e.SmokeTest#initialize login scan and stream media from real filesystem | - |
| 35 | TODO | GET | /api/work/random | - | Y | WorkController#randomWork | - | - |
| 36 | TODO | DELETE | /api/work/{id} | - | Y | WorkController#deleteWork | - | - |
| 37 | TODO | GET | /api/work/{id} | - | Y | WorkController#getWorkById | - | - |
