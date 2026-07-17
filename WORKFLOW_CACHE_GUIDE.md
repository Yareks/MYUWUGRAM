# Гайд: включить кэш сборки (ускорение ~18 мин → ~3–5 мин)

## Зачем

Нативные библиотеки (FFmpeg/libvpx/BoringSSL) — это ~14 из ~18 минут сборки.
Они меняются только когда двигается `HEAD` upstream `Telegram-FOSS`. Логика
restore/save уже есть в `scripts/build_zastogram_ci.sh`, но она пишет в
`.zastogram-native-cache` внутри рабочего пространства GitHub Actions — а оно
**стирается каждый запуск**, поэтому кэш никогда не срабатывал.

Нужно добавить `actions/cache` в `.github/workflows/build-zastogram-apk.yml`,
чтобы эта папка переживала между запусками. Это и есть ускорение.

## Почему я не запушил сам

У токена Arena нет права `workflows` — GitHub запрещает через него менять файлы
в `.github/workflows/*.yml`. Поэтому этот один файл пушит владелец репо (ты).

---

## Метод A — самый простой: редактировать прямо на GitHub (рекомендую)

1. Открой файл на редактирование в вебе (владелец репо может править напрямую):
   `https://github.com/Yareks/MYUWUGRAM/edit/arena/019f5ea9-myuwugram/.github/workflows/build-zastogram-apk.yml`
2. Удали всё содержимое и вставь ровно то, что ниже.
3. Внизу: «Commit directly to `arena/019f5ea9-myuwugram`» → **Commit changes**.
   Этот коммит сам запустит сборку (ветка уже есть в `push.branches`).

## Метод B — локально через PAT с правом `workflow`

Обычный пароль/токен не подойдёт — нужен **Personal Access Token со scope
`workflow`** (Settings → Developer settings → Personal access tokens).

```bash
git clone https://github.com/Yareks/MYUWUGRAM.git
cd MYUWUGRAM
git checkout arena/019f5ea9-myuwugram
# замени .github/workflows/build-zastogram-apk.yml на содержимое ниже
git add .github/workflows/build-zastogram-apk.yml
git commit -m "Cache native libs and Gradle deps for faster CI"
git push origin arena/019f5ea9-myuwugram
```

При `push` используй логин + PAT (со scope `workflow`) вместо пароля.

---

## Новое содержимое `.github/workflows/build-zastogram-apk.yml`

```yaml
name: Build Zastogram APK

on:
  workflow_dispatch:
  push:
    branches:
      - arena/019f5df1-myuwugram
      - arena/019f5ea9-myuwugram

jobs:
  build:
    name: Build full Zastogram APK
    runs-on: ubuntu-22.04
    timeout-minutes: 360

    env:
      BUILD_ANDROID_ABI: arm64-v8a
      BUILD_NATIVE_ARCHES: arm64

    steps:
      - name: Checkout session repository
        uses: actions/checkout@v4

      - name: Setup Java 11
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '11'

      - name: Install Linux build dependencies
        run: |
          sudo apt-get update
          sudo apt-get install -y ninja-build golang yasm nasm cmake make pkg-config

      - name: Install Android SDK/NDK components
        run: |
          echo "ANDROID_HOME=${ANDROID_HOME}"
          echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}"
          export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/tools/bin:${ANDROID_HOME}/platform-tools:${PATH}"
          command -v sdkmanager
          sdkmanager --install \
            'platforms;android-33' \
            'build-tools;33.0.0' \
            'cmake;3.10.2.4988404' \
            'ndk;21.4.7075529'
          yes | sdkmanager --licenses >/dev/null || true

      - name: Resolve Telegram-FOSS upstream commit
        id: upstream
        run: |
          sha=$(git ls-remote https://github.com/Telegram-FOSS-Team/Telegram-FOSS.git HEAD | awk '{print $1}')
          echo "sha=$sha" >> "$GITHUB_OUTPUT"
          echo "Upstream Telegram-FOSS commit: $sha"

      # Натив (FFmpeg/libvpx/BoringSSL) — ~14 из ~18 минут. Меняется только при
      # сдвиге HEAD upstream, поэтому кэш по коммиту upstream превращает почти
      # каждый пуш в быструю Gradle-сборку.
      - name: Cache native libraries
        uses: actions/cache@v4
        with:
          path: .zastogram-native-cache
          key: zastogram-native-${{ env.BUILD_ANDROID_ABI }}-v1-${{ steps.upstream.outputs.sha }}

      - name: Cache Gradle dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: zastogram-gradle-v1-${{ steps.upstream.outputs.sha }}
          restore-keys: |
            zastogram-gradle-v1-

      - name: Build APK from Telegram-FOSS upstream
        env:
          ZASTOGRAM_APP_ID: ${{ secrets.ZASTOGRAM_APP_ID }}
          ZASTOGRAM_APP_HASH: ${{ secrets.ZASTOGRAM_APP_HASH }}
        run: scripts/build_zastogram_ci.sh

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: zastogram-apk
          path: zastogram-output/*.apk
          if-no-files-found: error
```

