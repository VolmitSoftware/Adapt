package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.insight.InsightStat;
import art.arcane.adapt.api.insight.InsightView;
import art.arcane.adapt.platform.value.Id;


/** The one-line action bar readout players without the client mod or Gloss see for their Insight target. */
final class InsightActionBar {
  private static final String SEPARATOR = TextFormat.DARK_GRAY + " | ";

  private InsightActionBar() {
  }

  static String text(InsightView view, String species) {
    StringBuilder line = new StringBuilder(96);
    line.append(TextFormat.AQUA).append(view.name().isEmpty() ? species : view.name());
    line.append(SEPARATOR).append(TextFormat.RED).append("❤ ").append(TextFormat.WHITE).append(number(view.health()))
        .append(TextFormat.GRAY).append('/').append(number(view.maxHealth()));
    if (view.absorption() > 0D) {
      line.append(TextFormat.YELLOW).append(" +").append(number(view.absorption()));
    }
    if (view.armor() > 0D) {
      stat(line, Text.key("adapt.discovery.insight.armor"), number(view.armor()));
    }
    for (InsightStat stat : view.stats()) {
      if (stat.value() != 0D) {
        stat(line, label(stat), Format.f(stat.value(), decimals(stat)));
      }
    }
    if (!view.effects().isEmpty()) {
      stat(line, Text.key("adapt.discovery.insight.effects"), Integer.toString(view.effects().size()));
    }
    String bond = switch (view.bond()) {
      case NONE -> "";
      case WILD -> TextFormat.GRAY + Text.key("adapt.discovery.insight.wild").resolve();
      case YOURS -> TextFormat.GREEN + Text.key("adapt.discovery.insight.yours").resolve();
      case OWNED -> TextFormat.GRAY + (view.ownerName().isEmpty() ? Text.key("adapt.discovery.insight.tamed").resolve()
          : Text.key("adapt.discovery.insight.tamed_by").withMarkup("owner", TextFormat.WHITE + view.ownerName()).resolve());
    };
    if (!bond.isEmpty()) {
      line.append(SEPARATOR).append(bond);
    }
    if (view.tags().contains(InsightReadout.STABLE_HAND)) {
      line.append(SEPARATOR).append(TextFormat.AQUA).append(Text.key("adapt.discovery.insight.stable_hand").resolve());
    }
    return line.toString();
  }

  private static void stat(StringBuilder line, Text label, String value) {
    line.append(SEPARATOR).append(TextFormat.GRAY).append(Text.key("adapt.discovery.insight.stat").withMarkup("label", label.resolve()).withMarkup("value", TextFormat.WHITE + value).resolve());
  }

  private static Text label(InsightStat stat) {
    Id attribute = stat.attribute();
    if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
      return Text.key("adapt.discovery.insight.attack");
    }
    if (attribute.equals(Attributes.MOVEMENT_SPEED)) {
      return Text.key("adapt.discovery.insight.speed");
    }
    if (attribute.equals(Attributes.JUMP_STRENGTH)) {
      return Text.key("adapt.discovery.insight.jump");
    }
    if (attribute.equals(Attributes.ARMOR_TOUGHNESS)) {
      return Text.key("adapt.kinetics.quake_guard.lore2");
    }
    if (attribute.equals(Attributes.KNOCKBACK_RESISTANCE)) {
      return Text.key("adapt.kinetics.quake_guard.lore1");
    }
    if (attribute.equals(Attributes.FOLLOW_RANGE)) {
      return Text.key("adapt.discovery.insight.lore1");
    }
    return Text.literal(attribute.path());
  }

  private static int decimals(InsightStat stat) {
    Id attribute = stat.attribute();
    return attribute.equals(Attributes.MOVEMENT_SPEED) || attribute.equals(Attributes.JUMP_STRENGTH)
        || attribute.equals(Attributes.KNOCKBACK_RESISTANCE) ? 2 : 1;
  }

  private static String number(double value) {
    double rounded = Math.round(value * 10D) / 10D;
    return rounded == Math.rint(rounded) ? Long.toString((long) rounded) : Format.f(rounded, 1);
  }
}
