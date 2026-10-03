package app.exteraless.menu;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;

import java.util.HashSet;

/**
 * Порядок и состав шести меню «⋮».
 * Числа id совпадают с константами ProfileActivity и ChatActivity — по ним
 * живое меню прячет и переставляет уже созданные пункты.
 */
public final class OverflowMenus {

    public static final int OWN_PROFILE = 0;
    public static final int OTHER_PROFILE = 1;
    public static final int CHAT = 2;
    public static final int SAVED = 3;
    public static final int OWN_CHANNEL = 4;
    public static final int OTHER_CHANNEL = 5;
    public static final int COUNT = 6;

    private static final String PREFS = "uwugram_overflow_menu";

    public static final class Entry {
        public final int id;
        public final int icon;
        public final int titleRes;
        public final boolean destructive;

        Entry(int id, int icon, int titleRes, boolean destructive) {
            this.id = id;
            this.icon = icon;
            this.titleRes = titleRes;
            this.destructive = destructive;
        }
    }

    private static Entry[][] catalogs;

    private OverflowMenus() {
    }

    public static int kindForChat(TLRPC.User user, TLRPC.Chat chat) {
        if (user != null && (user.self || UserObject.isUserSelf(user))) {
            return SAVED;
        }
        if (chat != null && ChatObject.isChannel(chat) && !chat.megagroup) {
            return chat.creator || ChatObject.hasAdminRights(chat) ? OWN_CHANNEL : OTHER_CHANNEL;
        }
        return CHAT;
    }

    public static int titleRes(int kind) {
        switch (kind) {
            case OWN_PROFILE:
                return R.string.OEMenuOwnProfile;
            case OTHER_PROFILE:
                return R.string.OEMenuOtherProfile;
            case CHAT:
                return R.string.OEMenuChat;
            case SAVED:
                return R.string.OEMenuSaved;
            case OWN_CHANNEL:
                return R.string.OEMenuOwnChannel;
            case OTHER_CHANNEL:
                return R.string.OEMenuOtherChannel;
            default:
                return R.string.OEAppearanceMenu;
        }
    }

    public static Entry[] catalog(int kind) {
        if (catalogs == null) {
            catalogs = buildCatalogs();
        }
        if (kind < 0 || kind >= catalogs.length) {
            return new Entry[0];
        }
        return catalogs[kind];
    }

    public static Entry find(int kind, int id) {
        for (Entry entry : catalog(kind)) {
            if (entry.id == id) {
                return entry;
            }
        }
        return null;
    }

    /** Пока пользователь ничего не менял, живое меню не трогаем. */
    public static boolean isCustom(int kind) {
        return prefs().contains(orderKey(kind));
    }

    /**
     * Видимый порядок. Без настройки — каталог целиком.
     * Скрытые id сюда не входят.
     */
    public static int[] order(int kind) {
        if (!isCustom(kind)) {
            return defaultOrder(kind);
        }
        return parse(prefs().getString(orderKey(kind), ""));
    }

    public static int[] hiddenIds(int kind) {
        if (!isCustom(kind)) {
            return new int[0];
        }
        return parse(prefs().getString(hiddenKey(kind), ""));
    }

    public static HashSet<Integer> hiddenSet(int kind) {
        HashSet<Integer> set = new HashSet<>();
        for (int id : hiddenIds(kind)) {
            set.add(id);
        }
        return set;
    }

    /**
     * Пункт, который код добавляет только по флагу NaConfig.
     * После правки меню решает редактор, до неё — прежний флаг.
     */
    public static boolean include(int kind, int id, boolean fallback) {
        if (!isCustom(kind) || find(kind, id) == null) {
            return fallback;
        }
        return !hiddenSet(kind).contains(id);
    }

    public static void save(int kind, int[] visible, int[] hidden) {
        prefs().edit()
                .putString(orderKey(kind), join(visible))
                .putString(hiddenKey(kind), join(hidden))
                .apply();
    }

    public static void reset(int kind) {
        prefs().edit().remove(orderKey(kind)).remove(hiddenKey(kind)).apply();
    }

    public static void apply(ActionBarMenuItem item, int kind) {
        if (item == null) {
            return;
        }
        if (!isCustom(kind)) {
            item.clearSubItemOrder();
            return;
        }
        item.setSubItemOrder(order(kind), hiddenIds(kind));
    }

