package com.umar.yaraan.chate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.widget.CheckBox;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TARGET_URL = "https://yaraan.online";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int FILE_CHOOSER_REQUEST_CODE = 1002;
    private static final int RC_SIGN_IN = 1003;

    private FrameLayout webViewContainer;
    private WebView webView;
    private WebView popupWebView;
    private Dialog popupDialog;
    private RelativeLayout splashScreen;
    private LinearLayout offlineScreen;
    private Button btnRetry;

    // Native Login UI Container & Background Video
    private RelativeLayout nativeLoginScreen;
    private VideoView videoView;

    // Email form section views
    private RelativeLayout sectionInitialLogin;
    private RelativeLayout sectionEmailLogin;
    private EditText etName, etEmail, etPassword;
    private TextView tvFormTitle, tvForgotPassword, tvToggleMode;
    private Button btnNativeLogin;
    private LinearLayout btnNativeGoogle;
    private ImageView btnOpenEmailScreen;
    private ImageView btnBackToInitial;

    // Privacy Policy UI components
    private CheckBox cbPrivacyPolicy;
    private TextView tvPrivacyPolicy;

    // File upload variables
    private ValueCallback<Uri[]> filePathCallback;
    private String cameraPhotoPath;

    // Google Sign-In SDK
    private GoogleSignInClient mGoogleSignInClient;

    // Form states
    private enum FormMode {
        LOGIN,
        REGISTER,
        FORGOT_PASSWORD
    }
    private FormMode currentFormMode = FormMode.LOGIN;

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

        setContentView(R.layout.activity_main);

        // 1. Configure Full-Screen Transparent Status Bar & Immersive Mode (Always call after setContentView for safe window attachments)
        configureFullScreen();

        // Initialize UI components
        webViewContainer = findViewById(R.id.webview_container);
        webView = findViewById(R.id.webview);

        // WebView matches full edge-to-edge screens seamlessly with zero padding
        webViewContainer.setPadding(0, 0, 0, 0);

        splashScreen = findViewById(R.id.splash_screen);
        offlineScreen = findViewById(R.id.offline_screen);
        btnRetry = findViewById(R.id.btn_retry);

        // Initialize Native Login Views
        nativeLoginScreen = findViewById(R.id.native_login_screen);
        videoView = findViewById(R.id.video_view);

        sectionInitialLogin = findViewById(R.id.section_initial_login);
        sectionEmailLogin = findViewById(R.id.section_email_login);

        btnNativeGoogle = findViewById(R.id.btn_native_google);
        btnOpenEmailScreen = findViewById(R.id.btn_open_email_screen);
        btnBackToInitial = findViewById(R.id.btn_back_to_initial);

        etName = findViewById(R.id.et_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        tvFormTitle = findViewById(R.id.tv_form_title);
        tvForgotPassword = findViewById(R.id.tv_forgot_password);
        tvToggleMode = findViewById(R.id.tv_toggle_mode);
        btnNativeLogin = findViewById(R.id.btn_native_login);

        cbPrivacyPolicy = findViewById(R.id.cb_privacy_policy);
        tvPrivacyPolicy = findViewById(R.id.tv_privacy_policy);

        if (tvPrivacyPolicy != null) {
            tvPrivacyPolicy.setPaintFlags(tvPrivacyPolicy.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            tvPrivacyPolicy.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/document/d/1Mq4m80_848fEtMh4t3S1CZaj4yQEczCQCbWVfC_TDmM/edit?usp=drivesdk"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Unable to open Privacy Policy", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Start background video playback on native screen
        setupBackgroundVideo();

        // 2. Setup WebView and Settings
        setupWebView();

        // 3. Initialize Google Sign-In SDK with the correct Firebase Web Client ID
        SharedPreferences prefs = getSharedPreferences("YaraanPrefs", MODE_PRIVATE);
        String cachedClientId = prefs.getString("google_client_id", "740464208491-r63hohlm9o2lvc40f8gffitrbe6pceq8.apps.googleusercontent.com");
        initGoogleSignIn(cachedClientId);

        // 4. Setup Back Button Callback
        setupBackButton();

        // 5. Retry connection click handler
        btnRetry.setOnClickListener(v -> attemptLoadUrl());

        // Toggle visibility between initial screen and email form screen
        btnOpenEmailScreen.setOnClickListener(v -> {
            if (cbPrivacyPolicy != null && !cbPrivacyPolicy.isChecked()) {
                Toast.makeText(this, "Please agree to the Privacy Policy to proceed.", Toast.LENGTH_SHORT).show();
                return;
            }
            sectionInitialLogin.setVisibility(View.GONE);
            sectionEmailLogin.setVisibility(View.VISIBLE);
            updateFormMode(FormMode.LOGIN);
        });

        btnBackToInitial.setOnClickListener(v -> {
            sectionEmailLogin.setVisibility(View.GONE);
            sectionInitialLogin.setVisibility(View.VISIBLE);
        });

        tvForgotPassword.setOnClickListener(v -> updateFormMode(FormMode.FORGOT_PASSWORD));

        tvToggleMode.setOnClickListener(v -> {
            if (currentFormMode == FormMode.LOGIN) {
                updateFormMode(FormMode.REGISTER);
            } else {
                updateFormMode(FormMode.LOGIN);
            }
        });

        // Setup native email action submit click listener
        btnNativeLogin.setOnClickListener(v -> {
            if (cbPrivacyPolicy != null && !cbPrivacyPolicy.isChecked()) {
                Toast.makeText(this, "Please agree to the Privacy Policy to proceed.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isNetworkConnected()) {
                Toast.makeText(this, "No internet connection. Please check your network.", Toast.LENGTH_SHORT).show();
                showOfflineScreen();
                return;
            }

            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();
            String name = etName.getText().toString().trim();

            if (email.isEmpty() || !email.contains("@")) {
                etEmail.setError("Please enter a valid email");
                return;
            }

            if (currentFormMode != FormMode.FORGOT_PASSWORD && password.isEmpty()) {
                etPassword.setError("Please enter a password");
                return;
            }

            if (currentFormMode == FormMode.REGISTER && name.isEmpty()) {
                etName.setError("Please enter your name");
                return;
            }

            // Set UI to loading state
            btnNativeLogin.setEnabled(false);
            btnNativeLogin.setText("Processing...");
            btnNativeGoogle.setEnabled(false);

            // Set 10-second safety timeout to reset buttons if request hangs
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutHandler.postDelayed(timeoutRunnable, 10000);

            // Programmatically auto-check privacy policy checkbox on the website to bypass block
            webView.evaluateJavascript(
                "var cb = document.getElementById('privacy-checkbox'); " +
                "if (cb) { cb.checked = true; cb.dispatchEvent(new Event('change', { bubbles: true })); }", null);

            if (currentFormMode == FormMode.LOGIN) {
                // Perform web login via PostMessage API
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
            } else if (currentFormMode == FormMode.REGISTER) {
                // Inject credentials, switch web auth to signup mode, and dispatch form submit natively
                String escapedName = name.replace("\"", "\\\"");
                String escapedEmail = email.replace("\"", "\\\"");
                String escapedPassword = password.replace("\"", "\\\"");

                String js = "(function() { " +
                        "    var cb = document.getElementById('privacy-checkbox'); " +
                        "    if (cb) { cb.checked = true; cb.dispatchEvent(new Event('change', { bubbles: true })); } " +
                        "    var uFieldContainer = document.getElementById('username-field'); " +
                        "    if (uFieldContainer && uFieldContainer.classList.contains('hidden')) { " +
                        "        window.toggleAuthMode(); " +
                        "    } " +
                        "    var userField = document.getElementById('username'); " +
                        "    var emailField = document.getElementById('email'); " +
                        "    var passField = document.getElementById('password'); " +
                        "    if (userField) userField.value = \"" + escapedName + "\"; " +
                        "    if (emailField) emailField.value = \"" + escapedEmail + "\"; " +
                        "    if (passField) passField.value = \"" + escapedPassword + "\"; " +
                        "    var form = document.getElementById('auth-form'); " +
                        "    if (form) { " +
                        "        form.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true })); " +
                        "    } " +
                        "})();";
                webView.evaluateJavascript(js, null);
            } else if (currentFormMode == FormMode.FORGOT_PASSWORD) {
                // Fill web forgot password input and trigger otp reset natively
                String escapedEmail = email.replace("\"", "\\\"");
                String js = "(function() { " +
                        "    var fpField = document.getElementById('fp-email-input'); " +
                        "    if (fpField) fpField.value = \"" + escapedEmail + "\"; " +
                        "    if (window.sendForgotPasswordOTP) { window.sendForgotPasswordOTP(); } " +
                        "})();";
                webView.evaluateJavascript(js, null);
                // Since reset is done in sweetalert and won't trigger auth state changes, release UI loading state immediately
                resetLoginButtons();
            }
        });

        // Native Google sign-in trigger
        btnNativeGoogle.setOnClickListener(v -> {
            if (cbPrivacyPolicy != null && !cbPrivacyPolicy.isChecked()) {
                Toast.makeText(this, "Please agree to the Privacy Policy to proceed.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isNetworkConnected()) {
                Toast.makeText(this, "No internet connection. Please check your network.", Toast.LENGTH_SHORT).show();
                showOfflineScreen();
                return;
            }

            // Programmatically auto-check privacy policy checkbox on the website to bypass block
            webView.evaluateJavascript(
                "var cb = document.getElementById('privacy-checkbox'); " +
                "if (cb) { cb.checked = true; cb.dispatchEvent(new Event('change', { bubbles: true })); }", null);

            SharedPreferences prefs1 = getSharedPreferences("YaraanPrefs", MODE_PRIVATE);
            String clientId = prefs1.getString("google_client_id", null);

            if (clientId != null && mGoogleSignInClient != null) {
                // Trigger modern Google accounts chooser bottom sheet
                btnNativeGoogle.setEnabled(false);
                btnNativeLogin.setEnabled(false);
                mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
                    Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                    startActivityForResult(signInIntent, RC_SIGN_IN);
                });
            } else {
                // Cold-start fallback: Let main WebView load Google login programmatically to capture Google's client ID first
                btnNativeGoogle.setEnabled(false);
                btnNativeLogin.setEnabled(false);

                // Set 15-second safety timeout
                timeoutHandler.removeCallbacks(timeoutRunnable);
                timeoutHandler.postDelayed(timeoutRunnable, 15000);

                // Dynamically override userAgent so auth_system.js treats the client as a standard Chrome mobile browser
                String jsOverrideUA = "Object.defineProperty(navigator, 'userAgent', { get: function () { return 'Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36'; } });";
                webView.evaluateJavascript(jsOverrideUA, null);

                // Trigger web's standard Google Login
                webView.evaluateJavascript("if (window.handleGoogleLoginTrigger) { window.handleGoogleLoginTrigger(); }", null);
            }
        });

        // 6. Request necessary runtime permissions
        checkAndRequestPermissions();

        // Register dynamic network callback for auto-reconnect
        registerNetworkCallback();

        // 7. Start loading TARGET_URL
        attemptLoadUrl();
    }

    private void configureFullScreen() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        // Hide status bar completely, showing it temporarily only if user swipes down
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private void setupBackgroundVideo() {
        try {
            File videoFile = getAssetVideoFile();
            if (videoFile.exists() && videoFile.length() > 0) {
                videoView.setVideoPath(videoFile.getAbsolutePath());
                videoView.setOnPreparedListener(mp -> {
                    mp.setLooping(true);
                    mp.setVolume(0f, 0f); // Play silently
                    videoView.start();
                });
                videoView.setOnErrorListener((mp, what, extra) -> {
                    videoView.setVisibility(View.GONE);
                    return true;
                });
            } else {
                videoView.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            e.printStackTrace();
            videoView.setVisibility(View.GONE);
        }
    }

    private File getAssetVideoFile() {
        File file = new File(getCacheDir(), "bg_login.mp4");
        try (InputStream is = getAssets().open("bg_login.mp4");
             FileOutputStream os = new FileOutputStream(file)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

    private void updateFormMode(FormMode mode) {
        currentFormMode = mode;
        runOnUiThread(() -> {
            etName.setError(null);
            etEmail.setError(null);
            etPassword.setError(null);

            switch (mode) {
                case LOGIN:
                    tvFormTitle.setText("Sign In");
                    etName.setVisibility(View.GONE);
                    etEmail.setVisibility(View.VISIBLE);
                    etPassword.setVisibility(View.VISIBLE);
                    tvForgotPassword.setVisibility(View.VISIBLE);
                    btnNativeLogin.setText("Sign In");
                    tvToggleMode.setText("Don't have an account? Create one");
                    break;
                case REGISTER:
                    tvFormTitle.setText("Create Account");
                    etName.setVisibility(View.VISIBLE);
                    etEmail.setVisibility(View.VISIBLE);
                    etPassword.setVisibility(View.VISIBLE);
                    tvForgotPassword.setVisibility(View.GONE);
                    btnNativeLogin.setText("Create Account");
                    tvToggleMode.setText("Already have an account? Sign In");
                    break;
                case FORGOT_PASSWORD:
                    tvFormTitle.setText("Reset Password");
                    etName.setVisibility(View.GONE);
                    etEmail.setVisibility(View.VISIBLE);
                    etPassword.setVisibility(View.GONE);
                    tvForgotPassword.setVisibility(View.GONE);
                    btnNativeLogin.setText("Send Reset Link");
                    tvToggleMode.setText("Back to Sign In");
                    break;
            }
        });
    }

    private void initGoogleSignIn(String clientId) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientId)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void checkAndExtractClientId(String url) {
        if (url == null) return;
        if (url.contains("client_id=") && url.contains("apps.googleusercontent.com")) {
            try {
                Uri uri = Uri.parse(url);
                String clientId = uri.getQueryParameter("client_id");
                if (clientId != null && !clientId.isEmpty()) {
                    SharedPreferences prefs = getSharedPreferences("YaraanPrefs", MODE_PRIVATE);
                    String savedClientId = prefs.getString("google_client_id", null);
                    if (savedClientId == null || !savedClientId.equals(clientId)) {
                        prefs.edit().putString("google_client_id", clientId).apply();
                        initGoogleSignIn(clientId);

                        // Cold start capture successful! Cancel popup dialog and trigger the native accounts chooser immediately
                        runOnUiThread(() -> {
                            if (popupDialog != null && popupDialog.isShowing()) {
                                popupDialog.dismiss();
                                popupDialog = null;
                            }
                            if (popupWebView != null) {
                                popupWebView.destroy();
                                popupWebView = null;
                            }
                            resetLoginButtons();
                            mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
                                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                                startActivityForResult(signInIntent, RC_SIGN_IN);
                            });
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
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
            cleanUserAgent = cleanUserAgent + " YaraanFlutterApp";
            settings.setUserAgentString(cleanUserAgent);
        }

        // Custom WebViewClient
        webView.addJavascriptInterface(new WebAppInterface(), "YaraanAppChannel");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // Inject CSS to completely hide/bypass the website's built-in login screen (#view-auth)
                webView.evaluateJavascript(
                    "var style = document.createElement('style'); " +
                    "style.innerHTML = '#view-auth { display: none !important; }'; " +
                    "document.head.appendChild(style);", null);

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
                    "    function extractSwalText(obj) { " +
                    "        if (!obj) return 'Authentication failed'; " +
                    "        if (typeof obj === 'string') return obj; " +
                    "        if (obj.text) return obj.text; " +
                    "        if (obj.title && !obj.html) return obj.title; " +
                    "        if (obj.html) { " +
                    "            try { " +
                    "                var temp = document.createElement('div'); " +
                    "                temp.innerHTML = obj.html; " +
                    "                var h3 = temp.querySelector('h3'); " +
                    "                var p = temp.querySelector('p'); " +
                    "                var msg = ''; " +
                    "                if (h3) msg += h3.textContent.trim() + ' - '; " +
                    "                if (p) msg += p.textContent.trim(); " +
                    "                if (!msg) msg = temp.textContent.trim(); " +
                    "                return msg || 'Authentication failed'; " +
                    "            } catch(e) { " +
                    "                return 'Authentication failed'; " +
                    "            } " +
                    "        } " +
                    "        return JSON.stringify(obj); " +
                    "    } " +
                    "    function hookSwal() { " +
                    "        if (window.Swal && window.Swal.fire && !window.Swal._hooked) { " +
                    "            window.Swal._hooked = true; " +
                    "            const originalSwalFire = window.Swal.fire; " +
                    "            window.Swal.fire = function() { " +
                    "                let msg = ''; " +
                    "                if (arguments.length > 0) { " +
                    "                    msg = extractSwalText(arguments[0]); " +
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
                    "                    msg = extractSwalText(arguments[0]); " +
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

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                checkAndExtractClientId(url);
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showOfflineScreen();
            }

            @TargetApi(Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    showOfflineScreen();
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                checkAndExtractClientId(url);
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false; // Load in WebView
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    view.getContext().startActivity(intent);
                    return true;
                } catch (Exception e) {
                    return true;
                }
            }

            @TargetApi(Build.VERSION_CODES.N)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                String url = request.getUrl().toString();
                checkAndExtractClientId(url);
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false; // Load in WebView
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    view.getContext().startActivity(intent);
                    return true;
                } catch (Exception e) {
                    return true;
                }
            }
        });

        // Custom WebChromeClient to handle camera permissions, file uploads, and window popups
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                MainActivity.this.runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        request.grant(request.getResources());
                    }
                });
            }

            @SuppressLint("SetJavaScriptEnabled")
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
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

                android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.setAcceptThirdPartyCookies(popupWebView, true);
                }

                String originalUA = popupSettings.getUserAgentString();
                if (originalUA != null) {
                    String cleanUA = originalUA.replace("; wv", "");
                    cleanUA = cleanUA.replaceAll("Version/[0-9.]+\\s?", "");
                    cleanUA = cleanUA + " YaraanFlutterApp";
                    popupSettings.setUserAgentString(cleanUA);
                }

                popupWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                        super.onPageStarted(view, url, favicon);
                        checkAndExtractClientId(url);
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        checkAndExtractClientId(url);
                        return false;
                    }

                    @TargetApi(Build.VERSION_CODES.N)
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                        checkAndExtractClientId(request.getUrl().toString());
                        return false;
                    }
                });

                popupDialog = new Dialog(MainActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                popupDialog.setContentView(popupWebView);
                popupDialog.setCancelable(true);

                popupDialog.setOnCancelListener(dialog -> {
                    if (popupWebView != null) {
                        popupWebView.destroy();
                        popupWebView = null;
                    }
                    popupDialog = null;
                    SharedPreferences prefs1 = getSharedPreferences("YaraanPrefs", MODE_PRIVATE);
                    boolean isLoggedIn = prefs1.getBoolean("is_logged_in", false);
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
                        SharedPreferences prefs1 = getSharedPreferences("YaraanPrefs", MODE_PRIVATE);
                        boolean isLoggedIn = prefs1.getBoolean("is_logged_in", false);
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
        if (requestCode == RC_SIGN_IN) {
            // Re-enable interactive elements
            resetLoginButtons();

            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                String idToken = account.getIdToken();
                if (idToken != null) {
                    // Ensure checkbox is checked on web-side as well
                    webView.evaluateJavascript(
                        "var cb = document.getElementById('privacy-checkbox'); " +
                        "if (cb) { cb.checked = true; cb.dispatchEvent(new Event('change', { bubbles: true })); }", null);

                    // Send Google security ID Token to the WebView
                    JSONObject jsonPayload = new JSONObject();
                    jsonPayload.put("type", "googleLogin");
                    jsonPayload.put("idToken", idToken);

                    String js = "window.postMessage(" + jsonPayload.toString() + ", '*');";
                    webView.evaluateJavascript(js, null);
                } else {
                    Toast.makeText(this, "Failed to retrieve Google token.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Google Sign-In failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }

            Uri[] results = null;

            if (resultCode == RESULT_OK) {
                if (data == null || data.getData() == null) {
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
                    SharedPreferences prefs1 = getSharedPreferences("YaraanPrefs", MODE_PRIVATE);
                    boolean isLoggedIn = prefs1.getBoolean("is_logged_in", false);
                    if (!isLoggedIn) {
                        showNativeLoginScreen();
                        resetLoginButtons();
                    }
                } else if (sectionEmailLogin.getVisibility() == View.VISIBLE) {
                    runOnUiThread(() -> {
                        sectionEmailLogin.setVisibility(View.GONE);
                        sectionInitialLogin.setVisibility(View.VISIBLE);
                    });
                } else if (nativeLoginScreen.getVisibility() == View.VISIBLE) {
                    finish();
                } else if (webView.canGoBack()) {
                    webView.goBack();
                } else {
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
    protected void onResume() {
        super.onResume();
        configureFullScreen();
        if (videoView != null && nativeLoginScreen.getVisibility() == View.VISIBLE) {
            videoView.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null) {
            videoView.pause();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.webkit.CookieManager.getInstance().flush();
        }
    }

    @Override
    protected void onDestroy() {
        unregisterNetworkCallback();
        if (videoView != null) {
            videoView.stopPlayback();
        }
        super.onDestroy();
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
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
            webView.setVisibility(View.INVISIBLE);
            nativeLoginScreen.setVisibility(View.VISIBLE);
            if (videoView != null) {
                videoView.start();
            }
        });
    }

    private void hideNativeLoginScreen() {
        runOnUiThread(() -> {
            nativeLoginScreen.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            if (videoView != null) {
                videoView.pause();
            }
        });
    }

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutRunnable = this::resetLoginButtons;

    private void resetLoginButtons() {
        runOnUiThread(() -> {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            btnNativeLogin.setEnabled(true);
            btnNativeLogin.setText(currentFormMode == FormMode.LOGIN ? "Sign In" : (currentFormMode == FormMode.REGISTER ? "Create Account" : "Send Reset Link"));
            btnNativeGoogle.setEnabled(true);
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
                    Toast.makeText(this, "Permission " + permissions[i] + " denied. Some features might not work properly.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
