package art.arcane.adapt.pack.link;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.AdaptMessages;
import art.arcane.adapt.localization.catalog.PackMessages;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageKey;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The words that push a player without the Adapt client mod towards the pack, in their language and by the
 * {@code pack} config: the prompt, the chat invitation, the steps after a decline and the required-pack disconnect.
 * Each names the client mod as the other way when {@code pack.mentionMod} is on. Chat lines are Adapt markup.
 */
public final class PackPitch {
  static final String OFFER_COMMAND = "/adapt pack offer";

  private PackPitch() {
  }

  /** The prompt the client shows: the full text with {@code pack.strongPrompt}, else the {@code promptKey} text. */
  public static String prompt(UUID playerId) {
    AdaptConfig.Pack config = AdaptConfig.get().getPack();
    MessageKey key = config.isStrongPrompt() ? PackMessages.PROMPT_STRONG : configuredPrompt(config);
    String text = text(playerId, key);
    return config.isMentionMod() && config.isStrongPrompt() ? text + "\n" + text(playerId, PackMessages.PROMPT_MOD) : text;
  }

  /** The invitation: a click that sends the offer again, then the mod line. */
  public static String invite(UUID playerId) {
    return join(text(playerId, PackMessages.INVITE) + " <gold><click:run_command:'" + OFFER_COMMAND + "'>"
        + text(playerId, PackMessages.INVITE_PACK) + "</click></gold>", modLine(playerId, true));
  }

  /** How to let the client accept server packs again, then the mod line. */
  public static String steps(UUID playerId) {
    return join(text(playerId, PackMessages.CLIENT_DECLINED), modLine(playerId, true));
  }

  /** Why a player who declined a required pack was disconnected, then the mod line without a link. */
  public static String required(UUID playerId) {
    return join(text(playerId, PackMessages.REQUIRED), modLine(playerId, false));
  }

  /** The lore of the chest menu invitation for a player whose client refuses packs, or who can be offered one. */
  public static List<String> lore(UUID playerId, boolean refusing) {
    List<String> lore = new ArrayList<>();
    lore.add(refusing ? text(playerId, PackMessages.CLIENT_DECLINED) : text(playerId, PackMessages.INVITE));
    if (AdaptConfig.get().getPack().isMentionMod()) {
      lore.add(text(playerId, PackMessages.INVITE_MOD));
    }
    return lore;
  }

  /** The mod line, with a download link when one is set and {@code link}; empty with {@code pack.mentionMod} off. */
  static String modLine(UUID playerId, boolean link) {
    AdaptConfig.Pack config = AdaptConfig.get().getPack();
    if (!config.isMentionMod()) {
      return "";
    }
    String line = "<gray>" + text(playerId, PackMessages.INVITE_MOD) + "</gray>";
    if (!link || config.getModUrl().isEmpty()) {
      return line;
    }
    return line + " <gold><click:open_url:'" + config.getModUrl().replace("\\", "\\\\").replace("'", "\\'") + "'>"
        + text(playerId, PackMessages.INVITE_MOD_LINK) + "</click></gold>";
  }

  private static MessageKey configuredPrompt(AdaptConfig.Pack config) {
    MessageKey configured = AdaptMessages.catalog().key(config.getPromptKey());
    return configured == null ? PackMessages.PROMPT : configured;
  }

  private static String join(String first, String second) {
    return second.isEmpty() ? first : first + "\n" + second;
  }

  private static String text(UUID playerId, MessageKey key) {
    return AdaptLanguage.text(playerId, key, MessageArgs.empty());
  }
}
