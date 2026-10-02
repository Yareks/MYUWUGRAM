package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.drawer.DrawerContainer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import tw.nekomimi.nekogram.NekoConfig;

/**
 * Экран «Профиль и боковое меню».
 *
 * Боковое меню: своя фоновая картинка (хранится копией во внутреннем хранилище,
 * поэтому переживает удаление исходника), затемнение фона в процентах.
 * Профиль: скрытие номера телефона в шторке (существующий флаг NekoConfig,
 * рисует {@code DrawerHeaderView}).
 *
 * Собран на {@link UniversalRecyclerView}, как соседний экран «App Navigation».
 */
public class OpenExteraProfileDrawerActivity extends BaseFragment {

    private static final int ID_PICK_BG = 1;
    private static final int ID_RESET_BG = 2;
    private static final int ID_DIM = 3;
    private static final int ID_HIDE_PHONE = 4;
    private static final int ID_PICK_PROFILE_BG = 5;
    private static final int ID_RESET_PROFILE_BG = 6;
    private static final int ID_PROFILE_DIM = 7;
    private static final int ID_PROFILE_FIT = 8;

    private static final int REQ_PICK_BG = 13271;
    private static final int REQ_PICK_PROFILE_BG = 13272;

    /** Ключ пути к картинке в общих prefs; читает {@link DrawerContainer#applyDrawerBackground}. */
    public static final String PREF_BG_PATH = "OEAppearanceDrawerBgPath";
    /** Имя файла-копии выбранной картинки во внутреннем хранилище. */
    private static final String BG_FILE_NAME = "drawer_background.jpg";

