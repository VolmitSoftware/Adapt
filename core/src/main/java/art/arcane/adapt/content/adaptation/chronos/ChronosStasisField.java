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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ItemCooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ChronosMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public class ChronosStasisField extends SimpleAdaptation<ChronosStasisField.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> CLOCK_SOUNDS = CommonPreferences.toggle("clock-sounds", ChronosMessages.PREFERENCE_CHRONOSSTASISFIELD_CLOCK_SOUNDS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> OFFHAND = CommonPreferences.toggle("offhand-shard", ChronosMessages.PREFERENCE_CHRONOSSTASISFIELD_OFFHAND, CommonPreferences.Toggle.OFF);

  private static final Id AMETHYST_SHARD = Id.minecraft("amethyst_shard");

  private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
  private final ChronosStasis stasis = new ChronosStasis(this, () -> new ChronosStasis.Tuning(
      getConfig().removeProjectilesOnExpire, getConfig().slownessAmplifier, getConfig().jumpLockAmplifier,
      getConfig().effectRefreshTicks, getConfig().pulseIntervalMillis, getConfig().outlineParticleCount,
      getConfig().outlineRefreshMillis));

  public ChronosStasisField() {
    super("chronos-stasis-field");
    needs(PlatformNeed.partialAttribute(Attributes.JUMP_STRENGTH, "creatures in the field can still jump"));
    registerConfiguration(Config.class);
    setIcon(AMETHYST_SHARD);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("amethyst_shard"))
        .key("challenge_chronos_stasis_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("clock"))
            .key("challenge_chronos_stasis_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_chronos_stasis_50", "chronos.stasis-field.casts", 50, 400);
    registerMilestone("challenge_chronos_stasis_500", "chronos.stasis-field.casts", 500, 1500);
  }

  @Override
  public void unregister() {
    stasis.shutdown();
    cooldowns.clear();
    super.unregister();
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CLOCK_SOUNDS, OFFHAND);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRadius(level)), 1);
    statLore(v, C.YELLOW, "+ ", Form.duration(getDurationMillis(level), 1), 2);
    statLore(v, C.RED, "* ", Form.duration(getCooldownMillis(), 1), 3);
    v.addLore(C.GRAY + "* " + AdaptLanguage.text(ChronosMessages.STASIS_FIELD_LORE4));
    if (getConfig().consumeShard) {
      v.addLore(C.RED + "* " + AdaptLanguage.text(ChronosMessages.STASIS_FIELD_LORE_COST_SHARD));
    }
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    cooldowns.remove(e.player().uuid());
    stasis.quit(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityRemoveEvent e) {
    if (e.entity() instanceof ProjectileRef projectile) {
      stasis.forgetProjectile(projectile.uuid());
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_AIR && action != InteractAction.RIGHT_CLICK_BLOCK) {
      return;
    }

    PlayerRef player = e.player();
    if (!player.isSneaking()) {
      return;
    }
    Hand hand = e.hand();
    if (hand == null) {
      return;
    }

    ItemRef held = hand == Hand.OFF_HAND
        ? player.inventory().offHand()
        : player.inventory().mainHand();
    if (!held.type().equals(AMETHYST_SHARD)
        || preferenceEnabled(player, OFFHAND) && hand != Hand.OFF_HAND) {
      return;
    }

    Adaptation.BlockActionContext context = resolveInteractContext(player, player.location());
    if (context == null) {
      return;
    }
    e.setCancelled(true);
    if (!stasis.isAccepting()) {
      return;
    }

    long now = M.ms();
    UUID ownerId = player.uuid();
    long until = cooldowns.getOrDefault(ownerId, 0L);
    if (until > now) {
      // The shard has no vanilla right-click use, so the whole material can
      // carry the sweep without blocking anything a player could otherwise do.
      ItemCooldowns.pushMaterial(player, AMETHYST_SHARD, until - now);
      playReject(player);
      return;
    }
    cooldowns.remove(ownerId, until);

    Loc center = player.location().add(0D, getCenterYOffset(), 0D);
    ChronosStasis.StasisBubble bubble = stasis.open(player, center, getRadius(context.level()), now,
        now + getDurationMillis(context.level()));
    if (bubble == null) {
      if (stasis.isAccepting()) {
        playReject(player);
      }
      return;
    }

    if (getConfig().consumeShard && !payItemCost(player, "shard",
        AdaptCore.platform().host().items().create(AMETHYST_SHARD, 1), 1, () -> {
          held.setAmount(held.amount() - 1);
          player.inventory().setHand(hand, held);
          return true;
        })) {
      stasis.retire(bubble);
      return;
    }
    long cooldownMillis = getCooldownMillis();
    cooldowns.put(ownerId, now + cooldownMillis);
    AbilityGlyphs.cooldown(ownerId, this, now, cooldownMillis);
    ItemCooldowns.pushMaterial(player, AMETHYST_SHARD, cooldownMillis);
    emitDeploy(player, bubble, context.level());
  }

  @Override
  public void onTick() {
    stasis.tick();
  }

  private void emitDeploy(PlayerRef player, ChronosStasis.StasisBubble bubble, int level) {
    Loc center = stasis.center(bubble);
    if ((getConfig().playClockSounds && preferenceEnabled(player, CLOCK_SOUNDS))) {
      ChronosSoundFX.playTimeBombDetonate(center);
    }
    stasis.castFx(bubble);
    addStat(player, "chronos.stasis-field.casts", 1D);
    xp(player, center, getCastXp(level));
  }

  /** The flash, ring and expanding dome of a fresh bubble. */
  public static void castFx(Adaptation<?> owner, Loc center, double radius) {
    Fx.now(owner, center, FxPriority.GAMEPLAY)
        .particle(Particles.FLASH, 1, 0D, 0D, 0D, 0D, 0D)
        .dustRing(new Rgb(120, 200, 255), radius, 24, 1.2F)
        .chord(Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6F, 1.5F,
            Sounds.BLOCK_GLASS_PLACE, 0.5F, 0.7F);
    FxTimeline.at(owner, center)
        .duration(4)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(radius + 16D)
        .frame((emitter, tick, progress) -> emitter.dome(
            Particles.END_ROD,
            radius * (0.4D + (0.6D * progress)),
            16
        ))
        .start();
  }

  public static void freezeFx(Adaptation<?> owner, Loc at) {
    Fx.now(owner, at, FxPriority.COMBAT)
        .particle(Particles.END_ROD, 3, 0D, 0D, 0D, 0.1D, 0D);
  }

  /** The spinning shell of a live bubble; each point asks {@code budget} first. */
  public static void outlineFx(Adaptation<?> owner, Loc center, double radius, int points, long now, BooleanSupplier budget) {
    double spin = ((now % 4_000L) / 4_000D) * Math.PI * 2D;
    double golden = Math.PI * (3D - Math.sqrt(5D));
    FxEmitter shell = Fx.now(owner, center, FxPriority.AMBIENT);
    for (int index = 0; index < points; index++) {
      if (!budget.getAsBoolean()) {
        break;
      }
      double vertical = points == 1 ? 0D : (((2D * index) / (points - 1D)) - 1D);
      double ringRadius = Math.sqrt(Math.max(0D, 1D - (vertical * vertical)));
      double angle = (golden * index) + spin;
      shell.particle(
          Particles.END_ROD,
          1,
          Math.cos(angle) * ringRadius * radius,
          vertical * radius,
          Math.sin(angle) * ringRadius * radius,
          0D,
          0D
      );
    }
    if (budget.getAsBoolean()) {
      shell.particle(Particles.WAX_ON, 2, 0D, 0D, 0D, radius * 0.35D, 0D);
    }
  }

  public static void expiryFx(Adaptation<?> owner, Loc center, double radius) {
    Fx.now(owner, center, FxPriority.TRANSITION)
        .particle(Particles.FLASH, 1, 0D, 0D, 0D, 0D, 0D)
        .particle(Particles.PORTAL, 20, 0D, 0D, 0D, radius * 0.4D, 0.5D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_BREAK, 0.5F, 0.9F);
  }

  private void playReject(PlayerRef player) {
    if ((getConfig().playClockSounds && preferenceEnabled(player, CLOCK_SOUNDS))) {
      ChronosSoundFX.playClockReject(player);
    }
  }

  private double getRadius(int level) {
    double configured = getConfig().baseRadius
        + ((Math.max(1, level) - 1D) * getConfig().radiusPerLevel);
    return clamp(configured, 1D, 12D);
  }

  private long getDurationMillis(int level) {
    long configured = getConfig().baseDurationMillis
        + ((Math.max(1, level) - 1L) * getConfig().durationPerLevelMillis);
    return clamp(configured, 500L, 30_000L);
  }

  private long getCooldownMillis() {
    return clamp(getConfig().cooldownMillis, 0L, 3_600_000L);
  }

  private double getCenterYOffset() {
    return clamp(getConfig().centerYOffset, -8D, 8D);
  }

  /** The field's hold on {@code living}: slower movement and, for negative amplifiers, lower jumps. */
  public static void applyStasis(Adaptation<?> owner, LivingRef living, int slownessAmplifier, int jumpLockAmplifier,
                                 int refreshTicks) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.applyTimed(
        living,
        owner.getName(),
        "slow",
        Attributes.MOVEMENT_SPEED,
        stasisSlowScalar(slownessAmplifier),
        AttributeOperation.ADD_MULTIPLIED_TOTAL,
        refreshTicks
    );
    double jumpScalar = jumpLockScalar(jumpLockAmplifier);
    if (jumpScalar < 0D) {
      attributes.applyTimed(
          living,
          owner.getName(),
          "jump",
          Attributes.JUMP_STRENGTH,
          jumpScalar,
          AttributeOperation.ADD_MULTIPLIED_TOTAL,
          refreshTicks
      );
    }
  }

  static double stasisSlowScalar(int amplifier) {
    return -Math.min(1.0D, 0.15D * (Math.max(0, amplifier) + 1.0D));
  }

  static double jumpLockScalar(int amplifier) {
    return Math.max(-1.0D, Math.min(0.0D, amplifier / 6.0D));
  }

  private double getCastXp(int level) {
    double configured = getConfig().xpOnCast + (Math.max(1, level) * getConfig().xpPerLevel);
    return clamp(configured, 0D, 10_000D);
  }

  private int clamp(int value, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private long clamp(long value, long minimum, long maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private double clamp(double value, double minimum, double maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  @ConfigDescription("Sneak and right click with an amethyst shard to deploy a stasis bubble that freezes projectiles and locks down mobs inside.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls clock sounds for Chronos Stasis Field.", impact = "True enables deployment and rejection sounds.")
    boolean playClockSounds = true;
    @ConfigDoc(value = "Consumes the amethyst shard used to deploy a stasis bubble.", impact = "True makes each admitted cast cost one shard.")
    boolean consumeShard = true;
    @ConfigDoc(value = "Base radius of the stasis bubble in blocks.", impact = "Higher values affect a larger area within the hard radius limit.")
    double baseRadius = 3.5D;
    @ConfigDoc(value = "Extra bubble radius granted per adaptation level.", impact = "Higher values make leveling expand the bubble faster.")
    double radiusPerLevel = 0.75D;
    @ConfigDoc(value = "Base bubble lifetime in milliseconds.", impact = "Higher values keep the bubble active longer within the hard duration limit.")
    long baseDurationMillis = 3_000L;
    @ConfigDoc(value = "Extra bubble lifetime in milliseconds per adaptation level.", impact = "Higher values make leveling extend the bubble duration faster.")
    long durationPerLevelMillis = 750L;
    @ConfigDoc(value = "Cooldown between bubble deployments in milliseconds.", impact = "Higher values force longer waits between casts.")
    long cooldownMillis = 20_000L;
    @ConfigDoc(value = "Vertical offset of the bubble center above the caster.", impact = "Higher values raise the bubble center off the ground.")
    double centerYOffset = 1D;
    @ConfigDoc(value = "Slowness amplifier applied to mobs inside the bubble.", impact = "Higher values slow trapped mobs more severely within the supported effect range.")
    int slownessAmplifier = 5;
    @ConfigDoc(value = "Jump suppression amplifier applied to mobs inside the bubble as a jump_strength reduction scaling linearly from 0 (none) to -6 (full lock); positive values apply no modifier.", impact = "More negative values suppress jumping more strongly; -6 or below locks jumping entirely.")
    int jumpLockAmplifier = -6;
    @ConfigDoc(value = "Duration in ticks of each refreshed stasis pulse.", impact = "The runtime preserves enough buffering to cover the bounded pulse scheduler delay.")
    int effectRefreshTicks = 20;
    @ConfigDoc(value = "Milliseconds between bubble scans.", impact = "Lower values refresh effects sooner within the supported pulse range.")
    long pulseIntervalMillis = 250L;
    @ConfigDoc(value = "Removes frozen projectiles when the bubble expires instead of restoring their motion.", impact = "True deletes trapped projectiles; false releases their buffered motion.")
    boolean removeProjectilesOnExpire = false;
    @ConfigDoc(value = "Number of outline particles requested per visual refresh.", impact = "Higher values make the edge denser within per-bubble and global FX limits.")
    int outlineParticleCount = 10;
    @ConfigDoc(value = "Milliseconds between bubble outline refreshes.", impact = "Lower values redraw the outline more often within the global FX limit.")
    long outlineRefreshMillis = 400L;
    @ConfigDoc(value = "XP granted when an admitted bubble is deployed.", impact = "Higher values grant more skill XP per cast.")
    double xpOnCast = 22D;
    @ConfigDoc(value = "Extra XP granted per adaptation level on an admitted cast.", impact = "Higher values scale cast XP faster.")
    double xpPerLevel = 3D;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}

final class StasisBubbleCoordinator {
  private final int activeLimit;
  private final int ownerLimit;
  private final Map<UUID, Slot> slots = new HashMap<>();
  private final Map<UUID, Set<UUID>> ownerSlots = new HashMap<>();
  private final ArrayDeque<UUID> activeQueue = new ArrayDeque<>();
  private long nextGeneration;

  StasisBubbleCoordinator(int activeLimit, int ownerLimit) {
    if (activeLimit <= 0 || ownerLimit <= 0) {
      throw new IllegalArgumentException("Stasis bubble limits must be positive");
    }
    this.activeLimit = activeLimit;
    this.ownerLimit = ownerLimit;
  }

  synchronized long reserve(UUID ownerId, UUID bubbleId) {
    if (slots.size() >= activeLimit || slots.containsKey(bubbleId)) {
      return -1L;
    }
    Set<UUID> owned = ownerSlots.computeIfAbsent(ownerId, ignored -> new HashSet<>());
    if (owned.size() >= ownerLimit) {
      return -1L;
    }
    long generation = ++nextGeneration;
    slots.put(bubbleId, new Slot(ownerId, generation));
    owned.add(bubbleId);
    return generation;
  }

  synchronized boolean activate(UUID bubbleId, long generation) {
    Slot slot = slots.get(bubbleId);
    if (slot == null || slot.generation != generation || slot.active) {
      return false;
    }
    slot.active = true;
    activeQueue.addLast(bubbleId);
    return true;
  }

  synchronized List<Lease> take(int limit) {
    int count = Math.min(Math.max(0, limit), activeQueue.size());
    if (count == 0) {
      return List.of();
    }
    List<Lease> leases = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      UUID bubbleId = activeQueue.removeFirst();
      Slot slot = slots.get(bubbleId);
      if (slot == null || !slot.active) {
        continue;
      }
      activeQueue.addLast(bubbleId);
      leases.add(new Lease(bubbleId, slot.generation));
    }
    return leases;
  }

  synchronized boolean remove(UUID bubbleId, long generation) {
    Slot slot = slots.get(bubbleId);
    if (slot == null || slot.generation != generation) {
      return false;
    }
    removeSlot(bubbleId, slot);
    return true;
  }

  synchronized List<Lease> removeOwner(UUID ownerId) {
    Set<UUID> owned = ownerSlots.remove(ownerId);
    if (owned == null || owned.isEmpty()) {
      return List.of();
    }
    List<Lease> removed = new ArrayList<>(owned.size());
    for (UUID bubbleId : owned) {
      Slot slot = slots.remove(bubbleId);
      if (slot != null) {
        activeQueue.remove(bubbleId);
        removed.add(new Lease(bubbleId, slot.generation));
      }
    }
    return removed;
  }

  synchronized boolean isCurrent(UUID bubbleId, long generation) {
    Slot slot = slots.get(bubbleId);
    return slot != null && slot.active && slot.generation == generation;
  }

  synchronized int activeCount() {
    return slots.size();
  }

  synchronized void clear() {
    slots.clear();
    ownerSlots.clear();
    activeQueue.clear();
  }

  private void removeSlot(UUID bubbleId, Slot slot) {
    slots.remove(bubbleId, slot);
    activeQueue.remove(bubbleId);
    Set<UUID> owned = ownerSlots.get(slot.ownerId);
    if (owned == null) {
      return;
    }
    owned.remove(bubbleId);
    if (owned.isEmpty()) {
      ownerSlots.remove(slot.ownerId, owned);
    }
  }

  record Lease(UUID bubbleId, long generation) {
  }

  private static final class Slot {
    private final UUID ownerId;
    private final long generation;
    private boolean active;

    private Slot(UUID ownerId, long generation) {
      this.ownerId = ownerId;
      this.generation = generation;
    }
  }
}

final class StasisCapacityGate {
  private final int limit;
  private final AtomicInteger count = new AtomicInteger();

  StasisCapacityGate(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("Stasis capacity must be positive");
    }
    this.limit = limit;
  }

  boolean tryAcquire() {
    int current = count.get();
    while (current < limit) {
      if (count.compareAndSet(current, current + 1)) {
        return true;
      }
      current = count.get();
    }
    return false;
  }

  void release() {
    int current = count.get();
    while (current > 0 && !count.compareAndSet(current, current - 1)) {
      current = count.get();
    }
  }

  int count() {
    return count.get();
  }

  void reset() {
    count.set(0);
  }
}

