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
import org.bukkit.Color;
import org.bukkit.Location;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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

public class TamingDamage extends SimpleAdaptation<TamingDamage.Config> {
  private static final UUID MODIFIER = UUID.nameUUIDFromBytes("adapt-tame-damage-boost".getBytes());
  private static final NamespacedKey MODIFIER_KEY = NamespacedKey.fromString("adapt:tame-damage-boost");
  private static final String LIFECYCLE_SCOPE = "tame-damage";
  private static final long ATTRIBUTE_REFRESH_MILLIS = 6119L;
  private static final long OWNER_LEVEL_REFRESH_MILLIS = 5000L;
  private static final long STATE_PRUNE_MILLIS = 60000L;
  private final TameableOwnershipIndex ownershipIndex = TameableOwnershipIndex.instance();
  private final TameableOwnershipIndex.Generation generation = ownershipIndex.claimGeneration(LIFECYCLE_SCOPE);
  private final Map<UUID, Integer> appliedLevels = new ConcurrentHashMap<>();
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
    setIcon(Material.FLINT);
    setInterval(50);
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
  public void unregister() {
    if (lifecycleCleanupStarted.compareAndSet(false, true)) {
      ownershipIndex.cleanupLoaded(generation, this::removeDamageModifier);
      ownerLevels.clear();
      nextUpdateAt.clear();
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
      refreshOwnerLevels(candidates, now);
    } else {
      ownerLevels.clear();
    }
    if (ownerLevels.isEmpty() && appliedLevels.isEmpty()) {
      pruneState(now);
      return;
    }

    ownershipIndex.ensureBootstrapped();
    ownershipIndex.discoverNearbyFolia(candidates, now);
    processTameables(now);
    pruneState(now);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (lifecycleCleanupStarted.get() || !ownershipIndex.isGenerationCurrent(generation)) {
      return;
    }

    if (e.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent dmgEvent
        && dmgEvent.getDamager() instanceof Tameable tam
        && tam.isTamed()
        && tam.getOwner() instanceof Player owner) {
      Location deathLocation = e.getEntity().getLocation();
      J.runEntity(owner, () -> recordPetKill(owner, deathLocation));
    }
    appliedLevels.remove(e.getEntity().getUniqueId());
    nextUpdateAt.remove(e.getEntity().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesUnloadEvent event) {
    for (Entity entity : event.getEntities()) {
      if (entity instanceof Tameable) {
        nextUpdateAt.remove(entity.getUniqueId());
      }
    }
  }

  private void refreshOwnerLevels(List<AdaptPlayer> online, long now) {
    if (now < nextOwnerLevelRefresh) {
      return;
    }

    if (J.isFoliaThreading()) {
      refreshFoliaOwnerLevels(online);
      nextOwnerLevelRefresh = now + OWNER_LEVEL_REFRESH_MILLIS;
      return;
    }

    Map<UUID, Integer> refreshed = new HashMap<>(online.size());
    for (AdaptPlayer adaptPlayer : online) {
      Player owner = adaptPlayer.getPlayer();
      if (owner == null || !owner.isOnline()) {
        continue;
      }

      int level = getActiveLevel(owner);
      if (level > 0) {
        refreshed.put(owner.getUniqueId(), level);
      }
    }
    ownerLevels.clear();
    ownerLevels.putAll(refreshed);
    nextOwnerLevelRefresh = now + OWNER_LEVEL_REFRESH_MILLIS;
  }

  private void refreshFoliaOwnerLevels(List<AdaptPlayer> online) {
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
      removeDamageModifier(tracked.entity());
      return;
    }

    update(tracked.entity(), ownerLevels.getOrDefault(ownerId, 0));
  }

  private void recordPetKill(Player owner, Location deathLocation) {
    ownershipIndex.runIfGenerationCurrent(generation, () -> {
      if (lifecycleCleanupStarted.get() || !hasActiveAdaptation(owner)) {
        return;
      }
      addStat(owner, "taming.damage.pet-kills", 1);
      fx(deathLocation, FxPriority.COMBAT)
          .burst(Particle.CRIT, 5, 0.3D)
          .particle(Particle.SWEEP_ATTACK, 1, 0, 0.6D, 0, 0, 0)
          .sound(Sound.ENTITY_WOLF_GROWL, 0.45F, 1.1F);
    });
  }

  private void update(Tameable j, int level) {
    UUID tameableId = j.getUniqueId();
    IAttribute attribute = Version.get().getAttribute(j, Attributes.ATTACK_DAMAGE);
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

    attribute.setTransientModifier(MODIFIER, MODIFIER_KEY, getDamageBoost(level), AttributeModifier.Operation.ADD_SCALAR);
    appliedLevels.put(tameableId, level);
    fx(j, FxPriority.TRANSITION)
        .ring(Particles.CRIT_MAGIC, 0.4D, 6, 0.6D)
        .dustBurst(Color.fromRGB(0xB0202A), 3, 0.3D, 1.0F)
        .chord(Sound.ENTITY_WOLF_GROWL, 0.5F, 0.8F, Sound.ITEM_TRIDENT_RETURN, 0.3F, 0.7F);
  }

  private void pruneState(long now) {
    if (now < nextStatePrune) {
      return;
    }

    nextUpdateAt.keySet().removeIf(entityId -> !ownershipIndex.contains(entityId));
    nextStatePrune = now + STATE_PRUNE_MILLIS;
  }

  private void removeDamageModifier(Tameable tameable) {
    if (!tameable.isValid() || tameable.isDead()) {
      appliedLevels.remove(tameable.getUniqueId());
      return;
    }

    IAttribute attribute = Version.get().getAttribute(tameable, Attributes.ATTACK_DAMAGE);
    if (attribute != null && attribute.hasModifier(MODIFIER, MODIFIER_KEY)) {
      attribute.removeModifier(MODIFIER, MODIFIER_KEY);
    }
    appliedLevels.remove(tameable.getUniqueId());
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
      baseCost = 6;
      costFactor = 0.4;
      initialCost = 5;
    }
  }
}
