package art.arcane.adapt.testkit;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.XpEarnings;

/** Skill XP an adaptation asked to pay a player, read from the player's earnings ledger. */
public final class XpPaid {
  private XpPaid() {
  }

  public static double by(AdaptPlayer player, Adaptation<?> adaptation) {
    String marker = "adaptation:" + adaptation.getId().toString() + ":";
    double paid = 0D;
    for (XpEarnings.SourceSnapshot source : player.getData().getXpEarnings().snapshot()) {
      if (source.source().contains(marker)) {
        paid += source.requested();
      }
    }
    return paid;
  }
}
