package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneEffects;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemHelpMessages;
import art.arcane.volmlib.util.localization.TextKey;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class RuneLore {
  private RuneLore() {
  }

  static List<String> describe(GolemRunicAugments.Config config, GolemRune rune, GolemMastery.Perks perks) {
    ActiveRunes active = new ActiveRunes(Map.of(rune.aspect(), rune.tier()), perks.tierUp(), perks.tierThreeBoost());
    GolemRuneEffects effects = GolemRunicAugments.runeEffects(config, active);
    List<String> lines = new ArrayList<>(5);
    if (perks.tierUp()) {
      GolemRuneEffects.Tier tier = GolemRuneEffects.effectiveTier(rune.tier(), true, perks.tierThreeBoost());
      lines.add(AdaptLanguage.text(GolemHelpMessages.RUNE_EFFECTIVE, trusted("tier", tier.tier()), trusted("value", number(tier.multiplier()))));
    }
    switch (rune.aspect()) {
      case SWIFTNESS -> {
        add(lines, GolemHelpMessages.RUNE_SPEED, active.value(config.swiftnessSpeedByTier, GolemRuneType.SWIFTNESS) * 100D);
        add(lines, GolemHelpMessages.RUNE_WORK, (1D - GolemRunicAugments.runeWorkInterval(config, active)) * 100D);
      }
      case MIGHT -> {
        add(lines, GolemHelpMessages.RUNE_CARRY, GolemRunicAugments.runeCarry(config, active).bonusFraction() * 100D);
        add(lines, GolemHelpMessages.RUNE_ATTACK, active.value(config.mightAttackByTier, GolemRuneType.MIGHT));
      }
      case EMBER -> {
        add(lines, GolemHelpMessages.RUNE_IGNITE, active.count(config.emberIgniteSecondsByTier, GolemRuneType.EMBER));
        flag(lines, GolemHelpMessages.RUNE_FIRE, active.flag(config.emberFireImmuneByTier, GolemRuneType.EMBER));
        flag(lines, GolemHelpMessages.RUNE_LAVA, active.flag(config.emberLavaImmuneByTier, GolemRuneType.EMBER));
      }
      case TIDE -> {
        add(lines, GolemHelpMessages.RUNE_RADIUS, effects.radiusBonus());
        flag(lines, GolemHelpMessages.RUNE_WATER, active.flag(config.tideWaterBreathingByTier, GolemRuneType.TIDE));
        add(lines, GolemHelpMessages.RUNE_SWIM, active.value(config.tideSwimSpeedByTier, GolemRuneType.TIDE));
      }
      case ORDER -> {
        add(lines, GolemHelpMessages.RUNE_CHANNELS, effects.channelSlots());
        add(lines, GolemHelpMessages.RUNE_RETRY, (1D - effects.reclaimDelayMultiplier()) * 100D);
      }
      case ENTROPY -> {
        add(lines, GolemHelpMessages.RUNE_YIELD, effects.bonusYield() * 100D);
        int level = active.count(config.entropyWeaknessLevelByTier, GolemRuneType.ENTROPY);
        int seconds = active.count(config.entropyWeaknessSecondsByTier, GolemRuneType.ENTROPY);
        if (level > 0 && seconds > 0) {
          lines.add(AdaptLanguage.text(GolemHelpMessages.RUNE_WEAKNESS, trusted("level", level), trusted("value", seconds)));
        }
      }
      case BULWARK -> {
        add(lines, GolemHelpMessages.RUNE_ARMOR, active.value(config.bulwarkArmorByTier, GolemRuneType.BULWARK));
        add(lines, GolemHelpMessages.RUNE_TOUGHNESS, active.value(config.bulwarkToughnessByTier, GolemRuneType.BULWARK));
        add(lines, GolemHelpMessages.RUNE_KNOCKBACK, active.value(config.bulwarkKnockbackResistanceByTier, GolemRuneType.BULWARK) * 100D);
      }
    }
    return List.copyOf(lines);
  }

  private static void add(List<String> lines, TextKey key, double value) {
    if (Double.isFinite(value) && value != 0D) {
      lines.add(AdaptLanguage.text(key, trusted("value", number(value))));
    }
  }

  private static void flag(List<String> lines, TextKey key, boolean active) {
    if (active) {
      lines.add(AdaptLanguage.text(key));
    }
  }

  private static String number(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
  }
}
