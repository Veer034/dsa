# Design Netflix / YouTube
> Tests: CDN architecture, video ingestion pipeline, ABR streaming, encoding, recommendation, storage at scale.

---

## Clarifying Questions You Should Ask

| Question | Why You're Asking |
|----------|--------------------|
| Upload (YouTube) or catalog-only (Netflix)? | Upload = ingestion pipeline needed |
| Live streaming or VOD or both? | Live is harder — different buffer/latency constraints |
| What resolutions and devices to support? | Drives encoding ladder |
| Concurrent viewers at peak? | CDN sizing and origin server capacity |
| Global? | CDN PoP placement, geo-routing |
| Recommendation system needed? | ML pipeline scope |
| DRM required? | Licensing / Widevine / FairPlay integration |
| Resume playback? | Watch position storage |

**Typical answer:** YouTube-like (user uploads), VOD + Live, support 360p to 4K, 2B users, peak 10M concurrent streams, global.

---

## Scale Estimation

```
Storage:    500 hours of video uploaded per minute (YouTube stat)
            1 hour of video at 1080p ≈ 2 GB raw → after encoding ~500MB
            500 hrs/min * 60 min * 500 MB = 900 TB/day new content

Streaming:  10M concurrent streams at avg 5 Mbps = 50 Tbps egress
            → This is why CDN is not optional — origin servers cannot handle this

Encoding:   1 hour raw → encode to 7 qualities → ~30 min encoding with 32 vCPUs
            → Need auto-scaling encoding fleet (AWS Elastic Transcoder / custom)
```

---

## HLD Diagram

```
                    ┌─────────────────┐
                    │  Creator/Upload │
                    └────────┬────────┘
                             │ HTTPS (chunked upload)
                    ┌────────▼────────┐
                    │  Upload Service │ → S3 Raw Bucket (multipart)
                    │  [Java/SBoot]   │
                    └────────┬────────┘
                             │ event: video.uploaded
                    ┌────────▼────────┐
                    │  <Kafka Topic>  │ video.uploaded
                    └────────┬────────┘
                             │
              ┌──────────────▼──────────────────────┐
              │         Video Processing Pipeline    │
              │  [Python Workers on K8s Jobs]        │
              │  1. Validate (codec, size, duration) │
              │  2. Split into chunks (GoP aligned)  │
              │  3. Encode to 7 qualities (FFmpeg)   │
              │     360p, 480p, 720p, 1080p, 4K      │
              │  4. Package to HLS/DASH segments     │
              │  5. Upload to S3 Processed Bucket    │
              │  6. Generate thumbnail               │
              └──────────────┬──────────────────────┘
                             │
                    ┌────────▼────────┐
                    │  Metadata Svc   │ → ((MySQL)) video metadata
                    │                 │ → ((Elasticsearch)) for search
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  CDN Network    │  CloudFront / Akamai
                    │  (100+ PoPs)    │  ← Origin: S3 Processed
                    └────────┬────────┘
                             │ HLS/DASH segments
                    ┌────────▼────────┐
                    │  Viewer Client  │  ABR Player (video.js / ExoPlayer)
                    └─────────────────┘

Side services:
  ┌──────────────┐   ┌─────────────────┐   ┌──────────────────┐
  │ Playback API │   │ Recommendation  │   │  Analytics Svc   │
  │ (resume pos) │   │ Engine (Python) │   │  Kafka → Druid   │
  └──────────────┘   └─────────────────┘   └──────────────────┘
```

---

## Key Design Decisions

### 1. Video Upload — Chunked + Resumable

```
Large file uploads (10GB+ raw) need resumable upload support.

Protocol: TUS (open protocol) or S3 Multipart Upload

Flow:
  1. Client: POST /upload/initiate → server returns uploadId + presigned URLs for each 5MB chunk
  2. Client uploads each chunk directly to S3 (parallel, 4 concurrent chunks)
  3. Client: POST /upload/complete { uploadId, partETags[] } → server calls S3 CompleteMultipartUpload
  4. Server publishes video.uploaded event to Kafka

Why client-to-S3 directly?
  → Bypasses our servers — saves egress cost and server load
  → S3 handles durability, we just orchestrate
```

### 2. Video Processing Pipeline

```
This is the encoding pipeline — the most complex part.

Step 1: Chunk the raw video
  → Split into 2-second GOP-aligned chunks for parallel encoding
  → Each chunk encoded independently → parallelized on K8s Jobs

Step 2: Encoding Ladder (FFmpeg)
  Quality    Resolution   Bitrate
  ─────────────────────────────────
  144p       256x144      100 kbps
  360p       640x360      400 kbps
  480p       854x480      800 kbps
  720p       1280x720     2500 kbps
  1080p      1920x1080    5000 kbps
  1440p      2560x1440    10000 kbps
  4K         3840x2160    20000 kbps

Step 3: Package to HLS (HTTP Live Streaming)
  → Each quality → .ts segment files + .m3u8 playlist
  → Master playlist: master.m3u8 references all quality playlists
  → Client player picks quality based on bandwidth (ABR)

Step 4: Store to S3
  s3://videos-processed/{videoId}/hls/master.m3u8
  s3://videos-processed/{videoId}/hls/720p/segment_001.ts
  ...

Step 5: Invalidate CDN cache if re-encode (quality update)
```

### 3. CDN Strategy

