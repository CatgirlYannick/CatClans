package dev.catgirlyannick.catclans.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ClanTagFormatter {

    private static final TagResolver VISUAL_TAGS = TagResolver.builder()
            .resolver(StandardTags.color())
            .resolver(StandardTags.decorations())
            .resolver(StandardTags.gradient())
            .resolver(StandardTags.rainbow())
            .resolver(StandardTags.transition())
            .resolver(StandardTags.reset())
            .build();
    private static final MiniMessage TAG_MINI_MESSAGE = MiniMessage.builder()
            .tags(VISUAL_TAGS)
            .build();
    private static final PlainTextComponentSerializer PLAIN_TEXT =
            PlainTextComponentSerializer.plainText();
    private static final LegacyComponentSerializer LEGACY_TEXT =
            LegacyComponentSerializer.builder()
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();
    private static final LegacyComponentSerializer LEGACY_INPUT =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexCharacter('#')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();
    private static final Pattern RGB_TAG = Pattern.compile(
            "(?i)<(?:color:|colour:|c:)?#[0-9a-f]{6}>"
    );
    private static final Pattern GRADIENT_TAG = Pattern.compile(
            "(?i)</?gradient(?::[^>]*)?>"
    );
    private static final Pattern LEGACY_FORMAT = Pattern.compile(
            "(?i)&(?:#[0-9a-f]{6}|[0-9a-fk-or]|x(?:&[0-9a-f]){6})"
    );
    private static final Pattern LEGACY_RGB = Pattern.compile(
            "(?i)&(?:#[0-9a-f]{6}|x(?:&[0-9a-f]){6})"
    );

    private ClanTagFormatter() {
    }

    public static Optional<ParsedTag> parse(String input, int maximumFormatLength) {
        return parse(input, maximumFormatLength, true, true);
    }

    public static Optional<ParsedTag> parse(
            String input,
            int maximumFormatLength,
            boolean rgbEnabled,
            boolean gradientsEnabled
    ) {
        String formatted = normalize(input);
        if (formatted.isEmpty()
                || formatted.codePointCount(0, formatted.length()) > maximumFormatLength
                || hasControlCharacters(formatted)
                || !formattingAllowed(formatted, rgbEnabled, gradientsEnabled)) {
            return Optional.empty();
        }
        try {
            boolean legacyFormat = LEGACY_FORMAT.matcher(formatted).find();
            if (legacyFormat && containsMiniMessageSyntax(formatted)) {
                return Optional.empty();
            }
            Component component = legacyFormat
                    ? LEGACY_INPUT.deserialize(formatted)
                    : TAG_MINI_MESSAGE.deserialize(formatted);
            String plain = normalize(PLAIN_TEXT.serialize(component));
            if (plain.isEmpty() || hasControlCharacters(plain)) {
                return Optional.empty();
            }
            String canonicalFormat = legacyFormat
                    ? TAG_MINI_MESSAGE.serialize(component)
                    : formatted;
            return Optional.of(new ParsedTag(plain, canonicalFormat));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public static Component render(String formattedTag) {
        return render(formattedTag, true, true);
    }

    public static Component render(
            String formattedTag,
            boolean rgbEnabled,
            boolean gradientsEnabled
    ) {
        try {
            String normalized = normalize(formattedTag);
            Component rendered = LEGACY_FORMAT.matcher(normalized).find()
                    ? LEGACY_INPUT.deserialize(normalized)
                    : TAG_MINI_MESSAGE.deserialize(normalized);
            return formattingAllowed(normalized, rgbEnabled, gradientsEnabled)
                    ? rendered
                    : Component.text(PLAIN_TEXT.serialize(rendered));
        } catch (RuntimeException ignored) {
            return Component.text(normalize(formattedTag));
        }
    }

    public static String legacy(String formattedTag) {
        return LEGACY_TEXT.serialize(render(formattedTag));
    }

    public static String legacy(
            String formattedTag,
            boolean rgbEnabled,
            boolean gradientsEnabled
    ) {
        return LEGACY_TEXT.serialize(render(formattedTag, rgbEnabled, gradientsEnabled));
    }

    public static String safeMarkup(
            String formattedTag,
            boolean rgbEnabled,
            boolean gradientsEnabled
    ) {
        String normalized = normalize(formattedTag);
        if (!formattingAllowed(normalized, rgbEnabled, gradientsEnabled)) {
            return PLAIN_TEXT.serialize(render(normalized));
        }
        if (LEGACY_FORMAT.matcher(normalized).find()) {
            try {
                return TAG_MINI_MESSAGE.serialize(LEGACY_INPUT.deserialize(normalized));
            } catch (RuntimeException ignored) {
                return PLAIN_TEXT.serialize(render(normalized));
            }
        }
        return normalized;
    }

    private static boolean formattingAllowed(
            String formatted,
            boolean rgbEnabled,
            boolean gradientsEnabled
    ) {
        return (rgbEnabled || !RGB_TAG.matcher(formatted).find()
                && !LEGACY_RGB.matcher(formatted).find())
                && (gradientsEnabled || !GRADIENT_TAG.matcher(formatted).find());
    }

    private static boolean containsMiniMessageSyntax(String formatted) {
        return formatted.indexOf('<') >= 0 || formatted.indexOf('>') >= 0;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(
                value == null ? "" : value.trim(),
                Normalizer.Form.NFC
        );
    }

    private static boolean hasControlCharacters(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    public record ParsedTag(String plain, String formatted) {
    }
}
