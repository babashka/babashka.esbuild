# Development

## Requirements

- [Go](https://go.dev) builds the shim in `libesbuild`.
- [zig](https://ziglang.org) cross compiles the linux and windows shims. Only
  `bb natives --all` needs it: `brew install zig`.

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

To release from your own machine instead, build the natives first, because the
library pom names them:

    bb natives --all
    cd libesbuild
    clojure -T:build jar
    clojure -T:build install

`clojure -T:build jar` prints the platforms it packed. Clojars keeps every
version it accepts, so read that line and check it names all five before you
deploy both:

    export CLOJARS_USERNAME=<user>
    export CLOJARS_PASSWORD=<token from https://clojars.org/tokens>
    clojure -T:build deploy
    cd ..
    clojure -T:build jar
    clojure -T:build deploy

## Lint

The root and `libesbuild` are separate projects that both name their build
namespace `build`, so lint them apart:

    clj-kondo --lint src test examples script build.clj
    clj-kondo --lint libesbuild/build.clj
