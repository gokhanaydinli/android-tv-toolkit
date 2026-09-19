package local.kutu.home;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;

/**
 * Ikonlari tutarli, yuvarlak kose kartlara normalize eder.
 * Firmware maskesi kullanilmaz: adaptive ikonlarda arka+on katman
 * kendimiz birlestirilir, kirpma bize ait.
 */
public final class IconUtils {

    private static final int INNER_BG = 0xFF14171F; // siyaha yakin ic zemin

    private IconUtils() {}

    public static Bitmap render(Drawable src, int sizePx, float radiusPx, String label) {
        Bitmap raw = drawableToBitmap(src, sizePx);
        if (raw == null) {
            return monogram(label, sizePx, radiusPx);
        }
        boolean needsPlate = hasSignificantTransparency(raw);
        return roundedCard(raw, sizePx, radiusPx, needsPlate);
    }

    private static Bitmap drawableToBitmap(Drawable d, int size) {
        if (d == null) return null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            Drawable bg = a.getBackground();
            Drawable fg = a.getForeground();
            Drawable layered;
            if (bg != null && fg != null) {
                layered = new LayerDrawable(new Drawable[]{bg, fg});
            } else if (fg != null) {
                layered = fg;
            } else {
                layered = bg;
            }
            if (layered == null) return null;
            // Adaptive ikonun guvenli alani merkezin 72/108'i; tasmayi kirpmak
            // yerine biraz iceri cekip tam kareyi kullaniyoruz.
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            int bleed = Math.round(size * 0.08f);
            layered.setBounds(-bleed, -bleed, size + bleed, size + bleed);
            layered.draw(c);
            return b;
        }

        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            Bitmap bm = ((BitmapDrawable) d).getBitmap();
            return Bitmap.createScaledBitmap(bm, size, size, true);
        }

        int w = d.getIntrinsicWidth();
        int h = d.getIntrinsicHeight();
        if (w <= 0 || h <= 0) return null;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        // en-boy oranini koru, ortala
        float scale = Math.min((float) size / w, (float) size / h);
        int dw = Math.round(w * scale);
        int dh = Math.round(h * scale);
        int left = (size - dw) / 2;
        int top = (size - dh) / 2;
        d.setBounds(left, top, left + dw, top + dh);
        d.draw(c);
        return b;
    }

    /** Kenarlarda ve toplamda belirgin saydamlik var mi? */
    private static boolean hasSignificantTransparency(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        if (w == 0 || h == 0) return true;
        int step = Math.max(1, w / 24);
        int transparent = 0, total = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int a = Color.alpha(b.getPixel(x, y));
                if (a < 24) transparent++;
                total++;
            }
        }
        if (total == 0) return true;
        return (transparent * 100 / total) > 12;
    }

    private static Bitmap roundedCard(Bitmap content, int size, float radius, boolean plate) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        RectF r = new RectF(0, 0, size, size);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        if (plate) {
            p.setColor(INNER_BG);
            c.drawRoundRect(r, radius, radius, p);
            // saydam logoyu biraz kucultup ortala ki nefes alsin
            int inset = Math.round(size * 0.14f);
            Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG);
            ip.setFilterBitmap(true);
            c.drawBitmap(content,
                    null,
                    new RectF(inset, inset, size - inset, size - inset),
                    ip);
            return out;
        }

        p.setShader(new BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, p);
        return out;
    }

    public static Bitmap monogram(String label, int size, float radius) {
        String ch = (label == null || label.trim().isEmpty())
                ? "?" : label.trim().substring(0, 1).toUpperCase();
        int hue = Math.abs((label == null ? 0 : label.hashCode())) % 360;
        int bg = Color.HSVToColor(new float[]{hue, 0.35f, 0.26f});

        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(bg);
        c.drawRoundRect(new RectF(0, 0, size, size), radius, radius, p);

        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0xFFF2F4F8);
        tp.setTextSize(size * 0.44f);
        tp.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = tp.getFontMetrics();
        float y = size / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(ch, size / 2f, y, tp);
        return out;
    }

    public static Drawable bestIcon(PackageManager pm, AppInfo info) {
        Drawable d = null;
        try {
            d = pm.getActivityIcon(info.component);
        } catch (Exception ignored) {}
        if (d == null) {
            try {
                d = pm.getApplicationIcon(info.packageName);
            } catch (Exception ignored) {}
        }
        return d;
    }
}
