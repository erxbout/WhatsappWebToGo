package io.kuenzler.whatsappwebtogo;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebView;

public class CustomWebView extends WebView {
    private boolean mKeyboardEnabled = true;

    public CustomWebView(Context context) {
        super(context);
    }

    public CustomWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setKeyboardEnabled(boolean enabled) {
        this.mKeyboardEnabled = enabled;
    }

    public boolean isKeyboardEnabled() {
        return mKeyboardEnabled;
    }

    @Override
    public boolean onCheckIsTextEditor() {
        if (!mKeyboardEnabled) {
            return false;
        }
        return super.onCheckIsTextEditor();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (!mKeyboardEnabled) {
            return null;
        }
        return super.onCreateInputConnection(outAttrs);
    }
}
