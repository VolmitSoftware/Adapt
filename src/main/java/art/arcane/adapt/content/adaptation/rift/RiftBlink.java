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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static art.arcane.adapt.api.adaptation.chunk.ChunkLoading.loadChunkAsync;


public class RiftBlink extends SimpleAdaptation<RiftBlink.Config> {
  private final Cooldowns lastBlink = cooldowns();
  private final DoubleJumpGesture doubleJump = new DoubleJumpGesture();

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
    if (getConfig().pearlConsumeChance > 0) {
      v.addLore(C.RED + "* " + Form.pc(getConfig().pearlConsumeChance, 0) + C.GRAY + " " + Localizer.dLocalize("rift.blink.lore_cost_pearl"));
    }
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
    return hasActiveAdaptation(p) && p.getGameMode() == GameMode.SURVIVAL;
  }

  private boolean isOnCooldown(UUID id) {
    return !lastBlink.isReady(id, Math.max(0L, getConfig().cooldownMillis));
  }

  private double getBlinkDistance(int level) {
    return getConfig().baseDistance + (getLevelPercent(level) * getConfig().distanceFactor);
  }

  private void attemptBlink(Player p) {
    UUID id = p.getUniqueId();
    if (isOnCooldown(id)) {
      return;
    }

    Location origin = p.getLocation().clone();
    Location destination = findBlinkDestination(p);
    double minDistance = Math.max(0.5, getConfig().minBlinkDistance);
    if (destination == null || origin.distanceSquared(destination) < minDistance * minDistance) {
      fx(p, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 4, 0.2)
          .sound(Sound.BLOCK_CONDUIT_DEACTIVATE, 0.5f, 1.4f);
      return;
    }

    lastBlink.mark(id);
    destination.setYaw(origin.getYaw());
    destination.setPitch(origin.getPitch());
    Vector carry = origin.getDirection().clone().multiply(getConfig().momentumCarry);
    fx(origin, FxPriority.TRANSITION)
        .line(Particle.REVERSE_PORTAL, destination.getX(), destination.getY() + 1, destination.getZ(), 24)
        .particle(Particle.REVERSE_PORTAL, 6, 0, 1.0, 0, 0.25, 0.03)
        .chord(Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.4f, 1.7f);

    loadChunkAsync(destination, chunk -> J.runEntity(p, () -> {
      AdaptAdaptationTeleportEvent event = new AdaptAdaptationTeleportEvent(!Bukkit.isPrimaryThread(), getPlayer(p), this, origin, destination.clone());
      Bukkit.getPluginManager().callEvent(event);
      if (event.isCancelled()) {
        lastBlink.clear(id);
        return;
      }

      consumeBlinkPearl(p);
      PlayerSkillLine line = getPlayer(p).getData().getSkillLineNullable("rift");
      PlayerAdaptation adaptation = line != null ? line.getAdaptation("rift-resist") : null;
      if (adaptation != null && adaptation.getLevel() > 0) {
        RiftResist.riftResistStackAdd(this, p, 10, 5);
      }

      J.teleport(p, destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
      p.setFallDistance(0);
      p.setVelocity(carry);
      fx(destination, FxPriority.TRANSITION)
          .ring(Particles.END_ROD, 0.8, 10, 0.1)
          .sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.3f);
      addStat(p, "rift.teleports", 1);
      addStat(p, "rift.blink.blinks", 1);
      addStat(p, "rift.blink.distance-blinked", (int) origin.distance(destination));
    }));
  }

  private Location findBlinkDestination(Player p) {
    Location eye = p.getEyeLocation();
    Vector direction = eye.getDirection().clone();
    if (direction.lengthSquared() <= 0.000001) {
      return null;
    }

    direction.normalize();
    double maxDistance = getBlinkDistance(getLevel(p));
    RayTraceResult hit = p.getWorld().rayTraceBlocks(eye, direction, maxDistance, FluidCollisionMode.NEVER, true);

    if (hit != null && hit.getHitBlock() != null) {
      Block mantleFeet = hit.getHitBlock().getRelative(BlockFace.UP);
      if (isStandableBlock(mantleFeet)) {
        return mantleFeet.getLocation().add(0.5, 0, 0.5);
      }
    }

    double reach = hit == null ? maxDistance : Math.max(0, hit.getHitPosition().distance(eye.toVector()) - 0.5);
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

  private void consumeBlinkPearl(Player p) {
    double chance = Math.max(0D, Math.min(1D, getConfig().pearlConsumeChance));
    if (chance <= 0D || ThreadLocalRandom.current().nextDouble() >= chance) {
      return;
    }

    for (ItemStack stack : p.getInventory().getContents()) {
      if (stack == null || stack.getType() != Material.ENDER_PEARL || stack.hasItemMeta()) {
        continue;
      }

      stack.setAmount(stack.getAmount() - 1);
      return;
    }
  }

  @ConfigDescription("Double-jump to blink toward where you are looking.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between successful Rift Blink triggers in milliseconds.", impact = "Higher values reduce blink frequency; lower values allow faster reuse.")
    int cooldownMillis = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance per successful blink to consume one plain ender pearl from the inventory.", impact = "Higher values make blinking drain pearls faster; 0 disables the pearl cost.")
    double pearlConsumeChance = 0.2;
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

    public Config() {
      baseCost = 7;
      costFactor = 0.12;
      initialCost = 1;
    }
  }
}
