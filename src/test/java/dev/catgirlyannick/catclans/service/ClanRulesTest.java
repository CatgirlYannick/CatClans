package dev.catgirlyannick.catclans.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClanRulesTest {

    private final ClanRules rules = new ClanRules(
            1,
            20,
            "^[\\p{L}\\p{N}][\\p{L}\\p{N} _-]*$",
            2,
            6,
            "^[A-Za-z0-9]+$"
    );

    @Test
    void acceptsConfiguredUnicodeClanNames() {
        assertTrue(rules.validName("Wächter 7"));
    }

    @Test
    void rejectsControlCharactersAndOversizedNames() {
        assertFalse(rules.validName("Test\nClan"));
        assertFalse(rules.validName("123456789012345678901"));
    }

    @Test
    void keepsTagsStrictUntilSpecialCharactersAreConfirmed() {
        assertTrue(rules.validTag("ASHEN"));
        assertFalse(rules.validTag("AC-1"));
        assertFalse(rules.validTag("A"));
    }

    @Test
    void countsOnlyVisibleTagCharactersAroundMiniMessageFormatting() {
        var parsed = rules.parseTag(
                "<gradient:#FF0000:#00FFFF><bold><strikethrough>ASH"
        ).orElseThrow();

        assertEquals("ASH", parsed.plain());
        assertEquals(
                "<gradient:#FF0000:#00FFFF><bold><strikethrough>ASH",
                parsed.formatted()
        );
        assertTrue(rules.validTag("<rainbow>ABC123"));
        assertTrue(rules.validTag("<#55D6C2>ASHEN"));
        assertFalse(rules.validTag("<red>TOOLONG"));
    }

    @Test
    void acceptsLegacyRgbAndDecorationFormattingInClanTags() {
        var parsed = rules.parseTag("&#D67DE9&l&oMeow").orElseThrow();

        assertEquals("Meow", parsed.plain());
        assertTrue(parsed.formatted().toLowerCase().contains("#d67de9"));
        assertTrue(parsed.formatted().contains("<bold>"));
        assertTrue(parsed.formatted().contains("<italic>"));
        assertTrue(rules.validTag("&cASH"));
        assertTrue(rules.validTag("&x&F&F&0&0&0&0ASH"));
        assertTrue(rules.validTag("&#FF0000A&#00FF00S&#0000FFH"));
        assertTrue(rules.validTag("&n&mASH"));
    }

    @Test
    void rejectsInteractiveOrMixedFormattingInClanTags() {
        assertFalse(rules.validTag("<click:run_command:/op>ASH</click>"));
        assertFalse(rules.validTag("<hover:show_text:'Test'>ASH</hover>"));
        assertFalse(rules.validTag("<red>&lASH"));
    }

    @Test
    void normalizesKeysCaseInsensitively() {
        assertEquals("äther", rules.normalizeKey(" ÄTHER "));
    }

    @Test
    void honorsRgbAndGradientFeatureSwitchesForClanTags() {
        ClanRules restricted = new ClanRules(
                1,
                20,
                "^[\\p{L}\\p{N}][\\p{L}\\p{N} _-]*$",
                2,
                6,
                "^[A-Za-z0-9]+$",
                256,
                24,
                "^[\\p{L}\\p{N} _-]+$",
                false,
                false
        );

        assertTrue(restricted.validTag("<red>ASH"));
        assertTrue(restricted.validTag("&cASH"));
        assertFalse(restricted.validTag("<#55D6C2>ASH"));
        assertFalse(restricted.validTag("&#55D6C2ASH"));
        assertFalse(restricted.validTag("<gradient:#FF0000:#00FFFF>ASH"));
        assertEquals(
                "ASH",
                ClanTagFormatter.safeMarkup(
                        "<gradient:#FF0000:#00FFFF>ASH",
                        true,
                        false
                )
        );
    }
}
