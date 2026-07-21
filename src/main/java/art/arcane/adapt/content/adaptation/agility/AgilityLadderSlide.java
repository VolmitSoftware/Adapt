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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public class AgilityLadderSlide extends SimpleAdaptation<AgilityLadderSlide.Config> {
  private static final double TICKS_PER_SECOND = 20.0D;
  private static final int COLUMN_END_BUFFER_BLOCKS = 2;
  private static final long SLIDE_GRACE_MS = 700L;
  private static final double MOVEMENT_EPSILON = 1.0E-4D;
  private static final double MAX_VERTICAL_SPEED = 1.0D;
  private static final double MIN_LOOK_ACTIVATION = 1.0D;
  private static final double MAX_LOOK_ANGLE = 89.0D;
  private static final double LEGACY_LOOK_ACTIVATION = 20.0D;
  private static final double LEGACY_LOOK_RELEASE = 10.0D;
  private static final double DEFAULT_LOOK_ACTIVATION = 30.0D;
  private static final double DEFAULT_LOOK_RELEASE = 15.0D;
  private static final Predicate<Player> GROUNDED_ACTIONS = player -> !player.isFlying() && !player.isGliding() && !player.isSwimming();

  private final Map<UUID, ControlSession> controlSessions = playerState();
  private final Cooldowns slideGrace = cooldowns();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean(false);
  private final AtomicLong clientTagGeneration = new AtomicLong(0L);
  private final Object clientTagPacketLock = new Object();
  private volatile ClientTagPackets clientTagPackets;

  public AgilityLadderSlide() {
    super("agility-ladder-slide");
    registerConfiguration(Config.class);
    setIcon(Material.LADDER);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LADDER)
        .key("challenge_agility_ladder_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.IRON_CHAIN)
            .key("challenge_agility_ladder_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_agility_ladder_500", "agility.ladder-slide.blocks-climbed", 500, 300);
    registerMilestone("challenge_agility_ladder_10k", "agility.ladder-slide.blocks-climbed", 10000, 1000);
  }

  @Override
  public void unregister() {
    if (lifecycleCleanupStarted.compareAndSet(false, true)) {
      ControlSession[] sessions = controlSessions.values().toArray(new ControlSession[0]);
      controlSessions.clear();
      for (ControlSession session : sessions) {
        retireControlSession(session);
      }
    }
    super.unregister();
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getDescentSpeed(level) * TICKS_PER_SECOND, 1), 1);
    statLore(v, Form.f(getClimbAssist(level) * TICKS_PER_SECOND, 1), 2);
    statLore(v, Form.f(getLookActivation(), 0) + " / " + Form.f(getLookRelease(), 0), 3);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.descentSpeedBase = normalizeSpeed(loadedConfig.descentSpeedBase);
    loadedConfig.descentSpeedPerLevel = normalizeSpeed(loadedConfig.descentSpeedPerLevel);
    loadedConfig.climbAssistBase = normalizeSpeed(loadedConfig.climbAssistBase);
    loadedConfig.climbAssistPerLevel = normalizeSpeed(loadedConfig.climbAssistPerLevel);
    if (Double.compare(loadedConfig.lookActivationDegrees, LEGACY_LOOK_ACTIVATION) == 0
        && Double.compare(loadedConfig.lookReleaseDegrees, LEGACY_LOOK_RELEASE) == 0) {
      loadedConfig.lookActivationDegrees = DEFAULT_LOOK_ACTIVATION;
      loadedConfig.lookReleaseDegrees = DEFAULT_LOOK_RELEASE;
    } else {
      loadedConfig.lookActivationDegrees = normalizeActivation(loadedConfig.lookActivationDegrees);
      loadedConfig.lookReleaseDegrees = normalizeRelease(
          loadedConfig.lookReleaseDegrees,
          loadedConfig.lookActivationDegrees
      );
    }
    loadedConfig.maxLevel = 1;
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (e.getTo() == null || e instanceof PlayerTeleportEvent) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isClimbing()) {
      clearPlayerState(p, false);
      return;
    }

    withPlayerThread(p, e, () -> startControl(p));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }

    Player p = e.getPlayer();
    ControlSession session = controlSessions.get(p.getUniqueId());
    if (session == null) {
      return;
    }

    withPlayerThread(p, e, () -> haltControl(session));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    clearPlayerState(e.getPlayer(), true);
  }

  @EventHandler
  public void on(PlayerDeathEvent e) {
    clearPlayerState(e.getEntity(), true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerGameModeChangeEvent e) {
    clearPlayerState(e.getPlayer(), true);
  }

  @EventHandler
  public void on(PlayerChangedWorldEvent e) {
    clearPlayerState(e.getPlayer(), true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerTeleportEvent e) {
    clearPlayerState(e.getPlayer(), true);
  }

  @EventHandler
  public void on(ServerResourcesReloadedEvent e) {
    synchronized (clientTagPacketLock) {
      clientTagPackets = null;
      clientTagGeneration.incrementAndGet();
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.FALL) {
      return;
    }

    if (!getConfig().safeLanding || slideGrace.isReady(p.getUniqueId(), SLIDE_GRACE_MS)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      slideGrace.clear(p.getUniqueId());
      e.setCancelled(true);
      p.setFallDistance(0);
      fx(p.getLocation(), FxPriority.TRANSITION)
          .burst(Particle.CLOUD, 4, 0.12D)
          .sound(Sound.BLOCK_LADDER_STEP, 0.35F, 1.1F);
    });
  }

  private void startControl(Player p) {
    if (lifecycleCleanupStarted.get()) {
      return;
    }

    BlockActionContext context = resolveInteractContext(p, p.getLocation(), GROUNDED_ACTIONS);
    Block climbable = context == null ? null : activeClimbable(context.location());
    if (context == null || climbable == null) {
      clearPlayerState(p, false);
      return;
    }

    UUID playerId = p.getUniqueId();
    ControlSession session = controlSessions.get(playerId);
    if (isInColumnEndBuffer(climbable)) {
      if (session != null) {
        finishControlInEndBuffer(session, context, climbable, p.getLocation());
      }
      return;
    }
    if (session != null && session.worldId.equals(p.getWorld().getUID())) {
      scheduleControlTick(session);
      return;
    }
    if (session != null) {
      finishControlSession(session);
    }

    ControlSession created = new ControlSession(p, p.getLocation());
    controlSessions.put(playerId, created);
    scheduleControlTick(created);
  }

  private void scheduleControlTick(ControlSession session) {
    if (lifecycleCleanupStarted.get()
        || controlSessions.get(session.owner.getUniqueId()) != session
        || session.tickScheduled) {
      return;
    }

    session.tickScheduled = true;
    if (!J.runEntity(session.owner, () -> runControlTick(session), 1)) {
      session.tickScheduled = false;
      finishControlSession(session);
    }
  }

  private void runControlTick(ControlSession session) {
    session.tickScheduled = false;
    if (controlSessions.get(session.owner.getUniqueId()) != session) {
      return;
    }

    try {
      maintainControl(session);
    } catch (RuntimeException | Error error) {
      finishControlSession(session);
      throw error;
    }
  }

  private void maintainControl(ControlSession session) {
    Player p = session.owner;
    if (!p.isOnline()
        || p.isDead()
        || !session.worldId.equals(p.getWorld().getUID())
        || !p.isClimbing()) {
      finishControlSession(session);
      return;
    }

    Location current = p.getLocation();
    BlockActionContext context = resolveInteractContext(p, current, GROUNDED_ACTIONS);
    Block climbable = context == null ? null : activeClimbable(context.location());
    if (context == null || climbable == null) {
      finishControlSession(session);
      return;
    }

    if (isInColumnEndBuffer(climbable)) {
      finishControlInEndBuffer(session, context, climbable, current);
      return;
    }
    recordMovement(session, current);
    Mode mode = resolveMode(
        session.mode,
        current.getPitch(),
        getLookActivation(),
        getLookRelease(),
        p.isSneaking()
    );
    if (!applyMode(session, context.level(), climbable, mode)) {
      finishControlSession(session);
      return;
    }
    session.lastY = current.getY();
    scheduleControlTick(session);
  }

  private void finishControlInEndBuffer(
      ControlSession session,
      BlockActionContext context,
      Block climbable,
      Location current
  ) {
    recordMovement(session, current);
    applyMode(session, context.level(), climbable, Mode.NONE);
    session.lastY = current.getY();
    finishControlSession(session);
  }

  private void haltControl(ControlSession session) {
    Player p = session.owner;
    if (controlSessions.get(p.getUniqueId()) != session || !p.isClimbing()) {
      return;
    }

    Location current = p.getLocation();
    BlockActionContext context = resolveInteractContext(p, current, GROUNDED_ACTIONS);
    Block climbable = context == null ? null : activeClimbable(context.location());
    if (context == null || climbable == null) {
      finishControlSession(session);
      return;
    }

    recordMovement(session, current);
    if (!applyMode(session, context.level(), climbable, Mode.NONE)) {
      finishControlSession(session);
      return;
    }
    session.lastY = current.getY();
    scheduleControlTick(session);
  }

  private boolean applyMode(ControlSession session, int level, Block climbable, Mode mode) {
    Player p = session.owner;
    if (!ensureClientClimbingState(session, climbable.getType(), mode == Mode.SLIDE)) {
      return false;
    }

    boolean changed = session.mode != mode;
    transition(p, session, mode);
    if (mode == Mode.NONE) {
      return !changed || sendVerticalMotion(p, 0D);
    }

    double targetY = targetVerticalVelocity(
        mode,
        getClimbAssist(level),
        getDescentSpeed(level)
    );
    if (!sendVerticalMotion(p, targetY)) {
      return false;
    }

    p.setFallDistance(0);
    return true;
  }

  private boolean ensureClientClimbingState(ControlSession session, Material climbable, boolean suppressed) {
    long generation = clientTagGeneration.get();
    if (suppressed) {
      if (session.clientClimbingSuppressed.get()
          && session.suppressedMaterial == climbable
          && session.clientTagGeneration == generation) {
        return true;
      }
      if (!sendClientClimbingState(session.owner, climbable)) {
        return false;
      }
      session.clientClimbingSuppressed.set(true);
      session.suppressedMaterial = climbable;
      session.clientTagGeneration = generation;
      return true;
    }

    if (!session.clientClimbingSuppressed.get()) {
      return true;
    }
    if (!sendClientClimbingState(session.owner, null)) {
      return false;
    }
    session.clientClimbingSuppressed.set(false);
    session.suppressedMaterial = null;
    session.clientTagGeneration = generation;
    return true;
  }

  protected boolean sendVerticalMotion(Player p, double targetY) {
    if (!(p instanceof CraftPlayer craftPlayer)) {
      return false;
    }

    ServerPlayer handle = craftPlayer.getHandle();
    Vec3 knownMovement = handle.getKnownMovement();
    Vec3 targetMovement = new Vec3(knownMovement.x, targetY, knownMovement.z);
    handle.setDeltaMovement(targetMovement);
    handle.connection.send(new ClientboundSetEntityMotionPacket(handle.getId(), targetMovement));
    return true;
  }

  protected boolean sendClientClimbingState(Player p, Material suppressedMaterial) {
    if (!(p instanceof CraftPlayer craftPlayer)) {
      return false;
    }

    ServerPlayer handle = craftPlayer.getHandle();
    ClientTagPackets packets = getClientTagPackets(handle);
    ClientboundUpdateTagsPacket packet = suppressedMaterial == null
        ? packets.standard()
        : packets.suppressed(suppressedMaterial);
    handle.connection.send(packet);
    return true;
  }

  private void recordMovement(ControlSession session, Location current) {
    double deltaY = current.getY() - session.lastY;
    if (session.mode == Mode.CLIMB && deltaY > MOVEMENT_EPSILON) {
      addStat(session.owner, "agility.ladder-slide.blocks-climbed", deltaY);
      maybeTrail(session.owner, false);
      return;
    }
    if (session.mode == Mode.SLIDE && deltaY < -MOVEMENT_EPSILON) {
      recordDescent(session.owner, -deltaY);
    }
  }

  private void recordDescent(Player p, double distance) {
    p.setFallDistance(0);
    if (getConfig().safeLanding) {
      slideGrace.mark(p.getUniqueId());
    }
    addStat(p, "agility.ladder-slide.blocks-descended", distance);
    maybeTrail(p, true);
  }

  private void finishControlSession(ControlSession session) {
    if (controlSessions.remove(session.owner.getUniqueId(), session)) {
      retireControlSession(session);
    }
  }

  private void retireControlSession(ControlSession session) {
    session.tickScheduled = false;
    if (!session.clientClimbingSuppressed.compareAndSet(true, false)) {
      return;
    }

    session.suppressedMaterial = null;
    try {
      sendClientClimbingState(session.owner, null);
    } catch (RuntimeException | Error error) {
      error.printStackTrace();
    }
  }

  private void clearPlayerState(Player p, boolean clearLandingGrace) {
    ControlSession session = controlSessions.get(p.getUniqueId());
    if (session != null) {
      finishControlSession(session);
    }
    if (clearLandingGrace) {
      slideGrace.clear(p.getUniqueId());
    }
  }

  private void transition(Player p, ControlSession session, Mode to) {
    if (session.mode == to) {
      return;
    }

    session.mode = to;
    switch (to) {
      case CLIMB -> fx(p.getLocation().add(0D, 1.0D, 0D), FxPriority.TRANSITION)
          .burst(Particle.CRIT, 2, 0.05D)
          .sound(Sound.ENTITY_PHANTOM_FLAP, 0.22F, 1.2F);
      case SLIDE -> fx(p.getLocation(), FxPriority.TRANSITION)
          .burst(Particle.CLOUD, 3, 0.08D)
          .sound(Sound.BLOCK_LADDER_STEP, 0.3F, 0.7F);
      case NONE -> {
      }
    }
  }

  private void maybeTrail(Player p, boolean sliding) {
    if (!M.r(0.08D)) {
      return;
    }

    double offsetY = sliding ? 0.2D : -0.2D;
    fx(p.getLocation(), FxPriority.TRAIL).particle(Particle.CLOUD, 1, 0D, offsetY, 0D, 0.05D, 0.02D);
  }

  private Block activeClimbable(Location location) {
    Block feet = location.getBlock();
    if (isControlledClimbable(feet.getType())) {
      return feet;
    }

    Block head = feet.getRelative(BlockFace.UP);
    if (isControlledClimbable(head.getType())) {
      return head;
    }

    return null;
  }

  boolean isInColumnEndBuffer(Block climbable) {
    return isInColumnEndBuffer(climbable, this::isControlledClimbable);
  }

  static boolean isInColumnEndBuffer(Block climbable, Predicate<Material> climbableType) {
    return !hasClimbableRun(climbable, BlockFace.DOWN, COLUMN_END_BUFFER_BLOCKS, climbableType)
        || !hasClimbableRun(climbable, BlockFace.UP, COLUMN_END_BUFFER_BLOCKS, climbableType);
  }

  private static boolean hasClimbableRun(
      Block climbable,
      BlockFace direction,
      int length,
      Predicate<Material> climbableType
  ) {
    Block cursor = climbable;
    for (int i = 0; i < length; i++) {
      cursor = cursor.getRelative(direction);
      if (!climbableType.test(cursor.getType())) {
        return false;
      }
    }
    return true;
  }

  private boolean isControlledClimbable(Material type) {
    return type != Material.SCAFFOLDING && Tag.CLIMBABLE.isTagged(type);
  }

  private ClientTagPackets getClientTagPackets(ServerPlayer player) {
    long generation = clientTagGeneration.get();
    ClientTagPackets packets = clientTagPackets;
    if (packets != null && packets.generation() == generation) {
      return packets;
    }

    synchronized (clientTagPacketLock) {
      packets = clientTagPackets;
      generation = clientTagGeneration.get();
      if (packets == null || packets.generation() != generation) {
        packets = buildClientTagPackets(
            player.level().getServer().registryAccess().lookupOrThrow(Registries.BLOCK),
            BlockTags.CLIMBABLE,
            generation
        );
        clientTagPackets = packets;
      }
      return packets;
    }
  }

  private static <T> ClientTagPackets buildClientTagPackets(Registry<T> registry, TagKey<T> climbableTag, long generation) {
    Map<Identifier, IntList> standardTags = new HashMap<>();
    for (HolderSet.Named<T> named : registry.getTags().toList()) {
      IntList ids = new IntArrayList(named.size());
      for (Holder<T> holder : named) {
        ids.add(registry.getId(holder.value()));
      }
      standardTags.put(named.key().location(), ids);
    }

    Map<Identifier, Integer> blockIds = new HashMap<>();
    for (Identifier identifier : registry.keySet()) {
      T value = registry.getValue(identifier);
      blockIds.put(identifier, registry.getId(value));
    }

    Map<Identifier, IntList> immutableTags = Map.copyOf(standardTags);
    return new ClientTagPackets(
        generation,
        climbableTag.location(),
        immutableTags,
        Map.copyOf(blockIds),
        blockTagPacket(new TagNetworkSerialization.NetworkPayload(immutableTags))
    );
  }

  static Map<Identifier, IntList> suppressBlockInTag(
      Map<Identifier, IntList> standardTags,
      Identifier tag,
      int suppressedBlockId
  ) {
    IntList original = standardTags.get(tag);
    if (original == null) {
      throw new IllegalStateException("Missing client block tag " + tag);
    }

    IntList filtered = new IntArrayList(original.size());
    boolean removed = false;
    for (int index = 0; index < original.size(); index++) {
      int blockId = original.getInt(index);
      if (blockId == suppressedBlockId) {
        removed = true;
      } else {
        filtered.add(blockId);
      }
    }
    if (!removed) {
      throw new IllegalStateException("Block " + suppressedBlockId + " is not in client block tag " + tag);
    }

    Map<Identifier, IntList> suppressedTags = new HashMap<>(standardTags);
    suppressedTags.put(tag, filtered);
    return Map.copyOf(suppressedTags);
  }

  private static ClientboundUpdateTagsPacket blockTagPacket(TagNetworkSerialization.NetworkPayload payload) {
    Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> registryTags = new HashMap<>(1);
    registryTags.put(Registries.BLOCK, payload);
    return new ClientboundUpdateTagsPacket(registryTags);
  }

  static Mode resolveMode(Mode current, float pitch, double activationDegrees, double releaseDegrees) {
    if (!Float.isFinite(pitch)) {
      return Mode.NONE;
    }

    double activation = normalizeActivation(activationDegrees);
    double release = normalizeRelease(releaseDegrees, activation);
    if (pitch <= -activation) {
      return Mode.CLIMB;
    }
    if (pitch >= activation) {
      return Mode.SLIDE;
    }
    if (current == Mode.CLIMB && pitch < -release) {
      return Mode.CLIMB;
    }
    if (current == Mode.SLIDE && pitch > release) {
      return Mode.SLIDE;
    }
    return Mode.NONE;
  }

  static Mode resolveMode(
      Mode current,
      float pitch,
      double activationDegrees,
      double releaseDegrees,
      boolean sneaking
  ) {
    return sneaking
        ? Mode.NONE
        : resolveMode(current, pitch, activationDegrees, releaseDegrees);
  }

  static double targetVerticalVelocity(Mode mode, double climbSpeed, double descentSpeed) {
    return switch (mode) {
      case CLIMB -> normalizeSpeed(climbSpeed);
      case SLIDE -> -normalizeSpeed(descentSpeed);
      case NONE -> 0D;
    };
  }

  static double normalizeSpeed(double speed) {
    if (!Double.isFinite(speed) || speed <= 0D) {
      return 0D;
    }
    return Math.min(speed, MAX_VERTICAL_SPEED);
  }

  static double normalizeActivation(double activationDegrees) {
    if (!Double.isFinite(activationDegrees)) {
      return DEFAULT_LOOK_ACTIVATION;
    }
    return Math.max(MIN_LOOK_ACTIVATION, Math.min(activationDegrees, MAX_LOOK_ANGLE));
  }

  static double normalizeRelease(double releaseDegrees, double activationDegrees) {
    double activation = normalizeActivation(activationDegrees);
    if (!Double.isFinite(releaseDegrees)) {
      return Math.min(DEFAULT_LOOK_RELEASE, activation - MIN_LOOK_ACTIVATION);
    }
    return Math.max(0D, Math.min(releaseDegrees, activation - MIN_LOOK_ACTIVATION));
  }

  private double getDescentSpeed(int level) {
    return normalizeSpeed(getConfig().descentSpeedBase + (getLevelPercent(level) * getConfig().descentSpeedPerLevel));
  }

  private double getClimbAssist(int level) {
    return normalizeSpeed(getConfig().climbAssistBase + (getLevelPercent(level) * getConfig().climbAssistPerLevel));
  }

  private double getLookActivation() {
    return normalizeActivation(getConfig().lookActivationDegrees);
  }

  private double getLookRelease() {
    return normalizeRelease(getConfig().lookReleaseDegrees, getLookActivation());
  }

  @ConfigDescription("Look up to climb quickly, look down to descend quickly, look near the horizon for normal ladder control, and sneak to halt. The first and last two blocks of each climbable column always use normal control.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base downward speed in blocks per tick before per-level scaling.", impact = "Higher values make downward gaze movement faster; runtime motion is capped at one block per tick.")
    double descentSpeedBase = 0.30D;
    @ConfigDoc(value = "Additional downward speed granted at the maximum level.", impact = "Higher values widen the descent-speed gain earned from leveling this adaptation.")
    double descentSpeedPerLevel = 0.30D;
    @ConfigDoc(value = "Base upward speed in blocks per tick before per-level scaling.", impact = "Higher values make upward gaze movement faster.")
    double climbAssistBase = 0.28D;
    @ConfigDoc(value = "Additional upward speed granted at the maximum level.", impact = "Higher values widen the climb-speed gain earned from leveling this adaptation.")
    double climbAssistPerLevel = 0.22D;
    @ConfigDoc(value = "Camera angle above or below the horizon that activates gaze-directed ladder movement.", impact = "Higher values require looking farther up or down before the adaptation takes control.")
    double lookActivationDegrees = DEFAULT_LOOK_ACTIVATION;
    @ConfigDoc(value = "Camera angle where an active gaze direction releases back to normal ladder control.", impact = "This is clamped below the activation angle to prevent rapid direction flicker.")
    double lookReleaseDegrees = DEFAULT_LOOK_RELEASE;
    @ConfigDoc(value = "Prevents fall damage that results directly from a fast ladder descent.", impact = "True negates descent-attributable landing damage; false lets vanilla fall damage apply.")
    boolean safeLanding = true;

    public Config() {
      baseCost = 1;
      costFactor = 0.12D;
      maxLevel = 1;
      initialCost = 1;
    }
  }

  enum Mode {
    NONE,
    CLIMB,
    SLIDE
  }

  private static final class ClientTagPackets {
    private final long generation;
    private final Identifier climbableTag;
    private final Map<Identifier, IntList> standardTags;
    private final Map<Identifier, Integer> blockIds;
    private final ClientboundUpdateTagsPacket standard;
    private final ConcurrentMap<Integer, ClientboundUpdateTagsPacket> suppressed = new ConcurrentHashMap<>();

    private ClientTagPackets(
        long generation,
        Identifier climbableTag,
        Map<Identifier, IntList> standardTags,
        Map<Identifier, Integer> blockIds,
        ClientboundUpdateTagsPacket standard
    ) {
      this.generation = generation;
      this.climbableTag = climbableTag;
      this.standardTags = standardTags;
      this.blockIds = blockIds;
      this.standard = standard;
    }

    private long generation() {
      return generation;
    }

    private ClientboundUpdateTagsPacket standard() {
      return standard;
    }

    private ClientboundUpdateTagsPacket suppressed(Material material) {
      NamespacedKey key = material.getKey();
      Identifier identifier = Identifier.fromNamespaceAndPath(key.getNamespace(), key.getKey());
      Integer blockId = blockIds.get(identifier);
      if (blockId == null) {
        throw new IllegalStateException("Missing client block registry entry " + identifier);
      }

      return suppressed.computeIfAbsent(blockId, id -> blockTagPacket(new TagNetworkSerialization.NetworkPayload(
          suppressBlockInTag(standardTags, climbableTag, id)
      )));
    }
  }

  private static final class ControlSession {
    private final Player owner;
    private final UUID worldId;
    private final AtomicBoolean clientClimbingSuppressed = new AtomicBoolean(false);
    private double lastY;
    private long clientTagGeneration = -1L;
    private Material suppressedMaterial;
    private boolean tickScheduled;
    private Mode mode = Mode.NONE;

    private ControlSession(Player owner, Location origin) {
      this.owner = owner;
      this.worldId = origin.getWorld().getUID();
      this.lastY = origin.getY();
    }
  }
}
