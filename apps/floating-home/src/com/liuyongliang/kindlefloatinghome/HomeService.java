package com.liuyongliang.kindlefloatinghome;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class HomeService extends Service {
    private static final String POLICY_CONTROL = "policy_control";
    private static final String IMMERSIVE_POLICY = "immersive.full=*";
    private WindowManager manager;
    private WindowManager.LayoutParams params;
    private FrameLayout button;
    private float downX, downY;
    private int originalX, originalY;
    private boolean moved;
    private int touchSlop;
    private Handler mainHandler;
    private ContentObserver policyObserver;
    private final Runnable enforcePolicy = new Runnable() {
        @Override public void run() { enforceImmersivePolicy(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        policyObserver = new ContentObserver(mainHandler) {
            @Override public void onChange(boolean selfChange, Uri uri) {
                // SystemUI rewrites this setting while starting. Coalesce change
                // notifications, then check the value before writing it back.
                mainHandler.removeCallbacks(enforcePolicy);
                mainHandler.postDelayed(enforcePolicy, 200);
            }
        };
        getContentResolver().registerContentObserver(
            Settings.Global.getUriFor(POLICY_CONTROL), false, policyObserver);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (button == null) showButton();
        enforceImmersivePolicy();
        mainHandler.removeCallbacks(enforcePolicy);
        mainHandler.postDelayed(enforcePolicy, 2000);
        return START_STICKY;
    }

    private void enforceImmersivePolicy() {
        try {
            String current = Settings.Global.getString(getContentResolver(), POLICY_CONTROL);
            if (IMMERSIVE_POLICY.equals(current)) return;
            if (!Settings.Global.putString(getContentResolver(), POLICY_CONTROL, IMMERSIVE_POLICY)) {
                Log.w("KindleFloatingHome", "Could not save immersive policy");
            }
        } catch (SecurityException error) {
            Log.e("KindleFloatingHome", "WRITE_SECURE_SETTINGS is required", error);
        }
    }

    private void showButton() {
        manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        button = new FrameLayout(this);
        button.setContentDescription("点击打开桌面，拖动移动小圆点");
        button.setBackgroundResource(R.drawable.ic_launcher);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        int size = (int)(48 * getResources().getDisplayMetrics().density + 0.5f);
        params = new WindowManager.LayoutParams(size, size,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.LEFT | Gravity.TOP;
        params.x = Math.max(0, Math.min(getResources().getDisplayMetrics().widthPixels - size,
            getSharedPreferences("position", 0).getInt("x", getResources().getDisplayMetrics().widthPixels - size - 2)));
        params.y = Math.max(0, Math.min(getResources().getDisplayMetrics().heightPixels - size,
            getSharedPreferences("position", 0).getInt("y", getResources().getDisplayMetrics().heightPixels - size - 90)));
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getRawX(); downY = event.getRawY(); originalX = params.x; originalY = params.y; moved = false; return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    int deltaX = (int)(event.getRawX() - downX), deltaY = (int)(event.getRawY() - downY);
                    if (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop) moved = true;
                    if (moved) {
                        params.x = Math.max(0, Math.min(getResources().getDisplayMetrics().widthPixels - params.width, originalX + deltaX));
                        params.y = Math.max(0, Math.min(getResources().getDisplayMetrics().heightPixels - params.height, originalY + deltaY));
                        manager.updateViewLayout(button, params);
                    }
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (moved) getSharedPreferences("position", 0).edit().putInt("x", params.x).putInt("y", params.y).apply();
                    else openHome();
                    return true;
                }
                return true;
            }
        });
        manager.addView(button, params);
    }

    private void openHome() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_LAUNCHER);
            home.setComponent(new ComponentName("cn.modificator.launcher", "cn.modificator.launcher.Launcher"));
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        } catch (Exception error) {
            Log.e("KindleFloatingHome", "Cannot open E-Ink launcher", error);
        }
    }

    @Override public void onDestroy() {
        if (policyObserver != null) getContentResolver().unregisterContentObserver(policyObserver);
        if (mainHandler != null) mainHandler.removeCallbacks(enforcePolicy);
        if (button != null) { manager.removeView(button); button = null; }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
