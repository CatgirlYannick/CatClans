package dev.catgirlyannick.catclans.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ClanRules {

    private final int nameMinLength;
    private final int nameMaxLength;
    private final Pattern namePattern;
    private final int tagMinLength;
    private final int tagMaxLength;
    private final Pattern tagPattern;
    private final int tagMaximumFormatLength;
    private final int roleNameMaxLength;
    private final Pattern roleNamePattern;
    private final boolean tagRgbEnabled;
    private final boolean tagGradientsEnabled;

    public ClanRules(
            int nameMinLength,
            int nameMaxLength,
            String namePattern,
            int tagMinLength,
            int tagMaxLength,
            String tagPattern
    ) {
        this(
                nameMinLength,
                nameMaxLength,
                namePattern,
                tagMinLength,
                tagMaxLength,
                tagPattern,
                256,
                24,
                "^[\\p{L}\\p{N} _-]+$",
                true,
                true
        );
    }

    public ClanRules(
            int nameMinLength,
            int nameMaxLength,
            String namePattern,
            int tagMinLength,
            int tagMaxLength,
            String tagPattern,
            int roleNameMaxLength,
            String roleNamePattern
    ) {
        this(
                nameMinLength,
                nameMaxLength,
                namePattern,
                tagMinLength,
                tagMaxLength,
                tagPattern,
                256,
                roleNameMaxLength,
                roleNamePattern,
                true,
                true
        );
    }

    public ClanRules(
            int nameMinLength,
            int nameMaxLength,
            String namePattern,
            int tagMinLength,
            int tagMaxLength,
            String tagPattern,
            int tagMaximumFormatLength,
            int roleNameMaxLength,
            String roleNamePattern
    ) {
        this(
                nameMinLength,
                nameMaxLength,
                namePattern,
                tagMinLength,
                tagMaxLength,
                tagPattern,
                tagMaximumFormatLength,
                roleNameMaxLength,
                roleNamePattern,
                true,
                true
        );
    }

    public ClanRules(
            int nameMinLength,
            int nameMaxLength,
            String namePattern,
            int tagMinLength,
            int tagMaxLength,
            String tagPattern,
            int tagMaximumFormatLength,
            int roleNameMaxLength,
            String roleNamePattern,
            boolean tagRgbEnabled,
            boolean tagGradientsEnabled
    ) {
        this.nameMinLength = nameMinLength;
        this.nameMaxLength = nameMaxLength;
        this.namePattern = Pattern.compile(namePattern);
        this.tagMinLength = tagMinLength;
        this.tagMaxLength = tagMaxLength;
        this.tagPattern = Pattern.compile(tagPattern);
        this.tagMaximumFormatLength = tagMaximumFormatLength;
        this.roleNameMaxLength = roleNameMaxLength;
        this.roleNamePattern = Pattern.compile(roleNamePattern);
        this.tagRgbEnabled = tagRgbEnabled;
        this.tagGradientsEnabled = tagGradientsEnabled;
    }

    public boolean validName(String name) {
        String clean = cleanDisplay(name);
        return clean.length() >= nameMinLength
                && clean.length() <= nameMaxLength
                && namePattern.matcher(clean).matches()
                && hasNoControlCharacters(clean);
    }

    public boolean validTag(String tag) {
        return parseTag(tag).isPresent();
    }

    public Optional<ClanTagFormatter.ParsedTag> parseTag(String tag) {
        return ClanTagFormatter.parse(
                        tag,
                        tagMaximumFormatLength,
                        tagRgbEnabled,
                        tagGradientsEnabled
                )
                .filter(parsed -> validPlainTag(parsed.plain()));
    }

    private boolean validPlainTag(String clean) {
        int visibleLength = clean.codePointCount(0, clean.length());
        return visibleLength >= tagMinLength
                && visibleLength <= tagMaxLength
                && tagPattern.matcher(clean).matches()
                && hasNoControlCharacters(clean);
    }

    public boolean validRoleName(String roleName) {
        String clean = cleanDisplay(roleName);
        return !clean.isEmpty()
                && clean.length() <= roleNameMaxLength
                && roleNamePattern.matcher(clean).matches()
                && hasNoControlCharacters(clean);
    }

    public String cleanDisplay(String value) {
        return Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFC);
    }

    public String normalizeKey(String value) {
        return cleanDisplay(value).toLowerCase(Locale.ROOT);
    }

    private static boolean hasNoControlCharacters(String value) {
        return value.codePoints().noneMatch(Character::isISOControl);
    }
}
