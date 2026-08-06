package dev.catgirlyannick.catclans.message;

public final class SmallCapsFormatter {

    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String[] SMALL_CAPS = {
            "ᴀ", "ʙ", "ᴄ", "ᴅ", "ᴇ", "ꜰ", "ɢ", "ʜ", "ɪ", "ᴊ", "ᴋ", "ʟ", "ᴍ",
            "ɴ", "ᴏ", "ᴘ", "ǫ", "ʀ", "ꜱ", "ᴛ", "ᴜ", "ᴠ", "ᴡ", "x", "ʏ", "ᴢ"
    };

    private SmallCapsFormatter() {
    }

    public static String formatTemplate(String input) {
        return format(input, true);
    }

    public static String formatValue(String input) {
        return format(input, false);
    }

    private static String format(String input, boolean preservePlaceholders) {
        StringBuilder result = new StringBuilder(input.length());
        boolean insideMiniMessageTag = false;
        boolean insidePlaceholder = false;
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            int legacyCodeLength = legacyCodeLength(input, index);
            if (!insideMiniMessageTag && !insidePlaceholder && legacyCodeLength > 0) {
                result.append(input, index, index + legacyCodeLength);
                index += legacyCodeLength - 1;
                continue;
            }
            if (character == '<' && !insidePlaceholder) {
                insideMiniMessageTag = true;
                result.append(character);
                continue;
            }
            if (character == '>' && insideMiniMessageTag) {
                insideMiniMessageTag = false;
                result.append(character);
                continue;
            }
            if (preservePlaceholders && character == '{' && !insideMiniMessageTag) {
                insidePlaceholder = true;
                result.append(character);
                continue;
            }
            if (preservePlaceholders && character == '}' && insidePlaceholder) {
                insidePlaceholder = false;
                result.append(character);
                continue;
            }
            if (insideMiniMessageTag || insidePlaceholder) {
                result.append(character);
                continue;
            }
            appendSmallCap(result, character);
        }
        return result.toString();
    }

    private static int legacyCodeLength(String input, int start) {
        if (input.charAt(start) != '&' || start + 1 >= input.length()) {
            return 0;
        }
        char code = Character.toLowerCase(input.charAt(start + 1));
        if (code == '#' && start + 8 <= input.length()) {
            for (int offset = 2; offset < 8; offset++) {
                if (!isHexDigit(input.charAt(start + offset))) {
                    return 0;
                }
            }
            return 8;
        }
        if (code == 'x' && start + 14 <= input.length()) {
            for (int offset = 2; offset < 14; offset += 2) {
                if (input.charAt(start + offset) != '&'
                        || !isHexDigit(input.charAt(start + offset + 1))) {
                    return 0;
                }
            }
            return 14;
        }
        return "0123456789abcdefklmnor".indexOf(code) >= 0 ? 2 : 0;
    }

    private static boolean isHexDigit(int character) {
        return character >= '0' && character <= '9'
                || character >= 'a' && character <= 'f'
                || character >= 'A' && character <= 'F';
    }

    private static void appendSmallCap(StringBuilder target, char character) {
        int lowerIndex = LOWER.indexOf(character);
        if (lowerIndex >= 0) {
            target.append(SMALL_CAPS[lowerIndex]);
            return;
        }
        int upperIndex = UPPER.indexOf(character);
        if (upperIndex >= 0) {
            target.append(SMALL_CAPS[upperIndex]);
            return;
        }
        target.append(character);
    }
}
