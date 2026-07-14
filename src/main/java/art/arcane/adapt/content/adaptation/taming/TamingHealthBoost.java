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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class TamingHealthBoost extends SimpleAdaptation<TamingHealthBoost.Config> {
  private static final UUID MODIFIER = UUID.nameUUIDFromBytes("adapt-tame-health-boost".getBytes());
  private static final NamespacedKey MODIFIER_KEY = NamespacedKey.fromString("adapt:tame-health-boost");
  private static final String LIFECYCLE_SCOPE = "tame-health";
  private static final long ATTRIBUTE_REFRESH_MILLIS = 4753L;
  private static final long OWNER_LEVEL_REFRESH_MILLIS = 1000L;
  private static final long STATE_PRUNE_MILLIS = 60000L;
  private final TameableOwnershipIndex ownershipIndex = TameableOwnershipIndex.instance();
  private final TameableOwnershipIndex.Generation generation = ownershipIndex.claimGeneration(LIFECYCLE_SCOPE);
  private final Map<UUID, Integer> appliedLevels = new ConcurrentHashMap<>();
  private final Map<UUID, Long> nextUpdateAt = new ConcurrentHashMap<>();
  private final Map<UUID, Long> activeStatAt = new ConcurrentHashMap<>();
  private final Map<UUID, OwnerState> ownerStates = new ConcurrentHashMap<>();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean();
  private Iterator<TameableOwnershipIndex.TrackedTameable> workCursor = Collections.emptyIterator();
  private long nextOwnerLevelRefresh;
  private long nextStatePrune;

  static double elapsedActiveTicks(Long previous, long now) {
    if (previous == null || now <= previous) {
      return 0D;
    }
    return (now - previous) / 50D;
  }

  public TamingHealthBoost() {
    super("tame-health");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.health");
    setIcon(Material.COOKED_BEEF);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GOLDEN_APPLE)
        .key("challenge_taming_health_boost_72k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_taming_health_boost_72k", "taming.health-boost.ticks-active", 72000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getHealthBoost(level), 0) + C.GRAY + " " + Localizer.dLocalize("taming.health.lore1"));
  }

  private double getHealthBoost(int level) {
    return ((getLevelPercent(level) * getConfig().healthBoostFactor) + getConfig().healthBoostBase);
  }

  @Override
  public void unregister() {
    if (lifecycleCleanupStarted.compareAndSet(false, true)) {
      ownershipIndex.cleanupLoaded(generation, this::removeHealthModifier);
      ownerStates.clear();
      nextUpdateAt.clear();
      activeStatAt.clear();
      appliedLevels.clear();
    }
    super.unregister();
  }

  @Override
  public void onTick() {
    if (lifecycleCleanupStarted.get() || !ownershipIndex.isGenerationCurrent(generation)) {
      return;
    }

    long now = System.currentTimeMillis();
    List<AdaptPlayer> candidates = isEnabled() ? learnedCandidates(now) : List.of();
    if (isEnabled()) {
      refreshOwnerStates(candidates, now);
    } else {
      ownerStates.clear();
    }
    if (ownerStates.isEmpty() && appliedLevels.isEmpty()) {
      pruneState(now);
      return;
    }

    ownershipIndex.ensureBootstrapped();
    ownershipIndex.discoverNearbyFolia(candidates, now);
    processTameables(now);
    pruneState(now);
  }

  private void refreshOwnerStates(List<AdaptPlayer> online, long now) {
    if (now < nextOwnerLevelRefresh) {
      return;
    }

    if (J.isFoliaThreading()) {
      refreshFoliaOwnerStates(online);
      nextOwnerLevelRefresh = now + OWNER_LEVEL_REFRESH_MILLIS;
      return;
    }

    Map<UUID, OwnerState> refreshed = new HashMap<>(online.size());
    for (AdaptPlayer adaptPlayer : online) {
      Player owner = adaptPlayer.getPlayer();
      if (owner == null || !owner.isOnline()) {
        continue;
      }

      int level = getActiveLevel(owner);
      if (level > 0) {
        refreshed.put(owner.getUniqueId(), new OwnerState(adaptPlayer, level));
      }
    }
    ownerStates.clear();
    ownerStates.putAll(refreshed);
    nextOwnerLevelRefresh = now + OWNER_LEVEL_REFRESH_MILLIS;
  }

  private void refreshFoliaOwnerStates(List<AdaptPlayer> online) {
    Set<UUID> onlineIds = new HashSet<>(online.size());
    for (AdaptPlayer adaptPlayer : online) {
      Player owner = adaptPlayer.getPlayer();
      if (owner == null) {
        continue;
      }

      UUID ownerId = owner.getUniqueId();
      onlineIds.add(ownerId);
      J.runEntity(owner, () -> ownershipIndex.runIfGenerationCurrent(generation, () -> {
        if (lifecycleCleanupStarted.get()) {
          return;
        }
        if (!owner.isOnline()) {
          ownerStates.remove(ownerId);
          return;
        }

        int level = getActiveLevel(owner);
        if (level > 0) {
          ownerStates.put(ownerId, new OwnerState(adaptPlayer, level));
        } else {
          ownerStates.remove(ownerId);
        }
      }));
    }
    ownerStates.keySet().removeIf(ownerId -> !onlineIds.contains(ownerId));
  }

  private void processTameables(long now) {
    if (!workCursor.hasNext()) {
      workCursor = ownershipIndex.iterator();
    }

    int limit = Math.max(1, getConfig().maxTameablesPerPass);
    int examined = 0;
    while (examined < limit && workCursor.hasNext()) {
      TameableOwnershipIndex.TrackedTameable tracked = workCursor.next();
      examined++;

      UUID entityId = tracked.entityId();
      Long nextUpdate = nextUpdateAt.get(entityId);
      if (nextUpdate != null && now < nextUpdate) {
        continue;
      }

      nextUpdateAt.put(entityId, now + ATTRIBUTE_REFRESH_MILLIS);
      Runnable updateTask = () -> ownershipIndex.runIfGenerationCurrent(generation, () -> {
        if (!lifecycleCleanupStarted.get()) {
          updateTrackedTameable(tracked);
        }
      });
      if (J.isFoliaThreading()) {
        J.runEntity(tracked.entity(), updateTask);
      } else {
        updateTask.run();
      }
    }
  }

  private void updateTrackedTameable(TameableOwnershipIndex.TrackedTameable tracked) {
    UUID ownerId = ownershipIndex.refreshOwner(tracked);
    if (ownerId == null) {
      removeHealthModifier(tracked.entity());
      nextUpdateAt.remove(tracked.entityId());
      activeStatAt.remove(tracked.entityId());
      return;
    }

    OwnerState state = ownerStates.get(ownerId);
    int level = state == null ? 0 : state.level();
    update(tracked.entity(), level);
    if (state != null && level > 0 && appliedLevels.containsKey(tracked.entityId())) {
      double activeTicks = activeTicksSinceLastUpdate(tracked.entityId());
      if (activeTicks > 0D) {
        addActiveTicks(state.ownerData(), activeTicks);
      }
    } else {
      activeStatAt.remove(tracked.entityId());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent event) {
    clearEntityState(event.getEntity().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesUnloadEvent event) {
    for (Entity entity : event.getEntities()) {
      if (entity instanceof Tameable) {
        nextUpdateAt.remove(entity.getUniqueId());
        activeStatAt.remove(entity.getUniqueId());
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

    if (appliedLevel != null && appliedLevel == level && attribute.hasModifier(MODIFIER, MODIFIER_KEY)) {
      return;
    }

    attribute.setTransientModifier(MODIFIER, MODIFIER_KEY, getHealthBoost(level), AttributeModifier.Operation.ADD_SCALAR);
    appliedLevels.put(tameableId, level);
    fx(j, FxPriority.TRANSITION)
        .ring(Particles.VILLAGER_HAPPY, 0.5D, 6, 0.3D)
        .column(Particle.HEART, 4, 1.2D)
        .chord(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4F, 1.5F, Sound.ENTITY_WOLF_PANT, 0.5F, 1.4F);
  }

  private void pruneState(long now) {
    if (now < nextStatePrune) {
      return;
    }

    nextUpdateAt.keySet().removeIf(entityId -> !ownershipIndex.contains(entityId));
    activeStatAt.keySet().removeIf(entityId -> !ownershipIndex.contains(entityId));
    nextStatePrune = now + STATE_PRUNE_MILLIS;
  }

  private double activeTicksSinceLastUpdate(UUID entityId) {
    long now = System.currentTimeMillis();
    Long previous = activeStatAt.put(entityId, now);
    return elapsedActiveTicks(previous, now);
  }

  private void addActiveTicks(AdaptPlayer ownerData, double activeTicks) {
    Player owner = ownerData.getPlayer();
    if (owner == null) {
      return;
    }

    J.runEntity(owner, () -> ownershipIndex.runIfGenerationCurrent(generation, () -> {
      if (!lifecycleCleanupStarted.get()) {
        ownerData.getData().addStat("taming.health-boost.ticks-active", activeTicks);
      }
    }));
  }

  private void clearEntityState(UUID entityId) {
    appliedLevels.remove(entityId);
    nextUpdateAt.remove(entityId);
    activeStatAt.remove(entityId);
  }

  private void removeHealthModifier(Tameable tameable) {
    if (!tameable.isValid() || tameable.isDead()) {
      appliedLevels.remove(tameable.getUniqueId());
      return;
    }

    IAttribute attribute = Version.get().getAttribute(tameable, Attributes.GENERIC_MAX_HEALTH);
    if (attribute != null && attribute.hasModifier(MODIFIER, MODIFIER_KEY)) {
      attribute.removeModifier(MODIFIER, MODIFIER_KEY);
    }
    appliedLevels.remove(tameable.getUniqueId());
  }

  private record OwnerState(AdaptPlayer ownerData, int level) {
  }

  @ConfigDescription("Increase your tamed animal maximum health.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Health Boost Factor for the Taming Health Boost adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double healthBoostFactor = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Health Boost Base for the Taming Health Boost adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double healthBoostBase = 0.57;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum loaded tameables examined per scheduler pass.", impact = "Higher values refresh very large pet populations faster but increase per-tick work.")
    int maxTameablesPerPass = 128;

    public Config() {
      baseCost = 6;
      costFactor = 0.4;
      initialCost = 3;
    }
  }
}
