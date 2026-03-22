# Blog 博客系统

## 概述

博客基于 Markdown 文件驱动，构建时由 Vite 插件将 `.md` 文件编译为静态内容，最终产物为纯前端静态站点，部署只需 nginx。

支持中英双语，每篇文章需同时提供中文和英文两份 `.md` 文件。

## 目录结构

```
content/blog/
├── zh/                  # 中文文章
│   ├── hello-world.md
│   └── my-post.md
└── en/                  # 英文文章
    ├── hello-world.md
    └── my-post.md
```

同一篇文章在 `zh/` 和 `en/` 下使用 **相同的文件名**（即 slug），用户可在页面上切换语言。

## 编写文章

每篇 `.md` 文件顶部需包含 YAML frontmatter：

```markdown
---
title: 文章标题
description: 文章简短描述，用于列表页展示
publishAt: 1774137600
draft: false
cover: /images/my-cover.jpg
---

# 正文标题

正文内容...
```

### Frontmatter 字段

| 字段          | 必填 | 说明                                                      |
| ------------- | ---- | --------------------------------------------------------- |
| `title`       | 是   | 文章标题                                                  |
| `description` | 是   | 摘要描述，显示在博客列表页                                |
| `publishAt`   | 是   | 发布时间，秒级 UTC 时间戳（如 `1774137600` = 2026-03-22） |
| `cover`       | 否   | 头图路径，放在 `public/` 目录下使用绝对路径               |
| `draft`       | 是   | 设为 `true` 标记草稿，开发模式可见，生产构建自动过滤      |

### 正文

正文使用标准 Markdown 语法，支持：

- 标题（h1 ~ h6）
- 段落、加粗、斜体
- 有序/无序列表
- 链接、图片
- 引用块
- 代码块（含行内代码）
- 表格
- 分隔线
- HTML（需谨慎使用）

### 图片

静态图片放在 `public/images/blog/` 下，在 Markdown 中使用绝对路径引用：

```markdown
![alt text](/images/blog/my-image.png)
```

## 本地预览

```sh
yarn dev
```

启动后访问：

- 博客列表页：`http://localhost:5173/blog`
- 文章详情页：`http://localhost:5173/blog/<slug>`

修改 `.md` 文件后 Vite 会自动热更新。

## 构建

```sh
yarn build
```

产物输出到 `dist/` 目录。

## 部署

将 `dist/` 目录部署到 nginx，配置 SPA 路由回退：

```nginx
server {
    listen 80;
    server_name example.com;
    root /path/to/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

## 草稿

在 frontmatter 中添加 `draft: true` 即可将文章标记为草稿：

```markdown
---
title: 未完成的文章
description: 这是一篇草稿
publishAt: 1774137600
draft: true
---
```

- **开发模式** (`yarn dev`)：草稿在列表页可见，标题旁会显示 `DRAFT` 标记
- **生产构建** (`yarn build`)：草稿自动从列表中过滤，不会被编译

发布时删除 `draft: true` 或改为 `draft: false` 即可。

## 新增文章流程

1. 在 `content/blog/zh/` 下新建 `<slug>.md`，填写 frontmatter 和中文正文
2. 在 `content/blog/en/` 下新建同名 `<slug>.md`，填写 frontmatter 和英文正文
3. 写作期间可设置 `draft: true`，通过 `yarn dev` 本地预览
4. 完成后移除 `draft: true`
5. `yarn build` 构建后部署
