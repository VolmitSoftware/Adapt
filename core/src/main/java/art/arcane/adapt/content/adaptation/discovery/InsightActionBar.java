package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.client.view.InsightStat;
import art.arcane.adapt.client.view.InsightView;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.adapt.localization.catalog.KineticsMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.TextKey;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/** The one-line action bar readout players without the client mod or Gloss see for their Insight target. */
final class InsightActionBar {
  private static final String SEPARATOR = C.DARK_GRAY + " | ";

  private InsightActionBar() {
  }

  static String text(InsightView view, String species) {
    StringBuilder line = new StringBuilder(96);
    line.append(C.AQUA).append(view.name().isEmpty() ? species : view.name());
    line.append(SEPARATOR).append(C.RED).append("❤ ").append(C.WHITE).append(number(view.health()))
        .append(C.GRAY).append('/').append(number(view.maxHealth()));
    if (view.absorption() > 0D) {
      line.append(C.YELLOW).append(" +").append(number(view.absorption()));
    }
    if (view.armor() > 0D) {
      stat(line, DiscoveryMessages.INSIGHT_ARMOR, number(view.armor()));
    }
    for (InsightStat stat : view.stats()) {
      if (stat.value() != 0D) {
        stat(line, label(stat), Form.f(stat.value(), decimals(stat)));
      }
    }
    if (!view.effects().isEmpty()) {
      stat(line, DiscoveryMessages.INSIGHT_EFFECTS, Integer.toString(view.effects().size()));
    }
    String bond = switch (view.bond()) {
      case NONE -> "";
      case WILD -> C.GRAY + AdaptLanguage.text(DiscoveryMessages.INSIGHT_WILD);
      case YOURS -> C.GREEN + AdaptLanguage.text(DiscoveryMessages.INSIGHT_YOURS);
      case OWNED -> C.GRAY + (view.ownerName().isEmpty() ? AdaptLanguage.text(DiscoveryMessages.INSIGHT_TAMED)
          : AdaptLanguage.text(DiscoveryMessages.INSIGHT_TAMED_BY, trusted("owner", C.WHITE + view.ownerName())));
    };
    if (!bond.isEmpty()) {
      line.append(SEPARATOR).append(bond);
    }
    if (view.stableHand()) {
      line.append(SEPARATOR).append(C.AQUA).append(AdaptLanguage.text(DiscoveryMessages.INSIGHT_STABLE_HAND));
    }
    return line.toString();
  }

  private static void stat(StringBuilder line, TextKey label, String value) {
    line.append(SEPARATOR).append(C.GRAY).append(AdaptLanguage.text(DiscoveryMessages.INSIGHT_STAT,
        trusted("label", AdaptLanguage.text(label)), trusted("value", C.WHITE + value)));
  }

  private static TextKey label(InsightStat stat) {
    return switch (stat.kind()) {
      case ATTACK -> DiscoveryMessages.INSIGHT_ATTACK;
      case SPEED -> DiscoveryMessages.INSIGHT_SPEED;
      case JUMP -> DiscoveryMessages.INSIGHT_JUMP;
      case TOUGHNESS -> KineticsMessages.QUAKE_GUARD_LORE2;
      case KNOCKBACK_RESISTANCE -> KineticsMessages.QUAKE_GUARD_LORE1;
      case FOLLOW_RANGE -> DiscoveryMessages.INSIGHT_LORE1;
    };
  }

  private static int decimals(InsightStat stat) {
    return switch (stat.kind()) {
      case SPEED, JUMP, KNOCKBACK_RESISTANCE -> 2;
      case ATTACK, TOUGHNESS, FOLLOW_RANGE -> 1;
    };
  }

  private static String number(double value) {
    double rounded = Math.round(value * 10D) / 10D;
    return rounded == Math.rint(rounded) ? Long.toString((long) rounded) : Form.f(rounded, 1);
  }
}
