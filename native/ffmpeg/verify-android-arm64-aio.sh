#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
readonly SCRIPT_DIR
readonly BINARY="${1:-$SCRIPT_DIR/.build/aio/staging/libffmpeg.so}"
readonly CONFIG_MAK="${AIO_CONFIG_MAK:-$SCRIPT_DIR/.build/aio/build/ffmpeg/ffbuild/config.mak}"
if [[ -n "${ANDROID_NDK_ROOT:-}" ]]; then
    ndk_root="$ANDROID_NDK_ROOT"
else
    ndk_root=""
    for sdk_root in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" \
            /opt/homebrew/share/android-commandlinetools; do
        if [[ -n "$sdk_root" && -d "$sdk_root/ndk/29.0.14206865" ]]; then
            ndk_root="$sdk_root/ndk/29.0.14206865"
            break
        fi
    done
fi
readonly NDK_ROOT="$ndk_root"

if [[ -z "$NDK_ROOT" ]] ||
        ! grep -Eq '^Pkg.Revision[[:space:]]*=[[:space:]]*29\.0\.14206865[[:space:]]*$' \
        "$NDK_ROOT/source.properties" 2>/dev/null; then
    echo "Android NDK 29.0.14206865 is required for AIO ELF verification." >&2
    echo "Set ANDROID_NDK_ROOT or install the pinned NDK under ANDROID_SDK_ROOT." >&2
    exit 1
fi

case "$(uname -s)" in
    Darwin) readonly NDK_HOST="darwin-x86_64" ;;
    Linux) readonly NDK_HOST="linux-x86_64" ;;
    *) echo "Unsupported verification host: $(uname -s)" >&2; exit 1 ;;
esac

readonly READELF="$NDK_ROOT/toolchains/llvm/prebuilt/$NDK_HOST/bin/llvm-readelf"
if [[ ! -x "$READELF" ]]; then
    echo "llvm-readelf was not found under the pinned Android NDK." >&2
    exit 1
fi
if [[ ! -f "$BINARY" ]]; then
    echo "AIO FFmpeg binary was not found: $BINARY" >&2
    exit 1
fi

temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/ffmpeggui-aio-verify.XXXXXX")"
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
    echo "Expected an AArch64 position-independent ELF executable." >&2
    cat "$ELF_HEADER_FILE" >&2
    exit 1
fi

if ! awk '
    $1 == "LOAD" {
        count++
        if ($NF != "0x4000") bad = 1
    }
    END { exit(count > 0 && !bad ? 0 : 1) }
' "$PROGRAM_HEADERS_FILE"; then
    echo "Every ELF LOAD segment must use 16KB alignment." >&2
    cat "$PROGRAM_HEADERS_FILE" >&2
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
    echo "Unexpected dynamic libraries:" >&2
    printf '%s\n' "$unexpected_libraries" >&2
    exit 1
fi

if grep -En '/Users/|Documents/Codex|native/ffmpeg/\.build|/opt/(homebrew|zerobrew)' \
        "$STRINGS_FILE"; then
    echo "AIO FFmpeg contains a host build path." >&2
    exit 1
fi

for required_flag in --enable-gpl --disable-version3 --disable-nonfree; do
    if ! grep -F -- "$required_flag" "$STRINGS_FILE" >/dev/null; then
        echo "Missing required standard-AIO configure flag: $required_flag" >&2
        exit 1
    fi
done
for forbidden_marker in --enable-version3 --enable-nonfree --enable-libfdk-aac libfdk_aac; do
    if grep -F -- "$forbidden_marker" "$STRINGS_FILE" >/dev/null; then
        echo "Standard AIO contains a forbidden licensing marker: $forbidden_marker" >&2
        exit 1
    fi
done

for component in \
        mpdecimate drawtext ass subtitles \
        libx264 libx265 libvpx-vp9 libaom-av1 \
        libaribcaption \
        h264_mediacodec hevc_mediacodec vp9_mediacodec av1_mediacodec; do
    if ! grep -Fx -- "$component" "$STRINGS_FILE" >/dev/null; then
        echo "Required AIO runtime component string is missing: $component" >&2
        exit 1
    fi
done

if [[ -f "$CONFIG_MAK" ]]; then
    for expected_config in \
            CONFIG_GPL CONFIG_MPDECIMATE_FILTER CONFIG_PIXELUTILS \
            CONFIG_LIBASS CONFIG_ASS_FILTER CONFIG_SUBTITLES_FILTER \
            CONFIG_LIBFREETYPE CONFIG_LIBFRIBIDI CONFIG_LIBHARFBUZZ \
            CONFIG_DRAWTEXT_FILTER CONFIG_LIBARIBCAPTION \
            CONFIG_LIBARIBCAPTION_DECODER CONFIG_LIBX264 \
            CONFIG_LIBX264_ENCODER CONFIG_LIBX265 CONFIG_LIBX265_ENCODER \
            CONFIG_LIBVPX CONFIG_LIBVPX_VP9_ENCODER CONFIG_LIBAOM \
            CONFIG_LIBAOM_AV1_ENCODER; do
        if ! grep -Fx -- "${expected_config}=yes" "$CONFIG_MAK" >/dev/null; then
            echo "Required FFmpeg configuration is missing: $expected_config" >&2
            exit 1
        fi
    done
    for forbidden_config in CONFIG_VERSION3 CONFIG_NONFREE CONFIG_LIBFDK_AAC; do
        if grep -Fx -- "${forbidden_config}=yes" "$CONFIG_MAK" >/dev/null; then
            echo "Forbidden FFmpeg configuration is enabled: $forbidden_config" >&2
            exit 1
        fi
    done
fi

actual_sha256="$(shasum -a 256 "$BINARY" | awk '{ print $1 }')"
actual_size="$(wc -c <"$BINARY" | tr -d '[:space:]')"
if [[ -n "${EXPECTED_AIO_SHA256:-}" && "$actual_sha256" != "$EXPECTED_AIO_SHA256" ]]; then
    echo "AIO FFmpeg checksum mismatch." >&2
    echo "Expected $EXPECTED_AIO_SHA256, got $actual_sha256" >&2
    exit 1
fi

echo "Verified standard GPL AIO technical profile."
echo "SHA-256: $actual_sha256"
echo "Size: $actual_size bytes"
echo "Dynamic dependencies:"
awk '/\(NEEDED\)/ { print "  " $NF }' "$DYNAMIC_FILE"
