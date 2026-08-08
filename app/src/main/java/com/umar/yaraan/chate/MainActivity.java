package com.umar.yaraan.chate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.content.SharedPreferences;
import org.json.JSONObject;
import android.webkit.JavascriptInterface;
import android.widget.EditText;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TARGET_URL = "https://yaraan.online";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int FILE_CHOOSER_REQUEST_CODE = 1002;

    private FrameLayout webViewContainer;
    private WebView webView;
    private WebView popupWebView;
    private Dialog popupDialog;
    private RelativeLayout splashScreen;
    private LinearLayout offlineScreen;
    private Button btnRetry;

    // Native Login UI fields
    private RelativeLayout nativeLoginScreen;
    private EditText etEmail, etPassword;
    private Button btnNativeLogin, btnNativeGoogle;

    // File upload variables
    private ValueCallback<Uri[]> filePathCallback;
    private String cameraPhotoPath;

    // Permissions to request at startup or dynamically
    private final String[] requiredPermissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? Manifest.permission.POST_NOTIFICATIONS : Manifest.permission.INTERNET
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Configure Full-Screen Transparent Status Bar
        configureFullScreen();

        setContentView(R.layout.activity_main);

        // Initialize UI components
        webViewContainer = findViewById(R.id.webview_container);
        webView = findViewById(R.id.webview);

        // Apply dynamic status bar top padding to the WebView container
        ViewCompat.setOnApplyWindowInsetsListener(webViewContainer, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(0, statusBarHeight, 0, 0);
            return insets;
        });
        splashScreen = findViewById(R.id.splash_screen);
        offlineScreen = findViewById(R.id.offline_screen);
        btnRetry = findViewById(R.id.btn_retry);

        // Initialize Native Login Views
        nativeLoginScreen = findViewById(R.id.native_login_screen);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnNativeLogin = findViewById(R.id.btn_native_login);
        btnNativeGoogle = findViewById(R.id.btn_native_google);
        TextView tvForgotPassword = findViewById(R.id.tv_forgot_password);

        // 2. Setup WebView and Settings
        setupWebView();

        // 3. Setup Back Button Callback
        setupBackButton();

        // 4. Retry connection click handler
        btnRetry.setOnClickListener(v -> attemptLoadUrl());

        tvForgotPassword.setOnClickListener(v -> {
            if (!isNetworkConnected()) {
                Toast.makeText(this, "No internet connection. Please check your network.", Toast.LENGTH_SHORT).show();
                showOfflineScreen();
                return;
            }
            // Hide native login to let WebView show web forgot password modal
            nativeLoginScreen.setVisibility(View.GONE);
            webView.evaluateJavascript("if (window.openForgotPasswordModal) { window.openForgotPasswordModal(); }", null);
        });

        // Setup native login click listeners
        btnNativeLogin.setOnClickListener(v -> {
            if (!isNetworkConnected()) {
                Toast.makeText(this, "No internet connection. Please check your network.", Toast.LENGTH_SHORT).show();
                showOfflineScreen();
                return;
            }
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            // Set UI to loading state
            btnNativeLogin.setEnabled(false);
            btnNativeLogin.setText("Signing in...");
            btnNativeGoogle.setEnabled(false);

            // Set 10-second safety timeout to reset buttons if request hangs
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutHandler.postDelayed(timeoutRunnable, 10000);

            // Programmatically auto-check privacy policy checkbox on the website to bypass block
            webView.evaluateJavascript(
                "var cb = document.getElementById('privacy-checkbox'); " +
                "if (cb) { cb.checked = true; }", null);

            // Safely construct JSON payload to prevent escaping/special-character bugs
            try {
                JSONObject jsonPayload = new JSONObject();
                jsonPayload.put("type", "emailLogin");
                jsonPayload.put("email", email);
                jsonPayload.put("password", password);

                String js = "window.postMessage(" + jsonPayload.toString() + ", '*');";
                webView.evaluateJavascript(js, null);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Login initialization failed", Toast.LENGTH_SHORT).show();
                resetLoginButtons();
            }
        });

        btnNativeGoogle.setOnClickListener(v -> {
            if (!isNetworkConnected()) {
                Toast.makeText(this, "No internet connection. Please check your network.", Toast.LENGTH_SHORT).show();
                showOfflineScreen();
                return;
            }
            // Set UI to loading state
            btnNativeGoogle.setEnabled(false);
            btnNativeGoogle.setText("Opening Google...");
            btnNativeLogin.setEnabled(false);

            // Set 10-second safety timeout
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutHandler.postDelayed(timeoutRunnable, 10000);

            // Show webview so the user can complete Google Sign-In
            webView.setVisibility(View.VISIBLE);
            nativeLoginScreen.setVisibility(View.GONE);

            // Programmatically auto-check privacy policy checkbox on the website to bypass block
            webView.evaluateJavascript(
                "var cb = document.getElementById('privacy-checkbox'); " +
                "if (cb) { cb.checked = true; }", null);

            // Trigger web's standard Google Login
            webView.evaluateJavascript("window.handleGoogleLoginTrigger();", null);
        });

        // 5. Request necessary runtime permissions
        checkAndRequestPermissions();

        // Register dynamic network callback for auto-reconnect
        registerNetworkCallback();

        // 6. Start loading
        attemptLoadUrl();
    }

    private void configureFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
            window.setStatusBarColor(Color.TRANSPARENT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Ensure status bar icons are visible on light splash background
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                );
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Support opening popups / multiple windows (required for Google/Firebase OAuth popup flow)
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // Media/audio settings
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Enable hardware acceleration
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Enable and accept third-party cookies for seamless OAuth redirects across domains
        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        // Dynamically clean User Agent to bypass Google's disallowed_useragent checks in WebViews with robust regex
        String originalUserAgent = settings.getUserAgentString();
        if (originalUserAgent != null) {
            String cleanUserAgent = originalUserAgent.replace("; wv", "");
            cleanUserAgent = cleanUserAgent.replaceAll("Version/[0-9.]+\\s?", "");
            settings.setUserAgentString(cleanUserAgent);
        }

        // Custom WebViewClient
        webView.addJavascriptInterface(new WebAppInterface(), "YaraanAppChannel");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // Inject CSS to hide ONLY the website's login inputs and controls, keeping the premium background animation
                webView.evaluateJavascript(
                    "var style = document.createElement('style'); " +
                    "style.innerHTML = '#auth-content-wrapper { display: none !important; }'; " +
                    "document.head.appendChild(style);", null);

                // Intercept closeForgotPasswordModal to notify Android
                webView.evaluateJavascript(
                    "(function() { " +
                    "    if (window.openForgotPasswordModal && !window._forgotPasswordHooked) { " +
                    "        window._forgotPasswordHooked = true; " +
                    "        const originalClose = window.closeForgotPasswordModal; " +
                    "        window.closeForgotPasswordModal = function() { " +
                    "            if (originalClose) originalClose(); " +
                    "            if (window.YaraanAppChannel) { " +
                    "                window.YaraanAppChannel.postMessage(JSON.stringify({type: 'forgot_password_closed'})); " +
                    "            } " +
                    "        }; " +
                    "    } " +
                    "})();", null);

                // Inject dynamic auth state listener to notify Android on successful login
                webView.evaluateJavascript(
                    "if (window.auth) { " +
                    "    window.auth.onAuthStateChanged(function(user) { " +
                    "        if (user) { " +
                    "            YaraanAppChannel.postMessage(JSON.stringify({type: 'login_success', uid: user.uid})); " +
                    "        } " +
                    "    }); " +
                    "}", null);

                // Inject alerts/Swal/unhandledrejection overrides to propagate authentication or general errors back to native
                webView.evaluateJavascript(
                    "(function() { " +
                    "    const originalAlert = window.alert; " +
                    "    window.alert = function(msg) { " +
                    "        if (window.YaraanAppChannel) { " +
                    "            window.YaraanAppChannel.postMessage(JSON.stringify({type: 'error', message: String(msg)})); " +
                    "        } " +
                    "        originalAlert.apply(this, arguments); " +
                    "    }; " +
                    "    function hookSwal() { " +
                    "        if (window.Swal && window.Swal.fire && !window.Swal._hooked) { " +
                    "            window.Swal._hooked = true; " +
                    "            const originalSwalFire = window.Swal.fire; " +
                    "            window.Swal.fire = function() { " +
                    "                let msg = ''; " +
                    "                if (arguments.length > 0) { " +
                    "                    if (typeof arguments[0] === 'object') { " +
                    "                        msg = arguments[0].text || arguments[0].title || JSON.stringify(arguments[0]); " +
                    "                    } else { " +
                    "                        msg = Array.from(arguments).join(' '); " +
                    "                    } " +
                    "                } " +
                    "                if (window.YaraanAppChannel) { " +
                    "                    window.YaraanAppChannel.postMessage(JSON.stringify({type: 'error', message: msg})); " +
                    "                } " +
                    "                return originalSwalFire.apply(this, arguments); " +
                    "            }; " +
                    "        } " +
                    "        if (window.swal && !window.swal._hooked) { " +
                    "            window.swal._hooked = true; " +
                    "            const originalSwal = window.swal; " +
                    "            window.swal = function() { " +
                    "                let msg = ''; " +
                    "                if (arguments.length > 0) { " +
                    "                    if (typeof arguments[0] === 'object') { " +
                    "                        msg = arguments[0].text || arguments[0].title || JSON.stringify(arguments[0]); " +
                    "                    } else { " +
                    "                        msg = Array.from(arguments).join(' '); " +
                    "                    } " +
                    "                } " +
                    "                if (window.YaraanAppChannel) { " +
                    "                    window.YaraanAppChannel.postMessage(JSON.stringify({type: 'error', message: msg})); " +
                    "                } " +
                    "                return originalSwal.apply(this, arguments); " +
                    "            }; " +
                    "        } " +
                    "    } " +
                    "    hookSwal(); " +
                    "    setInterval(hookSwal, 1000); " +
                    "    window.addEventListener('unhandledrejection', function(event) { " +
                    "        let msg = event.reason ? (event.reason.message || event.reason) : 'Unknown Error'; " +
                    "        if (window.YaraanAppChannel) { " +
                    "            window.YaraanAppChannel.postMessage(JSON.stringify({type: 'error', message: String(msg)})); " +
                    "        } " +
                    "    }); " +
                    "})();", null);

                // Hide Splash screen once fully loaded
                if (splashScreen.getVisibility() == View.VISIBLE) {
                    AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
                    fadeOut.setDuration(400);
                    fadeOut.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(android.view.animation.Animation animation) {}

                        @Override
                        public void onAnimationEnd(android.view.animation.Animation animation) {
                            splashScreen.setVisibility(View.GONE);
                            // Only show WebView if user is logged in
                            SharedPreferences prefs = getSharedPreferences("YaraanPrefs", MODE_PRIVATE);
                            boolean isLoggedIn = prefs.getBoolean("is_logged_in", false);
                            if (isLoggedIn) {
                                webView.setVisibility(View.VISIBLE);
                            } else {
                                showNativeLoginScreen();
                            }
                        }

                        @Override
                        public void onAnimationRepeat(android.view.animation.Animation animation) {}
                    });
                    splashScreen.startAnimation(fadeOut);
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // Show offline screen on standard errors
                showOfflineScreen();
            }

            @TargetApi(Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                // Check if this error is for the main page
                if (request.isForMainFrame()) {
                    showOfflineScreen();
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false; // Load in WebView
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    view.getContext().startActivity(intent);
                    return true;
                } catch (Exception e) {
                    return true; // Handle missing apps gracefully
                }
            }

            @TargetApi(Build.VERSION_CODES.N)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false; // Load in WebView
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    view.getContext().startActivity(intent);
                    return true;
                } catch (Exception e) {
                    return true; // Handle missing apps gracefully
                }
            }
        });

        // Custom WebChromeClient to handle camera permissions, file uploads, and window popups
        webView.setWebChromeClient(new WebChromeClient() {
            // Support camera / microphone permissions dynamically in WebView
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                MainActivity.this.runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        request.grant(request.getResources());
                    }
                });
            }

            // Support popup windows (e.g. Firebase/Google sign-in popup flow) by creating a dynamic popup WebView
            @SuppressLint("SetJavaScriptEnabled")
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                // If there's an existing popup webview/dialog, clean it up first
                if (popupDialog != null && popupDialog.isShowing()) {
                    popupDialog.dismiss();
                    popupDialog = null;
                }
                if (popupWebView != null) {
                    popupWebView.destroy();
                    popupWebView = null;
                }

                popupWebView = new WebView(MainActivity.this);
                popupWebView.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));

                // Enable hardware acceleration for popup WebView
                popupWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                WebSettings popupSettings = popupWebView.getSettings();
                popupSettings.setJavaScriptEnabled(true);
                popupSettings.setDomStorageEnabled(true);
                popupSettings.setDatabaseEnabled(true);
                popupSettings.setAllowFileAccess(true);
                popupSettings.setAllowContentAccess(true);
                popupSettings.setUseWideViewPort(true);
                popupSettings.setLoadWithOverviewMode(true);
                popupSettings.setSupportMultipleWindows(true);
                popupSettings.setJavaScriptCanOpenWindowsAutomatically(true);

                // Ensure third-party cookies are accepted for OAuth popup
                android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.setAcceptThirdPartyCookies(popupWebView, true);
                }

                // Clean the popup WebView user agent with robust regex
                String originalUA = popupSettings.getUserAgentString();
                if (originalUA != null) {
                    String cleanUA = originalUA.replace("; wv", "");
                    cleanUA = cleanUA.replaceAll("Version/[0-9.]+\\s?", "");
                    popupSettings.setUserAgentString(cleanUA);
                }

                popupWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        return false; // let the popup WebView load it
                    }

                    @TargetApi(Build.VERSION_CODES.N)
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                        return false;
                    }
                });

                // Host popupWebView inside a native full-screen Dialog
                popupDialog = new Dialog(MainActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                popupDialog.setContentView(popupWebView);
                popupDialog.setCancelable(true);

                // If user cancels the dialog (e.g. by pressing back button), handle cleanup
                popupDialog.setOnCancelListener(dialog -> {
                    if (popupWebView != null) {
                        popupWebView.destroy();
                        popupWebView = null;
                    }
                    popupDialog = null;
                    SharedPreferences prefs = getSharedPreferences("YaraanPrefs", MODE_PRIVATE);
                    boolean isLoggedIn = prefs.getBoolean("is_logged_in", false);
                    if (!isLoggedIn) {
                        showNativeLoginScreen();
                        resetLoginButtons();
                    }
                });

                popupWebView.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onCloseWindow(WebView window) {
                        super.onCloseWindow(window);
                        if (popupDialog != null && popupDialog.isShowing()) {
                            popupDialog.dismiss();
                            popupDialog = null;
                        }
                        if (popupWebView != null) {
                            popupWebView.destroy();
                            popupWebView = null;
                        }
                        // Check if still not logged in, if so recover Native Login UI layout rather than blank screen
                        SharedPreferences prefs = getSharedPreferences("YaraanPrefs", MODE_PRIVATE);
                        boolean isLoggedIn = prefs.getBoolean("is_logged_in", false);
                        if (!isLoggedIn) {
                            showNativeLoginScreen();
                            resetLoginButtons();
                        }
                    }
                });

                popupDialog.show();

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popupWebView);
                resultMsg.sendToTarget();
                return true;
            }

            // File Chooser for <input type="file" />
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    File photoFile = null;
                    try {
                        photoFile = createImageFile();
                        takePictureIntent.putExtra("PhotoPath", cameraPhotoPath);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }

                    if (photoFile != null) {
                        Uri photoURI = FileProvider.getUriForFile(MainActivity.this,
                                "com.umar.yaraan.chate.fileprovider",
                                photoFile);
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    } else {
                        takePictureIntent = null;
                    }
                }

                Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                contentSelectionIntent.setType("*/*");

                Intent[] intentArray;
                if (takePictureIntent != null) {
                    intentArray = new Intent[]{takePictureIntent};
                } else {
                    intentArray = new Intent[0];
                }

                Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "Select File or Capture Image");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);

                startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST_CODE);
                return true;
            }
        });
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        cameraPhotoPath = "file:" + image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }

            Uri[] results = null;

            // Check if response is positive and contains values
            if (resultCode == RESULT_OK) {
                if (data == null || data.getData() == null) {
                    // Capture path contains image from camera
                    if (cameraPhotoPath != null) {
                        results = new Uri[]{Uri.parse(cameraPhotoPath)};
                    }
                } else {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
            }

            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void setupBackButton() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (popupDialog != null && popupDialog.isShowing()) {
                    popupDialog.dismiss();
                    popupDialog = null;
                    if (popupWebView != null) {
                        popupWebView.destroy();
                        popupWebView = null;
                    }
                    // Recover native login UI layout if they cancel out of popup
                    SharedPreferences prefs = getSharedPreferences("YaraanPrefs", MODE_PRIVATE);
                    boolean isLoggedIn = prefs.getBoolean("is_logged_in", false);
                    if (!isLoggedIn) {
                        showNativeLoginScreen();
                        resetLoginButtons();
                    }
                } else if (nativeLoginScreen.getVisibility() == View.VISIBLE) {
                    finish();
                } else if (nativeLoginScreen.getVisibility() != View.VISIBLE) {
                    SharedPreferences prefs = getSharedPreferences("YaraanPrefs", MODE_PRIVATE);
                    boolean isLoggedIn = prefs.getBoolean("is_logged_in", false);
                    if (!isLoggedIn) {
                        // User is in forgot password modal, close it and return to native login
                        webView.evaluateJavascript("if (window.closeForgotPasswordModal) { window.closeForgotPasswordModal(); }", null);
                    } else if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        finish();
                    }
                } else if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    // Exit the app gracefully
                    finish();
                }
            }
        });
    }

    private void attemptLoadUrl() {
        if (isNetworkConnected()) {
            hideOfflineScreen();
            if (webView.getUrl() == null) {
                webView.loadUrl(TARGET_URL);
            } else if (offlineScreen.getVisibility() == View.VISIBLE) {
                webView.reload();
            }
        } else {
            showOfflineScreen();
        }
    }

    private ConnectivityManager.NetworkCallback networkCallback;

    private void registerNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        runOnUiThread(() -> {
                            attemptLoadUrl();
                        });
                    }

                    @Override
                    public void onLost(@NonNull Network network) {
                        runOnUiThread(() -> {
                            if (!isNetworkConnected()) {
                                showOfflineScreen();
                            }
                        });
                    }
                };
                try {
                    cm.registerDefaultNetworkCallback(networkCallback);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void unregisterNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                try {
                    cm.unregisterNetworkCallback(networkCallback);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.webkit.CookieManager.getInstance().flush();
        }
    }

    @Override
    protected void onDestroy() {
        unregisterNetworkCallback();
        super.onDestroy();
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network network = cm.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                    return capabilities != null && (
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
                }
            } else {
                android.net.NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        }
        return false;
    }

    private void showOfflineScreen() {
        webView.setVisibility(View.GONE);
        splashScreen.setVisibility(View.GONE);
        nativeLoginScreen.setVisibility(View.GONE);
        offlineScreen.setVisibility(View.VISIBLE);
    }

    private void hideOfflineScreen() {
        offlineScreen.setVisibility(View.GONE);
        // Do not immediately make webview visible if splash screen is running
        if (splashScreen.getVisibility() != View.VISIBLE) {
            SharedPreferences prefs = getSharedPreferences("YaraanPrefs", MODE_PRIVATE);
            boolean isLoggedIn = prefs.getBoolean("is_logged_in", false);
            if (isLoggedIn) {
                webView.setVisibility(View.VISIBLE);
            } else {
                showNativeLoginScreen();
            }
        }
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void postMessage(String message) {
            runOnUiThread(() -> {
                handleWebMessage(message);
            });
        }
    }

    private void handleWebMessage(String message) {
        if (message == null) return;
        try {
            if (message.equals("user_logged_out")) {
                SharedPreferences prefs = getSharedPreferences("YaraanPrefs", MODE_PRIVATE);
                prefs.edit().putBoolean("is_logged_in", false).apply();
                showNativeLoginScreen();
            } else if (message.startsWith("{")) {
                JSONObject json = new JSONObject(message);
                String type = json.optString("type");
                if (type.equals("login_success")) {
                    SharedPreferences prefs = getSharedPreferences("YaraanPrefs", MODE_PRIVATE);
                    prefs.edit().putBoolean("is_logged_in", true).apply();
                    hideNativeLoginScreen();
                    resetLoginButtons();
                } else if (type.equals("error")) {
                    String errorMsg = json.optString("message", "Authentication failed");
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    resetLoginButtons();
                } else if (type.equals("forgot_password_closed")) {
                    showNativeLoginScreen();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showNativeLoginScreen() {
        runOnUiThread(() -> {
            webView.setVisibility(View.VISIBLE);
            nativeLoginScreen.setVisibility(View.VISIBLE);
        });
    }

    private void hideNativeLoginScreen() {
        runOnUiThread(() -> {
            nativeLoginScreen.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
        });
    }

    private final android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable timeoutRunnable = this::resetLoginButtons;

    private void resetLoginButtons() {
        runOnUiThread(() -> {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            btnNativeLogin.setEnabled(true);
            btnNativeLogin.setText("Sign In");
            btnNativeGoogle.setEnabled(true);
            btnNativeGoogle.setText("Sign in with Google");
        });
    }

    private void checkAndRequestPermissions() {
        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String perm : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(perm);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    // Standard notification or warning, but allow application to function
                    Toast.makeText(this, "Permission " + permissions[i] + " denied. Some features might not work properly.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
