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

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.event.AdaptAdaptationTeleportEvent;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.input.DoubleJumpGesture;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


public class RiftBlink extends SimpleAdaptation<RiftBlink.Config> {
  public static final PlayerPreference<Phasing> PHASING = new PlayerPreference<>(Phasing.class,
      new PlayerPreference.Definition<>("phasing", RiftMessages.BLINK_SETTING_PHASING, Phasing.SNEAK, List.of(
          new PlayerPreference.Choice<>(Phasing.SNEAK, RiftMessages.BLINK_OPTION_SNEAK, Material.LEATHER_BOOTS, 1),
          new PlayerPreference.Choice<>(Phasing.AIM, RiftMessages.BLINK_OPTION_AIM, Material.ENDER_EYE, 1),
          new PlayerPreference.Choice<>(Phasing.NEVER, RiftMessages.BLINK_OPTION_NEVER, Material.BRICKS, 1),
          new PlayerPreference.Choice<>(Phasing.AUTO, RiftMessages.RIFTBLINK_PREFERENCE_AUTO, Material.SPYGLASS, 1))));
  public static final PlayerPreference<Targeting> TARGETING = new PlayerPreference<>(Targeting.class,
      new PlayerPreference.Definition<>("targeting", RiftMessages.BLINK_SETTING_TARGETING, Targeting.DISTANCE, List.of(
          new PlayerPreference.Choice<>(Targeting.DISTANCE, RiftMessages.BLINK_OPTION_DISTANCE, Material.ARROW, 1),
          new PlayerPreference.Choice<>(Targeting.VERTICALITY, RiftMessages.BLINK_OPTION_VERTICALITY, Material.LADDER, 1))));
  public static final PlayerPreference<Activation> ACTIVATION = new PlayerPreference<>(Activation.class,
      new PlayerPreference.Definition<>("activation", RiftMessages.BLINK_SETTING_ACTIVATION, Activation.MANUAL, List.of(
          new PlayerPreference.Choice<>(Activation.MANUAL, RiftMessages.BLINK_OPTION_MANUAL, Material.FEATHER, 1),
          new PlayerPreference.Choice<>(Activation.REACTIVE, RiftMessages.BLINK_OPTION_REACTIVE, Material.SHIELD, 2))));
  public static final PlayerPreference<ReactiveDirection> REACTIVE_DIRECTION = new PlayerPreference<>(ReactiveDirection.class,
      new PlayerPreference.Definition<>("reactive-direction", RiftMessages.BLINK_SETTING_REACTIVE_DIRECTION, ReactiveDirection.LOOK, List.of(
          new PlayerPreference.Choice<>(ReactiveDirection.LOOK, RiftMessages.BLINK_OPTION_LOOK, Material.SPYGLASS, 2),
          new PlayerPreference.Choice<>(ReactiveDirection.AWAY_FROM_ATTACKER, RiftMessages.BLINK_OPTION_AWAY, Material.ENDER_PEARL, 2))));
  public static final PlayerPreference<Landing> LANDING = new PlayerPreference<>(Landing.class,
      new PlayerPreference.Definition<>("landing", RiftMessages.RIFTBLINK_PREFERENCE_LANDING, Landing.SERVER, List.of(
          new PlayerPreference.Choice<>(Landing.SERVER, RiftMessages.RIFTBLINK_PREFERENCE_LANDING_SERVER, Material.GRASS_BLOCK, 1),
          new PlayerPreference.Choice<>(Landing.NEAR, RiftMessages.RIFTBLINK_PREFERENCE_LANDING_NEAR, Material.STONE_SLAB, 1),
          new PlayerPreference.Choice<>(Landing.NONE, RiftMessages.RIFTBLINK_PREFERENCE_LANDING_NONE, Material.FEATHER, 1))));
  public static final PlayerPreference<Momentum> MOMENTUM = new PlayerPreference<>(Momentum.class,
      new PlayerPreference.Definition<>("momentum", RiftMessages.RIFTBLINK_PREFERENCE_MOMENTUM, Momentum.FULL, List.of(
          new PlayerPreference.Choice<>(Momentum.FULL, RiftMessages.RIFTBLINK_PREFERENCE_MOMENTUM_FULL, Material.ARROW, 1),
          new PlayerPreference.Choice<>(Momentum.HALF, RiftMessages.RIFTBLINK_PREFERENCE_MOMENTUM_HALF, Material.FEATHER, 1),
          new PlayerPreference.Choice<>(Momentum.STOP, RiftMessages.RIFTBLINK_PREFERENCE_MOMENTUM_STOP, Material.BARRIER, 1))));
  private static final List<PlayerPreference<?>> PREFERENCES = List.of(CommonPreferences.ENABLED, PHASING, TARGETING, ACTIVATION, REACTIVE_DIRECTION, LANDING, MOMENTUM);

