package art.arcane.adapt.bukkit.port;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

/** MiniMessage tags that colour text, as far as the server's Adventure version has them: no pride tag before 4.18. */
final class BukkitColorTags {
  private BukkitColorTags() {
  }

  static TagResolver resolver() {
    return TagResolver.resolver(StandardTags.color(), StandardTags.reset(), StandardTags.gradient(), StandardTags.rainbow(),
        StandardTags.transition());
  }
}
