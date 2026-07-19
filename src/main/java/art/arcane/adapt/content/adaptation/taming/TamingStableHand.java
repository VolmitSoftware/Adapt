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

import java.util.List;
import java.util.UUID;

public class TamingStableHand extends SimpleAdaptation<TamingStableHand.Config> {
  private static final UUID SPEED_MODIFIER = UUID.nameUUIDFromBytes("adapt-tame-stable-speed".getBytes());
  private static final NamespacedKey SPEED_KEY = NamespacedKey.fromString("adapt:tame-stable-speed");
  private static final UUID HEALTH_MODIFIER = UUID.nameUUIDFromBytes("adapt-tame-stable-health".getBytes());
  private static final NamespacedKey HEALTH_KEY = NamespacedKey.fromString("adapt:tame-stable-health");
  private static final UUID JUMP_MODIFIER = UUID.nameUUIDFromBytes("adapt-tame-stable-jump".getBytes());
  private static final NamespacedKey JUMP_KEY = NamespacedKey.fromString("adapt:tame-stable-jump");
  private static final UUID FALL_MODIFIER = UUID.nameUUIDFromBytes("adapt-tame-stable-fall".getBytes());
  private static final NamespacedKey FALL_KEY = NamespacedKey.fromString("adapt:tame-stable-fall");
  private static final double FALL_BLOCKS_PER_BIAS = 10.0;
  private static final List<ConfirmationDing> CONFIRMATION_DINGS = List.of(
      new ConfirmationDing(0, 1.2F),
      new ConfirmationDing(3, 1.5F),
      new ConfirmationDing(6, 1.8F));
  static double bias(double levelPercent, double base, double factor, double max) {
    return Math.min(max, base + (levelPercent * factor));
  }

  static double fallBonusBlocks(double bias) {
    return bias * FALL_BLOCKS_PER_BIAS;
  }

  public static boolean hasAppliedBias(LivingEntity animal) {
    if (animal == null) {
      return false;
    }

    IAttribute speed = Version.get().getAttribute(animal, Attributes.MOVEMENT_SPEED);
    IAttribute health = Version.get().getAttribute(animal, Attributes.MAX_HEALTH);
    Attribute jumpAttribute = Attributes.JUMP_STRENGTH;
    IAttribute jump = jumpAttribute == null ? null : Version.get().getAttribute(animal, jumpAttribute);
    return hasAppliedBias(speed, health, jump);
  }

  static boolean hasAppliedBias(IAttribute speed, IAttribute health, IAttribute jump) {
    return speed != null && speed.hasModifier(SPEED_MODIFIER, SPEED_KEY)
        || health != null && health.hasModifier(HEALTH_MODIFIER, HEALTH_KEY)
        || jump != null && jump.hasModifier(JUMP_MODIFIER, JUMP_KEY);
  }

  static List<ConfirmationDing> confirmationDings() {
    return CONFIRMATION_DINGS;
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
    v.addLore(C.GREEN + "+ " + Form.f(fallBonusBlocks(getBias(level)), 1) + C.GRAY + " " + Localizer.dLocalize("taming.stable_hand.lore2"));
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
    applied |= applyModifier(animal, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, SPEED_KEY, bias, AttributeModifier.Operation.ADD_SCALAR);
    Attribute jump = Attributes.JUMP_STRENGTH;
    if (jump != null) {
      applied |= applyModifier(animal, jump, JUMP_MODIFIER, JUMP_KEY, bias, AttributeModifier.Operation.ADD_SCALAR);
    }

    Attribute fall = Attributes.SAFE_FALL_DISTANCE;
    if (fall != null) {
      applied |= applyModifier(animal, fall, FALL_MODIFIER, FALL_KEY, fallBonusBlocks(bias), AttributeModifier.Operation.ADD_NUMBER);
    }

    IAttribute health = Version.get().getAttribute(animal, Attributes.MAX_HEALTH);
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
        .sound(Sound.ENTITY_HORSE_BREATHE, 0.4F, 1.2F);

    Player owner = Bukkit.getPlayer(ownerId);
    if (owner != null) {
      J.runEntity(owner, () -> creditShaped(owner));
    }
  }

  private boolean applyModifier(LivingEntity animal, Attribute attribute, UUID modifier, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
    IAttribute handle = Version.get().getAttribute(animal, attribute);
    if (handle == null || handle.hasModifier(modifier, key)) {
      return false;
    }

    handle.setModifier(modifier, key, amount, operation);
    return true;
  }

  private void creditShaped(Player owner) {
    if (!owner.isOnline()) {
      return;
    }
    addStat(owner, "taming.stable-hand.animals-shaped", 1);
    xp(owner, getConfig().xpPerAnimal);
    playConfirmation(owner);
  }

  private void playConfirmation(Player owner) {
    if (!areSoundsEnabled()) {
      return;
    }

    for (ConfirmationDing ding : CONFIRMATION_DINGS) {
      Runnable playback = () -> {
        if (owner.isOnline()) {
          owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.65F, ding.pitch());
        }
      };
      if (ding.delayTicks() == 0) {
        playback.run();
      } else {
        J.runEntity(owner, playback, ding.delayTicks());
      }
    }
  }

  private double getBias(int level) {
    return bias(getLevelPercent(level), getConfig().biasBase, getConfig().biasFactor, getConfig().maxBias);
  }


  @ConfigDescription("Animals you tame or breed keep a permanent bias toward better speed, jump, health, and safe fall distance.")
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

  record ConfirmationDing(int delayTicks, float pitch) {
  }
}
