package com.liuyongliang.kindlesettingsshortcut;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception first) {
            try {
                Intent fallback = new Intent(Intent.ACTION_MAIN);
                fallback.setClassName("com.android.settings", "com.android.settings.Settings");
                startActivity(fallback);
            } catch (Exception ignored) { }
        }
        finish();
    }
}
