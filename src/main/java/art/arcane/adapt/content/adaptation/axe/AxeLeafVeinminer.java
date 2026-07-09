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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
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

import java.util.*;

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
    v.addLore(C.GREEN + Localizer.dLocalize("axe.leaf_miner.lore1"));
    v.addLore(C.GREEN + "" + (level + getConfig().baseRange) + C.GRAY + " " + Localizer.dLocalize("axe.leaf_miner.lore2"));
    v.addLore(C.ITALIC + Localizer.dLocalize("axe.leaf_miner.lore3"));
  }

  private int getRadius(int lvl) {
    return lvl + getConfig().baseRange;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {

    if (VEIN_MINED.get(e.getBlock())) {
      return;
    }

    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    if (!p.isSneaking()) {
      return;
    }

    if (!isAxe(p.getInventory().getItemInMainHand())) {
      return;
    }

    Material blockType = e.getBlock().getType();
    if (!blockType.isItem() || !isLeaves(new ItemStack(blockType))) {
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
    Map<Location, Block> blockMap = new HashMap<>();
    Deque<Block> stack = new ArrayDeque<>();
    Set<Location> queued = new HashSet<>();
    stack.push(block);
    queued.add(block.getLocation());
    int radius = getRadius(getLevel(p));
    int radiusSquared = radius * radius;
    while (!stack.isEmpty() && blockMap.size() < radius) {
      Block currentBlock = stack.pop();
      Location currentLocation = currentBlock.getLocation();
      if (blockMap.containsKey(currentLocation)) {
        continue;
      }
      blockMap.put(currentLocation, currentBlock);
      for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
          for (int z = -1; z <= 1; z++) {
            if (x == 0 && y == 0 && z == 0) {
              continue;
            }
            Block b = currentBlock.getRelative(x, y, z);
            Location nextLocation = b.getLocation();
            if (b.getType() != block.getType()
                || blockMap.containsKey(nextLocation)
                || !queued.add(nextLocation)) {
              continue;
            }
            if (currentBlock.getLocation().distanceSquared(b.getLocation()) <= radiusSquared && canBlockBreak(p, b.getLocation())) {
              stack.push(b);
            } else {
              queued.remove(nextLocation);
            }
          }
        }
      }
    }

    int leavesBroken = blockMap.size();
    PlayerSkillLine line = getPlayer(p).getData().getSkillLineNullable("axes");
    PlayerAdaptation adaptation = line != null ? line.getAdaptation("axe-drop-to-inventory") : null;
    boolean toInventory = adaptation != null && adaptation.getLevel() > 0;
    J.runEntity(p, () -> {
      for (Location l : blockMap.keySet()) {
        Block b = block.getWorld().getBlockAt(l);
        VEIN_MINED.add(b);
        if (toInventory) {
          Collection<ItemStack> items = b.getDrops(p.getInventory().getItemInMainHand(), p);
          for (ItemStack i : items) {
            HashMap<Integer, ItemStack> extra = p.getInventory().addItem(i);
            if (!extra.isEmpty()) {
              p.getWorld().dropItem(p.getLocation(), extra.get(0));
            }
          }
          b.setType(Material.AIR);
        } else {
          b.breakNaturally(p.getItemInUse());
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
      for (Location l : blockMap.keySet()) {
        sumX += l.getX();
        sumY += l.getY();
        sumZ += l.getZ();
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


  @Override
  public void onTick() {
  }

  @ConfigDescription("Break bulk leaves at once while sneaking.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Range for the Axe Leaf Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseRange = 5;

    public Config() {
      baseCost = 6;
      costFactor = 0.325;
      initialCost = 1;
    }
  }
}
