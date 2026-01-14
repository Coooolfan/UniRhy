# 纸质风格音乐 App (PaperMusicApp) 后端接口需求

根据前端 React 组件 (`PaperMusicApp`) 的 UI 逻辑与交互需求，整理出以下后端需要提供的 API 接口。

## 1. 用户与个性化 (User & Personalization)

### 1.1 获取当前用户信息
用于显示右上角的头像及个人中心入口。
*   **Method**: `GET`
*   **Endpoint**: `/api/user/profile`
*   **Response**:
    ```json
    {
        "userId": "1001",
        "nickname": "MusikLover",
        "avatarUrl": "https://picsum.photos/seed/user/100/100"
    }
    ```

### 1.2 获取用户歌单 (Sidebar)
用于侧边栏“我的歌单”区域展示。
*   **Method**: `GET`
*   **Endpoint**: `/api/user/playlists`
*   **Response**:
    ```json
    [
        { "id": 1, "title": "雨天巴赫" },
        { "id": 2, "title": "咖啡馆噪音" },
        { "id": 3, "title": "深夜阅读" }
    ]
    ```

## 2. 首页内容发现 (Discovery & Home)

### 2.1 获取每日精选 (Hero Section)
用于首页顶部的“每日精选”卡片展示。
*   **Method**: `GET`
*   **Endpoint**: `/api/recommendations/daily`
*   **Response**:
    ```json
    {
        "id": "album_99",
        "title": "Nocturnes, Op. 9",
        "artist": "Frédéric Chopin",
        "year": "1832",
        "coverUrl": "https://picsum.photos/seed/chopin/600/600",
        "tags": ["Editor's Choice"],
        "playId": "track_01" // 点击播放时对应的资源ID
    }
    ```

### 2.2 获取音乐分类标签
用于首页中部的横向滚动分类条。
*   **Method**: `GET`
*   **Endpoint**: `/api/categories`
*   **Response**:
    ```json
    ["古典", "爵士", "极简主义", "环境音", "器乐"]
    ```

### 2.3 获取推荐/最近专辑列表
用于首页底部的专辑网格展示。
*   **Method**: `GET`
*   **Endpoint**: `/api/albums/recent` (或 `/api/recommendations/albums`)
*   **Response**:
    ```json
    [
        {
            "id": "alb_01",
            "title": "Kind of Blue",
            "artist": "Miles Davis",
            "coverUrl": "https://picsum.photos/seed/jazz1/400/400"
        },
        // ... 其他专辑
    ]
    ```

## 3. 搜索 (Search)

### 3.1 搜索音乐
用于顶部搜索框。
*   **Method**: `GET`
*   **Endpoint**: `/api/search`
*   **Query Params**: `?q={keywords}`
*   **Response**:
    ```json
    {
        "albums": [...],
        "artists": [...],
        "tracks": [...]
    }
    ```

## 4. 播放控制 (Player Control)

### 4.1 获取歌曲播放详情
当点击播放按钮时，需要获取音频流地址。
*   **Method**: `GET`
*   **Endpoint**: `/api/tracks/{id}/play`
*   **Response**:
    ```json
    {
        "id": "track_01",
        "title": "Ballade No. 1 in G Minor",
        "artist": "Chopin",
        "coverUrl": "https://picsum.photos/seed/chopin/600/600",
        "duration": 540, // 秒
        "streamUrl": "https://cdn.example.com/music/chopin_ballade.mp3"
    }
    ```

## 5. 交互操作 (Interactions)

### 5.1 收藏/取消收藏
用于点击爱心图标。
*   **Method**: `POST`
*   **Endpoint**: `/api/tracks/{id}/favorite`
*   **Body**: `{ "isFavorite": true }`

### 5.2 记录播放历史
用于更新侧边栏“最近播放”列表。
*   **Method**: `POST`
*   **Endpoint**: `/api/user/history`
*   **Body**: `{ "trackId": "track_01", "timestamp": 1678888888 }`
