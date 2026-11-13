package io.kuenzler.whatsappwebtogo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.Locale;

public class WebviewActivity extends AppCompatActivity {

    private static final String CHROME_TEMPLATE = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%s Safari/537.36";
    private static final String CHROME_VERSION_FALLBACK = "156.0.0.0";
    private static String USER_AGENT = String.format(CHROME_TEMPLATE, CHROME_VERSION_FALLBACK);

    private static final String STORAGE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE; // "android.permission.WRITE_EXTERNAL_STORAGE"
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA; // "android.permission.CAMERA"
    private static final String AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO; // "android.permission.RECORD_AUDIO"
    private static final String[] VIDEO_PERMISSION = {CAMERA_PERMISSION, AUDIO_PERMISSION};

    private static final String WHATSAPP_HOMEPAGE_URL = "https://www.whatsapp.com/";

    private static final String WHATSAPP_WEB_BASE_URL = "web.whatsapp.com";
    private static final String WORLD_ICON = "\uD83C\uDF10";
    private static final String WHATSAPP_WEB_URL = "https://" + WHATSAPP_WEB_BASE_URL
            + "/" + WORLD_ICON + "/"
            + Locale.getDefault().getLanguage();

    private static final int FILECHOOSER_RESULTCODE        = 200;
    private static final int CAMERA_PERMISSION_RESULTCODE  = 201;
    private static final int AUDIO_PERMISSION_RESULTCODE   = 202;
    private static final int VIDEO_PERMISSION_RESULTCODE   = 203;
    private static final int STORAGE_PERMISSION_RESULTCODE = 204;

    public static final String DEBUG_TAG = "WAWEBTOGO";

    private final Activity activity = this;

    private SharedPreferences mSharedPrefs;

    private CustomWebView mWebView;
    private ViewGroup mMainView;

    private View mRadialOverlay;
    private ViewGroup mRadialContainer;
    private FloatingActionButton mFabMenu;
    private View[] mRadialItems;
    private boolean mIsRadialMenuOpen = false;

    private long mLastBackClick = 0;

    private boolean mKeyboardEnabled = false;
    private boolean mDarkMode;
    /**
     * Tracks whether the Activity currently requests fullscreen display.
     */
    private boolean mFullScreen;

    private static final String PREF_DARK_MODE = "darkMode";
    private static final String PREF_FULLSCREEN = "fullScreen";

    private ValueCallback<Uri[]> mUploadMessage;
    private PermissionRequest mCurrentPermissionRequest;
    private String mCurrentDownloadRequest = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mSharedPrefs = this.getSharedPreferences(this.getPackageName(), Context.MODE_PRIVATE);
        mDarkMode = mSharedPrefs.getBoolean(PREF_DARK_MODE, false);
        mFullScreen = mSharedPrefs.getBoolean(PREF_FULLSCREEN, true);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        applyFullscreenMode();

