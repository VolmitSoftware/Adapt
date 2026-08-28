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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class SeaborneCoralGardener extends SimpleAdaptation<SeaborneCoralGardener.Config> {
  private static final int MAX_TRACKED_CORAL = 8192;
  private static final Material[] CORAL_BLOCKS = {
      Material.TUBE_CORAL_BLOCK,
      Material.BRAIN_CORAL_BLOCK,
      Material.BUBBLE_CORAL_BLOCK,
      Material.FIRE_CORAL_BLOCK,
      Material.HORN_CORAL_BLOCK
  };
  private static final BlockFace[] GROW_FACES = {
      BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN
  };
  private static final Set<Material> EXTRA_REEF = EnumSet.of(
      Material.PRISMARINE,
      Material.PRISMARINE_BRICKS,
      Material.DARK_PRISMARINE,
      Material.SEA_LANTERN,
      Material.SPONGE,
      Material.WET_SPONGE
  );

  private final Map<String, Long> placedCoral = new ConcurrentHashMap<>();

  public SeaborneCoralGardener() {
    super("seaborne-coral-gardener");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.coral_gardener");
    setIcon(Material.BRAIN_CORAL_BLOCK);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TUBE_CORAL_BLOCK)
        .key("challenge_seaborne_coral_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.BRAIN_CORAL_BLOCK)
            .key("challenge_seaborne_coral_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_seaborne_coral_500", "seaborne.coral-gardener.coral-placed", 500, 300);
    registerMilestone("challenge_seaborne_coral_5k", "seaborne.coral-gardener.coral-placed", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.duration(getSurvivalMillis(level), 0), 1);
    statLore(v, Form.pc(getGrowthChance(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    Player p = e.getPlayer();
    Block b = e.getBlockPlaced();
    Material m = b.getType();
    boolean coral = isFadeableCoral(m);
    boolean reef = coral || EXTRA_REEF.contains(m);
    if (!reef) {
      return;
    }

    Location location = b.getLocation();
    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      xp(p, getConfig().reefPlaceXp);
      if (coral) {
        trackCoral(location, level);
        addStat(p, "seaborne.coral-gardener.coral-placed", 1);
        fx(location.clone().add(0.5D, 0.6D, 0.5D), FxPriority.AMBIENT)
            .particle(Particle.HAPPY_VILLAGER, 4, 0D, 0.2D, 0D, 0.35D, 0D)
            .sound(Sound.BLOCK_CORAL_BLOCK_PLACE, 0.5F, 1.3F);
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockFadeEvent e) {
    Block b = e.getBlock();
    if (!isFadeableCoral(b.getType())) {
      return;
    }

    String key = key(b.getLocation());
    Long expiry = placedCoral.get(key);
    if (expiry == null) {
      return;
    }

    if (System.currentTimeMillis() < expiry) {
      e.setCancelled(true);
      return;
    }

    placedCoral.remove(key);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND
        || e.getAction() != Action.RIGHT_CLICK_BLOCK
        || e.useInteractedBlock() == Event.Result.DENY) {
      return;
    }

    ItemStack item = e.getItem();
    if (item == null || item.getType() != Material.BONE_MEAL) {
      return;
    }

    Block clicked = e.getClickedBlock();
    if (clicked == null) {
      return;
    }

    Player p = e.getPlayer();
    if (!ownsCoralTarget(p, clicked) || !isFadeableCoral(clicked.getType())) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      if (ThreadLocalRandom.current().nextDouble() >= getGrowthChance(level)) {
        return;
      }

      Block target = null;
      for (BlockFace face : GROW_FACES) {
        Block relative = clicked.getRelative(face);
        if (ownsCoralTarget(p, relative) && relative.getType() == Material.WATER) {
          target = relative;
          break;
        }
      }

      if (target == null) {
        return;
      }

      Material coral = CORAL_BLOCKS[ThreadLocalRandom.current().nextInt(CORAL_BLOCKS.length)];
      if (!growCoral(p, target, coral, level)) {
        return;
      }
      fx(clicked.getLocation().clone().add(0.5D, 1.0D, 0.5D), FxPriority.GAMEPLAY)
          .particle(Particle.HAPPY_VILLAGER, 8, 0D, 0.4D, 0D, 0.5D, 0D)
          .particle(Particle.BUBBLE, 6, 0D, 0.3D, 0D, 0.4D, 0.02D)
          .chord(Sound.BLOCK_CORAL_BLOCK_PLACE, 0.6F, 1.2F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
    });
  }

  private boolean growCoral(Player p, Block target, Material coral, int level) {
    if (!ownsCoralTarget(p, target)
        || target.getType() != Material.WATER
        || !canBlockPlace(p, target.getLocation())
        || !ProtectionEventProbe.attemptBlockPlaceProbe(p, target)) {
      return false;
    }
    if (!ownsCoralTarget(p, target) || target.getType() != Material.WATER) {
      return false;
    }

    target.setType(coral, false);
    boolean costPaid = false;
    try {
      costPaid = consumeBoneMeal(p);
      if (!costPaid) {
        return false;
      }
    } finally {
      if (!costPaid && ownsCoralTarget(p, target) && target.getType() == coral) {
        target.setType(Material.WATER, false);
      }
    }

    trackCoral(target.getLocation(), level);
    fx(target.getLocation().clone().add(0.5D, 0.5D, 0.5D), FxPriority.TRANSITION)
        .ring(Particle.HAPPY_VILLAGER, 0.5D, 8, 0.2D)
        .sound(Sound.BLOCK_CORAL_BLOCK_BREAK, 0.4F, 1.8F);
    J.runEntity(p, () -> {
      if (!p.isOnline()) {
        return;
      }
      xp(p, getConfig().growthXp);
      addStat(p, "seaborne.coral-gardener.coral-placed", 1);
    });
    return true;
  }

  private boolean ownsCoralTarget(Player player, Block target) {
    return !J.isFoliaThreading()
        || (J.isOwnedByCurrentRegion(player) && J.isOwnedByCurrentRegion(target.getLocation()));
  }

  private boolean consumeBoneMeal(Player p) {
    if (p.getGameMode() == GameMode.CREATIVE) {
      return true;
    }

    ItemStack inHand = p.getInventory().getItemInMainHand();
    if (inHand.getType() != Material.BONE_MEAL) {
      return false;
    }

    return payItemCost(p, "bonemeal", new ItemStack(Material.BONE_MEAL), 1, () -> {
      inHand.setAmount(inHand.getAmount() - 1);
      return true;
    });
  }

  private void trackCoral(Location location, int level) {
    if (placedCoral.size() >= MAX_TRACKED_CORAL) {
      purgeExpired();
    }

    if (placedCoral.size() >= MAX_TRACKED_CORAL) {
      return;
    }

    placedCoral.put(key(location), System.currentTimeMillis() + getSurvivalMillis(level));
  }

  private void purgeExpired() {
    long now = System.currentTimeMillis();
    placedCoral.values().removeIf(expiry -> expiry < now);
  }

  private static String key(Location location) {
    return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
  }

  private static boolean isFadeableCoral(Material m) {
    return Tag.CORAL_BLOCKS.isTagged(m) || Tag.CORALS.isTagged(m) || Tag.WALL_CORALS.isTagged(m);
  }

  private long getSurvivalMillis(int level) {
    return survivalMillis(getConfig().survivalSecondsBase, getConfig().survivalSecondsFactor, getLevelPercent(level));
  }

  private double getGrowthChance(int level) {
    return growthChance(getConfig().growthChanceBase, getConfig().growthChanceFactor, getLevelPercent(level));
  }

  static long survivalMillis(double baseSeconds, double factorSeconds, double levelPercent) {
    return (long) (Math.max(0D, baseSeconds + (levelPercent * factorSeconds)) * 1000D);
  }

  static double growthChance(double base, double factor, double levelPercent) {
    return Math.min(1D, Math.max(0D, base + (levelPercent * factor)));
  }

  @ConfigDescription("Coral you place survives out of water far longer, bonemeal grows coral, and reef blocks grant bonus XP.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base seconds placed coral survives out of water before it may fade.", impact = "Higher values keep coral alive longer at low levels.")
    double survivalSecondsBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional survival seconds gained across levels.", impact = "Higher values make higher levels protect coral for much longer.")
    double survivalSecondsFactor = 240;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base chance bonemeal grows a new coral block from live coral.", impact = "Higher values make bonemeal growth succeed more often at low levels.")
    double growthChanceBase = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bonemeal growth chance gained across levels.", impact = "Higher values make higher levels grow coral far more reliably.")
    double growthChanceFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus XP granted when placing a reef block.", impact = "Higher values reward reef building with more skill XP.")
    double reefPlaceXp = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus XP granted when bonemeal grows new coral.", impact = "Higher values reward coral gardening with more skill XP.")
    double growthXp = 14;

    public Config() {
      baseCost = 4;
      costFactor = 0.55;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
