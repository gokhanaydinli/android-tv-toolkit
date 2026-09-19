package local.kutu.home;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Ikonlar bir kez render edilir, diske PNG olarak yazilir.
 * Sonraki aciliglarda sadece decode edilir - adaptive ikon
 * kompozisyonu, saydamlik taramasi ve yuvarlatma tekrar calismaz.
 * Anahtar: paket + surum kodu, yani uygulama guncellenince kendini yeniler.
 */
public final class IconCache {

    private static final Map<String, Bitmap> MEM = new HashMap<>();

    private IconCache() {}

    private static File dir(Context c) {
        File d = new File(c.getCacheDir(), "icons");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static String key(Context c, String pkg, int sizePx) {
        long ver = 0;
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(pkg, 0);
            ver = pi.versionCode;
        } catch (Exception ignored) {}
        return pkg + "_" + ver + "_" + sizePx;
    }

    public static Bitmap get(Context c, AppInfo info, int sizePx, float radiusPx) {
        String k = key(c, info.packageName, sizePx);

        Bitmap m = MEM.get(k);
        if (m != null && !m.isRecycled()) return m;

        File f = new File(dir(c), k + ".png");
        if (f.exists()) {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) {
                MEM.put(k, b);
                return b;
            }
            f.delete();
        }

        Drawable d = IconUtils.bestIcon(c.getPackageManager(), info);
        Bitmap b = IconUtils.render(d, sizePx, radiusPx, info.label);
        MEM.put(k, b);

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception ignored) {
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }
        return b;
    }

    /** Paket silindiginde/degistiginde o pakete ait onbellegi dusur. */
    public static void invalidate(Context c, String pkg) {
        File d = dir(c);
        File[] files = d.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().startsWith(pkg + "_")) f.delete();
            }
        }
        java.util.Iterator<String> it = MEM.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(pkg + "_")) it.remove();
        }
    }
}