    private static int[] defaultOrder(int kind) {
        Entry[] entries = catalog(kind);
        int[] ids = new int[entries.length];
        for (int i = 0; i < entries.length; i++) {
            ids[i] = entries[i].id;
        }
        return ids;
    }

    private static String orderKey(int kind) {
        return "order_" + kind;
    }

    private static String hiddenKey(int kind) {
        return "hidden_" + kind;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String join(int[] ids) {
        if (ids == null || ids.length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(ids[i]);
        }
        return out.toString();
    }

    private static int[] parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new int[0];
        }
        String[] parts = raw.split(",");
        int[] ids = new int[parts.length];
        int n = 0;
        for (String part : parts) {
            try {
                ids[n++] = Integer.parseInt(part.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (n == ids.length) {
            return ids;
        }
        int[] trimmed = new int[n];
        System.arraycopy(ids, 0, trimmed, 0, n);
        return trimmed;
    }

    private static Entry e(int id, int icon, int title, boolean destructive) {
        return new Entry(id, icon, title, destructive);
    }

    private static Entry[][] buildCatalogs() {
        Entry[][] all = new Entry[COUNT][];
        // ProfileActivity: edit_info=30, add_photo=36, edit_color=40, set_username=43,
        // copy_link_profile=42, clear_cache=104.
        all[OWN_PROFILE] = new Entry[]{
                e(30, R.drawable.msg_edit, R.string.EditInfo, false),
                e(36, R.drawable.msg_addphoto, R.string.AddPhoto, false),
                e(40, R.drawable.menu_profile_colors, R.string.ProfileColorEdit, false),
                e(43, R.drawable.menu_username_change, R.string.ProfileUsernameEdit, false),
                e(42, R.drawable.msg_link2, R.string.ProfileCopyLink, false),
                e(104, R.drawable.msg_delete, R.string.ClearCache, false)
        };
        // add_contact=1, block=2, share_contact=3, edit_contact=4, delete_contact=5,
        // share=10, add_shortcut=14, start_secret_chat=20, report=24, gift=38,
        // bot_privacy=44, enable_no_forwards=46, message_filter=103, add_to_folder=105, shadow_ban=107.
        all[OTHER_PROFILE] = new Entry[]{
                e(103, R.drawable.hide_title, R.string.RegexFilters, false),
                e(105, R.drawable.msg_folders, R.string.FilterAddTo, false),
                e(104, R.drawable.msg_clear, R.string.ClearCache, false),
                e(1, R.drawable.msg_addcontact, R.string.AddContact, false),
                e(10, R.drawable.msg_share, R.string.BotShare, false),
                e(3, R.drawable.msg_share, R.string.ShareContact, false),
                e(44, R.drawable.menu_privacy_policy, R.string.BotPrivacyPolicy, false),
                e(107, R.drawable.hide_title, R.string.ShadowBan, false),
                e(2, R.drawable.msg_block, R.string.BlockContact, true),
                e(4, R.drawable.msg_edit, R.string.EditContact, false),
                e(5, R.drawable.msg_delete, R.string.DeleteContact, true),
                e(38, R.drawable.msg_gift_premium, R.string.ProfileSendAGift, false),
                e(20, R.drawable.msg_secret, R.string.StartEncryptedChat, false),
                e(46, R.drawable.menu_share_off_24, R.string.DisableSharing, false),
                e(14, R.drawable.msg_home, R.string.AddShortcut, false),
                e(24, R.drawable.msg_report, R.string.ReportBot, true)
        };
        // ChatActivity headerItem: call=32, video_call=33, search=40, boost_group=29,
        // translate=62, nkheaderbtn_linked_chat=2005, share_contact=17, change_colors=27,
        // clear_history=15, to_the_beginning=200, to_the_message=201, bookmarks=2040,
        // hide_title=2029, viewDeleted=2101, clearDeleted=2100, delete own=2004,
        // delete_chat=16, bot_settings=31, bot_help=30, report=21, pinned=2003.
        all[CHAT] = new Entry[]{
                e(32, R.drawable.msg_callback, R.string.Call, false),
                e(33, R.drawable.msg_videocall, R.string.VideoCall, false),
                e(40, R.drawable.msg_search, R.string.Search, false),
                e(2003, R.drawable.msg_pin, R.string.PinnedMessage, false),
                e(29, R.drawable.boosts, R.string.BoostingBoostGroupMenu, false),
                e(62, R.drawable.msg_translate, R.string.TranslateMessage, false),
                e(2005, R.drawable.msg_channel, R.string.LinkedChannelChat, false),
                e(17, R.drawable.msg_addcontact, R.string.AddToContacts, false),
                e(27, R.drawable.msg_background, R.string.SetWallpapers, false),
                e(15, R.drawable.msg_clear, R.string.ClearHistory, false),
                e(200, R.drawable.ic_upward, R.string.ToTheBeginning, false),
                e(201, R.drawable.msg_go_up, R.string.ToTheMessage, false),
                e(2040, R.drawable.msg_fave, R.string.BookmarksManager, false),
                e(2029, R.drawable.hide_title, R.string.HideTitle, false),
                e(2101, R.drawable.msg_view_file, R.string.ViewDeleted, false),
                e(2100, R.drawable.msg_clear, R.string.ClearDeleted, false),
                e(2004, R.drawable.msg_delete, R.string.DeleteAllFromSelf, true),
                e(31, R.drawable.msg_settings_old, R.string.BotSettings, false),
                e(30, R.drawable.msg_help, R.string.BotHelp, false),
                e(21, R.drawable.msg_report, R.string.ReportBot, true),
                e(16, R.drawable.msg_delete, R.string.DeleteChatUser, true)
        };
        // view_as_topics=59, add_shortcut=24.
        all[SAVED] = new Entry[]{
                e(59, R.drawable.msg_topics, R.string.SavedViewAsChats, false),
                e(40, R.drawable.msg_search, R.string.Search, false),
                e(2003, R.drawable.msg_pin, R.string.PinnedMessage, false),
                e(62, R.drawable.msg_translate, R.string.TranslateMessage, false),
                e(27, R.drawable.msg_background, R.string.SetWallpapers, false),
                e(24, R.drawable.msg_home, R.string.AddShortcut, false),
                e(15, R.drawable.msg_clear, R.string.ClearHistory, false),
                e(200, R.drawable.ic_upward, R.string.ToTheBeginning, false),
                e(201, R.drawable.msg_go_up, R.string.ToTheMessage, false),
                e(2040, R.drawable.msg_fave, R.string.BookmarksManager, false),
                e(2029, R.drawable.hide_title, R.string.HideTitle, false),
                e(2101, R.drawable.msg_view_file, R.string.ViewDeleted, false),
                e(2100, R.drawable.msg_clear, R.string.ClearDeleted, false),
                e(16, R.drawable.msg_delete, R.string.DeleteChatUser, true)
        };
        Entry[] ownChannel = new Entry[]{
                e(40, R.drawable.msg_search, R.string.Search, false),
                e(2003, R.drawable.msg_pin, R.string.PinnedMessage, false),
                e(29, R.drawable.boosts, R.string.BoostingBoostChannelMenu, false),
                e(62, R.drawable.msg_translate, R.string.TranslateMessage, false),
                e(2005, R.drawable.msg_groups, R.string.LinkedGroupChat, false),
                e(27, R.drawable.msg_background, R.string.SetWallpapers, false),
                e(15, R.drawable.msg_clear, R.string.ClearHistory, false),
                e(200, R.drawable.ic_upward, R.string.ToTheBeginning, false),
                e(201, R.drawable.msg_go_up, R.string.ToTheMessage, false),
                e(2040, R.drawable.msg_fave, R.string.BookmarksManager, false),
                e(2029, R.drawable.hide_title, R.string.HideTitle, false),
                e(2101, R.drawable.msg_view_file, R.string.ViewDeleted, false),
                e(2100, R.drawable.msg_clear, R.string.ClearDeleted, false),
                e(16, R.drawable.msg_leave, R.string.LeaveChannelMenu, true)
        };
        all[OWN_CHANNEL] = ownChannel;
        all[OTHER_CHANNEL] = ownChannel;
        return all;
    }
}
