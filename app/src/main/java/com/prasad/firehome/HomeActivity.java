package com.prasad.firehome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 5-column wide rectangular banner launcher with top Wi-Fi & Settings buttons. */
public class HomeActivity extends Activity {

    private static final String PREFS = "firehome";
    private static final String KEY_ORDER = "order";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_WALL = "wall";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final String KEY_CITY = "city";
    private static final String KEY_CORNER_PCT = "corner_pct";

    private static final int ID_BTN_CLEAN = 2000;
    private static final int ID_BTN_WIFI = 2001;
    private static final int ID_BTN_SETTINGS = 2002;
    private static final int ID_BTN_OPTIONS = 2003;
    private static final int ID_GRID = 2004;

    private static final int[][] GRADIENTS = {
            {0xFF0D9488, 0xFF064E3B}, // 1. Emerald Mint & Deep Teal
            {0xFF0284C7, 0xFF1E3A8A}, // 2. Ocean Cyan & Royal Blue
            {0xFF06B6D4, 0xFF312E81}, // 3. Tropical Aqua & Midnight Indigo
            {0xFFE11D48, 0xFF4C0519}, // 4. Sunset Coral & Deep Berry
            {0xFF7C3AED, 0xFF2E1065}, // 5. Electric Violet & Deep Plum
            {0xFF16A34A, 0xFF14532D}, // 6. Forest Sage & Pine Green
            {0xFFD97706, 0xFF78350F}, // 7. Golden Amber & Warm Bronze
            {0xFF2563EB, 0xFF0F172A}, // 8. Sky Azure & Sapphire Night
            {0xFFDB2777, 0xFF500724}, // 9. Rose Magenta & Velvet Wine
            {0xFF14B8A6, 0xFF0F172A}, // 10. Lagoon Turquoise & Sea Slate
            {0xFF65A30D, 0xFF064E3B}, // 11. Aurora Lime & Deep Emerald
            {0xFFEA580C, 0xFF431407}, // 12. Tangerine Copper & Espresso
            {0xFF0EA5E9, 0xFF1E293B}, // 13. Nordic Ice & Steel Blue
            {0xFF9333EA, 0xFF172554}, // 14. Amethyst Orchid & Royal Navy
            {0xFF334155, 0xFF090D16}, // 15. Classic Carbon & Obsidian
    };

    private static final String[] WALLPAPER_NAMES = {
            "1. Emerald Mint & Deep Teal",
            "2. Ocean Cyan & Royal Blue",
            "3. Tropical Aqua & Midnight Indigo",
            "4. Sunset Coral & Deep Berry",
            "5. Electric Violet & Deep Plum",
            "6. Forest Sage & Pine Green",
            "7. Golden Amber & Warm Bronze",
            "8. Sky Azure & Sapphire Night",
            "9. Rose Magenta & Velvet Wine",
            "10. Lagoon Turquoise & Sea Slate",
            "11. Aurora Lime & Deep Emerald",
            "12. Tangerine Copper & Espresso",
            "13. Nordic Ice & Steel Blue",
            "14. Amethyst Orchid & Royal Navy",
            "15. Classic Carbon & Obsidian"
    };

    private static final int[] BANNER_ACCENTS = {
            0xFF1E3A5F, 0xFF2D234A, 0xFF1B4332, 0xFF4A1D24, 0xFF2C3E50, 0xFF3B2F2F
    };

    private static class AppItem {
        String id;
        String label;
        Drawable banner;
        Drawable icon;
        int[] cardColors;
        Intent launch;
    }

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppItem> items = new ArrayList<>();
    private AppAdapter adapter;
    private GridView grid;
    private ImageView wallpaper;
    private TextView weatherView;
    private FrameLayout screensaverOverlay;
    private LinearLayout screensaverCard;
    private TextView screensaverWeatherView;
    private boolean screensaverActive = false;
    private long screensaverStartMs = 0;
    private int driftStep = 0;
    private static final long SCREENSAVER_IDLE_MS = 5 * 60 * 1000L; // 5 minutes
    private int selectedAfterMove = -1;

    private final Runnable screensaverIdleTrigger = new Runnable() {
        @Override
        public void run() {
            startScreensaver();
        }
    };

