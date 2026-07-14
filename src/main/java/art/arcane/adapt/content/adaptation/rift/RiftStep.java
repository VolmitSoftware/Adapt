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
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class RiftStep extends SimpleAdaptation<RiftStep.Config> {
  private static final double HARD_MAX_SEARCH_RADIUS = 6D;
  private static final int HARD_MAX_SEARCH_DEPTH = 96;

  public RiftStep() {
    super("rift-step");
    registerConfiguration(Config.class);
    setIcon(Material.PHANTOM_MEMBRANE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_step_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.PHANTOM_MEMBRANE)
            .key("challenge_rift_step_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_step_50", "rift.step.saves", 50, 400);
    registerMilestone("challenge_rift_step_500", "rift.step.saves", 500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.YELLOW + Localizer.dLocalize("rift.step.lore1"));
    statLore(v, C.RED, "* ", getXpCost(level), 2);
    v.addLore(C.RED + "* " + Localizer.dLocalize("rift.step.lore3"));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || !p.isSneaking()) {
      return;
    }

    if (p.getHealth() - e.getFinalDamage() > 0D) {
      return;
    }

    if (!hasPlainPearl(p)) {
      return;
    }

    Location destination = findSafeSurface(p);
    if (destination == null) {
      return;
    }

    int xpCost = getXpCost(level);
    if (p.getTotalExperience() < xpCost) {
      fx(p.getEyeLocation(), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 4, 0.2)
          .sound(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.6f, 0.6f);
      return;
    }

    if (!consumePlainPearl(p)) {
      return;
    }

    e.setCancelled(true);
    p.giveExp(-xpCost);
    Location origin = p.getLocation().clone();
    destination.setYaw(origin.getYaw());
    destination.setPitch(origin.getPitch());
    fx(origin, FxPriority.TRANSITION)
        .particle(Particle.REVERSE_PORTAL, 12, 0, 1.0, 0, 0.3, 0.05)
        .chord(Sound.ENTITY_PHANTOM_FLAP, 0.7f, 1.3f, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.9f);

    J.runEntity(p, () -> {
      if (!p.isOnline()) {
        return;
      }
      J.teleport(p, destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
      p.setFallDistance(0f);
      p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, getConfig().slowFallTicks, 0, true, false, false));
      fx(destination, FxPriority.TRANSITION)
          .ring(Particles.END_ROD, 0.8, 12, 0.1)
          .particle(Particle.CLOUD, 8, 0, -0.1, 0, 0.25, 0.02)
          .chord(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.2f);
    });

    addStat(p, "rift.step.saves", 1);
    addStat(p, "rift.teleports", 1);
    xp(p, getConfig().xpOnSave, "rift:step:save");
  }

  private Location findSafeSurface(Player p) {
    Location origin = p.getLocation();
    int depth = Math.max(4, Math.min(HARD_MAX_SEARCH_DEPTH, getConfig().searchDepth));
    Location straight = searchDown(origin.getBlock(), depth);
    if (straight != null) {
      return straight;
    }

    double radius = Math.max(1D, Math.min(HARD_MAX_SEARCH_RADIUS, getConfig().searchRadius));
    int steps = (int) Math.ceil(radius);
    for (int ring = 1; ring <= steps; ring++) {
      for (int dx = -ring; dx <= ring; dx++) {
        for (int dz = -ring; dz <= ring; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
            continue;
          }
          Location resolved = searchDown(origin.clone().add(dx, 0, dz).getBlock(), depth);
          if (resolved != null) {
            return resolved;
          }
        }
      }
    }
    return null;
  }

  private Location searchDown(Block from, int depth) {
    for (int dy = 0; dy <= depth; dy++) {
      Block feet = from.getRelative(0, -dy, 0);
      if (feet.getY() < feet.getWorld().getMinHeight()) {
        break;
      }
      if (isStandable(feet)) {
        return feet.getLocation().add(0.5, 0, 0.5);
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

  private int getXpCost(int level) {
    int value = getConfig().xpCostBase - (Math.max(0, level - 1) * getConfig().xpCostReductionPerLevel);
    return Math.max(getConfig().minimumXpCost, value);
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

  @ConfigDescription("Hold sneak during a lethal fall to phase to the nearest safe surface, spending an ender pearl and some experience.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Experience points spent per phase at level 1.", impact = "Higher values make each save cost more experience.")
    int xpCostBase = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Experience cost reduction per adaptation level beyond the first.", impact = "Higher values make leveling cut the experience cost faster.")
    int xpCostReductionPerLevel = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Lowest possible experience cost regardless of level.", impact = "Higher values keep a floor under the experience cost.")
    int minimumXpCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Blocks searched downward for a safe landing surface.", impact = "Higher values find ground from further above, up to a hard 96-block limit.")
    int searchDepth = 48;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Horizontal search radius in blocks for a safe landing surface.", impact = "Higher values widen the search when the spot directly below is unsafe, up to a hard 6-block limit.")
    double searchRadius = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Slow falling duration in ticks applied after a phase.", impact = "Higher values keep you drifting safely longer after landing.")
    int slowFallTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Skill XP granted when a phase triggers.", impact = "Higher values grant more skill XP per save.")
    double xpOnSave = 20;

    public Config() {
      baseCost = 6;
      costFactor = 0.4;
      maxLevel = 4;
      initialCost = 5;
    }
  }
}
