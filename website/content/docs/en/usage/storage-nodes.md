---
title: Storage Nodes
description: Read-only vs. writable nodes, the system storage node, how tasks relate to nodes, and the difference between the file system and OSS providers.
---

A **storage node** is a concrete location UniRhy can read from and write to. Audio files, cover art and transcoded outputs all reside on some node, and metadata parse and transcode tasks are submitted against specific nodes. Nodes are mounted under _Settings → Storage Nodes_; any number may be mounted, and multiple nodes are processed in parallel.

## Read-only vs. writable

Every node is marked either read-only or writable at mount time.

- **Read-only nodes** take part in scanning and reading only. UniRhy never writes any file to a read-only node. This suits an existing music collection, a NAS share, or a bucket maintained by a third party.
- **Writable nodes** are read/write. In addition to being scanned, they may receive data written at runtime, such as transcoded outputs and extracted cover art.

A recommended setup keeps at least one read-only node for the source music library, plus one writable node for the system to write into.

## The system storage node

Among all mounted nodes, UniRhy designates one as the **system storage node** — the default destination when the system itself writes data. The path filled in during the first-run wizard is registered as the initial system storage node; see [First Run](/en/docs/usage/first-run).

The system storage node is subject to two constraints:

- exactly one node holds this role at any moment;
- it cannot be a read-only node.

Administrators can switch the role to any other writable node under _Settings → Storage Nodes_.

## How tasks relate to nodes

Built-in tasks require node selection at submission time, as follows.

### Metadata parse

Submission selects **one node to scan** (the system storage node is excluded). The task walks the audio files on that node and parses their embedded metadata.

Metadata parsing offers no destination node option and never moves or copies audio files. Its only write output is cover art extracted from the audio, whose destination is fixed:

- if the scanned node is writable, the cover is written back to that node;
- if the scanned node is read-only, the cover is written to the system storage node.

### Transcode

Submission selects a **source node** and a **destination node** separately:

- **Source node**: the node holding the audio to transcode (the system storage node is excluded);
- **Destination node**: where transcoded outputs are written — any writable node, including the system storage node.

Once transcoding completes, the output lands on the destination node and is registered as a new audio file of the corresponding track.

## The two provider types

Under the hood, storage nodes are backed by a **provider**. Two providers ship today; they differ in how the data is physically stored and how the server accesses it.

### File System provider

Points at an absolute path directly accessible to the server process.

| Item          | Description                                                                     |
| ------------- | ------------------------------------------------------------------------------- |
| Key fields    | Root path                                                                       |
| Prerequisites | The server process holds the required read/write permission on the path         |
| Docker        | Must be an in-container mount path; anything outside is lost with the container |
| Best fit      | Single-node deployments, local disks, NAS mounts, shared directories on the LAN |

### OSS provider

Points at an S3-compatible object storage bucket. Aliyun OSS, AWS S3, Cloudflare R2, MinIO and self-hosted S3 gateways all work.

| Item          | Description                                                              |
| ------------- | ------------------------------------------------------------------------ |
| Key fields    | Endpoint, bucket, access key, secret key, optional path prefix           |
| Prerequisites | Network reachability; credentials with the required rights on the bucket |
| Best fit      | Cloud archives, off-site backups, multiple instances sharing one library |

## Impact on the playback path

Both provider types expose the same interface to the player — the client obtains a URL from the API and hands it to the audio element. The difference lies in where that URL points:

- **File system nodes**: the URL points at the server's own media endpoint; the audio stream is read from disk by the server and forwarded to the client.
- **OSS nodes**: the server returns a **short-lived presigned URL**; the client fetches the object directly from the object store, and playback traffic does not flow through the server.

OSS nodes therefore substantially reduce the server's bandwidth footprint in multi-user or geographically distributed setups, while the short presign lifetime bounds the abuse window if a URL leaks.
