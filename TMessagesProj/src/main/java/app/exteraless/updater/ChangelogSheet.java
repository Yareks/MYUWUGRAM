package app.exteraless.updater;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/** Список изменений из файла на GitHub. */
public class ChangelogSheet extends BottomSheet {

    public ChangelogSheet(Context context, Theme.ResourcesProvider resourcesProvider, String title, String notes) {
        super(context, false, resourcesProvider);
        fixNavigationBar();

        android.widget.LinearLayout container = new android.widget.LinearLayout(context);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(dp(18), dp(12), dp(18), dp(16));

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        titleView.setText(title);
        container.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView notesView = new TextView(context);
        notesView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        notesView.setLineSpacing(dp(2), 1f);
        notesView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        notesView.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink));
        notesView.setText(Emoji.replaceEmoji(UpdateSheet.format(notes), notesView.getPaint().getFontMetricsInt(), false));
        notesView.setMovementMethod(LinkMovementMethod.getInstance());

        ScrollView scrollView = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        (int) (AndroidUtilities.displaySize.y * 0.62f), MeasureSpec.AT_MOST));
            }
        };
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(notesView, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout card = new FrameLayout(context);
        card.setBackground(Theme.createRoundRectDrawable(dp(18), getThemedColor(Theme.key_graySection)));
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        container.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        setCustomView(container);
    }
}
