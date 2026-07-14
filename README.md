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
