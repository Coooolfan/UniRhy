---
title: Works, Tracks, Albums and Playlists
description: How UniRhy organizes music — and why "a song" is split into two layers, the work and the track.
---

In most players, one audio file is one song, and albums and playlists are simply collections of songs. UniRhy adjusts this model slightly — each song is organized in two layers: a **Work** and a **Track**.

## Work: the abstract piece

A **Work** is the abstract notion of a piece of music.

_Canon in D_ is a work. _Bohemian Rhapsody_ is a work. A film's theme song is a work. A work carries no duration, no performer and no audio file — it represents only the idea of the piece.

A playback request that names "Canon" refers to the work.

## Track: one concrete take of a work

A **Track** is one specific performance or recording of a work.

A single _Canon in D_ may exist as several tracks:

- Karajan with the Berlin Philharmonic, 1963
- Abbado live, 1989
- A chamber arrangement
- A solo guitar cover by an independent musician

All of them are _Canon_, yet they sound entirely different. In UniRhy they live as distinct **tracks** under the same **work**. Each track maintains its own artists, duration, cover and audio files.

When a playback request does not name a track, the system plays the track marked as the default; to play a specific one, select it from the work's detail page.

## Albums and playlists collect tracks

**Albums** and **playlists** do not collect abstract works. They collect **specific tracks**.

For example: adding a track to a playlist adds the exact track that was playing at that moment. Reopening the playlist later plays the same track — not another performance, and not another take of it.

When a pop song exists as a studio version, a cover, an acoustic and a live take, UniRhy keeps them all under the same work while preserving each one's identity.

## Relationships at a glance

