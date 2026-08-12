#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
readonly SCRIPT_DIR
PROJECT_ROOT="$(CDPATH='' cd -- "$SCRIPT_DIR/../.." && pwd)"
readonly PROJECT_ROOT

readonly FFMPEG_TAG="n8.1.2"
readonly FFMPEG_COMMIT="38b88335f99e76ed89ff3c93f877fdefce736c13"
readonly FFMPEG_REPOSITORY="https://github.com/FFmpeg/FFmpeg.git"
readonly MEDIACODEC_DRAIN_PATCH="$SCRIPT_DIR/patches/0001-mediacodec-bound-decoder-drain.patch"
readonly MEDIACODEC_DRAIN_PATCH_SHA256="3904661ebc989d4b04b8358c498c66cade7f88050fa122cc88563afc0afae49c"
readonly MEDIACODEC_ENCODER_PATCH="$SCRIPT_DIR/patches/0002-mediacodec-avoid-ndk-encoder-flush.patch"
readonly MEDIACODEC_ENCODER_PATCH_SHA256="24b764eca149529d006d2802e10370e03d17f2def293b5a5cc52d61947e71099"
readonly MEDIACODEC_INPUT_PATCH="$SCRIPT_DIR/patches/0003-mediacodec-validate-encoder-input.patch"
readonly MEDIACODEC_INPUT_PATCH_SHA256="06affdce42468b7767acf98928ab4372e7332bcc925f39ec4ff61fc243f47488"
readonly ANDROID_API="${ANDROID_API:-26}"
readonly NDK_ROOT="${ANDROID_NDK_ROOT:-/opt/homebrew/share/android-commandlinetools/ndk/29.0.14206865}"
readonly EXPECTED_NDK_REVISION="29.0.14206865"
readonly WORK_ROOT="$SCRIPT_DIR/.build"
readonly SOURCE_DIR="$WORK_ROOT/source"
readonly BUILD_DIR="$WORK_ROOT/build-arm64-v8a"
readonly OUTPUT_FILE="$PROJECT_ROOT/app/src/main/libs/arm64-v8a/libffmpeg.so"
readonly REPRODUCIBLE_SOURCE_PREFIX="/ffmpeg-8.1.2-src"

case "$(uname -s)" in
    Darwin) readonly NDK_HOST="darwin-x86_64" ;;
    Linux) readonly NDK_HOST="linux-x86_64" ;;
    *)
        echo "Unsupported build host: $(uname -s)" >&2
        exit 1
        ;;
esac

readonly TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/$NDK_HOST"
readonly CC="$TOOLCHAIN/bin/aarch64-linux-android${ANDROID_API}-clang"

if [[ ! -x "$CC" ]]; then
    echo "Android NDK clang wrapper was not found: $CC" >&2
    echo "Set ANDROID_NDK_ROOT to an Android NDK r29 installation." >&2
    exit 1
fi

if ! grep -Eq "^Pkg.Revision[[:space:]]*=[[:space:]]*${EXPECTED_NDK_REVISION}[[:space:]]*$" \
        "$NDK_ROOT/source.properties"; then
    echo "Android NDK revision mismatch; expected $EXPECTED_NDK_REVISION." >&2
    exit 1
fi

if [[ -d "$SOURCE_DIR/.git" ]]; then
    git -C "$SOURCE_DIR" fetch --depth=1 origin "refs/tags/$FFMPEG_TAG:refs/tags/$FFMPEG_TAG"
else
    mkdir -p "$WORK_ROOT"
    git clone --filter=blob:none --no-checkout --depth=1 --branch "$FFMPEG_TAG" \
        "$FFMPEG_REPOSITORY" "$SOURCE_DIR"
fi

git -C "$SOURCE_DIR" checkout --detach --force "$FFMPEG_COMMIT"
actual_commit="$(git -C "$SOURCE_DIR" rev-parse HEAD)"
if [[ "$actual_commit" != "$FFMPEG_COMMIT" ]]; then
    echo "FFmpeg source mismatch: expected $FFMPEG_COMMIT, got $actual_commit" >&2
    exit 1
fi

if [[ ! -f "$MEDIACODEC_DRAIN_PATCH" ]]; then
    echo "Required MediaCodec drain patch was not found: $MEDIACODEC_DRAIN_PATCH" >&2
    exit 1
fi

actual_patch_sha256="$(shasum -a 256 "$MEDIACODEC_DRAIN_PATCH" | awk '{ print $1 }')"
if [[ "$actual_patch_sha256" != "$MEDIACODEC_DRAIN_PATCH_SHA256" ]]; then
    echo "MediaCodec drain patch checksum mismatch." >&2
    echo "Expected $MEDIACODEC_DRAIN_PATCH_SHA256, got $actual_patch_sha256" >&2
    exit 1
fi

if [[ ! -f "$MEDIACODEC_ENCODER_PATCH" ]]; then
    echo "Required MediaCodec encoder patch was not found: $MEDIACODEC_ENCODER_PATCH" >&2
    exit 1
fi

