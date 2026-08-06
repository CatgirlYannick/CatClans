package dev.catgirlyannick.catclans.message;

import dev.catgirlyannick.catclans.util.MenuTextNormalizer;
import dev.catgirlyannick.catclans.config.ConfigBundle;
import dev.catgirlyannick.catclans.service.ClanTagFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageService {

    private static final Pattern CONFIG_PLACEHOLDER = Pattern.compile("\\{([a-z0-9_]+)}");
    private static final Pattern RGB_TAG = Pattern.compile(
            "(?i)<(?:color:|colour:|c:)?#[0-9a-f]{6}>"
    );
    private static final Pattern GRADIENT_TAG = Pattern.compile("(?i)</?gradient(?::[^>]*)?>");

    private final ConfigBundle configs;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final boolean rgbEnabled;
    private final boolean gradientsEnabled;
    private final boolean smallCapsEnabled;
    private final Map<String, String> brandingPlaceholders;

    public MessageService(ConfigBundle configs) {
        this.configs = configs;
        this.rgbEnabled = configs.messages().getBoolean(
                "formatting.minimessage.rgb-enabled",
                true
        );
        this.gradientsEnabled = configs.messages().getBoolean(
                "formatting.minimessage.gradients-enabled",
                true
        );
        this.smallCapsEnabled = configs.messages().getBoolean(
                "formatting.small-caps.enabled",
                true
        );
        this.brandingPlaceholders = Map.of(
                "server_name", configs.main().getString(
                        "branding.server-name",
                        "{{SERVER_NAME}}"
                ),
                "author_name", configs.main().getString(
                        "branding.author-name",
                        "CatgirlYannick"
                ),
                "plugin_name", configs.main().getString(
                        "branding.plugin-name",
                        "CatClans"
                ),
                "currency", configs.economy().getString(
                        "currency.display-name",
                        "Coins"
                )
        );
        validateConfiguredFormatting();
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(render(configs.messages().getString("prefix", "") + raw(path), placeholders));
    }

    public void sendList(CommandSender sender, String path) {
        sendList(sender, path, Map.of());
    }

    public void sendList(
            CommandSender sender,
            String path,
            Map<String, String> placeholders
    ) {
        List<String> lines = configs.messages().getStringList(path);
        for (String line : lines) {
            sender.sendMessage(render(line, placeholders));
        }
    }

    public void sendCreateCommandHelp(CommandSender sender) {
        sendList(sender, "general.create-command-help", Map.of(
                "usage", "/clan create <Tag> <ClanName>",
                "example", "/clan create CAT Moonlight Keepers",
                "rgb_example", "/clan create &#D67DE9&l&oCAT Moonlight Keepers",
                "tag_min", Integer.toString(configs.main().getInt(
                        "clans.tags.min-length", 2
                )),
                "tag_max", Integer.toString(configs.main().getInt(
                        "clans.tags.max-length", 6
                )),
                "name_max", Integer.toString(configs.main().getInt(
                        "clans.names.max-length", 20
                ))
        ));
    }

    public Component render(String raw, Map<String, String> placeholders) {
        validateFormatting(raw);
        Map<String, String> effectivePlaceholders = new HashMap<>(brandingPlaceholders);
        effectivePlaceholders.putAll(placeholders);
        if (!smallCapsEnabled) {
            return renderTemplate(
                    miniMessage,
                    raw,
                    Map.copyOf(effectivePlaceholders),
                    rgbEnabled,
                    gradientsEnabled
            );
        }
        Map<String, String> formattedPlaceholders = new HashMap<>();
        effectivePlaceholders.forEach((key, value) ->
                formattedPlaceholders.put(key, SmallCapsFormatter.formatValue(value)));
        return renderTemplate(
                miniMessage,
                SmallCapsFormatter.formatTemplate(raw),
                Map.copyOf(formattedPlaceholders),
                rgbEnabled,
                gradientsEnabled
        );
    }

    public Component renderMenu(String raw, Map<String, String> placeholders) {
        Map<String, String> normalizedPlaceholders = new HashMap<>();
        placeholders.forEach((key, value) -> normalizedPlaceholders.put(
                key,
                MenuTextNormalizer.normalize(value)
        ));
        return MenuTextNormalizer.normalize(render(
                MenuTextNormalizer.normalize(raw),
                Map.copyOf(normalizedPlaceholders)
        ));
    }

    static Component renderTemplate(
            MiniMessage miniMessage,
            String raw,
            Map<String, String> placeholders
    ) {
        return renderTemplate(miniMessage, raw, placeholders, true, true);
    }

    static Component renderTemplate(
            MiniMessage miniMessage,
            String raw,
            Map<String, String> placeholders,
            boolean rgbEnabled,
            boolean gradientsEnabled
    ) {
        TagResolver.Builder resolver = TagResolver.builder();
        placeholders.forEach((key, value) -> resolver.resolver(
                "formatted_tag".equals(key)
                        ? Placeholder.component(
                        key,
                        ClanTagFormatter.render(value, rgbEnabled, gradientsEnabled)
                )
                        : Placeholder.unparsed(key, value)
        ));
        return miniMessage.deserialize(resolveConfigPlaceholders(raw, placeholders), resolver.build());
    }

    private static String resolveConfigPlaceholders(
            String raw,
            Map<String, String> placeholders
    ) {
        if (raw.indexOf('{') < 0) {
            return raw;
        }
        Matcher matcher = CONFIG_PLACEHOLDER.matcher(raw);
        StringBuilder resolved = new StringBuilder(raw.length());
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = placeholders.containsKey(name)
                    ? "<" + name + ">"
                    : matcher.group();
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        return matcher.appendTail(resolved).toString();
    }

    public Component renderConfig(String file, String path, Map<String, String> placeholders) {
        String raw = switch (file) {
            case "gui" -> configs.gui().getString(path, "<red>Missing GUI text: " + path);
            default -> throw new IllegalArgumentException("Unknown configuration: " + file);
        };
        return renderMenu(raw, placeholders);
    }

    private String raw(String path) {
        return configs.messages().getString(path, "<red>Missing message text: " + path);
    }

    private void validateConfiguredFormatting() {
        if (!configs.messages().getBoolean(
                "formatting.minimessage.validate-on-start",
                true
        )) {
            return;
        }
        validateConfigurationValues("messages.yml", configs.messages().getValues(true));
        validateConfigurationValues("gui.yml", configs.gui().getValues(true));
    }

    private void validateConfigurationValues(String fileName, Map<String, Object> values) {
        values.forEach((path, value) -> {
            if (value instanceof String text) {
                validateFormattingValue(fileName, path, text);
            } else if (value instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof String text) {
                        validateFormattingValue(fileName, path, text);
                    }
                }
            }
        });
    }

    private void validateFormattingValue(String fileName, String path, String raw) {
        try {
            validateFormatting(raw);
            miniMessage.deserialize(raw);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(fileName + ": invalid MiniMessage format at "
                    + path + ": " + exception.getMessage(), exception);
        }
    }

    private void validateFormatting(String raw) {
        if (!rgbEnabled && RGB_TAG.matcher(raw).find()) {
            throw new IllegalArgumentException("RGB tags are disabled in messages.yml");
        }
        if (!gradientsEnabled && GRADIENT_TAG.matcher(raw).find()) {
            throw new IllegalArgumentException("Gradient tags are disabled in messages.yml");
        }
    }
}
