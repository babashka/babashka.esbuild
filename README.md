# babashka.esbuild

Bundle and transform JavaScript, TypeScript, JSX and CSS from
[babashka](https://github.com/babashka/babashka) with
[esbuild](https://esbuild.github.io/).

esbuild runs in the babashka process through
[babashka.ffi](https://github.com/babashka/ffi). There is no subprocess and no
Node.js.

Status: experimental, because `babashka.ffi` is experimental.

## Install

Add the library and the natives to `bb.edn` or `deps.edn`:

```clojure
{:deps {io.github.babashka/babashka.esbuild {:mvn/version "0.1.0"}
        io.github.babashka/libesbuild {:mvn/version "0.28.2-1"}}}
```

`libesbuild` carries the compiled esbuild for macOS, Linux and Windows on
x86_64 and aarch64. The first call unpacks the one for your platform into
`<xdg-cache>/babashka/esbuild/<version>/`. Later runs load it from there.

On the JVM, start with `--enable-native-access=ALL-UNNAMED`.

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

Build the shim for your platform, then run the tests on both hosts:

    bb natives
    bb test
    clojure -M:test

`libesbuild/build.sh` writes into `libesbuild/resources`, which the top level
`deps.edn` picks up with `:local/root`. The published jar carries all five
platforms and is built by the `natives` workflow.

Release the natives by pushing a `libesbuild-<version>` tag.

## License

Copyright © 2026 Michiel Borkent

Distributed under the MIT License. See LICENSE.

esbuild is MIT licensed, Copyright (c) 2020 Evan Wallace. The `libesbuild` jar
carries its license.
