package com.prasad.firehome;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 1) Intercepts the Fire TV Home button and stock Amazon Launcher window state
 *    so pressing Home always opens Fire Home.
 * 2) When the user clicks "Clean RAM" in Fire Home:
 *    - Opens each running background app's Settings page with "Force stop" automatically highlighted.
 *    - As soon as the user presses OK (setting FLAG_STOPPED), automatically closes the page
 *      and advances to the next running app (or returns straight to Fire Home when done).
 */
public class HomeAccessibilityService extends AccessibilityService {

    private static HomeAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> stopQueue = new ArrayList<>();
    private String currentStoppingPkg = null;
    private long pkgStartMs = 0;
    private boolean focusedCurrentPkg = false;
    private int totalToStop = 0;
    private int stepIndex = 0;
    private int actuallyStoppedCount = 0;
    private long beforeMemMB = 0;
    private long totalMemMB = 0;
    private long lastNetMirrorLaunchMs = 0;

    private final Runnable delayedLaunch = new Runnable() {
        @Override
        public void run() {
            if (currentStoppingPkg == null && stopQueue.isEmpty()) {
                startFireHome();
            }
        }
    };

    private final Runnable pollForceStop = new Runnable() {
        @Override
        public void run() {
            checkAndFocusForceStop();
        }
    };

