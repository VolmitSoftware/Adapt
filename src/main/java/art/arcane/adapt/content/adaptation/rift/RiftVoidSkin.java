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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RiftVoidSkin extends SimpleAdaptation<RiftVoidSkin.Config> {
  private static final double HARD_MAX_TRIGGER_HEALTH = 12D;
  private static final double HARD_MAX_SEARCH_RADIUS = 16D;
  private static final int SAFE_SAMPLES = 32;

  private final Cooldowns cooldown = cooldowns();

  public RiftVoidSkin() {
    super("rift-void-skin");
    registerConfiguration(Config.class);
    setIcon(Material.ECHO_SHARD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_void_skin_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ECHO_SHARD)
            .key("challenge_rift_void_skin_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_void_skin_50", "rift.void-skin.escapes", 50, 400);
    registerMilestone("challenge_rift_void_skin_500", "rift.void-skin.escapes", 500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getTriggerHealth(level) / 2D, 1), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 2);
    v.addLore(C.RED + "* " + Localizer.dLocalize("rift.void_skin.lore3"));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    double resulting = p.getHealth() - e.getFinalDamage();
    if (resulting <= 0D || resulting >= getTriggerHealth(level)) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!cooldown.isReady(id, getCooldownMillis(level))) {
      return;
    }

    if (!hasPlainPearl(p)) {
      return;
    }

    Location destination = findSafeSpot(p);
    if (destination == null) {
      return;
    }

    if (!consumePlainPearl(p)) {
      return;
    }

    cooldown.mark(id);
    Location origin = p.getLocation().clone();
    fx(origin, FxPriority.TRANSITION)
        .dome(Particles.ENCHANTMENT_TABLE, 1.1, 16)
        .particle(Particle.WITCH, 10, 0, 1.0, 0, 0.4, 0.02)
        .chord(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.8f, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, 1.2f);

    destination.setYaw(origin.getYaw());
    destination.setPitch(origin.getPitch());
    J.runEntity(p, () -> {
      if (!p.isOnline()) {
        return;
      }
      J.teleport(p, destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
      p.setFallDistance(0f);
      p.addPotionEffect(new PotionEffect(PotionEffectTypes.DAMAGE_RESISTANCE,
          getResistanceTicks(level), getResistanceAmplifier(), true, false, false));
      fx(destination, FxPriority.TRANSITION)
          .ring(Particles.END_ROD, 0.8, 12, 0.1)
          .particle(Particle.REVERSE_PORTAL, 8, 0, 1.0, 0, 0.3, 0.04)
          .chord(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.3f, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.5f, 1.1f);
    });

    addStat(p, "rift.void-skin.escapes", 1);
    addStat(p, "rift.teleports", 1);
    xp(p, getConfig().xpOnEscape, "rift:void-skin:escape");
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    cooldown.clear(e.getPlayer().getUniqueId());
  }

  private double getTriggerHealth(int level) {
    double value = getConfig().triggerHealthBase + (Math.max(0, level - 1) * getConfig().triggerHealthPerLevel);
    return Math.max(1D, Math.min(HARD_MAX_TRIGGER_HEALTH, value));
  }

  private long getCooldownMillis(int level) {
    long value = getConfig().cooldownBaseMillis - ((long) Math.max(0, level - 1) * getConfig().cooldownReductionPerLevelMillis);
    return Math.max(getConfig().minimumCooldownMillis, value);
  }

  private int getResistanceTicks(int level) {
    return Math.max(20, getConfig().resistanceTicksBase + (level * getConfig().resistanceTicksPerLevel));
  }

  private int getResistanceAmplifier() {
    return Math.max(0, Math.min(4, getConfig().resistanceAmplifier));
  }

  private Location findSafeSpot(Player p) {
    Location origin = p.getLocation();
    double maxRadius = Math.max(3D, Math.min(HARD_MAX_SEARCH_RADIUS, getConfig().searchRadius));
    double minRadius = Math.min(maxRadius - 1D, Math.max(2D, getConfig().minRadius));
    Location best = null;
    double bestDistance = -1D;
    for (int i = 0; i < SAFE_SAMPLES; i++) {
      double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2D);
      double distance = minRadius + ThreadLocalRandom.current().nextDouble(maxRadius - minRadius);
      double dx = Math.cos(angle) * distance;
      double dz = Math.sin(angle) * distance;
      Location feet = origin.clone().add(dx, 0, dz);
      Location resolved = resolveStandable(feet.getBlock(), 4);
      if (resolved == null) {
        continue;
      }
      double resolvedDistance = resolved.distanceSquared(origin);
      if (resolvedDistance > bestDistance) {
        bestDistance = resolvedDistance;
        best = resolved;
      }
    }
    return best;
  }

  private Location resolveStandable(Block base, int vertical) {
    for (int dy = 0; dy <= vertical; dy++) {
      Block up = base.getRelative(0, dy, 0);
      if (isStandable(up)) {
        return up.getLocation().add(0.5, 0, 0.5);
      }
      Block down = base.getRelative(0, -dy, 0);
      if (isStandable(down)) {
        return down.getLocation().add(0.5, 0, 0.5);
      }
    }
    return null;
  }

  private boolean isStandable(Block feet) {
    Block head = feet.getRelative(BlockFace.UP);
    Block ground = feet.getRelative(BlockFace.DOWN);
    return feet.isPassable()
        && head.isPassable()
        && ground.getType().isSolid()
        && !isHazard(ground.getType())
        && !isHazard(feet.getType())
        && !isHazard(head.getType());
  }

  private boolean isHazard(Material material) {
    return switch (material) {
      case LAVA, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, MAGMA_BLOCK, CACTUS, POWDER_SNOW, SWEET_BERRY_BUSH, WITHER_ROSE -> true;
      default -> false;
    };
  }

  private boolean hasPlainPearl(Player p) {
    for (ItemStack stack : p.getInventory().getContents()) {
      if (isPlainPearl(stack)) {
        return true;
      }
    }
    return false;
  }

  private boolean consumePlainPearl(Player p) {
    for (ItemStack stack : p.getInventory().getContents()) {
      if (isPlainPearl(stack)) {
        stack.setAmount(stack.getAmount() - 1);
        return true;
      }
    }
    return false;
  }

  private boolean isPlainPearl(ItemStack stack) {
    return stack != null && stack.getType() == Material.ENDER_PEARL && !stack.hasItemMeta() && stack.getAmount() > 0;
  }

  @ConfigDescription("Dropping below a few hearts blinks you to a nearby safe spot with brief resistance, spending an ender pearl.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Health in half-hearts of raw HP at level 1 below which the escape triggers.", impact = "Higher values trigger the escape while you still have more health.")
    double triggerHealthBase = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra trigger HP granted per adaptation level beyond the first.", impact = "Higher values make leveling trigger the escape at a safer health level.")
    double triggerHealthPerLevel = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base cooldown in milliseconds between escapes.", impact = "Higher values force longer waits between escapes.")
    long cooldownBaseMillis = 120000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds per adaptation level beyond the first.", impact = "Higher values make leveling shorten the cooldown faster.")
    long cooldownReductionPerLevelMillis = 18000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Lowest possible cooldown in milliseconds regardless of level.", impact = "Higher values keep a floor under cooldown reduction.")
    long minimumCooldownMillis = 45000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base resistance duration in ticks applied after an escape.", impact = "Higher values keep the resistance buff up longer.")
    int resistanceTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra resistance ticks per adaptation level.", impact = "Higher values extend the resistance buff as you level.")
    int resistanceTicksPerLevel = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Resistance amplifier applied after an escape.", impact = "Higher values reduce incoming damage more during the buff.")
    int resistanceAmplifier = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum horizontal search radius in blocks for a safe blink spot.", impact = "Higher values let the escape reach further, up to a hard 16-block limit.")
    double searchRadius = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum horizontal blink distance in blocks.", impact = "Higher values push the escape further from where you were hit.")
    double minRadius = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Skill XP granted when an escape triggers.", impact = "Higher values grant more skill XP per escape.")
    double xpOnEscape = 40;

    public Config() {
      baseCost = 8;
      costFactor = 0.4;
      maxLevel = 4;
      initialCost = 6;
    }
  }
}
