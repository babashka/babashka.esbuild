# babashka.esbuild

Use [esbuild](https://esbuild.github.io/) from
[babashka](https://github.com/babashka/babashka) and the JVM.

Bundle and transform JavaScript, TypeScript, JSX and CSS.

This library runs on babashka or on the JVM through
[babashka.ffi](https://github.com/babashka/ffi). It does not use or need Node.js.

## Status

Experimental. The API may change as we gather feedback.

## Install

Add the library to `bb.edn` or `deps.edn`:

```clojure
{:deps {io.github.babashka/babashka.esbuild
        {:git/sha "f9e39b6914c9028b8224ffa4ea1a9c781f09f3ef"}}}
```

That pulls in `io.github.babashka/libesbuild`, which carries esbuild for
macOS, Linux and Windows on x86_64 and aarch64. See [How esbuild
ships](#how-esbuild-ships).

`babashka.ffi` comes with it. Babashka includes it as well.

On the JVM, start with `--enable-native-access=ALL-UNNAMED`.

## Distribution

`io.github.babashka/libesbuild` is a jar of esbuild shared libraries, one per
platform, published to Clojars. Its version is the esbuild version it wraps
plus a shim number, so `0.28.2-2` holds esbuild v0.28.2. `babashka.esbuild`
depends on it, so a release of this library pins one esbuild.

The operating system loads a shared library from a file, so the first call
unpacks the one for your platform into
`<xdg-cache>/babashka/esbuild/<libesbuild version>/`. Later runs load it from
there. The version sits in the path, so an upgrade writes a new directory
instead of changing a file another process may be using.

## Usage

```clojure
(require '[babashka.esbuild :as esbuild])

(esbuild/transform "const x: number = 1" {:loader :ts})
;;=> {:code "const x = 1;\n"}

(esbuild/transform "let f = (x: number): number => x*2"
                   {:loader :ts :minify true :target :es2020})
;;=> {:code "let f=e=>e*2;\n"}
```

`build` bundles entry points and returns the output in memory:

```clojure
(esbuild/build {:entry-points ["src/main.tsx"] :bundle true :format :esm :minify true})
;;=> {:outputs [{:path "<stdout>" :contents "..."}]}
```

Pass `:write true` with `:outfile` or `:outdir` to write to disk instead.

Use kebab-case keywords for option names. Enum values can also be keywords.
`:entry-points` reaches esbuild as `entryPoints`.

Build and transform errors throw exceptions with error details in `ex-data`:

```clojure
(esbuild/transform "let = ;")
;; Unexpected ";"
;; {:type :babashka.esbuild/error
;;  :errors [{:text "Unexpected \";\"" :file "<stdin>" :line 1 :column 6}]}
```

## Examples

`examples/squint_bundle.clj` compiles ClojureScript with
[squint](https://github.com/squint-cljs/squint) and bundles it into one
JavaScript file:

    bb examples/squint_bundle.clj

## Development

See [doc/dev.md](doc/dev.md).

## License

Copyright © 2026 Michiel Borkent

Distributed under the MIT License. See LICENSE.

esbuild is MIT licensed, Copyright (c) 2020 Evan Wallace. The `libesbuild` jar
carries its license.
