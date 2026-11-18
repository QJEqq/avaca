# Политика конфиденциальности Avaca

*Действует с 18 ноября 2026 года. [English below](#avaca-privacy-policy).*

Avaca — VPN-клиент с открытым исходным кодом. У приложения нет своего сервера, и разработчик не получает от него никаких данных.

## Чего нет

- Аналитики, рекламы, отчётов о сбоях, сторонних SDK для сбора данных.
- Аккаунтов и регистрации.
- Передачи, продажи или анализа вашего трафика.

## Что хранится на устройстве

- Подписки, серверы и настройки — в личной папке приложения.
- Журнал приложения — там же; в папку «Загрузки» он попадает только по кнопке «Сохранить».
- Журнал соединений (режим разработчика) — только в памяти, пока режим включён.
- Cookies для входа в сервис, если его требует канал OpenFlux, — только на устройстве.

Удаление приложения удаляет всё это.

## Куда приложение обращается само

| Куда | Зачем | Что уходит |
| --- | --- | --- |
| Серверы подписок, которые вы добавили | Получить список серверов | User-Agent с версией приложения, версией Android и моделью устройства. Для подписок по HTTPS — случайный идентификатор установки (`x-hwid`), название и версия ОС, модель устройства (`x-device-os`, `x-ver-os`, `x-device-model`): по ним панели вроде Remnawave считают устройства. Ничего из этого не уходит никуда, кроме сервера подписки. |
| VPN-серверы, которые вы выбрали | Само VPN-соединение | Ваш трафик, зашифрованный протоколом сервера. |
| DNS-резолверы из настроек (по умолчанию Cloudflare `1.1.1.1` по DoH и `8.8.8.8`) | Разрешение имён | Имена сайтов, которые открывают приложения. |
| GitHub (релизы v2fly/geoip и v2fly/domain-list-community) | Скачать базы маршрутизации | Обычный запрос на скачивание файла. |
| `cp.cloudflare.com`, `connectivitycheck.gstatic.com` | Проверить, что туннель и DNS работают | Пустой запрос `/generate_204`. |
| `api.ipify.org` | Диагностика в режиме разработчика, только по кнопке | Запрос внешнего IP. |
| Сервисы канала OpenFlux | Передача трафика через канал, выданный вашим провайдером | Трафик туннеля. |

## Как используется VpnService

Avaca использует системный `VpnService` только для того, чтобы направить трафик устройства через серверы, которые выбрали вы, по правилам маршрутизации, которые задали вы или ваш провайдер. Содержимое трафика приложение не читает, не сохраняет и не передаёт.

## Разрешения

| Разрешение | Зачем |
| --- | --- |
| Интернет, состояние сети | Подключение к серверам, проверка сети |
| Уведомления, работа в фоне | Уведомление о работающем туннеле, сообщения об автопереключении |
| Камера | Сканирование QR-кодов с подписками и серверами — только когда вы открыли сканер |
| Список приложений | Раздельное туннелирование: выбрать приложения, которые идут мимо VPN или только через него |
| Запуск при включении | Автоподключение после перезагрузки, если вы его включили |
| Работа без ограничений батареи | Чтобы система не останавливала туннель в режиме сна; спрашивается один раз |
| Запись в хранилище (Android 9 и старше) | Сохранение журнала в «Загрузки» |

## Дети

Приложение не предназначено для детей и не собирает данных о них.

## Изменения и связь

Изменения этой политики публикуются в этом файле: [PRIVACY.md](https://github.com/QJEqq/avaca/blob/main/PRIVACY.md).
Вопросы — в [issues](https://github.com/QJEqq/avaca/issues).

---

# Avaca privacy policy

*Effective 18 November 2026.*

Avaca is an open-source VPN client. The app has no server of its own, and the developer receives no data from it.

## What there is none of

- Analytics, ads, crash reporting, third-party data collection SDKs.
- Accounts or registration.
- Passing on, selling or analysing your traffic.

## What stays on the device

- Subscriptions, servers and settings — in the app's private storage.
- The app journal — there too; it reaches Downloads only when you tap "Save".
- The connection journal (developer mode) — in memory only, while the mode is on.
- Cookies for signing in to a service, when an OpenFlux channel needs one — on the device only.

Uninstalling the app removes all of it.

## Where the app connects on its own

| Where | Why | What is sent |
| --- | --- | --- |
| Subscription servers you added | Fetch the server list | A User-Agent with the app version, the Android version and the device model. For HTTPS subscriptions: a random per-install identifier (`x-hwid`), the OS name and version, the device model (`x-device-os`, `x-ver-os`, `x-device-model`), which panels such as Remnawave use to count devices. None of it goes anywhere but the subscription server. |
| VPN servers you chose | The VPN connection itself | Your traffic, encrypted by the server's protocol. |
| DNS resolvers from settings (default Cloudflare `1.1.1.1` over DoH and `8.8.8.8`) | Name resolution | The names of sites your apps open. |
| GitHub (v2fly/geoip and v2fly/domain-list-community releases) | Download routing databases | A plain file download. |
| `cp.cloudflare.com`, `connectivitycheck.gstatic.com` | Check that the tunnel and DNS work | An empty `/generate_204` request. |
| `api.ipify.org` | Developer-mode diagnostics, only on request | An external IP lookup. |
| The services of an OpenFlux channel | Carry traffic over the channel your provider issued | Tunnel traffic. |

## How VpnService is used

Avaca uses the system `VpnService` only to route the device's traffic through servers you chose, by routing rules set by you or your provider. The app does not read, store or pass on the content of that traffic.

## Permissions

| Permission | Why |
| --- | --- |
| Internet, network state | Connecting to servers, checking the network |
| Notifications, foreground service | The running-tunnel notification, failover messages |
| Camera | Scanning QR codes of subscriptions and servers — only while the scanner is open |
| Installed apps | Split tunnelling: choosing apps that bypass the VPN or use only it |
| Start at boot | Auto-connect after a reboot, if you turned it on |
| Ignore battery optimisations | So the system does not stop the tunnel in Doze; asked once |
| Storage write (Android 9 and older) | Saving the journal to Downloads |

## Children

The app is not directed at children and collects no data about them.

## Changes and contact

Changes to this policy are published in this file: [PRIVACY.md](https://github.com/QJEqq/avaca/blob/main/PRIVACY.md).
Questions go to [issues](https://github.com/QJEqq/avaca/issues).
