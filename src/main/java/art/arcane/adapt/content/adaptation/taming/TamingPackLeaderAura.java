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
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class TamingPackLeaderAura extends SimpleAdaptation<TamingPackLeaderAura.Config> {
  private static final String LIFECYCLE_SCOPE = "tame-pack-leader-aura";
  private static final long OWNER_REFRESH_MILLIS = 500L;
  private static final long OWNER_SNAPSHOT_MAX_AGE_MILLIS = 5000L;
  private static final long PET_REFRESH_MILLIS = 1000L;
  private static final long STATE_PRUNE_MILLIS = 60000L;

  private final TameableOwnershipIndex ownershipIndex = TameableOwnershipIndex.instance();
  private final TameableOwnershipIndex.Generation generation = ownershipIndex.claimGeneration(LIFECYCLE_SCOPE);
  private final Cooldowns ringCd = cooldowns();
  private final Map<UUID, OwnerAuraSnapshot> ownerSnapshots = new ConcurrentHashMap<>();
  private final Map<UUID, Long> nextOwnerRefreshAt = new ConcurrentHashMap<>();
  private final Map<UUID, Long> nextPetRefreshAt = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastPetBuffAt = new ConcurrentHashMap<>();
  private final Map<UUID, Double> pendingBuffedTicks = new ConcurrentHashMap<>();
  private final Set<UUID> pendingOwnerRefreshes = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingPetRefreshes = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean();
  private Iterator<TameableOwnershipIndex.TrackedTameable> petCursor = Collections.emptyIterator();
  private int ownerCursor = 0;
  private long nextStatePruneAt;

  public TamingPackLeaderAura() {
    super("tame-pack-leader-aura");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.pack_leader_aura");
    setIcon(Material.BONE);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE)
        .key("challenge_taming_pack_72k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_taming_pack_72k", "taming.pack-leader.buffed-ticks", 72000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.f(getRadius(level)) + C.GRAY + " " + Localizer.dLocalize("taming.pack_leader_aura.lore1"));
    v.addLore(C.GREEN + "+ " + (1 + getAmplifier(level)) + C.GRAY + " " + Localizer.dLocalize("taming.pack_leader_aura.lore2"));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    UUID ownerId = event.getPlayer().getUniqueId();
    removeOwner(ownerId);
    pendingBuffedTicks.remove(ownerId);
    pendingOwnerRefreshes.remove(ownerId);
  }

  @Override
  public void unregister() {
    if (lifecycleCleanupStarted.compareAndSet(false, true)) {
      ownerSnapshots.clear();
      nextOwnerRefreshAt.clear();
      nextPetRefreshAt.clear();
      lastPetBuffAt.clear();
      pendingBuffedTicks.clear();
      pendingOwnerRefreshes.clear();
      pendingPetRefreshes.clear();
    }
    super.unregister();
  }

  @Override
  public void onTick() {
    if (lifecycleCleanupStarted.get() || !ownershipIndex.isGenerationCurrent(generation)) {
      return;
    }

    long now = System.currentTimeMillis();
    List<AdaptPlayer> owners = learnedCandidates(now);
    ownershipIndex.ensureBootstrapped();
    refreshOwnerBatch(owners, now);
    refreshPetBatch(now);
    pruneState(now);
  }

  private void refreshOwnerBatch(List<AdaptPlayer> owners, long now) {
    int size = owners.size();
    if (size == 0) {
      ownerCursor = 0;
      return;
    }

    int limit = Math.min(size, PackLeaderWorkLimits.ownerRefreshes(getConfig().maxOwnersPerPass));
    int start = Math.floorMod(ownerCursor, size);
    for (int i = 0; i < limit; i++) {
      queueOwnerRefresh(owners.get((start + i) % size), now);
    }
    ownerCursor = (start + limit) % size;
  }

  private void queueOwnerRefresh(AdaptPlayer ownerData, long now) {
    Player owner = ownerData.getPlayer();
    if (owner == null) {
      return;
    }

    UUID ownerId = owner.getUniqueId();
    Long nextRefresh = nextOwnerRefreshAt.get(ownerId);
    if ((nextRefresh != null && now < nextRefresh) || !pendingOwnerRefreshes.add(ownerId)) {
      return;
    }

    nextOwnerRefreshAt.put(ownerId, now + OWNER_REFRESH_MILLIS);
    if (!J.runEntity(owner, () -> refreshOwnerOwned(ownerData, owner))) {
      pendingOwnerRefreshes.remove(ownerId);
      nextOwnerRefreshAt.remove(ownerId);
    }
  }

  private void refreshOwnerOwned(AdaptPlayer ownerData, Player owner) {
    UUID ownerId = owner.getUniqueId();
    try {
      if (lifecycleCleanupStarted.get() || !ownershipIndex.isGenerationCurrent(generation) || !owner.isOnline()) {
        removeOwner(ownerId);
        return;
      }

      Double accruedTicks = pendingBuffedTicks.remove(ownerId);
      if (accruedTicks != null && accruedTicks > 0D) {
        ownerData.getData().addStat("taming.pack-leader.buffed-ticks", accruedTicks);
      }

      int level = getActiveLevel(owner);
      if (level <= 0) {
        removeOwner(ownerId);
        return;
      }

      Location ownerLocation = owner.getLocation();
      double radius = getRadius(level);
      int amplifier = getAmplifier(level);
      ownerSnapshots.put(ownerId, new OwnerAuraSnapshot(
          ownerLocation.getWorld().getUID(),
          ownerLocation.getX(),
          ownerLocation.getY(),
          ownerLocation.getZ(),
          radius * radius,
          amplifier,
          Math.max(1, getConfig().effectTicks),
          System.currentTimeMillis()));

      if (accruedTicks != null && accruedTicks > 0D && ringCd.isReady(ownerId, 1000L)) {
        ringCd.mark(ownerId);
        fx(ownerLocation, FxPriority.AMBIENT)
            .dustRing(Color.fromRGB(0xE8E0C8), 1.8D + (amplifier * 0.4D), 8, 1.0F);
      }
    } finally {
      pendingOwnerRefreshes.remove(ownerId);
    }
  }

  private void refreshPetBatch(long now) {
    if (!petCursor.hasNext()) {
      petCursor = ownershipIndex.iterator();
    }

    int limit = PackLeaderWorkLimits.tameableRefreshes(getConfig().maxTameablesPerPass);
    int examined = 0;
    while (examined < limit && petCursor.hasNext()) {
      TameableOwnershipIndex.TrackedTameable tracked = petCursor.next();
      examined++;
      UUID entityId = tracked.entityId();
      Long nextRefresh = nextPetRefreshAt.get(entityId);
      if ((nextRefresh != null && now < nextRefresh) || !pendingPetRefreshes.add(entityId)) {
        continue;
      }

      nextPetRefreshAt.put(entityId, now + PET_REFRESH_MILLIS);
      if (!J.runEntity(tracked.entity(), () -> refreshPetOwned(tracked))) {
        pendingPetRefreshes.remove(entityId);
        nextPetRefreshAt.remove(entityId);
      }
    }
  }

  private void refreshPetOwned(TameableOwnershipIndex.TrackedTameable tracked) {
    UUID entityId = tracked.entityId();
    try {
      if (lifecycleCleanupStarted.get() || !ownershipIndex.isGenerationCurrent(generation)) {
        return;
      }

      UUID ownerId = ownershipIndex.refreshOwner(tracked);
      OwnerAuraSnapshot snapshot = ownerId == null ? null : ownerSnapshots.get(ownerId);
      long now = System.currentTimeMillis();
      if (snapshot == null || now - snapshot.capturedAt() > OWNER_SNAPSHOT_MAX_AGE_MILLIS) {
        lastPetBuffAt.remove(entityId);
        return;
      }

      Tameable tameable = tracked.entity();
      Location petLocation = tameable.getLocation();
      if (!snapshot.worldId().equals(petLocation.getWorld().getUID())
          || snapshot.distanceSquared(petLocation) > snapshot.radiusSquared()) {
        lastPetBuffAt.remove(entityId);
        return;
      }

      tameable.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, snapshot.effectTicks(), snapshot.amplifier(), false, false));
      tameable.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, snapshot.effectTicks(), snapshot.amplifier(), false, false));
      Long previous = lastPetBuffAt.put(entityId, now);
      double elapsedTicks = PackLeaderCadence.elapsedTicks(previous, now, 250L, snapshot.effectTicks() * 50L);
      pendingBuffedTicks.merge(ownerId, elapsedTicks, Double::sum);
    } finally {
      pendingPetRefreshes.remove(entityId);
    }
  }

  private void removeOwner(UUID ownerId) {
    ownerSnapshots.remove(ownerId);
    nextOwnerRefreshAt.remove(ownerId);
  }

  private void pruneState(long now) {
    if (now < nextStatePruneAt) {
      return;
    }

    ownerSnapshots.entrySet().removeIf(entry -> now - entry.getValue().capturedAt() > OWNER_SNAPSHOT_MAX_AGE_MILLIS);
    nextPetRefreshAt.keySet().removeIf(entityId -> !ownershipIndex.contains(entityId));
    lastPetBuffAt.keySet().removeIf(entityId -> !ownershipIndex.contains(entityId));
    nextStatePruneAt = now + STATE_PRUNE_MILLIS;
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private int getAmplifier(int level) {
    return Math.max(0, (int) Math.floor(getLevelPercent(level) * getConfig().maxAmplifier));
  }

  private record OwnerAuraSnapshot(UUID worldId, double x, double y, double z, double radiusSquared,
                                   int amplifier, int effectTicks, long capturedAt) {
    private double distanceSquared(Location location) {
      double dx = x - location.getX();
      double dy = y - location.getY();
      double dz = z - location.getZ();
      return (dx * dx) + (dy * dy) + (dz * dz);
    }
  }

  @ConfigDescription("Nearby tamed companions gain speed and regeneration near their owner.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Taming Pack Leader Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Taming Pack Leader Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Amplifier for the Taming Pack Leader Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxAmplifier = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effect Ticks for the Taming Pack Leader Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int effectTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum owners processed per scheduler tick, capped internally at 16.", impact = "Lower values reduce burst workload but spread updates across more ticks.")
    int maxOwnersPerPass = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum indexed tameables examined per scheduler tick, capped internally at 48.", impact = "Higher values refresh very large pet populations faster but schedule more entity-owned work per tick.")
    int maxTameablesPerPass = 48;

    public Config() {
      baseCost = 3;
      costFactor = 0.65;
      initialCost = 3;
    }
  }
}

final class PackLeaderCadence {
  private PackLeaderCadence() {
  }

  static double elapsedTicks(Long previous, long now, long initialMillis, long maximumMillis) {
    long boundedInitial = Math.max(50L, initialMillis);
    long boundedMaximum = Math.max(boundedInitial, maximumMillis);
    long elapsed = previous == null
        ? boundedInitial
        : Math.min(boundedMaximum, Math.max(50L, now - previous));
    return elapsed / 50D;
  }
}

final class PackLeaderWorkLimits {
  private static final int MAX_OWNER_REFRESHES_PER_TICK = 16;
  private static final int MAX_TAMEABLE_REFRESHES_PER_TICK = 48;

  private PackLeaderWorkLimits() {
  }

  static int ownerRefreshes(int configured) {
    return Math.min(MAX_OWNER_REFRESHES_PER_TICK, Math.max(1, configured));
  }

  static int tameableRefreshes(int configured) {
    return Math.min(MAX_TAMEABLE_REFRESHES_PER_TICK, Math.max(1, configured));
  }
}
