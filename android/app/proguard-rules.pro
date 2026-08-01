# WebView JS bridge objects need their public methods kept when minifying release builds.
-keepclassmembers class online.idleworld.pokegrid.web.* {
    @android.webkit.JavascriptInterface <methods>;
}
