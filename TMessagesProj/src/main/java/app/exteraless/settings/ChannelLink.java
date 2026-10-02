package app.exteraless.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Канал в ссылках настроек. По умолчанию beli_meow.
 * Имя забирается из update/channel.md, сверяется с Telegram и кэшируется:
 * без сети остаётся последняя полученная копия.
 * Смена с аккаунта {@link #ADMIN_ID} пишется в описание якорных каналов,
 * чтобы остальные клиенты подхватили её при следующей проверке.
 */
public final class ChannelLink {

    public static final String DEFAULT = "beli_meow";
    public static final long ADMIN_ID = 6491182415L;

    private static final String RAW = "https://raw.githubusercontent.com/Yareks/MYUWUGRAM/main/update/channel.md";
    private static final String PREFS = "uwugram_channel";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{4,32}");
    private static final Pattern MARKER = Pattern.compile("(?i)uwugram-channel:([A-Za-z0-9_]{4,32})");

    private static final long REFRESH_INTERVAL = 30 * 60 * 1000L;
    private static volatile boolean refreshing;
    private static Runnable listener;

    private ChannelLink() {
    }

    public static void setListener(Runnable listener) {
        ChannelLink.listener = listener;
    }

    public static boolean isAdmin() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            UserConfig config = UserConfig.getInstance(i);
            if (config.isClientActivated() && config.getClientUserId() == ADMIN_ID) {
                return true;
            }
        }
        return false;
    }

    public static int adminAccount() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            UserConfig config = UserConfig.getInstance(i);
            if (config.isClientActivated() && config.getClientUserId() == ADMIN_ID) {
                return i;
            }
        }
        return -1;
    }

    public static String username() {
        String saved = prefs().getString("username", "");
        return TextUtils.isEmpty(saved) ? DEFAULT : saved;
    }

    public static String display() {
        return "@" + username();
    }

    public static String link() {
        return "https://t.me/" + username();
    }

    public static void copy(BaseFragment fragment) {
        AndroidUtilities.addToClipboard(link());
        if (fragment != null) {
            BulletinFactory.of(fragment).createCopyLinkBulletin().show();
        }
    }

    public static void refresh(int account) {
        refresh(account, false);
    }

    public static void refresh(int account, boolean force) {
        if (refreshing) {
            return;
        }
        if (!force && Math.abs(System.currentTimeMillis() - prefs().getLong("last_check", 0)) < REFRESH_INTERVAL) {
            return;
        }
        refreshing = true;
        final int readAccount = account;
        new Thread(() -> {
            String github = fetchGithub();
            AndroidUtilities.runOnUIThread(() -> verify(readAccount, github));
        }, "uwu-channel").start();
    }

    public interface ChangeCallback {
        void onResult(boolean found, boolean published);
    }

    /** Проверить имя и опубликовать его для остальных клиентов. */
    public static void change(BaseFragment fragment, String raw, ChangeCallback callback) {
        final String name = normalize(raw);
        final int account = adminAccount();
        if (name == null || account < 0) {
            if (callback != null) {
                callback.onResult(false, false);
            }
            return;
        }
        resolve(account, name, (chat, full) -> {
            if (chat == null) {
                tryRename(fragment, account, name, callback);
                return;
            }
            final String previous = username();
            final boolean[] published = new boolean[]{false};
            final ArrayList<String> anchors = anchors(previous);
            writeMarkers(account, anchors, name, 0, published, () -> {
                saveUsername(name, chat);
                if (callback != null) {
                    callback.onResult(true, published[0] || name.equalsIgnoreCase(previous));
                }
            });
        });
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim();
        int at = name.lastIndexOf('@');
        if (at >= 0 && at < name.length() - 1) {
            name = name.substring(at + 1);
        }
        name = name.replace("https://", "").replace("http://", "");
        if (name.startsWith("t.me/") || name.startsWith("telegram.me/")) {
            name = name.substring(name.indexOf('/') + 1);
        }
        int slash = name.indexOf('/');
        if (slash >= 0) {
            name = name.substring(0, slash);
        }
        int query = name.indexOf('?');
        if (query >= 0) {
            name = name.substring(0, query);
        }
        name = name.trim();
        return NAME.matcher(name).matches() ? name : null;
    }

    private static void verify(int account, String github) {
        SharedPreferences prefs = prefs();
        if (!TextUtils.isEmpty(github) && !github.equalsIgnoreCase(prefs.getString("last_github", ""))) {
            prefs.edit().putString("username", github).putString("last_github", github).apply();
        }
        if (!UserConfig.getInstance(account).isClientActivated()) {
            refreshing = false;
            notifyListener();
            return;
        }
        resolve(account, username(), (chat, full) -> {
            if (chat != null) {
                applyResolved(chat, full, account);
            } else if (prefs().getLong("channel_id", 0) != 0) {
                loadStored(account);
            } else {
                finishRefresh();
            }
        });
    }

    private static void applyResolved(TLRPC.Chat chat, TLRPC.ChatFull full, int account) {
        saveChat(chat);
        String marker = marker(full);
        String current = username();
        if (!TextUtils.isEmpty(marker) && !marker.equalsIgnoreCase(current)) {
            prefs().edit().putString("username", marker).apply();
            resolve(account, marker, (next, nextFull) -> {
                if (next != null) {
                    saveChat(next);
                    String renamed = ChatObject.getPublicUsername(next);
                    if (!TextUtils.isEmpty(renamed)) {
                        prefs().edit().putString("username", renamed).apply();
                    }
                }
                finishRefresh();
            });
            return;
        }
        String renamed = ChatObject.getPublicUsername(chat);
        if (!TextUtils.isEmpty(renamed) && !renamed.equalsIgnoreCase(current)) {
            prefs().edit().putString("username", renamed).apply();
        }
        finishRefresh();
    }

    private static void loadStored(int account) {
        TLRPC.TL_channels_getFullChannel req = new TLRPC.TL_channels_getFullChannel();
        TLRPC.TL_inputChannel input = new TLRPC.TL_inputChannel();
        input.channel_id = prefs().getLong("channel_id", 0);
        input.access_hash = prefs().getLong("access_hash", 0);
        req.channel = input;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_messages_chatFull) {
                TLRPC.TL_messages_chatFull full = (TLRPC.TL_messages_chatFull) response;
                MessagesController.getInstance(account).putChats(full.chats, false);
                TLRPC.Chat chat = full.chats.isEmpty() ? null : full.chats.get(0);
                if (chat != null) {
                    applyResolved(chat, full.full_chat, account);
                    return;
                }
            }
            finishRefresh();
        }));
    }

    private static void tryRename(BaseFragment fragment, int account, String name, ChangeCallback callback) {
        long channelId = prefs().getLong("channel_id", 0);
        if (channelId == 0 || MessagesController.getInstance(account).getChat(channelId) == null) {
            if (callback != null) {
                callback.onResult(false, false);
            }
            return;
        }
        MessagesController.getInstance(account).updateChannelUserName(fragment, channelId, name, () -> {
            prefs().edit().putString("username", name).apply();
            notifyListener();
            if (callback != null) {
                callback.onResult(true, true);
            }
        }, () -> {
            if (callback != null) {
                callback.onResult(false, false);
            }
        });
    }

    private static ArrayList<String> anchors(String previous) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        String github = prefs().getString("last_github", "");
        if (!TextUtils.isEmpty(github)) {
            names.add(github);
        }
        names.add(DEFAULT);
        if (!TextUtils.isEmpty(previous)) {
            names.add(previous);
        }
        return new ArrayList<>(names);
    }

    private static void writeMarkers(int account, ArrayList<String> anchors, String name, int index, boolean[] published, Runnable done) {
        if (index >= anchors.size()) {
            done.run();
            return;
        }
        String anchor = anchors.get(index);
        if (anchor.equalsIgnoreCase(name)) {
            writeMarkers(account, anchors, name, index + 1, published, done);
            return;
        }
        resolve(account, anchor, (chat, full) -> {
            if (chat == null || full == null) {
                writeMarkers(account, anchors, name, index + 1, published, done);
                return;
            }
            editAbout(account, chat, full, withMarker(full.about, name), () -> {
                published[0] = true;
                writeMarkers(account, anchors, name, index + 1, published, done);
            }, () -> writeMarkers(account, anchors, name, index + 1, published, done));
        });
    }

    private static void editAbout(int account, TLRPC.Chat chat, TLRPC.ChatFull full, String about, Runnable ok, Runnable fail) {
        TLRPC.TL_messages_editChatAbout req = new TLRPC.TL_messages_editChatAbout();
        req.peer = MessagesController.getInstance(account).getInputPeer(-chat.id);
        req.about = about;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_boolTrue) {
                full.about = about;
                ok.run();
            } else {
                fail.run();
            }
        }));
    }

    private interface Resolved {
        void onResolved(TLRPC.Chat chat, TLRPC.ChatFull full);
    }

    private static void resolve(int account, String name, Resolved callback) {
        if (TextUtils.isEmpty(name) || !UserConfig.getInstance(account).isClientActivated()) {
            callback.onResolved(null, null);
            return;
        }
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = name;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (!(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                AndroidUtilities.runOnUIThread(() -> callback.onResolved(null, null));
                return;
            }
            TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
            MessagesController controller = MessagesController.getInstance(account);
            AndroidUtilities.runOnUIThread(() -> {
                controller.putUsers(resolved.users, false);
                controller.putChats(resolved.chats, false);
                TLRPC.Chat chat = resolved.chats.isEmpty() ? null : resolved.chats.get(0);
                if (chat == null) {
                    callback.onResolved(null, null);
                    return;
                }
                TLRPC.TL_channels_getFullChannel fullReq = new TLRPC.TL_channels_getFullChannel();
                fullReq.channel = MessagesController.getInputChannel(chat);
                ConnectionsManager.getInstance(account).sendRequest(fullReq, (fullResponse, fullError) -> AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.ChatFull full = null;
                    if (fullResponse instanceof TLRPC.TL_messages_chatFull) {
                        TLRPC.TL_messages_chatFull packed = (TLRPC.TL_messages_chatFull) fullResponse;
                        controller.putChats(packed.chats, false);
                        full = packed.full_chat;
                        if (!packed.chats.isEmpty()) {
                            callback.onResolved(packed.chats.get(0), full);
                            return;
                        }
                    }
                    callback.onResolved(chat, full);
                }));
            });
        });
    }

    private static String marker(TLRPC.ChatFull full) {
        if (full == null || TextUtils.isEmpty(full.about)) {
            return null;
        }
        Matcher matcher = MARKER.matcher(full.about);
        String found = null;
        while (matcher.find()) {
            found = matcher.group(1);
        }
        return found;
    }

    private static String withMarker(String about, String name) {
        String line = "uwugram-channel:" + name;
        String base = about == null ? "" : MARKER.matcher(about).replaceAll("").trim();
        String next = base.isEmpty() ? line : base + "\n" + line;
        if (next.length() <= 255) {
            return next;
        }
        int keep = 255 - line.length() - 1;
        if (keep <= 0) {
            return line;
        }
        return base.substring(0, Math.min(base.length(), keep)).trim() + "\n" + line;
    }

    private static void saveUsername(String name, TLRPC.Chat chat) {
        saveChat(chat);
        prefs().edit().putString("username", name).apply();
        notifyListener();
    }

    private static void saveChat(TLRPC.Chat chat) {
        if (chat == null) {
            return;
        }
        prefs().edit().putLong("channel_id", chat.id).putLong("access_hash", chat.access_hash).apply();
    }

    private static void finishRefresh() {
        refreshing = false;
        prefs().edit().putLong("last_check", System.currentTimeMillis()).apply();
        notifyListener();
    }

    private static void notifyListener() {
        Runnable current = listener;
        if (current != null) {
            current.run();
        }
    }

    private static String fetchGithub() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RAW).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "uwugram");
            connection.connect();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String name = normalize(line);
                    if (name != null) {
                        return name;
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e("ChannelLink: fetch failed", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
