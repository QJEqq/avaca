// Package mobilecore bundles the two engines AVACA ships on Android into ONE
// gomobile .aar: the Xray core inherited from Yumi (github.com/GXUser7/yumi/core-xray, package
// "yumi") and the OpenFlux transport (github.com/p1neappleXpress/OpenFlux/mobile,
// package "mobile"). Each gomobile bind embeds a Go runtime as jni/<abi>/libgojni.so;
// two separate .aars would ship two of those under the same path and the APK would
// fail to package (or silently drop one core). Binding both packages in a single
// gomobile invocation produces one libgojni.so that exports both APIs.
//
// This file itself exports nothing — the blank imports only keep both packages in
// the module graph so `go mod tidy` retains them and `gomobile bind` can resolve
// them by import path. The real build is tools/build-core.sh.
package mobilecore

import (
	_ "github.com/GXUser7/yumi/core-xray"
	_ "openflux-mobile"
)
