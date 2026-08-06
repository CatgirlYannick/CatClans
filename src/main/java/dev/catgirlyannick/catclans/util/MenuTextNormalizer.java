package dev.catgirlyannick.catclans.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;

public final class MenuTextNormalizer {

    private static final TextReplacementConfig LOWER_SHARP_S =
            TextReplacementConfig.builder()
                    .matchLiteral("ß")
                    .replacement("ss")
                    .build();
    private static final TextReplacementConfig UPPER_SHARP_S =
            TextReplacementConfig.builder()
                    .matchLiteral("ẞ")
                    .replacement("SS")
                    .build();

    private MenuTextNormalizer() {
    }

    public static String normalize(String text) {
        return text.replace("ß", "ss").replace("ẞ", "SS");
    }

    public static Component normalize(Component component) {
        return component.replaceText(LOWER_SHARP_S).replaceText(UPPER_SHARP_S);
    }
}
