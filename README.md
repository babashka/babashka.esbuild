# babashka.esbuild

Use [esbuild](https://esbuild.github.io/) from
[babashka](https://github.com/babashka/babashka) and the JVM.

Bundle and transform JavaScript, TypeScript, JSX and CSS.

Esbuild runs in the babashka process through
[babashka.ffi](https://github.com/babashka/ffi). It does not use or need Node.js.

## Status

Experimental because this is a new library which needs some rounds of feedback first, before we can promise a stable API.

## Install

Add the library to `bb.edn` or `deps.edn`:

```clojure
{:deps {io.github.babashka/esbuild {:mvn/version "0.1.0"}}}
```

That pulls in `io.github.babashka/libesbuild`, which carries esbuild for
macOS, Linux and Windows on x86_64 and aarch64. See [How esbuild
ships](#how-esbuild-ships).

On the JVM, start with `--enable-native-access=ALL-UNNAMED` and add
`babashka.ffi`, which babashka has built in and which is not released yet:

```clojure
io.github.babashka/ffi {:git/url "https://github.com/babashka/ffi"
                        :git/sha "3917f39ededc25372b78f91b5ef9f409f522eeba"}
```

## How esbuild ships

esbuild is a Go program and releases executables only. There is no libesbuild
to link against and no C API, so this project builds one. `libesbuild/shim.go`
wraps the esbuild Go API in four C functions and Go compiles it with
`-buildmode=c-shared`:

```c
char *esbuild_version(void);
char *esbuild_transform(const char *code, const char *options_json);
char *esbuild_build(const char *options_json);
void  esbuild_free(char *p);
```

Options and results cross as JSON, which keeps the C interface at four
functions while esbuild keeps adding options.

`io.github.babashka/libesbuild` is a jar of those shared libraries, one per
platform, published to Clojars. Its version is the esbuild version it wraps
plus a shim number, so `0.28.2-1` holds esbuild v0.28.2. `babashka.esbuild`
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
