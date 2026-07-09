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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TamingHealthBoost extends SimpleAdaptation<TamingHealthBoost.Config> {
  private static final UUID MODIFIER = UUID.nameUUIDFromBytes("adapt-tame-health-boost".getBytes());
  private static final NamespacedKey MODIFIER_KEY = NamespacedKey.fromString("adapt:tame-health-boost");
  private static final double FOLIA_SCAN_RADIUS = 48D;
  private final Map<UUID, Integer> appliedLevels = new ConcurrentHashMap<>();

  public TamingHealthBoost() {
    super("tame-health");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.health");
    setIcon(Material.COOKED_BEEF);
    setInterval(4753);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GOLDEN_APPLE)
        .key("challenge_taming_health_boost_1728k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_taming_health_boost_1728k", "taming.health-boost.ticks-active", 1728000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getHealthBoost(level), 0) + C.GRAY + " " + Localizer.dLocalize("taming.health.lore1"));
  }

  private double getHealthBoost(int level) {
    return ((getLevelPercent(level) * getConfig().healthBoostFactor) + getConfig().healthBoostBase);
  }

  @Override
  public void onTick() {
    if (J.isFoliaThreading()) {
      onFoliaTick();
      pruneInvalidAppliedLevels();
      return;
    }

    Map<UUID, OwnerState> ownerStates = new HashMap<>();
    boolean hasActiveOwners = false;
    for (AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player owner = adaptPlayer.getPlayer();
      int level = getLevel(owner);
      if (level > 0) {
        ownerStates.put(owner.getUniqueId(), new OwnerState(adaptPlayer, owner, level));
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
          OwnerState state = ownerStates.get(p.getUniqueId());
          int level = state == null ? 0 : state.level();
          update(tameable, level);
          if (level > 0 && state != null) {
            state.ownerData().getData().addStat("taming.health-boost.ticks-active", 1);
          }
        }
      }
    }
    clearMissingAppliedLevels(seen);
  }

  private void onFoliaTick() {
    for (AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player owner = adaptPlayer.getPlayer();
      OwnerState state = new OwnerState(adaptPlayer, owner, getLevel(owner));
      J.runEntity(owner, () -> updateNearbyOwnedTameables(state));
    }
  }

  private void updateNearbyOwnedTameables(OwnerState state) {
    Player owner = state.owner();
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

      update(tameable, state.level());
      if (state.level() > 0) {
        state.ownerData().getData().addStat("taming.health-boost.ticks-active", 1);
      }
    }
  }

  private void update(Tameable j, int level) {
    UUID tameableId = j.getUniqueId();
    IAttribute attribute = Version.get().getAttribute(j, Attributes.GENERIC_MAX_HEALTH);
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

    attribute.setModifier(MODIFIER, MODIFIER_KEY, getHealthBoost(level), AttributeModifier.Operation.ADD_SCALAR);
    appliedLevels.put(tameableId, level);
    fx(j, FxPriority.TRANSITION)
        .ring(Particles.VILLAGER_HAPPY, 0.5D, 6, 0.3D)
        .column(Particle.HEART, 4, 1.2D)
        .chord(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4F, 1.5F, Sound.ENTITY_WOLF_PANT, 0.5F, 1.4F);
  }

  private void clearAppliedLevels() {
    if (appliedLevels.isEmpty()) {
      return;
    }

    for (UUID tameableId : new HashSet<>(appliedLevels.keySet())) {
      Entity entity = Bukkit.getEntity(tameableId);
      if (entity instanceof Tameable tameable) {
        IAttribute attribute = Version.get().getAttribute(tameable, Attributes.GENERIC_MAX_HEALTH);
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
        IAttribute attribute = Version.get().getAttribute(tameable, Attributes.GENERIC_MAX_HEALTH);
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

  private record OwnerState(AdaptPlayer ownerData, Player owner, int level) {
  }

  @ConfigDescription("Increase your tamed animal maximum health.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Health Boost Factor for the Taming Health Boost adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double healthBoostFactor = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Health Boost Base for the Taming Health Boost adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double healthBoostBase = 0.57;

    public Config() {
      baseCost = 6;
      costFactor = 0.4;
      initialCost = 3;
    }
  }
}
