# Sandbox: Safe Downloads (Android)

## What this app is

An Android browser that keeps every downloaded file locked inside the app's private storage (the "vault"). Files can never be installed, executed or written to shared storage. They are only opened in built in viewers, and all risky parsing (images, PDFs) happens inside an **isolated process** with no permissions, no network and no file access. Sharing defaults to a **safe copy** that is rebuilt from scratch.

The browsing itself is a normal Chromium WebView. We never claim browsing is safer than Chrome. The security value is entirely about what happens to downloaded files.

## Golden rules (never break these)

1. The app manifest requests **only** `INTERNET`, `POST_NOTIFICATIONS` and `USE_BIOMETRIC`. Never add storage permissions, `REQUEST_INSTALL_PACKAGES`, `QUERY_ALL_PACKAGES` or anything else without asking.
2. Downloaded files are only ever written to `context.filesDir/vault/`. Never to MediaStore, Downloads, external storage or cache shared with other apps.
3. Never pass a vault file to another app with `Intent.ACTION_VIEW`. The only way out is the share flow, through `FileProvider`, with a read only temporary grant.
4. Executable types (APK, APKS, XAPK, DEX, JAR, EXE, MSI, scripts, unknown binaries) are stored but can **never** be opened or shared. The UI shows them as Blocked.
5. File type is decided by **magic bytes**, never by extension or the server's MIME type.
6. Image and PDF decoding never happens in the main app process. It goes through the `:sandbox` isolated service.
7. WebView hardening settings (see below) must not be relaxed. No `addJavascriptInterface` on pages that load web content.
8. `android:allowBackup="false"` and data extraction rules exclude everything, so vault files never reach cloud backups.

## Tech stack

* Kotlin, Jetpack Compose, Material 3 (fixed custom colour scheme, **no dynamic colour**)
* Hilt for DI
* Room for vault metadata
* DataStore (Preferences) for settings
* OkHttp for downloading (with cookies copied from WebView `CookieManager`)
* Retrofit + kotlinx.serialization for the VirusTotal hash lookup
* Media3 ExoPlayer for video and audio playback
* Media3 Transformer for re encoding video and audio safe copies
* `android.graphics.pdf.PdfRenderer` and `BitmapFactory` inside the isolated process
* WorkManager for scheduled cleanup (auto delete after X days)
* AndroidX Biometric for vault lock
* Coroutines + Flow, MVVM with unidirectional state (`UiState` data classes, `ViewModel` exposes `StateFlow`)
* Compose Navigation (type safe routes)
* Gradle version catalog (`gradle/libs.versions.toml`), Kotlin DSL
* minSdk 28, targetSdk and compileSdk: latest stable
* Testing: JUnit 5 or JUnit 4 + Turbine + MockK for unit tests, Compose UI tests for key screens
* Static checks: ktlint + detekt

Package name: `dev.ikram.sandbox`

## Gradle modules

```
sandbox/
├── app/                      Main app: UI, features, navigation, WebView, DI wiring
├── core/
│   ├── model/                Pure Kotlin data classes and enums (no Android deps)
│   ├── data/                 Room, DataStore, repositories, downloader, VirusTotal client
│   ├── designsystem/         Compose theme, colours, typography, shared components
│   └── filetype/             Magic byte sniffer and type policy (pure Kotlin, heavily tested)
├── sandbox/                  Isolated process renderer service + AIDL (library module)
├── gradle/libs.versions.toml
├── settings.gradle.kts
└── build.gradle.kts
```

Dependency direction: `app` depends on everything. `core:data` depends on `core:model` and `core:filetype`. `sandbox` depends only on `core:model` (keep it tiny). Nothing depends on `app`.

## Detailed structure

