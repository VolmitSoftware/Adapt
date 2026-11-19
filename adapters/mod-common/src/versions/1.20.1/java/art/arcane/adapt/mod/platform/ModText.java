package art.arcane.adapt.mod.platform;

import art.arcane.adapt.mod.platform.text.Markup;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapt markup to vanilla components and back. Markup goes through MiniMessage, so colours, hex,
 * gradients, decorations, fonts, insertions, translations, keybinds and click/hover events all
 * survive. Text shadow colours are dropped: this version's clients cannot show them.
 */
public final class ModText {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private ModText() {
    }

    /** Renders Adapt markup (legacy section codes and MiniMessage tags) into a vanilla component. */
    public static MutableComponent component(String markup) {
        if (markup == null || markup.isEmpty()) {
            return Component.empty();
        }
        return vanilla(adventure(markup));
    }

    /** Like {@link #component(String)} with italics off unless the markup turns them on, for item names and lore. */
    public static MutableComponent itemText(String markup) {
        MutableComponent text = component(markup);
        return Component.empty().withStyle((Style style) -> style.withItalic(Boolean.FALSE)).append(text);
    }

    /** Adapt markup (MiniMessage) for a vanilla component. */
    public static String markup(Component component) {
        if (component == null) {
            return "";
        }
        return MINI_MESSAGE.serialize(adventure(component));
    }

    /** Plain text of a vanilla component. */
    public static String plain(Component component) {
        return component == null ? "" : component.getString();
    }

    public static net.kyori.adventure.text.Component adventure(String markup) {
        String normalized = Markup.toMiniMessage(markup);
        try {
            return MINI_MESSAGE.deserialize(normalized);
        } catch (RuntimeException invalid) {
            return LEGACY.deserialize(markup);
        }
    }

    public static MutableComponent vanilla(net.kyori.adventure.text.Component source) {
        MutableComponent out = switch (source) {
            case TextComponent text -> Component.literal(text.content());
            case TranslatableComponent translatable -> Component.translatableWithFallback(translatable.key(), translatable.fallback(), arguments(translatable.arguments()));
            case KeybindComponent keybind -> Component.keybind(keybind.keybind());
            default -> Component.empty();
        };
        out.setStyle(style(source.style()));
        for (net.kyori.adventure.text.Component child : source.children()) {
            out.append(vanilla(child));
        }
        return out;
    }

    public static net.kyori.adventure.text.Component adventure(Component source) {
        net.kyori.adventure.text.Component out = switch (source.getContents()) {
            case LiteralContents plain -> net.kyori.adventure.text.Component.text(plain.text());
            case TranslatableContents translatable -> net.kyori.adventure.text.Component.translatable(translatable.getKey(), translatable.getFallback(), adventureArguments(translatable.getArgs()));
            case KeybindContents keybind -> net.kyori.adventure.text.Component.keybind(keybind.getName());
            default -> net.kyori.adventure.text.Component.empty();
        };
        out = out.style(adventureStyle(source.getStyle()));
        List<net.kyori.adventure.text.Component> children = new ArrayList<>();
        for (Component sibling : source.getSiblings()) {
            children.add(adventure(sibling));
        }
        return children.isEmpty() ? out : out.children(children);
    }

    private static Object[] arguments(List<TranslationArgument> arguments) {
        Object[] out = new Object[arguments.size()];
        for (int index = 0; index < out.length; index++) {
            Object value = arguments.get(index).value();
            out[index] = value instanceof net.kyori.adventure.text.Component component ? vanilla(component) : value;
        }
        return out;
    }

    private static List<TranslationArgument> adventureArguments(Object[] arguments) {
        List<TranslationArgument> out = new ArrayList<>(arguments.length);
        for (Object argument : arguments) {
            switch (argument) {
                case Component component -> out.add(TranslationArgument.component(adventure(component)));
                case Boolean flag -> out.add(TranslationArgument.bool(flag));
                case Number number -> out.add(TranslationArgument.numeric(number));
                case null -> out.add(TranslationArgument.component(net.kyori.adventure.text.Component.empty()));
                default -> out.add(TranslationArgument.component(net.kyori.adventure.text.Component.text(String.valueOf(argument))));
            }
        }
        return out;
    }

