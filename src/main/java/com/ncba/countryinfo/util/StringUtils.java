package com.ncba.countryinfo.util;

/**
 * Small string helpers for the integration flow.
 */
public final class StringUtils {

    private StringUtils() {
    }

    /**
     * Converts an arbitrary country name into sentence case: the first letter
     * of each whitespace-separated word is upper-cased and the rest lower-cased.
     *
     * <p>Examples:</p>
     * <ul>
     *     <li>{@code "kenya"} → {@code "Kenya"}</li>
     *     <li>{@code "UNITED STATES"} → {@code "United States"}</li>
     *     <li>{@code "  south   africa "} → {@code "South Africa"}</li>
     * </ul>
     *
     * @param input the raw country name (may be {@code null})
     * @return the sentence-cased name, or the original value when {@code null}
     *         or blank
     */
    public static String toSentenceCase(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String[] words = input.trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) {
                continue;
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1).toLowerCase());
            }
            if (i < words.length - 1) {
                result.append(' ');
            }
        }
        return result.toString();
    }
}
