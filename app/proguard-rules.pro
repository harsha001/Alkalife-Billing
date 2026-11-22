# Keep WebView JavaScript interfaces (none used, but safe default)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
