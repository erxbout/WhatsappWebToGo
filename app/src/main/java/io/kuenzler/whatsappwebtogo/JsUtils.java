package io.kuenzler.whatsappwebtogo;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/// Utility functions for JavaScript generation and escaping.
/// Usage:
///     String js = JsUtils.loadJsAsset(this, "javascript.js");
///     String escapedString = JsUtils.escapeForJsString("Hello \"world\"\n");
///     String escapedTemplate = JsUtils.escapeForJsTemplateLiteral("Price: $10");
/// or in a Fragment:
///     String js = JsUtils.loadJsAsset(requireContext(), "cleanMetaAI.js");
public final class JsUtils {

    private JsUtils() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Loads a JavaScript file from app/src/main/assets/js/.
     *
     * @param context Android context
     * @param fileName JavaScript file name
     * @return file contents
     */
    public static String loadJsAsset(Context context, String fileName) {
        String assetPath = "js/" + fileName;

        try (InputStream stream = context.getAssets().open(assetPath)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = stream.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }

            //noinspection CharsetObjectCanBeUsed
            return output.toString(StandardCharsets.UTF_8.name());

        } catch (IOException e) {
            throw new RuntimeException("Failed to read asset: " + assetPath, e);
        }
    }

    /**
     * Escapes a string so it can be safely embedded inside a JavaScript
     * double-quoted string.
     */
    @SuppressWarnings("unused")
    public static String escapeForJsString(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Escapes a string for use in a JavaScript template literal.
     */
    @SuppressWarnings("unused")
    public static String escapeForJsTemplateLiteral(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("$", "\\$");
    }

}