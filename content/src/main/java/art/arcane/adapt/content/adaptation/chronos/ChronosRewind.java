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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ChronosRewind extends SimpleAdaptation<ChronosRewind.Config> {
  public static final Id ID = Id.adapt("chronos/rewind");
  public static final PlayerPreference<CommonPreferences.Toggle> CLOCK_SOUNDS = CommonPreferences.toggle("clock-sounds", Text.key("adapt.chronos.preferences.chronosrewind.clock-sounds"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> CLOCK = CommonPreferences.toggle("require-clock", Text.key("adapt.chronos.preferences.preference_chronosrewind_clock"), CommonPreferences.Toggle.OFF);

  private static final Id CLOCK_ITEM = Id.minecraft("clock");
  private final Map<UUID, RewindSnapshot> snapshots = playerState();
  private final Map<UUID, Long> cooldowns = playerState();
  private final Map<UUID, Boolean> cooldownReadyNotify = playerState();
  private final Map<UUID, RewindSnapshot> pendingRewinds = playerState();

  public ChronosRewind() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ender_eye"));
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_eye"))
        .key("challenge_chronos_rewind_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("recovery_compass"))
            .key("challenge_chronos_rewind_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_chronos_rewind_50", "chronos.rewind.rewinds", 50, 350);
    registerMilestone("challenge_chronos_rewind_500", "chronos.rewind.rewinds", 500, 1400);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CLOCK_SOUNDS, CLOCK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.duration(getConfig().snapshotWindowMillis, 1), 1);
    statLore(v, TextFormat.RED, "* ", Format.duration(getCooldownMillis(level), 1), 2);
    v.addLore(TextFormat.GRAY + "* " + Text.key("adapt.chronos.rewind.lore3").resolve());
    if (getConfig().hungerCost > 0) {
      statLore(v, TextFormat.RED, "* ", getConfig().hungerCost, Text.key("adapt.chronos.rewind.lore_cost_hunger"));
    }
  }

  private long getCooldownMillis(int level) {
    return Math.max(getConfig().minimumCooldownMillis,
        getConfig().baseCooldownMillis - (Math.max(1, level) * getConfig().cooldownReductionPerLevelMillis));
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    snapshots.remove(id);
    cooldowns.remove(id);
    cooldownReadyNotify.remove(id);
    pendingRewinds.remove(id);
    ChronosGesture.forget(id);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef p = e.player();
    if (!p.isSneaking() || preferenceEnabled(p, CLOCK)
        && !p.inventory().mainHand().type().equals(CLOCK_ITEM)
        && !p.inventory().offHand().type().equals(CLOCK_ITEM)) {
      return;
    }

    Adaptation.BlockActionContext context = resolveInteractContext(p, p.location());
    if (context == null) {
      return;
    }

    ChronosGesture.use(e);
    UUID id = p.uuid();
    long now = System.currentTimeMillis();

    RewindSnapshot snapshot = snapshots.get(id);
    if (snapshot != null && snapshot.expiresAt() > now) {
      performRewind(p, snapshot);
      return;
    }

    long cooldownUntil = cooldowns.getOrDefault(id, 0L);
    if (cooldownUntil > now) {
      reject(p);
      return;
    }

    snapshots.put(id, new RewindSnapshot(p.location(), p.health(), p.foodLevel(),
        now + Math.max(1000L, getConfig().snapshotWindowMillis)));

    if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
      ChronosSoundFX.playRewindStart(p);
    }

    fx(p.location(), FxPriority.TRANSITION)
        .column(Particles.END_ROD, 4, 1.4D)
        .ring(Particles.REVERSE_PORTAL, 0.5D, 8, 0.6D)
        .sound(Sounds.BLOCK_AMETHYST_CLUSTER_PLACE, 0.5F, 1.6F);
  }

  private void reject(PlayerRef p) {
    if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
      ChronosSoundFX.playClockReject(p);
    }
    fx(p.location().add(0, 1.2, 0), FxPriority.TRANSITION).burst(Particles.SMOKE, 3, 0.15D);
  }

  private void performRewind(PlayerRef p, RewindSnapshot snapshot) {
    UUID id = p.uuid();
    int hungerCost = Math.max(0, getConfig().hungerCost);
    if (hungerCost > 0 && p.foodLevel() < hungerCost) {
      reject(p);
      return;
    }

    Loc destination = snapshot.location();
    if (destination.world() == null) {
      reject(p);
      return;
    }

    if (pendingRewinds.putIfAbsent(id, snapshot) != null) {
      return;
    }

    boolean scheduled = AdaptApi.scheduler().runEntity(p, () -> beginScheduledRewind(p, snapshot, destination, hungerCost));
    if (!scheduled) {
      pendingRewinds.remove(id, snapshot);
      reject(p);
    }
  }

  private void beginScheduledRewind(PlayerRef p, RewindSnapshot snapshot, Loc destination, int hungerCost) {
    UUID id = p.uuid();
    if (!p.isOnline() || p.isDead() || pendingRewinds.get(id) != snapshot
        || snapshots.get(id) != snapshot || snapshot.expiresAt() <= System.currentTimeMillis()) {
      pendingRewinds.remove(id, snapshot);
      return;
    }

    Adaptation.BlockActionContext sourceContext = resolveInteractContext(p, p.location());
    Adaptation.BlockActionContext destinationContext = resolveInteractContext(p, destination);
    int level = scheduledRewindLevel(sourceContext, destinationContext);
    if (level <= 0 || (hungerCost > 0 && p.foodLevel() < hungerCost)) {
      pendingRewinds.remove(id, snapshot);
      reject(p);
      return;
    }

    Loc departure = p.location();
    CompletableFuture<Boolean> teleport;
    try {
      teleport = p.teleport(destination, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      pendingRewinds.remove(id, snapshot);
      AdaptApi.log().error("Chronos Rewind could not start a teleport for " + id + ".", null);
      AdaptApi.log().error("ChronosRewind failed", error);
      reject(p);
      return;
    }

    if (teleport == null) {
      pendingRewinds.remove(id, snapshot);
      reject(p);
      return;
    }
    teleport.whenComplete((success, failure) ->
        finishScheduledRewind(p, snapshot, departure, destination, hungerCost, level, success, failure));
  }

  private void finishScheduledRewind(
      PlayerRef p,
      RewindSnapshot snapshot,
      Loc departure,
      Loc destination,
      int hungerCost,
      int level,
      Boolean success,
      Throwable failure
  ) {
    UUID id = p.uuid();
    if (failure != null) {
      AdaptApi.log().error("Chronos Rewind teleport failed for " + id + ".", null);
      AdaptApi.log().error("ChronosRewind failed", failure);
    }

    Runnable completion = () -> {
      boolean operationOwned = pendingRewinds.remove(id, snapshot);
      if (!shouldCommitRewind(success, failure, operationOwned)
          || !p.isOnline() || p.isDead()) {
        return;
      }

      if (!snapshots.remove(id, snapshot)) {
        return;
      }

      long cooldownStart = System.currentTimeMillis();
      long cooldownMillis = getCooldownMillis(level);
      cooldowns.put(id, cooldownStart + cooldownMillis);
      HudStatus.cooldown(id, this, cooldownStart, cooldownMillis);
      cooldownReadyNotify.put(id, true);
      applySuccessfulRewind(p, snapshot, departure, destination, hungerCost, level);
    };
    boolean scheduled = AdaptApi.scheduler().runEntity(p, completion);
    if (!scheduled) {
      if (AdaptApi.scheduler().owns(p)) {
        completion.run();
      } else if (failure != null || !Boolean.TRUE.equals(success)) {
        pendingRewinds.remove(id, snapshot);
      }
    }
  }

  private void applySuccessfulRewind(
      PlayerRef p,
      RewindSnapshot snapshot,
      Loc departure,
      Loc destination,
      int hungerCost,
      int level
  ) {
    departFx(this, departure, destination);

    if (p.health() < snapshot.health()) {
      double maxHealth = p.hasAttribute(Attributes.MAX_HEALTH) ? p.attributeValue(Attributes.MAX_HEALTH) : 20D;
      p.setHealth(Math.min(maxHealth, snapshot.health()));
    }

    if (p.foodLevel() < snapshot.foodLevel()) {
      p.setFoodLevel(Math.min(20, snapshot.foodLevel()));
    }

    if (hungerCost > 0) {
      p.setFoodLevel(Math.max(0, p.foodLevel() - hungerCost));
    }

    arriveFx(this, p);

    if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
      ChronosSoundFX.playRewindFinish(p);
    }

    addStat(p, "chronos.rewind.rewinds", 1);
    xp(p, destination, getConfig().xpOnRewind + (level * getConfig().xpPerLevel));
  }

  /** The portal burst where the player left and the trail home. */
  public static void departFx(Adaptation<?> owner, Loc departure, Loc destination) {
    Loc departFx = departure.add(0, 1, 0);
    Fx.now(owner, departFx, FxPriority.TRANSITION)
        .particle(Particles.REVERSE_PORTAL, 18, 0, 0, 0, 0.35D, 0.04D)
        .particle(Particles.PORTAL, 28, 0, 0, 0, 0.45D, 0.6D)
        .sound(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.7F, 0.65F);

    if (departure.sameWorld(destination)) {
      Fx.now(owner, departFx, FxPriority.TRAIL)
          .line(Particles.PORTAL, destination.x(), destination.y() + 1, destination.z(), 10);
    }
  }

  /** The darkness and burst on arrival. */
  public static void arriveFx(Adaptation<?> owner, PlayerRef p) {
    p.setFallDistance(0);
    p.addEffect(EffectInstance.of(Id.minecraft("darkness"), 35, 0).withAmbient(true).withParticles(false).withIcon(false));
    Fx.now(owner, p.location().add(0, 1, 0), FxPriority.TRANSITION)
        .particle(Particles.END_ROD, 10, 0, 0, 0, 0.3D, 0.02D)
        .particle(Particles.REVERSE_PORTAL, 22, 0, 0, 0, 0.4D, 0.05D)
        .particle(Particles.PORTAL, 28, 0, 0, 0, 0.45D, 0.6D)
        .sound(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.7F, 0.85F);
  }

  static int scheduledRewindLevel(
      Adaptation.BlockActionContext sourceContext,
      Adaptation.BlockActionContext destinationContext
  ) {
    if (sourceContext == null || destinationContext == null) {
      return 0;
    }
    return Math.min(sourceContext.level(), destinationContext.level());
  }

  static boolean shouldCommitRewind(Boolean success, Throwable failure, boolean operationOwned) {
    return operationOwned && failure == null && Boolean.TRUE.equals(success);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    snapshots.entrySet().removeIf(entry ->
        entry.getValue().expiresAt() <= now
            && pendingRewinds.get(entry.getKey()) != entry.getValue());

    for (UUID id : cooldownReadyNotify.keySet()) {
      PlayerRef p = AdaptApi.platform().host().player(id);
      if (p == null) {
        cooldownReadyNotify.remove(id);
        continue;
      }

      if (cooldowns.getOrDefault(id, 0L) <= now && cooldownReadyNotify.remove(id) != null) {
        AdaptApi.scheduler().runEntity(p, () -> {
          if (p.isOnline() && (getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
            ChronosSoundFX.playCooldownReady(p);
          }
        });
      }
    }

    cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
  }

  @ConfigDescription("Sneak and swap hands to mark a moment in time, then do it again within the window to snap back with health and hunger restored.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Rewind adaptation.", impact = "True enables this behavior and false disables it.")
    boolean playClockSounds = true;
    @ConfigDoc(value = "Food points consumed when a rewind completes.", impact = "Higher values make each rewind cost more hunger; 0 disables the cost.")
    int hungerCost = 6;
    @ConfigDoc(value = "Window in milliseconds after marking a snapshot during which the rewind can be completed.", impact = "Higher values give more time to trigger the snap back.")
    long snapshotWindowMillis = 10000;
    @ConfigDoc(value = "Base cooldown in milliseconds applied after a completed rewind.", impact = "Higher values force longer waits between rewinds.")
    long baseCooldownMillis = 45000;
    @ConfigDoc(value = "Cooldown reduction in milliseconds per adaptation level.", impact = "Higher values make leveling shorten the cooldown faster.")
    long cooldownReductionPerLevelMillis = 4000;
    @ConfigDoc(value = "Lowest possible cooldown in milliseconds regardless of level.", impact = "Higher values keep a floor under cooldown reduction.")
    long minimumCooldownMillis = 15000;
    @ConfigDoc(value = "XP granted when a rewind completes.", impact = "Higher values grant more skill XP per rewind.")
    double xpOnRewind = 18;
    @ConfigDoc(value = "Extra XP granted per adaptation level on rewind.", impact = "Higher values scale rewind XP with level faster.")
    double xpPerLevel = 3;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }

  private record RewindSnapshot(Loc location, double health,
                                int foodLevel, long expiresAt) {
  }
}
