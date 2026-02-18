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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
    setDescription(Localizer.dLocalize("taming.health.description"));
    setDisplayName(Localizer.dLocalize("taming.health.name"));
    setIcon(Material.COOKED_BEEF);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setInterval(4753);
    setCostFactor(getConfig().costFactor);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GOLDEN_APPLE)
        .key("challenge_taming_health_boost_1728k")
        .title(Localizer.dLocalize("advancement.challenge_taming_health_boost_1728k.title"))
        .description(Localizer.dLocalize("advancement.challenge_taming_health_boost_1728k.description"))
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

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  private record OwnerState(AdaptPlayer ownerData, Player owner, int level) {
  }

  @NoArgsConstructor
  @ConfigDescription("Increase your tamed animal maximum health.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Health Boost Factor for the Taming Health Boost adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double healthBoostFactor = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Health Boost Base for the Taming Health Boost adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double healthBoostBase = 0.57;
  }
}
