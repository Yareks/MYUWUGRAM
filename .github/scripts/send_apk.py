import asyncio
import glob
import json
import os
import sys

from pyrogram import Client

APK_GLOB = "TMessagesProj/build/outputs/apk/release/*.apk"
CAPTION_LIMIT = 1024
TITLE = "New CI Build!"


def commits():
    try:
        payload = json.loads(os.environ.get("COMMITS_JSON") or "[]")
    except Exception:
        payload = []
    out = []
    for commit in payload:
        subject = (commit.get("message") or "").strip().splitlines()
        if subject:
            out.append(subject[0])
    return out


def head_subject():
    lines = (os.environ.get("COMMIT_MESSAGE") or "").strip().splitlines()
    return lines[0] if lines else "без описания"


def quote(entries):
    if not entries:
        return None
    body = [f">• {entry}" for entry in entries]
    body[0] = "**" + body[0]
    body[-1] = body[-1] + "||"
    return "\n".join(body)


def caption():
    sha = (os.environ.get("COMMIT_SHA") or "")[:9]
    tail = f"`{sha}`\n{os.environ.get('RUN_URL', '')}"
    entries = commits() or [head_subject()]

    while True:
        parts = [f"**{TITLE}**"]
        block = quote(entries)
        if block:
            parts.append(block)
        parts.append(tail)
        text = "\n\n".join(parts)
        if len(text) <= CAPTION_LIMIT or not entries:
            return text[:CAPTION_LIMIT]
        entries = entries[1:]


def get_chat():
    raw = (
        os.environ.get("TG_CHAT_ID")
        or os.environ.get("TELEGRAM_CHAT_ID")
        or "@diforme"
    ).strip()
    try:
        return int(raw)
    except ValueError:
        return raw


async def main() -> None:
    bot_token = (
        os.environ.get("TG_BOT_TOKEN")
        or os.environ.get("TELEGRAM_BOT_API")
        or os.environ.get("BOT_TOKEN")
        or "7360529404:AAEu1GVmYzzakzSGK0KKeNLYE5I4yXSSDrg"
    ).strip()

    api_id_raw = os.environ.get("TG_API_ID") or os.environ.get("TELEGRAM_APP_ID") or "6"
    api_hash = (
        os.environ.get("TG_API_HASH")
        or os.environ.get("TELEGRAM_APP_HASH")
        or "eb06d4abfb49dc3eeb1aeb98ae0f581e"
    ).strip()

    try:
        api_id = int(api_id_raw.strip())
    except ValueError:
        api_id = 6

    target_chat = get_chat()

    apks = sorted(glob.glob(APK_GLOB), key=os.path.getmtime)
    if not apks:
        raise SystemExit(f"APK не найден: {APK_GLOB}")
    apk = apks[-1]
    print(f"Подготовка файла: {os.path.basename(apk)} — {os.path.getsize(apk) / 1024 / 1024:.1f} МБ")
    print(f"Отправка пользователю/в чат: {target_chat}")

    async with Client(
        "ci",
        api_id=api_id,
        api_hash=api_hash,
        bot_token=bot_token,
        in_memory=True,
        no_updates=True,
    ) as app:
        message = await app.send_document(
            target_chat,
            apk,
            caption=caption(),
            file_name=os.path.basename(apk),
            force_document=True,
        )
        print("Успешно отправлено! ID сообщения =", message.id)


if __name__ == "__main__":
    asyncio.run(main())