```
app/src/main/
├── AndroidManifest.xml
├── res/xml/
│   ├── file_paths.xml                    FileProvider: only share_tmp/ directory
│   └── data_extraction_rules.xml         Exclude everything
└── java/dev/ikram/sandbox/
    ├── SandboxApp.kt                     @HiltAndroidApp, WorkManager setup
    ├── MainActivity.kt                   Single activity, edge to edge, hosts NavHost
    ├── navigation/
    │   ├── Routes.kt                     @Serializable route objects
    │   └── SandboxNavHost.kt
    ├── di/
    │   ├── AppModule.kt
    │   ├── NetworkModule.kt              OkHttp, Retrofit
    │   └── DatabaseModule.kt
    ├── feature/
    │   ├── onboarding/
    │   │   ├── OnboardingScreen.kt       3 pages + "Start browsing"
    │   │   └── OnboardingViewModel.kt
    │   ├── browser/
    │   │   ├── BrowserScreen.kt          Address bar, WebView host, tab count, vault badge
    │   │   ├── BrowserViewModel.kt
    │   │   ├── TabManager.kt             In memory tabs (v1: simple list)
    │   │   ├── web/
    │   │   │   ├── SandboxWebView.kt     Compose wrapper (AndroidView)
    │   │   │   ├── WebViewHardening.kt   Applies all security settings
    │   │   │   ├── SandboxWebViewClient.kt   Blocks file:// content:// intent:// etc.
    │   │   │   ├── SandboxChromeClient.kt    Progress, title, no file chooser in v1
    │   │   │   └── DownloadInterceptor.kt    DownloadListener → DownloadRequest
    │   │   └── components/
    │   │       ├── AddressBar.kt
    │   │       └── DownloadPromptSheet.kt    "Save to vault" / blocked variant
    │   ├── vault/
    │   │   ├── VaultScreen.kt            Search, filter chips, list/grid toggle, empty state
    │   │   ├── VaultViewModel.kt
    │   │   ├── FileDetailsSheet.kt       Real type, size, source, date, SHA256, VirusTotal row
    │   │   ├── MismatchWarningDialog.kt
    │   │   └── components/
    │   │       ├── VaultItemRow.kt
    │   │       ├── VaultItemCard.kt
    │   │       └── StatusBadge.kt        Safe / Text only / Blocked
    │   ├── viewer/
    │   │   ├── ViewerRouter.kt           Picks viewer from FileCategory; blocked → refuse
    │   │   ├── image/
    │   │   │   ├── ImageViewerScreen.kt  Zoom, swipe; bitmap comes from SandboxRenderer
    │   │   │   └── ImageViewerViewModel.kt
    │   │   ├── pdf/
    │   │   │   ├── PdfViewerScreen.kt    Lazy page list, page counter, thumbnails
    │   │   │   └── PdfViewerViewModel.kt Requests pages one at a time from SandboxRenderer
    │   │   ├── media/
    │   │   │   ├── VideoPlayerScreen.kt  Media3 PlayerView in Compose
    │   │   │   ├── AudioPlayerScreen.kt
    │   │   │   └── MediaViewModel.kt
    │   │   ├── text/
    │   │   │   ├── TextViewerScreen.kt   Monospace, size limited, decoded as UTF 8 with fallback
    │   │   │   └── TextViewerViewModel.kt
    │   │   └── components/SandboxIndicator.kt   "Opened in sandbox" chip in top bar
    │   ├── share/
    │   │   ├── ShareSheet.kt             Safe copy (default) / Original (warning) / Link
    │   │   ├── ShareViewModel.kt
    │   │   └── ShareLauncher.kt          FileProvider URI + ACTION_SEND with read grant
    │   ├── settings/
    │   │   ├── SettingsScreen.kt
    │   │   └── SettingsViewModel.kt
    │   ├── explainer/
    │   │   └── HowProtectionWorksScreen.kt
    │   └── lock/
    │       └── BiometricGate.kt          Wraps vault routes when lock is enabled
    └── util/
        └── Formatters.kt                 Sizes, dates

core/model/src/main/java/dev/ikram/sandbox/core/model/
├── VaultFile.kt              id, displayName, sizeBytes, detectedType, claimedType,
│                             category, sha256, sourceUrl, sourceHost, createdAt, scanStatus
├── FileCategory.kt           IMAGE, VIDEO, AUDIO, PDF, TEXT, ARCHIVE, BLOCKED, UNKNOWN
├── DetectedType.kt           JPEG, PNG, WEBP, GIF, BMP, HEIC, MP4, WEBM, MKV, MP3, OGG,
│                             FLAC, WAV, M4A, PDF, ZIP, APK, ELF, PE_EXE, TEXT, UNKNOWN ...
├── ScanStatus.kt             NOT_CHECKED, CHECKING, CLEAN, FLAGGED(count), ERROR
├── DownloadRequest.kt        url, userAgent, contentDisposition, mimeType, contentLength, referer
├── DownloadState.kt
└── ShareMode.kt              SAFE_COPY, ORIGINAL, LINK

core/filetype/src/main/java/dev/ikram/sandbox/core/filetype/
├── MagicSniffer.kt           Reads first 64 bytes (and ZIP central dir for APK) → DetectedType
├── TypePolicy.kt             DetectedType → FileCategory, canOpen, canShareOriginal, canSafeCopy
└── NameSanitizer.kt          Cleans server file names for display only (never used as a path)
core/filetype/src/test/       Exhaustive tests with fixture byte arrays for every type,
                              including disguised files (APK named .mp4, EXE named .pdf)

core/data/src/main/java/dev/ikram/sandbox/core/data/
├── db/
│   ├── SandboxDatabase.kt
│   ├── VaultFileEntity.kt
│   └── VaultFileDao.kt
├── storage/
│   ├── VaultStorage.kt       Paths: filesDir/vault/<uuid>.bin (no extension ever),
│   │                         filesDir/share_tmp/ for outgoing safe copies
│   └── TempShareCleaner.kt
├── download/
│   ├── VaultDownloader.kt    OkHttp stream → temp file → hash while streaming →
│   │                         sniff → atomic move into vault → insert Room row
│   ├── DownloadLimits.kt     Max size (e.g. 2 GB), timeout
│   └── WebViewCookieJar.kt   Bridges CookieManager to OkHttp
├── scan/
│   ├── VirusTotalApi.kt      GET /api/v3/files/{sha256} only. Never upload files.
│   └── ScanRepository.kt
├── settings/
│   └── SettingsRepository.kt DataStore: askBeforeDownload, autoScan, autoDeleteDays,
│                             vaultLock, onboardingDone
├── repository/
│   └── VaultRepository.kt    Flow<List<VaultFile>>, search, filter, delete
└── work/
    └── AutoDeleteWorker.kt

core/designsystem/src/main/java/dev/ikram/sandbox/core/designsystem/
├── theme/
│   ├── Color.kt              Broadsheet inspired teal/deep blue palette, light + dark
│   ├── SemanticColors.kt     safe (teal), caution (amber), blocked (red), neutral (grey)
│   │                         exposed via CompositionLocal, NOT part of dynamic colour
│   ├── Type.kt               Sans serif (Roboto) for all UI text
│   └── Theme.kt              SandboxTheme, dynamicColor = false always
└── component/
    ├── FileTypeIcon.kt
    ├── SandboxTopBar.kt
    └── EmptyState.kt

sandbox/src/main/
├── AndroidManifest.xml       <service android:name=".RendererService"
│                                      android:isolatedProcess="true"
│                                      android:process=":renderer"
│                                      android:exported="false" />
├── aidl/dev/ikram/sandbox/renderer/
│   └── IRenderer.aidl
└── java/dev/ikram/sandbox/renderer/
    ├── RendererService.kt    Binder implementation; validates all inputs
    ├── ImageDecoderImpl.kt   BitmapFactory from FD, enforces max dimensions, downsamples
    ├── PdfRendererImpl.kt    PdfRenderer page → bitmap at requested width
    ├── SafeImageEncoder.kt   Decode → re encode JPEG/PNG, metadata gone by construction
    ├── SafePdfBuilder.kt     Render each page → new PdfDocument from bitmaps
    └── SharedMemoryBitmap.kt Moves pixels back through SharedMemory (binder 1 MB limit)
└── client side (in sandbox module, used by app):
    └── SandboxRenderer.kt    Binds service, exposes suspend funs, restarts on crash,
                              timeouts every call, treats renderer death as "file unsafe"
```

