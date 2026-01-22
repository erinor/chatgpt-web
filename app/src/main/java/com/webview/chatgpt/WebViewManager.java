package com.webview.chatgpt;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;
import android.widget.Toast;

public class WebViewManager {

    public static final int FILE_PICKER_REQUEST_CODE = 1001;
    private static final int MIC_PERMISSION_REQUEST_CODE = 2002;
    private static ValueCallback<Uri[]> fileCallback;
    private static Uri cameraUri;

    public static void setupWebView(
            Activity activity, WebView webView, String url, ProgressBar progressBar) {

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setForceDark(WebSettings.FORCE_DARK_AUTO);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(
                new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView webView, String url) {
                        super.onPageFinished(webView, url);

                        if (url.contains("login") || url.contains("auth") || url.contains("sign") || url.contains("password")) {
                            CookieManager.getInstance().flush();
                        }
                    }
                });

        webView.setWebChromeClient(
                new WebChromeClient() {
                    @Override
                    public void onProgressChanged(WebView view, int progress) {
                        if (progress < 100) {
                            progressBar.setVisibility(View.VISIBLE);
                            progressBar.setProgress(progress);
                        } else {
                            progressBar.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public boolean onShowFileChooser(
                            WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                        if (fileCallback != null) fileCallback.onReceiveValue(null);
                        fileCallback = callback;
                        boolean wantsImage = false;

                        for (String type : params.getAcceptTypes()) {
                            if (type != null && type.startsWith("image")) {
                                wantsImage = true;
                                break;
                            }
                        }
                        if (wantsImage && params.isCaptureEnabled()) {
                            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                            cameraUri = createImageUri(activity);
                            if (cameraUri != null) {
                                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                                activity.startActivityForResult(
                                        cameraIntent, FILE_PICKER_REQUEST_CODE);
                                return true;
                            }
                        }
                        Intent intent = params.createIntent();
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        activity.startActivityForResult(intent, FILE_PICKER_REQUEST_CODE);
                        return true;
                    }

                    @Override
                    public void onPermissionRequest(final PermissionRequest request) {
                        activity.runOnUiThread(() -> {
                            if (activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
                                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                activity.requestPermissions(
                                    new String[] {
                                        android.Manifest.permission.RECORD_AUDIO
                                    },
                                    MIC_PERMISSION_REQUEST_CODE
                                );
                                return;
                            }
                            request.grant(request.getResources());
                        });
                    }
                });

        setupDownloadManager(activity, webView);
        webView.loadUrl(url);
    }

    private static void setupDownloadManager(Activity activity, WebView webView) {
        webView.setDownloadListener(
                (url, userAgent, contentDisposition, mimeType, contentLength) -> {
                    String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    request.setDescription("Downloading file...");

                    String cookie = CookieManager.getInstance().getCookie(url);
                    if (cookie != null) {
                        request.addRequestHeader("Cookie", cookie);
                    }
                    DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(request);
                        Toast.makeText(activity, "Downloading: " + fileName, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, "Download manager not available", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public static void handleFileResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILE_PICKER_REQUEST_CODE || fileCallback == null) return;
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            if (data == null && cameraUri != null) {
                results = new Uri[] {cameraUri};
            } else if (data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[] {data.getData()};
                }
            }
        }
        fileCallback.onReceiveValue(results);
        fileCallback = null;
        cameraUri = null;
    }

    private static Uri createImageUri(Context context) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "photo");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        return context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    public static void ensureMicPermission(Activity activity) {
        if (activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(
                    new String[] {android.Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_REQUEST_CODE);
        }
    }

    public static void handleOnBackPressed(Activity activity, WebView webView) {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            new AlertDialog.Builder(activity)
                    .setMessage("Do you want to exit the app?")
                    .setPositiveButton("Yes", (dialog, which) -> activity.finishAffinity())
                    .setNegativeButton("No", null)
                    .show();
        }
    }
    
    public static void onResume(WebView webView) {
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    public static void onPause(WebView webView) {
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
    }

    public static void destroyWebView(WebView webView) {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.clearCache(true);
            webView.removeAllViews();
            webView.destroy();
        }
    }
}
