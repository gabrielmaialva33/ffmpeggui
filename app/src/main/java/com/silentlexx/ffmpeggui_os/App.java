package com.silentlexx.ffmpeggui_os;

import android.app.Application;
import android.util.Log;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (getResources() == null) {
            Log.e("APP", "app is replacing...kill");
            System.exit(1);
        }
    }
}