        applySystemBarInsets();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mIsRadialMenuOpen) {
                    closeRadialMenu();
                    return;
                }
                if (System.currentTimeMillis() - mLastBackClick < 4000) {
                    finishAffinity();
                } else {
                    if (mWebView != null) {
                        mWebView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE));
                    }
                    showToast(R.string.navigation_drawer_close_confirm);
                    mLastBackClick = System.currentTimeMillis();
                }
            }
        });

        mFabMenu = findViewById(R.id.fab_menu);
        mRadialOverlay = findViewById(R.id.radial_menu_overlay);
        mRadialContainer = findViewById(R.id.radial_items_container);

        setupRadialMenu();

        if (mFabMenu != null) {
            mFabMenu.setOnClickListener(v -> toggleRadialMenu(!mIsRadialMenuOpen));
            View coordinatorLayout = findViewById(R.id.coordinator_layout);
            if (coordinatorLayout != null) {
                coordinatorLayout.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    int height = bottom - top;
                    if (height > 0) {
                        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mFabMenu.getLayoutParams();
                        int targetBottomMargin = 5 * height / 12;
                        if (lp != null && lp.bottomMargin != targetBottomMargin) {
                            mFabMenu.post(() -> {
                                ViewGroup.MarginLayoutParams currentLp = (ViewGroup.MarginLayoutParams) mFabMenu.getLayoutParams();
                                if (currentLp != null && currentLp.bottomMargin != targetBottomMargin) {
                                    currentLp.bottomMargin = targetBottomMargin;
                                    mFabMenu.setLayoutParams(currentLp);
                                }
                            });
                        }
                    }
                });
            }
        }

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        mMainView = findViewById(R.id.layout);

        // webview stuff

        mWebView = findViewById(R.id.webview);

        mWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                mCurrentDownloadRequest = url;
                if(checkPermission(STORAGE_PERMISSION)) {
                    mWebView.loadUrl(BlobDownloader.getBase64StringFromBlobUrl(url));
                    triggerDownload();
                } else {
                    requestPermission(STORAGE_PERMISSION);
                }
            }
        });
        mWebView.addJavascriptInterface(new BlobDownloader(getApplicationContext()), BlobDownloader.JsInstance);

        mWebView.getSettings().setJavaScriptEnabled(true); //for wa web
        mWebView.getSettings().setAllowContentAccess(true); // for camera
        mWebView.getSettings().setAllowFileAccess(true);
        mWebView.getSettings().setAllowFileAccessFromFileURLs(true);
        mWebView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        mWebView.getSettings().setMediaPlaybackRequiresUserGesture(false); //for audio messages

        mWebView.getSettings().setDomStorageEnabled(true); //for html5 app
        mWebView.getSettings().setDatabaseEnabled(true);
        //mWebView.getSettings().setAppCacheEnabled(false); // deprecated and REMOVED!
        mWebView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);

        mWebView.getSettings().setLoadWithOverviewMode(true);
        mWebView.getSettings().setUseWideViewPort(true);

        mWebView.getSettings().setSupportZoom(true);
        mWebView.getSettings().setBuiltInZoomControls(true);
        mWebView.getSettings().setDisplayZoomControls(false);

        mWebView.getSettings().setSaveFormData(true);
        mWebView.getSettings().setLoadsImagesAutomatically(true);
        mWebView.getSettings().setBlockNetworkImage(false);
        mWebView.getSettings().setBlockNetworkLoads(false);
        mWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        mWebView.getSettings().setNeedInitialFocus(false);
        mWebView.getSettings().setGeolocationEnabled(true);
        mWebView.getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);

        mWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        mWebView.setScrollbarFadingEnabled(true);
        //if (BuildConfig.DEBUG)
        {
            // WebView remote debugging in Windows Chromium via "chrome://inspect/#devices"
            WebView.setWebContentsDebuggingEnabled(true);
        }

        addDarkMode(mWebView);

        mWebView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, Message resultMsg) {
                Toast.makeText(getApplicationContext(), "OnCreateWindow", Toast.LENGTH_LONG).show();
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (request.getResources()[0].equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                    if (ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) == PackageManager.PERMISSION_DENIED
                            && ContextCompat.checkSelfPermission(activity, AUDIO_PERMISSION) == PackageManager.PERMISSION_DENIED) {
                        ActivityCompat.requestPermissions(activity, VIDEO_PERMISSION, VIDEO_PERMISSION_RESULTCODE);
                        mCurrentPermissionRequest = request;
                    } else if (ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) == PackageManager.PERMISSION_DENIED) {
                        ActivityCompat.requestPermissions(activity, new String[]{CAMERA_PERMISSION}, CAMERA_PERMISSION_RESULTCODE);
                        mCurrentPermissionRequest = request;
                    } else if (ContextCompat.checkSelfPermission(activity, AUDIO_PERMISSION) == PackageManager.PERMISSION_DENIED) {
                        ActivityCompat.requestPermissions(activity, new String[]{AUDIO_PERMISSION}, AUDIO_PERMISSION_RESULTCODE);
                        mCurrentPermissionRequest = request;
                    } else {
                        request.grant(request.getResources());
                    }
                } else if (request.getResources()[0].equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    if (ContextCompat.checkSelfPermission(activity, AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        ActivityCompat.requestPermissions(activity, new String[]{AUDIO_PERMISSION}, AUDIO_PERMISSION_RESULTCODE);
                        mCurrentPermissionRequest = request;
                    }
                } else {
                    try {
                        request.grant(request.getResources());
                    } catch (RuntimeException e) {
                        Log.d(DEBUG_TAG, "Granting permissions failed", e);
                    }
                }
            }

            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(DEBUG_TAG, "WebView console message: " + cm.message());
                return super.onConsoleMessage(cm);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                mUploadMessage = filePathCallback;
                Intent chooserIntent = fileChooserParams.createIntent();
                WebviewActivity.this.startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE);
                return true;
            }
        });

        mWebView.setWebViewClient(new WebViewClient() {

            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                setContentSize(view);
                addDarkMode(view);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                setContentSize(view);
                addDarkMode(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.scrollTo(0, 0);
                setContentSize(view);
                addDarkMode(view);
                view.evaluateJavascript(
                        "JSON.stringify({" +
                                "prefersDark: window.matchMedia('(prefers-color-scheme: dark)').matches," +
                                "htmlClass: document.documentElement.className," +
                                "bodyClass: document.body ? document.body.className : null," +
                                "colorScheme: getComputedStyle(document.documentElement).colorScheme," +
                                "background: document.body" +
                                " ? getComputedStyle(document.body).backgroundColor" +
                                " : null" +
                                "})",
                        value -> Log.d(DEBUG_TAG, "Web theme state: " + value)
                );
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                Log.d(DEBUG_TAG, url.toString());

                if (url.toString().equals(WHATSAPP_HOMEPAGE_URL)){
                    // when WhatsApp somehow detects that waweb is running on a phone (e.g. through
                    // the user agent but apparently somehow else), it automatically redirects to the
                    // WHATSAPP_HOMEPAGE_URL. It's highly unlikely that a user wants to visit the
                    // WHATSAPP_HOMEPAGE_URL from within waweb.
                    // -> block the request and reload waweb
                    showToast(R.string.reload_required);
                    loadWhatsapp();
                    return true;
                } else if (url.getHost().equals(WHATSAPP_WEB_BASE_URL)) {
                    // whatsapp web request -> fine
                    return super.shouldOverrideUrlLoading(view, request);
                } else {
                    Intent intent = new Intent(Intent.ACTION_VIEW, url);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        showToast(R.string.no_app_to_open_link);
                    }
                    return true;
                }
            }

            @RequiresApi(api = Build.VERSION_CODES.M)
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                String msg = String.format("Error: %s - %s", error.getErrorCode(), error.getDescription());
                Log.d(DEBUG_TAG, msg);
            }

            public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
                Log.d(DEBUG_TAG, "Unhandled key event: " + event.toString());
            }
        });

        if (savedInstanceState == null) {
            loadWhatsapp();
        } else {
            Log.d(DEBUG_TAG, "savedInstanceState is present");
        }

        // set version of local webview to user agent to avoid outdated agent
        PackageInfo webviewPackageInfo = WebViewCompat.getCurrentWebViewPackage(getApplicationContext());
        if (webviewPackageInfo != null && !webviewPackageInfo.versionName.isEmpty()) {
            USER_AGENT = String.format(CHROME_TEMPLATE, webviewPackageInfo.versionName);
        }

        mWebView.getSettings().setUserAgentString(USER_AGENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWebView != null) {
            mWebView.onResume();
        }

        mKeyboardEnabled = mSharedPrefs.getBoolean("keyboardEnabled", true);

        setKeyboardEnabled(mKeyboardEnabled, false);

        showIntroInfo();
        showVersionInfo();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWebView != null) {
            mWebView.onPause();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            applyFullscreenMode();
        }
    }

    private boolean checkPermission(String permission) {
        return PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(activity, permission);
    }

    private void requestPermission(String permission) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            ActivityCompat.requestPermissions(activity, new String[]{permission}, STORAGE_PERMISSION_RESULTCODE);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case VIDEO_PERMISSION_RESULTCODE:
                if (permissions.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    try {
                        mCurrentPermissionRequest.grant(mCurrentPermissionRequest.getResources());
                    } catch (RuntimeException e) {
                        Log.e(DEBUG_TAG, "Granting permissions failed", e);
                    }
                } else {
                    showSnackbar(R.string.permission_denied_video);
                    mCurrentPermissionRequest.deny();
                }
                break;
            case CAMERA_PERMISSION_RESULTCODE:
            case AUDIO_PERMISSION_RESULTCODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    try {
                        mCurrentPermissionRequest.grant(mCurrentPermissionRequest.getResources());
                    } catch (RuntimeException e) {
                        Log.e(DEBUG_TAG, "Granting permissions failed", e);
                    }
                } else {
                    showSnackbar(R.string.permission_denied_cam +
                            (requestCode == CAMERA_PERMISSION_RESULTCODE ? R.string.camera : R.string.microphone));
                    mCurrentPermissionRequest.deny();
                }
                break;
            case STORAGE_PERMISSION_RESULTCODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    triggerDownload();
                } else {
                    showToast(R.string.permission_denied_download);
                    mCurrentDownloadRequest = null;
                }
                break;
            default:
                Log.d(DEBUG_TAG, "Got permission result with unknown request code " +
                        requestCode + " - " + Arrays.asList(permissions).toString());
        }
        mCurrentPermissionRequest = null;
    }

    private void triggerDownload(){
        if(null != mCurrentDownloadRequest) {
            mWebView.loadUrl(BlobDownloader.getBase64StringFromBlobUrl(mCurrentDownloadRequest));
        }
        mCurrentDownloadRequest = null;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mWebView != null) {
            mWebView.saveState(outState);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (mWebView != null) {
            mWebView.restoreState(savedInstanceState);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case FILECHOOSER_RESULTCODE:
                if (resultCode == RESULT_CANCELED || data.getData() == null) {
                    mUploadMessage.onReceiveValue(null);
                } else {
                    Uri result = data.getData();
                    Uri[] results = new Uri[1];
                    results[0] = result;
                    mUploadMessage.onReceiveValue(results);
                }
                break;
            default:
                Log.d(DEBUG_TAG, "Got activity result with unknown request code " +
                        requestCode + " - " + data.toString());
        }
    }

    private void showPopupDialog(String message) {
        final SpannableString msg = new SpannableString(message);
        Linkify.addLinks(msg, Linkify.WEB_URLS|Linkify.EMAIL_ADDRESSES);

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(R.string.ok_button, null);
        AlertDialog alert = builder.create();
        alert.show();
        TextView messageView = alert.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
            if (mDarkMode) {
                messageView.setTextColor(Color.WHITE);
            }
        }
        if (mDarkMode && alert.getWindow() != null) {
            alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        }
    }

    private void showPopupDialog(int resId) {
       showPopupDialog(getString(resId));
    }

    private void showToast(int resId) {
        showToast(getString(resId));
    }

    private void showToast(String message) {
        this.runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private void showSnackbar(int resId) {
        showSnackbar(getString(resId));
    }

    private void showSnackbar(CharSequence message) {
        this.runOnUiThread(() -> {
            View view = findViewById(android.R.id.content);
            if (view != null) {
                final Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT);
                snackbar.setAction(getString(R.string.dismiss), (View v) -> snackbar.dismiss());
                snackbar.setActionTextColor(Color.parseColor("#075E54"));
                snackbar.show();
            }
        });
    }

    private void toggleKeyboard() {
        setKeyboardEnabled(!mKeyboardEnabled);
    }

    private void setKeyboardEnabled(final boolean enable) {
        setKeyboardEnabled(enable, true);
    }

    private void setKeyboardEnabled(final boolean enable, final boolean showFeedback) {
        mKeyboardEnabled = enable;
        if (mWebView != null) {
            mWebView.setKeyboardEnabled(enable);
        }
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            if (enable) {
                if (mMainView != null && mMainView.getDescendantFocusability() == ViewGroup.FOCUS_BLOCK_DESCENDANTS) {
                    mMainView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                }
                if (showFeedback) {
                    showSnackbar(R.string.unblock_keyboard);
                }
            } else {
                if (mMainView != null) {
                    mMainView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                }
                if (showFeedback) {
                    showSnackbar(R.string.block_keyboard);
                }
                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                } else if (mWebView != null) {
                    inputMethodManager.hideSoftInputFromWindow(mWebView.getWindowToken(), 0);
                } else if (mMainView != null) {
                    inputMethodManager.hideSoftInputFromWindow(mMainView.getWindowToken(), 0);
                }
            }
        }
        if (mWebView != null) {
            mWebView.evaluateJavascript("if (window.__wwtgSetKeyboardBlocked) { window.__wwtgSetKeyboardBlocked(" + (!enable) + "); }", null);
        }
        if (mSharedPrefs != null) {
            mSharedPrefs.edit().putBoolean("keyboardEnabled", enable).apply();
        }
        updateKeyboardMenuState();
    }

    private void updateKeyboardMenuState() {
        FloatingActionButton fabKeyboard = findViewById(R.id.radial_fab_keyboard);
        TextView labelKeyboard = findViewById(R.id.radial_label_keyboard);

        int textRes = mKeyboardEnabled ? R.string.menu_block_keyboard : R.string.menu_unblock_keyboard;
        int iconRes = mKeyboardEnabled ? R.drawable.ic_keyboard_block : R.drawable.ic_keyboard_unblock;

        if (labelKeyboard != null) {
            labelKeyboard.setText(textRes);
        }
        if (fabKeyboard != null) {
            fabKeyboard.setImageResource(iconRes);
            fabKeyboard.setContentDescription(getString(textRes));
        }
    }

    private void setupRadialMenu() {
        if (mRadialOverlay == null || mRadialContainer == null) return;

        mRadialOverlay.setOnClickListener(v -> closeRadialMenu());

        View itemReload = findViewById(R.id.radial_item_reload);
        View itemKeyboard = findViewById(R.id.radial_item_keyboard);
        View itemSettings = findViewById(R.id.radial_item_settings);
        View itemLogout = findViewById(R.id.radial_item_logout);
        View itemAbout = findViewById(R.id.radial_item_about);

        mRadialItems = new View[]{itemReload, itemKeyboard, itemSettings, itemLogout, itemAbout};

        if (itemReload != null) {
            itemReload.setOnClickListener(v -> {
                closeRadialMenu();
                showSnackbar(R.string.reloading);
                loadWhatsapp();
            });
        }
        if (itemKeyboard != null) {
            itemKeyboard.setOnClickListener(v -> {
                closeRadialMenu();
                toggleKeyboard();
            });
        }
        if (itemSettings != null) {
            itemSettings.setOnClickListener(v -> {
                closeRadialMenu();
                //toggleDarkMode();
                showSettingsDialog();
            });
        }
        if (itemLogout != null) {
            itemLogout.setOnClickListener(v -> {
                closeRadialMenu();
                logout();
            });
        }
        if (itemAbout != null) {
            itemAbout.setOnClickListener(v -> {
                closeRadialMenu();
                showAbout();
            });
        }

        applyRadialMenuTheme();
        updateKeyboardMenuState();
    }

    private void toggleRadialMenu(boolean show) {
        if (show) {
            updateKeyboardMenuState();
            mIsRadialMenuOpen = true;
            mRadialOverlay.setVisibility(View.VISIBLE);
            mRadialOverlay.setAlpha(0f);
            mRadialOverlay.animate().alpha(1f).setDuration(220).start();

            float fabHalf = getResources().getDisplayMetrics().density * 20f;
            float cx = (mFabMenu != null) ? (mFabMenu.getX() + mFabMenu.getWidth() / 2f) : fabHalf;
            float cy = (mFabMenu != null) ? (mFabMenu.getY() + mFabMenu.getHeight() / 2f) : fabHalf;

            if (mFabMenu != null) {
                mFabMenu.animate().rotation(135f).setDuration(220).start();
            }

            float radius = getResources().getDimension(R.dimen.radial_menu_radius);
            float startX = cx - fabHalf;
            float startY = cy - fabHalf;

            if (mRadialItems != null) {
                for (int i = 0; i < mRadialItems.length; i++) {
                    View item = mRadialItems[i];
                    if (item == null) continue;
                    double angleRad = Math.toRadians(-60.0 + i * 30.0);
                    float targetX = cx + (float) (radius * Math.cos(angleRad)) - fabHalf;
                    float targetY = cy + (float) (radius * Math.sin(angleRad)) - fabHalf;

                    item.setX(startX);
                    item.setY(startY);
                    item.setPivotX(fabHalf);
                    item.setPivotY(fabHalf);
                    item.setScaleX(0f);
                    item.setScaleY(0f);
                    item.setAlpha(0f);

                    item.animate()
                            .x(targetX)
                            .y(targetY)
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(250)
                            .setInterpolator(new OvershootInterpolator(1.2f))
                            .start();
                }
            }
        } else {
            closeRadialMenu();
        }
    }

    private void closeRadialMenu() {
        if (!mIsRadialMenuOpen) return;
        mIsRadialMenuOpen = false;

        float fabHalf = getResources().getDisplayMetrics().density * 20f;
        float startX = (mFabMenu != null) ? (mFabMenu.getX() + mFabMenu.getWidth() / 2f - fabHalf) : 0f;
        float startY = (mFabMenu != null) ? (mFabMenu.getY() + mFabMenu.getHeight() / 2f - fabHalf) : 0f;

        if (mRadialItems != null) {
            for (View item : mRadialItems) {
                if (item == null) continue;
                item.animate()
                        .x(startX)
                        .y(startY)
                        .scaleX(0f)
                        .scaleY(0f)
                        .alpha(0f)
                        .setDuration(180)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
        }

        if (mRadialOverlay != null) {
            mRadialOverlay.animate()
                    .alpha(0f)
                    .setDuration(180)
                    .withEndAction(() -> mRadialOverlay.setVisibility(View.GONE))
                    .start();
        }

        if (mFabMenu != null) {
            mFabMenu.animate().rotation(0f).setDuration(180).start();
        }
    }

    private void applyRadialMenuTheme() {
        if (mRadialItems == null) return;

        int fabBgColor = ContextCompat.getColor(this, mDarkMode ? R.color.radial_fab_bg_dark : R.color.radial_fab_bg_light);
        int iconTint = ContextCompat.getColor(this, mDarkMode ? R.color.radial_icon_tint_dark : R.color.radial_icon_tint_light);
        int labelBgRes = mDarkMode ? R.drawable.radial_label_background_dark : R.drawable.radial_label_background_light;
        int textColor = ContextCompat.getColor(this, mDarkMode ? R.color.radial_text_dark : R.color.radial_text_light);

        int[] fabIds = {
                R.id.radial_fab_reload,
                R.id.radial_fab_keyboard,
                R.id.radial_fab_settings,
                R.id.radial_fab_logout,
                R.id.radial_fab_about
        };
        int[] labelIds = {
                R.id.radial_label_reload,
                R.id.radial_label_keyboard,
                R.id.radial_label_settings,
                R.id.radial_label_logout,
                R.id.radial_label_about
        };

        for (int i = 0; i < fabIds.length; i++) {
            FloatingActionButton fab = findViewById(fabIds[i]);
            if (fab != null) {
                fab.setBackgroundTintList(ColorStateList.valueOf(fabBgColor));
                fab.setImageTintList(ColorStateList.valueOf(iconTint));
            }
            TextView label = findViewById(labelIds[i]);
            if (label != null) {
                label.setBackgroundResource(labelBgRes);
                label.setTextColor(textColor);
            }
        }
    }

    /**
     * Applies and persists the application's selected theme to both the Android
     * application UI and the currently loaded WebView content.
     *
     * <p>The WebView darkening configuration must be updated explicitly because
     * changing the Android application night mode does not reliably restyle an
     * already-loaded web document.</p>
     */
    private void applyDarkMode() {
        Log.d(DEBUG_TAG, "Dark Mode Enabled: " + mDarkMode);

        /*
         * Android 12/API 31 and newer support application-level night mode
         * directly through UiModeManager.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            UiModeManager uiModeManager = getSystemService(UiModeManager.class);
            if (uiModeManager == null) {
                Log.e(DEBUG_TAG, "UiModeManager is unavailable.");
                return;
            }

            uiModeManager.setApplicationNightMode(
                    mDarkMode
                            ? UiModeManager.MODE_NIGHT_YES
                            : UiModeManager.MODE_NIGHT_NO
            );
        } else {
            /*
             * Android 11/API 30 and older use AppCompatDelegate. This can recreate
             * the Activity so that resources for the selected theme are reloaded.
             */
            AppCompatDelegate.setDefaultNightMode(
                    mDarkMode
                            ? AppCompatDelegate.MODE_NIGHT_YES
                            : AppCompatDelegate.MODE_NIGHT_NO
            );
        }

        /*
         * Apply the new mode explicitly to the existing WebView. Changing the
         * application night mode alone does not update WebView darkening settings.
         */
        if (mWebView != null) {
            addDarkMode(mWebView);
        }

        // Update the native radial menu without waiting for Activity recreation.
        applyRadialMenuTheme();
    }

    /**
     * Applies the status-bar and navigation-bar appearance for the currently
     * selected application theme.
     *
     * <p>On edge-to-edge Android versions, the status bar itself is transparent.
     * Therefore, the Activity root view must draw the desired color behind the
     * status-bar icons.</p>
     */
    private void applySystemBarTheme() {
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(
                        getWindow(),
                        getWindow().getDecorView()
                );

        /*
         * Resolve the desired system-bar background color from the setting stored
         * in mDarkMode instead of relying on Configuration.UI_MODE_NIGHT_MASK.
         */
        int color = mDarkMode
                ? Color.BLACK
                : Color.WHITE;

        /*
         * Retain these calls for Android versions and device configurations where
         * opaque system-bar colors are still supported.
         */
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);

        /*
         * With edge-to-edge, the status bar is transparent. The visible background
         * is consequently supplied by the Activity content drawn underneath it.
         *
         * applySystemBarInsets() adds top padding to this root view, exposing the
         * root background in the status-bar region.
         */
        ViewGroup contentContainer = findViewById(android.R.id.content);
        if (contentContainer != null && contentContainer.getChildCount() > 0) {
            View rootView = contentContainer.getChildAt(0);
            rootView.setBackgroundColor(color);
        } else {
            Log.e(
                    DEBUG_TAG,
                    "Cannot update system-bar background: root content view is missing."
            );
        }

        /*
         * Light-status-bars=true means dark icons intended for a light background.
         * Dark mode therefore needs false so that Android displays light icons.
         */
        insetsController.setAppearanceLightStatusBars(!mDarkMode);
        insetsController.setAppearanceLightNavigationBars(!mDarkMode);

    }

    /**
     * Keeps the Activity content outside the status-bar area while supporting
     * Android's enforced edge-to-edge window behavior.
     *
     * <p>The listener applies the status-bar inset as top padding to the root
     * content view. The original padding is preserved so repeated inset dispatches
     * do not accumulate additional padding.</p>
     */
    private void applySystemBarInsets() {
        /*
         * android.R.id.content is the framework container into which the Activity's
         * layout is placed. Its first child is the actual root view declared by the
         * Activity layout.
         */
        ViewGroup contentContainer =
                findViewById(android.R.id.content);

        if (
                contentContainer == null ||
                        contentContainer.getChildCount() == 0
        ) {
            Log.e(
                    DEBUG_TAG,
                    "Cannot apply system-bar insets: root content view is missing."
            );
            return;
        }

        View rootView = contentContainer.getChildAt(0);

        /*
         * Capture the layout's original padding exactly once. Insets can be
         * dispatched multiple times after rotation, theme changes, keyboard
         * changes, or system-bar visibility updates.
         */
        int initialPaddingLeft = rootView.getPaddingLeft();
        int initialPaddingTop = rootView.getPaddingTop();
        int initialPaddingRight = rootView.getPaddingRight();
        int initialPaddingBottom = rootView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(
                rootView,
                (view, windowInsets) -> {
                    boolean statusBarVisible =
                            windowInsets.isVisible(
                                    WindowInsetsCompat.Type.statusBars()
                            );

                    Insets topInsets = windowInsets.getInsets(
                            WindowInsetsCompat.Type.statusBars()
                                    | WindowInsetsCompat.Type.displayCutout()
                    );

                    /*
                     * Do not reserve status-bar space while fullscreen is active.
                     * Continue respecting a physical display cutout if necessary.
                     */
                    int topPadding = mFullScreen
                            ? 0
                            : topInsets.top;

                    view.setPadding(
                            initialPaddingLeft,
                            initialPaddingTop + topPadding,
                            initialPaddingRight,
                            initialPaddingBottom
                    );

                    Log.d(
                            DEBUG_TAG,
                            "Applied top system-bar inset: "
                                    + topPadding
                                    + " px"
                    );
                    return windowInsets;
                }
        );

        /*
         * Request an immediate inset dispatch when the Activity is already
         * attached to a window.
         */
        ViewCompat.requestApplyInsets(rootView);
    }

    /**
     * Applies the selected status-bar visibility.
     */
    private void applyFullscreenMode() {
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(
                        getWindow(),
                        getWindow().getDecorView()
                );

        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );

        if (mFullScreen) {
            // Hide only the status bar.
            insetsController.hide(WindowInsetsCompat.Type.statusBars());
            // Hide both the status bar and navigation bar
            //insetsController.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            // Restore the status bar.
            insetsController.show(WindowInsetsCompat.Type.statusBars());
            // Restore both the status bar and navigation bar
            //insetsController.show(WindowInsetsCompat.Type.systemBars());
        }

        /*
         * Request a fresh inset calculation because status-bar visibility changed.
         */
        ViewCompat.requestApplyInsets(
                getWindow().getDecorView()
        );
    }

    /**
     * Shows the application settings dialog.
     */
    private void showSettingsDialog() {

        View dialogView = getLayoutInflater().inflate(R.layout.settings_dialog, null);

        Switch darkModeSwitch = dialogView.findViewById(R.id.switchDarkMode);
        Switch fullscreenSwitch = dialogView.findViewById(R.id.switchFullscreen);

        darkModeSwitch.setChecked(mSharedPrefs.getBoolean(PREF_DARK_MODE, false));

        fullscreenSwitch.setChecked(mSharedPrefs.getBoolean(PREF_FULLSCREEN, true));

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> {
                    boolean darkMode = darkModeSwitch.isChecked();
                    boolean fullscreen = fullscreenSwitch.isChecked();

                    boolean darkModeChanged = darkMode != mDarkMode;
                    boolean fullscreenChanged = fullscreen != mFullScreen;

                    if (!darkModeChanged && !fullscreenChanged) {
                        return;
                    }

                    boolean saved = mSharedPrefs.edit()
                            .putBoolean(PREF_DARK_MODE, darkMode)
                            .putBoolean(PREF_FULLSCREEN, fullscreen)
                            .commit();

                    if (!saved) {
                        Log.e(DEBUG_TAG, "Failed to save preferences.");
                        return;
                    }

                    /*
                     * Store the complete new state before applying either setting. This ensures
                     * that all update methods observe consistent dark-mode and fullscreen values.
                     */
                    mDarkMode = darkMode;
                    mFullScreen = fullscreen;

                    if (darkModeChanged) {
                        applyDarkMode();
                    }

                    if (fullscreenChanged) {
                        applyFullscreenMode();
                    }

                    /*
                     * Reapply the final system-bar theme after fullscreen visibility and insets
                     * have been updated.
                     */
                    applySystemBarTheme();
                })
                .setNegativeButton("Cancel",null)
                .show();
    }

    private void showVersionInfo() {
        int lastShownVersionCode = 0;
        int currentVersionCode = 0;
        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            currentVersionCode = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(DEBUG_TAG, "Error checking versioncode", e);
            return;
        }
        try {
            lastShownVersionCode = mSharedPrefs.getInt("lastShownVersionCode", 0);
        } catch (Exception ignored) {
        }
        if (lastShownVersionCode == 0) {
            mSharedPrefs.edit().putInt("lastShownVersionCode", currentVersionCode).apply();
            return;
        }
        if (lastShownVersionCode < currentVersionCode) {
            showPopupDialog(R.string.versionInfoText);
        } else {
            return;
        }
        mSharedPrefs.edit().putInt("lastShownVersionCode", currentVersionCode).apply();
    }

    private void showIntroInfo() {
        if (!mSharedPrefs.getBoolean("introShown", false)) {
            showPopupDialog(R.string.introInfoText);
        } else {
            return;
        }
        mSharedPrefs.edit().putBoolean("introShown", true).apply();
    }

    private void showAbout() {
        showPopupDialog(R.string.aboutText);
    }

    private void loadWhatsapp() {
        mWebView.getSettings().setUserAgentString(USER_AGENT);
        mWebView.loadUrl(WHATSAPP_WEB_URL);
    }

    @SuppressWarnings("deprecation")
    public void addDarkMode(final WebView mWebView) {
        // Keep the system bars synchronized with the current application theme.
        applySystemBarTheme();
        if (mDarkMode) {
            mWebView.setBackgroundColor(Color.parseColor("#111b21"));

            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(mWebView.getSettings(), true);
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(mWebView.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                    WebSettingsCompat.setForceDarkStrategy(mWebView.getSettings(), WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING);
                }
            }
        } else {
            mWebView.setBackgroundColor(Color.WHITE);
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(mWebView.getSettings(), false);
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(mWebView.getSettings(), WebSettingsCompat.FORCE_DARK_OFF);
            }
        }
        setContentSize(mWebView);
    }

    public void setContentSize(final WebView mWebView) {

        boolean isTablet = getResources().getBoolean(R.bool.isTablet);

        if (!isTablet) {
            //ToDo: Scroll to the right. See `focusChatTextInput(e)-Method of bootstrap_qr.e002978cf4688a3eaf75.js:formatted  line 67087
            mWebView.loadUrl("javascript:( function() {"
                    + "  try { "
                    + "    var css = '.two > div:nth-child(4){flex: 1 0 100vmin;}.two{overflow:visible}',"
                    + "        head = document.head || document.getElementsByTagName('head')[0],"
                    + "        style = document.createElement('style');"
                    + "    head.appendChild(style);"
                    + "    style.type = 'text/css';"
                    + "    if (style.styleSheet) {"
                    + "        style.styleSheet.cssText = css;"
                    + "    } else {"
                    + "        style.appendChild(document.createTextNode(css));"
                    + "    }"
                    + "  } catch(err) { }"
                    + "} )()"
            );
        }

        String cleanMetaAI = JsUtils.loadJsAsset(this, "cleanMetaAI.js");
        mWebView.evaluateJavascript(cleanMetaAI, null);

        String focusPatch = JsUtils.loadJsAsset(this, "focusPatch.js");
        // ensure focusPatch is defined (loaded) before calling (installing) the patch
        mWebView.evaluateJavascript(focusPatch, value -> {
            mWebView.evaluateJavascript(
                    "installFocusPatch(" + mKeyboardEnabled + ");",
                    null
            );
        });

        // Load the attachment-menu positioning patch.
        String attachmentMenuPositionPatch = JsUtils.loadJsAsset(this,"attachmentMenuPositionPatch.js");
        mWebView.evaluateJavascript(attachmentMenuPositionPatch, null);

    }

    public void logout(){
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_message)
                .setPositiveButton(R.string.yes_button, (dialog, which) -> {
                    showSnackbar(R.string.logging_out);
                    mWebView.loadUrl("javascript:localStorage.clear()");
                    WebStorage.getInstance().deleteAllData();
                    loadWhatsapp();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.no_button, (dialog, which) -> dialog.dismiss())
                .show();
    }

}
