package dev.catgirlyannick.catclans.gui;

import dev.catgirlyannick.catclans.util.MenuTextNormalizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;

final class GuiTextStyle {

    private GuiTextStyle() {
    }

    static Component nonItalic(Component component) {
        return MenuTextNormalizer.normalize(component).decoration(
                TextDecoration.ITALIC,
                TextDecoration.State.FALSE
        );
    }

    static List<Component> nonItalic(List<Component> components) {
        return components.stream()
                .map(GuiTextStyle::nonItalic)
                .toList();
    }
}