## Что ожидать

- **Первый запуск** после правки: всё ещё ~18 мин — это cache miss, натив
  соберётся и сохранится в кэш.
- **Второй и далее** (пока upstream `Telegram-FOSS` не обновился): ~3–5 мин —
  только Gradle + pull исходников.
- Когда upstream сдвинется — кэш автоматически инвалидируется и натив соберётся
  заново один раз.

## Проверить лог кэша

В логе сборки ищи строки:
- `Cache native libraries` → `Cache hit` (попадание) или `Cache restored`/`miss`.
- Внутри скрипта: `== Restoring native cache ...` (попадание) или
  `== Native cache miss: building native dependencies ...`.

---

# Дополнительно: включить генерацию кастомной иконки (нужен ImageMagick)

Иконка приложения (`branding/icon.png`) заменяется в момент сборки скриптом
`scripts/generate_meowgram_icon.sh`, которому нужен ImageMagick. В текущем
workflow он **не установлен**, поэтому генератор аккуратно пропускается
(сборка не ломается, остаётся upstream-иконка).

Чтобы кастомная иконка применилась, добавь `imagemagick` в шаг установки
зависимостей в `.github/workflows/build-zastogram-apk.yml`:

Было:
```yaml
      - name: Install Linux build dependencies
        run: |
          sudo apt-get update
          sudo apt-get install -y ninja-build golang yasm nasm cmake make pkg-config
```

Стало (добавлен `imagemagick` в конец):
```yaml
      - name: Install Linux build dependencies
        run: |
          sudo apt-get update
          sudo apt-get install -y ninja-build golang yasm nasm cmake make pkg-config imagemagick
```

(Этот файл не может править токен Arena — нет права `workflows`. Владелец репо
редактирует его в вебе или пушит с PAT со scope `workflow`.)

После правки: замени `branding/icon.png` на свою картинку и запусти сборку —
иконка приложения во всей системе станет твоей с полупрозрачным самолётиком
Telegram поверх.

---

# Прямая ссылка на APK через GitHub Release (фикс «пакет повреждён»)

APK из artifacts качается как 63-МБ zip, который часто обрывается → Android
пишет «пакет повреждён». Чтобы получить **прямую публичную ссылку на сам .apk**
(без zip, без логина), добавь этот шаг в конец `.github/workflows/build-zastogram-apk.yml`,
сразу после блока «Upload APK artifact»:

```yaml
      - name: Publish APK to GitHub Release
        if: success()
        uses: softprops/action-gh-release@v2
        with:
          tag_name: meowgram-${{ github.run_id }}
          name: MeowGram build ${{ github.run_number }}
          files: zastogram-output/*.apk
          prerelease: true
          make_latest: false
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

После следующей сборки в разделе **Releases** репозитория появится прямой APK.
Ссылка будет вида:
`https://github.com/Yareks/MYUWUGRAM/releases/download/meowgram-<id>/zastogram-afat-debug.apk`
— публичная, качается целиком в один клик, без zip.

(Этот шаг не может добавить токен Arena — нет права `workflows`. Владелец репо
вставляет его в веб-редакторе файла или пушит с PAT со scope `workflow`.)
