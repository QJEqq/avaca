# Changelog

## 1.0.1 — 2026-11-18

- Полные исходники в теге: в 1.0.0 правило `logs/` из `.gitignore` Yumi не пустило в репозиторий
  `LogsScreen.kt`, и код по тегу `v1.0.0` не собирался. Правило теперь `/logs/`.
- README: скриншоты обоих дизайнов. PRIVACY.md: в User-Agent есть модель устройства.
- Код приложения тот же, что в 1.0.0; ставится поверх него.

Complete sources in the tag: in 1.0.0 the `logs/` rule from Yumi's `.gitignore` kept `LogsScreen.kt`
out of the repository, so the `v1.0.0` tag did not build. The app code is the same as 1.0.0 and
installs over it.

## 1.0.0 — 2026-11-18

Первый релиз Avaca. Приложение начиналось как форк [Yumi](https://github.com/GXUser7/yumi) и
получило новый id пакета `app.avaca.vpn`: ставится рядом с Yumi, не поверх.

**Ядро**
- Xray-core и OpenFlux в одном `avacacore.aar`; исходники и скрипт сборки — в репозитории
  (`core/`, `core-xray/`, `third_party/OpenFlux`, `tools/build-core.sh`).
- Повтор SYN через ~3 с вместо 16 с: соединение не пропадает при переключении между приложениями.

**Подписки и конфиги**
- JSON-документы провайдера идут в ядро как есть: outbound, `dialerProxy`, маршруты и DNS.
- XHTTP `extra`, Final Mask (`fm`), `pinSHA256` у Hysteria2 — из ссылок и из JSON.
- Документы `/<uuid>/json` сами прикрепляются к серверам из ссылок.
- DNS провайдера опрашивается параллельно со своим DoH.
- Ссылки `avaca://` для добавления подписок.

**Интерфейс**
- Два дизайна — Avaca и Git; выбор при первом запуске и в Настройки → Оформление.
- Семь иконок приложения, иконка в шторке.
- Свои правила маршрутизации: через VPN / напрямую / блок.
- Режим разработчика: диагностика по шагам, конфиг Xray, журнал соединений.

**Убрано из Yumi**
- Приложение для телевизора и пульт, замер скорости, обновления с GitHub Yumi, отдельный список
  серверов для мобильной сети, отправка журнала в Telegram автора Yumi.

---

First Avaca release. The app started as a fork of [Yumi](https://github.com/GXUser7/yumi) and now
has its own package id, `app.avaca.vpn`: it installs next to Yumi, not over it.

- **Core:** Xray-core and OpenFlux in one `avacacore.aar`, built from sources in this repository;
  SYN retried after ~3 s instead of 16 s, so switching apps no longer drops the connection.
- **Subscriptions:** provider JSON passed to the core as written (outbound, `dialerProxy`, routing,
  DNS); XHTTP `extra`, Final Mask, Hysteria2 `pinSHA256`; `/<uuid>/json` documents attached to link
  servers automatically; provider DNS raced against your own DoH; `avaca://` links.
- **Interface:** two designs (Avaca and Git) chosen on first launch or in Settings → Appearance;
  seven app icons; own routing rules; developer-mode diagnostics, Xray config and connection journal.
- **Removed from Yumi:** the TV app and remote, the speed test, updates from Yumi's GitHub, the
  separate mobile-network server list, sending the journal to the Yumi author's Telegram.
