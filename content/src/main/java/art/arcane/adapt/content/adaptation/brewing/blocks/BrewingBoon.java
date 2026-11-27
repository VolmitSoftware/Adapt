package art.arcane.adapt.content.adaptation.brewing.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** One helpful effect, picked at random from the configured list. */
public class BrewingBoon extends BoonBlock<BrewingBoon.Config> {
  public BrewingBoon() {
    super(SkillBrewing.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("glistering_melon_slice"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    List<String> effects = getConfig().effects;
    if (effects.isEmpty()) {
      return Outcome.MISSED;
    }
    Id effect = Id.tryParse(effects.get(ThreadLocalRandom.current().nextInt(effects.size())));
    PlayerRef player = use.player();
    int ticks = (int) (use.scaled(getConfig().durationMillisAtFirstLevel, getConfig().durationMillisAtTopLevel) / 50D);
    if (effect == null || !player.addEffect(EffectInstance.of(effect, ticks, 0))) {
      return Outcome.MISSED;
    }
    return Outcome.lasting(player, ticks);
  }

  @ConfigDescription("A random helpful potion effect for 6 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Effects the Boon picks from, as effect ids.", impact = "Fewer entries make the effect predictable.")
    List<String> effects = List.of("minecraft:speed", "minecraft:haste", "minecraft:strength",
        "minecraft:jump_boost", "minecraft:regeneration", "minecraft:resistance", "minecraft:fire_resistance",
        "minecraft:water_breathing", "minecraft:night_vision", "minecraft:absorption");
    @ConfigDoc(value = "How long the effect lasts at level 1, in milliseconds.", impact = "Higher values keep the effect longer.")
    double durationMillisAtFirstLevel = 6_000D;
    @ConfigDoc(value = "How long the effect lasts at the top level, in milliseconds.", impact = "Higher values keep the effect longer.")
    double durationMillisAtTopLevel = 10_000D;
  }
}
