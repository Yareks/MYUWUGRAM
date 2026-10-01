package app.exteraless.appearance;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.BitmapShader;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;

/**
 * Кастомный фон шапки профиля («баннер»), как в плагине Custom Banner.
 *
 * Хранится копией во внутреннем хранилище, ключ пути — {@link #PREF_PATH}, затемнение —
 * {@link AppearanceConfig#profileBackgroundDim}. Битмап грузится один раз на фоновой
 * очереди; метод {@link #draw} возвращает false, пока картинки ещё нет, — вызывающий
 * код рисует обычную подложку, поэтому мигания и пустых кадров не бывает.
 */
public final class ProfileBanner {

    public static final String PREF_PATH = "OEAppearanceProfileBgPath";
    public static final String FILE_NAME = "profile_background.jpg";

    private static final int MAX_SIDE = 1440;

    private static volatile Bitmap bitmap;
    private static volatile String bitmapPath;
    private static boolean loadStarted;

    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Paint dimPaint = new Paint();
    private static final Matrix matrix = new Matrix();

    private ProfileBanner() {
    }

    /** Путь к текущей картинке или null, если её нет / файл потерялся. */
    public static String currentPath() {
        String path = AppearanceConfig.getPreferences().getString(PREF_PATH, null);
        return path != null && new File(path).exists() ? path : null;
    }

    /** Сбросить кэш после смены или удаления картинки в настройках. */
    public static void reload() {
        bitmap = null;
        bitmapPath = null;
        loadStarted = false;
    }

    /**
     * Рисует баннер в прямоугольник [0,0,w,h] канваса шапки профиля (center-crop + затемнение).
     *
     * @return true, если баннер отрисован и обычную заливку шапки рисовать не нужно.
     */
    public static boolean draw(Canvas canvas, View host, int w, int h) {
        final String path = currentPath();
        if (path == null || w <= 0 || h <= 0) {
            return false;
        }
        Bitmap bmp = bitmap;
        if (bmp == null || !path.equals(bitmapPath)) {
            if (!loadStarted) {
                startLoad(path, host);
            }
            return false;
        }
        final float scale = Math.max((float) w / bmp.getWidth(), (float) h / bmp.getHeight());
        matrix.reset();
        matrix.setScale(scale, scale);
        matrix.postTranslate((w - bmp.getWidth() * scale) / 2f, (h - bmp.getHeight() * scale) / 2f);
        BitmapShader shader = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        canvas.save();
        canvas.clipRect(0, 0, w, h);
        canvas.drawPaint(paint);
        final int dim = AppearanceConfig.profileBackgroundDim.Int();
        if (dim > 0) {
            dimPaint.setColor(Color.BLACK);
            dimPaint.setAlpha(dim * 255 / 100);
            canvas.drawRect(0, 0, w, h, dimPaint);
        }
        canvas.restore();
        paint.setShader(null);
        return true;
    }

    private static void startLoad(final String path, final View host) {
        loadStarted = true;
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
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
                decoded = BitmapFactory.decodeFile(path, opts);
            } catch (Throwable t) {
                FileLog.e("ProfileBanner: decode failed", t);
            }
            bitmap = decoded;
            bitmapPath = decoded != null ? path : null;
            loadStarted = false;
            AndroidUtilities.runOnUIThread(() -> {
                if (host != null) {
                    host.invalidate();
                }
            });
        });
    }

    /** Файл-копия картинки во внутреннем хранилище (для экрана настроек). */
    public static File targetFile() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), FILE_NAME);
    }
}
