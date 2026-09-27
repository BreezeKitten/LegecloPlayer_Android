package com.legeclo.player;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private WebView webView;
    private LocalWebServer localServer;
    private long lastBackPressTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Force Landscape & Keep Screen On
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 2. Setup View
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);
        setupWebView();

        // 3. Immersive Fullscreen (Hide Status Bar & Navigation Bar, Short Edges Notch)
        hideSystemUI();

        // 4. Start Local Embedded Server
        localServer = new LocalWebServer(this, 8888);
        localServer.start();

        // 5. Request Storage Permissions on Android 11+ if needed
        checkStoragePermission();

        // 6. Load Game Player
        Intent intent = getIntent();
        String loadUrl = intent != null ? intent.getStringExtra("url") : null;
        if (loadUrl == null || loadUrl.isEmpty()) {
            loadUrl = "http://127.0.0.1:8888/";
        }
        webView.loadUrl(loadUrl);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && webView != null) {
            String url = intent.getStringExtra("url");
            if (url != null && !url.isEmpty()) {
                webView.loadUrl(url);
            }
        }
    }

    private void hideSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(false);
                View decorView = getWindow().getDecorView();
                if (decorView != null) {
                    WindowInsetsController controller = decorView.getWindowInsetsController();
                    if (controller != null) {
                        controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                        controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    }
                }
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(lp);
            } else {
                View decorView = getWindow().getDecorView();
                if (decorView != null) {
                    decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                    );
                }
            }
        } catch (Exception ignored) {}
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        }
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                hideSystemUI();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("(function(){" +
                "var m = document.querySelector('.modal:not(.hidden)');" +
                "if(m){ m.classList.add('hidden'); return true; }" +
                "var sb = document.getElementById('sidebar');" +
                "if(sb && sb.classList.contains('open')){ sb.classList.remove('open'); return true; }" +
                "return false;" +
                "})();", value -> {
                    if (!"true".equals(value)) {
                        if (System.currentTimeMillis() - lastBackPressTime < 2000) {
                            finish();
                        } else {
                            lastBackPressTime = System.currentTimeMillis();
                            Toast.makeText(this, "再按一次退出播放器", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        } else {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (localServer != null) {
            localServer.stop();
        }
        if (webView != null) {
            webView.destroy();
        }
    }
}
