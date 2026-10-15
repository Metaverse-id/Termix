package com.csoftware.termix.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ANSI terminal escape codes and formats output with Android Spannable color spans.
 */
public class AnsiColorFilter {

    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[([0-9;]*)m");

    // Standard dark-theme terminal palette
    private static final int COLOR_DEFAULT = Color.parseColor("#E6EDF3");
    private static final int COLOR_BLACK   = Color.parseColor("#6E7681");
    private static final int COLOR_RED     = Color.parseColor("#FF7B72");
    private static final int COLOR_GREEN   = Color.parseColor("#33FF33");
    private static final int COLOR_YELLOW  = Color.parseColor("#F1E05A");
    private static final int COLOR_BLUE    = Color.parseColor("#79C0FF");
    private static final int COLOR_MAGENTA = Color.parseColor("#D2A8FF");
    private static final int COLOR_CYAN    = Color.parseColor("#56D4DD");
    private static final int COLOR_WHITE   = Color.parseColor("#F0F6FC");

    /**
     * Converts raw text containing ANSI codes into a SpannableStringBuilder with styled text.
     */
    public static CharSequence formatAnsi(String raw) {
        if (raw == null) return "";

        // Remove carriage returns that overwrite text
        String sanitized = raw.replace("\r\n", "\n").replace("\r", "");
        // Remove non-color control sequences (e.g. cursor hide/show, bracketed paste)
        sanitized = sanitized.replaceAll("\u001B\\[[0-9;]*[a-zA-Z]", "");

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        Matcher matcher = ANSI_PATTERN.matcher(raw);

        int lastEnd = 0;
        int currentColor = COLOR_DEFAULT;
        boolean isBold = false;

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            // Append text before this code
            if (start > lastEnd) {
                String segment = raw.substring(lastEnd, start);
                appendStyledSegment(ssb, segment, currentColor, isBold);
            }

            // Parse escape code numbers
            String codes = matcher.group(1);
            if (codes == null || codes.isEmpty() || codes.equals("0")) {
                currentColor = COLOR_DEFAULT;
                isBold = false;
            } else {
                String[] parts = codes.split(";");
                for (String part : parts) {
                    try {
                        int code = Integer.parseInt(part.trim());
                        switch (code) {
                            case 0:
                                currentColor = COLOR_DEFAULT;
                                isBold = false;
                                break;
                            case 1:
                                isBold = true;
                                break;
                            case 30:
                                currentColor = COLOR_BLACK;
                                break;
                            case 31:
                                currentColor = COLOR_RED;
                                break;
                            case 32:
                                currentColor = COLOR_GREEN;
                                break;
                            case 33:
                                currentColor = COLOR_YELLOW;
                                break;
                            case 34:
                                currentColor = COLOR_BLUE;
                                break;
                            case 35:
                                currentColor = COLOR_MAGENTA;
                                break;
                            case 36:
                                currentColor = COLOR_CYAN;
                                break;
                            case 37:
                                currentColor = COLOR_WHITE;
                                break;
                            case 39:
                                currentColor = COLOR_DEFAULT;
                                break;
                            default:
                                break;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            lastEnd = end;
        }

        // Append remaining text
        if (lastEnd < raw.length()) {
            String segment = raw.substring(lastEnd);
            appendStyledSegment(ssb, segment, currentColor, isBold);
        }

        return ssb;
    }

    private static void appendStyledSegment(SpannableStringBuilder ssb, String text, int color, boolean bold) {
        if (text.isEmpty()) return;

        // Clean any residual control characters
        String clean = text.replace("\r", "");
        int startPos = ssb.length();
        ssb.append(clean);
        int endPos = ssb.length();

        if (color != COLOR_DEFAULT) {
            ssb.setSpan(new ForegroundColorSpan(color), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (bold) {
            ssb.setSpan(new StyleSpan(Typeface.BOLD), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
