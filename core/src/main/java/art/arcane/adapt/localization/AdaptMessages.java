package art.arcane.adapt.localization;

import art.arcane.adapt.localization.catalog.CommandMessages;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.ConfigFieldMessages;
import art.arcane.adapt.localization.catalog.ConfigMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.localization.catalog.MutationMessages;
import art.arcane.adapt.localization.catalog.PackMessages;
import art.arcane.adapt.localization.catalog.PreferenceMessages;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.localization.catalog.SeatMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.localization.catalog.UpgradeMessages;
import art.arcane.adapt.localization.catalog.XpGuideMessages;
import art.arcane.volmlib.util.director.DirectorMessages;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The engine's own messages. The built-in content's messages come from the addon of namespace {@code adapt}; both
 * together make the built-in catalog, see {@link AdaptLanguage#builtInCatalog()}. The messages of VolmLib's command
 * help and language menus are declared under {@code adapt.} like the rest; {@link #declared} maps the library's own
 * keys to them.
 */
public final class AdaptMessages {
  private static final List<MessageKey> LIBRARY = Stream.concat(DirectorMessages.keys().stream(),
      BukkitLanguageMessages.keys().stream()).toList();
  private static final Map<MessageKey, MessageKey> ADOPTED = adopt(LIBRARY);
  private static final MessageCatalog CATALOG = createCatalog();

  private AdaptMessages() {
  }

  public static MessageCatalog catalog() {
    return CATALOG;
  }

  /** {@code key} as the built-in namespace declares it: a VolmLib message under {@code adapt.}, any other unchanged. */
  public static MessageKey declared(MessageKey key) {
    MessageKey adopted = ADOPTED.get(key);
    return adopted == null ? key : adopted;
  }

  private static Map<MessageKey, MessageKey> adopt(List<MessageKey> keys) {
    Map<MessageKey, MessageKey> adopted = new IdentityHashMap<>();
    for (MessageKey key : keys) {
      adopted.put(key, AdaptKeys.adopted(key));
    }
    return adopted;
  }

  private static MessageCatalog createCatalog() {
    MessageCatalog.Builder builder = MessageCatalog.builder("en_US");
    for (MessageKey key : LIBRARY) {
      builder.add(ADOPTED.get(key));
    }
    ConfigMessages.addTo(builder);
    ConfigFieldMessages.addTo(builder);
    GuiMessages.addTo(builder);
    PreferenceMessages.addTo(builder);
    RuntimeMessages.addTo(builder);
    SnippetsMessages.addTo(builder);
    UpgradeMessages.addTo(builder);
    MutatedMessages.addTo(builder);
    MutationMessages.addTo(builder);
    XpGuideMessages.addTo(builder);
    SeatMessages.addTo(builder);
    PackMessages.addTo(builder);
    CommandMessages.addTo(builder);
    CommandRuntimeMessages.addTo(builder);
    return builder.build();
  }
}
