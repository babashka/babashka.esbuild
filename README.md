# babashka.esbuild

Bundle and transform JavaScript, TypeScript, JSX and CSS from
[babashka](https://github.com/babashka/babashka) with
[esbuild](https://esbuild.github.io/).

Esbuild runs in the babashka process through
[babashka.ffi](https://github.com/babashka/ffi). It does not use or need Node.js.

## Status

Experimental because this is a new library which needs some rounds of feedback first, before we can promise a stable API.

## Install

Add the library to `bb.edn` or `deps.edn`:

```clojure
{:deps {io.github.babashka/esbuild {:mvn/version "0.1.0"}}}
```

That pulls in `io.github.babashka/libesbuild`, which carries the compiled
esbuild for macOS, Linux and Windows on x86_64 and aarch64. The first call
unpacks the one for your platform into
`<xdg-cache>/babashka/esbuild/<version>/`. Later runs load it from there.

On the JVM, start with `--enable-native-access=ALL-UNNAMED` and add
`babashka.ffi`, which babashka has built in and which is not released yet:

```clojure
io.github.babashka/ffi {:git/url "https://github.com/babashka/ffi"
                        :git/sha "3917f39ededc25372b78f91b5ef9f409f522eeba"}
```

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

Options are the esbuild options as kebab-case keywords with keyword values.
`:entry-points` reaches esbuild as `entryPoints`.

Errors throw. The messages are in the ex-data:

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