## The isolated renderer (most important piece)

* The isolated process cannot open files, so the app opens the vault file **read only** and passes a `ParcelFileDescriptor` through AIDL.
* Output pixels come back via `SharedMemory` (set read only before sending). Never send `Bitmap` objects through binder.
* Every call has a timeout (e.g. 10 s). If the renderer crashes or times out, show "This file couldn't be opened safely" and mark it in the vault. Never fall back to decoding in the main process.
* Enforce limits inside the service: max image pixels (e.g. 50 MP), max PDF pages rendered at once, max output size.

Sketch of `IRenderer.aidl`:

```
interface IRenderer {
    ImageResult decodeImage(in ParcelFileDescriptor fd, int maxWidth, int maxHeight);
    int pdfPageCount(in ParcelFileDescriptor fd);
    ImageResult renderPdfPage(in ParcelFileDescriptor fd, int pageIndex, int width);
    void safeCopyImage(in ParcelFileDescriptor input, in ParcelFileDescriptor output, int format);
    void safeCopyPdf(in ParcelFileDescriptor input, in ParcelFileDescriptor output);
}
```

`ImageResult` is a Parcelable holding a `SharedMemory`, width, height and pixel config. Output descriptors are created by the app in `share_tmp/` and passed write only.

## WebView hardening (WebViewHardening.kt)

