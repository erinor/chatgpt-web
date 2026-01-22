package com.webview.chatgpt;

import android.app.Application;
import android.os.Build;
import android.webkit.WebView;

public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String processName = getProcessName(); // API 28+
            if (!getApplicationContext().getPackageName().equals(processName)) {
                WebView.setDataDirectorySuffix(processName);
            }
        }
    }
}
