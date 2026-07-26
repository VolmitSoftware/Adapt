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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.content.event.AdaptAdaptationTeleportEvent;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;
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
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


public class RiftBlink extends SimpleAdaptation<RiftBlink.Config> {
  private final Cooldowns lastBlink = cooldowns();
  private final DoubleJumpGesture doubleJump = new DoubleJumpGesture();
  private final Map<UUID, Boolean> pendingBlinks = playerState();

  public RiftBlink() {
    super("rift-blink");
    registerConfiguration(Config.class);
    setIcon(Material.FEATHER);
    setInterval(9288);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_blink_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_EYE)
            .key("challenge_rift_blink_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_blink_500", "rift.blink.blinks", 500, 400);
    registerMilestone("challenge_rift_blink_5k", "rift.blink.distance-blinked", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getBlinkDistance(level), 1), 1);
    statLore(v, Form.f(getPearlDamage(level) / 2D, 1), 2);
    if (getConfig().phaseWhileSneaking) {
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
    if (!isBlinkEligible(p)) {
      doubleJump.reset(p);
      return;
    }

    if (!doubleJump.update(p) || isOnCooldown(p.getUniqueId())) {
      return;
    }

    attemptBlink(p);
  }


  private boolean isBlinkEligible(Player p) {
    return p.getGameMode() == GameMode.SURVIVAL && hasActiveAdaptation(p);
  }

  private boolean isOnCooldown(UUID id) {
    return pendingBlinks.containsKey(id) || !lastBlink.isReady(id, Math.max(0L, getConfig().cooldownMillis));
  }

  private double getBlinkDistance(int level) {
    return getConfig().baseDistance + (getLevelPercent(level) * getConfig().distanceFactor);
  }

  private double getPearlDamage(int level) {
    return calculatePearlDamage(
        level,
        getConfig().pearlDamageBase,
        getConfig().pearlDamageReductionPerLevel,
        getConfig().minimumPearlDamage
    );
  }

  private void attemptBlink(Player p) {
    UUID id = p.getUniqueId();
    if (isOnCooldown(id)) {
      return;
    }

    pendingBlinks.put(id, Boolean.TRUE);

    Location origin = p.getLocation().clone();
    Location destination = findBlinkDestination(p);
    double minDistance = Math.max(0.5, getConfig().minBlinkDistance);
    if (destination == null || origin.distanceSquared(destination) < minDistance * minDistance) {
      pendingBlinks.remove(id);
      fx(p, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 4, 0.2)
          .sound(Sound.BLOCK_CONDUIT_DEACTIVATE, 0.5f, 1.4f);
      return;
    }

    destination.setYaw(origin.getYaw());
    destination.setPitch(origin.getPitch());
    Vector carry = origin.getDirection().clone().multiply(getConfig().momentumCarry);
    BlinkOperation operation = new BlinkOperation(id, origin, destination, carry, getLevel(p));
    AdaptAdaptationTeleportEvent event = new AdaptAdaptationTeleportEvent(!Bukkit.isPrimaryThread(), getPlayer(p), this,
        operation.origin(), operation.requestedDestination().clone());
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()) {
      pendingBlinks.remove(id);
      return;
    }

    fx(operation.origin(), FxPriority.TRANSITION)
        .line(Particle.REVERSE_PORTAL, operation.requestedDestination().getX(),
            operation.requestedDestination().getY() + 1, operation.requestedDestination().getZ(), 24)
        .particle(Particle.REVERSE_PORTAL, 6, 0, 1.0, 0, 0.25, 0.03)
        .chord(Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.4f, 1.7f);

    CompletableFuture<Boolean> teleport;
    try {
      teleport = p.teleportAsync(operation.requestedDestination(), PlayerTeleportEvent.TeleportCause.PLUGIN);
    } catch (RuntimeException exception) {
      pendingBlinks.remove(id);
      exception.printStackTrace();
      Adapt.error("Rift Blink could not start a teleport for " + p.getUniqueId() + ".");
      return;
    }

    teleport.whenComplete((success, failure) -> finishBlink(p, operation, success, failure));
  }

  private void finishBlink(Player p, BlinkOperation operation, Boolean success, Throwable failure) {
    if (failure != null) {
      failure.printStackTrace();
      Adapt.error("Rift Blink teleport failed for " + operation.playerId() + ".");
    }

    if (!J.runEntity(p, () -> {
      pendingBlinks.remove(operation.playerId());
      if (failure != null || !Boolean.TRUE.equals(success) || !p.isOnline()) {
        return;
      }

      lastBlink.mark(operation.playerId());
      PlayerSkillLine line = getPlayer(p).getData().getSkillLineNullable("rift");
      PlayerAdaptation adaptation = line != null ? line.getAdaptation("rift-resist") : null;
      if (adaptation != null && adaptation.getLevel() > 0) {
        RiftResist.riftResistStackAdd(this, p, 10, 5);
      }

      p.setFallDistance(0);
      p.setVelocity(operation.carry());
      applyPearlDamage(p, getPearlDamage(operation.level()));
      Location actualDestination = p.getLocation().clone();
      fx(actualDestination, FxPriority.TRANSITION)
          .ring(Particles.END_ROD, 0.8, 10, 0.1)
          .sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.3f);
      addStat(p, "rift.teleports", 1);
      addStat(p, "rift.blink.blinks", 1);
      addStat(p, "rift.blink.distance-blinked", (int) operation.origin().distance(actualDestination));
    })) {
      pendingBlinks.remove(operation.playerId());
    }
  }

  private void applyPearlDamage(Player p, double damage) {
    if (!Double.isFinite(damage) || damage <= 0D || p.isDead()) {
      return;
    }

    DamageSource source = DamageSource.builder(DamageType.ENDER_PEARL).build();
    p.damage(damage, source);
  }

  private Location findBlinkDestination(Player p) {
    Location eye = p.getEyeLocation();
    Vector direction = eye.getDirection().clone();
    if (direction.lengthSquared() <= 0.000001) {
      return null;
    }

    direction.normalize();
    double maxDistance = getBlinkDistance(getLevel(p));
    boolean phase = getConfig().phaseWhileSneaking && p.isSneaking();
    RayTraceResult hit = phase
        ? null
        : p.getWorld().rayTraceBlocks(eye, direction, maxDistance, FluidCollisionMode.NEVER, true);

    if (hit != null && hit.getHitBlock() != null) {
      Block mantleFeet = hit.getHitBlock().getRelative(BlockFace.UP);
      if (isStandableBlock(mantleFeet)) {
        return mantleFeet.getLocation().add(0.5, 0, 0.5);
      }
    }

    Double hitDistance = hit == null ? null : hit.getHitPosition().distance(eye.toVector());
    double reach = blinkReach(phase, maxDistance, hitDistance);
    for (double distance = reach; distance >= 1.0; distance -= 1.0) {
      Location feet = eye.clone().add(direction.clone().multiply(distance)).subtract(0, p.getEyeHeight(), 0);
      Location resolved = resolveStand(feet);
      if (resolved != null) {
        return resolved;
      }
    }

    return null;
  }

  private Location resolveStand(Location feet) {
    Block base = feet.getBlock();
    int snapDepth = Math.max(0, getConfig().groundSnapDepth);
    for (int i = 0; i <= snapDepth; i++) {
      Block candidate = base.getRelative(0, -i, 0);
      if (isStandableBlock(candidate)) {
        return new Location(feet.getWorld(), feet.getX(), candidate.getY(), feet.getZ());
      }

      if (candidate.getType().isSolid()) {
        break;
      }
    }

    if (!base.getType().isSolid() && !base.getRelative(BlockFace.UP).getType().isSolid()) {
      return feet.clone();
    }

    return null;
  }

  private boolean isStandableBlock(Block feet) {
    return !feet.getType().isSolid()
        && !feet.getRelative(BlockFace.UP).getType().isSolid()
        && feet.getRelative(BlockFace.DOWN).getType().isSolid();
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

  @ConfigDescription("Double-jump to blink toward where you are looking without consuming a pearl, taking ender pearl damage that decreases by level; sneaking while blinking phases through walls.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between successful Rift Blink triggers in milliseconds.", impact = "Higher values reduce blink frequency; lower values allow faster reuse.")
    int cooldownMillis = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vanilla ender pearl damage applied after a successful level-1 blink.", impact = "Higher values make low-level blinks more dangerous.")
    double pearlDamageBase = 5.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Blink self-damage removed for each level beyond the first.", impact = "Higher values make leveling reduce the health cost faster.")
    double pearlDamageReductionPerLevel = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum ender pearl damage a successful blink can inflict.", impact = "Higher values retain more self-damage at high levels.")
    double minimumPearlDamage = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Blink distance in blocks at level 0 before level scaling.", impact = "Higher values make every blink reach further regardless of level.")
    double baseDistance = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional blink distance in blocks granted at max level, scaling linearly with level.", impact = "Higher values widen the gap between low-level and max-level blink reach.")
    double distanceFactor = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Blocks searched downward from the aimed point to prefer landing on solid ground.", impact = "Higher values snap blinks to ground from further above it; lower values allow more mid-air blinks.")
    int groundSnapDepth = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Velocity carried along the look direction after a blink.", impact = "Higher values give a stronger dash feel on arrival; 0 stops the player dead.")
    double momentumCarry = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum distance a blink must cover to trigger.", impact = "Higher values prevent short hops from consuming the blink.")
    double minBlinkDistance = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows a blink triggered while sneaking to phase through walls and obstacles, landing at the farthest open space within range.", impact = "True lets sneaking blinks ignore obstructions along the look line; false makes every blink stop at the first obstacle.")
    boolean phaseWhileSneaking = true;

    public Config() {
      baseCost = 7;
      costFactor = 0.12;
      initialCost = 1;
    }
  }

  private record BlinkOperation(UUID playerId, Location origin, Location requestedDestination, Vector carry, int level) {
  }
}
