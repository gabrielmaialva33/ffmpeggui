# FFmpeg Android arm64 build

The app ships one standalone FFmpeg CLI executable at
`app/src/main/libs/arm64-v8a/libffmpeg.so`. The `.so` suffix makes Android's
native-library packaging extract the executable into `nativeLibraryDir`; the
file itself is an AArch64 PIE command-line program.

## Pinned inputs

- FFmpeg tag: `n8.1.2`
- FFmpeg commit: `38b88335f99e76ed89ff3c93f877fdefce736c13`
- Android NDK: r29 (`29.0.14206865`)
- Minimum Android API used by the native build: 26
- ABI: `arm64-v8a`
- Local MediaCodec decoder drain patch:
  `patches/0001-mediacodec-bound-decoder-drain.patch`
  (SHA-256 `3904661ebc989d4b04b8358c498c66cade7f88050fa122cc88563afc0afae49c`)
- Local NDK encoder global-header patch:
  `patches/0002-mediacodec-avoid-ndk-encoder-flush.patch`
  (SHA-256 `24b764eca149529d006d2802e10370e03d17f2def293b5a5cc52d61947e71099`)
- Local MediaCodec encoder input-validation patch:
  `patches/0003-mediacodec-validate-encoder-input.patch`
  (SHA-256 `06affdce42468b7767acf98928ab4372e7332bcc925f39ec4ff61fc243f47488`)

The build uses only FFmpeg's LGPL-compatible built-in components and Android
system libraries. GPL and nonfree external codec libraries are excluded. The
default internal codecs, demuxers, muxers, protocols, bitstream filters, and
filters remain enabled. JNI and MediaCodec support are explicitly enabled for
the Android hardware codec path.

## Build

From the repository root:

```sh
./native/ffmpeg/build-android-arm64.sh
```

The default NDK path is
`/opt/homebrew/share/android-commandlinetools/ndk/29.0.14206865`. Override it
when needed:

```sh
ANDROID_NDK_ROOT=/absolute/path/to/android-ndk-r29 \
  ./native/ffmpeg/build-android-arm64.sh
```

Sources and intermediate objects are kept under the ignored
`native/ffmpeg/.build/` directory. The script verifies the checked-out commit,
checks the exact NDK revision, verifies all three local patch checksums and
preimages, and limits their changes to
`libavcodec/mediacodecdec_common.c` and `libavcodec/mediacodecenc.c`. It fixes
the build locale/time/source timestamp, starts from a clean out-of-tree build,
strips the executable, copies it to the app's arm64 native library directory,
and prints its SHA-256 digest. Stable tool names and a logical install prefix
keep host checkout paths out of FFmpeg's embedded configure string.

FFmpeg 8.1.2 can return `EAGAIN` after a MediaCodec output-format event even
though decoder drain has started. The ffmpeg CLI correctly treats `EAGAIN`
during flush as a decoder contract violation and aborts. The local patch keeps
dequeueing transient MediaCodec events for at most four blocking attempts
(four seconds total). A codec that exhausts the limit returns a decoding error
and ends its drain, keeping the failure bounded. FFmpeg master commit
`03dc244a693ce639cebf82f7bae112fb75580919`, checked on 2026-08-12, still has
the same drain behavior and contains no equivalent fix.

