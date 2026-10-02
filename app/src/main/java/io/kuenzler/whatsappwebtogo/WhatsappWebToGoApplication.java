package io.kuenzler.whatsappwebtogo;

import android.app.Application;
import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Initializes application-wide settings before any Activity or WebView is created.
 */
public class WhatsappWebToGoApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Restore the application's saved theme before creating any Activity.
        SharedPreferences preferences = getSharedPreferences(
                getPackageName(),
                Context.MODE_PRIVATE
        );

        boolean darkMode = preferences.getBoolean("darkMode", false);
        applySavedNightMode(darkMode);
    }

    /**
     * Applies the saved application theme using the API recommended for the
     * current Android version.
     *
     * @param darkMode true for dark mode; false for light mode.
     */
    private void applySavedNightMode(boolean darkMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            UiModeManager uiModeManager = getSystemService(UiModeManager.class);

            if (uiModeManager != null) {
                uiModeManager.setApplicationNightMode(
                        darkMode
                                ? UiModeManager.MODE_NIGHT_YES
                                : UiModeManager.MODE_NIGHT_NO
                );
            }

            return;
        }

        AppCompatDelegate.setDefaultNightMode(
                darkMode
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO
        );
    }
}
