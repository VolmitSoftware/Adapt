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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AxeMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.integration.iris.IrisTreeFellerLink;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static art.arcane.adapt.util.data.Metadata.VEIN_MINED;

public class AxeLeafVeinminer extends SimpleAdaptation<AxeLeafVeinminer.Config> {
  public AxeLeafVeinminer() {
    super("axe-leaf-veinminer");
    registerConfiguration(AxeLeafVeinminer.Config.class);
    setLocalizationKey("axe.leaf_miner");
    setIcon(Material.BIRCH_LEAVES);
    setInterval(5849);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.OAK_LEAVES)
        .key("challenge_axe_leaf_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_axe_leaf_5k", "axe.leaf-veinminer.leaves-broken", 5000, 400);
  }

  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(AxeMessages.LEAF_MINER_LORE1));
    statLore(v, C.GREEN, "", level + getConfig().baseRange, 2);
    v.addLore(C.ITALIC + AdaptLanguage.text(AxeMessages.LEAF_MINER_LORE3));
  }

  private int getRadius(int lvl) {
    return lvl + getConfig().baseRange;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {

    if (VEIN_MINED.get(e.getBlock()) || IrisTreeFellerLink.isManagedBreak(e)) {
      return;
    }

    Player p = e.getPlayer();
    ItemStack tool = p.getInventory().getItemInMainHand();
    if (!p.isSneaking() || !isAxe(tool)) {
      return;
    }

    Material blockType = e.getBlock().getType();
    if (!isLeavesMaterial(blockType) || !hasActiveAdaptation(p)) {
      return;
    }

    if (!canBlockBreak(p, e.getBlock().getLocation())) {
      return;
    }

    VEIN_MINED.add(e.getBlock());

    Block block = e.getBlock();
    BlockData leafData = block.getBlockData();
    fx(p.getEyeLocation(), FxPriority.TRAIL)
        .line(Particles.VILLAGER_HAPPY, block.getX() + 0.5D, block.getY() + 0.5D, block.getZ() + 0.5D, 5)
        .sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5F, 1.2F);
    Set<Block> blockMap = new HashSet<>();
    Deque<Block> stack = new ArrayDeque<>();
    Set<Block> queued = new HashSet<>();
    stack.push(block);
    queued.add(block);
    int radius = getRadius(getLevel(p));
    int radiusSquared = radius * radius;
    int maxBlocks = Math.max(1, getConfig().maxBlocks);
    while (!stack.isEmpty() && blockMap.size() < maxBlocks) {
      Block currentBlock = stack.pop();
      if (blockMap.contains(currentBlock)) {
        continue;
      }
      blockMap.add(currentBlock);
      for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
          for (int z = -1; z <= 1; z++) {
            if (x == 0 && y == 0 && z == 0) {
              continue;
            }
            Block b = currentBlock.getRelative(x, y, z);
            if (b.getType() != block.getType()
                || blockMap.contains(b)
                || !queued.add(b)) {
              continue;
            }
            if (distanceSquared(block, b) <= radiusSquared && canBlockBreak(p, b.getLocation())) {
              stack.push(b);
            } else {
              queued.remove(b);
            }
          }
        }
      }
    }

    int leavesBroken = blockMap.size();
    J.runEntity(p, () -> {
      boolean toInventory = getActiveSiblingBlockBreakLevel(
          p,
          "axe-drop-to-inventory",
          block.getLocation()
      ) > 0;
      for (Block b : blockMap) {
        VEIN_MINED.add(b);
        if (toInventory) {
          Collection<ItemStack> items = b.getDrops(tool, p);
          for (ItemStack i : items) {
            HashMap<Integer, ItemStack> extra = p.getInventory().addItem(i);
            extra.values().forEach(item -> p.getWorld().dropItem(p.getLocation(), item));
          }
          b.setType(Material.AIR);
        } else {
          b.breakNaturally(tool);
        }
        VEIN_MINED.remove(b);
      }
      VEIN_MINED.remove(block);
    });
    if (leavesBroken > 0) {
      addStat(p, "axe.leaf-veinminer.leaves-broken", leavesBroken);
      double sumX = 0.0D;
      double sumY = 0.0D;
      double sumZ = 0.0D;
      for (Block selected : blockMap) {
        sumX += selected.getX();
        sumY += selected.getY();
        sumZ += selected.getZ();
      }
      Location center = new Location(block.getWorld(), (sumX / leavesBroken) + 0.5D, (sumY / leavesBroken) + 0.5D, (sumZ / leavesBroken) + 0.5D);
      double extent = Math.min(3.0D, 1.0D + (radius * 0.35D));
      timeline(center)
          .duration(8)
          .priority(FxPriority.TRANSITION)
          .cullRadius(24)
          .frame((fx, tick, progress) -> {
            fx.particle(Particles.BLOCK_CRACK, 3, 0.0D, extent * 0.4D * (1.0D - progress), 0.0D, extent, 0.02D, leafData);
            if (tick == 0) {
              fx.chord(Sound.BLOCK_AZALEA_LEAVES_BREAK, 0.7F, 1.0F, Sound.BLOCK_GRASS_BREAK, 0.4F, 1.3F);
            }
            if ((tick & 1) == 0) {
              fx.particle(Particles.VILLAGER_HAPPY, 1, 0.0D, 0.0D, 0.0D, extent, 0.0D);
            }
          })
          .start();
      if (leavesBroken >= 40) {
        fx(center, FxPriority.AMBIENT)
            .burst(Particles.VILLAGER_HAPPY, 6, 0.4D)
            .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.5F);
      }
    }
  }



  private int distanceSquared(Block first, Block second) {
    int dx = first.getX() - second.getX();
    int dy = first.getY() - second.getY();
    int dz = first.getZ() - second.getZ();
    return (dx * dx) + (dy * dy) + (dz * dz);
  }

  private boolean isLeavesMaterial(Material type) {
    return type == Material.MANGROVE_ROOTS
        || type == Material.MUDDY_MANGROVE_ROOTS
        || type.name().endsWith("_LEAVES");
  }

  @ConfigDescription("Break bulk leaves at once while sneaking.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Range for the Axe Leaf Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseRange = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blocks for the Axe Leaf Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxBlocks = 128;

    public Config() {
      baseCost = 6;
      costFactor = 0.325;
      initialCost = 1;
    }
  }
}