FFmpeg 8.1.2's synchronous NDK global-header probe queues a dummy frame and
EOS, then flushes the encoder before the first real frame. Android defines
flush recovery for decoders; encoder behavior is undefined. On the Pixel 10
Pro XL / Tensor G5, the next input reaches Codec2 status 95 and FFmpeg returns
`AVERROR_EXTERNAL`. Patch 0002 routes synchronous NDK encoding through the
existing `extract_extradata` bitstream-filter path, matching the asynchronous
NDK path and avoiding the encoder flush. See Android's
[`MediaCodec.flush()` contract](https://developer.android.com/reference/android/media/MediaCodec#flush()).

Patch 0003 checks the NDK encoder input-buffer pointer and capacity, propagates
queue failures with their raw status, and records EOS only after MediaCodec
accepts it. The patch preserves the encoder's existing payload-size behavior.

The linker flags set both maximum and common ELF page size to 16KB. This keeps
the packaged executable compatible with Android devices using either 4KB or
16KB memory pages.

## Verify

```sh
./native/ffmpeg/verify-android-arm64.sh
```

The verifier checks the AArch64 ELF header, every load segment's 16KB
alignment, the compiled MediaCodec component names, and the SHA-256 digest.
With `ANDROID_SERIAL`, it validates and uses that exact authorized device even
when several devices are attached. With one authorized device and no explicit
serial, it selects that device automatically. It copies the executable only to
`/data/local/tmp`, then checks the runtime version, encoders, decoders,
formats, filters, protocols, and hardware accelerators. The verifier leaves
installed apps and app/user data untouched.

Actual hardware codec availability is device-specific. Confirm the codecs
exposed by Android with `adb shell dumpsys media.codec` and test the selected
codec with representative input. Snapdragon 8 Elite and Tensor G5 devices can
advertise different codec names, profiles, levels, color formats, and limits.

For a repeatable on-device encode/decode smoke test:

```sh
./native/ffmpeg/smoke-test-mediacodec.sh
```

The smoke test requires H.264 and HEVC MediaCodec encode/decode to complete,
prints the actual Android codec components selected, and rejects Android's
`c2.android.*` reference software encoders for those required codecs. VP9 and
AV1 are tested and reported as optional because current SoCs can expose their
hardware decoders alongside Android reference software encoders. All
temporary files stay under `/data/local/tmp` and are removed when the test
exits. Set `ANDROID_SERIAL` when more than one adb device is attached; run the
same test once on the Snapdragon 8 Elite device and once on the Tensor G5
device.

Run the short-stream drain regression matrix separately:

```sh
./native/ffmpeg/test-short-mediacodec-drain.sh
```

It hardware-encodes and hardware-decodes H.264 and HEVC streams containing
1, 3, 9, 10, 11, and 30 frames. Every case must exit successfully, use vendor
hardware components in both directions, and report the exact expected decoded
frame count. The test catches the former `ffmpeg_dec.c` drain assertion at its
real CLI/MediaCodec seam.

Run the encoder input and surface-chain regression matrix with:

```sh
./native/ffmpeg/test-mediacodec-encoder-matrix.sh
```

For H.264 and HEVC, it covers synchronous and asynchronous NDK operation,
`yuv420p` and `nv12`, 640x360 and the unaligned 638x358 shape, and verifies
30/30 frames through hardware decode. It also covers a raw H.264 elementary
stream and synchronous/asynchronous MediaCodec surface transcoding
from H.264 to HEVC. Encoder locks, hardware decoder selection, input-buffer
errors, dimensions, and decoded frame counts are checked in every applicable
case.

Run the pixel-quality regression with:

```sh
./native/ffmpeg/test-mediacodec-quality.sh
```

This test generates a deterministic H.264 fixture at the deliberately
unaligned 1228x720 shape, transcodes it through MediaCodec hardware decode and
planar HEVC/AV1 MediaCodec encode, then pulls the results for host-side SSIM
and PSNR analysis. HEVC is required. AV1 skips only when FFmpeg explicitly
reports that Android has no `video/av01` MediaCodec encoder; every other AV1
failure fails the test. Set `REQUIRE_AV1=1` on a device known to expose a
hardware AV1 encoder, as used for Pixel validation:

```sh
REQUIRE_AV1=1 ANDROID_SERIAL=<device-serial> \
  ./native/ffmpeg/test-mediacodec-quality.sh
```

The default acceptance thresholds are SSIM 0.95 and PSNR 30 dB; they detect
horizontal row displacement and green bands even when dimensions, timestamps,
and frame count remain valid. The host FFmpeg must provide `libx264`, `ssim`,
and `psnr`.

## Lock a MediaCodec encoder component

Android can expose a hardware implementation and a reference software
implementation for the same MIME type. Query the device first with
`HardwareCodecSelector`, then pass the returned encoder component to FFmpeg's
private `-codec_name` option:

```text
-c:v h264_mediacodec -codec_name c2.qti.avc.encoder -ndk_codec 1
-c:v hevc_mediacodec -codec_name c2.qti.hevc.encoder -ndk_codec 1
```

The `c2.qti.*` names above are examples from the tested Snapdragon device.
The tested Tensor G5 exposes `c2.google.avc.encoder` and
`c2.google.hevc.encoder`; its matching decoders also use `c2.google.*`. Other
vendors expose different names. The read-only helper at
`app/src/main/java/com/silentlexx/ffmpeggui_os/model/HardwareCodecSelector.java`
uses Android's hardware flag on API 29+ and can also check whether a component
advertises the requested width, height, frame rate, and planar/flexible YUV420
input. A semiplanar-only component is rejected before command execution.

Built-in H.264, HEVC, VP9, and AV1 templates expose a separate Android system
software fallback. The app enumerates components marked software-only (or
known reference names on older APIs), locks the selected `c2.android.*` or
equivalent component, and removes its internal policy option immediately
before process execution. The same AIO binary therefore supports vendor
hardware and Android system software without SoC-specific builds.

FFmpeg 8.1.2 exposes `-codec_name` for MediaCodec encoders. Its MediaCodec
decoder wrapper lets Android select the component; use `-hwaccel mediacodec`
before `-i` to select that decoder path. The smoke test prints the component
that Android actually selected for both directions and then repeats each
encoder test locked to that exact component.

## Device-tested baseline and path-sanitized artifact

On 2026-08-12 JST, two consecutive clean builds with compiler prefix mapping
produced the same final checked-in artifact:

```text
SHA-256 785431d8f3fa65371c4ebf208e059b42a01d9de90086d858035675a9a6e73663
Size    21,593,576 bytes
```

The verifier pins this digest and rejects host checkout paths in the ELF.
Earlier device testing used the same pinned FFmpeg commit, configure feature
set, and three patch contents before compiler prefix mapping was added:

```text
SHA-256 b06219d3f85e32be5fa2cb843eba028ef9001ae3df2203f183e0bbca92e0e2d5
Size    21,593,672 bytes
```

Compiler prefix maps replace local checkout paths in compiled strings, which
changes the binary digest while leaving codec behavior unchanged. The device
results below record the `b06219d3...` artifact that actually ran on the
Snapdragon and Tensor devices. The modern2 build and signing record for the
path-sanitized artifact is in
[`verification/modern2-release-2026-08-12.log`](verification/modern2-release-2026-08-12.log).

`verify-android-arm64.sh` confirmed an AArch64 PIE executable, 16KB alignment
for every ELF LOAD segment, Android/NDK-only dynamic dependencies, FFmpeg
8.1.2 runtime execution, and the common codec/format/filter/protocol set. The
Snapdragon 8 Elite testing selected Qualcomm `c2.qti.*` hardware components
for H.264/HEVC encode and decode. AV1 decode selected `c2.qti.av1.decoder`,
while AV1 encode selected Android's `c2.android.av1.encoder` software
component. Tensor G5 testing selected `c2.google.*` hardware components for
H.264, HEVC, and AV1 encode and decode. Both devices passed the earlier
synchronous/asynchronous encoder matrix, including 638x358, raw H.264, and
hardware H.264-to-HEVC surface chains at 30/30 frames. A later full-length
1228x720 input exposed a Tensor G5 flexible-NV12 ByteBuffer stride mismatch:
Android negotiated 1248x736 while FFmpeg wrote a tightly packed layout. The
app now forces planar `yuv420p` for every MediaCodec encoder command. Pixel
quality validation with the same unaligned shape produced HEVC SSIM 0.999304 /
PSNR 52.728940 dB and AV1 SSIM 0.999240 / PSNR 52.272864 dB.

The Tensor G5 short-stream matrix also produced exact decoded counts for 1, 3,
9, 10, 11, and 30 frames in both H.264 and HEVC. Device records are in
[`verification/SM8750P-2026-08-12.log`](verification/SM8750P-2026-08-12.log)
and
[`verification/Tensor-G5-Pixel-10-Pro-XL-2026-08-12.log`](verification/Tensor-G5-Pixel-10-Pro-XL-2026-08-12.log).
The Tensor pixel-quality record is in
[`verification/Tensor-G5-planar-quality-2026-08-12.log`](verification/Tensor-G5-planar-quality-2026-08-12.log).
The Lenovo system-software matrix locked H.264, HEVC, VP9, and AV1 to their
respective `c2.android.*.encoder` components and produced 12/12 frames for all
four; see
[`verification/SM8750P-system-software-2026-08-12.log`](verification/SM8750P-system-software-2026-08-12.log).
