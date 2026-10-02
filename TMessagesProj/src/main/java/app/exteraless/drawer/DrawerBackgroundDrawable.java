package app.exteraless.drawer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

/**
 * Фон панели бокового меню: базовый цвет темы, поверх — опциональная картинка
 * и равномерное затемнение в процентах. По умолчанию center-crop; режим
 * растяжения кладёт картинку на всю панель без обрезки.
 *
 * Декод происходит один раз на фоновом потоке: до готовности битмапа виден
 * просто цвет темы, поэтому первое открытие шторки никогда не подвисает.
 */
public class DrawerBackgroundDrawable extends Drawable {

    /** Потолок декодируемой стороны, чтобы 4K-фото не съедало память. */
    private static final int MAX_SIDE = 1080;

    private final String path;
    private final int dim;
    private final boolean stretch;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint dimPaint = new Paint();
    private final Rect tmpRect = new Rect();

    private volatile Bitmap bitmap;
    private boolean loadStarted;

    public DrawerBackgroundDrawable(String path, int dim, boolean stretch) {
        this.path = path;
        this.dim = Math.max(0, Math.min(70, dim));
        this.stretch = stretch;
        dimPaint.setColor(Color.BLACK);
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect b = getBounds();
        canvas.drawColor(Theme.getColor(Theme.key_chats_menuBackground));

        Bitmap bmp = bitmap;
        if (path != null && bmp == null && !loadStarted) {
            startLoad();
        }
        if (bmp != null) {
            if (stretch) {
                tmpRect.set(b);
            } else {
                final float scale = Math.max(
                        (float) b.width() / bmp.getWidth(),
                        (float) b.height() / bmp.getHeight());
                final int w = Math.round(bmp.getWidth() * scale);
                final int h = Math.round(bmp.getHeight() * scale);
                tmpRect.set(
                        b.left + (b.width() - w) / 2,
                        b.top + (b.height() - h) / 2,
                        b.left + (b.width() + w) / 2,
                        b.top + (b.height() + h) / 2);
            }
            canvas.drawBitmap(bmp, null, tmpRect, bitmapPaint);
        }
        if (dim > 0) {
            dimPaint.setAlpha(dim * 255 / 100);
            canvas.drawRect(b, dimPaint);
        }
    }

    private void startLoad() {
        loadStarted = true;
        new Thread(() -> {
            Bitmap decoded = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, bounds);
                int sample = 1;
                while (bounds.outWidth / sample > MAX_SIDE || bounds.outHeight / sample > MAX_SIDE) {
                    sample *= 2;
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                decoded = BitmapFactory.decodeFile(path, opts);
            } catch (Throwable t) {
                FileLog.e("DrawerBackground: decode failed", t);
            }
            bitmap = decoded;
            AndroidUtilities.runOnUIThread(this::invalidateSelf);
        }, "drawer-bg-decode").start();
    }

    @Override
    public void setAlpha(int alpha) {
        bitmapPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        bitmapPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
