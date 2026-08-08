package com.umar.yaraan.chate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.OnBackPressedCallback;
import com.google.firebase.auth.UserProfileChangeRequest;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

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
    private static final int GOOGLE_SIGN_IN_REQUEST_CODE = 1003;

    private FrameLayout webViewContainer;
    private WebView webView;
    private RelativeLayout splashScreen;
    private LinearLayout offlineScreen;
    private Button btnRetry;

    // Native Login Elements
    private RelativeLayout loginScreenContainer;
    private VideoView videoView;
    private EditText etEmail, etPassword;
    private Button btnNativeLogin;
    private TextView btnForgotPassword, btnToggleSignup, tvToggleInfo;
    private LinearLayout btnGoogleSignin;
    private RelativeLayout glassLoadingOverlay;

    private ImageButton btnEmailToggle;
    private LinearLayout credentialsCard;
    private EditText etName;
    private TextView tvFormTitle;

    private enum FormMode {
        LOGIN,
        SIGN_UP,
        FORGOT_PASSWORD
    }
    private FormMode currentFormMode = FormMode.LOGIN;

    // Firebase & Auth Client variables
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    // Tokens to inject
    private String currentIdToken = null;
    private long currentExpirationTime = 0;

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

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

        // Configure Google Sign-In Options (Using Web Client ID from google-services.json)
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("740464208491-r63hohlm9o2lvc40f8gffitrbe6pceq8.apps.googleusercontent.com")
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

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

        // Initialize Login UI Components
        loginScreenContainer = findViewById(R.id.login_screen_container);
        videoView = findViewById(R.id.video_view);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnNativeLogin = findViewById(R.id.btn_native_login);
        btnForgotPassword = findViewById(R.id.btn_forgot_password);
        btnToggleSignup = findViewById(R.id.btn_toggle_signup);
        tvToggleInfo = findViewById(R.id.tv_toggle_info);
        btnGoogleSignin = findViewById(R.id.btn_google_signin);
        glassLoadingOverlay = findViewById(R.id.glass_loading_overlay);

        btnEmailToggle = findViewById(R.id.btn_email_toggle);
        credentialsCard = findViewById(R.id.credentials_card);
        etName = findViewById(R.id.et_name);
        tvFormTitle = findViewById(R.id.tv_form_title);

        // Apply Realtime Glassmorphic Blur to Loading card on Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            View glassCard = findViewById(R.id.glass_loading_card);
            if (glassCard != null) {
                glassCard.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(12f, 12f, android.graphics.Shader.TileMode.CLAMP));
            }
        }

        // 2. Setup WebView and Settings
        setupWebView();

        // 3. Setup Back Button Callback
        setupBackButton();

        // 4. Setup Click Listeners for Login UI
        setupLoginListeners();

        // 5. Request necessary runtime permissions
        checkAndRequestPermissions();

        // Register dynamic network callback for auto-reconnect
        registerNetworkCallback();

        // 6. Check Auth and start flow
        checkAuthAndStart();
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
            setStatusBarLightIcons(false); // Light icons on transparent dark background by default
        }
    }

    private void setStatusBarLightIcons(boolean isLight) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();
            WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
            controller.setAppearanceLightStatusBars(isLight);
        }
    }

    private void playBackgroundVideo() {
        if (videoView != null) {
            runOnUiThread(() -> {
                try {
                    Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.login_bg);
                    videoView.setVideoURI(videoUri);
                    videoView.setOnPreparedListener(mp -> {
                        mp.setLooping(true);
                        videoView.start();
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (loginScreenContainer != null && loginScreenContainer.getVisibility() == View.VISIBLE) {
            playBackgroundVideo();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    private void checkAuthAndStart() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // User is authenticated. Fetch token and load the website.
            fetchTokenAndLoadWeb(user);
        } else {
            // Not authenticated. Show Login UI.
            runOnUiThread(() -> {
                splashScreen.setVisibility(View.GONE);
                loginScreenContainer.setVisibility(View.VISIBLE);
                setStatusBarLightIcons(false); // Light icons over dark video background
                playBackgroundVideo();
            });
        }
    }

    private void fetchTokenAndLoadWeb(FirebaseUser user) {
        runOnUiThread(() -> {
            glassLoadingOverlay.setVisibility(View.VISIBLE);
            setStatusBarLightIcons(false); // keep status bar elegant during loading
        });

        user.getIdToken(true).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                currentIdToken = task.getResult().getToken();
                currentExpirationTime = task.getResult().getExpirationTimestamp() * 1000;

                runOnUiThread(() -> {
                    loginScreenContainer.setVisibility(View.GONE);
                    if (videoView != null && videoView.isPlaying()) {
                        videoView.stopPlayback();
                    }
                    attemptLoadUrl();
                });
            } else {
                runOnUiThread(() -> {
                    glassLoadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Failed to retrieve secure session token. Please sign in again.", Toast.LENGTH_LONG).show();
                    mAuth.signOut();
                    loginScreenContainer.setVisibility(View.VISIBLE);
                    setStatusBarLightIcons(false);
                    playBackgroundVideo();
                });
            }
        });
    }

    private void setFormMode(FormMode mode) {
        currentFormMode = mode;
        if (mode == FormMode.LOGIN) {
            tvFormTitle.setText("LOG IN");
            etName.setVisibility(View.GONE);
            etEmail.setVisibility(View.VISIBLE);
            etPassword.setVisibility(View.VISIBLE);
            btnForgotPassword.setVisibility(View.VISIBLE);
            btnNativeLogin.setText("Log In");
            tvToggleInfo.setText("Don't have an account? ");
            btnToggleSignup.setText("Create Account");
        } else if (mode == FormMode.SIGN_UP) {
            tvFormTitle.setText("CREATE ACCOUNT");
            etName.setVisibility(View.VISIBLE);
            etEmail.setVisibility(View.VISIBLE);
            etPassword.setVisibility(View.VISIBLE);
            btnForgotPassword.setVisibility(View.GONE);
            btnNativeLogin.setText("Create Account");
            tvToggleInfo.setText("Already have an account? ");
            btnToggleSignup.setText("Log In");
        } else if (mode == FormMode.FORGOT_PASSWORD) {
            tvFormTitle.setText("RESET PASSWORD");
            etName.setVisibility(View.GONE);
            etEmail.setVisibility(View.VISIBLE);
            etPassword.setVisibility(View.GONE);
            btnForgotPassword.setVisibility(View.GONE);
            btnNativeLogin.setText("Send Reset Link");
            tvToggleInfo.setText("Remembered your password? ");
            btnToggleSignup.setText("Log In");
        }
    }

    private void setupLoginListeners() {
        // Toggle Sign Up Mode Footer
        btnToggleSignup.setOnClickListener(v -> {
            if (currentFormMode == FormMode.LOGIN) {
                setFormMode(FormMode.SIGN_UP);
            } else {
                setFormMode(FormMode.LOGIN);
            }
        });

        // Circular Email Toggle Button Listener
        btnEmailToggle.setOnClickListener(v -> {
            if (credentialsCard.getVisibility() == View.VISIBLE) {
                credentialsCard.setVisibility(View.GONE);
            } else {
                credentialsCard.setVisibility(View.VISIBLE);
                setFormMode(FormMode.LOGIN);
            }
        });

        // Forgot Password Click inside Card
        btnForgotPassword.setOnClickListener(v -> {
            setFormMode(FormMode.FORGOT_PASSWORD);
        });

        // Native Login Button Handler
        btnNativeLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();

            if (currentFormMode == FormMode.FORGOT_PASSWORD) {
                if (email.isEmpty()) {
                    Toast.makeText(this, "Please enter your email address.", Toast.LENGTH_SHORT).show();
                    return;
                }
                runOnUiThread(() -> glassLoadingOverlay.setVisibility(View.VISIBLE));
                mAuth.sendPasswordResetEmail(email)
                        .addOnCompleteListener(task -> {
                            runOnUiThread(() -> glassLoadingOverlay.setVisibility(View.GONE));
                            if (task.isSuccessful()) {
                                Toast.makeText(MainActivity.this, "Password reset email sent successfully.", Toast.LENGTH_SHORT).show();
                                setFormMode(FormMode.LOGIN);
                            } else {
                                Toast.makeText(MainActivity.this, "Failed to send reset email: " +
                                        (task.getException() != null ? task.getException().getMessage() : "Unknown Error"),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                return;
            }

            String password = etPassword.getText().toString().trim();
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all email and password fields.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
                return;
            }

            runOnUiThread(() -> glassLoadingOverlay.setVisibility(View.VISIBLE));

            if (currentFormMode == FormMode.SIGN_UP) {
                String name = etName.getText().toString().trim();
                if (name.isEmpty()) {
                    runOnUiThread(() -> glassLoadingOverlay.setVisibility(View.GONE));
                    Toast.makeText(this, "Please enter your name.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Firebase Create User
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, task -> {
                            if (task.isSuccessful()) {
                                FirebaseUser user = mAuth.getCurrentUser();
                                if (user != null) {
                                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                            .setDisplayName(name)
                                            .build();
                                    user.updateProfile(profileUpdates).addOnCompleteListener(profileTask -> {
                                        fetchTokenAndLoadWeb(user);
                                    });
                                }
                            } else {
                                runOnUiThread(() -> glassLoadingOverlay.setVisibility(View.GONE));
                                Toast.makeText(MainActivity.this, "Registration failed: " +
                                        (task.getException() != null ? task.getException().getMessage() : "Unknown Error"),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
            } else {
                // Firebase Login
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, task -> {
                            if (task.isSuccessful()) {
                                FirebaseUser user = mAuth.getCurrentUser();
                                if (user != null) {
                                    fetchTokenAndLoadWeb(user);
                                }
                            } else {
                                runOnUiThread(() -> glassLoadingOverlay.setVisibility(View.GONE));
                                Toast.makeText(MainActivity.this, "Authentication failed: " +
                                        (task.getException() != null ? task.getException().getMessage() : "Invalid credentials"),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
            }
        });

        // Google Sign-In Click
        btnGoogleSignin.setOnClickListener(v -> {
            runOnUiThread(() -> glassLoadingOverlay.setVisibility(View.VISIBLE));
            mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, GOOGLE_SIGN_IN_REQUEST_CODE);
            });
        });

        // Retry Connection click handler
        btnRetry.setOnClickListener(v -> attemptLoadUrl());
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

        // Dynamically clean User Agent to bypass Google's disallowed_useragent checks in WebViews
        String originalUserAgent = settings.getUserAgentString();
        if (originalUserAgent != null) {
            String cleanUserAgent = originalUserAgent.replace("; wv", "");
            cleanUserAgent = cleanUserAgent.replaceAll("Version/\\d+\\.\\d+\\s?", "");
            settings.setUserAgentString(cleanUserAgent);
        }

        // Custom WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // Inject secure Session Bridge if logged in
                if (mAuth.getCurrentUser() != null && currentIdToken != null) {
                    String authValueJson = buildFirebaseStoreValueJson(mAuth.getCurrentUser(), currentIdToken, currentExpirationTime);
                    String apiKey = "AIzaSyA_06bFtGC54BqB1OoGBU4nAyPABIWsGow";
                    String authKey = "firebase:authUser:" + apiKey + ":[DEFAULT]";

                    String jsInjection = "javascript:(function() {"
                            + "var authKey = '" + authKey + "';"
                            + "var authValue = " + authValueJson + ";"
                            + "try {"
                                + "localStorage.setItem(authKey, JSON.stringify(authValue));"
                                + "console.log('Injected auth token to LocalStorage');"
                            + "} catch(e) { console.error('Error in LocalStorage injection', e); }"
                            + "try {"
                                + "var indexedDB = window.indexedDB || window.mozIndexedDB || window.webkitIndexedDB || window.msIndexedDB;"
                                + "if (indexedDB) {"
                                    + "var request = indexedDB.open('firebaseLocalStorageDb', 1);"
                                    + "request.onupgradeneeded = function(e) {"
                                        + "var db = e.target.result;"
                                        + "if (!db.objectStoreNames.contains('firebaseLocalStorage')) {"
                                            + "db.createObjectStore('firebaseLocalStorage', { keyPath: 'fbase_key' });"
                                        + "}"
                                    + "};"
                                    + "request.onsuccess = function(e) {"
                                        + "var db = e.target.result;"
                                        + "var transaction = db.transaction(['firebaseLocalStorage'], 'readwrite');"
                                        + "var store = transaction.objectStore('firebaseLocalStorage');"
                                        + "var record = {"
                                            + "fbase_key: authKey,"
                                            + "value: authValue"
                                        + "};"
                                        + "var putReq = store.put(record);"
                                        + "putReq.onsuccess = function() {"
                                            + "console.log('Injected auth token to IndexedDB');"
                                        + "};"
                                    + "};"
                                + "}"
                            + "} catch(e) { console.error('Error in IndexedDB injection', e); }"
                            + "})();";

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        view.evaluateJavascript(jsInjection, null);
                    } else {
                        view.loadUrl(jsInjection);
                    }
                }

                // Smoothly dismiss transitions and reveal WebView after write-back completes
                view.postDelayed(() -> {
                    runOnUiThread(() -> {
                        // Change status bar icons to adapt to light web content (dark icons on light bg)
                        setStatusBarLightIcons(true);

                        if (glassLoadingOverlay.getVisibility() == View.VISIBLE) {
                            AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
                            fadeOut.setDuration(400);
                            fadeOut.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                                @Override
                                public void onAnimationStart(android.view.animation.Animation animation) {}
                                @Override
                                public void onAnimationEnd(android.view.animation.Animation animation) {
                                    glassLoadingOverlay.setVisibility(View.GONE);
                                    webView.setVisibility(View.VISIBLE);
                                }
                                @Override
                                public void onAnimationRepeat(android.view.animation.Animation animation) {}
                            });
                            glassLoadingOverlay.startAnimation(fadeOut);
                        }
                        if (splashScreen.getVisibility() == View.VISIBLE) {
                            AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
                            fadeOut.setDuration(400);
                            fadeOut.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                                @Override
                                public void onAnimationStart(android.view.animation.Animation animation) {}
                                @Override
                                public void onAnimationEnd(android.view.animation.Animation animation) {
                                    splashScreen.setVisibility(View.GONE);
                                    webView.setVisibility(View.VISIBLE);
                                }
                                @Override
                                public void onAnimationRepeat(android.view.animation.Animation animation) {}
                            });
                            splashScreen.startAnimation(fadeOut);
                        }
                    });
                }, 1000);
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
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false;
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
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false;
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

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(view);
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

    private String buildFirebaseStoreValueJson(FirebaseUser user, String idToken, long expirationTime) {
        String uid = user.getUid() != null ? user.getUid() : "";
        String email = user.getEmail() != null ? user.getEmail() : "";
        boolean emailVerified = user.isEmailVerified();
        String displayName = user.getDisplayName() != null ? user.getDisplayName() : "";
        String photoUrl = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "";
        String apiKey = "AIzaSyA_06bFtGC54BqB1OoGBU4nAyPABIWsGow";

        return "{"
                + "\"uid\":\"" + escapeJsString(uid) + "\","
                + "\"email\":\"" + escapeJsString(email) + "\","
                + "\"emailVerified\":" + emailVerified + ","
                + "\"displayName\":\"" + escapeJsString(displayName) + "\","
                + "\"photoURL\":\"" + escapeJsString(photoUrl) + "\","
                + "\"isAnonymous\":false,"
                + "\"tenantId\":null,"
                + "\"providerData\":[],"
                + "\"stsTokenManager\":{"
                + "\"apiKey\":\"" + apiKey + "\","
                + "\"refreshToken\":\"" + escapeJsString(idToken) + "\","
                + "\"accessToken\":\"" + escapeJsString(idToken) + "\","
                + "\"expirationTime\":" + expirationTime
                + "},"
                + "\"apiKey\":\"" + apiKey + "\","
                + "\"appName\":\"[DEFAULT]\""
                + "}";
    }

    private String escapeJsString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
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
        } else if (requestCode == GOOGLE_SIGN_IN_REQUEST_CODE) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    firebaseAuthWithGoogle(account);
                } else {
                    runOnUiThread(() -> glassLoadingOverlay.setVisibility(View.GONE));
                    Toast.makeText(this, "Google Sign-In returned null account.", Toast.LENGTH_SHORT).show();
                }
            } catch (ApiException e) {
                runOnUiThread(() -> glassLoadingOverlay.setVisibility(View.GONE));
                Toast.makeText(this, "Google Sign-In failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            fetchTokenAndLoadWeb(user);
                        }
                    } else {
                        runOnUiThread(() -> glassLoadingOverlay.setVisibility(View.GONE));
                        Toast.makeText(MainActivity.this, "Firebase login with Google failed: " +
                                (task.getException() != null ? task.getException().getMessage() : "Unknown"),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setupBackButton() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
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
        loginScreenContainer.setVisibility(View.GONE);
        glassLoadingOverlay.setVisibility(View.GONE);
        offlineScreen.setVisibility(View.VISIBLE);
    }

    private void hideOfflineScreen() {
        offlineScreen.setVisibility(View.GONE);
        if (splashScreen.getVisibility() != View.VISIBLE && loginScreenContainer.getVisibility() != View.VISIBLE && glassLoadingOverlay.getVisibility() != View.VISIBLE) {
            webView.setVisibility(View.VISIBLE);
        }
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