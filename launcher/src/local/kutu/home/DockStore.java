package local.kutu.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Raf duzenini saklar. Kullanicinin duzeni asla otomatik ezilmez:
 * tohum liste sadece kayit hic yoksa yazilir.
 */
public final class DockStore {

    private static final String PREFS = "kutu_home";
    private static final String KEY_DOCK = "dock";
    private static final String KEY_SEEDED = "seeded_v1";

    /**
     * Rafin ilk acilistaki varsayilan sirasi. Kurulu olmayanlar otomatik atlanir.
     * Kendine gore degistir; kullanicinin duzeni bir kez olustuktan sonra
     * bu liste bir daha okunmaz.
     *
     * Default dock order on first launch. Missing packages are skipped.
     * Edit freely; once the user has a layout, this list is never read again.
     */
    private static final String[] SEED = {
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.spotify.tv.android",
            "com.android.vending"
    };

    private DockStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<String> load(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(KEY_SEEDED, false)) {
            List<String> seed = new ArrayList<>(Arrays.asList(SEED));
            save(c, seed);
            p.edit().putBoolean(KEY_SEEDED, true).apply();
            return seed;
        }
        String raw = p.getString(KEY_DOCK, "");
        List<String> out = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String s : raw.split(",")) {
                if (!s.trim().isEmpty()) out.add(s.trim());
            }
        }
        return out;
    }

    public static void save(Context c, List<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pkgs.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(pkgs.get(i));
        }
        prefs(c).edit().putString(KEY_DOCK, sb.toString()).apply();
    }
}
