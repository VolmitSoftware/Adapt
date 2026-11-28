package art.arcane.adapt.command;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.PackMessages;
import art.arcane.adapt.pack.link.PackDelivery;
import art.arcane.adapt.pack.link.PackOffers;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.command.FConst;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.localization.TextKey;

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

  /** The reply for an offer result. */
  private static TextKey reply(PackOffers.Result result) {
    return switch (result) {
      case OFFERED -> PackMessages.SENT;
      case NO_PACK -> PackMessages.NO_PACK;
      case UNHOSTED -> PackMessages.UNHOSTED;
      case UNAVAILABLE -> PackMessages.UNAVAILABLE;
      case CLIENT_MOD -> PackMessages.CLIENT_MOD;
      case BEDROCK -> PackMessages.BEDROCK;
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
