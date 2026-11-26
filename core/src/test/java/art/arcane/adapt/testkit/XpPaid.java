package art.arcane.adapt.testkit;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.XpEarnings;

/** Skill XP an adaptation asked to pay a player, read from the player's earnings ledger. */
public final class XpPaid {
  private XpPaid() {
  }

  public static double by(AdaptPlayer player, Adaptation<?> adaptation) {
    String marker = "adaptation:" + adaptation.getName() + ":";
    double paid = 0D;
    for (XpEarnings.SourceSnapshot source : player.getData().getXpEarnings().snapshot()) {
      if (source.source().contains(marker)) {
        paid += source.requested();
      }
    }
    return paid;
  }
}