final class StasisWindowBudget {
  private final int inspectionLimit;
  private final int handoffLimit;
  private final int outlinePointLimit;
  private final int freezeFxLimit;
  private final int castFxLimit;
  private final int expiryFxLimit;
  private final long windowNanos;
  private final LongSupplier clock;
  private long window = Long.MIN_VALUE;
  private int inspections;
  private int handoffs;
  private int outlinePoints;
  private int freezeFx;
  private int castFx;
  private int expiryFx;

  StasisWindowBudget(int inspectionLimit, int handoffLimit, int outlinePointLimit,
                     int freezeFxLimit, int castFxLimit, int expiryFxLimit,
                     long windowNanos) {
    this(inspectionLimit, handoffLimit, outlinePointLimit, freezeFxLimit,
        castFxLimit, expiryFxLimit, windowNanos, System::nanoTime);
  }

  StasisWindowBudget(int inspectionLimit, int handoffLimit, int outlinePointLimit,
                     int freezeFxLimit, int castFxLimit, int expiryFxLimit,
                     long windowNanos, LongSupplier clock) {
    if (inspectionLimit <= 0 || handoffLimit <= 0 || outlinePointLimit <= 0
        || freezeFxLimit <= 0 || castFxLimit <= 0 || expiryFxLimit <= 0
        || windowNanos <= 0) {
      throw new IllegalArgumentException("Stasis work limits must be positive");
    }
    this.inspectionLimit = inspectionLimit;
    this.handoffLimit = handoffLimit;
    this.outlinePointLimit = outlinePointLimit;
    this.freezeFxLimit = freezeFxLimit;
    this.castFxLimit = castFxLimit;
    this.expiryFxLimit = expiryFxLimit;
    this.windowNanos = windowNanos;
    this.clock = clock;
  }

