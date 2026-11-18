# Разработка Avaca

Коротко о том, как устроен проект и как его собрать. История форка — коммит `00ce7c8`, исходное
состояние [Yumi](https://github.com/GXUser7/yumi); в его `DEVELOPMENT.md` подробно разобраны
решения, унаследованные от Yumi (генератор конфигурации, замер задержки, проверка сертификатов).

## Устройство

| Путь | Что там |
| --- | --- |
| `app/` | Приложение: экраны на Compose, `MainViewModel`, навигация, иконки, выбор дизайна |
| `shared/` | Всё без интерфейса: модель, разбор ссылок и подписок, генератор конфигурации Xray (`core/xray/XrayConfigFactory.kt`), `VpnService`, автопереключение, диагностика |
| `design/` | Тема и общие компоненты: палитры Avaca и Git (`ui/theme/AvacaTheme.kt`), группы настроек |
| `core-xray/` | Go-привязка Xray (пакет `yumi`): запуск ядра, защита сокетов, повтор SYN |
| `core/` | Модуль, который связывает `core-xray` и OpenFlux в одно ядро |
| `third_party/OpenFlux` | Подмодуль: форк OpenFlux, ветка `avaca-0.17` |
| `tools/build-core.sh` | Сборка ядра в `shared/libs/avacacore.aar` |

Ядро — один `.aar` с одной средой Go: `gomobile bind` собирает `core-xray` и `openflux-mobile`
за один вызов. Два отдельных `.aar` принесли бы две `libgojni.so` по одному пути, и APK не собрался
бы. Kotlin видит ядро через пакеты `com.mydrop.vpn.xray.yumi.*` и `com.mydrop.vpn.xray.mobile.*`.

Пакеты Kotlin остались `com.mydrop.vpn.*` от Yumi; id приложения — `app.avaca.vpn`
(`app.avaca.vpn.debug` у отладочной сборки).

## Что нужно

- JDK 17+ (подойдёт JBR из Android Studio), Android SDK с платформой 37.
- Для ядра: Go не старше версии из `core/go.mod`, NDK, `gomobile` и `gobind` той версии, что
  закреплена в `core/go.mod`:

  ```bash
  (cd core && go install golang.org/x/mobile/cmd/gomobile golang.org/x/mobile/cmd/gobind)
  ```

## Сборка

```bash
git clone --recurse-submodules https://github.com/QJEqq/avaca.git
cd avaca
tools/build-core.sh            # arm64-v8a; `both` — ещё и armeabi-v7a, для релиза
./gradlew :app:assembleDebug   # app/build/outputs/apk/debug/
```

Без Go: скачать `avacacore-<версия>.aar` из релиза и положить в `shared/libs/avacacore.aar`.
Папка `shared/libs/` в `.gitignore` — двоичное ядро в репозиторий не попадает.

На машине с 8 ГБ памяти в `gradle.properties` уже стоят ограничения (`-Xmx2048m`, без
параллельной сборки). `kotlin.compiler.execution.strategy=in-process` не включать — ломает плагин
Compose.

## Тесты

```bash
./gradlew :shared:testDebugUnitTest
```

CI (`.github/workflows/build.yml`) собирает ядро, гоняет тесты, проверяет сгенерированные
конфигурации настоящим Xray той же версии (`xray run -test`) и собирает debug-APK.

## Релиз

Ключ подписи лежит вне репозитория, по умолчанию в `~/.avaca-signing/`:

| Файл | Что внутри |
| --- | --- |
| `avaca-release.jks` | ключ RSA-4096, PKCS12 |
| `keystore.properties` | `storeFile`, `storePassword`, `keyAlias`, `keyPassword` |

Другое место — `-Pavaca.signingDir=…` или `AVACA_SIGNING_DIR=…`. **Ключ невосстановим**: Android
узнаёт приложение по подписи, без ключа обновить установленную Avaca нельзя. Нужна резервная копия
не на этой машине.

```bash
tools/build-core.sh both
./gradlew :app:assembleRelease       # app/build/outputs/apk/release/
./gradlew :app:bundleRelease         # .aab для Google Play
```

В релиз на GitHub кладутся APK для обеих архитектур, `avacacore-<версия>.aar` и `SHA256SUMS.txt`;
тег — `v<версия>`, версия — `versionName` в `app/build.gradle.kts` (`versionCode` растёт на 1
с каждым релизом).

## Грабли

- Xray разбирает конфигурацию со строгим набором полей: одно незнакомое поле роняет весь документ.
  Поэтому конфиги оператора чистятся от полей, которые ядро удалило (`allowInsecure`, `flow`
  у Trojan, старый `noise`).
- AdGuard на ноде с Remnawave оставляет в контейнере `nameserver 127.0.0.53`, и REALITY перестаёт
  работать — похоже на блокировку, но это DNS ноды.
- Без `-checklinkname=0` ядро не линкуется: `wlynxg/anet`, через который Xray видит интерфейсы на
  Android 11+, обращается к `net.zoneCache` через `//go:linkname` (см. `tools/build-core.sh`).
- NDK 30 изредка падает (crash clang) на `golang.org/x/mobile/internal/mobileinit` при нехватке
  памяти; повторный запуск `tools/build-core.sh` проходит.
