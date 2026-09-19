package local.kutu.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cihazdaki acilabilir uygulamalari okur.
 * Once Leanback (TV) girisleri, sonra normal launcher girisleri -
 * TV girisi olmayan uygulamalar da kaybolmasin diye.
 */
public final class AppRepository {

    private AppRepository() {}

    public static List<AppInfo> loadAll(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, AppInfo> byPackage = new LinkedHashMap<>();

        collect(pm, byPackage, Intent.CATEGORY_LEANBACK_LAUNCHER, true);
        collect(pm, byPackage, Intent.CATEGORY_LAUNCHER, false);

        byPackage.remove(ctx.getPackageName());

        List<AppInfo> out = new ArrayList<>(byPackage.values());
        Collections.sort(out, new Comparator<AppInfo>() {
            @Override public int compare(AppInfo a, AppInfo b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    private static void collect(PackageManager pm, Map<String, AppInfo> into,
                                String category, boolean leanback) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(category);
        List<ResolveInfo> list;
        try {
            list = pm.queryIntentActivities(i, 0);
        } catch (Exception e) {
            return;
        }
        if (list == null) return;
        for (ResolveInfo ri : list) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (into.containsKey(pkg)) continue; // Leanback onceligi korunur
            ComponentName cn = new ComponentName(pkg, ri.activityInfo.name);
            String label;
            try {
                label = String.valueOf(ri.loadLabel(pm));
            } catch (Exception e) {
                label = pkg;
            }
            into.put(pkg, new AppInfo(pkg, cn, label, leanback));
        }
    }

    /** Leanback giris tercihli, yoksa normal launcher girisi. */
    public static Intent launchIntent(Context ctx, String pkg) {
        PackageManager pm = ctx.getPackageManager();
        Intent i = null;
        try {
            i = pm.getLeanbackLaunchIntentForPackage(pkg);
        } catch (Throwable ignored) {}
        if (i == null) {
            try {
                i = pm.getLaunchIntentForPackage(pkg);
            } catch (Throwable ignored) {}
        }
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
        return i;
    }
}
