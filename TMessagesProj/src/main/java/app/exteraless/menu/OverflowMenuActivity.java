package app.exteraless.menu;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import xyz.nextalone.nagram.NaConfig;

/**
 * Редактор шести меню «⋮»: предпросмотр, перетаскивание, нажатие убирает,
 * «+» снизу возвращает пункт.
 */
public class OverflowMenuActivity extends BaseFragment {

    private int kind;
    private final ArrayList<Integer> visible = new ArrayList<>();
    private final ArrayList<Integer> hidden = new ArrayList<>();
    private final HashMap<Integer, OverflowMenus.Entry> byId = new HashMap<>();

    private final TextView[] chips = new TextView[OverflowMenus.COUNT];
    private HorizontalScrollView chipsScroll;
    private LinearLayout card;
    private RecyclerView list;
    private MenuAdapter adapter;
    private ItemTouchHelper touchHelper;
    private TextView emptyView;
    private ActionBarMenuItem resetItem;
    private ActionBarPopupWindow addPopup;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.OEAppearanceMenu));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        resetItem = actionBar.createMenu().addItem(1, R.drawable.msg_reset);
        resetItem.setContentDescription(getString(R.string.Reset));
        resetItem.setOnClickListener(v -> resetKind());

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);

        chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout chipRow = new LinearLayout(context);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(dp(12), dp(10), dp(12), dp(4));
        for (int i = 0; i < OverflowMenus.COUNT; i++) {
            final int index = i;
            TextView chip = new TextView(context);
            chip.setText(getString(OverflowMenus.titleRes(i)));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setPadding(dp(14), dp(7), dp(14), dp(7));
            chip.setGravity(Gravity.CENTER);
            chip.setOnClickListener(v -> selectKind(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(i == 0 ? 0 : dp(6), 0, 0, 0);
            chipRow.addView(chip, lp);
            chips[i] = chip;
        }
        chipsScroll.addView(chipRow, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START));
        page.addView(chipsScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView hint = new TextView(context);
        hint.setText(getString(R.string.OEMenuHint));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(24), dp(6), dp(24), dp(10));
        page.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.END);
        column.setPadding(dp(16), 0, dp(12), dp(24));

        ImageView dots = new ImageView(context);
        dots.setImageResource(R.drawable.ic_ab_other);
        dots.setScaleType(ImageView.ScaleType.CENTER);
        dots.setColorFilter(new PorterDuffColorFilter(
                getThemedColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.SRC_IN));
        column.addView(dots, LayoutHelper.createLinear(40, 36, Gravity.END, 0, 0, 4, 0));

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        applyPopupBackground(context, card);

        emptyView = new TextView(context);
        emptyView.setText(getString(R.string.OEMenuEmpty));
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        card.addView(emptyView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setNestedScrollingEnabled(false);
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        list.setItemAnimator(animator);
        adapter = new MenuAdapter();
        list.setAdapter(adapter);
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                int fromPos = from.getAdapterPosition();
                int toPos = to.getAdapterPosition();
                if (fromPos < 0 || toPos < 0 || fromPos == toPos) {
                    return false;
                }
                Integer id = visible.remove(fromPos);
                visible.add(toPos, id);
                adapter.notifyItemMoved(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (viewHolder != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder.itemView.setTranslationZ(dp(8));
                    haptic(viewHolder.itemView);
                    if (list.getParent() != null) {
                        list.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.setTranslationZ(0);
                adapter.notifyDataSetChanged();
                save();
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        });
        touchHelper.attachToRecyclerView(list);
        list.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
                if (rv.canScrollVertically(-1) || rv.canScrollVertically(1)) {
                    android.view.ViewParent parent = rv.getParent();
                    while (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                        parent = parent.getParent();
                    }
                }
                return false;
            }
        });
        card.addView(list, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        View gap = new View(context);
        gap.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuSeparator));
        card.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8, 0, 2, 0, 0));

        TextView plus = new TextView(context);
        plus.setText("+");
        plus.setGravity(Gravity.CENTER);
        plus.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        plus.setTypeface(AndroidUtilities.bold());
        plus.setTextColor(getThemedColor(Theme.key_featuredStickers_addButton));
        plus.setContentDescription(getString(R.string.Add));
        plus.setBackground(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), 0, 8));
        plus.setOnClickListener(this::showAddPopup);
        card.addView(plus, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        int cardWidth = Math.min(dp(280), AndroidUtilities.displaySize.x - dp(48));
        if (cardWidth < dp(220)) {
            cardWidth = dp(260);
        }
        column.addView(card, LayoutHelper.createLinear(cardWidth, LayoutHelper.WRAP_CONTENT, Gravity.END));
        page.addView(column, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = root;
        loadKind(kind, false);
        return fragmentView;
    }

    private void applyPopupBackground(Context context, View target) {
        Drawable bg = context.getResources().getDrawable(R.drawable.popup_fixed_alert2).mutate();
        bg.setColorFilter(new PorterDuffColorFilter(
                getThemedColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        target.setBackground(bg);
        Rect pad = new Rect();
        if (!bg.getPadding(pad)) {
            pad.set(dp(8), dp(8), dp(8), dp(8));
        }
        target.setPadding(pad.left, pad.top, pad.right, pad.bottom);
    }

    private void selectKind(int index) {
        if (index == kind) {
            return;
        }
        dismissAddPopup();
        loadKind(index, true);
    }

    private void loadKind(int index, boolean scrollChip) {
        kind = index;
        visible.clear();
        hidden.clear();
        byId.clear();
        HashSet<Integer> hiddenIds = OverflowMenus.hiddenSet(kind);
        HashSet<Integer> seen = new HashSet<>();
        for (OverflowMenus.Entry entry : OverflowMenus.catalog(kind)) {
            byId.put(entry.id, entry);
        }
        for (int id : OverflowMenus.order(kind)) {
            if (byId.containsKey(id) && seen.add(id) && !hiddenIds.contains(id)) {
                visible.add(id);
            }
        }
        for (OverflowMenus.Entry entry : OverflowMenus.catalog(kind)) {
            if (hiddenIds.contains(entry.id)) {
                hidden.add(entry.id);
            } else if (seen.add(entry.id)) {
                visible.add(entry.id);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateListHeight();
        updateEmpty();
        updateReset();
        styleChips();
        actionBar.setSubtitle(getString(OverflowMenus.titleRes(kind)));
        if (scrollChip && chipsScroll != null && chips[kind] != null) {
            chipsScroll.post(() -> chipsScroll.smoothScrollTo(Math.max(0, chips[kind].getLeft() - dp(16)), 0));
        }
    }

    private void styleChips() {
        for (int i = 0; i < chips.length; i++) {
            if (chips[i] == null) {
                continue;
            }
            boolean selected = i == kind;
            chips[i].setTextColor(getThemedColor(selected
                    ? Theme.key_featuredStickers_buttonText
                    : Theme.key_windowBackgroundWhiteBlackText));
            chips[i].setBackground(Theme.createRoundRectDrawable(dp(16), getThemedColor(selected
                    ? Theme.key_featuredStickers_addButton
                    : Theme.key_windowBackgroundWhite)));
        }
    }

    private void updateListHeight() {
        if (list == null) {
            return;
        }
        int rows = visible.size() * dp(48);
        int screen = AndroidUtilities.displaySize.y;
        int max = screen > dp(480) ? Math.max(dp(48 * 4), screen - dp(340)) : dp(48 * 7);
        int height = Math.min(rows, max);
        ViewGroup.LayoutParams lp = list.getLayoutParams();
        if (lp != null && lp.height != height) {
            lp.height = height;
            list.setLayoutParams(lp);
        }
        list.setNestedScrollingEnabled(rows > height);
    }

    private void updateEmpty() {
        if (emptyView != null) {
            emptyView.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void updateReset() {
        if (resetItem != null) {
            resetItem.setVisibility(OverflowMenus.isCustom(kind) ? View.VISIBLE : View.GONE);
        }
    }

    private boolean isDefaultState() {
        if (!hidden.isEmpty()) {
            return false;
        }
        OverflowMenus.Entry[] catalog = OverflowMenus.catalog(kind);
        if (visible.size() != catalog.length) {
            return false;
        }
        for (int i = 0; i < catalog.length; i++) {
            if (visible.get(i) != catalog[i].id) {
                return false;
            }
        }
        return true;
    }

    private void save() {
        if (isDefaultState()) {
            OverflowMenus.reset(kind);
            updateReset();
            return;
        }
        int[] vis = new int[visible.size()];
        for (int i = 0; i < visible.size(); i++) {
            vis[i] = visible.get(i);
        }
        int[] hid = new int[hidden.size()];
        for (int i = 0; i < hidden.size(); i++) {
            hid[i] = hidden.get(i);
        }
        OverflowMenus.save(kind, vis, hid);
        updateReset();
    }

    private void resetKind() {
        dismissAddPopup();
        OverflowMenus.reset(kind);
        loadKind(kind, false);
        BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.OEMenuResetDone)).show();
    }

    private void removeAt(int position) {
        if (position < 0 || position >= visible.size()) {
            return;
        }
        int id = visible.remove(position);
        hidden.add(id);
        adapter.notifyItemRemoved(position);
        updateListHeight();
        updateEmpty();
        save();
        haptic(list);
    }

    private void addBack(int id) {
        hidden.remove((Integer) id);
        visible.add(id);
        adapter.notifyItemInserted(visible.size() - 1);
        updateListHeight();
        updateEmpty();
        save();
        if ((id == 2100 || id == 2101) && !NaConfig.INSTANCE.getEnableSaveDeletedMessages().Bool()) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.OEMenuDeletedNeedSave)).show();
        }
    }

    private void showAddPopup(View anchor) {
        dismissAddPopup();
        if (getContext() == null || fragmentView == null) {
            return;
        }
        if (hidden.isEmpty()) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.OEMenuAllVisible)).show();
            return;
        }
        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout =
                new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext(), R.drawable.popup_fixed_alert, getResourceProvider());
        popupLayout.setFitItems(true);
        for (int i = 0; i < hidden.size(); i++) {
            final int id = hidden.get(i);
            OverflowMenus.Entry entry = byId.get(id);
            if (entry == null) {
                continue;
            }
            ActionBarMenuSubItem cell = ActionBarMenuItem.addItem(
                    popupLayout, entry.icon, getString(entry.titleRes), false, getResourceProvider());
            if (entry.destructive) {
                int red = getThemedColor(Theme.key_text_RedRegular);
                cell.setColors(red, red);
            }
            cell.setOnClickListener(v -> {
                dismissAddPopup();
                addBack(id);
            });
        }
        ActionBarPopupWindow popup = new ActionBarPopupWindow(
                popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popup.setPauseNotifications(true);
        popup.setDismissAnimationDuration(180);
        popup.setOutsideTouchable(true);
        popup.setClippingEnabled(true);
        popup.setAnimationStyle(R.style.PopupContextAnimation);
        popup.setFocusable(true);
        popup.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupLayout.measure(
                View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST));
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        int x = loc[0] + anchor.getWidth() / 2 - popupLayout.getMeasuredWidth() / 2;
        int y = loc[1] - popupLayout.getMeasuredHeight() - dp(6);
        if (y < dp(64)) {
            y = loc[1] + anchor.getHeight() + dp(6);
        }
        addPopup = popup;
        popup.showAtLocation(fragmentView, Gravity.NO_GRAVITY, Math.max(dp(8), x), y);
        try {
            popup.dimBehind();
        } catch (Exception ignored) {
        }
    }

    private void dismissAddPopup() {
        if (addPopup != null) {
            addPopup.dismiss();
            addPopup = null;
        }
    }

    private static void haptic(View view) {
        if (view == null) {
            return;
        }
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onFragmentDestroy() {
        dismissAddPopup();
        super.onFragmentDestroy();
    }

    private class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.Holder> {

        MenuAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return visible.get(position);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ActionBarMenuSubItem cell = new ActionBarMenuSubItem(parent.getContext(), false, false, getResourceProvider());
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            return new Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            int id = visible.get(position);
            OverflowMenus.Entry entry = byId.get(id);
            ActionBarMenuSubItem cell = holder.cell;
            if (entry == null) {
                cell.setTextAndIcon("", 0);
                return;
            }
            cell.setTextAndIcon(getString(entry.titleRes), entry.icon);
            if (entry.destructive) {
                int red = getThemedColor(Theme.key_text_RedRegular);
                cell.setColors(red, red);
            } else {
                cell.setColors(
                        getThemedColor(Theme.key_actionBarDefaultSubmenuItem),
                        getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon));
            }
            cell.updateSelectorBackground(position == 0, false, 6);
            cell.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    haptic(v);
                    removeAt(pos);
                }
            });
            cell.setOnLongClickListener(v -> {
                touchHelper.startDrag(holder);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return visible.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ActionBarMenuSubItem cell;

            Holder(ActionBarMenuSubItem cell) {
                super(cell);
                this.cell = cell;
            }
        }
    }
}
