package local.kutu.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeActivity extends Activity {

    private static final String MIRROR_PKG = "io.github.jqssun.airplay";
    private static final int COLS = 5;

    private GridLayout dock;
    private HorizontalScrollView shelfScroll;
    private TextView clock, date, focusedLabel, moveHint;

    private final Map<String, AppInfo> catalog = new LinkedHashMap<>();
    private final List<String> dockPkgs = new ArrayList<>();

    private int tilePx, gapPx;
    private float radiusPx;

    private boolean moveMode = false;
    private View moveTarget = null;

    private final BroadcastReceiver timeTick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };

    private boolean dirty = true;

    private final BroadcastReceiver pkgChange = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            Uri data = i.getData();
            if (data != null) IconCache.invalidate(c, data.getSchemeSpecificPart());
            dirty = true;
            reload();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_home);

        dock = (GridLayout) findViewById(R.id.dock);
        dock.setOrientation(GridLayout.HORIZONTAL);
        dock.setColumnCount(COLS);
        shelfScroll = (HorizontalScrollView) findViewById(R.id.shelfScroll);
        clock = (TextView) findViewById(R.id.clock);
        date = (TextView) findViewById(R.id.date);
        focusedLabel = (TextView) findViewById(R.id.focusedLabel);
        moveHint = (TextView) findViewById(R.id.moveHint);

        tilePx = dp(72);
        gapPx = dp(14);
        radiusPx = dp(14);

        findViewById(R.id.chipMirror).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openMirroring(); }
        });
        findViewById(R.id.chipSettings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSettings(); }
        });
        findViewById(R.id.chipApps).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, AllAppsActivity.class));
            }
        });

        setBrand();
        updateClock();
        reload();
    }

    private void setBrand() {
        String ver = "";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        TextView b = (TextView) findViewById(R.id.brand);
        String owner = getString(R.string.brand_owner);
        if (owner == null || owner.trim().isEmpty()) {
            b.setVisibility(View.GONE);
        } else {
            b.setText(owner + "  ·  v" + ver);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(timeTick, new IntentFilter(Intent.ACTION_TIME_TICK));

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgChange, f);

        updateClock();
        if (dirty) reload();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(timeTick); } catch (Exception ignored) {}
        try { unregisterReceiver(pkgChange); } catch (Exception ignored) {}
        exitMoveMode();
    }

    /** HOME tusu: her zaman ana ekranda kal, hicbir sey yapma. */
    @Override public void onBackPressed() {
        if (moveMode) { exitMoveMode(); return; }
        // Ana ekranda BACK cikis yapmaz.
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private void updateClock() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        date.setText(new SimpleDateFormat("d MMMM EEEE", new Locale("tr", "TR")).format(now));
    }

    private void reload() {
        catalog.clear();
        for (AppInfo a : AppRepository.loadAll(this)) {
            catalog.put(a.packageName, a);
        }
        dockPkgs.clear();
        for (String p : DockStore.load(this)) {
            if (catalog.containsKey(p)) dockPkgs.add(p);
        }
        // Rafta hic gecerli uygulama kalmadiysa, kullanici kaybolmasin diye
        // katalogdan ilk birkacini goster (kayit ezilmez).
        if (dockPkgs.isEmpty()) {
            int n = 0;
            for (String p : catalog.keySet()) {
                dockPkgs.add(p);
                if (++n >= 6) break;
            }
        }
        buildDock();
        dirty = false;
    }

    private void buildDock() {
        dock.removeAllViews();
        PackageManager pm = getPackageManager();
        for (int i = 0; i < dockPkgs.size(); i++) {
            AppInfo info = catalog.get(dockPkgs.get(i));
            if (info == null) continue;
            dock.addView(makeTile(pm, info, i == 0));
        }
    }

    private View makeTile(PackageManager pm, final AppInfo info, boolean first) {
        final ImageView iv = new ImageView(this);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = tilePx;
        lp.height = tilePx;
        int m = gapPx / 2;
        lp.setMargins(m, m, m, m);
        iv.setLayoutParams(lp);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setFocusable(true);
        iv.setFocusableInTouchMode(false);
        iv.setTag(info.packageName);
        iv.setContentDescription(info.label);

        iv.setImageBitmap(IconCache.get(this, info, tilePx, radiusPx));

        iv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                applyFocus(v, has);
                if (has) {
                    focusedLabel.setText(info.label);
                    ensureVisible(v);
                }
            }
        });

        iv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (moveMode) { exitMoveMode(); return; }
                launch(info.packageName);
            }
        });

        // Fiziksel kumandada saglam uzun basma:
        // DPAD_CENTER/ENTER tekrar sayisi 1'e ulastiginda menuyu ac.
        iv.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                boolean center = code == KeyEvent.KEYCODE_DPAD_CENTER
                        || code == KeyEvent.KEYCODE_ENTER
                        || code == KeyEvent.KEYCODE_NUMPAD_ENTER;

                if (moveMode) {
                    if (e.getAction() == KeyEvent.ACTION_DOWN) {
                        if (code == KeyEvent.KEYCODE_DPAD_LEFT)  { shift(v, -1); return true; }
                        if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { shift(v,  1); return true; }
                        if (center) { exitMoveMode(); return true; }
                    }
                    return false;
                }

                if (center && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 1) {
                    showTileMenu(v, info);
                    return true;
                }
                return false;
            }
        });

        iv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                if (moveMode) return true;
                showTileMenu(v, info);
                return true;
            }
        });

        if (first) {
            iv.post(new Runnable() { @Override public void run() { iv.requestFocus(); } });
        }
        return iv;
    }

    private void applyFocus(View v, boolean has) {
        v.animate().cancel();
        v.animate()
                .scaleX(has ? 1.14f : 1f)
                .scaleY(has ? 1.14f : 1f)
                .alpha(has ? 1f : 0.86f)
                .translationZ(has ? dp(8) : 0)
                .setDuration(140)
                .start();
    }

    private void ensureVisible(View v) {
        int left = v.getLeft() - gapPx * 2;
        int right = v.getRight() + gapPx * 2;
        int sx = shelfScroll.getScrollX();
        int w = shelfScroll.getWidth();
        if (left < sx) shelfScroll.smoothScrollTo(Math.max(0, left), 0);
        else if (right > sx + w) shelfScroll.smoothScrollTo(right - w, 0);
    }

    private void launch(String pkg) {
        Intent i = AppRepository.launchIntent(this, pkg);
        if (i == null) {
            Toast.makeText(this, "Uygulama açılamadı", Toast.LENGTH_SHORT).show();
            return;
        }
        try { startActivity(i); }
        catch (Exception e) { Toast.makeText(this, "Uygulama açılamadı", Toast.LENGTH_SHORT).show(); }
    }

    private void openMirroring() {
        Intent i = AppRepository.launchIntent(this, MIRROR_PKG);
        if (i != null) { startActivity(i); return; }
        try {
            startActivity(new Intent("android.settings.CAST_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Yansıtma uygulaması bulunamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Ayarlar açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTileMenu(final View tile, final AppInfo info) {
        final CharSequence[] items = {
                getString(R.string.menu_move),
                getString(R.string.menu_remove),
                getString(R.string.menu_info),
                getString(R.string.menu_uninstall)
        };
        new AlertDialog.Builder(this)
                .setTitle(info.label)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0: enterMoveMode(tile); break;
                            case 1: removeFromDock(info.packageName); break;
                            case 2: openAppInfo(info.packageName); break;
                            case 3: requestUninstall(info.packageName); break;
                        }
                    }
                })
                .show();
    }

    private void enterMoveMode(View tile) {
        moveMode = true;
        moveTarget = tile;
        moveHint.setVisibility(View.VISIBLE);
        tile.setAlpha(0.7f);
        tile.requestFocus();
    }

    private void exitMoveMode() {
        if (!moveMode) return;
        moveMode = false;
        moveHint.setVisibility(View.GONE);
        if (moveTarget != null) moveTarget.setAlpha(1f);
        moveTarget = null;
        persistDock();
    }

    private void shift(View tile, int dir) {
        int idx = dock.indexOfChild(tile);
        int target = idx + dir;
        if (target < 0 || target >= dock.getChildCount()) return;
        dock.removeViewAt(idx);
        dock.addView(tile, target);
        fixMargins();
        tile.requestFocus();
        ensureVisible(tile);
    }

    private void fixMargins() {
        // GridLayout'ta kenar bosluklari esit, yeniden hesap gerekmiyor.
        dock.requestLayout();
    }

    private void persistDock() {
        List<String> order = new ArrayList<>();
        for (int i = 0; i < dock.getChildCount(); i++) {
            Object t = dock.getChildAt(i).getTag();
            if (t != null) order.add(String.valueOf(t));
        }
        dockPkgs.clear();
        dockPkgs.addAll(order);
        DockStore.save(this, order);
    }

    private void removeFromDock(String pkg) {
        dockPkgs.remove(pkg);
        DockStore.save(this, dockPkgs);
        buildDock();
    }

    private void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    /** Sessiz kaldirma yok: Android'in kendi onay ekrani acilir. */
    private void requestUninstall(String pkg) {
        try {
            Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Kaldırma ekranı açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

}
