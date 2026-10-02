package app.exteraless.updater;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


public final class GitHubUpdater {

    private static final String REPO = "Yareks/MYUWUGRAM";
    private static final String API = "https://api.github.com/repos/" + REPO;
    static final String CHANGELOG_URL = "https://raw.githubusercontent.com/Yareks/MYUWUGRAM/main/update/CHANGELOG.md";
    private static final long AUTO_INTERVAL = TimeUnit.HOURS.toMillis(6);
    private static final String PREFS = "uwugram_updater";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_SKIPPED = "skipped_tag";
    private static final String KEY_CHANGELOG = "changelog";
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private static volatile OkHttpClient client;
    private static volatile boolean checking;
    private static volatile boolean offeredThisProcess;
    private static volatile boolean sheetVisible;
    private static volatile Call download;
    private static Runnable listener;

    private GitHubUpdater() {
    }

    private static final class Release {
        String tag;
        String name;
        String body;
        String apkUrl;
        String apkName;
        long apkSize;
    }

    private static OkHttpClient client() {
        if (client == null) {
            synchronized (GitHubUpdater.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return client;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void setListener(Runnable listener) {
        GitHubUpdater.listener = listener;
    }

    public static boolean hasUpdate() {
        Release pending = loadPending();
        return pending != null && !isSkipped(pending);
    }

    public static String pendingTitle() {
        Release pending = loadPending();
        if (pending == null) {
            return "";
        }
        return TextUtils.isEmpty(pending.name) ? pending.tag : pending.name;
    }

    public static void showPending() {
        Release pending = loadPending();
        if (pending != null) {
            show(pending);
        }
    }

    public static void showChangelog(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        new Thread(() -> {
            String text = null;
            boolean fetched = false;
            try {
                Request request = new Request.Builder().url(CHANGELOG_URL)
                        .header("User-Agent", "uwugram").build();
                try (Response response = client().newCall(request).execute()) {
                    ResponseBody body = response.body();
                    if (response.isSuccessful() && body != null) {
                        text = body.string().trim();
                        fetched = true;
                        if (!TextUtils.isEmpty(text)) {
                            prefs().edit().putString(KEY_CHANGELOG, text).apply();
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e("GitHubUpdater: changelog failed", e);
            }
            final String remote = text;
            final boolean online = fetched;
            AndroidUtilities.runOnUIThread(() -> {
                if (fragment.getParentActivity() == null) {
                    return;
                }
                String notes = !TextUtils.isEmpty(remote) ? remote : prefs().getString(KEY_CHANGELOG, "");
                if (TextUtils.isEmpty(notes)) {
                    bulletin(LocaleController.getString(R.string.OpenExteraChangelogEmpty), true);
                    return;
                }
                if (!online) {
                    bulletin(LocaleController.getString(R.string.OpenExteraChangelogOffline), false);
                }
                new ChangelogSheet(fragment.getParentActivity(), fragment.getResourceProvider(),
                        LocaleController.getString(R.string.OpenExteraChangelogs), notes).show();
            });
        }, "gh-changelog").start();
    }

    public static void check(boolean force) {
        Release cached = loadPending();
        if (!force && cached != null && !isSkipped(cached) && !offeredThisProcess) {
            if (show(cached)) {
                offeredThisProcess = true;
            }
        }
        if (!force && Math.abs(System.currentTimeMillis() - prefs().getLong(KEY_LAST_CHECK, 0)) < AUTO_INTERVAL) {
            return;
        }
        if (checking) {
            return;
        }
        checking = true;
        new Thread(() -> {
            Release release = null;
            Boolean newer = null;
            try {
                release = latest();
                if (release != null) {
                    newer = isNewer(release);
                }
                prefs().edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
            } catch (Exception e) {
                FileLog.e("GitHubUpdater: check failed", e);
            }
            final Release found = release;
            final Boolean isNewer = newer;
            AndroidUtilities.runOnUIThread(() -> {
                checking = false;
                if (found != null && Boolean.TRUE.equals(isNewer)) {
                    savePending(found);
                } else if (Boolean.FALSE.equals(isNewer)) {
                    clearPending();
                }
                notifyListener();
                Release pending = loadPending();
                if (pending != null && !isSkipped(pending) && (force || !offeredThisProcess)) {
                    if (show(pending)) {
                        offeredThisProcess = true;
                    }
                } else if (force) {
                    bulletin(found == null && isNewer == null
                            ? LocaleController.getString(R.string.OEUpdateCheckFailed)
                            : LocaleController.getString(R.string.YourVersionIsLatestNax), found == null && isNewer == null);
                }
            });
        }, "gh-updater").start();
    }

    private static void bulletin(String text, boolean error) {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) {
            return;
        }
        if (error) {
            BulletinFactory.of(fragment).createErrorBulletin(text).show();
        } else {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.done, text).show();
        }
    }

    private static JSONObject getJson(String url) throws Exception {
        Request request = new Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "uwugram")
                .build();
        try (Response response = client().newCall(request).execute()) {
            ResponseBody body = response.body();
            if (response.code() == 404) {
                return null;
            }
            if (!response.isSuccessful() || body == null) {
                throw new IllegalStateException("HTTP " + response.code());
            }
            String text = body.string();
            return text.trim().startsWith("[") ? new JSONObject().put("items", new JSONArray(text)) : new JSONObject(text);
        }
    }

    private static Release latest() throws Exception {
        JSONObject wrapper = getJson(API + "/releases?per_page=20");
        JSONArray releases = wrapper == null ? null : wrapper.optJSONArray("items");
        if (releases == null) {
            return null;
        }
        Release ci = null;
        Release stable = null;
        for (int i = 0; i < releases.length(); i++) {
            JSONObject item = releases.optJSONObject(i);
            if (item == null || item.optBoolean("draft")) {
                continue;
            }
            JSONObject asset = pickAsset(item.optJSONArray("assets"));
            if (asset == null) {
                continue;
            }
            Release release = new Release();
            release.tag = item.optString("tag_name");
            release.name = item.optString("name", release.tag);
            release.body = item.optString("body", "");
            release.apkUrl = asset.optString("browser_download_url");
            release.apkName = asset.optString("name");
            release.apkSize = asset.optLong("size");
            if (release.tag != null && release.tag.startsWith("ci-")) {
                if (ci == null) {
                    ci = release;
                }
            } else if (!item.optBoolean("prerelease") && stable == null) {
                stable = release;
            }
        }
        return ci != null ? ci : stable;
    }

    private static JSONObject pickAsset(JSONArray assets) {
        if (assets == null) {
            return null;
        }
        JSONObject universal = null;
        JSONObject any = null;
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            String name = asset == null ? "" : asset.optString("name").toLowerCase(Locale.ROOT);
            if (!name.endsWith(".apk")) {
                continue;
            }
            if (!abi.isEmpty() && name.contains(abi.toLowerCase(Locale.ROOT))) {
                return asset;
            }
            if (name.contains("universal") && universal == null) {
                universal = asset;
            }
            if (any == null && !name.contains("armeabi") && !name.contains("x86")) {
                any = asset;
            }
        }
        return universal != null ? universal : any;
    }

    private static int[] version(String... sources) {
        for (String source : sources) {
            if (source == null) {
                continue;
            }
            Matcher matcher = VERSION.matcher(source);
            if (matcher.find()) {
                return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))};
            }
        }
        return null;
    }

    private static Integer compareVersion(Release release) {
        int[] remote = version(release.tag, release.name, release.apkName);
        int[] local = version(BuildVars.BUILD_VERSION_STRING);
        if (remote == null || local == null) {
            return null;
        }
        for (int i = 0; i < 3; i++) {
            if (remote[i] != local[i]) {
                return Integer.compare(remote[i], local[i]);
            }
        }
        return 0;
    }

    private static Boolean isNewer(Release release) throws Exception {
        String commit = BuildConfig.BUILD_COMMIT_ID;
        if (release.tag != null && release.tag.startsWith("ci-") && !TextUtils.isEmpty(commit)) {
            String tagCommit = release.tag.substring(3);
            if (commit.equalsIgnoreCase(tagCommit) || commit.startsWith(tagCommit) || tagCommit.startsWith(commit)) {
                return false;
            }
        }
        Integer versionOrder = compareVersion(release);
        if (versionOrder != null && versionOrder != 0) {
            return versionOrder > 0;
        }
        if (!TextUtils.isEmpty(commit) && !TextUtils.isEmpty(release.tag)) {
            JSONObject compare = getJson(API + "/compare/" + commit + "..." + Uri.encode(release.tag));
            if (compare != null) {
                String status = compare.optString("status");
                if ("identical".equals(status) || "behind".equals(status)) {
                    return false;
                }
                return compare.optInt("ahead_by", 0) > 0;
            }
        }
        if (versionOrder != null && versionOrder < 0) {
            return false;
        }
        return null;
    }

    private static boolean show(Release release) {
        if (release == null || sheetVisible || TextUtils.isEmpty(release.apkUrl)) {
            return false;
        }
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return false;
        }
        String size = release.apkSize > 0 ? AndroidUtilities.formatFileSize(release.apkSize) : "";
        int[] remote = version(release.tag, release.name, release.apkName);
        StringBuilder subtitle = new StringBuilder(TextUtils.isEmpty(release.name) ? release.tag : release.name);
        if (remote != null) {
            subtitle.append(" · ").append(remote[0]).append('.').append(remote[1]).append('.').append(remote[2]);
        }
        if (!size.isEmpty()) {
            subtitle.append(" · ").append(size);
        }
        String notes = TextUtils.isEmpty(release.body) ? release.tag : release.body;
        String updateText = size.isEmpty() ? LocaleController.getString(R.string.OEUpdateInstall)
                : LocaleController.formatString(R.string.OEUpdateInstallSize, size);
        UpdateSheet sheet = new UpdateSheet(activity, fragment.getResourceProvider(), LocaleController.getString(R.string.OEUpdateTitle),
                subtitle.toString(), notes, updateText, new UpdateSheet.Delegate() {
            @Override
            public void onUpdate(UpdateSheet sheet) {
                download(activity, sheet, release);
            }

            @Override
            public void onSkip() {
                prefs().edit().putString(KEY_SKIPPED, release.tag).apply();
                clearPending();
                notifyListener();
            }

            @Override
            public void onCancelDownload() {
                Call call = download;
                if (call != null) {
                    call.cancel();
                }
            }
        });
        sheet.setOnDismissListener(() -> sheetVisible = false);
        sheetVisible = true;
        sheet.show();
        return true;
    }

    private static boolean isSkipped(Release release) {
        return release != null && TextUtils.equals(release.tag, prefs().getString(KEY_SKIPPED, null));
    }

    private static void savePending(Release release) {
        prefs().edit()
                .putString("pending_tag", release.tag)
                .putString("pending_name", release.name)
                .putString("pending_body", release.body)
                .putString("pending_url", release.apkUrl)
                .putString("pending_apk", release.apkName)
                .putLong("pending_size", release.apkSize)
                .apply();
    }

    private static void clearPending() {
        prefs().edit()
                .remove("pending_tag")
                .remove("pending_name")
                .remove("pending_body")
                .remove("pending_url")
                .remove("pending_apk")
                .remove("pending_size")
                .apply();
    }

    private static Release loadPending() {
        String url = prefs().getString("pending_url", "");
        String tag = prefs().getString("pending_tag", "");
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(tag)) {
            return null;
        }
        Release release = new Release();
        release.tag = tag;
        release.name = prefs().getString("pending_name", tag);
        release.body = prefs().getString("pending_body", "");
        release.apkUrl = url;
        release.apkName = prefs().getString("pending_apk", "uwugram.apk");
        release.apkSize = prefs().getLong("pending_size", 0);
        return release;
    }

    private static void notifyListener() {
        Runnable current = listener;
        if (current != null) {
            current.run();
        }
    }

    private static void download(Activity activity, UpdateSheet sheet, Release release) {
        if (download != null) {
            return;
        }
        File dir = new File(activity.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            bulletin(LocaleController.getString(R.string.OEUpdateDownloadFailed), true);
            return;
        }
        File[] old = dir.listFiles();
        if (old != null) {
            for (File file : old) {
                file.delete();
            }
        }
        File target = new File(dir, release.apkName.replaceAll("[^A-Za-z0-9._-]", "_"));
        sheet.setDownloading(true);
        Call call = client().newCall(new Request.Builder().url(release.apkUrl)
                .header("User-Agent", "uwugram").build());
        download = call;
        new Thread(() -> {
            boolean ok = false;
            try (Response response = call.execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IllegalStateException("HTTP " + response.code());
                }
                long total = body.contentLength() > 0 ? body.contentLength() : release.apkSize;
                try (InputStream input = body.byteStream(); OutputStream output = new FileOutputStream(target)) {
                    byte[] buffer = new byte[64 * 1024];
                    long done = 0;
                    int lastPercent = -1;
                    int read;
                    while ((read = input.read(buffer)) > 0) {
                        output.write(buffer, 0, read);
                        done += read;
                        if (total > 0) {
                            int percent = (int) (done * 100 / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final long doneBytes = done;
                                AndroidUtilities.runOnUIThread(() -> sheet.setProgress(doneBytes, total));
                            }
                        }
                    }
                }
                ok = true;
            } catch (Exception e) {
                if (!call.isCanceled()) {
                    FileLog.e("GitHubUpdater: download failed", e);
                }
            }
            final boolean success = ok;
            AndroidUtilities.runOnUIThread(() -> {
                download = null;
                if (success) {
                    sheet.finishDownload();
                    install(activity, target);
                } else {
                    target.delete();
                    if (!call.isCanceled()) {
                        sheet.setDownloading(false);
                        bulletin(LocaleController.getString(R.string.OEUpdateDownloadFailed), true);
                    }
                }
            });
        }, "gh-updater-download").start();
    }

    private static void install(Activity activity, File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file),
                    "application/vnd.android.package-archive");
            activity.startActivity(intent);
        } catch (Exception e) {
            FileLog.e(e);
            bulletin(LocaleController.getString(R.string.OEUpdateDownloadFailed), true);
        }
    }
}
