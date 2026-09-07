#!/usr/bin/env bash
# Builds the shim for the host platform into resources/babashka/esbuild/<platform>/.
set -euo pipefail
cd "$(dirname "$0")"

esbuild_version=$(go list -m -f '{{.Version}}' github.com/evanw/esbuild)

case "$(uname -s)" in
  Darwin) os=darwin; lib=libesbuild.dylib ;;
  Linux)  os=linux;  lib=libesbuild.so ;;
  *)      os=windows; lib=esbuild.dll ;;
esac
case "$(uname -m)" in
  arm64|aarch64) arch=aarch64 ;;
  *)             arch=x86_64 ;;
esac

out="resources/babashka/esbuild/$os-$arch"
mkdir -p "$out"
CGO_ENABLED=1 go build -buildmode=c-shared \
  -ldflags "-s -w -X main.esbuildVersion=$esbuild_version" \
  -o "$out/$lib" shim.go
rm -f "$out/${lib%.*}.h"
ls -l "$out/$lib"
