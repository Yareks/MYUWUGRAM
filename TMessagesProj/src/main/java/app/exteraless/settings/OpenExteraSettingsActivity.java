package app.exteraless.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import app.exteraless.updater.GitHubUpdater;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Корневой экран раздела UwUgram.
 * Открывается из главных настроек.
 */
public class OpenExteraSettingsActivity extends BaseNekoSettingsActivity {

    private static final int MENU_CLOUD = 1;

    private static final int TYPE_ABOUT = 100;

    private int aboutRow;
    private int updateRow = -1;

    private int categoriesHeaderRow;
    private int generalRow;
    private int appearanceRow;
    private int chatsRow;
    private int aiRow;
    private int pluginsRow;
    private int otherRow;
    private int categoriesDividerRow;

    private int updatesHeaderRow;
    private int versionRow;
    private int changelogRow;
    private int updatesDividerRow;

    private int linksHeaderRow;
    private int channelRow;
    private int linksDividerRow;

    private final Runnable remoteChanged = () -> {
        if (isFinished || fragmentView == null || listAdapter == null) {
            return;
        }
        updateRows();
        listAdapter.notifyDataSetChanged();
    };

    @Override
    protected void updateRows() {
        super.updateRows();

        aboutRow = addRow("about");
        updateRow = GitHubUpdater.hasUpdate() ? addRow("update") : -1;

        categoriesHeaderRow = addRow("categoriesHeader");
        generalRow = addRow("general");
        appearanceRow = addRow("appearance");
        chatsRow = addRow("chats");
        aiRow = addRow("ai");
        pluginsRow = addRow("plugins");
        otherRow = addRow("other");
        categoriesDividerRow = addRow();

        updatesHeaderRow = addRow("updatesHeader");
        versionRow = addRow("version");
        changelogRow = addRow("changelog");
        updatesDividerRow = addRow();

        linksHeaderRow = addRow("linksHeader");
        channelRow = addRow("channel");
        linksDividerRow = addRow();
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        getMessagesController().getContentSettings(null);
        if (actionBar != null && fragmentView instanceof android.widget.FrameLayout) {
            actionBar.setBackground(null);
            actionBar.setCastShadows(false);
            actionBar.setAddToContainer(false);
            if (actionBar.getTitleTextView() != null) {
                actionBar.getTitleTextView().setAlpha(0f);
            }
            ((android.widget.FrameLayout) fragmentView).addView(actionBar,
                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                            android.view.Gravity.TOP));
        }
        if (actionBar != null) {
            actionBar.createMenu().addItem(MENU_CLOUD, R.drawable.cloud_sync, getResourceProvider());
            actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == MENU_CLOUD) {
                        presentFragment(new OpenExteraCloudActivity());
                    }
                }
            });
        }
        return view;
    }

    @Override
    protected void setupAdaptiveBackground() {
    }

    @Override
    protected boolean needActionBarBlur() {
        return false;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (listView != null) {
            listView.setPadding(0, top, 0, bottom);
            listView.setClipToPadding(false);
        }
    }

    @Override
    public void onResume() {
        ChannelLink.setListener(remoteChanged);
        GitHubUpdater.setListener(remoteChanged);
        updateRows();
        super.onResume();
        ChannelLink.refresh(currentAccount, true);
        GitHubUpdater.check(false);
    }

    @Override
    public void onFragmentDestroy() {
        ChannelLink.setListener(null);
        GitHubUpdater.setListener(null);
        super.onFragmentDestroy();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OpenExtera);
    }

    @Override
    public int getSearchGuid() {
        return 24000;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.msg_settings_old;
    }

    @Override
    public String getSearchPrefix() {
        return "OpenExtera";
    }

    @Override
    protected String getKey() {
        return "exteraless";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == updateRow) {
            GitHubUpdater.showPending();
        } else if (position == generalRow) {
            presentFragment(new OpenExteraGeneralActivity());
        } else if (position == appearanceRow) {
            presentFragment(new OpenExteraAppearanceActivity());
        } else if (position == chatsRow) {
            presentFragment(new OpenExteraChatsActivity());
        } else if (position == aiRow) {
            presentFragment(new OpenExteraAiActivity());
        } else if (position == pluginsRow) {
            presentFragment(new app.exteraless.plugins.ui.PluginsActivity());
        } else if (position == otherRow) {
            presentFragment(new OpenExteraOtherActivity());
        } else if (position == versionRow) {
            if (GitHubUpdater.hasUpdate()) {
                GitHubUpdater.showPending();
            } else {
                GitHubUpdater.check(true);
            }
        } else if (position == changelogRow) {
            GitHubUpdater.showChangelog(this);
        } else if (position == channelRow) {
            getMessagesController().openByUserName(ChannelLink.username(), this, 1);
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (position == channelRow) {
            if (ChannelLink.isAdmin()) {
                showChannelMenu();
            } else {
                ChannelLink.copy(this);
            }
            return true;
        }
        return super.onItemLongClick(view, position, x, y);
    }

    private void showChannelMenu() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
        builder.setTitle(ChannelLink.display());
        builder.setItems(new CharSequence[]{
                getString(R.string.Copy),
                getString(R.string.OpenExteraChangeChannel)
        }, (dialog, which) -> {
            if (which == 0) {
                ChannelLink.copy(this);
            } else {
                showChangeChannelDialog();
            }
        });
        showDialog(builder.create());
    }

    private void showChangeChannelDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.lineYFix = true;
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(ChannelLink.username());
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintColor(getThemedColor(Theme.key_groupcreate_hintText));
        editText.setHintText(getString(R.string.OpenExteraChangeChannelHint));
        editText.setFocusable(true);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setBackground(null);
        editText.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField),
                getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                getThemedColor(Theme.key_text_RedRegular));
        editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
        editText.setPadding(0, dp(6), 0, dp(6));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 24f, 0f, 24f, 10f));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.OpenExteraChangeChannel));
        builder.makeCustomMaxHeight();
        builder.setView(container);
        builder.setWidth(dp(292));
        builder.setPositiveButton(getString(R.string.Done), (dialog, which) -> {
            String value = editText.getText() == null ? "" : editText.getText().toString();
            if (ChannelLink.normalize(value) == null) {
                AndroidUtilities.shakeView(editText);
                return;
            }
            ChannelLink.change(this, value, (found, published) -> {
                if (!found) {
                    AndroidUtilities.shakeView(editText);
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.OpenExteraChannelNotFound)).show();
                    return;
                }
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(channelRow);
                }
                if (published) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.OpenExteraChannelChanged)).show();
                } else {
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.OpenExteraChannelSavedLocal)).show();
                }
                dialog.dismiss();
            });
        });
        builder.setNegativeButton(getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            editText.requestFocus();
            editText.setSelection(editText.length());
            AndroidUtilities.showKeyboard(editText);
        });
        dialog.setDismissDialogByButtons(false);
        showDialog(dialog, d -> AndroidUtilities.hideKeyboard(editText));
    }

    private static String installedVersion() {
        String version = BuildVars.BUILD_VERSION_STRING;
        try {
            PackageInfo info = ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            if (info != null) {
                version = version + " (" + info.versionCode + ")";
            }
        } catch (Exception ignore) {
        }
        return version;
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_ABOUT) {
                View view = new AboutHeaderCell(mContext);
                view.setTag(RecyclerListView.TAG_NOT_SECTION);
                view.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_ABOUT: {
                    if (holder.itemView instanceof AboutHeaderCell) {
                        ((AboutHeaderCell) holder.itemView).refreshLogo();
                    }
                    break;
                }
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == categoriesHeaderRow) {
                        cell.setText(getString(R.string.OpenExteraCategories));
                    } else if (position == updatesHeaderRow) {
                        cell.setText(getString(R.string.OpenExteraUpdates));
                    } else if (position == linksHeaderRow) {
                        cell.setText(getString(R.string.OpenExteraLinks));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == updateRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.OpenExteraUpdateAction),
                                GitHubUpdater.pendingTitle(), R.drawable.msg_download, false);
                    } else if (position == generalRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraGeneral), R.drawable.msg_media, true);
                    } else if (position == appearanceRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraAppearance), R.drawable.msg_theme, true);
                    } else if (position == chatsRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraChats), R.drawable.msg_discussion, true);
                    } else if (position == aiRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraAi), R.drawable.ai_chat, true);
                    } else if (position == pluginsRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraPlugins), R.drawable.msg_plugins, true);
                    } else if (position == otherRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraOther), R.drawable.msg_fave, false);
                    } else if (position == versionRow) {
                        String value = installedVersion();
                        if (GitHubUpdater.hasUpdate() && !TextUtils.isEmpty(GitHubUpdater.pendingTitle())) {
                            value = GitHubUpdater.pendingTitle();
                        }
                        cell.setTextAndValueAndIcon(getString(R.string.OpenExteraUpdateCurrent),
                                value, R.drawable.msg_info, true);
                    } else if (position == changelogRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraChangelogs), R.drawable.msg_list, false);
                    } else if (position == channelRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.ProfileChannel),
                                ChannelLink.display(), R.drawable.msg_channel, false);
                    }
                    cell.setImageLeft(21);
                    cell.setOffsetFromImage(71);
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_TEXT;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == aboutRow) {
                return TYPE_ABOUT;
            } else if (position == categoriesHeaderRow || position == linksHeaderRow
                    || position == updatesHeaderRow) {
                return TYPE_HEADER;
            } else if (position == categoriesDividerRow || position == linksDividerRow
                    || position == updatesDividerRow) {
                return TYPE_SHADOW;
            }
            return TYPE_TEXT;
        }
    }
}
