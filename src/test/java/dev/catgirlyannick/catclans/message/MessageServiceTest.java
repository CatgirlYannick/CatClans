package dev.catgirlyannick.catclans.message;

import dev.catgirlyannick.catclans.util.MenuTextNormalizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageServiceTest {

    @Test
    void preservesLegacyRgbCodesDuringSmallCapsFormatting() {
        assertTrue(
                SmallCapsFormatter.formatValue("&#D67DE9&l&oMeow")
                        .startsWith("&#D67DE9&l&o")
        );
    }

    @Test
    void resolvesCurlyConfigPlaceholdersAsUnparsedText() {
        Component rendered = MessageService.renderTemplate(
                MiniMessage.miniMessage(),
                "<green>Clan {clan} [{tag}] - {members}/{max_members}, "
                        + "Modus {mode}, Anzahl {count}",
                Map.of(
                        "clan", "<red>Ashen</red>",
                        "tag", "AC",
                        "members", "3",
                        "max_members", "27",
                        "mode", "Offen",
                        "count", "1"
                )
        );

        assertEquals(
                "Clan <red>Ashen</red> [AC] - 3/27, Modus Offen, Anzahl 1",
                PlainTextComponentSerializer.plainText().serialize(rendered)
        );
    }

    @Test
    void keepsUnknownConfigPlaceholderVisible() {
        Component rendered = MessageService.renderTemplate(
                MiniMessage.miniMessage(),
                "{known} {missing}",
                Map.of("known", "Wert")
        );

        assertEquals(
                "Wert {missing}",
                PlainTextComponentSerializer.plainText().serialize(rendered)
        );
    }

    @Test
    void rendersOnlyTheReservedFormattedTagAsVisualMiniMessage() {
        Component rendered = MessageService.renderTemplate(
                MiniMessage.miniMessage(),
                "<gray>Tag: {formatted_tag} | Clan: {clan}",
                Map.of(
                        "formatted_tag", "<#55D6C2><strikethrough>ASH",
                        "clan", "<red>Nicht formatiert"
                )
        );

        assertEquals(
                "Tag: ASH | Clan: <red>Nicht formatiert",
                PlainTextComponentSerializer.plainText().serialize(rendered)
        );
    }

    @Test
    void formatsTemplatesAndDynamicValuesAsSmallCapsWithoutBreakingMiniMessage() {
        assertEquals(
                "<gradient:#FF0000:#00FF00>\u1D04\u1D00\u1D1B\u1D04\u029F\u1D00\u0274\uA731</gradient> {clan}",
                SmallCapsFormatter.formatTemplate(
                        "<gradient:#FF0000:#00FF00>CatClans</gradient> {clan}"
                )
        );
        assertEquals(
                "<#55D6C2>ᴡäᴄʜᴛᴇʀ</#55D6C2>",
                SmallCapsFormatter.formatValue("<#55D6C2>Wächter</#55D6C2>")
        );
        assertEquals(
                "<gold>ᴄʟᴀɴ ʟɪꜱᴛᴇ",
                SmallCapsFormatter.formatTemplate("<gold>Clan Liste")
        );
        assertEquals(
                "ꜱᴛʀᴀꜱꜱᴇ",
                SmallCapsFormatter.formatValue(MenuTextNormalizer.normalize("Straße"))
        );
    }
}
