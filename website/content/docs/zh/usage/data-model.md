---
title: 作品、版本与专辑歌单
description: UniRhy 是如何组织音乐的——为什么"一首歌"被拆成了"作品"和"版本"两层。
---

传统播放器里，一个音频文件即为一首歌，专辑与歌单都是若干首歌的集合。UniRhy 在这套模型上做了一处调整——同一首歌被拆成两层来组织：**作品**与**版本**。

## 作品：抽象的曲目

**作品**指的是一首乐曲本身的抽象概念。

《卡农》是一个作品。《Bohemian Rhapsody》是一个作品。某部电影的主题曲也是一个作品。作品本身不携带时长、演奏者或音频文件，它只是"这首曲子"这一概念。

在播放请求中提到的"卡农"，指的便是作品。

## 版本：作品的一次具体演绎

**版本**是一首作品的某一次具体演绎或录制。

同一首《卡农》可能存在多个版本：

- 1963 年卡拉扬指挥柏林爱乐的录音
- 1989 年阿巴多的现场
- 室内乐改编版
- 某位独立音乐人翻弹的吉他版

这些都属于《卡农》，但听感各不相同。在 UniRhy 中，它们是同一个**作品**下的不同**版本**。每个版本各自维护演奏者、时长、封面，并对应自己的音频文件。

若播放请求未指明版本，系统会按预先设置的"默认版本"播放；若需指定，可在作品详情页中选择。

## 专辑与歌单收纳的是版本

这是 UniRhy 与多数播放器最显著的区别。

**专辑**和**歌单**收纳的并非抽象作品，而是**具体的版本**。

举例而言：将一首曲目加入歌单时，加入的是当时正在播放的那一个版本；后续打开歌单，听到的仍是该版本——既不会替换为另一次演绎，也不会替换为另一份录音。

这一行为看似自然，但多数播放器并不具备。当一首流行歌存在原唱、翻唱、不插电与现场等多种版本时，传统库要么将其视为四首互不相关的歌，要么强行塞进同一首歌的不同"音源"。UniRhy 让它们归属于同一个作品，同时保留各自的独立身份。

## 关系示意图

