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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TamingBeastRecall extends SimpleAdaptation<TamingBeastRecall.Config> {
  private static final int HARD_MAX_CANDIDATES_PER_ACTIVATION = 32;
  private static final int HARD_MAX_AFFECTED_PER_ACTIVATION = 1;
  private final Map<UUID, Long> pendingRecalls = playerState();

  public TamingBeastRecall() {
    super("tame-beast-recall");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.beast_recall");
    setIcon(Material.LEAD);
    setInterval(2200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LEAD)
        .key("challenge_taming_recall_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_PEARL)
            .key("challenge_taming_recall_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_taming_recall_100", "taming.beast-recall.recalls", 100, 300);
    registerMilestone("challenge_taming_recall_1k", "taming.beast-recall.recalls", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getSearchRadius(level)), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 2);
    if (getConfig().hungerCost > 0) {
      v.addLore(C.RED + "* " + getConfig().hungerCost + C.GRAY + " " + Localizer.dLocalize("taming.beast_recall.lore_cost_hunger"));
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    Player p = e.getPlayer();
    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0 || p.getInventory().getItemInMainHand().getType() != Material.LEAD || p.hasCooldown(Material.LEAD)) {
      return;
    }

    int hungerCost = Math.max(0, getConfig().hungerCost);
    if (hungerCost > 0 && p.getFoodLevel() < hungerCost) {
      return;
    }

    if (getAffectedLimit() <= 0) {
      return;
    }

    double radius = getSearchRadius(level);
    Location origin = p.getLocation().clone();
    List<Tameable> candidates = collectCandidates(origin, radius);
    if (candidates.isEmpty()) {
      return;
    }

    UUID playerId = p.getUniqueId();
    long token = System.nanoTime();
    if (pendingRecalls.putIfAbsent(playerId, token) != null) {
      return;
    }
    e.setCancelled(true);

    RecallScan scan = new RecallScan(p, origin, radius * radius, getConfig().minRecallDistanceSquared,
        level, hungerCost, getConfig().xpOnRecall, token, candidates.size());
    for (Tameable tameable : candidates) {
      if (!J.runEntity(tameable, () -> inspectCandidateOwned(scan, tameable))) {
        scan.complete();
      }
    }
    J.runEntity(p, () -> clearPending(scan), 20);
  }

  private List<Tameable> collectCandidates(Location origin, double radius) {
    int limit = getCandidateLimit();
    List<Tameable> candidates = new ArrayList<>(limit);
    for (Tameable tameable : origin.getWorld().getNearbyEntitiesByType(Tameable.class, origin, radius, radius, radius)) {
      candidates.add(tameable);
      if (candidates.size() >= limit) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(RecallScan scan, Tameable tameable) {
    if (!tameable.isValid() || tameable.isDead() || !tameable.isTamed()) {
      scan.complete();
      return;
    }

    AnimalTamer owner = tameable.getOwner();
    if (owner == null || !scan.playerId.equals(owner.getUniqueId())) {
      scan.complete();
      return;
    }

    Location location = tameable.getLocation();
    if (location.getWorld() != scan.origin.getWorld()) {
      scan.complete();
      return;
    }

    double distanceSquared = location.distanceSquared(scan.origin);
    if (distanceSquared > scan.minDistanceSquared && distanceSquared <= scan.radiusSquared) {
      scan.consider(tameable, distanceSquared);
    }
    scan.complete();
  }

  private void finishScanOwned(RecallScan scan) {
    if (!isPending(scan)
        || !scan.player.isOnline()
        || getActiveLevel(scan.player) <= 0
        || scan.player.hasCooldown(Material.LEAD)) {
      clearPending(scan);
      return;
    }

    RecallCandidate nearest = scan.nearest.get();
    Location safe = findSafeRecallLocation(scan.player);
    if (nearest == null || safe == null || !canPVE(scan.player, safe)) {
      clearPending(scan);
      return;
    }

    if (!J.runEntity(nearest.tameable(), () -> recallOwned(scan, nearest.tameable(), safe))) {
      clearPending(scan);
    }
  }

  private void recallOwned(RecallScan scan, Tameable tameable, Location safe) {
    if (!isPending(scan) || !tameable.isValid() || tameable.isDead() || !tameable.isTamed()) {
      clearPending(scan);
      return;
    }

    AnimalTamer owner = tameable.getOwner();
    if (owner == null || !scan.playerId.equals(owner.getUniqueId())) {
      clearPending(scan);
      return;
    }

    Location from = tameable.getLocation().clone();
    if (from.getWorld() == safe.getWorld() && from.distanceSquared(safe) <= scan.minDistanceSquared) {
      clearPending(scan);
      return;
    }

    if (!J.teleport(tameable, safe)) {
      clearPending(scan);
      return;
    }
    tameable.setFallDistance(0);
    if (!J.runEntity(scan.player, () -> completeRecallOwned(scan, from, safe))) {
      clearPending(scan);
    }
  }

  private void completeRecallOwned(RecallScan scan, Location from, Location safe) {
    if (!isPending(scan)) {
      return;
    }
    clearPending(scan);
    if (!scan.player.isOnline()) {
      return;
    }

    scan.player.setCooldown(Material.LEAD, getCooldownTicks(scan.level));
    if (scan.hungerCost > 0) {
      scan.player.setFoodLevel(Math.max(0, scan.player.getFoodLevel() - scan.hungerCost));
    }

    fx(from, FxPriority.TRANSITION)
        .burst(Particle.REVERSE_PORTAL, 12, 0.3D)
        .particle(Particle.POOF, 1, 0, 0.2D, 0, 0.1D, 0.02D)
        .chord(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.45F, Sound.ITEM_TRIDENT_RETURN, 0.4F, 1.6F);
    fx(safe, FxPriority.TRANSITION)
        .dome(Particle.PORTAL, 0.6D, 8)
        .particle(Particles.VILLAGER_HAPPY, 4, 0, 0.5D, 0, 0.4D, 0)
        .chord(Sound.ITEM_LEAD_BREAK, 0.6F, 1.2F, Sound.ENTITY_WOLF_PANT, 0.6F, 1.3F);
    xp(scan.player, scan.xpOnRecall);
    addStat(scan.player, "taming.beast-recall.recalls", 1);
  }

  private void clearPending(RecallScan scan) {
    pendingRecalls.remove(scan.playerId, scan.token);
  }

  private boolean isPending(RecallScan scan) {
    Long current = pendingRecalls.get(scan.playerId);
    return current != null && current == scan.token;
  }

  private Location findSafeRecallLocation(Player p) {
    Location base = p.getLocation();
    int[][] offsets = {
        {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
        {2, 0}, {-2, 0}, {0, 2}, {0, -2}
    };

    for (int y = 0; y <= 2; y++) {
      for (int[] offset : offsets) {
        Location candidate = base.clone().add(offset[0], y, offset[1]);
        if (isSafeSpot(candidate)) {
          return candidate;
        }
      }
    }

    return null;
  }

  private boolean isSafeSpot(Location location) {
    Block feet = location.getBlock();
    Block head = location.clone().add(0, 1, 0).getBlock();
    Block below = location.clone().add(0, -1, 0).getBlock();
    return feet.isPassable() && head.isPassable() && below.getType().isSolid();
  }

  private double getSearchRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private int getCooldownTicks(int level) {
    return Math.max(40, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  private int getCandidateLimit() {
    return Math.max(1, Math.min(getConfig().maxCandidatesPerActivation, HARD_MAX_CANDIDATES_PER_ACTIVATION));
  }

  private int getAffectedLimit() {
    return Math.max(0, Math.min(getConfig().maxAffectedPerActivation, HARD_MAX_AFFECTED_PER_ACTIVATION));
  }


  @ConfigDescription("Sneak-right-click with a lead to recall your nearest tamed companion.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 38;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Recall Distance Squared for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minRecallDistanceSquared = 9.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 420;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 280;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Recall for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnRecall = 26;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Food points consumed per beast recall.", impact = "Higher values make each recall cost more hunger; 0 disables the cost.")
    int hungerCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum nearby tameable candidates inspected by one recall.", impact = "Lower values reduce dense-pet scheduling; values are clamped to an absolute maximum of 32.")
    int maxCandidatesPerActivation = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum pets recalled by one activation.", impact = "This is hard-clamped to 1; set to 0 to disable recall effects without disabling the adaptation.")
    int maxAffectedPerActivation = 1;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }

  private final class RecallScan {
    private final Player player;
    private final UUID playerId;
    private final Location origin;
    private final double radiusSquared;
    private final double minDistanceSquared;
    private final int level;
    private final int hungerCost;
    private final double xpOnRecall;
    private final long token;
    private final AtomicInteger remaining;
    private final AtomicReference<RecallCandidate> nearest = new AtomicReference<>();
    private final AtomicBoolean finalized = new AtomicBoolean();

    private RecallScan(Player player, Location origin, double radiusSquared, double minDistanceSquared,
                       int level, int hungerCost, double xpOnRecall, long token, int candidates) {
      this.player = player;
      playerId = player.getUniqueId();
      this.origin = origin;
      this.radiusSquared = radiusSquared;
      this.minDistanceSquared = minDistanceSquared;
      this.level = level;
      this.hungerCost = hungerCost;
      this.xpOnRecall = xpOnRecall;
      this.token = token;
      remaining = new AtomicInteger(candidates);
    }

    private void consider(Tameable tameable, double distanceSquared) {
      RecallCandidate observed = nearest.get();
      while (observed == null || distanceSquared < observed.distanceSquared()) {
        RecallCandidate replacement = new RecallCandidate(tameable, distanceSquared);
        if (nearest.compareAndSet(observed, replacement)) {
          return;
        }
        observed = nearest.get();
      }
    }

    private void complete() {
      if (remaining.decrementAndGet() != 0 || !finalized.compareAndSet(false, true)) {
        return;
      }
      if (!J.runEntity(player, () -> finishScanOwned(this))) {
        clearPending(this);
      }
    }
  }

  private record RecallCandidate(Tameable tameable, double distanceSquared) {
  }
}
