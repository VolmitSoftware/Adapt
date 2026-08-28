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
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.bukkit.Particle.HEART;

public class TamingHealthRegeneration extends SimpleAdaptation<TamingHealthRegeneration.Config> {
  private static final int MAX_COOLDOWN_ENTRIES = 8192;
  private static final int MAX_PENDING_REQUESTS = 1024;
  private static final int MAX_REQUESTS_PER_TICK = 64;
  private static final long REGENERATION_COOLDOWN_MILLIS = 8000L;
  private static final long COOLDOWN_PRUNE_INTERVAL_MILLIS = 1000L;
  private static final long LEARNED_OWNER_REFRESH_INTERVAL_MILLIS = 1000L;

  private final Object cooldownLock = new Object();
  private final Map<UUID, Long> nextRegenerationAt = new HashMap<>();
  private final ArrayBlockingQueue<RegenerationRequest> pendingRegenerations = new ArrayBlockingQueue<>(MAX_PENDING_REQUESTS);
  private final AtomicBoolean lifecycleClosed = new AtomicBoolean(false);
  private volatile Set<UUID> learnedOwnerIds = Set.of();
  private long nextCooldownPrune;
  private long nextLearnedOwnerRefresh;

  public TamingHealthRegeneration() {
    super("tame-health-regeneration");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.regeneration");
    setIcon(Material.GOLDEN_APPLE);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GLISTERING_MELON_SLICE)
        .key("challenge_taming_regen_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_taming_regen_1k", "taming.health-regen.health-regened", 1000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRegenSpeed(level), 0), 1);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (lifecycleClosed.get()
        || !isEnabled()
        || !getSkill().isEnabled()
        || !(e.getEntity() instanceof Tameable tameable)
        || !tameable.isTamed()
        || !(tameable.getOwner() instanceof Player owner)
        || e.getFinalDamage() <= 0D) {
      return;
    }

    UUID ownerId = owner.getUniqueId();
    if (!learnedOwnerIds.contains(ownerId)) {
      return;
    }

    UUID tameableId = tameable.getUniqueId();
    long now = M.ms();
    if (!reserveCooldown(tameableId, now)) {
      return;
    }

    if (!pendingRegenerations.offer(new RegenerationRequest(owner, ownerId, tameable, tameableId))) {
      removeCooldown(tameableId);
    }
  }

  private void prepareRegeneration(Player owner, UUID ownerId, Tameable tameable, UUID tameableId) {
    if (lifecycleClosed.get() || !owner.isOnline() || !owner.getUniqueId().equals(ownerId)) {
      return;
    }

    int level = getActiveLevel(owner);
    if (level <= 0) {
      return;
    }

    double requestedHealing = getRegenSpeed(level);
    if (requestedHealing <= 0D || !Double.isFinite(requestedHealing)) {
      return;
    }

    J.runEntity(tameable, () -> applyRegeneration(owner, ownerId, tameable, tameableId, requestedHealing));
  }

  private void applyRegeneration(Player owner, UUID ownerId, Tameable tameable, UUID tameableId, double requestedHealing) {
    if (lifecycleClosed.get()
        || !tameable.isValid()
        || tameable.isDead()
        || !tameable.getUniqueId().equals(tameableId)
        || !(tameable.getOwner() instanceof Player currentOwner)
        || !currentOwner.getUniqueId().equals(ownerId)) {
      return;
    }

    IAttribute attribute = Version.get().getAttribute(tameable, Attributes.MAX_HEALTH);
    double currentHealth = tameable.getHealth();
    double maxHealth = attribute == null ? currentHealth : attribute.getValue();
    double actualHealing = actualHealing(currentHealth, maxHealth, requestedHealing);
    if (actualHealing <= 0D) {
      return;
    }

    tameable.setHealth(currentHealth + actualHealing);
    playRegenerationEffect(tameable, actualHealing);
    J.runEntity(owner, () -> recordHealing(owner, ownerId, actualHealing));
  }

  private void recordHealing(Player owner, UUID ownerId, double actualHealing) {
    if (lifecycleClosed.get() || !owner.isOnline() || !owner.getUniqueId().equals(ownerId)) {
      return;
    }
    addStat(owner, "taming.health-regen.health-regened", actualHealing);
  }

  private void playRegenerationEffect(Tameable tameable, double actualHealing) {
    int duration = Math.max(4, Math.min(7, 4 + (int) Math.ceil(actualHealing / 2D)));
    int heartCount = Math.max(1, Math.min(2, (int) Math.ceil(actualHealing / 3D)));
    timeline(tameable)
        .duration(duration)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.particle(HEART, heartCount, 0, 0.4D + progress, 0, 0.12D, 0.01D);
          if (tick == 0) {
            fx.particle(Particles.VILLAGER_HAPPY, 3, 0, 0.1D, 0, 0.25D, 0)
                .chord(Sound.ENTITY_WOLF_PANT, 0.5F, 1.3F, Sound.BLOCK_NOTE_BLOCK_BELL, 0.3F, 1.8F);
          }
        })
        .start();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    removeCooldown(e.getEntity().getUniqueId());
  }

  private double getRegenSpeed(int level) {
    return ((getLevelPercent(level) * (getLevelPercent(level)) * getConfig().regenFactor) + getConfig().regenBase);
  }

  static double actualHealing(double currentHealth, double maxHealth, double requestedHealing) {
    if (!Double.isFinite(currentHealth)
        || !Double.isFinite(maxHealth)
        || !Double.isFinite(requestedHealing)
        || currentHealth < 0D
        || maxHealth <= currentHealth
        || requestedHealing <= 0D) {
      return 0D;
    }
    return Math.min(requestedHealing, maxHealth - currentHealth);
  }

  private boolean reserveCooldown(UUID tameableId, long now) {
    synchronized (cooldownLock) {
      Long nextRegeneration = nextRegenerationAt.get(tameableId);
      if (nextRegeneration != null && nextRegeneration > now) {
        return false;
      }
      if (nextRegeneration == null && nextRegenerationAt.size() >= MAX_COOLDOWN_ENTRIES) {
        nextRegenerationAt.values().removeIf(next -> next <= now);
        if (nextRegenerationAt.size() >= MAX_COOLDOWN_ENTRIES) {
          return false;
        }
      }
      nextRegenerationAt.put(tameableId, now + REGENERATION_COOLDOWN_MILLIS);
      return true;
    }
  }

  private void removeCooldown(UUID tameableId) {
    synchronized (cooldownLock) {
      nextRegenerationAt.remove(tameableId);
    }
  }

  @Override
  public void onTick() {
    if (lifecycleClosed.get()) {
      return;
    }

    long now = M.ms();
    refreshLearnedOwners(now);
    for (int i = 0; i < MAX_REQUESTS_PER_TICK; i++) {
      RegenerationRequest request = pendingRegenerations.poll();
      if (request == null) {
        break;
      }
      J.runEntity(request.owner(), () -> prepareRegeneration(
          request.owner(),
          request.ownerId(),
          request.tameable(),
          request.tameableId()));
    }

    if (now < nextCooldownPrune) {
      return;
    }
    nextCooldownPrune = now + COOLDOWN_PRUNE_INTERVAL_MILLIS;
    synchronized (cooldownLock) {
      nextRegenerationAt.values().removeIf(next -> next <= now);
    }
  }

  private void refreshLearnedOwners(long now) {
    if (now < nextLearnedOwnerRefresh) {
      return;
    }
    nextLearnedOwnerRefresh = now + LEARNED_OWNER_REFRESH_INTERVAL_MILLIS;
    List<AdaptPlayer> learned = learnedCandidates(now);
    if (learned.isEmpty()) {
      learnedOwnerIds = Set.of();
      return;
    }

    HashSet<UUID> refreshed = new HashSet<>(learned.size());
    for (AdaptPlayer adaptPlayer : learned) {
      Player player = adaptPlayer.getPlayer();
      if (player != null) {
        refreshed.add(player.getUniqueId());
      }
    }
    learnedOwnerIds = Set.copyOf(refreshed);
  }

  @Override
  public void unregister() {
    lifecycleClosed.set(true);
    learnedOwnerIds = Set.of();
    pendingRegenerations.clear();
    synchronized (cooldownLock) {
      nextRegenerationAt.clear();
    }
    super.unregister();
  }

  @ConfigDescription("Increase your tamed animal regeneration rate.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Regen Factor for the Taming Health Regeneration adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double regenFactor = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Regen Base for the Taming Health Regeneration adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double regenBase = 1;

    public Config() {
      baseCost = 7;
      costFactor = 0.4;
      maxLevel = 3;
      initialCost = 8;
    }
  }

  private record RegenerationRequest(Player owner, UUID ownerId, Tameable tameable, UUID tameableId) {
  }
}
