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

package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PickaxeMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.integration.hiddenore.HiddenOreLink;
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static art.arcane.adapt.util.data.Metadata.VEIN_MINED;

public class PickaxeVeinminer extends SimpleAdaptation<PickaxeVeinminer.Config> {
  public PickaxeVeinminer() {
    super("pickaxe-veinminer");
    registerConfiguration(PickaxeVeinminer.Config.class);
    setLocalizationKey("pickaxe.vein_miner");
    setIcon(Material.IRON_PICKAXE);
    setInterval(8484);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_PICKAXE)
        .key("challenge_pickaxe_veinminer_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_PICKAXE)
        .key("challenge_pickaxe_veinminer_20")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_pickaxe_veinminer_2500", "pickaxe.veinminer.ores-veinmined", 2500, 500);
  }

  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(PickaxeMessages.VEIN_MINER_LORE1));
    statLore(v, C.GREEN, "", level + getConfig().baseRange, 2);
    v.addLore(C.ITALIC + AdaptLanguage.text(PickaxeMessages.VEIN_MINER_LORE3));
  }

  private int getRadius(int lvl) {
    return lvl + getConfig().baseRange;
  }

  static Material veinFamily(Material material) {
    String name = material.name();
    if (name.startsWith("DEEPSLATE_") && name.endsWith("_ORE")) {
      Material base = Material.matchMaterial(name.substring("DEEPSLATE_".length()));
      return base != null ? base : material;
    }
    return material;
  }

  static boolean isVeinminable(Material material) {
    return material.name().endsWith("_ORE")
        || material == Material.OBSIDIAN
        || material == Material.ANCIENT_DEBRIS;
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    if (VEIN_MINED.get(e.getBlock())) {
      return;
    }

    Player p = e.getPlayer();
    ItemStack tool = p.getInventory().getItemInMainHand();
    if (!p.isSneaking() || !isPickaxe(tool)) {
      return;
    }

    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0) {
      return;
    }

    if (!isVeinminable(e.getBlock().getType())) {
      chainHiddenVein(e.getBlock(), p, level);
      return;
    }
    VEIN_MINED.add(e.getBlock());

    Block block = e.getBlock();
    Material targetType = block.getType();
    Material targetFamily = veinFamily(targetType);
    Set<Block> blockMap = new HashSet<>();
    Set<Block> queued = new HashSet<>();
    Deque<Block> queue = new ArrayDeque<>();
    queue.add(block);
    queued.add(block);
    int radius = getRadius(level);
    int radiusSquared = radius * radius;
    int maxBlocks = Math.max(1, getConfig().maxBlocks);
    while (!queue.isEmpty() && blockMap.size() < maxBlocks) {
      Block current = queue.poll();
      if (current == null) {
        continue;
      }

      if (veinFamily(current.getType()) != targetFamily || blockMap.contains(current)) {
        continue;
      }

      if (distanceSquared(current, block) > radiusSquared || !canBlockBreak(p, current.getLocation())) {
        continue;
      }

      blockMap.add(current);
      for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
          for (int z = -1; z <= 1; z++) {
            if (x == 0 && y == 0 && z == 0) {
              continue;
            }

            Block next = current.getRelative(x, y, z);
            if (veinFamily(next.getType()) != targetFamily) {
              continue;
            }

            if (distanceSquared(next, block) > radiusSquared) {
              continue;
            }

            if (queued.add(next)) {
              queue.add(next);
            }
          }
        }
      }
    }

    int veinSize = blockMap.size();
    addStat(p, "pickaxe.veinminer.ores-veinmined", veinSize);

    double ringRadius = Math.min(4.0D, 0.8D + (veinSize * 0.03D));
    fx(block.getLocation().add(0.5, 0.5, 0.5), FxPriority.GAMEPLAY)
        .ring(Particle.ELECTRIC_SPARK, ringRadius, Math.min(28, 8 + veinSize), 0.2D)
        .chord(Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.7f, 0.9f, Sound.BLOCK_DEEPSLATE_BREAK, 0.5f, 0.6f);

    if (veinSize >= 20) {
      fx(p, FxPriority.TRANSITION)
          .dustHelix(0.6D, 2.0D, 14, 0, 1.0F)
          .sound(Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 2.0f);
      if (AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_pickaxe_veinminer_20")) {
        getPlayer(p).getAdvancementHandler().grant("challenge_pickaxe_veinminer_20");
      }
    }

    BlockData veinData = targetType.createBlockData();
    int autoSmeltLevel = getActiveSiblingBlockBreakLevel(p, "pickaxe-autosmelt", block.getLocation());
    boolean autoSmeltEnabled = autoSmeltLevel > 0
        && !tool.getEnchantments().containsKey(Enchantment.SILK_TOUCH);
    boolean dropToInventory = getActiveSiblingBlockBreakLevel(
        p,
        "pickaxe-drop-to-inventory",
        block.getLocation()
    ) > 0;
    J.runEntity(p, () -> {
      int index = 0;
      for (Block b : blockMap) {
        if (!canBlockBreak(p, b.getLocation())) {
          Adapt.verbose("Player " + p.getName() + " doesn't have permission.");
          continue;
        }
        VEIN_MINED.add(b);
        if (autoSmeltEnabled && ItemListings.getSmeltOre().contains(b.getType()) && b.isPreferredTool(tool)) {
          if (dropToInventory) {
            PickaxeAutosmelt.autosmeltBlockDTI(b, p, this, autoSmeltLevel);
          } else {
            PickaxeAutosmelt.autosmeltBlock(b, p, this, autoSmeltLevel);
          }
        } else {
          if (dropToInventory) {
            b.getDrops(p.getInventory().getItemInMainHand(), p).forEach(item -> {
              HashMap<Integer, ItemStack> extra = p.getInventory().addItem(item);
              extra.forEach((k, v) -> p.getWorld().dropItem(p.getLocation(), v));
            });
            b.setType(Material.AIR);
          } else {
            b.breakNaturally(tool);
            FxEmitter crumble = fx(b.getLocation().add(0.5, 0.5, 0.5), FxPriority.TRAIL)
                .particle(Particles.BLOCK_CRACK, 3, 0, 0, 0, 0.25, 0.0, veinData);
            if ((index & 3) == 0) {
              crumble.sound(Sound.BLOCK_STONE_BREAK, 0.5f, (float) (0.8D + Math.min(0.5D, index * 0.02D)));
            }
          }
        }
        VEIN_MINED.remove(b);
        index++;
      }
      VEIN_MINED.remove(block);
    });
  }

  private int distanceSquared(Block first, Block second) {
    int dx = first.getX() - second.getX();
    int dy = first.getY() - second.getY();
    int dz = first.getZ() - second.getZ();
    return (dx * dx) + (dy * dy) + (dz * dz);
  }

  private void chainHiddenVein(Block block, Player p, int level) {
    List<Block> siblings = HiddenOreLink.veinSiblings(block);
    if (siblings.isEmpty()) {
      return;
    }

    int radius = getRadius(level);
    int radiusSquared = radius * radius;
    Location origin = block.getLocation();
    List<Block> targets = new ArrayList<>();
    for (Block sibling : siblings) {
      if (sibling.getLocation().distanceSquared(origin) <= radiusSquared && canBlockBreak(p, sibling.getLocation())) {
        targets.add(sibling);
      }
    }

    if (targets.isEmpty()) {
      return;
    }

    addStat(p, "pickaxe.veinminer.ores-veinmined", targets.size());
    fx(block.getLocation().add(0.5, 0.5, 0.5), FxPriority.GAMEPLAY)
        .particle(Particle.WAX_ON, Math.min(12, targets.size() * 2), 0, 0.3, 0, 0.4, 0.02)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.5f, 1.4f);
    J.runEntity(p, () -> {
      for (Block target : targets) {
        if (VEIN_MINED.get(target)) {
          continue;
        }
        VEIN_MINED.add(target);
        p.breakBlock(target);
        VEIN_MINED.remove(target);
      }
    });
  }



  @ConfigDescription("Break connected ore veins at once while sneaking.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Range for the Pickaxe Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseRange = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum blocks mined by one Veinminer activation.", impact = "Higher values allow larger artificial veins but increase the worst-case block update cost.")
    int maxBlocks = 64;

    public Config() {
      baseCost = 6;
      costFactor = 0.95;
      initialCost = 4;
    }
  }
}
