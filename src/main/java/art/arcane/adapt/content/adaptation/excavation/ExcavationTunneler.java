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

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.List;

public class ExcavationTunneler extends SimpleAdaptation<ExcavationTunneler.Config> {
  private static final int[][] PLANE_OFFSETS = {
      {0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
  };

  public ExcavationTunneler() {
    super("excavation-tunneler");
    registerConfiguration(Config.class);
    setIcon(Material.IRON_SHOVEL);
    setInterval(3170);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SHOVEL)
        .key("challenge_excavation_tunneler_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_tunneler_10k", "excavation.tunneler.blocks-tunneled", 10000, 600);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getBonusBlocks(level), 1);
    statLore(v, C.YELLOW, "* ", getConfig().durabilityCostPerBonusBlock, 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isShovel(hand)) {
      return;
    }

    if (!isShovelable(e.getBlock().getType())) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();
    int bonus = getBonusBlocks(level);
    float pitch = p.getLocation().getPitch();
    float yaw = p.getLocation().getYaw();
    boolean horizontal = pitch >= 50 || pitch <= -50;
    boolean facingX = !horizontal && isFacingX(yaw);

    Block origin = e.getBlock();
    Location originCenter = origin.getLocation().add(0.5, 0.5, 0.5);
    List<int[]> sweepCells = new ArrayList<>(PLANE_OFFSETS.length);
    List<BlockData> sweepData = new ArrayList<>(PLANE_OFFSETS.length);
    int broken = 0;
    boolean durabilityStop = false;
    for (int[] offset : PLANE_OFFSETS) {
      if (broken >= bonus) {
        break;
      }

      int dx;
      int dy;
      int dz;
      if (horizontal) {
        dx = offset[0];
        dy = 0;
        dz = offset[1];
      } else if (facingX) {
        dx = 0;
        dy = offset[1];
        dz = offset[0];
      } else {
        dx = offset[0];
        dy = offset[1];
        dz = 0;
      }

      Block target = origin.getRelative(dx, dy, dz);
      if (!isShovelable(target.getType())) {
        continue;
      }

      if (!canBlockBreak(p, target.getLocation())) {
        continue;
      }

      if (!applyDurability(p, hand, getConfig().durabilityCostPerBonusBlock)) {
        durabilityStop = true;
        break;
      }

      sweepCells.add(new int[]{dx, dy, dz});
      sweepData.add(target.getBlockData());
      target.breakNaturally(hand);
      broken++;
    }

    if (durabilityStop) {
      fx(originCenter, FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0.2D, 0, 0.1D, 0.01D)
          .sound(Sound.ITEM_SHIELD_BLOCK, 0.3f, 1.3f);
    }

    if (broken <= 0) {
      return;
    }

    planeSweep(originCenter, sweepCells, sweepData, broken);
    addStat(p, "excavation.tunneler.blocks-tunneled", broken);
    xp(p, broken * getConfig().xpPerBonusBlock);
  }

  private void planeSweep(Location origin, List<int[]> cells, List<BlockData> data, int broken) {
    int count = cells.size();
    timeline(origin)
        .duration(Math.max(4, count))
        .priority(FxPriority.TRAIL)
        .cullRadius(20)
        .frame((fx, tick, progress) -> {
          if (tick < count) {
            int[] c = cells.get(tick);
            fx.particle(Particles.BLOCK_CRACK, 3, c[0], c[1], c[2], 0.15D, 0.05D, data.get(tick));
          }
          if (tick == 0) {
            fx.chord(Sound.ITEM_SHOVEL_FLATTEN, 0.7f, 0.8f, Sound.BLOCK_GRAVEL_BREAK, Math.min(0.7f, 0.2f + (broken * 0.06f)), 0.7f);
          }
        })
        .start();
  }

  private boolean isFacingX(float yaw) {
    float normalized = ((yaw % 360) + 360) % 360;
    return (normalized >= 45 && normalized < 135) || (normalized >= 225 && normalized < 315);
  }

  private boolean applyDurability(Player p, ItemStack hand, int cost) {
    if (cost <= 0) {
      return true;
    }

    if (!(hand.getItemMeta() instanceof Damageable damageable)) {
      return false;
    }

    int maxDurability = hand.getType().getMaxDurability();
    int currentDamage = damageable.getDamage();
    if (currentDamage + cost >= maxDurability) {
      return false;
    }

    damageable.setDamage(currentDamage + cost);
    hand.setItemMeta(damageable);
    p.getInventory().setItemInMainHand(hand);
    return true;
  }

  private boolean isShovelable(Material type) {
    return switch (type) {
      case CLAY, DIRT, COARSE_DIRT, ROOTED_DIRT, FARMLAND, GRASS_BLOCK,
           DIRT_PATH, GRAVEL, MYCELIUM, PODZOL, SAND, RED_SAND, SOUL_SAND,
           SOUL_SOIL, SNOW, SNOW_BLOCK, MUD, MUDDY_MANGROVE_ROOTS -> true;
      default -> false;
    };
  }

  private int getBonusBlocks(int level) {
    return Math.max(1, Math.min(8, (int) Math.floor(getLevelPercent(level) * getConfig().bonusBlocksMax)));
  }


  @ConfigDescription("Sneak while digging soft blocks to carve a facing-oriented plane of blocks at once.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Blocks Max for the Excavation Tunneler adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusBlocksMax = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Per Bonus Block for the Excavation Tunneler adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int durabilityCostPerBonusBlock = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Bonus Block for the Excavation Tunneler adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBonusBlock = 1.5;

    public Config() {
      baseCost = 5;
      costFactor = 0.75;
      initialCost = 5;
    }
  }
}
