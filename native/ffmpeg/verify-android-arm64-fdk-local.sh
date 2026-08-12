#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
readonly SCRIPT_DIR
readonly LOCAL_ROOT="$SCRIPT_DIR/.build/nonredistributable"
readonly BINARY="${1:-$LOCAL_ROOT/DO-NOT-DISTRIBUTE/libffmpeg-fdk.so}"
readonly CONFIG_MAK="${FDK_CONFIG_MAK:-$LOCAL_ROOT/build/ffmpeg/ffbuild/config.mak}"
readonly NDK_ROOT="${ANDROID_NDK_ROOT:-/opt/homebrew/share/android-commandlinetools/ndk/29.0.14206865}"

case "$(uname -s)" in
    Darwin) readonly NDK_HOST="darwin-x86_64" ;;
    Linux) readonly NDK_HOST="linux-x86_64" ;;
    *) echo "Unsupported verification host: $(uname -s)" >&2; exit 1 ;;
esac
readonly READELF="$NDK_ROOT/toolchains/llvm/prebuilt/$NDK_HOST/bin/llvm-readelf"

if [[ ! -x "$READELF" || ! -f "$BINARY" || ! -f "$CONFIG_MAK" ]]; then
    echo "The local FDK binary, its config.mak, and pinned llvm-readelf are required." >&2
    exit 1
fi

temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/ffmpeggui-fdk-verify.XXXXXX")"
cleanup() {
    rm -rf -- "$temporary_directory"
}
trap cleanup EXIT
readonly STRINGS_FILE="$temporary_directory/strings.txt"
readonly ELF_HEADER_FILE="$temporary_directory/elf-header.txt"
readonly PROGRAM_HEADERS_FILE="$temporary_directory/program-headers.txt"
readonly DYNAMIC_FILE="$temporary_directory/dynamic.txt"

file_description="$(file "$BINARY")"
echo "$file_description"
strings -n 3 "$BINARY" >"$STRINGS_FILE"
"$READELF" -h "$BINARY" >"$ELF_HEADER_FILE"
"$READELF" -lW "$BINARY" >"$PROGRAM_HEADERS_FILE"
"$READELF" -dW "$BINARY" >"$DYNAMIC_FILE"

if [[ "$file_description" != *"pie executable"* ]] ||
        ! grep -Eq 'Type:[[:space:]]+DYN ' "$ELF_HEADER_FILE" ||
        ! grep -Eq 'Machine:[[:space:]]+AArch64' "$ELF_HEADER_FILE"; then
    echo "Expected an Android AArch64 position-independent ELF executable." >&2
    exit 1
fi
if ! awk '
    $1 == "LOAD" {
        count++
        if ($NF != "0x4000") bad = 1
    }
    END { exit(count > 0 && !bad ? 0 : 1) }
' "$PROGRAM_HEADERS_FILE"; then
    echo "Every local FDK ELF LOAD segment must use 16KB alignment." >&2
    exit 1
fi

unexpected_libraries="$(awk '
    /\(NEEDED\)/ {
        library = $NF
        gsub(/^\[/, "", library)
        gsub(/\]$/, "", library)
        if (library != "libm.so" &&
            library != "libandroid.so" &&
            library != "libcamera2ndk.so" &&
            library != "libmediandk.so" &&
            library != "libz.so" &&
            library != "libdl.so" &&
            library != "libc.so") {
            print library
        }
    }
' "$DYNAMIC_FILE")"
if [[ -n "$unexpected_libraries" ]]; then
    echo "Unexpected local FDK dynamic libraries:" >&2
    printf '%s\n' "$unexpected_libraries" >&2
    exit 1
fi
if grep -En '/Users/|Documents/Codex|native/ffmpeg/\.build|/opt/(homebrew|zerobrew)' \
        "$STRINGS_FILE"; then
    echo "The local FDK ELF contains a host build path." >&2
    exit 1
fi

for required_flag in \
        --enable-gpl --enable-nonfree --enable-libfdk-aac --disable-version3; do
    if ! grep -F -- "$required_flag" "$STRINGS_FILE" >/dev/null; then
        echo "Missing required local FDK configure flag: $required_flag" >&2
        exit 1
    fi
done
for component in \
        mpdecimate drawtext ass subtitles libaribcaption \
        libx264 libx265 libvpx-vp9 libaom-av1 libfdk_aac \
        h264_mediacodec hevc_mediacodec vp9_mediacodec av1_mediacodec; do
    if ! grep -Fx -- "$component" "$STRINGS_FILE" >/dev/null; then
        echo "Required local FDK runtime component string is missing: $component" >&2
        exit 1
    fi
done

for required_config in \
        CONFIG_GPL CONFIG_NONFREE CONFIG_LIBFDK_AAC \
        CONFIG_LIBFDK_AAC_ENCODER CONFIG_LIBFDK_AAC_DECODER \
        CONFIG_MPDECIMATE_FILTER CONFIG_PIXELUTILS \
        CONFIG_LIBASS CONFIG_ASS_FILTER CONFIG_SUBTITLES_FILTER \
        CONFIG_LIBFREETYPE CONFIG_LIBFRIBIDI CONFIG_LIBHARFBUZZ \
        CONFIG_DRAWTEXT_FILTER CONFIG_LIBARIBCAPTION \
        CONFIG_LIBARIBCAPTION_DECODER CONFIG_LIBX264 \
        CONFIG_LIBX264_ENCODER CONFIG_LIBX265 CONFIG_LIBX265_ENCODER \
        CONFIG_LIBVPX CONFIG_LIBVPX_VP9_ENCODER CONFIG_LIBAOM \
        CONFIG_LIBAOM_AV1_ENCODER; do
    if ! grep -Fx -- "${required_config}=yes" "$CONFIG_MAK" >/dev/null; then
        echo "Required local FDK configuration is missing: $required_config" >&2
        exit 1
    fi
done
if grep -Fx -- 'CONFIG_VERSION3=yes' "$CONFIG_MAK" >/dev/null; then
    echo "The local FDK profile must keep FFmpeg version3 disabled." >&2
    exit 1
fi

if AIO_CONFIG_MAK="$CONFIG_MAK" \
        "$SCRIPT_DIR/verify-android-arm64-aio.sh" "$BINARY" \
        >"$temporary_directory/standard-verifier.log" 2>&1; then
    echo "The standard redistributable verifier unexpectedly accepted FDK AAC." >&2
    exit 1
fi

actual_sha256="$(shasum -a 256 "$BINARY" | awk '{ print $1 }')"
actual_size="$(wc -c <"$BINARY" | tr -d '[:space:]')"
if [[ -n "${EXPECTED_FDK_SHA256:-}" && "$actual_sha256" != "$EXPECTED_FDK_SHA256" ]]; then
    echo "Local FDK FFmpeg checksum mismatch." >&2
    echo "Expected $EXPECTED_FDK_SHA256, got $actual_sha256" >&2
    exit 1
fi

echo "Verified local nonredistributable FDK technical profile."
echo "SHA-256: $actual_sha256"
echo "Size: $actual_size bytes"
echo "DO NOT DISTRIBUTE. The FDK notice grants no patent license."
