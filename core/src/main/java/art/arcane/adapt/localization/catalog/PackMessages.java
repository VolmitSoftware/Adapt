package art.arcane.adapt.localization.catalog;

import art.arcane.adapt.localization.AdaptKeys;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

/** The Adapt resource pack: its prompt and the {@code /adapt pack} replies. */
public final class PackMessages {
  public static final TextKey PROMPT = AdaptKeys.text("pack.prompt", "Adapt book menus");
  public static final TextKey PROMPT_STRONG = AdaptKeys.text("pack.prompt_strong",
      "Adapt menus become an illustrated skill book with animated pages. Small download, menus only.");
  public static final TextKey PROMPT_MOD = AdaptKeys.text("pack.prompt_mod", "Or install the Adapt client mod instead.");
  public static final TextKey SENT = AdaptKeys.text("pack.sent", "Sent the Adapt pack.");
  public static final TextKey NO_PACK = AdaptKeys.text("pack.no_pack", "No Adapt pack is available.");
  public static final TextKey UNHOSTED = AdaptKeys.text("pack.unhosted", "The server could not host the Adapt pack.");
  public static final TextKey CLIENT_DECLINED = AdaptKeys.text("pack.client_declined",
      "Your client turns down server packs here. Edit this server in your server list, set Server Resource Packs to Enabled, then rejoin.");
  public static final TextKey UNAVAILABLE = AdaptKeys.text("pack.unavailable", "This server does not offer the Adapt pack.");
  public static final TextKey CLIENT_MOD = AdaptKeys.text("pack.client_mod", "Your Adapt client already shows book menus.");
  public static final TextKey BEDROCK = AdaptKeys.text("pack.bedrock", "Bedrock clients cannot load the Adapt pack.");
  public static final TextKey INVITE = AdaptKeys.text("pack.invite", "Adapt menus can open as an illustrated book.");
  public static final TextKey INVITE_PACK = AdaptKeys.text("pack.invite_pack", "[Load the book pack]");
  public static final TextKey INVITE_MOD = AdaptKeys.text("pack.invite_mod", "Or install the Adapt client mod for Fabric, Forge or NeoForge.");
  public static final TextKey INVITE_MOD_LINK = AdaptKeys.text("pack.invite_mod_link", "[Download]");
  public static final TextKey REQUIRED = AdaptKeys.text("pack.required",
      "This server needs the Adapt pack for its menus. Rejoin and press Proceed on the pack prompt.");
  public static final TextKey HELP_PACK = AdaptKeys.text("command.help.adapt_resource_pack", "Adapt resource pack");
  public static final TextKey HELP_OFFER = AdaptKeys.text("command.help.send_the_adapt_pack_again", "Send the Adapt pack again");

  private PackMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(PROMPT);
    builder.add(PROMPT_STRONG);
    builder.add(PROMPT_MOD);
    builder.add(SENT);
    builder.add(NO_PACK);
    builder.add(UNHOSTED);
    builder.add(CLIENT_DECLINED);
    builder.add(UNAVAILABLE);
    builder.add(CLIENT_MOD);
    builder.add(BEDROCK);
    builder.add(INVITE);
    builder.add(INVITE_PACK);
    builder.add(INVITE_MOD);
    builder.add(INVITE_MOD_LINK);
    builder.add(REQUIRED);
    builder.add(HELP_PACK);
    builder.add(HELP_OFFER);
  }
}
