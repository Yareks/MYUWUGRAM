package app.exteraless.appearance;

import android.graphics.Color;

import org.telegram.ui.ActionBar.Theme;

/**
 * Вуаль поверх фото шапки и шторки. В тёмной теме затемняет чёрным,
 * в светлой осветляет цветом фона окна, чтобы картинка не спорила с темой.
 */
public final class ThemeWash {

    /** Насколько светлая тема осветляет фото, даже если ползунок на нуле. */
    public static final int LIGHT_PERCENT = 46;

    private ThemeWash() {
    }

    public static boolean light() {
        return !Theme.isCurrentThemeDark();
    }

    public static int percent(int dimPercent) {
        final int dim = Math.max(0, Math.min(80, dimPercent));
        return light() ? Math.max(LIGHT_PERCENT, dim) : dim;
    }

    public static int color() {
        if (light()) {
            return Theme.getColor(Theme.key_windowBackgroundWhite);
        }
        return Color.BLACK;
    }
}
