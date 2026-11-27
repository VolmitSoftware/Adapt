package com.example.geology;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Event-driven: broken stone sometimes yields a sample, dropped or put straight into the inventory. */
public final class CoreSample extends SimpleAdaptation<CoreSample.Config> {
  public static final Id ID = Id.of(GeologyAddon.NAMESPACE, "geology/core_sample");
  public static final PlayerPreference<Delivery> DELIVERY = PlayerPreference.of("delivery", Delivery.class, Delivery.DROP,
      List.of(new PlayerPreference.Choice<>(Delivery.DROP, Id.minecraft("dropper"), 1),
          new PlayerPreference.Choice<>(Delivery.INVENTORY, Id.minecraft("chest"), 2)));
  public static final String SAMPLES_STAT = "example.geology.samples";

  public CoreSample() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("flint"));
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, DELIVERY);
  }

  @Override
  public void addStats(int level, StatLines lines) {
    statLore(lines, Format.pc(chance(level), 0), 1);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(BlockBreakEvent event) {
    PlayerRef player = event.player();
    if (!Rocks.isStone(event.block().type())) {
      return;
    }
    int level = getActiveBlockBreakLevel(player, event.block().location());
    if (level <= 0 || ThreadLocalRandom.current().nextDouble() >= chance(level)) {
      return;
    }
    ItemRef sample = AdaptApi.platform().host().items().create(pick(), 1);
    if (preference(player, DELIVERY) == Delivery.INVENTORY) {
      safeGiveItem(player, sample);
    } else {
      event.block().world().dropItemNaturally(event.block().location().add(0.5D, 0.5D, 0.5D).position(), sample);
    }
    addStat(player, SAMPLES_STAT, 1D);
  }

  private Id pick() {
    List<String> samples = getConfig().samples.isEmpty() ? List.of("flint") : getConfig().samples;
    return Id.parse(samples.get(ThreadLocalRandom.current().nextInt(samples.size())));
  }

  private double chance(int level) {
    return Math.min(1D, getConfig().chancePerLevel * level);
  }

  public enum Delivery {
    DROP,
    INVENTORY
  }

  public static final class Config extends AdaptationConfig {
    @ConfigDoc("Chance per level that broken stone yields a sample.")
    public double chancePerLevel = 0.08D;
    @ConfigDoc("Items a sample can be, as item ids.")
    public List<String> samples = new ArrayList<>(List.of("flint", "raw_iron", "amethyst_shard"));

    public Config() {
      baseCost = 3;
      initialCost = 1;
      costFactor = 0.45D;
      maxLevel = 5;
      requires = new ArrayList<>(List.of("adapt:pickaxe/gem_polish@1"));
    }
  }
}
