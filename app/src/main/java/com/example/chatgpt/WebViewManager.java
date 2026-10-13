package com.example.chatgpt;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.ViewGroup;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;
import android.widget.Toast;

public class WebViewManager {

    public static final int FILE_PICKER_REQUEST_CODE = 1001;
    public static final int MIC_PERMISSION_REQUEST_CODE = 2002;

    // NEW: Interface to safely pass events back to the single MainActivity
    public interface WebViewHostCallback {
        void onShowFileChooser(Intent intent, ValueCallback<Uri[]> callback, Uri cameraUri);
        void onRequestMicPermission(PermissionRequest request);
    }

    public static void setupWebView(
            Activity activity, 
            WebView webView, 
            String url, 
            ProgressBar progressBar,
            CookieManager isolatedCookieManager, // Pass isolated cookies
            WebViewHostCallback hostCallback     // Pass isolated callbacks
    ) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setForceDark(WebSettings.FORCE_DARK_AUTO);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(true);

        isolatedCookieManager.setAcceptCookie(true);
        isolatedCookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView webView, String url) {
                super.onPageFinished(webView, url);
                if (url.contains("login") || url.contains("auth") || url.contains("sign") || url.contains("password")) {
                    isolatedCookieManager.flush();
                }
                // Kept your excellent Blob interception logic!
                webView.evaluateJavascript("javascript:if(!window.blobStorage){window.blobStorage={};var orig=URL.createObjectURL;URL.createObjectURL=function(b){var u=orig.call(URL,b);window.blobStorage[u]=b;return u;}}", null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
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
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                boolean wantsImage = false;
                for (String type : params.getAcceptTypes()) {
                    if (type != null && type.startsWith("image")) {
                        wantsImage = true;
                        break;
                    }
                }
                
                Uri cameraUri = null;
                Intent intent;

                if (wantsImage && params.isCaptureEnabled()) {
                    intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    cameraUri = createImageUri(activity);
                    if (cameraUri != null) {
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                    }
                } else {
                    intent = params.createIntent();
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }

                // Delegate to Activity to handle safely
                if (hostCallback != null) {
                    hostCallback.onShowFileChooser(intent, callback, cameraUri);
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (hostCallback != null) {
                    hostCallback.onRequestMicPermission(request);
                }
            }
        });

        webView.addJavascriptInterface(new BlobDownloader(activity), "AndroidDownloader");
        setupDownloadManager(activity, webView, isolatedCookieManager);
        webView.loadUrl(url);
    }

    private static void setupDownloadManager(Activity activity, WebView webView, CookieManager cookieManager) {
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url.startsWith("blob:")) {
                Toast.makeText(activity, "Downloading...", Toast.LENGTH_SHORT).show();
                webView.evaluateJavascript("javascript:var b=window.blobStorage['"+url+"'],s=function(x){var r=new FileReader();r.onloadend=function(){AndroidDownloader.processBlobData(r.result,x.type);};r.readAsDataURL(x);};if(b)s(b);else fetch('"+url+"').then(res=>res.blob()).then(s);", null);
                return;
            }

            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            if (contentDisposition != null && contentDisposition.contains("filename*=")) {
                fileName = Uri.decode(contentDisposition.split("filename\\*=")[1].split(";")[0].replaceAll("(?i)^UTF-8''|[\"']", ""));
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            request.setDescription("Downloading file...");

            // NEW: Use isolated cookie manager
            String cookie = cookieManager.getCookie(url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);

            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) dm.enqueue(request);
            Toast.makeText(activity, "Downloading: " + fileName, Toast.LENGTH_SHORT).show();
        });
    }

    private static Uri createImageUri(Context context) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "photo");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        return context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
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

    // BlobDownloader class remains exactly identical to your old code!
    public static class BlobDownloader {
        private final Context context;
        private final Handler handler;

        public BlobDownloader(Context context) {
            this.context = context;
            this.handler = new Handler(Looper.getMainLooper());
        }

        @JavascriptInterface
        public void processBlobData(String base64Data, String mimeType) {
            new Thread(() -> {
                try {
                    String base64 = base64Data.replaceFirst("^data:.*?;base64,", "");
                    byte[] fileBytes = Base64.decode(base64, Base64.DEFAULT);
                    String cleanMimeType = mimeType.split(";")[0];
                    String mappedExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(cleanMimeType);
                    String ext = (mappedExt != null && !mappedExt.isEmpty()) ? "." + mappedExt : ".bin";
                    String fileName = "ChatGPT_" + System.currentTimeMillis() + ext;

                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, cleanMimeType);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                    Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("MediaStore failed.");

                    try (java.io.OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                        if (os != null) os.write(fileBytes);
                    }
                    handler.post(() -> Toast.makeText(context, "Saved: " + fileName, Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    handler.post(() -> Toast.makeText(context, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }
    }
}