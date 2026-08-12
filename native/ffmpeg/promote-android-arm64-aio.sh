#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
readonly SCRIPT_DIR
PROJECT_ROOT="$(CDPATH='' cd -- "$SCRIPT_DIR/../.." && pwd)"
readonly PROJECT_ROOT
readonly CANDIDATE="${1:-$SCRIPT_DIR/.build/aio/staging/libffmpeg.so}"
readonly DESTINATION="$PROJECT_ROOT/app/src/main/libs/arm64-v8a/libffmpeg.so"
readonly EXPECTED_SHA256="3e0eaf750cda7b06be48a1eb2f34714b5fc4854a90b0f2c635c09c2080288133"
readonly TEMPORARY_DESTINATION="${DESTINATION}.aio-new"

cleanup() {
    rm -f -- "$TEMPORARY_DESTINATION"
}
trap cleanup EXIT

EXPECTED_AIO_SHA256="$EXPECTED_SHA256" \
    "$SCRIPT_DIR/verify-android-arm64-aio.sh" "$CANDIDATE"

mkdir -p "$(dirname -- "$DESTINATION")"
install -m 0755 "$CANDIDATE" "$TEMPORARY_DESTINATION"
actual_sha256="$(shasum -a 256 "$TEMPORARY_DESTINATION" | awk '{ print $1 }')"
if [[ "$actual_sha256" != "$EXPECTED_SHA256" ]]; then
    echo "The copied AIO candidate changed before promotion." >&2
    exit 1
fi
mv -f -- "$TEMPORARY_DESTINATION" "$DESTINATION"

EXPECTED_AIO_SHA256="$EXPECTED_SHA256" \
    "$SCRIPT_DIR/verify-android-arm64-aio.sh" "$DESTINATION"
echo "Promoted verified GPL AIO FFmpeg: $DESTINATION"
