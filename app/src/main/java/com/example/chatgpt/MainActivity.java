package com.example.chatgpt;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

public class MainActivity extends Activity implements WebViewManager.WebViewHostCallback {

    private WebView[] webViews = new WebView[5];
    private int currentTabIndex = 0;
    
    // Instance variables replace statics to prevent Tab conflict
    private ValueCallback<Uri[]> mFileCallback;
    private Uri mCameraUri;
    private FrameLayout container;
    private ProgressBar progressBar;
    private ProfileStore profileStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab);

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            throw new RuntimeException("Multi-profile not supported!");
        }
        
        container = findViewById(R.id.webview_container);
        progressBar = findViewById(R.id.progressBar);
        profileStore = ProfileStore.getInstance();

        initSharedTabs();
        loadTabLazily(0);
        highlightCurrentTab(1);
    }
    
    private void loadTabLazily(int index) {
        // If the WebView already exists, do nothing (it's already loaded)
        if (webViews[index] != null) return;

        // Otherwise, create it for the very first time!
        webViews[index] = new WebView(this);
        
        Profile profile = profileStore.getOrCreateProfile("Account_" + index);
        WebViewCompat.setProfile(webViews[index], profile.getName());

        WebViewManager.setupWebView(
                this,
                webViews[index],
                "https://chatgpt.com",
                progressBar,
                profile.getCookieManager(),
                this
        );

        container.addView(webViews[index]);
    }

    private void initSharedTabs() {
        findViewById(R.id.tab1).setOnClickListener(v -> switchTab(0));
        findViewById(R.id.tab2).setOnClickListener(v -> switchTab(1));
        findViewById(R.id.tab3).setOnClickListener(v -> switchTab(2));
        findViewById(R.id.tab4).setOnClickListener(v -> switchTab(3));
        findViewById(R.id.tab5).setOnClickListener(v -> switchTab(4));
    }

    private void switchTab(int newIndex) {
        if (newIndex == currentTabIndex && webViews[currentTabIndex] != null) return;

        // 1. Pause rendering on the currently active tab
        if (webViews[currentTabIndex] != null) {
            webViews[currentTabIndex].onPause();
            webViews[currentTabIndex].setVisibility(View.GONE);
        }

        // 2. LAZY LOAD the new tab if it hasn't been opened yet
        loadTabLazily(newIndex);

        // 3. Resume rendering on the new tab
        webViews[newIndex].onResume();
        webViews[newIndex].setVisibility(View.VISIBLE);
        
        currentTabIndex = newIndex;
        highlightCurrentTab(currentTabIndex + 1);
    }

    protected void highlightCurrentTab(int index) {
        View[] tabs = {
            findViewById(R.id.tab1), findViewById(R.id.tab2),
            findViewById(R.id.tab3), findViewById(R.id.tab4), findViewById(R.id.tab5)
        };
        for (View tab : tabs) {
            if (tab != null) tab.setSelected(false);
        }
        if (index >= 1 && index <= 5 && tabs[index - 1] != null) {
            tabs[index - 1].setSelected(true);
        }
    }

    // --- DELEGATED CALLBACKS FROM WEBVIEWMANAGER ---

    @Override
    public void onShowFileChooser(Intent intent, ValueCallback<Uri[]> callback, Uri cameraUri) {
        if (mFileCallback != null) mFileCallback.onReceiveValue(null);
        mFileCallback = callback;
        mCameraUri = cameraUri;
        startActivityForResult(intent, WebViewManager.FILE_PICKER_REQUEST_CODE);
    }

    @Override
    public void onRequestMicPermission(PermissionRequest request) {
        runOnUiThread(() -> {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, WebViewManager.MIC_PERMISSION_REQUEST_CODE);
                // User will have to click Mic icon again after granting, matching your old flow
                return; 
            }
            request.grant(request.getResources());
        });
    }

    // --- LIFECYCLE & ACTIVITY RESULTS ---

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != WebViewManager.FILE_PICKER_REQUEST_CODE || mFileCallback == null) return;
        
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            if (data == null && mCameraUri != null) {
                results = new Uri[]{mCameraUri};    
            } else if (data != null) {
                if (data.getClipData() != null) {   
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) { 
                    results = new Uri[]{data.getData()};
                }
            }
        }
        mFileCallback.onReceiveValue(results);
        mFileCallback = null;
        mCameraUri = null;
    }

    @Override
    public void onBackPressed() {
        WebViewManager.handleOnBackPressed(this, webViews[currentTabIndex]);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webViews[currentTabIndex].onPause();
        CookieManager.getInstance().flush(); // Forces ALL profiles to save to disk
    }

    @Override
    protected void onResume() {
        super.onResume();
        webViews[currentTabIndex].onResume();
    }
}
