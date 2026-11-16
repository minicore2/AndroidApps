# VideoDownloader — functional Android browser prototype

A small, working Android browser (still in the `spike/` module) that:

- navigates any URL with back / forward / reload and a URL bar,
- accepts `http/https` `ACTION_VIEW` intents (links from other apps),
- blocks ads at two layers — network requests (`AdBlocker`) and cosmetic CSS
  injected at document start,
- blocks **all** popups (`window.open` override + `onCreateWindow`),
- sniffs HLS / DASH / progressive video URLs on the page and lets the user
  download them to `Movies/VideoDownloader` via MediaStore,
- expands an HLS master playlist into **one entry per resolution** (for example
  `1080p`, `720p`, `360p`) and does the same for DASH representations that have
  a standalone `BaseURL`; the master itself is no longer offered as a separate
  download,
- remuxes HLS/DASH to MP4 with Media3 Transformer.

It began as a throwaway capability spike; the original four-question verdict is
preserved at the bottom of this file.

## App icon

The launcher icon is an adaptive icon: a deep navy-to-teal gradient background
with a white play triangle and a downward download arrow. The vector foreground
and gradient background live in `app/src/main/res/drawable/`, the adaptive icon
XML in `app/src/main/res/mipmap-anydpi-v26/`, and legacy PNG mipmaps in
`app/src/main/res/mipmap-*dpi/`. The round icon uses the same adaptive XML;
there is no legacy round PNG. The 1024 px source is
`docs/icon/videodownloader-icon-1024.png`.

## Run

```
cd spike
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s VD_BROWSER VD_ADBLOCK VD_DOWNLOAD VD_RESOLVE
```

The app opens on the bundled `assets/spike.html` demo page (public HLS stream,
ad beacon, auto popup). Use the URL bar to navigate anywhere; press **↓** to
show/hide the detected-video panel.

### Resolution expansion

An HLS master playlist is fetched off the WebView network thread, parsed, and
replaced by one row per `#EXT-X-STREAM-INF` variant. Each row's label includes
the rendition's height (`1080p`, `720p`, ...), or its bandwidth when no
`RESOLUTION` attribute exists. Rows are deduplicated by URL, so the player's own
variant requests do not create a second entry.

A DASH manifest is expanded the same way when each `Representation` has a
standalone `BaseURL`. `SegmentTemplate`/`SegmentList` manifests stay as one
download item because they do not expose a single representation file.

Validated without a device by pure parser unit tests:

```
./gradlew :app:testDebugUnitTest
# PlaylistParserTest: 5 tests, 0 failures
```

### Verified on Pixel_9_Pro AVD (API 37, x86_64, 16KB page)

The AVD was started with 4 GB RAM (`-memory 4096`); 2 GB was not enough for
WebView plus Media3 and got OOM-killed.

- Resolution expansion on the bundled `spike.html`: the panel showed
  `偵測到的影片（5）` with `[HLS 720p]`, `[HLS 184p]`, `[HLS 288p]` and the
  remaining variants; the master playlist itself was not listed.
- Direct playlist URL: loading
  `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` also expanded to the same
  5 entries. `onPageStarted` skips its reset for a media URL because the
  main-frame request may already have started the resolver; otherwise the
  expansion result was cleared immediately.
- Download of the `184p` entry completed:
  `vd_1794748823214_193039199_mp4_h264_aac_ld_7_184p.mp4`,
  19,899,684 bytes, in `Movies/VideoDownloader/`
  (`content://media/external/video/media/47`). The filename carries the
  resolution suffix.
- Network ad block: `BLOCKED ad request: .../pagead2.googlesyndication.com/...`
- Cosmetic filter: the demo page's red `AD BANNER` is hidden.
- URL input: typing `example.org` logs `NAVIGATE: https://example.org` and loads it.

## Layout

| File | Responsibility |
| --- | --- |
| `SpikeActivity.kt` | Toolbar, URL bar, progress, WebView wiring, media list UI |
| `AdBlocker.kt` | EasyList-subset parser: network rules, exceptions, cosmetic rules |
| `MediaModels.kt` | `MediaKind` / `DetectedMedia` with the per-resolution quality label |
| `PlaylistParser.kt` | Pure HLS/DASH parsing; unit-tested |
| `MediaResolver.kt` | Background playlist fetch, cookie/referer forwarding, per-page expansion |
| `VideoDownloader.kt` | Progressive HTTP download, HLS/DASH remux, MediaStore export |
| `assets/adblock/rules.txt` | Curated starter ad/tracker rules (EasyList syntax) |
| `assets/spike.html` | Smoke-test page |

## Limits / next steps

- Ad rules are a curated starter list, not a full EasyList build, and there is no
  in-app updater yet.
- HLS/DASH downloads go through Media3's default `DataSource`, so sites that
  require cookies/referer for the media segments may fail; the playlist resolver
  does forward cookies/referer while expanding a master, and progressive files
  send them too.
- DASH manifests that use `SegmentTemplate` or `SegmentList` stay one item
  because there is no standalone URL for an individual representation.
- HLS masters that put audio in a separate `#EXT-X-MEDIA` rendition may download
  a video-only variant; audio-group muxing is not implemented yet.
- The engine is Android System WebView (Chromium). True embeddable Chromium and
  GeckoView trade-offs are in `../docs/adr/0001-rendering-engine.md`.
- Still not production packaging: package name `com.videodownloader.spike`, no
  foreground service, no persistent download queue.

## Original capability verdict (verified on Pixel_9_Pro AVD, API 37 x86_64, 16KB page)

All four questions answered **yes**:

1. Media sniffing — `shouldInterceptRequest` sees `m3u8`, variant playlists and `.ts` segments.
2. Cosmetic filtering — `addDocumentStartJavaScript` injects CSS at document start.
3. Popup blocking — JS `window.open` override + `onCreateWindow`.
4. HLS download + merge — Media3 `Transformer` produced a valid MP4:
   `REMUX COMPLETED ... size=1409863`.

### Implementation gotchas carried forward

- The **platform** `MediaMuxer` aborts on the API-37 x86_64 **16KB-page** emulator
  (`MPEG4Writer` FORTIFY: write count SIZE_MAX). Use Media3's pure-Java muxer
  (`media3-muxer` `Mp4Muxer`) via `Transformer.Builder.setMuxerFactory`.
- Media3 1.4.1's pure-Java muxers throw `Unsupported metadata` on the entries
  Transformer forwards; wrap the muxer and ignore `addMetadataEntry`.

Evidence: `verdict-logcat.txt`, `verdict-files.txt`, `verdict-remux-sample.mp4`.
