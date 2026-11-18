<div align="center">

<img src="docs/icons/charcoal.png" width="112" alt="Avaca">

# Avaca

**An Android VPN client on the Xray core, with the OpenFlux transport**

Remnawave and other panel subscriptions, provider JSON configs kept intact,<br>OpenFlux for whitelist-only networks, and two designs to choose from.

<br>

[![Release](https://img.shields.io/github/v/release/QJEqq/avaca?style=flat-square&label=release&labelColor=292929&color=B8A487)](https://github.com/QJEqq/avaca/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/QJEqq/avaca/build.yml?branch=main&style=flat-square&label=build&labelColor=292929)](https://github.com/QJEqq/avaca/actions/workflows/build.yml)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-B8A487?style=flat-square&labelColor=292929&logo=android&logoColor=white)](#install)
[![Xray-core](https://img.shields.io/badge/core-Xray-B8A487?style=flat-square&labelColor=292929)](https://github.com/XTLS/Xray-core)
[![License GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-B8A487?style=flat-square&labelColor=292929)](LICENSE)

<br>

### [Download the latest release](https://github.com/QJEqq/avaca/releases/latest)

[Features](#features) &nbsp;·&nbsp; [Design](#design) &nbsp;·&nbsp; [Install](#install) &nbsp;·&nbsp; [Build](#building-from-source) &nbsp;·&nbsp; [Privacy](#privacy) &nbsp;·&nbsp; [Русский](README.md)

</div>

## Features

**Subscriptions**
- Share links, base64 lists and Xray JSON documents; add from a link, the clipboard, a QR code, or `happ://` and `avaca://` links.
- Provider headers: announcement, traffic and expiry, support, profile page, update interval, fragmentation, Mux, fallback URL, moving with `new-url` and `new-domain`.
- Device limits of panels such as Remnawave: a random per-install `x-hwid`.

**Provider JSON as written**
- A server from a JSON document goes to the core whole: the outbound, `dialerProxy` chains, the document's routing and DNS.
- XHTTP `extra`, Final Mask (`fm`), certificate pinning — from JSON and from links.

**Protocols and transports**
- VLESS (REALITY, XTLS Vision, XUDP), VMess, Trojan, Shadowsocks (including 2022), Hysteria2, WireGuard, HTTP, SOCKS5.
- TCP, WebSocket, gRPC, HTTPUpgrade, XHTTP, mKCP; uTLS fingerprints, ALPN, SNI.
- **OpenFlux** — a transport for networks that let through only approved services. Per-user channels come from [Supwave](https://github.com/QJEqq/supwave), an add-on for Remnawave.

**Routing**
- Routing modes and your own rules (via VPN / direct / block): domains, links, `geosite:`, IPs and subnets, `geoip:`.
- Per-app split tunnelling, ad and QUIC blocking, LAN bypass.

**Resilience**
- Automatic failover to a live server, and back to the chosen one once it recovers.
- DNS over HTTPS with a dead resolver replaced; the provider's DNS raced against your own.
- Server addresses resolved over DoH, Mux, ClientHello fragmentation.

**For people who run their own servers** (developer mode)
- Step-by-step diagnostics: DNS → DoH → TCP → TLS/REALITY → traffic → DNS in the tunnel → external IP.
- The final Xray config, the core's log level, a connection journal showing where each connection went.

## Design

On first launch the app offers both designs a swipe apart; change it later in **Settings → Appearance**.

- **Avaca** — graphite and sand, a planet drawn in coastlines.
- **Git** — black, a planet of dots, a blue server marker.

Seven app icons to choose from:

<table>
  <tr>
    <td align="center"><img src="docs/icons/charcoal.png" width="72"><br><sub>Charcoal &amp; ash</sub></td>
    <td align="center"><img src="docs/icons/emerald.png" width="72"><br><sub>Dark emerald</sub></td>
    <td align="center"><img src="docs/icons/midnight.png" width="72"><br><sub>Midnight blue</sub></td>
    <td align="center"><img src="docs/icons/bordeaux.png" width="72"><br><sub>Bordeaux</sub></td>
    <td align="center"><img src="docs/icons/bronze.png" width="72"><br><sub>Bronze</sub></td>
    <td align="center"><img src="docs/icons/plum.png" width="72"><br><sub>Dark plum</sub></td>
    <td align="center"><img src="docs/icons/pine.png" width="72"><br><sub>Pine &amp; sand</sub></td>
  </tr>
</table>

## Install

APKs are in the [releases](https://github.com/QJEqq/avaca/releases/latest):

| File | For |
| --- | --- |
| `avaca-<version>-arm64-v8a.apk` | nearly every phone of recent years |
| `avaca-<version>-armeabi-v7a.apk` | older 32-bit phones |

Android 8.0 or newer. All builds are signed with one key; the certificate's SHA-256:

```
B2:D5:DF:F5:85:47:AD:58:05:C9:46:23:D3:88:3A:5F:C6:53:02:C2:B1:B5:EE:14:5F:ED:39:E8:A3:C7:89:52
```

Check with `apksigner verify --print-certs avaca-*.apk`.

## Building from source

```bash
git clone --recurse-submodules https://github.com/QJEqq/avaca.git
cd avaca
tools/build-core.sh            # the core → shared/libs/avacacore.aar (needs Go, gomobile, an NDK)
./gradlew :app:assembleDebug
```

Without Go, take the prebuilt `avacacore-<version>.aar` from a release and put it at `shared/libs/avacacore.aar`.
Details are in [DEVELOPMENT.md](DEVELOPMENT.md).

## Privacy

No analytics, ads, accounts or crash reports. Subscriptions, servers, settings and the journal stay on the phone.
The app talks only to the servers you add and a few service endpoints — listed in [PRIVACY.md](PRIVACY.md).

## Built on

- [Yumi](https://github.com/GXUser7/yumi) — Avaca started as a fork of Yumi: the data model, link parsing, Xray config generation, the core binding.
- [Xray-core](https://github.com/XTLS/Xray-core) — the tunnel core.
- [OpenFlux](https://github.com/p1neappleXpress/OpenFlux) — the transport; built from a [fork](https://github.com/QJEqq/OpenFlux/tree/avaca-0.17) with Android fixes.
- [v2fly/geoip](https://github.com/v2fly/geoip) and [v2fly/domain-list-community](https://github.com/v2fly/domain-list-community) — routing databases, downloaded on first start.

Every third-party component and its license is in [NOTICE.md](NOTICE.md).

## License

[GPL-3.0](LICENSE), like Yumi and OpenFlux. Bugs and ideas go to [issues](https://github.com/QJEqq/avaca/issues); vulnerabilities per [SECURITY.md](SECURITY.md).