    private static Style style(net.kyori.adventure.text.format.Style source) {
        Style style = Style.EMPTY;
        if (source.color() != null) {
            style = style.withColor(TextColor.fromRgb(source.color().value()));
        }
        style = style.withBold(decoration(source, TextDecoration.BOLD))
                .withItalic(decoration(source, TextDecoration.ITALIC))
                .withUnderlined(decoration(source, TextDecoration.UNDERLINED))
                .withStrikethrough(decoration(source, TextDecoration.STRIKETHROUGH))
                .withObfuscated(decoration(source, TextDecoration.OBFUSCATED));
        Key font = source.font();
        if (font != null) {
            ResourceLocation fontId = ResourceLocation.tryBuild(font.namespace(), font.value());
            if (fontId != null) {
                style = style.withFont(fontId);
            }
        }
        if (source.insertion() != null) {
            style = style.withInsertion(source.insertion());
        }
        ClickEvent click = click(source.clickEvent());
        if (click != null) {
            style = style.withClickEvent(click);
        }
        net.kyori.adventure.text.event.HoverEvent<?> hover = source.hoverEvent();
        if (hover != null && hover.value() instanceof net.kyori.adventure.text.Component shown) {
            style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, vanilla(shown)));
        }
        return style;
    }

    private static ClickEvent click(net.kyori.adventure.text.event.ClickEvent click) {
        if (click == null) {
            return null;
        }
        if (click.payload() instanceof net.kyori.adventure.text.event.ClickEvent.Payload.Int page
                && click.action() == net.kyori.adventure.text.event.ClickEvent.Action.CHANGE_PAGE) {
            return new ClickEvent(ClickEvent.Action.CHANGE_PAGE, Integer.toString(page.integer()));
        }
        if (!(click.payload() instanceof net.kyori.adventure.text.event.ClickEvent.Payload.Text text)) {
            return null;
        }
        String value = text.value();
        return switch (click.action()) {
            case RUN_COMMAND -> new ClickEvent(ClickEvent.Action.RUN_COMMAND, value);
            case SUGGEST_COMMAND -> new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, value);
            case COPY_TO_CLIPBOARD -> new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value);
            case OPEN_URL -> openUrl(value);
            default -> null;
        };
    }

    private static ClickEvent openUrl(String value) {
        try {
            return new ClickEvent(ClickEvent.Action.OPEN_URL, URI.create(value).toString());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static Boolean decoration(net.kyori.adventure.text.format.Style source, TextDecoration decoration) {
        return switch (source.decoration(decoration)) {
            case TRUE -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            case NOT_SET -> null;
        };
    }

    private static net.kyori.adventure.text.format.Style adventureStyle(Style source) {
        net.kyori.adventure.text.format.Style.Builder style = net.kyori.adventure.text.format.Style.style();
        TextColor color = source.getColor();
        if (color != null) {
            int rgb = color.getValue();
            NamedTextColor named = NamedTextColor.namedColor(rgb);
            style.color(named != null ? named : net.kyori.adventure.text.format.TextColor.color(rgb));
        }
        if (source.isBold()) {
            style.decoration(TextDecoration.BOLD, true);
        }
        if (source.isItalic()) {
            style.decoration(TextDecoration.ITALIC, true);
        }
        if (source.isUnderlined()) {
            style.decoration(TextDecoration.UNDERLINED, true);
        }
        if (source.isStrikethrough()) {
            style.decoration(TextDecoration.STRIKETHROUGH, true);
        }
        if (source.isObfuscated()) {
            style.decoration(TextDecoration.OBFUSCATED, true);
        }
        ResourceLocation font = source.getFont();
        if (font != null && !font.equals(Style.DEFAULT_FONT)) {
            style.font(Key.key(font.getNamespace(), font.getPath()));
        }
        if (source.getInsertion() != null) {
            style.insertion(source.getInsertion());
        }
        ClickEvent click = source.getClickEvent();
        if (click != null) {
            String value = click.getValue();
            net.kyori.adventure.text.event.ClickEvent converted = switch (click.getAction()) {
                case RUN_COMMAND -> net.kyori.adventure.text.event.ClickEvent.runCommand(value);
                case SUGGEST_COMMAND -> net.kyori.adventure.text.event.ClickEvent.suggestCommand(value);
                case COPY_TO_CLIPBOARD -> net.kyori.adventure.text.event.ClickEvent.copyToClipboard(value);
                case OPEN_URL -> net.kyori.adventure.text.event.ClickEvent.openUrl(value);
                case CHANGE_PAGE -> changePage(value);
                case OPEN_FILE -> null;
            };
            if (converted != null) {
                style.clickEvent(converted);
            }
        }
        HoverEvent hover = source.getHoverEvent();
        Component shown = hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_TEXT);
        if (shown != null) {
            style.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(adventure(shown)));
        }
        return style.build();
    }

    private static net.kyori.adventure.text.event.ClickEvent changePage(String value) {
        try {
            return net.kyori.adventure.text.event.ClickEvent.changePage(Integer.parseInt(value));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
