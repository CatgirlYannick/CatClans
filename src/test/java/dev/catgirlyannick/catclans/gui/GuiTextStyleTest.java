package dev.catgirlyannick.catclans.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiTextStyleTest {

    @Test
    void disablesInheritedItalicsForNamesAndLore() {
        Component name = GuiTextStyle.nonItalic(Component.text("Schließen und STRAẞE"));
        List<Component> lore = GuiTextStyle.nonItalic(List.of(Component.text("Clanbank")));

        assertEquals(
                TextDecoration.State.FALSE,
                name.decoration(TextDecoration.ITALIC)
        );
        assertEquals(
                TextDecoration.State.FALSE,
                lore.getFirst().decoration(TextDecoration.ITALIC)
        );
        assertEquals(
                "Schliessen und STRASSE",
                PlainTextComponentSerializer.plainText().serialize(name)
        );
    }
}