    private final Runnable screensaverDriftTick = new Runnable() {
        @Override
        public void run() {
            if (!screensaverActive || screensaverCard == null) return;
            driftStep++;
            double angle = driftStep * 0.65;
            float dx = (float) (Math.sin(angle) * dp(210));
            float dy = (float) (Math.cos(angle * 0.75) * dp(115));
            screensaverCard.animate()
                    .translationX(dx)
                    .translationY(dy)
                    .setDuration(4200)
                    .start();
            handler.postDelayed(this, 4200);
        }
    };

    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            applyWallpaper();
            fetchWeather();
            handler.postDelayed(this, 30 * 60 * 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.contains(KEY_WALL)) {
            prefs.edit().putInt(KEY_WALL, -2).apply(); // Default to Auto Day/Night Mode
        }
        buildUi();
        applyWallpaper();
        if (getIntent() != null && getIntent().getBooleanExtra("screensaver", false)) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startScreensaver();
                }
            }, 300);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureAccessibilityEnabled();
        applyWallpaper();
        loadApps();
        handler.removeCallbacks(weatherTick);
        handler.post(weatherTick);
        resetIdleTimer();
    }

    private void ensureAccessibilityEnabled() {
        try {
            String svc = getPackageName() + "/" + HomeAccessibilityService.class.getName();
            String cur = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null || !cur.contains(svc)) {
                Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, svc);
            }
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(screensaverIdleTrigger);
        handler.removeCallbacks(screensaverDriftTick);
        if (screensaverActive) {
            stopScreensaverImmediate();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        ensureAccessibilityEnabled();
        loadApps();
        if (intent != null && intent.getBooleanExtra("screensaver", false)) {
            startScreensaver();
            return;
        }
        if (screensaverActive) {
            stopScreensaver();
        }
        resetIdleTimer();
        if (intent != null && Intent.ACTION_MAIN.equals(intent.getAction()) && grid != null) {
            grid.smoothScrollToPosition(0);
            grid.requestFocus();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (screensaverActive) {
            if (System.currentTimeMillis() - screensaverStartMs > 600
                    && event.getAction() == KeyEvent.ACTION_DOWN) {
                stopScreensaver();
            }
            return true;
        }
        resetIdleTimer();
        return super.dispatchKeyEvent(event);
    }

    private void resetIdleTimer() {
        handler.removeCallbacks(screensaverIdleTrigger);
        handler.postDelayed(screensaverIdleTrigger, SCREENSAVER_IDLE_MS);
    }

    private void startScreensaver() {
        if (screensaverOverlay == null || screensaverActive) return;
        screensaverActive = true;
        screensaverStartMs = System.currentTimeMillis();
        handler.removeCallbacks(screensaverIdleTrigger);
        handler.removeCallbacks(screensaverDriftTick);
        if (weatherView != null && weatherView.getText().length() > 0 && screensaverWeatherView != null) {
            screensaverWeatherView.setText(weatherView.getText());
        }
        screensaverCard.setTranslationX(0f);
        screensaverCard.setTranslationY(0f);
        screensaverOverlay.setAlpha(0f);
        screensaverOverlay.setVisibility(View.VISIBLE);
        screensaverOverlay.animate().alpha(1f).setDuration(800).start();
        handler.postDelayed(screensaverDriftTick, 400);
    }

    private void stopScreensaver() {
        if (!screensaverActive || screensaverOverlay == null) return;
        screensaverActive = false;
        handler.removeCallbacks(screensaverDriftTick);
        screensaverOverlay.animate().alpha(0f).setDuration(320).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (!screensaverActive && screensaverOverlay != null) {
                    screensaverOverlay.setVisibility(View.GONE);
                }
            }
        }).start();
        resetIdleTimer();
    }

    private void stopScreensaverImmediate() {
        screensaverActive = false;
        handler.removeCallbacks(screensaverDriftTick);
        if (screensaverOverlay != null) {
            screensaverOverlay.animate().cancel();
            screensaverOverlay.setAlpha(0f);
            screensaverOverlay.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        // Home screen: do nothing.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showMainMenu();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------------------------------------------------------------- UI

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView makeTopButton(int id, String text, View.OnClickListener click) {
        TextView b = new TextView(this);
        b.setId(id);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setFocusable(true);
        b.setFocusableInTouchMode(false);
        b.setClickable(true);
        b.setNextFocusDownId(ID_GRID);

        StateListDrawable bg = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0x66FFFFFF);
        focused.setCornerRadius(dp(22));
        focused.setStroke(dp(2), Color.WHITE);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0x38FFFFFF);
        normal.setCornerRadius(dp(22));
        normal.setStroke(dp(1), 0x44FFFFFF);

        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{android.R.attr.state_selected}, focused);
        bg.addState(new int[]{}, normal);
        b.setBackground(bg);
        b.setOnClickListener(click);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.animate()
                        .scaleX(hasFocus ? 1.07f : 1.0f)
                        .scaleY(hasFocus ? 1.07f : 1.0f)
                        .translationZ(hasFocus ? dp(8) : 0f)
                        .setDuration(150)
                        .start();
            }
        });
        return b;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        View dim = new View(this);
        dim.setBackgroundColor(0x1C000000);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(44), dp(26), dp(44), dp(14));
        column.setClipChildren(false);
        column.setClipToPadding(false);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        // Top Header Row: Clock/Date on left, Weather + Wi-Fi & Settings buttons on right
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        header.setClipToPadding(false);
        column.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        header.addView(clockBox, new LinearLayout.LayoutParams(0, -2, 1f));

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("h:mm a");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(44);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockBox.addView(clock);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(0xCCFFFFFF);
        date.setTextSize(17);
        clockBox.addView(date);

        weatherView = new TextView(this);
        weatherView.setTextColor(Color.WHITE);
        weatherView.setTextSize(20);
        weatherView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
        wp.rightMargin = dp(22);
        header.addView(weatherView, wp);

        // Top Bar Action Buttons: Clean RAM, Wi-Fi, Settings, Options
        TextView cleanBtn = makeTopButton(ID_BTN_CLEAN, "\uD83E\uDDF9  Clean RAM", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cleanRam();
            }
        });
        LinearLayout.LayoutParams bp0 = new LinearLayout.LayoutParams(-2, -2);
        bp0.rightMargin = dp(12);
        header.addView(cleanBtn, bp0);

        TextView wifiBtn = makeTopButton(ID_BTN_WIFI, "\uD83D\uDCF6  Wi-Fi", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWifiSettings();
            }
        });
        LinearLayout.LayoutParams bp1 = new LinearLayout.LayoutParams(-2, -2);
        bp1.rightMargin = dp(12);
        header.addView(wifiBtn, bp1);

        TextView settingsBtn = makeTopButton(ID_BTN_SETTINGS, "\u2699\uFE0F  Settings", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMainSettings();
            }
        });
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(-2, -2);
        bp2.rightMargin = dp(12);
        header.addView(settingsBtn, bp2);

        TextView optionsBtn = makeTopButton(ID_BTN_OPTIONS, "\u2630  Options", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMainMenu();
            }
        });
        header.addView(optionsBtn, new LinearLayout.LayoutParams(-2, -2));

        cleanBtn.setNextFocusRightId(ID_BTN_WIFI);
        wifiBtn.setNextFocusLeftId(ID_BTN_CLEAN);
        wifiBtn.setNextFocusRightId(ID_BTN_SETTINGS);
        settingsBtn.setNextFocusLeftId(ID_BTN_WIFI);
        settingsBtn.setNextFocusRightId(ID_BTN_OPTIONS);
        optionsBtn.setNextFocusLeftId(ID_BTN_SETTINGS);

        // 5-column wide rectangular app grid
        grid = new GridView(this);
        grid.setId(ID_GRID);
        grid.setNumColumns(5);
        grid.setHorizontalSpacing(dp(18));
        grid.setVerticalSpacing(dp(12));
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(dp(8), dp(14), dp(8), dp(10));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setDrawSelectorOnTop(false);
        grid.setVerticalScrollBarEnabled(false);
        grid.setFocusable(true);
        grid.setNextFocusUpId(ID_BTN_CLEAN);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        column.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateGridZoom();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateGridZoom();
            }
        });
        grid.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                updateGridZoom();
            }
        });

        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                launch(items.get(pos));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                showItemMenu(pos);
                return true;
            }
        });

        TextView hint = new TextView(this);
        hint.setText("Hold OK on an app: move / hide    \u2022    Press UP for Wi-Fi & Settings    \u2022    Menu button: options");
        hint.setTextColor(0x99FFFFFF);
        hint.setTextSize(13);
        column.addView(hint, new LinearLayout.LayoutParams(-2, -2));

        // Full-Screen Ambient Screensaver Overlay (fades in after 5 minutes idle)
        screensaverOverlay = new FrameLayout(this);
        screensaverOverlay.setBackgroundColor(0xF604060B);
        screensaverOverlay.setVisibility(View.GONE);
        screensaverOverlay.setAlpha(0f);

        screensaverCard = new LinearLayout(this);
        screensaverCard.setOrientation(LinearLayout.VERTICAL);
        screensaverCard.setGravity(Gravity.CENTER);
        screensaverCard.setPadding(dp(48), dp(34), dp(48), dp(34));
        GradientDrawable ssBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0x22FFFFFF, 0x0CFFFFFF});
        ssBg.setCornerRadius(dp(34));
        ssBg.setStroke(dp(1), 0x33FFFFFF);
        screensaverCard.setBackground(ssBg);

        TextClock ssClock = new TextClock(this);
        ssClock.setFormat12Hour("h:mm a");
        ssClock.setFormat24Hour("h:mm a");
        ssClock.setTextColor(0xF5FFFFFF);
        ssClock.setTextSize(82);
        ssClock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        ssClock.setGravity(Gravity.CENTER);
        screensaverCard.addView(ssClock, new LinearLayout.LayoutParams(-2, -2));

        TextClock ssDate = new TextClock(this);
        ssDate.setFormat12Hour("EEEE, d MMMM");
        ssDate.setFormat24Hour("EEEE, d MMMM");
        ssDate.setTextColor(0xBBFFFFFF);
        ssDate.setTextSize(22);
        ssDate.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ssDateLp = new LinearLayout.LayoutParams(-2, -2);
        ssDateLp.topMargin = dp(4);
        screensaverCard.addView(ssDate, ssDateLp);

        screensaverWeatherView = new TextView(this);
        screensaverWeatherView.setText("\u26C5  26\u00B0C  Mysore");
        screensaverWeatherView.setTextColor(0xEEFFFFFF);
        screensaverWeatherView.setTextSize(28);
        screensaverWeatherView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        screensaverWeatherView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ssWeatherLp = new LinearLayout.LayoutParams(-2, -2);
        ssWeatherLp.topMargin = dp(18);
        screensaverCard.addView(screensaverWeatherView, ssWeatherLp);

        TextView ssHint = new TextView(this);
        ssHint.setText("\uD83C\uDF19  Ambient Anti-Burn-In Mode   \u2022   Press any button to wake");
        ssHint.setTextColor(0x77FFFFFF);
        ssHint.setTextSize(13);
        ssHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ssHintLp = new LinearLayout.LayoutParams(-2, -2);
        ssHintLp.topMargin = dp(20);
        screensaverCard.addView(ssHint, ssHintLp);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-2, -2);
        cardLp.gravity = Gravity.CENTER;
        screensaverOverlay.addView(screensaverCard, cardLp);
        root.addView(screensaverOverlay, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
    }

    private void cleanRam() {
        ensureAccessibilityEnabled();
        final ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        ActivityManager.MemoryInfo miBefore = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(miBefore);
        final long beforeMB = miBefore.availMem / (1024L * 1024L);
        final long totalMB = miBefore.totalMem / (1024L * 1024L);

        // Find all running user apps (and launchable heavy apps) where FLAG_STOPPED == 0
        final List<String> runningApps = new ArrayList<>();
        String myPkg = getPackageName();
        PackageManager pm = getPackageManager();
        for (PackageInfo pi : pm.getInstalledPackages(0)) {
            if (pi == null || pi.applicationInfo == null) continue;
            String pkg = pi.packageName;
            if (pkg == null || myPkg.equals(pkg)) continue;
            am.killBackgroundProcesses(pkg);
            ApplicationInfo ai = pi.applicationInfo;
            boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isStopped = (ai.flags & ApplicationInfo.FLAG_STOPPED) != 0;
            boolean isOverlay = pkg.contains(".overlay.");
            if (!isStopped && !isOverlay && !isSystem) {
                runningApps.add(pkg);
            }
        }
        System.gc();

        if (!runningApps.isEmpty()) {
            Toast.makeText(this, "\uD83E\uDDF9 Closing " + runningApps.size() + " background app"
                    + (runningApps.size() == 1 ? "..." : "s..."), Toast.LENGTH_SHORT).show();
            if (HomeAccessibilityService.startForceStopAll(this, runningApps, beforeMB, totalMB)) {
                return;
            }
        }

        ActivityManager.MemoryInfo miAfter = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(miAfter);
        long afterMB = miAfter.availMem / (1024L * 1024L);
        long freed = Math.max(0L, afterMB - beforeMB);
        String msg = "\uD83E\uDDF9 0 background apps running \u2022 RAM clean ("
                + afterMB + " / " + totalMB + " MB free" + (freed > 0 ? ", +" + freed + " MB" : "") + ")";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void openWifiSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            try {
                Intent i = new Intent();
                i.setClassName("com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.tv.network.NetworkActivity");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Throwable t2) {
                Toast.makeText(this, "Unable to open Wi-Fi settings", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int getCornerPercent() {
        int pct = prefs.getInt(KEY_CORNER_PCT, 25);
        if (pct < 5) pct = 5;
        if (pct > 50) pct = 50;
        return pct;
    }

    private void showCornerRoundnessDialog() {
        final int[] values = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50};
        final String[] labels = new String[values.length];
        int current = getCornerPercent();
        int checkedIdx = 4; // default 25%
        for (int i = 0; i < values.length; i++) {
            int v = values[i];
            if (v == 5) {
                labels[i] = "5%  (Slight roundness)";
            } else if (v == 25) {
                labels[i] = "25%  (Medium rounded)";
            } else if (v == 50) {
                labels[i] = "50%  (Maximum pill / circle)";
            } else {
                labels[i] = v + "%";
            }
            if (v == current) {
                checkedIdx = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Round App Icon Edges (5% - 50%)")
                .setSingleChoiceItems(labels, checkedIdx, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        int chosen = values[which];
                        prefs.edit().putInt(KEY_CORNER_PCT, chosen).apply();
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                        d.dismiss();
                        Toast.makeText(HomeActivity.this,
                                "App icon edges rounded to " + chosen + "%",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openSystemSettingsDirect() {
        try {
            Intent i = new Intent(Settings.ACTION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Unable to open Settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateGridZoom() {
        if (grid == null) return;
        boolean gridFocused = grid.hasFocus();
        View selected = gridFocused ? grid.getSelectedView() : null;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            boolean isTarget = (child == selected);
            float targetScale = isTarget ? 1.08f : 1.0f;
            float targetZ = isTarget ? dp(14) : 0f;
            child.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .translationZ(targetZ)
                    .setDuration(160)
                    .start();
        }
    }

    private String getCurrentWallpaperName() {
        int cur = prefs.getInt(KEY_WALL, -2);
        if (cur == -2) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            boolean isDay = (hour >= 6 && hour < 18);
            return "Auto Day/Night \u2022 " + (isDay ? "Day" : "Night");
        }
        if (cur == -1) return "Custom Photo";
        return WALLPAPER_NAMES[Math.max(0, cur) % WALLPAPER_NAMES.length];
    }

    private void showWallpaperPickerDialog() {
        final String[] options = new String[WALLPAPER_NAMES.length + 1];
        options[0] = "\uD83C\uDF17  Auto Day / Night Mode (Day: Bright Teal \u2022 Night: Electric Violet)";
        for (int i = 0; i < WALLPAPER_NAMES.length; i++) {
            options[i + 1] = WALLPAPER_NAMES[i];
        }
        int cur = prefs.getInt(KEY_WALL, -2);
        int checked = (cur == -2) ? 0 : (Math.max(0, cur) % WALLPAPER_NAMES.length) + 1;
        new AlertDialog.Builder(this)
                .setTitle("Choose Wallpaper (Auto Day/Night + 15 Dual-Color Themes)")
                .setSingleChoiceItems(options, checked, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) {
                            prefs.edit().putInt(KEY_WALL, -2).apply();
                            applyWallpaper();
                            d.dismiss();
                            Toast.makeText(HomeActivity.this,
                                    "Auto Day / Night Wallpaper Mode enabled",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            int idx = which - 1;
                            prefs.edit().putInt(KEY_WALL, idx).apply();
                            applyWallpaper();
                            d.dismiss();
                            Toast.makeText(HomeActivity.this,
                                    "Wallpaper: " + WALLPAPER_NAMES[idx],
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openMainSettings() {
        final String[] opts = {
                "\uD83C\uDFA8  Choose Dual-Color Wallpaper (" + getCurrentWallpaperName() + ")",
                "\uD83D\uDD32  Round App Icon Edges (" + getCornerPercent() + "%  \u2022  5% to 50%)",
                "\uD83C\uDF19  Start Ambient Screensaver Now (Auto: 5 min idle)",
                "\u2630  More Fire Home Options (Hidden Apps, Weather)",
                "\u2699\uFE0F  Fire TV System Settings"
        };
        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setItems(opts, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) {
                            showWallpaperPickerDialog();
                        } else if (which == 1) {
                            showCornerRoundnessDialog();
                        } else if (which == 2) {
                            startScreensaver();
                        } else if (which == 3) {
                            showMainMenu();
                        } else if (which == 4) {
                            openSystemSettingsDirect();
                        }
                    }
                })
                .show();
    }

    private void applyWallpaper() {
        int mode = prefs.getInt(KEY_WALL, -2);
        File dir = getExternalFilesDir(null);
        File custom = dir == null ? null : new File(dir, "wallpaper.jpg");
        if (custom != null && custom.exists() && mode == -1) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 1;
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap b = BitmapFactory.decodeFile(custom.getAbsolutePath(), o);
                if (b != null) {
                    wallpaper.setImageBitmap(b);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        int idx;
        if (mode == -2) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            // Daytime (6 AM - 6 PM): Emerald Mint & Deep Teal (0); Nighttime: Electric Violet & Deep Plum (4)
            idx = (hour >= 6 && hour < 18) ? 0 : 4;
        } else {
            idx = Math.max(0, mode) % GRADIENTS.length;
        }
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, GRADIENTS[idx]);
        wallpaper.setImageDrawable(g);
    }

    private void showMainMenu() {
        final String[] opts = {
                "Choose Dual-Color Wallpaper (" + getCurrentWallpaperName() + ")",
                "Next Dual-Color Wallpaper",
                "Round App Icon Edges (" + getCornerPercent() + "%  \u2022  5% to 50%)",
                "Start Ambient Screensaver Now (Auto: 5 min idle)",
                "Use custom wallpaper (wallpaper.jpg)",
                "Show hidden apps",
                "Refresh weather",
                "Fire TV System Settings"
        };
        new AlertDialog.Builder(this).setTitle("Fire Home Options").setItems(opts, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                switch (which) {
                    case 0:
                        showWallpaperPickerDialog();
                        break;
                    case 1:
                        int cur = prefs.getInt(KEY_WALL, 0);
                        int next = cur < 0 ? 0 : (cur + 1) % GRADIENTS.length;
                        prefs.edit().putInt(KEY_WALL, next).apply();
                        applyWallpaper();
                        Toast.makeText(HomeActivity.this,
                                "Wallpaper: " + WALLPAPER_NAMES[next],
                                Toast.LENGTH_SHORT).show();
                        break;
                    case 2:
                        showCornerRoundnessDialog();
                        break;
                    case 3:
                        startScreensaver();
                        break;
                    case 4:
                        File dir = getExternalFilesDir(null);
                        File f = dir == null ? null : new File(dir, "wallpaper.jpg");
                        if (f != null && f.exists()) {
                            prefs.edit().putInt(KEY_WALL, -1).apply();
                            applyWallpaper();
                        } else {
                            Toast.makeText(HomeActivity.this,
                                    "Copy a JPG to: " + (dir == null ? "app files" : dir.getAbsolutePath()) + "/wallpaper.jpg",
                                    Toast.LENGTH_LONG).show();
                        }
                        break;
                    case 5:
                        showHiddenApps();
                        break;
                    case 6:
                        fetchWeather();
                        break;
                    case 7:
                        openSystemSettingsDirect();
                        break;
                }
            }
        }).show();
    }

    private void showItemMenu(final int pos) {
        final AppItem it = items.get(pos);
        final String[] opts = {"Move left", "Move right", "Hide", "Cancel"};
        new AlertDialog.Builder(this).setTitle(it.label).setItems(opts, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                if (which == 0 && pos > 0) {
                    Collections.swap(items, pos, pos - 1);
                    saveOrder();
                    selectedAfterMove = pos - 1;
                    adapter.notifyDataSetChanged();
                    restoreSelection();
                } else if (which == 1 && pos < items.size() - 1) {
                    Collections.swap(items, pos, pos + 1);
                    saveOrder();
                    selectedAfterMove = pos + 1;
                    adapter.notifyDataSetChanged();
                    restoreSelection();
                } else if (which == 2) {
                    Set<String> hidden = new HashSet<>(prefs.getStringSet(KEY_HIDDEN, new HashSet<String>()));
                    hidden.add(it.id);
                    prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply();
                    loadApps();
                }
            }
        }).show();
    }

    private void restoreSelection() {
        final int p = selectedAfterMove;
        grid.post(new Runnable() {
            @Override
            public void run() {
                grid.requestFocus();
                grid.setSelection(p);
            }
        });
    }

    private void showHiddenApps() {
        final Set<String> hidden = new HashSet<>(prefs.getStringSet(KEY_HIDDEN, new HashSet<String>()));
        if (hidden.isEmpty()) {
            Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<AppItem> all = queryAll();
        final List<AppItem> hiddenItems = new ArrayList<>();
        for (AppItem a : all) {
            if (hidden.contains(a.id)) hiddenItems.add(a);
        }
        final String[] names = new String[hiddenItems.size()];
        final boolean[] checked = new boolean[hiddenItems.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = hiddenItems.get(i).label;
            checked[i] = true;
        }
        new AlertDialog.Builder(this).setTitle("Hidden apps (uncheck to unhide)")
                .setMultiChoiceItems(names, checked, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton("Apply", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        for (int i = 0; i < checked.length; i++) {
                            if (!checked[i]) hidden.remove(hiddenItems.get(i).id);
                        }
                        prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply();
                        loadApps();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------------------------------------------------------- apps

    private List<AppItem> queryAll() {
        PackageManager pm = getPackageManager();
        Map<String, AppItem> map = new HashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || "com.netflix.ninja".equals(pkg) || map.containsKey(pkg)) continue;
                AppItem a = new AppItem();
                a.id = pkg;
                a.label = String.valueOf(ri.loadLabel(pm));
                Drawable banner = null;
                try {
                    banner = ri.activityInfo.loadBanner(pm);
                    if (banner == null) banner = pm.getApplicationBanner(pkg);
                } catch (Throwable ignored) {
                }
                if (banner != null && banner.getIntrinsicWidth() > 0 && banner.getIntrinsicHeight() > 0) {
                    // Only treat as wide banner if noticeably wider than tall
                    if (banner.getIntrinsicWidth() >= banner.getIntrinsicHeight() * 1.25f) {
                        a.banner = banner;
                    }
                }
                a.icon = ri.loadIcon(pm);
                a.cardColors = extractCardGradientFromIcon(a.icon, a.id);
                Intent l = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                l.setClassName(pkg, ri.activityInfo.name);
                l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                a.launch = l;
                map.put(pkg, a);
            }
        }
        List<AppItem> list = new ArrayList<>(map.values());
        Collections.sort(list, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem x, AppItem y) {
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        return list;
    }

    private int[] extractCardGradientFromIcon(Drawable d, String fallbackId) {
        int fallbackAccent = BANNER_ACCENTS[Math.abs(fallbackId.hashCode()) % BANNER_ACCENTS.length];
        if (d == null) {
            return new int[]{fallbackAccent, 0xFF141820};
        }
        try {
            int size = 32;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            d.setBounds(0, 0, size, size);
            d.draw(canvas);

            float sumR = 0f, sumG = 0f, sumB = 0f, totalWeight = 0f;
            float[] hsv = new float[3];
            for (int y = 2; y < size - 2; y++) {
                for (int x = 2; x < size - 2; x++) {
                    int px = bmp.getPixel(x, y);
                    int alpha = (px >>> 24) & 0xFF;
                    if (alpha < 140) continue;
                    Color.colorToHSV(px, hsv);
                    float sat = hsv[1];
                    float val = hsv[2];
                    // Favor rich, saturated brand colors over pure white or pure black backgrounds
                    if (sat >= 0.22f && val >= 0.18f) {
                        float weight = sat * sat * (0.4f + val);
                        sumR += ((px >> 16) & 0xFF) * weight;
                        sumG += ((px >> 8) & 0xFF) * weight;
                        sumB += (px & 0xFF) * weight;
                        totalWeight += weight;
                    }
                }
            }
            bmp.recycle();

            if (totalWeight > 0.05f) {
                int avgR = Math.round(sumR / totalWeight);
                int avgG = Math.round(sumG / totalWeight);
                int avgB = Math.round(sumB / totalWeight);
                Color.RGBToHSV(avgR, avgG, avgB, hsv);
                float hue = hsv[0];
                float sat = Math.min(0.86f, Math.max(0.55f, hsv[1]));
                int topColor = Color.HSVToColor(new float[]{hue, sat, 0.56f});
                int bottomColor = Color.HSVToColor(new float[]{hue, Math.min(0.92f, sat + 0.08f), 0.20f});
                return new int[]{topColor, bottomColor};
            }
        } catch (Throwable ignored) {
        }
        return new int[]{fallbackAccent, 0xFF141820};
    }

    private boolean initialFocusSet = false;

    private void loadApps() {
        Set<String> hidden = prefs.getStringSet(KEY_HIDDEN, null);
        if (hidden == null) {
            hidden = new HashSet<>();
            hidden.add("com.amazon.tv.launcher");
            hidden.add("com.amazon.ftv.profilepicker");
            prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply();
        }
        List<AppItem> all = queryAll();
        List<AppItem> visible = new ArrayList<>();
        for (AppItem a : all) {
            if (!hidden.contains(a.id)) visible.add(a);
        }
        String saved = prefs.getString(KEY_ORDER, "");
        if (saved.isEmpty()) {
            saved = "org.smarttube.stable,com.amazon.firetv.youtube,com.jio.media.jiotvplus,"
                    + "app.netmirror.newtv,com.cricfytv.sports,com.playfy.tv,"
                    + "org.videolan.vlc,com.mxtech.videoplayer.television,cm.aptoide.pt,"
                    + "com.esaba.downloader,com.yablio.sendfilestotv,io.github.visnkmr.bapl,"
                    + "com.wolf.minilm,com.spocky.projengmenu";
            prefs.edit().putString(KEY_ORDER, saved).apply();
        }
        final List<String> order = new ArrayList<>();
        Collections.addAll(order, saved.split(","));
        Collections.sort(visible, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem x, AppItem y) {
                int ix = order.indexOf(x.id), iy = order.indexOf(y.id);
                if (ix < 0 && iy < 0) return x.label.compareToIgnoreCase(y.label);
                if (ix < 0) return 1;
                if (iy < 0) return -1;
                return ix - iy;
            }
        });
        items.clear();
        items.addAll(visible);
        adapter.notifyDataSetChanged();
        if (!initialFocusSet && grid != null) {
            initialFocusSet = true;
            grid.post(new Runnable() {
                @Override
                public void run() {
                    grid.requestFocus();
                    grid.setSelection(0);
                    updateGridZoom();
                }
            });
        } else if (grid != null) {
            grid.post(new Runnable() {
                @Override
                public void run() {
                    updateGridZoom();
                }
            });
        }
    }

    private void saveOrder() {
        StringBuilder sb = new StringBuilder();
        for (AppItem a : items) {
            if (sb.length() > 0) sb.append(',');
            sb.append(a.id);
        }
        prefs.edit().putString(KEY_ORDER, sb.toString()).apply();
    }

    private void launch(AppItem a) {
        try {
            startActivity(a.launch);
        } catch (Throwable t) {
            Toast.makeText(this, "Can't open " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- weather

    private void fetchWeather() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    double lat = Double.parseDouble(prefs.getString(KEY_LAT, "12.2958"));
                    double lon = Double.parseDouble(prefs.getString(KEY_LON, "76.6394"));
                    String city = prefs.getString(KEY_CITY, "Mysore");
                    if (!"Mysore".equals(city)) {
                        lat = 12.2958;
                        lon = 76.6394;
                        city = "Mysore";
                        prefs.edit().putString(KEY_LAT, "12.2958")
                                .putString(KEY_LON, "76.6394")
                                .putString(KEY_CITY, "Mysore").apply();
                    }
                    String url = String.format(Locale.US,
                            "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&current=temperature_2m,weather_code",
                            lat, lon);
                    JSONObject cur = new JSONObject(httpGet(url)).getJSONObject("current");
                    final String text = String.format(Locale.getDefault(), "%s  %d\u00B0C  %s",
                            icon(cur.getInt("weather_code")), Math.round(cur.getDouble("temperature_2m")), city);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            weatherView.setText(text);
                            if (screensaverWeatherView != null) {
                                screensaverWeatherView.setText(text);
                            }
                        }
                    });
                } catch (Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (weatherView.getText().length() == 0) weatherView.setText("");
                        }
                    });
                }
            }
        }).start();
    }

    private static String icon(int code) {
        if (code == 0) return "\u2600\uFE0F";
        if (code <= 3) return "\u26C5";
        if (code <= 48) return "\uD83C\uDF2B\uFE0F";
        if (code <= 67 || (code >= 80 && code <= 82)) return "\uD83C\uDF27\uFE0F";
        if (code <= 77) return "\u2744\uFE0F";
        return "\u26C8\uFE0F";
    }

    private static String httpGet(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "FireHome/1.1");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    // ---------------------------------------------------------------- adapter

    private class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int i) {
            return items.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout rootTile;
            FrameLayout cardBox;
            ImageView fullBannerView;
            LinearLayout fallbackWideBox;
            ImageView smallIconView;
            TextView insideTitleView;
            TextView bottomLabelView;

            if (convert == null) {
                rootTile = new LinearLayout(HomeActivity.this);
                rootTile.setOrientation(LinearLayout.VERTICAL);
                rootTile.setGravity(Gravity.CENTER_HORIZONTAL);
                rootTile.setPadding(dp(4), dp(4), dp(4), dp(4));
                rootTile.setLayoutParams(new AbsListViewLayout(dp(122)));

                // Wide 16:9 rectangular card container
                cardBox = new FrameLayout(HomeActivity.this);
                cardBox.setId(1000);
                cardBox.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        int w = view.getWidth();
                        int h = view.getHeight();
                        if (w > 0 && h > 0) {
                            float r = h * (getCornerPercent() / 100f);
                            outline.setRoundRect(0, 0, w, h, r);
                        }
                    }
                });
                cardBox.setClipToOutline(true);
                rootTile.addView(cardBox, new LinearLayout.LayoutParams(-1, dp(86)));

                // Mode A: Full 16:9 TV Banner image
                fullBannerView = new ImageView(HomeActivity.this);
                fullBannerView.setId(1001);
                fullBannerView.setScaleType(ImageView.ScaleType.FIT_XY);
                fullBannerView.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        int w = view.getWidth();
                        int h = view.getHeight();
                        if (w > 0 && h > 0) {
                            float r = h * (getCornerPercent() / 100f);
                            outline.setRoundRect(0, 0, w, h, r);
                        }
                    }
                });
                fullBannerView.setClipToOutline(true);
                cardBox.addView(fullBannerView, new FrameLayout.LayoutParams(-1, -1));

                // Mode B: Wide horizontal banner card for apps that only supply a square icon
                fallbackWideBox = new LinearLayout(HomeActivity.this);
                fallbackWideBox.setId(1002);
                fallbackWideBox.setOrientation(LinearLayout.HORIZONTAL);
                fallbackWideBox.setGravity(Gravity.CENTER_VERTICAL);
                fallbackWideBox.setPadding(dp(14), dp(10), dp(14), dp(10));
                cardBox.addView(fallbackWideBox, new FrameLayout.LayoutParams(-1, -1));

                smallIconView = new ImageView(HomeActivity.this);
                smallIconView.setId(1003);
                smallIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                smallIconView.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        int w = view.getWidth();
                        int h = view.getHeight();
                        if (w > 0 && h > 0) {
                            float r = h * (getCornerPercent() / 100f);
                            outline.setRoundRect(0, 0, w, h, r);
                        }
                    }
                });
                smallIconView.setClipToOutline(true);
                fallbackWideBox.addView(smallIconView, new LinearLayout.LayoutParams(dp(48), dp(48)));

                insideTitleView = new TextView(HomeActivity.this);
                insideTitleView.setId(1004);
                insideTitleView.setTextColor(Color.WHITE);
                insideTitleView.setTextSize(14);
                insideTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                insideTitleView.setMaxLines(2);
                insideTitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
                insideTitleView.setPadding(dp(10), 0, 0, 0);
                fallbackWideBox.addView(insideTitleView, new LinearLayout.LayoutParams(0, -2, 1f));

                // App name caption under the wide rectangle
                bottomLabelView = new TextView(HomeActivity.this);
                bottomLabelView.setId(1005);
                bottomLabelView.setTextColor(0xEEFFFFFF);
                bottomLabelView.setTextSize(13);
                bottomLabelView.setSingleLine(true);
                bottomLabelView.setEllipsize(android.text.TextUtils.TruncateAt.END);
                bottomLabelView.setGravity(Gravity.CENTER);
                bottomLabelView.setPadding(dp(4), dp(4), dp(4), 0);
                rootTile.addView(bottomLabelView, new LinearLayout.LayoutParams(-1, -2));
            } else {
                rootTile = (LinearLayout) convert;
                cardBox = rootTile.findViewById(1000);
                fullBannerView = rootTile.findViewById(1001);
                fallbackWideBox = rootTile.findViewById(1002);
                smallIconView = rootTile.findViewById(1003);
                insideTitleView = rootTile.findViewById(1004);
                bottomLabelView = rootTile.findViewById(1005);
            }

            float pct = getCornerPercent() / 100f;
            float cardRadius = dp(86) * pct;
            float outerRadius = Math.min(dp(36), cardRadius + dp(4));

            StateListDrawable outerBg = new StateListDrawable();
            GradientDrawable focused = new GradientDrawable();
            focused.setColor(0x33FFFFFF);
            focused.setCornerRadius(outerRadius);
            focused.setStroke(dp(3), Color.WHITE);
            GradientDrawable normal = new GradientDrawable();
            normal.setColor(Color.TRANSPARENT);
            normal.setCornerRadius(outerRadius);
            outerBg.addState(new int[]{android.R.attr.state_focused}, focused);
            outerBg.addState(new int[]{android.R.attr.state_selected}, focused);
            outerBg.addState(new int[]{}, normal);
            rootTile.setBackground(outerBg);

            AppItem a = items.get(pos);
            GradientDrawable cardBg = new GradientDrawable();
            if (a.cardColors != null && a.cardColors.length == 2) {
                cardBg.setColors(a.cardColors);
            } else {
                int accent = BANNER_ACCENTS[Math.abs(a.id.hashCode()) % BANNER_ACCENTS.length];
                cardBg.setColors(new int[]{accent, 0xFF141820});
            }
            cardBg.setOrientation(GradientDrawable.Orientation.TL_BR);
            cardBg.setCornerRadius(cardRadius);
            cardBg.setStroke(dp(1), 0x44FFFFFF);
            cardBox.setBackground(cardBg);
            cardBox.invalidateOutline();
            fullBannerView.invalidateOutline();
            smallIconView.invalidateOutline();

            boolean isSelectedTile = (grid != null && grid.hasFocus() && grid.getSelectedItemPosition() == pos);
            rootTile.setScaleX(isSelectedTile ? 1.08f : 1.0f);
            rootTile.setScaleY(isSelectedTile ? 1.08f : 1.0f);
            rootTile.setTranslationZ(isSelectedTile ? dp(14) : 0f);

            if (a.banner != null) {
                fullBannerView.setVisibility(View.VISIBLE);
                fullBannerView.setImageDrawable(a.banner);
                fallbackWideBox.setVisibility(View.GONE);
            } else {
                fullBannerView.setVisibility(View.GONE);
                fallbackWideBox.setVisibility(View.VISIBLE);
                smallIconView.setImageDrawable(a.icon);
                insideTitleView.setText(a.label);
            }
            bottomLabelView.setText(a.label);
            return rootTile;
        }
    }

    private static class AbsListViewLayout extends android.widget.AbsListView.LayoutParams {
        AbsListViewLayout(int h) {
            super(ViewGroup.LayoutParams.MATCH_PARENT, h);
        }
    }
}
