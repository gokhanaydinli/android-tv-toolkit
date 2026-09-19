package local.kutu.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class AllAppsActivity extends Activity {

    private static final int COLUMNS = 6;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        int pad = dp(48);
        int tile = dp(88);
        float radius = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(27), pad, dp(27));

        TextView title = new TextView(this);
        title.setText(R.string.all_apps_title);
        title.setTextColor(Color.parseColor("#FFF2F4F8"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setPadding(0, 0, 0, dp(18));
        root.addView(title);

        final TextView label = new TextView(this);
        label.setTextColor(Color.parseColor("#FF9AA3B2"));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        label.setSingleLine(true);
        label.setPadding(0, 0, 0, dp(12));
        root.addView(label);

        ScrollView sv = new ScrollView(this);
        sv.setScrollbarFadingEnabled(true);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);

        PackageManager pm = getPackageManager();
        List<AppInfo> apps = AppRepository.loadAll(this);
        final List<String> dockNow = new ArrayList<>(DockStore.load(this));

        for (final AppInfo info : apps) {
            ImageView iv = new ImageView(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = tile;
            lp.height = tile;
            lp.setMargins(dp(10), dp(10), dp(10), dp(10));
            iv.setLayoutParams(lp);
            iv.setFocusable(true);
            iv.setContentDescription(info.label);

            iv.setImageBitmap(IconCache.get(this, info, tile, radius));

            iv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean has) {
                    v.animate().cancel();
                    v.animate().scaleX(has ? 1.14f : 1f).scaleY(has ? 1.14f : 1f)
                            .alpha(has ? 1f : 0.86f).setDuration(140).start();
                    if (has) label.setText(info.label);
                }
            });

            iv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Intent i = AppRepository.launchIntent(AllAppsActivity.this, info.packageName);
                    if (i == null) {
                        Toast.makeText(AllAppsActivity.this, "Açılamadı", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    startActivity(i);
                }
            });

            iv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    final boolean inDock = dockNow.contains(info.packageName);
                    CharSequence[] items = { getString(inDock ? R.string.menu_remove : R.string.menu_add) };
                    new AlertDialog.Builder(AllAppsActivity.this)
                            .setTitle(info.label)
                            .setItems(items, new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface dlg, int w) {
                                    if (inDock) dockNow.remove(info.packageName);
                                    else dockNow.add(info.packageName);
                                    DockStore.save(AllAppsActivity.this, dockNow);
                                    Toast.makeText(AllAppsActivity.this,
                                            inDock ? "Raftan çıkarıldı" : "Rafa eklendi",
                                            Toast.LENGTH_SHORT).show();
                                }
                            })
                            .show();
                    return true;
                }
            });

            grid.addView(iv);
        }

        sv.addView(grid);
        root.addView(sv);
        root.setGravity(Gravity.START);
        setContentView(root);
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
