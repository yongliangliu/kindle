package com.liuyongliang.kindlefloatinghome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        startService(new Intent(this, HomeService.class));
        finish();
    }
}
