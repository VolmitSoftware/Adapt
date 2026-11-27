package art.arcane.adapt.command;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.PackMessages;
import art.arcane.adapt.pack.link.MenuStyle;
import art.arcane.adapt.pack.link.PackDelivery;
import art.arcane.adapt.pack.link.PackOffers;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.command.FConst;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Locale;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

@Director(name = "pack", origin = DirectorOrigin.PLAYER, description = "Adapt resource pack", descriptionKey = "adapt.command.help.adapt_resource_pack")
public class CommandPack {
  static final String PERMISSION = "adapt.pack";

  @Director(name = "offer", origin = DirectorOrigin.PLAYER, description = "Send the Adapt pack again", descriptionKey = "adapt.command.help.send_the_adapt_pack_again")
  public void offer() {
    CommandSource sender = CommandContext.source();
    PlayerRef player = permitted(sender);
    if (player == null) {
      return;
    }
    PackDelivery delivery = PackDelivery.current();
    PackOffers.Result result = delivery == null ? PackOffers.Result.UNAVAILABLE : delivery.request(player);
    TextKey reply = reply(result);
    (result == PackOffers.Result.OFFERED ? FConst.success(AdaptLanguage.text(reply)) : FConst.error(AdaptLanguage.text(reply)))
        .send(sender);
  }

  @Director(name = "menus", origin = DirectorOrigin.PLAYER, description = "Choose book or chest menus", descriptionKey = "adapt.command.help.choose_book_or_chest_menus")
  public void menus(
      @Param(aliases = "style", description = "book, chest or auto", defaultValue = "auto", descriptionKey = "adapt.command.help.book_chest_or_auto")
      String style
  ) {
    CommandSource sender = CommandContext.source();
    PlayerRef player = permitted(sender);
    if (player == null) {
      return;
    }
    String normalized = style == null ? "auto" : style.trim().toLowerCase(Locale.ROOT);
    if (!normalized.equals("book") && !normalized.equals("chest") && !normalized.equals("auto")) {
      FConst.error(AdaptLanguage.text(PackMessages.MENUS_INVALID)).send(sender);
      return;
    }
    PackDelivery delivery = PackDelivery.current();
    if (delivery == null) {
      FConst.error(AdaptLanguage.text(PackMessages.UNAVAILABLE)).send(sender);
      return;
    }
    MenuStyle chosen = switch (normalized) {
      case "book" -> MenuStyle.BOOK;
      case "chest" -> MenuStyle.CHEST;
      default -> null;
    };
    CommandTargetExecutor.run(player, () -> {
      delivery.viewers().style(player.uuid(), chosen);
      if (chosen == MenuStyle.BOOK && !delivery.viewers().loaded(player)) {
        PackOffers.Result result = delivery.bookChosen(player);
        TextKey waiting = result == PackOffers.Result.OFFERED ? PackMessages.MENUS_PENDING : reply(result);
        CommandTargetExecutor.send(sender, FConst.info(AdaptLanguage.text(waiting)));
        return;
      }
      TextKey reply = chosen == null ? PackMessages.MENUS_AUTO
          : chosen == MenuStyle.BOOK ? PackMessages.MENUS_BOOK : PackMessages.MENUS_CHEST;
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(reply)));
    }, sender);
  }

  /** The reply for an offer result. */
  private static TextKey reply(PackOffers.Result result) {
    return switch (result) {
      case OFFERED -> PackMessages.SENT;
      case NO_PACK -> PackMessages.NO_PACK;
      case UNHOSTED -> PackMessages.UNHOSTED;
      case UNAVAILABLE -> PackMessages.UNAVAILABLE;
      case CLIENT_MOD -> PackMessages.CLIENT_MOD;
      case BEDROCK -> PackMessages.BEDROCK;
      case DECLINED -> PackMessages.DECLINED;
      case CLIENT_DECLINED -> PackMessages.CLIENT_DECLINED;
    };
  }

  private static PlayerRef permitted(CommandSource sender) {
    if (!sender.hasPermission(PERMISSION)) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", PERMISSION)))
          .send(sender);
      return null;
    }
    return CommandContext.player();
  }
}
