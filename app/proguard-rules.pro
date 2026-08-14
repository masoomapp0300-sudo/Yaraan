# ProGuard rules for Google Sign-In, Firebase, and WebView JavascriptInterface

# Keep JavascriptInterface methods
-keepattributes SetJavaScriptEnabled
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Google Play Services & Google Sign-In SDK classes
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class com.google.android.gms.common.api.** { *; }
-keep class com.google.android.gms.tasks.** { *; }

# Keep Firebase Auth classes
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.** { *; }

# Keep generic webview client models if any
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**
