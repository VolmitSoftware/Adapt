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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class StealthDecoySwap extends SimpleAdaptation<StealthDecoySwap.Config> {
  private final StealthShadowDecoy shadowDecoy;
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, Long> lastSneakPress;
  private final Set<UUID> swapsInFlight = ConcurrentHashMap.newKeySet();

  public StealthDecoySwap(StealthShadowDecoy shadowDecoy) {
    super("stealth-decoy-swap");
    this.shadowDecoy = Objects.requireNonNull(shadowDecoy);
    registerConfiguration(Config.class);
    setIcon(Material.ENDER_PEARL);
    lastSneakPress = playerState();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ARMOR_STAND)
        .key("challenge_stealth_decoy_swap_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_PEARL)
            .key("challenge_stealth_decoy_swap_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_decoy_swap_100", "stealth.decoy-swap.swaps", 100, 400);
    registerMilestone("challenge_stealth_decoy_swap_1k", "stealth.decoy-swap.swaps", 1000, 1500);
  }

  static double computeSwapRange(double base, double factor, double percent) {
    return base + (percent * factor);
  }

  static long computeCooldown(double base, double factor, double percent) {
    return Math.max(2000L, Math.round(base - (percent * factor)));
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getSwapRange(level), 1), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldown(level), 1), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }

    Player p = e.getPlayer();
    int level = getActiveLevel(p);
    if (level <= 0 || shadowDecoy.getActiveLevel(p) <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    long now = System.currentTimeMillis();
    Long last = lastSneakPress.get(id);
    if (last != null && now - last <= getConfig().doubleTapWindowMillis) {
      lastSneakPress.remove(id);
      attemptSwap(p, id, level);
      return;
    }

    lastSneakPress.put(id, now);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    lastSneakPress.remove(id);
    cooldowns.clear(id);
  }

  private void attemptSwap(Player p, UUID id, int level) {
    Entity anchor = shadowDecoy.activeDecoyAnchor(id);
    if (anchor == null) {
      return;
    }

    if (!cooldowns.isReady(id, getCooldown(level))) {
      return;
    }
    if (!swapsInFlight.add(id)) {
      return;
    }

    Location playerLoc = p.getLocation().clone();
    double range = getSwapRange(level);
    if (!J.runEntity(anchor, () -> {
      if (!anchor.isValid()) {
        swapsInFlight.remove(id);
        return;
      }

      Location anchorLoc = anchor.getLocation().clone();
      if (anchorLoc.getWorld() == null || anchorLoc.getWorld() != playerLoc.getWorld()) {
        swapsInFlight.remove(id);
        return;
      }

      double dx = anchorLoc.getX() - playerLoc.getX();
      double dy = anchorLoc.getY() - playerLoc.getY();
      double dz = anchorLoc.getZ() - playerLoc.getZ();
      if ((dx * dx) + (dy * dy) + (dz * dz) > range * range) {
        swapsInFlight.remove(id);
        return;
      }

      Location playerDest = anchorLoc.clone();
      playerDest.setYaw(playerLoc.getYaw());
      playerDest.setPitch(playerLoc.getPitch());
      startAnchorTeleport(p, anchor, anchorLoc, playerLoc, playerDest, id);
    })) {
      swapsInFlight.remove(id);
    }
  }

  private void startAnchorTeleport(Player p, Entity anchor, Location anchorOrigin, Location playerOrigin,
                                   Location playerDestination, UUID playerId) {
    CompletableFuture<Boolean> teleport = beginTeleport(anchor, playerOrigin, "decoy");
    if (teleport == null) {
      swapsInFlight.remove(playerId);
      return;
    }
    teleport.whenComplete((success, failure) -> {
      reportTeleportFailure(anchor, "decoy", failure);
      if (!teleportCompleted(success, failure)) {
        swapsInFlight.remove(playerId);
        return;
      }
      if (!canStartPlayerLeg(swapsInFlight.contains(playerId), isRuntimeRegistered())) {
        rollbackAnchor(anchor, anchorOrigin, playerId);
        return;
      }
      if (!J.runEntity(p, () -> startPlayerTeleport(
          p,
          anchor,
          anchorOrigin,
          playerDestination,
          playerId
      ))) {
        rollbackAnchor(anchor, anchorOrigin, playerId);
      }
    });
  }

  private void startPlayerTeleport(Player p, Entity anchor, Location anchorOrigin,
                                   Location playerDestination, UUID playerId) {
    if (!canStartPlayerLeg(swapsInFlight.contains(playerId), isRuntimeRegistered()) || !p.isOnline()) {
      rollbackAnchor(anchor, anchorOrigin, playerId);
      return;
    }
    CompletableFuture<Boolean> teleport = beginTeleport(p, playerDestination, "player");
    if (teleport == null) {
      rollbackAnchor(anchor, anchorOrigin, playerId);
      return;
    }
    teleport.whenComplete((success, failure) -> {
      reportTeleportFailure(p, "player", failure);
      if (!teleportCompleted(success, failure)) {
        rollbackAnchor(anchor, anchorOrigin, playerId);
        return;
      }
      if (!J.runEntity(p, () -> commitSwap(p, anchorOrigin, playerDestination, playerId))) {
        swapsInFlight.remove(playerId);
        Adapt.warn("Stealth Decoy Swap completed both teleports but could not schedule rewards for "
            + playerId + ".");
      }
    });
  }

  private void commitSwap(Player p, Location anchorOrigin, Location playerDestination, UUID playerId) {
    if (!p.isOnline() || !isRuntimeRegistered()) {
      swapsInFlight.remove(playerId);
      return;
    }
    swapsInFlight.remove(playerId);
    cooldowns.mark(playerId);
    xp(p, getConfig().xpOnSwap);
    addStat(p, "stealth.decoy-swap.swaps", 1);
    swapFx(anchorOrigin);
    swapFx(playerDestination.clone().add(0, 1.0D, 0));
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.5F);
  }

  private void rollbackAnchor(Entity anchor, Location anchorOrigin, UUID playerId) {
    if (!J.runEntity(anchor, () -> {
      if (!anchor.isValid()) {
        swapsInFlight.remove(playerId);
        Adapt.warn("Stealth Decoy Swap could not roll back invalid decoy "
            + anchor.getUniqueId() + ".");
        return;
      }
      CompletableFuture<Boolean> rollback = beginTeleport(anchor, anchorOrigin, "decoy rollback");
      if (rollback == null) {
        swapsInFlight.remove(playerId);
        return;
      }
      rollback.whenComplete((success, failure) -> {
        reportTeleportFailure(anchor, "decoy rollback", failure);
        if (!teleportCompleted(success, failure)) {
          Adapt.warn("Stealth Decoy Swap could not roll back decoy "
              + anchor.getUniqueId() + " to " + anchorOrigin + ".");
        }
        swapsInFlight.remove(playerId);
      });
    })) {
      swapsInFlight.remove(playerId);
      Adapt.warn("Stealth Decoy Swap could not schedule rollback for decoy "
          + anchor.getUniqueId() + " to " + anchorOrigin + ".");
    }
  }

  private CompletableFuture<Boolean> beginTeleport(Entity entity, Location destination, String leg) {
    try {
      return PaperCompat.teleportAsync(entity, destination);
    } catch (RuntimeException error) {
      reportTeleportFailure(entity, leg, error);
      return null;
    }
  }

  private void reportTeleportFailure(Entity entity, String leg, Throwable failure) {
    if (failure == null) {
      return;
    }
    Adapt.error("Stealth Decoy Swap " + leg + " teleport failed for " + entity.getUniqueId() + ".");
    Adapt.error(failure);
  }

  static boolean teleportCompleted(Boolean success, Throwable failure) {
    return failure == null && Boolean.TRUE.equals(success);
  }

  static boolean canStartPlayerLeg(boolean currentInFlight, boolean runtimeRegistered) {
    return currentInFlight && runtimeRegistered;
  }

  @Override
  public void unregister() {
    swapsInFlight.clear();
    super.unregister();
  }

  private void swapFx(Location location) {
    fx(location, FxPriority.TRANSITION)
        .burst(Particle.REVERSE_PORTAL, 14, 0.35D)
        .burst(Particles.SMOKE, 6, 0.25D)
        .chord(Sound.ENTITY_ENDERMAN_TELEPORT, 0.5F, 1.2F, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.4F, 1.0F);
  }

  private double getSwapRange(int level) {
    return computeSwapRange(getConfig().swapRangeBase, getConfig().swapRangeFactor, getLevelPercent(level));
  }

  private long getCooldown(int level) {
    return computeCooldown(getConfig().cooldownBase, getConfig().cooldownFactor, getLevelPercent(level));
  }

  @ConfigDescription("Requires Shadow Decoy. While your decoy is alive, double-tap sneak to swap places with it.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base range within which you can swap with your decoy.", impact = "Higher values let you swap with decoys that are farther away.")
    double swapRangeBase = 10.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra swap range gained across levels.", impact = "Higher values extend swap range more per level.")
    double swapRangeFactor = 20.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base cooldown between swaps, in milliseconds.", impact = "Higher values mean longer waits between swaps.")
    double cooldownBase = 12000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How much the cooldown is reduced by leveling, in milliseconds.", impact = "Higher values shorten the swap cooldown more at higher levels.")
    double cooldownFactor = 8000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum time between the two sneak taps to register a double-tap, in milliseconds.", impact = "Higher values make the double-tap easier but riskier to trigger accidentally.")
    long doubleTapWindowMillis = 400;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Experience granted per successful swap.", impact = "Higher values level the adaptation faster.")
    double xpOnSwap = 12;

    public Config() {
      baseCost = 4;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 4;
    }
  }
}
