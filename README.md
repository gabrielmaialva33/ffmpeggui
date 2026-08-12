# FFmpegGUI OS

FFmpegGUI OS is a community-maintained Android edition of
[FFmpegGUI](https://play.google.com/store/apps/details?id=com.silentlexx.ffmpeggui).
It provides a direct FFmpeg command workflow for Android and runs independently
of Google Play services.

## Android and FFmpeg runtime

The application supports Android 8.0/API 26 and later, targets API 35, and is
packaged for `arm64-v8a`. It ships a reproducible standalone FFmpeg 8.1.2 CLI
with LGPL-compatible built-in components and Android MediaCodec support. Its
AArch64 PIE load segments are aligned to 16KB for devices using either 4KB or
16KB memory pages.

H.264, HEVC, VP9, and AV1 MediaCodec encoder commands are resolved at runtime.
Hardware presets query Android's codec inventory, select a component marked as
hardware, check the requested video shape when dimensions are available,
replace any model- or preset-supplied `-codec_name`, and lock FFmpeg to that
component. Every MediaCodec encoder command is normalized to planar
`-pix_fmt yuv420p` before execution. This avoids vendor-specific flexible/NV12
ByteBuffer stride and slice-height mismatches observed on Tensor G5. Component
selection requires advertised planar or flexible YUV420 support and fails
closed for a semiplanar-only encoder. Separate
system-software fallback presets select components marked
software-only, such as `c2.android.*`, and keep that policy through preview,
Console, batch processing, and final execution. The app validates the chosen
component again immediately before starting FFmpeg and removes its internal
policy marker before `ProcessBuilder` receives the command.
MediaCodec decoding uses `-hwaccel mediacodec` before `-i`; Android selects the
decoder component. The diagnostic inventory also reports H.264, HEVC, VP9,
and AV1 encoder/decoder availability. Selection accepts any vendor component
that satisfies Android's runtime flags and capabilities, including Exynos and
MediaTek component families; known reference software names remain assigned
to the software fallback policy.

The complete native build, reproducibility, verification, and device-test
instructions are in [`native/ffmpeg/README.md`](native/ffmpeg/README.md).
A functionally equivalent three-patch native artifact completed hardware-codec
validation on both target SoC families: `c2.qti.*` H.264/HEVC on Snapdragon 8
Elite and `c2.google.*` H.264/HEVC/AV1 on Tensor G5. The checked-in artifact is
a clean rebuild from the same pinned FFmpeg source and patches with compiler
path mapping added to remove host checkout paths.

## Direct filesystem access

The manifest declares `MANAGE_EXTERNAL_STORAGE` to preserve the application's
absolute-path, all-files workflow. On Android 11/API 30 and later, the app
opens its system **All files access** settings page and the user grants access
explicitly. The manifest also gates `WRITE_EXTERNAL_STORAGE` through API 29,
`READ_EXTERNAL_STORAGE` through API 32, and declares the Android 13+
`READ_MEDIA_*` permissions. App-specific startup initialization remains usable
while permission is pending. After the user returns from Android's all-files
settings, a pending Open/Open second input/Save action resumes exactly once.
All-files access is sensitive and its distribution must follow the rules of
the target app store.

## Gemini command planning

The AI button offers two command-planning workflows:

- **BYOK one-shot generation** sends the redacted prompt to a configurable
  OpenAI-compatible `/chat/completions` endpoint. The language-model presets
  are `gemini-3.6-flash` (default), `gemini-3.5-flash`,
  `gemini-3.5-flash-lite`, `gemini-3.1-pro-preview`,
  `gemini-3.1-flash-lite`, and `gemini-3-flash-preview`; a custom model ID is
  also supported. The API key is encrypted with an AES-GCM key held by Android
  Keystore, and the resulting ciphertext is stored under the app's no-backup
  directory. HTTPS is the default transport. Saving a local HTTP proxy requires
  an explicit warning acknowledgement tied to that exact endpoint because an
  unencrypted connection exposes the key and prompt to network observers.
- **Offline copy/paste** copies the same prompt for use in an external Gemini
  client, then accepts the returned JSON object or plain FFmpeg command through
  the editable paste dialog or the direct **Read result from clipboard** action.

BYOK uses `{{INPUT_1}}` and `{{OUTPUT_1}}`. Every copied offline prompt receives
a random 128-bit session suffix, producing numbered placeholders such as
`{{INPUT_1_SID_...}}` and `{{OUTPUT_1_SID_...}}`. Their corresponding absolute
paths are stored only in the app's no-backup directory for up to 24 hours. The
binding survives UI file changes, is consumed after one successful result, and
rejects a replay or a result copied from another session. Input and output
absolute paths and basenames remain outside copied prompts and API messages.
Input probing contributes only bounded, control-character-cleaned technical
metadata such as container, extension, size, duration, stream codecs,
dimensions, and frame rate. Runtime FFmpeg capabilities and Android codec
diagnostics are included without the selected media identity.

Model output is constrained to a single command and validated before the two
known placeholders are restored. Unknown or repeated placeholders, additional
inputs/outputs, network and pipe protocols, shell operators, and side-file
options are rejected. A validated result opens in the Console for review;
execution begins only after the user presses **Run**.

## Build the Android app

Requirements:

- JDK 17
- Android SDK platform 36 and the matching command-line/build tools
- The included Gradle wrapper (Gradle 9.5.0 and Android Gradle Plugin 9.2.1)

From the repository root on macOS with the locally installed SDK path used by
this project:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
export ANDROID_HOME="$ANDROID_SDK_ROOT"

./gradlew :app:assembleDebug --no-daemon
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Rebuilding the bundled FFmpeg CLI requires Android NDK r29. The native build
script verifies the pinned FFmpeg commit and NDK revision before replacing the
arm64 artifact:

```sh
ANDROID_NDK_ROOT=/opt/homebrew/share/android-commandlinetools/ndk/29.0.14206865 \
  ./native/ffmpeg/build-android-arm64.sh
```

## Test and verify

Run unit tests, rebuild the APK, and run Android lint together:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
export ANDROID_HOME="$ANDROID_SDK_ROOT"

./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon
./native/ffmpeg/verify-android-arm64.sh
```

MediaCodec behavior is device-specific. The checked-in Snapdragon 8 Elite and
Tensor G5 logs record the selected hardware components, smoke tests,
short-stream drain coverage, and the current encoder regression matrix. Set
`ANDROID_SERIAL` whenever several adb devices are attached:

```sh
ANDROID_SERIAL=<device-serial> ./native/ffmpeg/verify-android-arm64.sh
ANDROID_SERIAL=<device-serial> ./native/ffmpeg/smoke-test-mediacodec.sh
ANDROID_SERIAL=<device-serial> ./native/ffmpeg/test-short-mediacodec-drain.sh
ANDROID_SERIAL=<device-serial> ./native/ffmpeg/test-mediacodec-encoder-matrix.sh
REQUIRE_AV1=1 ANDROID_SERIAL=<device-serial> ./native/ffmpeg/test-mediacodec-quality.sh
```

The quality regression uses an unaligned 1228x720 fixture and verifies decoded
pixels with host-side SSIM/PSNR. On the tested Pixel 10 Pro XL, planar HEVC
measured SSIM 0.999304 / PSNR 52.728940 dB and planar AV1 measured SSIM
0.999240 / PSNR 52.272864 dB.

The device records are
[`SM8750P-2026-08-12.log`](native/ffmpeg/verification/SM8750P-2026-08-12.log)
and
[`Tensor-G5-Pixel-10-Pro-XL-2026-08-12.log`](native/ffmpeg/verification/Tensor-G5-Pixel-10-Pro-XL-2026-08-12.log).
The Tensor planar quality regression is recorded in
[`Tensor-G5-planar-quality-2026-08-12.log`](native/ffmpeg/verification/Tensor-G5-planar-quality-2026-08-12.log).
The four-codec Android system-software check is recorded in
[`SM8750P-system-software-2026-08-12.log`](native/ffmpeg/verification/SM8750P-system-software-2026-08-12.log).
The current path-sanitized modern2 build and signing gate is recorded in
[`modern2-release-2026-08-12.log`](native/ffmpeg/verification/modern2-release-2026-08-12.log).

## Licensing

The packaged FFmpeg executable is built from FFmpeg `n8.1.2` under LGPL 2.1 or
later. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) and the bundled
full license text in
[`LICENSE-FFMPEG-LGPL-2.1.txt`](LICENSE-FFMPEG-LGPL-2.1.txt).
Those files cover the bundled FFmpeg executable only. The application source
retains the upstream repository's unspecified licensing status; this fork
adds no separate application-code license grant.
