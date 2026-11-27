package com.example.geology;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** Passive: extra armor for as long as the adaptation is learned and enabled. */
public final class StoneSkin extends SimpleAdaptation<StoneSkin.Config> {
  public static final Id ID = Id.of(GeologyAddon.NAMESPACE, "geology/stone_skin");
  private static final String SLOT = "armor";

  public StoneSkin() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("stone"));
    setInterval(1000L);
  }

  @Override
  public void addStats(int level, StatLines lines) {
    statLore(lines, "+" + Format.f(armor(level), 1), 1);
  }

  @Override
  public void onTick() {
    for (PlayerRef player : AdaptApi.platform().host().onlinePlayers()) {
      AdaptApi.scheduler().runEntity(player, () -> reconcile(player));
    }
  }

  @Override
  public void unregister() {
    for (PlayerRef player : AdaptApi.platform().host().onlinePlayers()) {
      AdaptApi.scheduler().runEntity(player, () -> AdaptAttributeService.get().removeAll(player, getId().toString()));
    }
    super.unregister();
  }

  private void reconcile(PlayerRef player) {
    int level = getActiveLevel(player);
    if (level > 0) {
      AdaptAttributeService.get().apply(player, getId().toString(), SLOT, Attributes.ARMOR, armor(level),
          AttributeOperation.ADD_VALUE);
    } else {
      AdaptAttributeService.get().remove(player, getId().toString(), SLOT, Attributes.ARMOR);
    }
  }

  private double armor(int level) {
    return getConfig().armorPerLevel * level;
  }

  public static final class Config extends AdaptationConfig {
    @ConfigDoc("Armor points added per level.")
    public double armorPerLevel = 1.5D;

    public Config() {
      baseCost = 2;
      initialCost = 1;
      costFactor = 0.4D;
      maxLevel = 4;
    }
  }
}
