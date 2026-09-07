# Development

## Requirements

- [Go](https://go.dev) builds the shim in `libesbuild`.
- [zig](https://ziglang.org) cross compiles the linux and windows shims. Only
  `bb natives --all` needs it: `brew install zig`.

## C interface

Esbuild releases executables. This project builds a shared library from its
Go API. `libesbuild/shim.go` wraps the API in four C functions, compiled with
`-buildmode=c-shared`:

```c
char *esbuild_version(void);
char *esbuild_transform(const char *code, const char *options_json);
char *esbuild_build(const char *options_json);
void  esbuild_free(char *p);
```

Options and results use JSON, so new esbuild options do not require new C
functions.

## Build and test

Build the shim for your machine, then run the tests on both hosts:

    bb natives
    bb test
    clojure -M:local:test

`bb natives` writes into `libesbuild/resources`. The `:local` alias and
`bb.edn` point at that directory instead of the released jar.

## Cross compile

`bb natives --all` builds every platform the jar carries:

    bb natives --all

Xcode covers both mac architectures and zig covers the rest, so one macOS
machine builds the whole jar. The linux targets name a glibc version, which
sets the oldest linux the library loads on.

## Release

Push a `libesbuild-<version>` tag to release the natives, and a `v<version>`
tag to release the library.

To release from your own machine instead, deploy the natives first, because
the library pom names them:

    export CLOJARS_USERNAME=<user>
    export CLOJARS_PASSWORD=<token from https://clojars.org/tokens>
    bb publish:libesbuild
    bb publish:esbuild

`deploy` builds the jar, and the natives jar builds any shim that is missing
or was built from another esbuild. It refuses to deploy a jar that lacks a
platform, carries an empty or stale shared library, or carries one it does not
expect. Clojars keeps every version it accepts, so those checks are the last
line before it is permanent.

Use `bb install:libesbuild` for a local m2 install instead.

## Lint

The root and `libesbuild` are separate projects that both name their build
namespace `build`, so lint them apart:

    clj-kondo --lint src test examples script build.clj
    clj-kondo --lint libesbuild/build.clj