* `allowFileAccess = false`, `allowContentAccess = false`
* `allowFileAccessFromFileURLs = false`, `allowUniversalAccessFromFileURLs = false`
* `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`
* Safe Browsing enabled (`WebSettingsCompat` / manifest meta data)
* `setSupportMultipleWindows(false)` in v1
* `shouldOverrideUrlLoading`: allow only `http` and `https`. Block `file:`, `content:`, `intent:`, `javascript:` from navigation, and never launch external apps for unknown schemes in v1.
* No `addJavascriptInterface`
* `onShowFileChooser` returns false in v1 (no uploads)
* `WebView.setDataDirectorySuffix` not needed (single process), but clear cache/cookies from Settings

## Download flow

1. `DownloadListener` fires → build `DownloadRequest`.
2. If "ask before download" is on, show `DownloadPromptSheet`. Pre classify by claimed MIME type for the prompt text, but final decision comes from sniffing.
3. `VaultDownloader` streams with OkHttp (cookies + user agent + referer from WebView) into `filesDir/vault/.partial/<uuid>`, computing SHA256 while streaming.
4. Sniff magic bytes → `DetectedType` → `TypePolicy`.
5. Atomic rename to `filesDir/vault/<uuid>.bin`, insert Room row.
6. If detected type disagrees with extension/claimed MIME, flag `isMismatch` and show `MismatchWarningDialog` on first open.
7. If auto scan is on, query VirusTotal by hash.

v1 skips `blob:` and `data:` downloads (show "This download type isn't supported yet").

## Viewing rules

| Category | Viewer | Where parsing happens |
|---|---|---|
| IMAGE | ImageViewerScreen | Isolated renderer |
| PDF | PdfViewerScreen | Isolated renderer (PdfRenderer) |
| VIDEO, AUDIO | Media3 ExoPlayer | ExoPlayer extractors in app (Kotlin, memory safe), codecs in system media process |
| TEXT | TextViewerScreen | App, plain text only, capped at e.g. 5 MB |
| ARCHIVE | Not openable in v1 (show details only) | n/a |
| BLOCKED / UNKNOWN | Never opened | n/a |

For video/audio, use only Media3's default extractors. Do not add FFmpeg or VLC extensions.

## Share rules

* **Safe copy (default):** images and PDFs rebuilt in the isolated renderer; video and audio re encoded with Media3 Transformer. Output goes to `share_tmp/`, shared through `FileProvider` with `FLAG_GRANT_READ_URI_PERMISSION`, deleted after 1 hour by `TempShareCleaner`.
* **Original:** behind an amber warning dialog. Disabled for BLOCKED and UNKNOWN.
* **Link:** shares `sourceUrl` as text.
* TEXT safe copy = re encode as UTF 8 plain text.

## Build phases

1. **Skeleton:** modules, version catalog, theme, navigation, empty screens, manifest locked down. CI style checks pass.
2. **filetype module:** sniffer + policy + full unit tests (do this before any downloading).
3. **Browser:** hardened WebView, address bar, back/forward, download interception.
4. **Vault:** downloader, Room, vault list, details sheet, delete.
5. **Isolated renderer:** AIDL, service, client, image viewer.
6. **PDF viewer** via renderer.
7. **Media viewers** (video, audio) and text viewer.
8. **Share flow:** safe copy for image, PDF, text; then video/audio via Transformer.
9. **Settings, biometric lock, auto delete, VirusTotal lookup.**
10. **Onboarding and explainer screen**, polish, dark mode pass.

Finish each phase with passing tests and a working debug build before starting the next.

## Commands

* Build: `./gradlew assembleDebug`
* Unit tests: `./gradlew test`
* Lint and static checks: `./gradlew lint ktlintCheck detekt`
* Single module tests: `./gradlew :core:filetype:test`

## Conventions

* One screen = `XScreen.kt` (stateless composable taking `UiState` + callbacks) + `XRoute` composable that wires the ViewModel.
* ViewModels expose a single `StateFlow<UiState>` and handle `onEvent(Event)`.
* Repositories return `Flow` or `suspend` functions; no Android UI types in `core`.
* No file path is ever built from a server supplied name. Vault paths are always UUIDs.
* Any change touching the manifest, WebView settings, FileProvider paths, or the renderer must be called out explicitly in the summary of the change.
