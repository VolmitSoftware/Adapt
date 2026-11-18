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

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.StealthMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class StealthDecoySwap extends SimpleAdaptation<StealthDecoySwap.Config> {
  public static final PlayerPreference<Gesture> GESTURE = new PlayerPreference<>(Gesture.class,
      new PlayerPreference.Definition<>("gesture", StealthMessages.STEALTHDECOYSWAP_PREFERENCE_GESTURE, Gesture.DOUBLE, List.of(
          new PlayerPreference.Choice<>(Gesture.DOUBLE, StealthMessages.STEALTHDECOYSWAP_PREFERENCE_GESTURE_DOUBLE, Id.minecraft("rabbit_foot"), 1),
          new PlayerPreference.Choice<>(Gesture.HAND_SWAP, StealthMessages.STEALTHDECOYSWAP_PREFERENCE_GESTURE_HAND_SWAP, Id.minecraft("shield"), 1))));

  private final StealthShadowDecoy shadowDecoy;
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, Long> lastSneakPress;
  private final Set<UUID> swapsInFlight = ConcurrentHashMap.newKeySet();

  public StealthDecoySwap(StealthShadowDecoy shadowDecoy) {
    super("stealth-decoy-swap");
    this.shadowDecoy = Objects.requireNonNull(shadowDecoy);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ender_pearl"));
    lastSneakPress = playerState();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("armor_stand"))
        .key("challenge_stealth_decoy_swap_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_pearl"))
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
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, GESTURE);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    lastSneakPress.remove(player.getPlayer().uuid());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getSwapRange(level), 1), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldown(level), 1), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking() || preference(e.player(), GESTURE) != Gesture.DOUBLE || shadowDecoy.spawnedOn(e)) {
      return;
    }

    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    if (level <= 0 || shadowDecoy.getActiveLevel(p) <= 0) {
      return;
    }

    UUID id = p.uuid();
    long now = System.currentTimeMillis();
    Long last = lastSneakPress.get(id);
    if (last != null && now - last <= getConfig().doubleTapWindowMillis) {
      lastSneakPress.remove(id);
      attemptSwap(p, id, level);
      return;
    }

    lastSneakPress.put(id, now);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    lastSneakPress.remove(id);
    cooldowns.clear(id);
  }

  public boolean reservesDoubleSneak(PlayerRef player) {
    return isPlayerEnabled(player) && getActiveLevel(player) > 0 && preference(player, GESTURE) == Gesture.DOUBLE
        && shadowDecoy.hasActiveDecoy(player.uuid());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef p = e.player();
    if (!p.isSneaking() || preference(p, GESTURE) != Gesture.HAND_SWAP) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0 || shadowDecoy.getActiveLevel(p) <= 0 || !shadowDecoy.hasActiveDecoy(p.uuid())) {
      return;
    }
    e.setCancelled(true);
    attemptSwap(p, p.uuid(), level);
  }

  private void attemptSwap(PlayerRef p, UUID id, int level) {
    EntityRef anchor = shadowDecoy.activeDecoyAnchor(id);
    if (anchor == null) {
      return;
    }

    if (!cooldowns.isReady(id, getCooldown(level))) {
      return;
    }
    if (!swapsInFlight.add(id)) {
      return;
    }

    Loc playerLoc = p.location();
    double range = getSwapRange(level);
    if (!Tasks.runEntity(anchor, () -> {
      if (!anchor.isValid()) {
        swapsInFlight.remove(id);
        return;
      }

      Loc anchorLoc = anchor.location();
      if (!anchorLoc.sameWorld(playerLoc)) {
        swapsInFlight.remove(id);
        return;
      }

      double dx = anchorLoc.x() - playerLoc.x();
      double dy = anchorLoc.y() - playerLoc.y();
      double dz = anchorLoc.z() - playerLoc.z();
      if ((dx * dx) + (dy * dy) + (dz * dz) > range * range) {
        swapsInFlight.remove(id);
        return;
      }

      Loc playerDest = anchorLoc.withRotation(playerLoc.yaw(), playerLoc.pitch());
      startAnchorTeleport(p, anchor, anchorLoc, playerLoc, playerDest, id);
    })) {
      swapsInFlight.remove(id);
    }
  }

  private void startAnchorTeleport(PlayerRef p, EntityRef anchor, Loc anchorOrigin, Loc playerOrigin,
                                   Loc playerDestination, UUID playerId) {
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
      if (!Tasks.runEntity(p, () -> startPlayerTeleport(
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

  private void startPlayerTeleport(PlayerRef p, EntityRef anchor, Loc anchorOrigin,
                                   Loc playerDestination, UUID playerId) {
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
      if (!Tasks.runEntity(p, () -> commitSwap(p, anchorOrigin, playerDestination, playerId))) {
        swapsInFlight.remove(playerId);
        AdaptLog.warn("Stealth Decoy Swap completed both teleports but could not schedule rewards for "
            + playerId + ".");
      }
    });
  }

  private void commitSwap(PlayerRef p, Loc anchorOrigin, Loc playerDestination, UUID playerId) {
    if (!p.isOnline() || !isRuntimeRegistered()) {
      swapsInFlight.remove(playerId);
      return;
    }
    swapsInFlight.remove(playerId);
    cooldowns.mark(playerId);
    xp(p, getConfig().xpOnSwap);
    addStat(p, "stealth.decoy-swap.swaps", 1);
    swapFx(anchorOrigin);
    swapFx(playerDestination.add(0, 1.0D, 0));
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .sound(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.5F);
  }

  private void rollbackAnchor(EntityRef anchor, Loc anchorOrigin, UUID playerId) {
    if (!Tasks.runEntity(anchor, () -> {
      if (!anchor.isValid()) {
        swapsInFlight.remove(playerId);
        AdaptLog.warn("Stealth Decoy Swap could not roll back invalid decoy "
            + anchor.uuid() + ".");
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
          AdaptLog.warn("Stealth Decoy Swap could not roll back decoy "
              + anchor.uuid() + " to " + anchorOrigin + ".");
        }
        swapsInFlight.remove(playerId);
      });
    })) {
      swapsInFlight.remove(playerId);
      AdaptLog.warn("Stealth Decoy Swap could not schedule rollback for decoy "
          + anchor.uuid() + " to " + anchorOrigin + ".");
    }
  }

  private CompletableFuture<Boolean> beginTeleport(EntityRef entity, Loc destination, String leg) {
    try {
      return entity.teleport(destination, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      reportTeleportFailure(entity, leg, error);
      return null;
    }
  }

  private void reportTeleportFailure(EntityRef entity, String leg, Throwable failure) {
    if (failure == null) {
      return;
    }
    AdaptLog.error("Stealth Decoy Swap " + leg + " teleport failed for " + entity.uuid() + ".");
    AdaptLog.error(failure);
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

  private void swapFx(Loc location) {
    fx(location, FxPriority.TRANSITION)
        .burst(Particles.REVERSE_PORTAL, 14, 0.35D)
        .burst(Particles.SMOKE, 6, 0.25D)
        .chord(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.5F, 1.2F, Sounds.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.4F, 1.0F);
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

  public enum Gesture { DOUBLE, HAND_SWAP }
}