<figure class="diagram-figure" role="img" aria-labelledby="dm-title-zh" aria-describedby="dm-desc-zh">
<span id="dm-title-zh" class="sr-only">作品、版本、音频文件与专辑歌单的关系示意图</span>
<span id="dm-desc-zh" class="sr-only">作品 A 包含版本 A1 和版本 A2，作品 B 包含版本 B1 和版本 B2。每个版本对应一份或多份音频文件：版本 A1 挂载音频文件 A1.1 与 A1.2，版本 B2 挂载音频文件 B2.1 与 B2.2，其余版本各挂载一份。专辑 C 跨作品收纳版本 A1 与版本 B2；歌单 D 跨作品收纳版本 A2 与版本 B1。说明：专辑与歌单收纳的是版本而非作品，且可跨作品自由组合。</span>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 600" role="presentation" aria-hidden="true" focusable="false">
  <defs>
    <marker id="dm-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
      <path d="M0,0 L10,5 L0,10 z" fill="#8a6a3a"/>
    </marker>
    <style>
      .dm-node { fill: #f5f0e6; stroke: #b8721b; stroke-width: 1.5; }
      .dm-label { fill: #2c2825; font-family: Georgia, 'Times New Roman', Times, serif; font-size: 15px; }
      .dm-line { fill: none; stroke: #8a6a3a; stroke-width: 1.4; }
      .dm-dash { fill: none; stroke: #8a6a3a; stroke-width: 1.4; stroke-dasharray: 5 4; }
    </style>
  </defs>

  <rect class="dm-node" x="20" y="110" width="110" height="50" rx="3"/>
  <text class="dm-label" x="75" y="141" text-anchor="middle">作品 A</text>
  <rect class="dm-node" x="20" y="450" width="110" height="50" rx="3"/>
  <text class="dm-label" x="75" y="481" text-anchor="middle">作品 B</text>

  <rect class="dm-node" x="200" y="200" width="110" height="50" rx="3"/>
  <text class="dm-label" x="255" y="231" text-anchor="middle">专辑 C</text>
  <rect class="dm-node" x="200" y="360" width="110" height="50" rx="3"/>
  <text class="dm-label" x="255" y="391" text-anchor="middle">歌单 D</text>

  <rect class="dm-node" x="600" y="60" width="130" height="50" rx="3"/>
  <text class="dm-label" x="665" y="91" text-anchor="middle">版本 A1</text>
  <rect class="dm-node" x="600" y="220" width="130" height="50" rx="3"/>
  <text class="dm-label" x="665" y="251" text-anchor="middle">版本 A2</text>
  <rect class="dm-node" x="600" y="340" width="130" height="50" rx="3"/>
  <text class="dm-label" x="665" y="371" text-anchor="middle">版本 B1</text>
  <rect class="dm-node" x="600" y="500" width="130" height="50" rx="3"/>
  <text class="dm-label" x="665" y="531" text-anchor="middle">版本 B2</text>

  <rect class="dm-node" x="840" y="30" width="140" height="46" rx="3"/>
  <text class="dm-label" x="910" y="58" text-anchor="middle">音频文件 A1.1</text>
  <rect class="dm-node" x="840" y="94" width="140" height="46" rx="3"/>
  <text class="dm-label" x="910" y="122" text-anchor="middle">音频文件 A1.2</text>
  <rect class="dm-node" x="840" y="220" width="140" height="50" rx="3"/>
  <text class="dm-label" x="910" y="251" text-anchor="middle">音频文件 A2.1</text>
  <rect class="dm-node" x="840" y="340" width="140" height="50" rx="3"/>
  <text class="dm-label" x="910" y="371" text-anchor="middle">音频文件 B1.1</text>
  <rect class="dm-node" x="840" y="476" width="140" height="46" rx="3"/>
  <text class="dm-label" x="910" y="504" text-anchor="middle">音频文件 B2.1</text>
  <rect class="dm-node" x="840" y="540" width="140" height="46" rx="3"/>
  <text class="dm-label" x="910" y="568" text-anchor="middle">音频文件 B2.2</text>

  <path class="dm-line" d="M130,135 C 320,80 470,70 600,85" marker-end="url(#dm-arrow)"/>
  <path class="dm-line" d="M130,135 C 280,130 480,230 600,245" marker-end="url(#dm-arrow)"/>
  <path class="dm-line" d="M130,475 C 280,480 480,380 600,365" marker-end="url(#dm-arrow)"/>
  <path class="dm-line" d="M130,475 C 320,540 470,530 600,525" marker-end="url(#dm-arrow)"/>

  <path class="dm-line" d="M730,85 L 840,55" marker-end="url(#dm-arrow)"/>
  <path class="dm-line" d="M730,85 L 840,117" marker-end="url(#dm-arrow)"/>
  <path class="dm-line" d="M730,245 L 840,245" marker-end="url(#dm-arrow)"/>
  <path class="dm-line" d="M730,365 L 840,365" marker-end="url(#dm-arrow)"/>
  <path class="dm-line" d="M730,525 L 840,499" marker-end="url(#dm-arrow)"/>
  <path class="dm-line" d="M730,525 L 840,563" marker-end="url(#dm-arrow)"/>

  <path class="dm-dash" d="M310,225 C 420,150 520,95 600,85" marker-end="url(#dm-arrow)"/>
  <path class="dm-dash" d="M310,225 C 460,420 540,505 600,525" marker-end="url(#dm-arrow)"/>
  <path class="dm-dash" d="M310,385 C 420,310 520,250 600,245" marker-end="url(#dm-arrow)"/>
  <path class="dm-dash" d="M310,385 C 440,395 540,372 600,365" marker-end="url(#dm-arrow)"/>
</svg>
</figure>

- 一个**作品**下挂载多个**版本**；
- 每个**版本**下可挂载多份**音频文件**（同一版本的不同格式或码率，由客户端按网络条件自动选取）；
- **专辑**与**歌单**均为有序排列的**版本**集合。

## 该设计带来的能力

### 古典乐的多版本归并

对古典乐而言，同一部作品在不同指挥、不同乐团、不同年代下的录音差异显著，且为听众关注的核心维度。UniRhy 将其归入同一作品之下，便于浏览、对比，亦可分别加入不同的歌单。

### 翻唱与改编的清晰归属

流行乐中的 Live、Acoustic、Remix、Cover 不再以"歌名 (Live)""歌名 (Remix)"的形式分散在搜索结果中，而是以版本的身份共存于同一作品之下，原始版本仍以原本面貌呈现。

### 歌单内容的稳定性

歌单引用的是版本而非作品。某日加入歌单的演绎，半年后听到的仍是同一版本，不会因后续导入了同名的新录音而被悄然替换。

## 实际使用中的表现

- **导入音乐时**：UniRhy 根据音频文件中的元数据（曲名、艺术家等）自动判断其所属的作品与版本，多数情况下无需人工干预。
- **元数据不准时**：若同一作品的多份录音被错分至不同作品，或互不相关的曲目被错合并，可在管理界面手动调整。
- **同一版本多份文件**：原始无损文件与转码后的 Opus 文件会自动归入同一版本，客户端从中选取合适的一份用于播放，使用者无需关心底层细节。

日常使用中可见的概念主要是"专辑、歌单、艺术家、作品"几项；"版本"一词更多出现在作品详情页中，但正是这一层使整套结构得以贯通。
