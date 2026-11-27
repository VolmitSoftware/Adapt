package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.golem.GolemMastery;
import art.arcane.adapt.content.golem.GolemRune;
import art.arcane.adapt.content.golem.GolemRuneEffects;
import art.arcane.adapt.content.golem.GolemRuneType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


final class RuneLore {
  private RuneLore() {
  }

  static List<String> describe(GolemRunicAugments.Config config, GolemRune rune, GolemMastery.Perks perks) {
    ActiveRunes active = new ActiveRunes(Map.of(rune.aspect(), rune.tier()), perks.tierUp(), perks.tierThreeBoost());
    GolemRuneEffects effects = GolemRunicAugments.runeEffects(config, active);
    List<String> lines = new ArrayList<>(5);
    if (perks.tierUp()) {
      GolemRuneEffects.Tier tier = GolemRuneEffects.effectiveTier(rune.tier(), true, perks.tierThreeBoost());
      lines.add(Text.key("adapt.golemancy.help.rune.effective").withMarkup("tier", String.valueOf(tier.tier())).withMarkup("value", number(tier.multiplier())).resolve());
    }
    switch (rune.aspect()) {
      case SWIFTNESS -> {
        add(lines, Text.key("adapt.golemancy.help.rune.speed"), active.value(config.swiftnessSpeedByTier, GolemRuneType.SWIFTNESS) * 100D);
        add(lines, Text.key("adapt.golemancy.help.rune.work"), (1D - GolemRunicAugments.runeWorkInterval(config, active)) * 100D);
      }
      case MIGHT -> {
        add(lines, Text.key("adapt.golemancy.help.rune.carry"), GolemRunicAugments.runeCarry(config, active).bonusFraction() * 100D);
        add(lines, Text.key("adapt.golemancy.help.rune.attack"), active.value(config.mightAttackByTier, GolemRuneType.MIGHT));
      }
      case EMBER -> {
        add(lines, Text.key("adapt.golemancy.help.rune.ignite"), active.count(config.emberIgniteSecondsByTier, GolemRuneType.EMBER));
        flag(lines, Text.key("adapt.golemancy.help.rune.fire"), active.flag(config.emberFireImmuneByTier, GolemRuneType.EMBER));
        flag(lines, Text.key("adapt.golemancy.help.rune.lava"), active.flag(config.emberLavaImmuneByTier, GolemRuneType.EMBER));
      }
      case TIDE -> {
        add(lines, Text.key("adapt.golemancy.help.rune.radius"), effects.radiusBonus());
        flag(lines, Text.key("adapt.golemancy.help.rune.water"), active.flag(config.tideWaterBreathingByTier, GolemRuneType.TIDE));
        add(lines, Text.key("adapt.golemancy.help.rune.swim"), active.value(config.tideSwimSpeedByTier, GolemRuneType.TIDE));
      }
      case ORDER -> {
        add(lines, Text.key("adapt.golemancy.help.rune.channels"), effects.channelSlots());
        add(lines, Text.key("adapt.golemancy.help.rune.retry"), (1D - effects.reclaimDelayMultiplier()) * 100D);
      }
      case ENTROPY -> {
        add(lines, Text.key("adapt.golemancy.help.rune.yield"), effects.bonusYield() * 100D);
        int level = active.count(config.entropyWeaknessLevelByTier, GolemRuneType.ENTROPY);
        int seconds = active.count(config.entropyWeaknessSecondsByTier, GolemRuneType.ENTROPY);
        if (level > 0 && seconds > 0) {
          lines.add(Text.key("adapt.golemancy.help.rune.weakness").withMarkup("level", String.valueOf(level)).withMarkup("value", String.valueOf(seconds)).resolve());
        }
      }
      case BULWARK -> {
        add(lines, Text.key("adapt.golemancy.help.rune.armor"), active.value(config.bulwarkArmorByTier, GolemRuneType.BULWARK));
        add(lines, Text.key("adapt.golemancy.help.rune.toughness"), active.value(config.bulwarkToughnessByTier, GolemRuneType.BULWARK));
        add(lines, Text.key("adapt.golemancy.help.rune.knockback"), active.value(config.bulwarkKnockbackResistanceByTier, GolemRuneType.BULWARK) * 100D);
      }
    }
    return List.copyOf(lines);
  }

  private static void add(List<String> lines, Text key, double value) {
    if (Double.isFinite(value) && value != 0D) {
      lines.add(key.withMarkup("value", String.valueOf(number(value))).resolve());
    }
  }

  private static void flag(List<String> lines, Text key, boolean active) {
    if (active) {
      lines.add(key.resolve());
    }
  }

  private static String number(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
  }
}
