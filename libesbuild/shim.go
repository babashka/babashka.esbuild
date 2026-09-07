// C ABI shim around esbuild's Go API, for babashka.ffi.
//
//	go build -buildmode=c-shared -o libesbuild.dylib shim.go
package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"encoding/json"
	"unsafe"

	"github.com/evanw/esbuild/pkg/api"
)

var loaders = map[string]api.Loader{
	"js": api.LoaderJS, "jsx": api.LoaderJSX, "ts": api.LoaderTS, "tsx": api.LoaderTSX,
	"json": api.LoaderJSON, "css": api.LoaderCSS, "text": api.LoaderText,
	"base64": api.LoaderBase64, "dataurl": api.LoaderDataURL, "file": api.LoaderFile,
	"binary": api.LoaderBinary, "default": api.LoaderDefault,
}

var formats = map[string]api.Format{
	"iife": api.FormatIIFE, "cjs": api.FormatCommonJS, "esm": api.FormatESModule,
}

var platforms = map[string]api.Platform{
	"browser": api.PlatformBrowser, "node": api.PlatformNode, "neutral": api.PlatformNeutral,
}

var targets = map[string]api.Target{
	"esnext": api.ESNext, "es5": api.ES5, "es2015": api.ES2015, "es2016": api.ES2016,
	"es2017": api.ES2017, "es2018": api.ES2018, "es2019": api.ES2019, "es2020": api.ES2020,
	"es2021": api.ES2021, "es2022": api.ES2022, "es2023": api.ES2023, "es2024": api.ES2024,
}

var sourcemaps = map[string]api.SourceMap{
	"none": api.SourceMapNone, "inline": api.SourceMapInline,
	"external": api.SourceMapExternal, "linked": api.SourceMapLinked,
	"both": api.SourceMapInlineAndExternal,
}

var jsxModes = map[string]api.JSX{
	"transform": api.JSXTransform, "preserve": api.JSXPreserve, "automatic": api.JSXAutomatic,
}

type options struct {
	// shared
	Format    string            `json:"format"`
	Target    string            `json:"target"`
	Platform  string            `json:"platform"`
	Minify    bool              `json:"minify"`
	Sourcemap string            `json:"sourcemap"`
	JSX       string            `json:"jsx"`
	Define    map[string]string `json:"define"`
	// transform
	Loader     string `json:"loader"`
	Sourcefile string `json:"sourcefile"`
	// build
	EntryPoints []string          `json:"entryPoints"`
	Bundle      bool              `json:"bundle"`
	Outfile     string            `json:"outfile"`
	Outdir      string            `json:"outdir"`
	Splitting   bool              `json:"splitting"`
	Write       bool              `json:"write"`
	External    []string          `json:"external"`
	Alias       map[string]string `json:"alias"`
}

type message struct {
	Text   string `json:"text"`
	File   string `json:"file,omitempty"`
	Line   int    `json:"line,omitempty"`
	Column int    `json:"column,omitempty"`
}

type outputFile struct {
	Path     string `json:"path"`
	Contents string `json:"contents"`
}

type result struct {
	Code        string       `json:"code,omitempty"`
	Map         string       `json:"map,omitempty"`
	OutputFiles []outputFile `json:"outputFiles,omitempty"`
	Errors      []message    `json:"errors"`
	Warnings    []message    `json:"warnings"`
}

func messages(ms []api.Message) []message {
	out := make([]message, 0, len(ms))
	for _, m := range ms {
		msg := message{Text: m.Text}
		if m.Location != nil {
			msg.File, msg.Line, msg.Column = m.Location.File, m.Location.Line, m.Location.Column
		}
		out = append(out, msg)
	}
	return out
}

// cstring hands ownership to the caller, who must call esbuild_free.
func cstring(v any) *C.char {
	b, err := json.Marshal(v)
	if err != nil {
		b, _ = json.Marshal(result{Errors: []message{{Text: err.Error()}}})
	}
	return C.CString(string(b))
}

func fail(err error) *C.char {
	return cstring(result{Errors: []message{{Text: err.Error()}}})
}

func parse(optionsJSON *C.char) (options, error) {
	var o options
	s := C.GoString(optionsJSON)
	if s == "" {
		return o, nil
	}
	return o, json.Unmarshal([]byte(s), &o)
}

// esbuildVersion is set at build time with
// -ldflags "-X main.esbuildVersion=$(go list -m -f '{{.Version}}' github.com/evanw/esbuild)".
var esbuildVersion = "unknown"

//export esbuild_version
func esbuild_version() *C.char {
	return C.CString(esbuildVersion)
}

//export esbuild_transform
func esbuild_transform(code *C.char, optionsJSON *C.char) *C.char {
	o, err := parse(optionsJSON)
	if err != nil {
		return fail(err)
	}
	r := api.Transform(C.GoString(code), api.TransformOptions{
		Loader:            loaders[o.Loader],
		Format:            formats[o.Format],
		Target:            targets[o.Target],
		Platform:          platforms[o.Platform],
		Sourcemap:         sourcemaps[o.Sourcemap],
		JSX:               jsxModes[o.JSX],
		Sourcefile:        o.Sourcefile,
		Define:            o.Define,
		MinifyWhitespace:  o.Minify,
		MinifyIdentifiers: o.Minify,
		MinifySyntax:      o.Minify,
	})
	return cstring(result{
		Code:     string(r.Code),
		Map:      string(r.Map),
		Errors:   messages(r.Errors),
		Warnings: messages(r.Warnings),
	})
}

//export esbuild_build
func esbuild_build(optionsJSON *C.char) *C.char {
	o, err := parse(optionsJSON)
	if err != nil {
		return fail(err)
	}
	r := api.Build(api.BuildOptions{
		EntryPoints:       o.EntryPoints,
		Bundle:            o.Bundle,
		Outfile:           o.Outfile,
		Outdir:            o.Outdir,
		Splitting:         o.Splitting,
		Write:             o.Write,
		External:          o.External,
		Alias:             o.Alias,
		Format:            formats[o.Format],
		Target:            targets[o.Target],
		Platform:          platforms[o.Platform],
		Sourcemap:         sourcemaps[o.Sourcemap],
		JSX:               jsxModes[o.JSX],
		Define:            o.Define,
		MinifyWhitespace:  o.Minify,
		MinifyIdentifiers: o.Minify,
		MinifySyntax:      o.Minify,
	})
	files := make([]outputFile, 0, len(r.OutputFiles))
	for _, f := range r.OutputFiles {
		files = append(files, outputFile{Path: f.Path, Contents: string(f.Contents)})
	}
	return cstring(result{
		OutputFiles: files,
		Errors:      messages(r.Errors),
		Warnings:    messages(r.Warnings),
	})
}

//export esbuild_free
func esbuild_free(p *C.char) {
	C.free(unsafe.Pointer(p))
}

func main() {}