  synchronized boolean tryInspection() {
    rotate();
    if (inspections >= inspectionLimit) {
      return false;
    }
    inspections++;
    return true;
  }

  synchronized boolean tryEntityHandoff() {
    rotate();
    if (handoffs >= handoffLimit) {
      return false;
    }
    handoffs++;
    return true;
  }

  synchronized boolean tryOutlinePoint() {
    rotate();
    if (outlinePoints >= outlinePointLimit) {
      return false;
    }
    outlinePoints++;
    return true;
  }

  synchronized boolean tryFreezeFx() {
    rotate();
    if (freezeFx >= freezeFxLimit) {
      return false;
    }
    freezeFx++;
    return true;
  }

  synchronized boolean tryCastFx() {
    rotate();
    if (castFx >= castFxLimit) {
      return false;
    }
    castFx++;
    return true;
  }

  synchronized boolean tryExpiryFx() {
    rotate();
    if (expiryFx >= expiryFxLimit) {
      return false;
    }
    expiryFx++;
    return true;
  }

  private void rotate() {
    long currentWindow = Math.floorDiv(clock.getAsLong(), windowNanos);
    if (currentWindow == window) {
      return;
    }
    window = currentWindow;
    inspections = 0;
    handoffs = 0;
    outlinePoints = 0;
    freezeFx = 0;
    castFx = 0;
    expiryFx = 0;
  }
}
