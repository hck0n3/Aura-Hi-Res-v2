#!/usr/bin/env bash
# Builds the trimmed rclone of tools/rclone-android into librclone.so for each Android ABI (registry row 886).
#
#   scripts/build-rclone-android.sh                  # arm64-v8a armeabi-v7a x86_64 x86
#   scripts/build-rclone-android.sh x86_64           # only the ABIs named
#   scripts/build-rclone-android.sh --stamp          # print the inputs fingerprint (CI cache key) and exit
#
# Output: tools/rclone-android/out/jniLibs/<abi>/librclone.so, which app/build.gradle.kts packages as a jniLib
# (never committed; .gitignore). Android 10+ only lets an app exec files from its nativeLibraryDir, so the
# executable travels as a "library". Without it the app still builds and the private cloud says it is not
# available on this device.
#
# Supply chain: rclone comes from the Go module proxy at the pinned tag; go.sum pins its content (checked
# against sum.golang.org) and the proxy's tag -> commit answer must equal RCLONE_COMMIT, as must GitHub's own
# tag. The Go toolchain is pinned too (GOTOOLCHAIN downloads exactly that one, checksum-verified).
#
# Idempotent: an ABI whose binary was built from the same inputs and still verifies is skipped without
# touching Go, the NDK or the network.
set -euo pipefail

RCLONE_VERSION=v1.76.0
RCLONE_COMMIT=e982ff36668f5130d94b4f70409babb3d964df42
GO_TOOLCHAIN=go1.27.4

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MOD="$ROOT/tools/rclone-android"
OUT="$MOD/out/jniLibs"
STAMPS="$MOD/out/stamps"  # outside jniLibs: only the .so files there get packaged
CHECK=(python3 "$ROOT/scripts/rclone_android/rclone_android.py")
# Licence notices of everything linked into the binary, shipped in the APK (row 887).
NOTICES=app/src/main/assets/licenses/rclone-android-NOTICES.txt

die() { echo "build-rclone-android: $*" >&2; exit 1; }

# --- pins must agree with the module ------------------------------------------------------------------------
grep -qx "require github.com/rclone/rclone $RCLONE_VERSION" "$MOD/go.mod" \
  || die "tools/rclone-android/go.mod does not require rclone $RCLONE_VERSION"
grep -qx "toolchain $GO_TOOLCHAIN" "$MOD/go.mod" || die "tools/rclone-android/go.mod is not pinned to $GO_TOOLCHAIN"

# --- inputs fingerprint: what the binaries are built from ---------------------------------------------------
NDK_VERSION="$("${CHECK[@]}" gradle "$ROOT/app/build.gradle.kts" ndkVersion)"
MIN_API="$("${CHECK[@]}" gradle "$ROOT/app/build.gradle.kts" minSdk)"
STAMP="$RCLONE_VERSION-$(cat "$MOD/go.mod" "$MOD/go.sum" "$MOD/main.go" "$ROOT/scripts/build-rclone-android.sh" \
  "$ROOT/scripts/rclone_android/rclone_android.py" "$ROOT/$NOTICES" | sha256sum | cut -c1-32)-ndk$NDK_VERSION-api$MIN_API"

if [ "${1:-}" = "--stamp" ]; then echo "$STAMP"; exit 0; fi
if [ "$#" -gt 0 ]; then ABIS=("$@"); else ABIS=(arm64-v8a armeabi-v7a x86_64 x86); fi

up_to_date() {
  local abi="$1" so="$OUT/$1/librclone.so"
  [ -f "$so" ] && [ -f "$STAMPS/$abi" ] && [ "$(cat "$STAMPS/$abi")" = "$STAMP" ] \
    && "${CHECK[@]}" binary "$abi" "$so" >/dev/null 2>&1
}

TODO=()
for abi in "${ABIS[@]}"; do
  case "$abi" in arm64-v8a|armeabi-v7a|x86_64|x86) ;; *) die "unknown ABI $abi" ;; esac
  if up_to_date "$abi"; then echo "$abi: up to date"; else TODO+=("$abi"); fi
done
[ "${#TODO[@]}" -eq 0 ] && exit 0

