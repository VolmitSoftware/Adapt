package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.localization.MarkupPort;
import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

final class BukkitMarkupPort implements MarkupPort {
  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
  private static final TagResolver COLOR_TAGS = BukkitColorTags.resolver();

  @Override
  public String normalize(String mixedMarkup) {
    return ComponentText.normalizeMarkup(mixedMarkup);
  }

  @Override
  public String legacy(String mixedMarkup) {
    return ComponentText.markup(mixedMarkup).legacy();
  }

  @Override
  public String plain(String mixedMarkup) {
    return ComponentText.markup(mixedMarkup).plain();
  }

  @Override
  public String renderMiniMessage(String miniMessage) {
    return ComponentText.component(MINI_MESSAGE.deserialize(miniMessage)).legacy();
  }

  @Override
  public String escapeTags(String text) {
    return MINI_MESSAGE.escapeTags(text);
  }

  @Override
  public boolean hasColorTags(String miniMessage) {
    ColorPresence colors = new ColorPresence();
    MINI_MESSAGE.deserializeToTree(miniMessage, colors);
    return colors.present;
  }

  @Override
  public String jsonToMarkup(String componentJson) {
    if (componentJson == null || componentJson.isBlank()) {
      return "";
    }
    try {
      return MINI_MESSAGE.serialize(GsonComponentSerializer.gson().deserialize(componentJson));
    } catch (RuntimeException unreadable) {
      return "";
    }
  }

  private static final class ColorPresence implements TagResolver {
    private boolean present;

    @Override
    public Tag resolve(String name, ArgumentQueue arguments, Context context) {
      Tag tag = COLOR_TAGS.resolve(name, arguments, context);
      if (tag != null) {
        present = true;
      }
      return tag;
    }

    @Override
    public boolean has(String name) {
      return COLOR_TAGS.has(name);
    }
  }
}
