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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.world.ChunkEntitiesEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

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

public class TamingDamage extends SimpleAdaptation<TamingDamage.Config> {
  private static final String LIFECYCLE_SCOPE = "tame-damage";
  private static final long ATTRIBUTE_REFRESH_MILLIS = 6119L;
  private static final long OWNER_LEVEL_REFRESH_MILLIS = 5000L;
  private static final long STATE_PRUNE_MILLIS = 60000L;
  private final TameableOwnershipIndex ownershipIndex = TameableOwnershipIndex.instance();
  private final TameableOwnershipIndex.Generation generation = ownershipIndex.claimGeneration(LIFECYCLE_SCOPE);
  private final TameAttributeBoost boost = new TameAttributeBoost(Id.of("adapt", "tame-damage-boost"), Attributes.ATTACK_DAMAGE, this::showBoost);
  private final Map<UUID, Long> nextUpdateAt = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> ownerLevels = new ConcurrentHashMap<>();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean();
  private Iterator<TameableOwnershipIndex.TrackedTameable> workCursor = Collections.emptyIterator();
  private long nextOwnerLevelRefresh;
  private long nextStatePrune;

  public TamingDamage() {
    super("tame-damage");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.damage");
    setIcon(Id.minecraft("flint"));
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_taming_damage_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_sword"))
            .key("challenge_taming_damage_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_damage_500", "taming.damage.pet-kills", 500, 400);
    registerMilestone("challenge_taming_damage_5k", "taming.damage.pet-kills", 5000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getDamageBoost(level), 0), 1);
  }

  private double getDamageBoost(int level) {
    return ((getLevelPercent(level) * getConfig().damageFactor) + getConfig().baseDamage);
  }

  @Override
  public void unregister() {
    if (lifecycleCleanupStarted.compareAndSet(false, true)) {
      ownershipIndex.cleanupLoaded(generation, boost::remove);
      ownerLevels.clear();
      nextUpdateAt.clear();
      boost.clear();
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
      refreshOwnerLevels(candidates, now);
    } else {
      ownerLevels.clear();
    }
    if (ownerLevels.isEmpty() && boost.isEmpty()) {
      pruneState(now);
      return;
    }

    ownershipIndex.ensureBootstrapped();
    ownershipIndex.discoverNearbyFolia(candidates, now);
    processTameables(now);
    pruneState(now);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    if (lifecycleCleanupStarted.get() || !ownershipIndex.isGenerationCurrent(generation)) {
      return;
    }

    LivingRef dead = e.entity();
    LastDamage lastDamage = dead.lastDamage();
    EntityRef damager = lastDamage == null ? null : lastDamage.damager();
    PlayerRef owner = damager != null && damager.has(EntityTrait.TAMEABLE)
        && AdaptCore.platform().host().animals().isTamed(damager) && damager.tameOwner() != null
        ? AdaptCore.platform().host().player(damager.tameOwner())
        : null;
    if (owner != null) {
      Loc deathLocation = dead.location();
      Tasks.runEntity(owner, () -> recordPetKill(owner, deathLocation));
    }
    boost.forget(dead.uuid());
    nextUpdateAt.remove(dead.uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ChunkEntitiesEvent event) {
    if (event.phase() != ChunkEntitiesEvent.Phase.UNLOAD) {
      return;
    }
    for (EntityRef entity : event.entities()) {
      if (entity.has(EntityTrait.TAMEABLE)) {
        nextUpdateAt.remove(entity.uuid());
      }
    }
  }

  private void refreshOwnerLevels(List<AdaptPlayer> online, long now) {
    if (now < nextOwnerLevelRefresh) {
      return;
    }

    if (Tasks.isFoliaThreading()) {
      refreshFoliaOwnerLevels(online);
      nextOwnerLevelRefresh = now + OWNER_LEVEL_REFRESH_MILLIS;
      return;
    }

    Map<UUID, Integer> refreshed = new HashMap<>(online.size());
    for (AdaptPlayer adaptPlayer : online) {
      PlayerRef owner = adaptPlayer.getPlayer();
      if (owner == null || !owner.isOnline()) {
        continue;
      }

      int level = getActiveLevel(owner);
      if (level > 0) {
        refreshed.put(owner.uuid(), level);
      }
    }
    ownerLevels.clear();
    ownerLevels.putAll(refreshed);
    nextOwnerLevelRefresh = now + OWNER_LEVEL_REFRESH_MILLIS;
  }

  private void refreshFoliaOwnerLevels(List<AdaptPlayer> online) {
    Set<UUID> onlineIds = new HashSet<>(online.size());
    for (AdaptPlayer adaptPlayer : online) {
      PlayerRef owner = adaptPlayer.getPlayer();
      if (owner == null) {
        continue;
      }

      UUID ownerId = owner.uuid();
      onlineIds.add(ownerId);
      Tasks.runEntity(owner, () -> ownershipIndex.runIfGenerationCurrent(generation, () -> {
        if (lifecycleCleanupStarted.get()) {
          return;
        }
        if (!owner.isOnline()) {
          ownerLevels.remove(ownerId);
          return;
        }
        int level = getActiveLevel(owner);
        if (level > 0) {
          ownerLevels.put(ownerId, level);
        } else {
          ownerLevels.remove(ownerId);
        }
      }));
    }
    ownerLevels.keySet().removeIf(ownerId -> !onlineIds.contains(ownerId));
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
      if (Tasks.isFoliaThreading()) {
        LivingRef entity = tracked.entity();
        if (entity != null) {
          Tasks.runEntity(entity, updateTask);
        }
      } else {
        updateTask.run();
      }
    }
  }

  private void updateTrackedTameable(TameableOwnershipIndex.TrackedTameable tracked) {
    UUID ownerId = ownershipIndex.refreshOwner(tracked);
    LivingRef entity = tracked.entity();
    if (entity == null) {
      boost.forget(tracked.entityId());
      return;
    }
    if (ownerId == null) {
      boost.remove(entity);
      return;
    }

    int level = ownerLevels.getOrDefault(ownerId, 0);
    boost.update(entity, level, getDamageBoost(level));
  }

  private void recordPetKill(PlayerRef owner, Loc deathLocation) {
    ownershipIndex.runIfGenerationCurrent(generation, () -> {
      if (lifecycleCleanupStarted.get() || !hasActiveAdaptation(owner)) {
        return;
      }
      addStat(owner, "taming.damage.pet-kills", 1);
      fx(deathLocation, FxPriority.COMBAT)
          .burst(Particles.CRIT, 5, 0.3D)
          .particle(Particles.SWEEP_ATTACK, 1, 0, 0.6D, 0, 0, 0)
          .sound(Sounds.ENTITY_WOLF_GROWL, 0.45F, 1.1F);
    });
  }

  private void showBoost(LivingRef j) {
    fx(j, FxPriority.TRANSITION)
        .ring(Particles.CRIT_MAGIC, 0.4D, 6, 0.6D)
        .dustBurst(Rgb.of(0xB0202A), 3, 0.3D, 1.0F)
        .chord(Sounds.ENTITY_WOLF_GROWL, 0.5F, 0.8F, Sounds.ITEM_TRIDENT_RETURN, 0.3F, 0.7F);
  }

  private void pruneState(long now) {
    if (now < nextStatePrune) {
      return;
    }

    nextUpdateAt.keySet().removeIf(entityId -> !ownershipIndex.contains(entityId));
    nextStatePrune = now + STATE_PRUNE_MILLIS;
  }

  @ConfigDescription("Increase your tamed animal damage dealt.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Damage for the Taming Damage adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseDamage = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Taming Damage adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 0.65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum loaded tameables examined per scheduler pass.", impact = "Higher values refresh very large pet populations faster but increase per-tick work.")
    int maxTameablesPerPass = 128;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
