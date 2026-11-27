package com.example.geology;

import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.concurrent.ThreadLocalRandom;

/** Added to the built-in Pickaxes skill: breaking ore sometimes grants a short burst of Haste. */
public final class Aftershock extends SimpleAdaptation<Aftershock.Config> {
  public static final Id ID = Id.of(GeologyAddon.NAMESPACE, "pickaxe/aftershock");
  private static final Id HASTE = Id.minecraft("haste");

  public Aftershock() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("golden_pickaxe"));
  }

  @Override
  public void addStats(int level, StatLines lines) {
    statLore(lines, Format.pc(chance(level), 0), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent event) {
    PlayerRef player = event.player();
    if (!Rocks.isOre(event.block().type()) || !Rocks.isPickaxe(player.inventory().mainHand())) {
      return;
    }
    int level = getActiveBlockBreakLevel(player, event.block().location());
    if (level > 0 && ThreadLocalRandom.current().nextDouble() < chance(level)) {
      addPotionStacks(player, HASTE, Math.max(0, level / 2), getConfig().hasteTicks, false);
    }
  }

  private double chance(int level) {
    return Math.min(1D, getConfig().chancePerLevel * level);
  }

  public static final class Config extends AdaptationConfig {
    @ConfigDoc("Chance per level that broken ore grants Haste.")
    public double chancePerLevel = 0.1D;
    @ConfigDoc("Haste duration in ticks.")
    public int hasteTicks = 100;

    public Config() {
      baseCost = 2;
      initialCost = 1;
      maxLevel = 3;
    }
  }
}
