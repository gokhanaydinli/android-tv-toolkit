package local.kutu.home;

import android.content.ComponentName;
import android.graphics.Bitmap;

public class AppInfo {
    public final String packageName;
    public final ComponentName component;
    public final String label;
    public final boolean leanback;
    public Bitmap icon;

    public AppInfo(String packageName, ComponentName component, String label, boolean leanback) {
        this.packageName = packageName;
        this.component = component;
        this.label = label;
        this.leanback = leanback;
    }
}