    private static final int[] DIM_STEPS = {0, 15, 30, 45, 60};

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.OEProfileDrawerTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);
        fragmentView = contentView;
        return contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.OEProfileDrawerSectionDrawer)));
        items.add(UItem.asButton(ID_PICK_BG, R.drawable.msg_media,
                getString(R.string.OEProfileDrawerBackground), currentBackgroundLabel()));
        if (currentBackgroundPath() != null) {
            items.add(UItem.asButton(ID_RESET_BG, getString(R.string.OEProfileDrawerResetBackground)));
        }
        items.add(UItem.asButton(ID_DIM, R.drawable.msg_theme,
                getString(R.string.OEProfileDrawerDim), dimLabel()));
        items.add(UItem.asShadow(getString(R.string.OEProfileDrawerInfo)));

        items.add(UItem.asHeader(getString(R.string.OEProfileDrawerSectionProfile)));
        items.add(UItem.asButton(ID_PICK_PROFILE_BG, R.drawable.msg_media,
                getString(R.string.OEProfileBannerPick), currentProfileBackgroundLabel()));
        if (app.exteraless.appearance.ProfileBanner.currentPath() != null) {
            items.add(UItem.asButton(ID_RESET_PROFILE_BG, getString(R.string.OEProfileBannerReset)));
        }
        items.add(UItem.asButton(ID_PROFILE_DIM, R.drawable.msg_theme,
                getString(R.string.OEProfileBannerDim), profileDimLabel()));
        items.add(UItem.asButton(ID_PROFILE_FIT, R.drawable.msg_theme,
                getString(R.string.OEProfileBannerFit), profileFitLabel()));
        items.add(UItem.asShadow(getString(R.string.OEProfileBannerInfo)));

        items.add(UItem.asHeader(getString(R.string.OEProfileDrawerSectionDrawer2)));
        items.add(UItem.asCheck(ID_HIDE_PHONE, getString(R.string.OEProfileDrawerHidePhone))
                .setChecked(NekoConfig.hidePhone.Bool()));
        items.add(UItem.asShadow(""));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_PICK_BG) {
            pickImage();
        } else if (item.id == ID_RESET_BG) {
            resetBackground();
        } else if (item.id == ID_DIM) {
            showDimDialog();
        } else if (item.id == ID_PICK_PROFILE_BG) {
            pickProfileImage();
        } else if (item.id == ID_RESET_PROFILE_BG) {
            resetProfileBackground();
        } else if (item.id == ID_PROFILE_DIM) {
            showProfileDimDialog();
        } else if (item.id == ID_PROFILE_FIT) {
            showProfileFitDialog();
        } else if (item.id == ID_HIDE_PHONE) {
            NekoConfig.hidePhone.setConfigBool(!NekoConfig.hidePhone.Bool());
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        }
    }

    // ---- фон шторки ----

    private static String currentBackgroundPath() {
        String path = AppearanceConfig.getPreferences().getString(PREF_BG_PATH, null);
        return path != null && new File(path).exists() ? path : null;
    }

    private String currentBackgroundLabel() {
        return getString(currentBackgroundPath() != null
                ? R.string.OEProfileDrawerBackgroundCustom
                : R.string.OEProfileDrawerBackgroundDefault);
    }

    private void pickImage() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, REQ_PICK_BG);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void resetBackground() {
        File old = new File(ApplicationLoader.applicationContext.getFilesDir(), BG_FILE_NAME);
        //noinspection ResultOfMethodCallIgnored
        old.delete();
        AppearanceConfig.getPreferences().edit().remove(PREF_BG_PATH).apply();
        DrawerContainer.refreshActiveDrawerBackground();
        updateList();
    }

    // ---- баннер профиля ----

    private String currentProfileBackgroundLabel() {
        return getString(app.exteraless.appearance.ProfileBanner.currentPath() != null
                ? R.string.OEProfileDrawerBackgroundCustom
                : R.string.OEProfileBannerDefault);
    }

    private void pickProfileImage() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, REQ_PICK_PROFILE_BG);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void resetProfileBackground() {
        //noinspection ResultOfMethodCallIgnored
        app.exteraless.appearance.ProfileBanner.targetFile().delete();
        AppearanceConfig.getPreferences().edit()
                .remove(app.exteraless.appearance.ProfileBanner.PREF_PATH)
                .apply();
        app.exteraless.appearance.ProfileBanner.reload();
        updateList();
    }

    private void copyProfileImage(final android.net.Uri uri) {
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = false;
            try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
                File out = app.exteraless.appearance.ProfileBanner.targetFile();
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                    }
                }
                AppearanceConfig.getPreferences().edit()
                        .putString(app.exteraless.appearance.ProfileBanner.PREF_PATH, out.getAbsolutePath())
                        .apply();
                ok = true;
            } catch (Throwable t) {
                FileLog.e("ProfileBanner: copy failed", t);
            }
            final boolean success = ok;
            AndroidUtilities.runOnUIThread(() -> {
                if (success) {
                    app.exteraless.appearance.ProfileBanner.reload();
                    updateList();
                }
            });
        });
    }

    private String profileDimLabel() {
        int dim = AppearanceConfig.profileBackgroundDim.Int();
        return dim <= 0 ? getString(R.string.OEProfileDrawerDimOff) : dim + "%";
    }

    private void showProfileDimDialog() {
        if (getParentActivity() == null) {
            return;
        }
        String[] names = new String[DIM_STEPS.length];
        for (int i = 0; i < DIM_STEPS.length; i++) {
            names[i] = DIM_STEPS[i] <= 0 ? getString(R.string.OEProfileDrawerDimOff) : DIM_STEPS[i] + "%";
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.OEProfileBannerDim));
        builder.setItems(names, (dialog, which) -> {
            AppearanceConfig.profileBackgroundDim.setConfigInt(DIM_STEPS[which]);
            updateList();
        });
        showDialog(builder.create());
    }

    private String profileFitLabel() {
        return getString(AppearanceConfig.profileBackgroundFit.Bool()
                ? R.string.OEProfileBannerFitWhole
                : R.string.OEProfileBannerFitCrop);
    }

    private void showProfileFitDialog() {
        if (getParentActivity() == null) {
            return;
        }
        String[] names = new String[]{
                getString(R.string.OEProfileBannerFitCrop),
                getString(R.string.OEProfileBannerFitWhole)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.OEProfileBannerFit));
        builder.setItems(names, (dialog, which) -> {
            AppearanceConfig.profileBackgroundFit.setConfigBool(which == 1);
            updateList();
        });
        showDialog(builder.create());
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK_PROFILE_BG && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            if (getParentActivity() != null) {
                copyProfileImage(data.getData());
            }
            return;
        }
        if (requestCode != REQ_PICK_BG || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (getParentActivity() == null) {
            return;
        }
        final android.net.Uri uri = data.getData();
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = false;
            try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
                File out = new File(ApplicationLoader.applicationContext.getFilesDir(), BG_FILE_NAME);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                    }
                }
                AppearanceConfig.getPreferences().edit()
                        .putString(PREF_BG_PATH, out.getAbsolutePath())
                        .apply();
                ok = true;
            } catch (Throwable t) {
                FileLog.e("ProfileDrawer: copy background failed", t);
            }
            final boolean success = ok;
            AndroidUtilities.runOnUIThread(() -> {
                if (success) {
                    DrawerContainer.refreshActiveDrawerBackground();
                    updateList();
                }
            });
        });
    }

    // ---- затемнение ----

    private String dimLabel() {
        int dim = AppearanceConfig.drawerBackgroundDim.Int();
        return dim <= 0 ? getString(R.string.OEProfileDrawerDimOff) : dim + "%";
    }

    private void showDimDialog() {
        if (getParentActivity() == null) {
            return;
        }
        String[] names = new String[DIM_STEPS.length];
        for (int i = 0; i < DIM_STEPS.length; i++) {
            names[i] = DIM_STEPS[i] <= 0 ? getString(R.string.OEProfileDrawerDimOff) : DIM_STEPS[i] + "%";
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.OEProfileDrawerDim));
        builder.setItems(names, (dialog, which) -> {
            AppearanceConfig.drawerBackgroundDim.setConfigInt(DIM_STEPS[which]);
            DrawerContainer.refreshActiveDrawerBackground();
            updateList();
        });
        showDialog(builder.create());
    }

    private void updateList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }
}