actual_encoder_patch_sha256="$(shasum -a 256 "$MEDIACODEC_ENCODER_PATCH" | awk '{ print $1 }')"
if [[ "$actual_encoder_patch_sha256" != "$MEDIACODEC_ENCODER_PATCH_SHA256" ]]; then
    echo "MediaCodec encoder patch checksum mismatch." >&2
    echo "Expected $MEDIACODEC_ENCODER_PATCH_SHA256, got $actual_encoder_patch_sha256" >&2
    exit 1
fi

if [[ ! -f "$MEDIACODEC_INPUT_PATCH" ]]; then
    echo "Required MediaCodec input patch was not found: $MEDIACODEC_INPUT_PATCH" >&2
    exit 1
fi

actual_input_patch_sha256="$(shasum -a 256 "$MEDIACODEC_INPUT_PATCH" | awk '{ print $1 }')"
if [[ "$actual_input_patch_sha256" != "$MEDIACODEC_INPUT_PATCH_SHA256" ]]; then
    echo "MediaCodec input patch checksum mismatch." >&2
    echo "Expected $MEDIACODEC_INPUT_PATCH_SHA256, got $actual_input_patch_sha256" >&2
    exit 1
fi

if [[ -n "$(git -C "$SOURCE_DIR" status --short)" ]]; then
    echo "FFmpeg source is not clean after pinned checkout." >&2
    git -C "$SOURCE_DIR" status --short >&2
    exit 1
fi

git -C "$SOURCE_DIR" apply --check "$MEDIACODEC_DRAIN_PATCH"
git -C "$SOURCE_DIR" apply "$MEDIACODEC_DRAIN_PATCH"
git -C "$SOURCE_DIR" apply --check "$MEDIACODEC_ENCODER_PATCH"
git -C "$SOURCE_DIR" apply "$MEDIACODEC_ENCODER_PATCH"
git -C "$SOURCE_DIR" apply --check "$MEDIACODEC_INPUT_PATCH"
git -C "$SOURCE_DIR" apply "$MEDIACODEC_INPUT_PATCH"
git -C "$SOURCE_DIR" diff --check

patched_files="$(git -C "$SOURCE_DIR" diff --name-only)"
expected_patched_files="$(printf '%s\n' \
    'libavcodec/mediacodecdec_common.c' \
    'libavcodec/mediacodecenc.c')"
if [[ "$patched_files" != "$expected_patched_files" ]]; then
    echo "Unexpected files changed by MediaCodec patches:" >&2
    printf '%s\n' "$patched_files" >&2
    exit 1
fi

export LC_ALL=C
export TZ=UTC
export SOURCE_DATE_EPOCH
SOURCE_DATE_EPOCH="$(git -C "$SOURCE_DIR" show -s --format=%ct "$FFMPEG_COMMIT")"
export PATH="$TOOLCHAIN/bin:$PATH"
export CFLAGS="-ffile-prefix-map=$SOURCE_DIR=$REPRODUCIBLE_SOURCE_PREFIX -fmacro-prefix-map=$SOURCE_DIR=$REPRODUCIBLE_SOURCE_PREFIX -fdebug-prefix-map=$SOURCE_DIR=$REPRODUCIBLE_SOURCE_PREFIX"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR" "$(dirname -- "$OUTPUT_FILE")"

cd "$BUILD_DIR"

"$SOURCE_DIR/configure" \
    --prefix=/ffmpeggui/ffmpeg-8.1.2 \
    --target-os=android \
    --arch=aarch64 \
    --cpu=armv8-a \
    --enable-cross-compile \
    --cc="aarch64-linux-android${ANDROID_API}-clang" \
    --cxx="aarch64-linux-android${ANDROID_API}-clang++" \
    --ar=llvm-ar \
    --as="aarch64-linux-android${ANDROID_API}-clang" \
    --nm=llvm-nm \
    --ranlib=llvm-ranlib \
    --strip=llvm-strip \
    --pkg-config=/usr/bin/false \
    --disable-autodetect \
    --disable-doc \
    --disable-debug \
    --disable-ffplay \
    --disable-ffprobe \
    --disable-shared \
    --enable-static \
    --enable-pic \
    --enable-jni \
    --enable-mediacodec \
    --enable-zlib \
    --enable-pthreads \
    --enable-network \
    --enable-neon \
    --disable-symver \
    --extra-cflags="-O3 -fPIC -fvisibility=hidden" \
    --extra-ldflags="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"

if command -v gmake >/dev/null 2>&1; then
    make_command="gmake"
else
    make_command="make"
fi

if command -v nproc >/dev/null 2>&1; then
    jobs="$(nproc)"
elif command -v sysctl >/dev/null 2>&1; then
    jobs="$(sysctl -n hw.logicalcpu)"
else
    jobs="4"
fi

"$make_command" -j"$jobs" ffmpeg
llvm-strip --strip-unneeded ffmpeg
install -m 0755 ffmpeg "$OUTPUT_FILE"

echo "Built $OUTPUT_FILE"
shasum -a 256 "$OUTPUT_FILE"