```
Why CDN is the entire architecture here:
  10M streams * 5 Mbps = 50 Tbps — no origin can serve this

CDN setup:
  Origin:  S3 (us-east-1) as origin
  CDN:     CloudFront with 250+ global PoPs (or Akamai for enterprise)
  
Cache behavior:
  → Segments (.ts files) cached at edge for 24h (immutable after encode)
  → Manifest (.m3u8) cached for 5 seconds (live) or 1h (VOD)
  → Thumbnail images cached for 7 days

Geo-routing:
  → DNS routes viewer to nearest PoP
  → PoP serves from cache → cache miss → fetches from S3 origin → caches
  → Popular content: 99% cache hit rate at PoP level

For Live Streaming:
  → Origin: RTMP ingest server → HLS transcoder → push segments to S3/CDN every 2s
  → CDN TTL for live segments: 2s (near real-time)
  → Latency: 10-30 seconds (HLS) vs 3-5 seconds (LL-HLS or DASH-CMAF)
```

### 4. ABR — Adaptive Bitrate Streaming

```
Player (ExoPlayer on Android, video.js on web):
  1. Download master.m3u8 (manifest of all quality playlists)
  2. Measure bandwidth (download time of last segment)
  3. Select quality playlist that fits bandwidth with buffer margin
  4. Download segments from selected quality
  5. Continuously re-evaluate — switch up/down every few segments

The player runs entirely on client — our server just serves files.
Our CDN serves segments fast enough to keep 30+ second buffer.

What we track (Analytics via Kafka):
  → quality_switches (how often did it degrade?)
  → buffer_stall_events (rebuffering — key quality metric)
  → startup_time (first frame latency)
  → exit_before_start (impatient users)
```

### 5. Recommendation Engine

```
Input signals (Kafka → Druid → ML pipeline):
  → watch history (video_id, user_id, watch_percent, timestamp)
  → search queries
  → likes, shares, comments
  → session context (time of day, device)

Models used (mention these):
  → Collaborative Filtering: users who watched X also watched Y
  → Matrix Factorization (ALS): user-item embedding
  → Two-tower Neural Net: separate user embedding + item embedding, dot product for score
  → Real-time: contextual bandits for "trending now"

Infrastructure:
  → Offline training: PySpark on EMR / Databricks
  → Model store: MLflow
  → Feature store: Redis (real-time features) + Hive (batch features)
  → Inference: Python Flask / FastAPI serving → called by Recommendation Service
  → Pre-compute: nightly batch generates "top 100 for each user" → store in Redis
  → Real-time override: inject trending/new content at serve time
```

### 6. Metadata Storage

```
MySQL (transactional):
  videos (video_id, creator_id, title, description, status, duration, created_at)
  video_stats (video_id, view_count, like_count, comment_count)  ← updated async

Elasticsearch (search & discovery):
  Index: videos
  → title, description, tags, transcript (for search)
  → category, language, duration_range (for filtering)
  → popularity_score (for ranking)

ScyllaDB (watch history — high write throughput):
  watch_history (user_id, video_id, watch_position, watched_at)
  → Partition by user_id, cluster by watched_at DESC
  → Used for resume playback and recommendation input

Redis:
  → resume_position:{userId}:{videoId} → seconds (TTL 30 days)
  → trending_videos → Sorted Set by view velocity
```

---

## LLD — Playback API

```java
// Playback session — called when user presses Play
@GetMapping("/watch/{videoId}")
public PlaybackResponse getPlaybackInfo(@PathVariable String videoId,
                                         @RequestHeader("Authorization") String jwt) {
    String userId = jwtService.extractUserId(jwt);
    
    VideoMetadata meta = videoMetadataService.get(videoId);  // MySQL cached in Redis
    if (meta.getStatus() != VideoStatus.READY) throw new VideoNotReadyException();
    
    // DRM token if premium content
    String drmToken = meta.isDrm() ? drmService.generateToken(userId, videoId) : null;
    
    // Resume position
    Long resumePosition = watchHistoryService.getPosition(userId, videoId);  // Redis lookup
    
    // CDN URL construction
    String manifestUrl = cdnService.buildManifestUrl(videoId);  // CDN domain + path
    
    // Async: log playback start event
    kafkaTemplate.send("video.play", new PlayEvent(userId, videoId, Instant.now()));
    
    return new PlaybackResponse(manifestUrl, resumePosition, drmToken, meta.getSubtitles());
}
```

---

## DRM (Digital Rights Management)

```
Mention you know the flow, don't go deep unless asked:

Widevine (Google/Android) + FairPlay (Apple) + PlayReady (Microsoft)
→ Video encrypted with AES-128 during packaging
→ Key stored in Key Management Service (KMS)
→ Player sends DRM license request → License Server validates entitlement → returns decryption key
→ Player decrypts and plays — key never leaves secure enclave

In your system:
  → Entitlement check: is this user subscribed? Can they watch this content in their region?
  → License server: BuyDRM / EZDRM (third-party) or build in-house
  → Token: signed JWT with userId, videoId, region, expiry → passed to license server
```

---

## Interview Tips

- **The pipeline, not just the player** — interviewers want to see ingestion → encode → CDN → playback, not just "we use CDN."
- **Name HLS and DASH** — and explain the difference (Apple vs open standard).
- **ABR is client-side** — important to clarify: your servers don't pick quality, the player does based on bandwidth.
- Drop **two-tower model** for recommendations — shows ML awareness.
- Mention **watch position in Redis** for resume — low latency, high write, perfect use case.
- For live streaming: call out the **latency vs buffering trade-off** (10s HLS vs 3s LL-HLS).
- Mention **geo-blocking** via CDN signed cookies + region check in playback API.
