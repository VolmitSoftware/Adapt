package art.arcane.adapt.localization.catalog;

import art.arcane.adapt.localization.AdaptKeys;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

/** The Adapt resource pack: its prompt and the {@code /adapt pack} replies. */
public final class PackMessages {
  public static final TextKey PROMPT = AdaptKeys.text("pack.prompt", "Adapt book menus");
  public static final TextKey SENT = AdaptKeys.text("pack.sent", "Sent the Adapt pack.");
  public static final TextKey NO_PACK = AdaptKeys.text("pack.no_pack", "No Adapt pack is available.");
  public static final TextKey UNHOSTED = AdaptKeys.text("pack.unhosted", "The server could not host the Adapt pack.");
  public static final TextKey DECLINED = AdaptKeys.text("pack.declined", "Run /adapt pack offer to load the Adapt pack for book menus.");
  public static final TextKey CLIENT_DECLINED = AdaptKeys.text("pack.client_declined",
      "Your client declined the Adapt pack. Rejoin with Server Resource Packs set to Prompt or Enabled, then run /adapt pack offer.");
  public static final TextKey UNAVAILABLE = AdaptKeys.text("pack.unavailable", "This server does not offer the Adapt pack.");
  public static final TextKey CLIENT_MOD = AdaptKeys.text("pack.client_mod", "Your Adapt client already shows book menus.");
  public static final TextKey BEDROCK = AdaptKeys.text("pack.bedrock", "Bedrock clients cannot load the Adapt pack.");
  public static final TextKey MENUS_BOOK = AdaptKeys.text("pack.menus.book", "Adapt menus: book.");
  public static final TextKey MENUS_CHEST = AdaptKeys.text("pack.menus.chest", "Adapt menus: chest.");
  public static final TextKey MENUS_PENDING = AdaptKeys.text("pack.menus.pending", "Book menus start once the Adapt pack is loaded.");
  public static final TextKey MENUS_AUTO = AdaptKeys.text("pack.menus.auto", "Adapt menus: book with the pack, chest without.");
  public static final TextKey MENUS_INVALID = AdaptKeys.text("pack.menus.invalid", "Use book, chest or auto.");
  public static final TextKey HELP_PACK = AdaptKeys.text("command.help.adapt_resource_pack", "Adapt resource pack");
  public static final TextKey HELP_OFFER = AdaptKeys.text("command.help.send_the_adapt_pack_again", "Send the Adapt pack again");
  public static final TextKey HELP_MENUS = AdaptKeys.text("command.help.choose_book_or_chest_menus", "Choose book or chest menus");
  public static final TextKey HELP_STYLE = AdaptKeys.text("command.help.book_chest_or_auto", "book, chest or auto");

  private PackMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(PROMPT);
    builder.add(SENT);
    builder.add(NO_PACK);
    builder.add(UNHOSTED);
    builder.add(DECLINED);
    builder.add(CLIENT_DECLINED);
    builder.add(UNAVAILABLE);
    builder.add(CLIENT_MOD);
    builder.add(BEDROCK);
    builder.add(MENUS_BOOK);
    builder.add(MENUS_CHEST);
    builder.add(MENUS_PENDING);
    builder.add(MENUS_AUTO);
    builder.add(MENUS_INVALID);
    builder.add(HELP_PACK);
    builder.add(HELP_OFFER);
    builder.add(HELP_MENUS);
    builder.add(HELP_STYLE);
  }
}
