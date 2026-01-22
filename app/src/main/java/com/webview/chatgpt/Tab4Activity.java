package com.webview.chatgpt;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

public class Tab4Activity extends BaseActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab);

        initSharedTabs();
        highlightCurrentTab(4);

        ProgressBar progressBar = findViewById(R.id.progressBar);
        FrameLayout container = findViewById(R.id.webview_container);

        webView = new WebView(this);
        container.addView(webView);


        WebViewManager.setupWebView(this, webView, "https://chatgpt.com", progressBar);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        highlightCurrentTab(4);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        WebViewManager.handleFileResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        WebViewManager.handleOnBackPressed(this, webView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        WebViewManager.onResume(webView);
    }

    @Override
    protected void onPause() {
        WebViewManager.onPause(webView);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        WebViewManager.destroyWebView(webView);
        webView = null;
        super.onDestroy();
    }
}
