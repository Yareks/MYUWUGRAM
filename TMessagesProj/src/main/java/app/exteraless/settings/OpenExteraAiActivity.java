package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;

import app.exteraless.ai.ui.AiSettingsActivity;
import app.exteraless.appearance.AppearanceConfig;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.helpers.TranscribeHelper;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

/**
 * Все функции ИИ в одном пункте настроек: ассистент, скрытие встроенных
 * AI-кнопок Telegram и расшифровка голосовых.
 */
public class OpenExteraAiActivity extends BaseNekoSettingsActivity {

    private int assistantHeaderRow;
    private int assistantRow;
    private int hideHeaderRow;
    private int hideEditorRow;
    private int hideSummaryRow;
    private int hideIvRow;
    private int transcribeHeaderRow;
    private int transcribeProviderRow;
    private int cloudflareCredentialsRow;
    private int geminiApiKeyRow;
    private int openAiCredentialsRow;
    private int voskModelsRow;
    private int infoRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        assistantHeaderRow = addRow("assistantHeader");
        assistantRow = addRow("OEChatsAiChat");

        hideHeaderRow = addRow("hideHeader");
        hideEditorRow = addRow("OEAppearanceHideAiEditor");
        hideSummaryRow = addRow("OEAppearanceHideAiSummary");
        hideIvRow = addRow("OEAppearanceHideAiIv");

        transcribeHeaderRow = addRow("transcribeHeader");
        transcribeProviderRow = addRow("TranscribeProviderShort");
        cloudflareCredentialsRow = addRow("CloudflareCredentials");
        geminiApiKeyRow = addRow("LlmProviderGeminiKey");
        openAiCredentialsRow = NaConfig.INSTANCE.getTranscribeProvider().Int() == TranscribeHelper.TRANSCRIBE_OPENAI
                ? addRow("TranscribeProviderOpenAI") : -1;
        voskModelsRow = NaConfig.INSTANCE.getTranscribeProvider().Int() == TranscribeHelper.TRANSCRIBE_VOSK
                ? addRow("VoskModelsShort") : -1;
        infoRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OpenExteraAi);
    }

    @Override
    public int getSearchGuid() {
        return 27000;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.ai_chat;
    }

    @Override
    public String getSearchPrefix() {
        return "OEAi";
    }

    @Override
    protected String getKey() {
        return "exteraless_ai_hub";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == assistantRow) {
            presentFragment(new AiSettingsActivity());
        } else if (position == hideEditorRow) {
            toggleHide(view, AppearanceConfig.hideAiEditor);
        } else if (position == hideSummaryRow) {
            toggleHide(view, AppearanceConfig.hideMessageSummary);
        } else if (position == hideIvRow) {
            toggleHide(view, AppearanceConfig.hideIvSummary);
        } else if (position == transcribeProviderRow) {
            showProviderDialog();
        } else if (position == cloudflareCredentialsRow) {
            TranscribeHelper.showCfCredentialsDialog(this);
        } else if (position == geminiApiKeyRow) {
            TranscribeHelper.showGeminiApiKeyDialog(this);
        } else if (position == openAiCredentialsRow) {
            TranscribeHelper.showOpenAiCredentialsDialog(this);
        } else if (position == voskModelsRow) {
            presentFragment(new app.exteraless.speech.VoskSettingsActivity());
        }
    }

    private void toggleHide(View view, ConfigItem item) {
        boolean enabled = item.toggleConfigBool();
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(enabled);
        }
    }

    private void showProviderDialog() {
        if (getParentActivity() == null) {
            return;
        }
        CharSequence[] options = transcribeProviderOptions();
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.TranscribeProviderShort));
        builder.setItems(options, (dialog, which) -> {
            NaConfig.INSTANCE.getTranscribeProvider().setConfigInt(which);
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        showDialog(builder.create());
    }

    private CharSequence[] transcribeProviderOptions() {
        return new CharSequence[]{
                getString(R.string.TranscribeProviderAuto),
                getString(R.string.TelegramPremium),
                getString(R.string.TranscribeProviderWorkersAI),
                getString(R.string.TranscribeProviderGemini),
                getString(R.string.TranscribeProviderOpenAI),
                getString(R.string.TranscribeProviderVosk)
        };
    }

    private static int clampIndex(int index, int length) {
        return index < 0 || index >= length ? 0 : index;
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == assistantHeaderRow) {
                        cell.setText(getString(R.string.OEAiTitle));
                    } else if (position == hideHeaderRow) {
                        cell.setText(getString(R.string.OEAppearanceHideAi));
                    } else if (position == transcribeHeaderRow) {
                        cell.setText(getString(R.string.PremiumPreviewVoiceToText));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    cell.heightDp = 50;
                    if (position == assistantRow) {
                        cell.setTextAndIcon(getString(R.string.OEChatsAiChat), R.drawable.ai_chat, false);
                        cell.setSubtitle(getString(R.string.OEChatsAiChatInfo));
                        cell.heightDp = 64;
                        cell.offsetFromImage = 60;
                    } else if (position == cloudflareCredentialsRow) {
                        cell.setText(getString(R.string.CloudflareCredentials), true);
                    } else if (position == geminiApiKeyRow) {
                        cell.setText(getString(R.string.LlmProviderGeminiKey),
                                openAiCredentialsRow != -1 || voskModelsRow != -1);
                    } else if (position == openAiCredentialsRow) {
                        cell.setText(getString(R.string.TranscribeProviderOpenAI), false);
                    } else if (position == voskModelsRow) {
                        cell.setText(getString(R.string.VoskModelsShort), false);
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == hideEditorRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceHideAiEditor),
                                AppearanceConfig.hideAiEditor.Bool(), true);
                    } else if (position == hideSummaryRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceHideAiSummary),
                                AppearanceConfig.hideMessageSummary.Bool(), true);
                    } else if (position == hideIvRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceHideAiIv),
                                AppearanceConfig.hideIvSummary.Bool(), false);
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == transcribeProviderRow) {
                        CharSequence[] options = transcribeProviderOptions();
                        cell.setTextAndValue(getString(R.string.TranscribeProviderShort),
                                options[clampIndex(NaConfig.INSTANCE.getTranscribeProvider().Int(), options.length)],
                                true);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == infoRow) {
                        cell.setText(getString(R.string.OEAiHubInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext,
                                R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == assistantHeaderRow || position == hideHeaderRow || position == transcribeHeaderRow) {
                return TYPE_HEADER;
            } else if (position == assistantRow || position == cloudflareCredentialsRow
                    || position == geminiApiKeyRow || position == openAiCredentialsRow
                    || position == voskModelsRow) {
                return TYPE_TEXT;
            } else if (position == transcribeProviderRow) {
                return TYPE_SETTINGS;
            } else if (position == infoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_CHECK;
        }
    }
}
