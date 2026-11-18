# Third-party components

Avaca is licensed under the [GNU General Public License v3.0](LICENSE). It includes or is built
from the following components, under their own licenses.

| Component | Use | License |
| --- | --- | --- |
| [Yumi](https://github.com/GXUser7/yumi) | The project Avaca was forked from: app code, `core-xray/` binding | GPL-3.0 |
| [Xray-core](https://github.com/XTLS/Xray-core) | Tunnel core, linked into `avacacore.aar` | MPL-2.0 |
| [OpenFlux](https://github.com/p1neappleXpress/OpenFlux) ([fork](https://github.com/QJEqq/OpenFlux/tree/avaca-0.17)) | Transport, linked into `avacacore.aar`; sources in `third_party/OpenFlux` | GPL-3.0 |
| [pion](https://github.com/pion) (`webrtc`, `sctp` and others) | WebRTC transport inside OpenFlux | MIT |
| [gomobile](https://pkg.go.dev/golang.org/x/mobile) | Go ↔ Java binding | BSD-3-Clause |
| Go standard library and `golang.org/x/*` | Core runtime | BSD-3-Clause |
| [Jetpack Compose, AndroidX, Material 3](https://developer.android.com/jetpack) | User interface | Apache-2.0 |
| [Kotlin, kotlinx.coroutines, kotlinx.serialization](https://kotlinlang.org) | Language and libraries | Apache-2.0 |
| [ZXing](https://github.com/zxing/zxing), [CameraX](https://developer.android.com/media/camera/camerax) | Reading and showing QR codes | Apache-2.0 |
| [v2fly/geoip](https://github.com/v2fly/geoip) | IP routing database, downloaded at run time, not bundled | CC-BY-SA-4.0 |
| [v2fly/domain-list-community](https://github.com/v2fly/domain-list-community) | Domain routing database, downloaded at run time, not bundled | MIT |

The full dependency list with versions is in `core/go.mod`, `core-xray/go.mod`,
`third_party/OpenFlux/go.mod` and `gradle/libs.versions.toml`.

## Source for the binaries

Every release APK is built from the tagged commit of this repository: the app from the Gradle
modules, the core from `core/`, `core-xray/` and the `third_party/OpenFlux` submodule at the commit
the tag pins, with `tools/build-core.sh`. The core is also attached to each release as
`avacacore-<version>.aar`.
