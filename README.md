# Zastogram

Полноценный Android-форк на базе [Telegram-FOSS](https://github.com/Telegram-FOSS-Team/Telegram-FOSS).

## Что делает сборка

GitHub Actions workflow:

1. скачивает `Telegram-FOSS` с submodules;
2. создаёт `API_KEYS` из GitHub Secrets `ZASTOGRAM_APP_ID` и `ZASTOGRAM_APP_HASH`;
3. переименовывает приложение в `Zastogram`;
4. меняет `applicationId` на `org.zastogram.messenger`;
5. собирает настоящий Android APK variant `afatDebug`;
6. публикует APK как artifact `zastogram-apk`.

## APK

После успешного workflow APK можно скачать в GitHub Actions artifacts.

Локальная команда для скачивания последнего artifact после сборки:

```bash
gh run download --name zastogram-apk --dir downloads
```

## Ускорение сборки

Нативные библиотеки (FFmpeg/libvpx/BoringSSL) — это основная часть времени сборки
(~14 из ~18 минут). Они меняются только когда двигается `HEAD` upstream
`Telegram-FOSS`, поэтому workflow кэширует их через `actions/cache` с ключом по
коммиту upstream:

- `.zastogram-native-cache` — собранные нативные артефакты (key:
  `zastogram-native-<abi>-v1-<upstream-sha>`);
- `~/.gradle/caches`, `~/.gradle/wrapper` — зависимости Gradle.

Пока upstream не обновился, каждый push по этому репозиторию собирается за пару
минут (только Gradle). При смене upstream кэш инвалидируется автоматически и натив
собирается заново один раз.

## Пункт "Zastogram" в настройках

Строка "Zastogram: Chat text size" в Settings открывает отдельный экран
`ZastogramTextSizeActivity` с настоящим ползунком размера шрифта чата
(`SeekBarView`, привязан к `SharedConfig.fontSize`, 12–30). Значение применяется
живьём и сохраняется.