# --- NDK: the exact version the app builds with ------------------------------------------------------------
find_ndk() {
  if [ -n "${RCLONE_NDK_DIR:-}" ]; then echo "$RCLONE_NDK_DIR"; return; fi
  local sdk_dirs=("${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}")
  if [ -f "$ROOT/local.properties" ]; then
    sdk_dirs+=("$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | head -n 1)")
  fi
  local sdk
  for sdk in "${sdk_dirs[@]}"; do
    if [ -n "$sdk" ] && [ -f "$sdk/ndk/$NDK_VERSION/source.properties" ]; then echo "$sdk/ndk/$NDK_VERSION"; return; fi
  done
  for sdk in "${sdk_dirs[@]}"; do
    if [ -n "$sdk" ] && [ -x "$sdk/cmdline-tools/latest/bin/sdkmanager" ]; then
      echo "Installing NDK $NDK_VERSION with sdkmanager" >&2
      # `yes` dies of SIGPIPE when sdkmanager exits; the source.properties check below is the real verdict.
      yes 2>/dev/null | "$sdk/cmdline-tools/latest/bin/sdkmanager" --install "ndk;$NDK_VERSION" >/dev/null || true
      echo "$sdk/ndk/$NDK_VERSION"; return
    fi
  done
}
NDK="$(find_ndk)"
[ -n "$NDK" ] && [ -f "$NDK/source.properties" ] \
  || die "NDK $NDK_VERSION not found (set ANDROID_SDK_ROOT, sdk.dir in local.properties, or RCLONE_NDK_DIR)"
grep -q "^Pkg.Revision *= *$NDK_VERSION\$" "$NDK/source.properties" || die "the NDK found is not $NDK_VERSION"
CLANG_BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
[ -d "$CLANG_BIN" ] || die "this script builds on a linux-x86_64 host"

# --- Go: pinned toolchain, checksum database on, module graph read-only ------------------------------------
export GOTOOLCHAIN="$GO_TOOLCHAIN" GOFLAGS=-mod=readonly GOSUMDB=sum.golang.org GONOSUMDB= GONOSUMCHECK= \
  GOINSECURE= GOPRIVATE= GOWORK=off
cd "$MOD"
GO_VERSION="$(go env GOVERSION)"
[ "$GO_VERSION" = "$GO_TOOLCHAIN" ] || die "go reports $GO_VERSION, pinned $GO_TOOLCHAIN"

# The proxy's tag -> commit, then GitHub's own tag, must both be the pinned commit.
go mod download -json "github.com/rclone/rclone@$RCLONE_VERSION" | "${CHECK[@]}" origin "$RCLONE_VERSION" "$RCLONE_COMMIT"
UPSTREAM="$(git ls-remote https://github.com/rclone/rclone.git "refs/tags/$RCLONE_VERSION^{}" | cut -f1)"
[ "$UPSTREAM" = "$RCLONE_COMMIT" ] || die "GitHub tag $RCLONE_VERSION is ${UPSTREAM:-missing}, pinned $RCLONE_COMMIT"

for abi in "${TODO[@]}"; do
  case "$abi" in
    arm64-v8a) GOARCH=arm64; GOARM=; TRIPLE=aarch64-linux-android ;;
    armeabi-v7a) GOARCH=arm; GOARM=7; TRIPLE=armv7a-linux-androideabi ;;
    x86_64) GOARCH=amd64; GOARM=; TRIPLE=x86_64-linux-android ;;
    x86) GOARCH=386; GOARM=; TRIPLE=i686-linux-android ;;
  esac
  CC="$CLANG_BIN/$TRIPLE$MIN_API-clang"
  [ -x "$CC" ] || die "missing $CC"
  mkdir -p "$OUT/$abi" "$STAMPS"
  rm -f "$STAMPS/$abi"
  echo "$abi: building rclone $RCLONE_VERSION with $GO_VERSION (cgo, NDK $NDK_VERSION, API $MIN_API)"
  # cgo is required, not a choice: android/arm, 386 and amd64 can only link externally, and without cgo Go
  # uses its own DNS resolver, which cannot resolve on Android (no /etc/resolv.conf; golang.org/issue/10714).
  GOOS=android GOARCH="$GOARCH" GOARM="$GOARM" CGO_ENABLED=1 CC="$CC" \
    go build -trimpath -buildvcs=false \
      -ldflags="-s -w -X github.com/rclone/rclone/fs.Version=$RCLONE_VERSION -extldflags=-Wl,-z,max-page-size=16384" \
      -o "$STAMPS/$abi.tmp" .
  "${CHECK[@]}" binary "$abi" "$STAMPS/$abi.tmp"
  # A module or Go release the shipped notices do not cover fails the build until they are regenerated:
  #   go version -m <librclone.so> | scripts/rclone_android/rclone_android.py notices "$(go env GOMODCACHE)" \
  #     "$(go env GOROOT)" > app/src/main/assets/licenses/rclone-android-NOTICES.txt
  go version -m "$STAMPS/$abi.tmp" | "${CHECK[@]}" notices-check "$ROOT/$NOTICES"
  mv -f "$STAMPS/$abi.tmp" "$OUT/$abi/librclone.so"
  echo "$STAMP" > "$STAMPS/$abi"
done

go mod verify