<figure class="diagram-figure" role="img" aria-labelledby="dm-title-en" aria-describedby="dm-desc-en">
<span id="dm-title-en" class="sr-only">Diagram showing how works, tracks, audio files, albums and playlists relate.</span>
<span id="dm-desc-en" class="sr-only">Work A contains Track A1 and Track A2; Work B contains Track B1 and Track B2. Each track maps to one or more audio files: Track A1 holds Audio File A1.1 and A1.2; Track B2 holds Audio File B2.1 and B2.2; the remaining tracks hold one file each. Album C collects Track A1 and Track B2 across both works; Playlist D collects Track A2 and Track B1 across both works. Note that albums and playlists collect tracks rather than works, and may freely mix tracks from different works.</span>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 600" role="presentation" aria-hidden="true" focusable="false">
  <defs>
    <marker id="dm-arrow-en" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
      <path d="M0,0 L10,5 L0,10 z" fill="#8a6a3a"/>
    </marker>
    <style>
      .dm-node { fill: #f5f0e6; stroke: #b8721b; stroke-width: 1.5; }
      .dm-label { fill: #2c2825; font-family: Georgia, 'Times New Roman', Times, serif; font-size: 14px; }
      .dm-line { fill: none; stroke: #8a6a3a; stroke-width: 1.4; }
      .dm-dash { fill: none; stroke: #8a6a3a; stroke-width: 1.4; stroke-dasharray: 5 4; }
    </style>
  </defs>

  <rect class="dm-node" x="20" y="110" width="110" height="50" rx="3"/>
  <text class="dm-label" x="75" y="141" text-anchor="middle">Work A</text>
  <rect class="dm-node" x="20" y="450" width="110" height="50" rx="3"/>
  <text class="dm-label" x="75" y="481" text-anchor="middle">Work B</text>

  <rect class="dm-node" x="200" y="200" width="110" height="50" rx="3"/>
  <text class="dm-label" x="255" y="231" text-anchor="middle">Album C</text>
  <rect class="dm-node" x="200" y="360" width="110" height="50" rx="3"/>
  <text class="dm-label" x="255" y="391" text-anchor="middle">Playlist D</text>

  <rect class="dm-node" x="600" y="60" width="130" height="50" rx="3"/>
  <text class="dm-label" x="665" y="91" text-anchor="middle">Track A1</text>
  <rect class="dm-node" x="600" y="220" width="130" height="50" rx="3"/>
  <text class="dm-label" x="665" y="251" text-anchor="middle">Track A2</text>
  <rect class="dm-node" x="600" y="340" width="130" height="50" rx="3"/>
  <text class="dm-label" x="665" y="371" text-anchor="middle">Track B1</text>
  <rect class="dm-node" x="600" y="500" width="130" height="50" rx="3"/>
  <text class="dm-label" x="665" y="531" text-anchor="middle">Track B2</text>

  <rect class="dm-node" x="840" y="30" width="140" height="46" rx="3"/>
  <text class="dm-label" x="910" y="58" text-anchor="middle">Audio File A1.1</text>
  <rect class="dm-node" x="840" y="94" width="140" height="46" rx="3"/>
  <text class="dm-label" x="910" y="122" text-anchor="middle">Audio File A1.2</text>
  <rect class="dm-node" x="840" y="220" width="140" height="50" rx="3"/>
  <text class="dm-label" x="910" y="251" text-anchor="middle">Audio File A2.1</text>
  <rect class="dm-node" x="840" y="340" width="140" height="50" rx="3"/>
  <text class="dm-label" x="910" y="371" text-anchor="middle">Audio File B1.1</text>
  <rect class="dm-node" x="840" y="476" width="140" height="46" rx="3"/>
  <text class="dm-label" x="910" y="504" text-anchor="middle">Audio File B2.1</text>
  <rect class="dm-node" x="840" y="540" width="140" height="46" rx="3"/>
  <text class="dm-label" x="910" y="568" text-anchor="middle">Audio File B2.2</text>

  <path class="dm-line" d="M130,135 C 320,80 470,70 600,85" marker-end="url(#dm-arrow-en)"/>
  <path class="dm-line" d="M130,135 C 280,130 480,230 600,245" marker-end="url(#dm-arrow-en)"/>
  <path class="dm-line" d="M130,475 C 280,480 480,380 600,365" marker-end="url(#dm-arrow-en)"/>
  <path class="dm-line" d="M130,475 C 320,540 470,530 600,525" marker-end="url(#dm-arrow-en)"/>

  <path class="dm-line" d="M730,85 L 840,55" marker-end="url(#dm-arrow-en)"/>
  <path class="dm-line" d="M730,85 L 840,117" marker-end="url(#dm-arrow-en)"/>
  <path class="dm-line" d="M730,245 L 840,245" marker-end="url(#dm-arrow-en)"/>
  <path class="dm-line" d="M730,365 L 840,365" marker-end="url(#dm-arrow-en)"/>
  <path class="dm-line" d="M730,525 L 840,499" marker-end="url(#dm-arrow-en)"/>
  <path class="dm-line" d="M730,525 L 840,563" marker-end="url(#dm-arrow-en)"/>

  <path class="dm-dash" d="M310,225 C 420,150 520,95 600,85" marker-end="url(#dm-arrow-en)"/>
  <path class="dm-dash" d="M310,225 C 460,420 540,505 600,525" marker-end="url(#dm-arrow-en)"/>
  <path class="dm-dash" d="M310,385 C 420,310 520,250 600,245" marker-end="url(#dm-arrow-en)"/>
  <path class="dm-dash" d="M310,385 C 440,395 540,372 600,365" marker-end="url(#dm-arrow-en)"/>
</svg>
</figure>

- A **work** holds many **tracks**;
- Each **track** holds one or more **audio files** (different formats or bitrates of the same track — the client picks one based on network conditions);
- **Albums** and **playlists** are ordered collections of **tracks**.

## What this enables

### Multiple tracks of a classical work

For classical music, tracks of the same work by different conductors, ensembles and eras differ substantially and are the dimension listeners care about most. UniRhy groups them under a single work, enabling side-by-side browsing, comparison and independent inclusion in different playlists.

### Clean placement of covers and arrangements

Live, acoustic, remix and cover variants of a pop song no longer clutter search results as _Song Name (Live)_ / _Song Name (Remix)_. They coexist as tracks of one work, and the original take continues to appear as itself.

### Playlist stability

A playlist references the track, not the work. A track added today will remain the one played six months from now; it will not be silently replaced because a later import introduced a new track sharing the same title.

## Behaviour during everyday use

- **During import**: UniRhy reads embedded metadata (title, artist, etc.) to determine the work a file belongs to and which track it is. Manual intervention is rarely required.
- **When metadata is inaccurate**: if tracks of a single work are split across separate works, or unrelated tracks are merged into one, the grouping can be corrected from the admin UI.
- **Multiple files per track**: the original lossless file and its Opus/MP3 transcodes are automatically attached to the same track; the client picks an appropriate one for playback without surfacing the detail.

In day-to-day use the visible concepts are mainly Albums, Playlists, Artists and Works. The term Track appears mostly on the work detail page, yet it is the layer that ties the whole structure together.
