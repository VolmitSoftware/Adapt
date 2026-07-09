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
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TamingDamage extends SimpleAdaptation<TamingDamage.Config> {
  private static final UUID MODIFIER = UUID.nameUUIDFromBytes("adapt-tame-damage-boost".getBytes());
  private static final NamespacedKey MODIFIER_KEY = NamespacedKey.fromString("adapt:tame-damage-boost");
  private static final double FOLIA_SCAN_RADIUS = 48D;
  private final Map<UUID, Integer> appliedLevels = new ConcurrentHashMap<>();

  public TamingDamage() {
    super("tame-damage");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.damage");
    setIcon(Material.FLINT);
    setInterval(6119);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE)
        .key("challenge_taming_damage_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_SWORD)
            .key("challenge_taming_damage_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_taming_damage_500", "taming.damage.pet-kills", 500, 400);
    registerMilestone("challenge_taming_damage_5k", "taming.damage.pet-kills", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getDamageBoost(level), 0) + C.GRAY + " " + Localizer.dLocalize("taming.damage.lore1"));
  }

  private double getDamageBoost(int level) {
    return ((getLevelPercent(level) * getConfig().damageFactor) + getConfig().baseDamage);
  }

  @Override
  public void onTick() {
    if (J.isFoliaThreading()) {
      onFoliaTick();
      pruneInvalidAppliedLevels();
      return;
    }

    Map<UUID, Integer> ownerLevels = new HashMap<>();
    boolean hasActiveOwners = false;
    for (AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player owner = adaptPlayer.getPlayer();
      int level = getActiveLevel(owner);
      if (level > 0) {
        ownerLevels.put(owner.getUniqueId(), level);
        hasActiveOwners = true;
      }
    }

    if (!hasActiveOwners) {
      clearAppliedLevels();
      return;
    }

    Set<UUID> seen = new HashSet<>();
    for (World world : Bukkit.getServer().getWorlds()) {
      Collection<Tameable> tameables = world.getEntitiesByClass(Tameable.class);
      for (Tameable tameable : tameables) {
        if (tameable.isTamed() && tameable.getOwner() instanceof Player p) {
          seen.add(tameable.getUniqueId());
          update(tameable, ownerLevels.getOrDefault(p.getUniqueId(), 0));
        }
      }
    }
    clearMissingAppliedLevels(seen);
  }

  private void onFoliaTick() {
    for (AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player owner = adaptPlayer.getPlayer();
      int level = getActiveLevel(owner);
      J.runEntity(owner, () -> updateNearbyOwnedTameables(owner, level));
    }
  }

  private void updateNearbyOwnedTameables(Player owner, int level) {
    if (owner == null || !owner.isOnline()) {
      return;
    }

    for (Entity nearby : owner.getNearbyEntities(FOLIA_SCAN_RADIUS, FOLIA_SCAN_RADIUS, FOLIA_SCAN_RADIUS)) {
      if (!(nearby instanceof Tameable tameable) || !tameable.isTamed()) {
        continue;
      }
      if (!(tameable.getOwner() instanceof Player tameOwner) || !tameOwner.getUniqueId().equals(owner.getUniqueId())) {
        continue;
      }

      update(tameable, level);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (e.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent dmgEvent
        && dmgEvent.getDamager() instanceof Tameable tam
        && tam.isTamed()
        && tam.getOwner() instanceof Player p
        && hasActiveAdaptation(p)) {
      addStat(p, "taming.damage.pet-kills", 1);
      fx(e.getEntity().getLocation(), FxPriority.COMBAT)
          .burst(Particle.CRIT, 5, 0.3D)
          .particle(Particle.SWEEP_ATTACK, 1, 0, 0.6D, 0, 0, 0)
          .sound(Sound.ENTITY_WOLF_GROWL, 0.45F, 1.1F);
    }
    appliedLevels.remove(e.getEntity().getUniqueId());
  }

  private void update(Tameable j, int level) {
    UUID tameableId = j.getUniqueId();
    IAttribute attribute = Version.get().getAttribute(j, Attributes.GENERIC_ATTACK_DAMAGE);
    if (attribute == null) {
      appliedLevels.remove(tameableId);
      return;
    }

    Integer appliedLevel = appliedLevels.get(tameableId);
    if (level <= 0) {
      if (appliedLevel != null || attribute.hasModifier(MODIFIER, MODIFIER_KEY)) {
        attribute.removeModifier(MODIFIER, MODIFIER_KEY);
      }
      appliedLevels.remove(tameableId);
      return;
    }

    if (appliedLevel != null && appliedLevel == level) {
      return;
    }

    attribute.setModifier(MODIFIER, MODIFIER_KEY, getDamageBoost(level), AttributeModifier.Operation.ADD_SCALAR);
    appliedLevels.put(tameableId, level);
    fx(j, FxPriority.TRANSITION)
        .ring(Particles.CRIT_MAGIC, 0.4D, 6, 0.6D)
        .dustBurst(Color.fromRGB(0xB0202A), 3, 0.3D, 1.0F)
        .chord(Sound.ENTITY_WOLF_GROWL, 0.5F, 0.8F, Sound.ITEM_TRIDENT_RETURN, 0.3F, 0.7F);
  }

  private void clearAppliedLevels() {
    if (appliedLevels.isEmpty()) {
      return;
    }

    for (UUID tameableId : new HashSet<>(appliedLevels.keySet())) {
      Entity entity = Bukkit.getEntity(tameableId);
      if (entity instanceof Tameable tameable) {
        IAttribute attribute = Version.get().getAttribute(tameable, Attributes.GENERIC_ATTACK_DAMAGE);
        if (attribute != null && attribute.hasModifier(MODIFIER, MODIFIER_KEY)) {
          attribute.removeModifier(MODIFIER, MODIFIER_KEY);
        }
      }
      appliedLevels.remove(tameableId);
    }
  }

  private void clearMissingAppliedLevels(Set<UUID> seen) {
    if (appliedLevels.isEmpty()) {
      return;
    }

    for (UUID tameableId : new HashSet<>(appliedLevels.keySet())) {
      if (seen.contains(tameableId)) {
        continue;
      }

      Entity entity = Bukkit.getEntity(tameableId);
      if (entity instanceof Tameable tameable) {
        IAttribute attribute = Version.get().getAttribute(tameable, Attributes.GENERIC_ATTACK_DAMAGE);
        if (attribute != null && attribute.hasModifier(MODIFIER, MODIFIER_KEY)) {
          attribute.removeModifier(MODIFIER, MODIFIER_KEY);
        }
      }
      appliedLevels.remove(tameableId);
    }
  }

  private void pruneInvalidAppliedLevels() {
    if (appliedLevels.isEmpty()) {
      return;
    }

    for (UUID tameableId : new HashSet<>(appliedLevels.keySet())) {
      Entity entity = Bukkit.getEntity(tameableId);
      if (!(entity instanceof Tameable tameable) || !tameable.isValid() || tameable.isDead()) {
        appliedLevels.remove(tameableId);
      }
    }
  }

  @ConfigDescription("Increase your tamed animal damage dealt.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Damage for the Taming Damage adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseDamage = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Taming Damage adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 0.65;

    public Config() {
      baseCost = 6;
      costFactor = 0.4;
      initialCost = 5;
    }
  }
}