    private final Runnable stepTimeout = new Runnable() {
        @Override
        public void run() {
            // If user didn't press OK within 8 seconds, close and return to Fire Home
            stopQueue.clear();
            currentStoppingPkg = null;
            performGlobalAction(GLOBAL_ACTION_BACK);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startFireHome();
                }
            }, 250);
        }
    };

    public static boolean startForceStopAll(Context ctx, List<String> runningPkgs, long beforeMB, long totalMB) {
        if (instance == null) return false;
        instance.beginForceStop(runningPkgs, beforeMB, totalMB);
        return true;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        startFireHome();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (instance == this) instance = null;
        return super.onUnbind(intent);
    }

    private void beginForceStop(List<String> pkgs, long beforeMB, long totalMB) {
        handler.removeCallbacks(stepTimeout);
        handler.removeCallbacks(pollForceStop);
        stopQueue.clear();
        stopQueue.addAll(pkgs);
        totalToStop = pkgs.size();
        stepIndex = 0;
        actuallyStoppedCount = 0;
        beforeMemMB = beforeMB;
        totalMemMB = totalMB;
        currentStoppingPkg = null;
        openNextInQueue(false);
    }

    private void openNextInQueue(boolean needBackFirst) {
        handler.removeCallbacks(stepTimeout);
        handler.removeCallbacks(pollForceStop);
        if (needBackFirst) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    launchNextPkg();
                }
            }, 320);
        } else {
            launchNextPkg();
        }
    }

    private void launchNextPkg() {
        while (!stopQueue.isEmpty()) {
            String next = stopQueue.remove(0);
            stepIndex++;
            if (!isPackageStopped(next)) {
                currentStoppingPkg = next;
                focusedCurrentPkg = false;
                pkgStartMs = System.currentTimeMillis();
                try {
                    String label = getAppLabel(next);
                    Toast.makeText(this,
                            "\uD83D\uDC49 Press OK to Force Stop " + label + " (" + stepIndex + "/" + totalToStop + ")",
                            Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.parse("package:" + next));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(i);
                    handler.postDelayed(pollForceStop, 450);
                    handler.postDelayed(stepTimeout, 8000);
                    return;
                } catch (Throwable ignored) {
                }
            } else {
                actuallyStoppedCount++;
            }
        }
        // Finished all queued packages
        currentStoppingPkg = null;
        startFireHome();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                long afterMB = beforeMemMB;
                if (am != null) {
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    afterMB = mi.availMem / (1024L * 1024L);
                }
                long freed = Math.max(0L, afterMB - beforeMemMB);
                String msg = "\uD83E\uDDF9 All " + actuallyStoppedCount + " background app"
                        + (actuallyStoppedCount == 1 ? "" : "s") + " Force-Stopped!"
                        + (freed > 0 ? " \u2022 +" + freed + " MB" : "")
                        + " (" + afterMB + " / " + totalMemMB + " MB free)";
                Toast.makeText(HomeAccessibilityService.this, msg, Toast.LENGTH_LONG).show();
            }
        }, 350);
    }

    private String getAppLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return String.valueOf(pm.getApplicationLabel(ai));
        } catch (Throwable t) {
            return pkg;
        }
    }

    private boolean isPackageStopped(String pkg) {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            return (ai.flags & ApplicationInfo.FLAG_STOPPED) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    private void checkAndFocusForceStop() {
        if (currentStoppingPkg == null) return;
        if (isPackageStopped(currentStoppingPkg)) {
            actuallyStoppedCount++;
            openNextInQueue(true);
            return;
        }
        if (System.currentTimeMillis() - pkgStartMs >= 420) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null
                    && "com.amazon.tv.settings.v2".equals(root.getPackageName().toString())) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Force stop");
                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo n : nodes) {
                        AccessibilityNodeInfo p = n;
                        while (p != null && !p.isClickable()) {
                            p = p.getParent();
                        }
                        if (p != null && p.isEnabled() && !p.isFocused()) {
                            p.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                            p.performAction(AccessibilityNodeInfo.ACTION_SELECT);
                            focusedCurrentPkg = true;
                            break;
                        }
                    }
                }
            }
        }
        handler.removeCallbacks(pollForceStop);
        handler.postDelayed(pollForceStop, 180);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();

        if (currentStoppingPkg != null) {
            if (isPackageStopped(currentStoppingPkg)) {
                actuallyStoppedCount++;
                openNextInQueue(true);
            }
            return;
        }

        // Intercept Netflix remote button fallback (Amazon Appstore AppLaunchActivity or com.netflix.ninja) -> open NetMirror
        if (pkg != null) {
            String p = pkg.toString();
            CharSequence cls = event.getClassName();
            String c = cls == null ? "" : cls.toString();
            // Netflix button -> Amazon Appstore opens "AppLaunchActivity" then the Netflix "AppDetailsActivity"
            // (download page). Close that page and open NetMirror instead.
            if ("com.netflix.ninja".equals(p)
                    || ("com.amazon.venezia".equals(p)
                        && (c.contains("AppLaunchActivity") || c.contains("AppDetailsActivity")))) {
                long now = System.currentTimeMillis();
                if (now - lastNetMirrorLaunchMs > 1500) {
                    lastNetMirrorLaunchMs = now;
                    launchNetMirror();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            launchNetMirror();
                        }
                    }, 450);
                }
                return;
            }
        }

        // Normal Home interception when not force-stopping
        if (pkg != null && "com.amazon.tv.launcher".equals(pkg.toString())) {
            CharSequence cls = event.getClassName();
            String c = cls == null ? "" : cls.toString();
            if (c.contains("Settings")) {
                return;
            }
            startFireHome();
            handler.removeCallbacks(delayedLaunch);
            handler.postDelayed(delayedLaunch, 120);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();
        // Netflix button on Fire TV remote: KEYCODE_APP_1 (288) or scanCode 249 / 744
        if (keyCode == 288 || scanCode == 249 || scanCode == 744) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                launchNetMirror();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                stopQueue.clear();
                currentStoppingPkg = null;
                handler.removeCallbacks(stepTimeout);
                handler.removeCallbacks(pollForceStop);
                startFireHome();
                handler.removeCallbacks(delayedLaunch);
                handler.postDelayed(delayedLaunch, 120);
            }
            return true;
        }
        return super.onKeyEvent(event);
    }

    private void launchNetMirror() {
        try {
            PackageManager pm = getPackageManager();
            Intent launch = pm.getLaunchIntentForPackage("app.netmirror.newtv");
            if (launch == null) {
                launch = new Intent(Intent.ACTION_MAIN);
                launch.setClassName("app.netmirror.newtv", "app.netmirror.newtv.MainActivity");
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
        } catch (Throwable ignored) {
        }
    }

    private void startFireHome() {
        Intent i = new Intent(this, HomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(i);
    }

    @Override
    public void onInterrupt() {
    }
}
