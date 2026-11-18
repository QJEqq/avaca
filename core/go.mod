module github.com/QJEqq/avaca/core

go 1.26.6

require (
	github.com/GXUser7/yumi/core-xray v0.0.0
	openflux-mobile v0.0.0
)

require (
	github.com/andybalholm/brotli v1.0.6 // indirect
	github.com/apernet/quic-go v0.59.1-0.20260425001925-6c6cc9bcb716 // indirect
	github.com/cloudflare/circl v1.6.4 // indirect
	github.com/dlclark/regexp2/v2 v2.5.2 // indirect
	github.com/dop251/goja v0.0.0-20260926152631-39ec2650adc9 // indirect
	github.com/dop251/goja_nodejs v0.0.0-20260918173711-b481721df8a2 // indirect
	github.com/ghodss/yaml v1.0.1-0.20220118164431-d8423dcdf344 // indirect
	github.com/go-sourcemap/sourcemap v2.1.4+incompatible // indirect
	github.com/google/btree v1.1.2 // indirect
	github.com/google/pprof v0.0.0-20240727154555-813a5fbdbec8 // indirect
	github.com/google/uuid v1.6.0 // indirect
	github.com/gorilla/websocket v1.5.3 // indirect
	github.com/jlaffaye/ftp v0.2.4 // indirect
	github.com/juju/ratelimit v1.0.2 // indirect
	github.com/klauspost/compress v1.20.0 // indirect
	github.com/klauspost/cpuid/v2 v2.4.0 // indirect
	github.com/miekg/dns v1.1.72 // indirect
	github.com/p1neappleXpress/OpenFlux v0.0.0 // indirect
	github.com/pelletier/go-toml v1.9.5 // indirect
	github.com/pierrec/lz4/v4 v4.1.27 // indirect
	github.com/pion/datachannel v1.6.3 // indirect
	github.com/pion/dtls/v3 v3.1.9 // indirect
	github.com/pion/ice/v4 v4.4.4 // indirect
	github.com/pion/interceptor v0.1.49 // indirect
	github.com/pion/logging v0.2.4 // indirect
	github.com/pion/mdns/v2 v2.2.1 // indirect
	github.com/pion/randutil v0.1.0 // indirect
	github.com/pion/rtcp v1.2.18 // indirect
	github.com/pion/rtp v1.10.5 // indirect
	github.com/pion/sctp v1.11.3 // indirect
	github.com/pion/sdp/v3 v3.0.20 // indirect
	github.com/pion/srtp/v3 v3.1.0 // indirect
	github.com/pion/stun/v3 v3.1.6 // indirect
	github.com/pion/stun/v4 v4.0.1 // indirect
	github.com/pion/transport/v4 v4.0.2 // indirect
	github.com/pion/transport/v5 v5.1.1 // indirect
	github.com/pion/turn/v5 v5.1.2 // indirect
	github.com/pion/webrtc/v4 v4.2.22 // indirect
	github.com/pires/go-proxyproto v0.15.0 // indirect
	github.com/quic-go/qpack v0.6.0 // indirect
	github.com/refraction-networking/utls v1.8.3-0.20260301010127-aa6edf4b11af // indirect
	github.com/robfig/cron/v3 v3.0.1 // indirect
	github.com/sagernet/sing v0.5.1 // indirect
	github.com/sagernet/sing-shadowsocks v0.2.7 // indirect
	github.com/skip2/go-qrcode v0.0.0-20200617195104-da1b6568686e // indirect
	github.com/vishvananda/netlink v1.3.1 // indirect
	github.com/vishvananda/netns v0.0.5 // indirect
	github.com/wlynxg/anet v0.0.5 // indirect
	github.com/xjasonlyu/windivert-go v0.0.0-20201010013527-4239d0afa76f // indirect
	github.com/xtls/reality v0.0.0-20260322125925-9234c772ba8f // indirect
	github.com/xtls/xray-core v1.260327.1-0.20260728075948-5ca6f4b7d4dc // indirect
	go4.org/netipx v0.0.0-20231129151722-fdeea329fbba // indirect
	golang.org/x/crypto v0.58.0 // indirect
	golang.org/x/exp v0.0.0-20250711185948-6ae5c78190dc // indirect
	golang.org/x/mobile v0.0.0-20261011034528-197770444867 // indirect
	golang.org/x/mod v0.42.0 // indirect
	golang.org/x/net v0.61.0 // indirect
	golang.org/x/sync v0.24.0 // indirect
	golang.org/x/sys v0.49.0 // indirect
	golang.org/x/text v0.43.0 // indirect
	golang.org/x/time v0.15.0 // indirect
	golang.org/x/tools v0.52.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	golang.zx2c4.com/wireguard v0.0.0-20260522210424-ecfc5a8d5446 // indirect
	golang.zx2c4.com/wireguard/windows v1.0.1 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20260414002931-afd174a4e478 // indirect
	google.golang.org/grpc v1.82.1 // indirect
	google.golang.org/protobuf v1.36.11 // indirect
	gopkg.in/yaml.v2 v2.4.0 // indirect
	gvisor.dev/gvisor v0.0.0-20260530041128-23ef90c42be7 // indirect
	lukechampine.com/blake3 v1.4.1 // indirect
)

// All three are local checkouts. Replace directives only take effect in the main
// module, so openflux-mobile's own `replace ... => ..` does not apply when it is a
// dependency here — mobile-core must point at the OpenFlux root itself.
replace github.com/GXUser7/yumi/core-xray => ../core-xray

replace github.com/p1neappleXpress/OpenFlux => ../third_party/OpenFlux

replace openflux-mobile => ../third_party/OpenFlux/mobile

// OpenFlux ships a one-change copy of pion/sctp (adds SetOutboundMTU, used by the
// WebRTC transport) and replaces it in ITS go.mod with ./third_party/sctp. That
// replace does not reach us (replaces only apply in the main module), so mirror it
// here with the absolute path, else gomobile fails: undefined sctp.SetOutboundMTU.
replace github.com/pion/sctp => ../third_party/OpenFlux/third_party/sctp

tool golang.org/x/mobile/cmd/gobind
