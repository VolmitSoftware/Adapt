/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.util.reflect.registries.RegistryUtil;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityTameEvent;

import java.util.UUID;

public class TamingStableHand extends SimpleAdaptation<TamingStableHand.Config> {
  private static final UUID SPEED_MODIFIER = UUID.nameUUIDFromBytes("adapt-tame-stable-speed".getBytes());
  private static final NamespacedKey SPEED_KEY = NamespacedKey.fromString("adapt:tame-stable-speed");
  private static final UUID HEALTH_MODIFIER = UUID.nameUUIDFromBytes("adapt-tame-stable-health".getBytes());
  private static final NamespacedKey HEALTH_KEY = NamespacedKey.fromString("adapt:tame-stable-health");
  private static final UUID JUMP_MODIFIER = UUID.nameUUIDFromBytes("adapt-tame-stable-jump".getBytes());
  private static final NamespacedKey JUMP_KEY = NamespacedKey.fromString("adapt:tame-stable-jump");
  private static volatile Attribute jumpAttribute;
  private static volatile boolean jumpAttributeResolved;

  private static Attribute jumpAttribute() {
    if (!jumpAttributeResolved) {
      jumpAttribute = RegistryUtil.findOptional(Attribute.class, "generic_jump_strength", "jump_strength").orElse(null);
      jumpAttributeResolved = true;
    }
    return jumpAttribute;
  }

  static double bias(double levelPercent, double base, double factor, double max) {
    return Math.min(max, base + (levelPercent * factor));
  }

  public TamingStableHand() {
    super("tame-stable-hand");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.stable_hand");
    setIcon(Material.SADDLE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SADDLE)
        .key("challenge_taming_stable_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_HORSE_ARMOR)
            .key("challenge_taming_stable_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_taming_stable_100", "taming.stable-hand.animals-shaped", 100, 400);
    registerMilestone("challenge_taming_stable_1k", "taming.stable-hand.animals-shaped", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getBias(level), 0) + C.GRAY + " " + Localizer.dLocalize("taming.stable_hand.lore1"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityTameEvent e) {
    if (!(e.getOwner() instanceof Player p) || !(e.getEntity() instanceof LivingEntity animal)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    double bias = getBias(level);
    UUID ownerId = p.getUniqueId();
    J.runEntity(animal, () -> applyBias(animal, bias, ownerId));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityBreedEvent e) {
    if (!(e.getBreeder() instanceof Player p) || !(e.getEntity() instanceof LivingEntity child)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    double bias = getBias(level);
    UUID ownerId = p.getUniqueId();
    J.runEntity(child, () -> applyBias(child, bias, ownerId), 1);
  }

  private void applyBias(LivingEntity animal, double bias, UUID ownerId) {
    if (!animal.isValid() || animal.isDead() || bias <= 0) {
      return;
    }

    boolean applied = false;
    applied |= applyModifier(animal, Attributes.GENERIC_MOVEMENT_SPEED, SPEED_MODIFIER, SPEED_KEY, bias);
    Attribute jump = jumpAttribute();
    if (jump != null) {
      applied |= applyModifier(animal, jump, JUMP_MODIFIER, JUMP_KEY, bias);
    }

    IAttribute health = Version.get().getAttribute(animal, Attributes.GENERIC_MAX_HEALTH);
    if (health != null && !health.hasModifier(HEALTH_MODIFIER, HEALTH_KEY)) {
      health.setModifier(HEALTH_MODIFIER, HEALTH_KEY, bias, AttributeModifier.Operation.ADD_SCALAR);
      double newMax = health.getValue();
      if (newMax > 0 && !Double.isNaN(newMax)) {
        animal.setHealth(Math.min(newMax, Math.max(animal.getHealth(), newMax)));
      }
      applied = true;
    }

    if (!applied) {
      return;
    }

    fx(animal, FxPriority.TRANSITION)
        .ring(Particles.VILLAGER_HAPPY, 0.6D, 8, 0.3D)
        .column(Particle.HEART, 3, 1.0D)
        .chord(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.6F, Sound.ENTITY_HORSE_BREATHE, 0.4F, 1.2F);

    Player owner = Bukkit.getPlayer(ownerId);
    if (owner != null) {
      J.runEntity(owner, () -> creditShaped(owner));
    }
  }

  private boolean applyModifier(LivingEntity animal, Attribute attribute, UUID modifier, NamespacedKey key, double bias) {
    IAttribute handle = Version.get().getAttribute(animal, attribute);
    if (handle == null || handle.hasModifier(modifier, key)) {
      return false;
    }

    handle.setModifier(modifier, key, bias, AttributeModifier.Operation.ADD_SCALAR);
    return true;
  }

  private void creditShaped(Player owner) {
    if (!owner.isOnline()) {
      return;
    }
    addStat(owner, "taming.stable-hand.animals-shaped", 1);
    xp(owner, getConfig().xpPerAnimal);
  }

  private double getBias(int level) {
    return bias(getLevelPercent(level), getConfig().biasBase, getConfig().biasFactor, getConfig().maxBias);
  }


  @ConfigDescription("Animals you tame or breed keep a permanent bias toward better speed, jump, and health.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bias Base for the Taming Stable Hand adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double biasBase = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bias Factor for the Taming Stable Hand adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double biasFactor = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Bias for the Taming Stable Hand adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBias = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted each time a tamed or bred animal receives the stable-hand bias.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerAnimal = 20;

    public Config() {
      baseCost = 5;
      costFactor = 0.5;
      initialCost = 3;
    }
  }
}