  private final Cooldowns lastBlink = cooldowns();
  private final DoubleJumpGesture doubleJump = new DoubleJumpGesture();
  private final Map<UUID, UUID> pendingBlinks = new ConcurrentHashMap<>();
  private final Map<UUID, Boolean> replayingAttacks = playerState();
  private final Map<UUID, PendingAttack> deferredAttacks = new ConcurrentHashMap<>();

  public RiftBlink() {
    super("rift-blink");
    registerConfiguration(Config.class);
    setIcon(Material.FEATHER);
    setInterval(9288);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_blink_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_EYE)
            .key("challenge_rift_blink_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_blink_500", "rift.blink.blinks", 500, 400);
    registerMilestone("challenge_rift_blink_5k", "rift.blink.distance-blinked", 5000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return PREFERENCES;
  }

  public boolean usesManualTrigger(Player player) {
    return isBlinkEligible(player) && preference(player, ACTIVATION) == Activation.MANUAL;
  }

  @Override
  public boolean isPlayerPreferenceVisible(AdaptPlayer player, PlayerPreference<?> preference) {
    return preference != REACTIVE_DIRECTION || (getLevel(player) >= 2
        && PlayerPreferences.resolve(this, player.getData(), getLevel(player), ACTIVATION) == Activation.REACTIVE);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getBlinkDistance(level), 1), 1);
    statLore(v, Form.f(getPearlDamage(level) / 2D, 1), 2);
    if (getConfig().allowPhasing) {
      v.addLore(C.LIGHT_PURPLE + "+ " + AdaptLanguage.text(RiftMessages.BLINK_LORE3));
    }
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    if (!usesManualTrigger(p)) {
      doubleJump.reset(p);
      return;
    }

    if (!doubleJump.update(p) || isOnCooldown(p.getUniqueId())) {
      return;
    }

    attemptBlink(p, p.getEyeLocation().getDirection(), null);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player player)
        || !isReactiveAttack(event.getCause(), event.getFinalDamage())
        || replayingAttacks.containsKey(player.getUniqueId())
        || !isBlinkEligible(player)
        || getLevel(player) < 2
        || preference(player, ACTIVATION) != Activation.REACTIVE
        || isOnCooldown(player.getUniqueId())) {
      return;
    }
    Vector direction = player.getEyeLocation().getDirection();
    if (preference(player, REACTIVE_DIRECTION) == ReactiveDirection.AWAY_FROM_ATTACKER) {
      direction = awayDirection(player.getLocation(), direction, attackLocation(event.getDamageSource()));
    }
    attemptBlink(player, direction, event);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    pendingBlinks.remove(player.getUniqueId());
    PendingAttack attack = deferredAttacks.remove(player.getUniqueId());
    if (attack != null) {
      replayAttack(player, attack);
    }
  }

  private boolean isBlinkEligible(Player player) {
    if (player.getGameMode() != GameMode.SURVIVAL) {
      return false;
    }
    AdaptPlayer owner = getPlayer(player);
    return owner != null && owner.isRuntimeReady() && owner.getPlayer() == player
        && PlayerPreferences.resolve(this, owner.getData(), getLevel(player), CommonPreferences.ENABLED) == CommonPreferences.Toggle.ON
        && hasActiveAdaptation(player);
  }

  private Location attackLocation(DamageSource source) {
    Location location = source.getDamageLocation();
    if (location != null) {
      return location;
    }
    Entity attacker = source.getCausingEntity();
    if (attacker != null && (!J.isFoliaThreading() || J.isOwnedByCurrentRegion(attacker))) {
      return attacker.getLocation();
    }
    Entity direct = source.getDirectEntity();
    return direct != null && (!J.isFoliaThreading() || J.isOwnedByCurrentRegion(direct)) ? direct.getLocation() : null;
  }

  private boolean isOnCooldown(UUID id) {
    return pendingBlinks.containsKey(id) || !lastBlink.isReady(id, Math.max(0L, getConfig().cooldownMillis));
  }

  private double getBlinkDistance(int level) {
    double distance = getConfig().baseDistance + (getLevelPercent(level) * getConfig().distanceFactor);
    return Double.isFinite(distance) ? Math.max(0D, Math.min(128D, distance)) : 0D;
  }

  private double getPearlDamage(int level) {
    return calculatePearlDamage(
        level,
        getConfig().pearlDamageBase,
        getConfig().pearlDamageReductionPerLevel,
        getConfig().minimumPearlDamage
    );
  }

  private void attemptBlink(Player p, Vector direction, EntityDamageByEntityEvent incomingAttack) {
    UUID id = p.getUniqueId();
    if (isOnCooldown(id)) {
      return;
    }

    UUID ticket = UUID.randomUUID();
    if (pendingBlinks.putIfAbsent(id, ticket) != null) {
      return;
    }

    boolean started = false;
    try {
      started = startBlink(p, direction, incomingAttack, ticket);
    } catch (RuntimeException failure) {
      Adapt.error(failure);
      Adapt.error("Rift Blink failed to prepare a destination for " + id + ".");
    } finally {
      if (!started) {
        pendingBlinks.remove(id, ticket);
      }
    }
  }

  private boolean startBlink(Player p, Vector direction, EntityDamageByEntityEvent incomingAttack, UUID ticket) {
    UUID id = p.getUniqueId();
    Location origin = p.getLocation().clone();
    Location destination = findBlinkDestination(p, direction);
    double minDistance = Math.max(0.5, getConfig().minBlinkDistance);
    if (destination == null || origin.distanceSquared(destination) < minDistance * minDistance) {
      fx(p, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 4, 0.2)
          .sound(Sound.BLOCK_CONDUIT_DEACTIVATE, 0.5f, 1.4f);
      return false;
    }

    destination.setYaw(origin.getYaw());
    destination.setPitch(origin.getPitch());
    double momentum = Double.isFinite(getConfig().momentumCarry) ? Math.max(0D, getConfig().momentumCarry) : 0D;
    Vector carry = direction.clone().normalize().multiply(momentum * momentumMultiplier(preference(p, MOMENTUM)));
    PendingAttack attack = incomingAttack == null ? null : new PendingAttack(ticket, incomingAttack.getDamageSource(),
        incomingAttack.getOriginalDamage(EntityDamageEvent.DamageModifier.BASE));
    AdaptAdaptationTeleportEvent event = new AdaptAdaptationTeleportEvent(!Bukkit.isPrimaryThread(), getPlayer(p), this,
        origin.clone(), destination.clone());
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()) {
      return false;
    }
    destination = event.getToLocation().clone();
    if (!validCandidate(p, origin, destination, getBlinkDistance(getLevel(p)))
        || !canInteract(p, destination) || !checkRegion(p, destination)) {
      return false;
    }

    BlinkOperation operation = new BlinkOperation(id, ticket, origin, destination, carry, getPearlDamage(getLevel(p)), attack);
    fx(operation.origin(), FxPriority.TRANSITION)
        .line(Particle.REVERSE_PORTAL, operation.requestedDestination().getX(),
            operation.requestedDestination().getY() + 1, operation.requestedDestination().getZ(), 24)
        .particle(Particle.REVERSE_PORTAL, 6, 0, 1.0, 0, 0.25, 0.03)
        .chord(Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.4f, 1.7f);

    CompletableFuture<Boolean> teleport;
    try {
      teleport = PaperCompat.teleportAsync(p, operation.requestedDestination(), PlayerTeleportEvent.TeleportCause.PLUGIN);
    } catch (RuntimeException exception) {
      Adapt.error(exception);
      Adapt.error("Rift Blink could not start a teleport for " + p.getUniqueId() + ".");
      return false;
    }

    if (teleport == null) {
      Adapt.error("Rift Blink got no teleport future for " + id + ".");
      return false;
    }
    if (incomingAttack != null) {
      deferredAttacks.put(id, attack);
      incomingAttack.setCancelled(true);
    }
    teleport.whenComplete((success, failure) -> finishBlink(p, operation, success, failure));
    return true;
  }

  private void finishBlink(Player p, BlinkOperation operation, Boolean success, Throwable failure) {
    if (failure != null) {
      Adapt.error(failure);
      Adapt.error("Rift Blink teleport failed for " + operation.playerId() + ".");
    }

    if (!J.runEntity(p, () -> {
      if (!pendingBlinks.remove(operation.playerId(), operation.ticket())) {
        return;
      }
      if (operation.attack() != null && !deferredAttacks.remove(operation.playerId(), operation.attack())) {
        return;
      }
      if (!p.isOnline()) {
        return;
      }
      if (failure != null || !Boolean.TRUE.equals(success)) {
        replayAttack(p, operation.attack());
        return;
      }

      lastBlink.mark(operation.playerId());
      if (getActiveSiblingLevel(p, "rift-resist") > 0) {
        RiftResist.riftResistStackAdd(this, p, 10, 5);
      }

      p.setFallDistance(0);
      p.setVelocity(operation.carry());
      applyPearlDamage(p, operation.pearlDamage());
      Location actualDestination = p.getLocation().clone();
      fx(actualDestination, FxPriority.TRANSITION)
          .ring(Particles.END_ROD, 0.8, 10, 0.1)
          .sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.3f);
      addStat(p, "rift.teleports", 1);
      addStat(p, "rift.blink.blinks", 1);
      if (operation.origin().getWorld().equals(actualDestination.getWorld())) {
        addStat(p, "rift.blink.distance-blinked", (int) operation.origin().distance(actualDestination));
      }
    })) {
      pendingBlinks.remove(operation.playerId(), operation.ticket());
    }
  }

  private void replayAttack(Player player, PendingAttack attack) {
    if (attack == null || player.isDead()) {
      return;
    }
    replayingAttacks.put(player.getUniqueId(), Boolean.TRUE);
    int immunity = player.getNoDamageTicks();
    try {
      player.setNoDamageTicks(0);
      player.damage(attack.damage(), attack.source());
    } finally {
      player.setNoDamageTicks(Math.max(immunity, player.getNoDamageTicks()));
      replayingAttacks.remove(player.getUniqueId());
    }
  }

  private void applyPearlDamage(Player p, double damage) {
    if (!Double.isFinite(damage) || damage <= 0D || p.isDead()) {
      return;
    }

    DamageSource source = DamageSource.builder(DamageType.ENDER_PEARL).build();
    p.damage(damage, source);
  }

  private Location findBlinkDestination(Player player, Vector requestedDirection) {
    Location eye = player.getEyeLocation();
    Vector direction = requestedDirection.clone();
    if (!Double.isFinite(direction.lengthSquared()) || direction.lengthSquared() <= 0.000001) {
      return null;
    }
    direction.normalize();
    double maxDistance = getBlinkDistance(getLevel(player));
    double readableDistance = readableReach(eye, direction, maxDistance);
    if (readableDistance < 1D) {
      return null;
    }
    boolean phase = phaseForDirection(getConfig().allowPhasing, preference(player, PHASING), player.isSneaking(), direction.getY());
    Targeting targeting = preference(player, TARGETING);
    RayTraceResult hit = phase ? null : eye.getWorld().rayTraceBlocks(eye, direction, readableDistance, FluidCollisionMode.NEVER, true);
    Location origin = player.getLocation();
    Location best = null;
    if (hit != null && hit.getHitBlock() != null) {
      Block mantleFeet = hit.getHitBlock().getRelative(BlockFace.UP);
      Location mantle = mantleFeet.getLocation().add(0.5D, 0D, 0.5D);
      if (isStandableBlock(mantleFeet) && validCandidate(player, origin, mantle, maxDistance)) {
        if (targeting == Targeting.DISTANCE) {
          return mantle;
        }
        best = mantle;
      }
    }
    Double hitDistance = hit == null ? null : hit.getHitPosition().distance(eye.toVector());
    double reach = blinkReach(phase, readableDistance, hitDistance);
    int verticalSearch = targeting == Targeting.VERTICALITY ? Math.max(0, Math.min(16, getConfig().verticalSearchHeight)) : 0;
    for (double distance = reach; distance >= 1D; distance -= 1D) {
      Location feet = eye.clone().add(direction.clone().multiply(distance)).subtract(0D, player.getEyeHeight(), 0D);
      Location resolved = resolveStand(feet, preference(player, LANDING));
      if (validCandidate(player, origin, resolved, maxDistance)) {
        if (targeting == Targeting.DISTANCE) {
          return resolved;
        }
        best = betterCandidate(origin, best, resolved, targeting);
      }
      for (int rise = 1; rise <= verticalSearch; rise++) {
        Location raised = new Location(feet.getWorld(), feet.getX(), feet.getBlockY() + rise, feet.getZ());
        if (!canRead(raised, 0.35D) || !isStandableBlock(raised.getBlock())
            || !validCandidate(player, origin, raised, maxDistance)
            || (!phase && !hasClearSight(eye, raised.clone().add(0D, player.getEyeHeight(), 0D)))) {
          continue;
        }
        best = betterCandidate(origin, best, raised, targeting);
      }
    }
    return best;
  }

  private boolean validCandidate(Player player, Location origin, Location candidate, double maxDistance) {
    double minimum = Math.max(0.5D, getConfig().minBlinkDistance);
    return candidate != null && Double.isFinite(candidate.getX()) && Double.isFinite(candidate.getY())
        && Double.isFinite(candidate.getZ()) && origin.getWorld().equals(candidate.getWorld())
        && origin.distanceSquared(candidate) >= minimum * minimum
        && origin.distanceSquared(candidate) <= maxDistance * maxDistance
        && isSafeDestination(candidate, player.getHeight());
  }

  private double readableReach(Location eye, Vector direction, double maxDistance) {
    double reach = 0D;
    for (double distance = 0D; distance <= maxDistance; distance += 0.5D) {
      if (!canRead(eye.clone().add(direction.clone().multiply(distance)), 0.4D)) {
        break;
      }
      reach = distance;
    }
    return reach;
  }

  private boolean hasClearSight(Location from, Location to) {
    Vector direction = to.toVector().subtract(from.toVector());
    double distance = direction.length();
    if (distance < 0.001D) {
      return true;
    }
    direction.normalize();
    return readableReach(from, direction, distance + 0.5D) >= distance
        && from.getWorld().rayTraceBlocks(from, direction, distance, FluidCollisionMode.NEVER, true) == null;
  }

  private Location resolveStand(Location feet, Landing landing) {
    if (!canRead(feet, 0.35D)) {
      return null;
    }
    if (landing == Landing.NONE) {
      return isSafeDestination(feet, 1.8D) ? feet.clone() : null;
    }
    int snapDepth = landingSnapDepth(getConfig().groundSnapDepth, landing);
    for (int i = 0; i <= snapDepth; i++) {
      Location candidateLocation = feet.clone().subtract(0D, i, 0D);
      if (!canRead(candidateLocation, 0.35D)) {
        break;
      }
      Block candidate = candidateLocation.getBlock();
      if (isStandableBlock(candidate)) {
        return new Location(feet.getWorld(), feet.getX(), candidate.getY(), feet.getZ());
      }
      if (!candidate.isPassable()) {
        break;
      }
    }
    return isSafeDestination(feet, 1.8D) ? feet.clone() : null;
  }

  private boolean isStandableBlock(Block feet) {
    if (!canRead(feet.getLocation(), 0.35D) || !feet.isPassable() || !feet.getRelative(BlockFace.UP).isPassable()) {
      return false;
    }
    Block floor = feet.getRelative(BlockFace.DOWN);
    return !floor.isPassable() && floor.getBoundingBox().getMaxY() >= feet.getY() - 0.001D;
  }

  private boolean canRead(Location location, double radius) {
    World world = location.getWorld();
    if (world == null || location.getY() < world.getMinHeight() + 1D || location.getY() + 2D >= world.getMaxHeight()) {
      return false;
    }
    int minChunkX = (int) Math.floor((location.getX() - radius) / 16D);
    int maxChunkX = (int) Math.floor((location.getX() + radius) / 16D);
    int minChunkZ = (int) Math.floor((location.getZ() - radius) / 16D);
    int maxChunkZ = (int) Math.floor((location.getZ() + radius) / 16D);
    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          return false;
        }
      }
    }
    return !J.isFoliaThreading() || J.isOwnedByCurrentRegion(location, radius, radius);
  }

  private boolean isSafeDestination(Location feet, double height) {
    if (!canRead(feet, 0.35D) || !Double.isFinite(height)
        || feet.getY() + Math.max(1.8D, height) >= feet.getWorld().getMaxHeight()
        || !feet.getWorld().getWorldBorder().isInside(feet.clone().add(-0.3D, 0D, -0.3D))
        || !feet.getWorld().getWorldBorder().isInside(feet.clone().add(0.3D, 0D, 0.3D))) {
      return false;
    }
    BoundingBox body = new BoundingBox(feet.getX() - 0.3D, feet.getY() + 0.001D, feet.getZ() - 0.3D,
        feet.getX() + 0.3D, feet.getY() + Math.max(1.8D, height), feet.getZ() + 0.3D);
    for (int x = (int) Math.floor(body.getMinX()); x <= (int) Math.floor(body.getMaxX()); x++) {
      for (int y = (int) Math.floor(body.getMinY()); y <= (int) Math.floor(body.getMaxY()); y++) {
        for (int z = (int) Math.floor(body.getMinZ()); z <= (int) Math.floor(body.getMaxZ()); z++) {
          Block block = feet.getWorld().getBlockAt(x, y, z);
          if (block.getType() == Material.LAVA || block.getType() == Material.FIRE || block.getType() == Material.SOUL_FIRE
              || (!block.isPassable() && block.getBoundingBox().overlaps(body))) {
            return false;
          }
        }
      }
    }
    return true;
  }

  static double momentumMultiplier(Momentum momentum) {
    return switch (momentum) {
      case FULL -> 1D;
      case HALF -> 0.5D;
      case STOP -> 0D;
    };
  }

  static int landingSnapDepth(int configuredDepth, Landing landing) {
    int depth = Math.max(0, Math.min(32, configuredDepth));
    return switch (landing) {
      case SERVER -> depth;
      case NEAR -> Math.min(2, depth);
      case NONE -> 0;
    };
  }

  static boolean phaseForDirection(boolean serverAllows, Phasing mode, boolean sneaking, double verticalAim) {
    return mode == Phasing.AUTO ? serverAllows && verticalAim <= 0.2D : canPhase(serverAllows, mode, sneaking);
  }

  static boolean canPhase(boolean serverAllows, Phasing mode, boolean sneaking) {
    return serverAllows && (mode == Phasing.AIM || (mode == Phasing.SNEAK && sneaking));
  }

  static boolean isReactiveAttack(EntityDamageEvent.DamageCause cause, double damage) {
    return Double.isFinite(damage) && damage > 0D && switch (cause) {
      case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE -> true;
      default -> false;
    };
  }

  static Vector awayDirection(Location origin, Vector look, Location source) {
    if (source == null || (source.getWorld() != null && !origin.getWorld().equals(source.getWorld()))) {
      return look.clone();
    }
    Vector away = origin.toVector().subtract(source.toVector()).setY(0D);
    return away.lengthSquared() > 0.000001D ? away.normalize() : look.clone();
  }

  static Location betterCandidate(Location origin, Location current, Location candidate, Targeting targeting) {
    if (current == null) {
      return candidate;
    }
    if (targeting == Targeting.VERTICALITY && candidate.getY() != current.getY()) {
      return candidate.getY() > current.getY() ? candidate : current;
    }
    return origin.distanceSquared(candidate) > origin.distanceSquared(current) ? candidate : current;
  }

  static double blinkReach(boolean phase, double maxDistance, Double hitDistance) {
    if (phase || hitDistance == null) {
      return maxDistance;
    }
    return Math.max(0, hitDistance - 0.5);
  }

  static double calculatePearlDamage(int level, double baseDamage, double reductionPerLevel, double minimumDamage) {
    double safeBase = Double.isFinite(baseDamage) ? Math.max(0D, baseDamage) : 5D;
    double safeReduction = Double.isFinite(reductionPerLevel) ? Math.max(0D, reductionPerLevel) : 0D;
    double safeMinimum = Double.isFinite(minimumDamage) ? Math.max(0D, Math.min(safeBase, minimumDamage)) : 0D;
    double scaled = safeBase - (Math.max(0, level - 1) * safeReduction);
    return Math.max(safeMinimum, scaled);
  }

  @ConfigDescription("Blink with player-selectable activation, targeting and phasing. Reactive activation unlocks at level 2 and replaces double-jump with an attack dodge. Successful blinks deal level-scaled ender pearl damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between successful Rift Blink triggers in milliseconds.", impact = "Higher values reduce blink frequency; lower values allow faster reuse.")
    int cooldownMillis = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vanilla ender pearl damage applied after a successful level-1 blink.", impact = "Higher values make low-level blinks more dangerous.")
    double pearlDamageBase = 5.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Blink self-damage removed for each level beyond the first.", impact = "Higher values make leveling reduce the health cost faster.")
    double pearlDamageReductionPerLevel = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum ender pearl damage a successful blink can inflict.", impact = "Higher values retain more self-damage at high levels.")
    double minimumPearlDamage = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Blink distance in blocks at level 0 before level scaling. The combined range is bounded from 0 to 128 blocks.", impact = "Higher values make every blink reach further regardless of level.")
    double baseDistance = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional blink distance in blocks granted at max level, scaling linearly with level.", impact = "Higher values widen the gap between low-level and max-level blink reach.")
    double distanceFactor = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Blocks searched downward from the aimed point to prefer landing on solid ground, bounded from 0 to 32.", impact = "Higher values snap blinks to ground from further above it; lower values allow more mid-air blinks.")
    int groundSnapDepth = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Velocity carried along the look direction after a blink.", impact = "Higher values give a stronger dash feel on arrival; 0 stops the player dead.")
    double momentumCarry = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum distance a blink must cover to trigger.", impact = "Higher values prevent short hops from consuming the blink.")
    double minBlinkDistance = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows phasing through obstacles using the player-selected phasing mode.", impact = "False blocks phasing regardless of player preference.")
    boolean allowPhasing = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum extra blocks searched upward for a landing when verticality is preferred, bounded from 0 to 16.", impact = "Higher values allow higher visible ledges within the normal blink range.")
    int verticalSearchHeight = 6;

    public Config() {
      baseCost = 7;
      costFactor = 0.12;
      initialCost = 1;
    }
  }

  public enum Phasing { SNEAK, AIM, NEVER, AUTO }

  public enum Targeting { DISTANCE, VERTICALITY }

  public enum Activation { MANUAL, REACTIVE }

  public enum ReactiveDirection { LOOK, AWAY_FROM_ATTACKER }

  private record PendingAttack(UUID ticket, DamageSource source, double damage) {
  }

  private record BlinkOperation(UUID playerId, UUID ticket, Location origin, Location requestedDestination, Vector carry,
                                double pearlDamage, PendingAttack attack) {
  }

  public enum Landing { SERVER, NEAR, NONE }
  public enum Momentum { FULL, HALF, STOP }
}
