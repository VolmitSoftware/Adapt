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
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static art.arcane.adapt.util.data.Metadata.VEIN_MINED;

public class AxeWoodVeinminer extends SimpleAdaptation<AxeWoodVeinminer.Config> {
  public AxeWoodVeinminer() {
    super("axe-wood-veinminer");
    registerConfiguration(AxeWoodVeinminer.Config.class);
    setLocalizationKey("axe.wood_miner");
    setIcon(Material.DIAMOND_AXE);
    setInterval(5849);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.OAK_LOG)
        .key("challenge_axe_wood_vein_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_AXE)
        .key("challenge_axe_wood_vein_cascade")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_axe_wood_vein_2500", "axe.wood-veinminer.logs-veinmined", 2500, 500);
  }

  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(AxeMessages.WOOD_MINER_LORE1));
    statLore(v, C.GREEN, "", level + getConfig().baseRange, 2);
    v.addLore(C.ITALIC + AdaptLanguage.text(AxeMessages.WOOD_MINER_LORE3));
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
    if (!p.isSneaking() || !isAxe(tool) || !isLogMaterial(e.getBlock().getType())) {
      return;
    }

    if (!hasActiveAdaptation(p)) {
      return;
    }

    VEIN_MINED.add(e.getBlock());
    Block block = e.getBlock();
    Material blockType = block.getType();
    BlockData logData = block.getBlockData();
    int maxBlocks = Math.max(1, getConfig().maxBlocks);
    Set<Block> blockMap = new HashSet<>(maxBlocks);
    int radius = getRadius(getLevel(p));
    int radiusSquared = radius * radius;
    scan:
    for (int i = 0; i < radius; i++) {
      for (int x = -i; x <= i; x++) {
        for (int y = -i; y <= i; y++) {
          for (int z = -i; z <= i; z++) {
            if (blockMap.size() >= maxBlocks) {
              break scan;
            }
            Block b = block.getRelative(x, y, z);
            if ((!J.isFoliaThreading() || J.isOwnedByCurrentRegion(b.getLocation()))
                && b.getType() == blockType) {
              if ((x * x) + (y * y) + (z * z) > radiusSquared) {
                continue;
              }
              if (!canBlockBreak(p, b.getLocation())) {
                continue;
              }
              blockMap.add(b);
            }
          }
        }
      }
    }

    Set<Block> siblings = new HashSet<>(blockMap);
    siblings.remove(block);
    boolean scheduled = J.runEntity(
        p,
        () -> mineWood(p, block, blockType, logData, siblings),
        1
    );
    if (!scheduled) {
      VEIN_MINED.remove(block);
    }
  }

  private void mineWood(Player player, Block origin, Material blockType, BlockData logData,
                        Set<Block> targets) {
    Location originLocation = origin.getLocation();
    if (!player.isOnline()
        || player.getWorld() != origin.getWorld()
        || (J.isFoliaThreading()
        && (!J.isOwnedByCurrentRegion(player) || !J.isOwnedByCurrentRegion(originLocation)))
        || origin.getType() == blockType) {
      VEIN_MINED.remove(origin);
      return;
    }

    ItemStack tool = player.getInventory().getItemInMainHand();
    if (!isAxe(tool)) {
      VEIN_MINED.remove(origin);
      return;
    }

    timeline(originLocation.add(0.5D, 0.5D, 0.5D))
        .duration(3)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.CRIT_MAGIC, 0.7D - (0.6D * progress), 8, 0.0D);
          if (tick == 0) {
            fx.sound(Sound.ITEM_AXE_STRIP, 1.0F, 0.7F);
          }
        })
        .start();
    List<Block> brokenBlocks = new ArrayList<>(targets.size() + 1);
    brokenBlocks.add(origin);
    try {
      for (Block target : targets) {
        Location location = target.getLocation();
        if ((J.isFoliaThreading()
            && (!J.isOwnedByCurrentRegion(player) || !J.isOwnedByCurrentRegion(location)))
            || target.getType() != blockType
            || !canBlockBreak(player, location)) {
          continue;
        }

        VEIN_MINED.add(target);
        try {
          if (ProtectionEventProbe.attemptBlockBreak(player, target)) {
            brokenBlocks.add(target);
          }
        } finally {
          VEIN_MINED.remove(target);
        }
      }
    } finally {
      VEIN_MINED.remove(origin);
    }

    emitWoodFeedback(player, origin, logData, brokenBlocks);
  }

  private void emitWoodFeedback(Player player, Block origin, BlockData logData,
                                List<Block> brokenBlocks) {
    int logsVeinmined = brokenBlocks.size();
    if (logsVeinmined > 0) {
      addStat(player, "axe.wood-veinminer.logs-veinmined", logsVeinmined);
      int minY = Integer.MAX_VALUE;
      int maxY = Integer.MIN_VALUE;
      double sumX = 0.0D;
      double sumZ = 0.0D;
      for (Block b : brokenBlocks) {
        minY = Math.min(minY, b.getY());
        maxY = Math.max(maxY, b.getY());
        sumX += b.getX();
        sumZ += b.getZ();
      }
      double climb = maxY - minY;
      int steps = Math.min(10, Math.max(3, logsVeinmined));
      int lastTick = steps - 1;
      Location cascade = new Location(origin.getWorld(), (sumX / logsVeinmined) + 0.5D, minY + 0.5D, (sumZ / logsVeinmined) + 0.5D);
      timeline(cascade)
          .duration(steps)
          .priority(FxPriority.TRANSITION)
          .cullRadius(28)
          .frame((fx, tick, progress) -> {
            fx.particle(Particles.BLOCK_CRACK, 4, 0.0D, climb * progress, 0.0D, 0.2D, 0.02D, logData);
            if ((tick & 1) == 0) {
              fx.sound(Sound.BLOCK_WOOD_BREAK, 0.6F, (float) (0.8D + (0.5D * progress)));
            }
            if (tick == lastTick) {
              fx.sound(Sound.ENTITY_RAVAGER_STUNNED, 0.3F, 0.5F);
            }
          })
          .start();
      if (logsVeinmined >= 15 && grantOnce(player, "challenge_axe_wood_vein_cascade")) {
        fx(player.getLocation().add(0.0D, 1.0D, 0.0D), FxPriority.TRANSITION)
            .column(Particles.TOTEM, 16, 2.0D)
            .chord(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.1F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.5F);
      }
    }
  }



  private boolean isLogMaterial(Material type) {
    String name = type.name();
    return type == Material.MUSHROOM_STEM
        || type == Material.BROWN_MUSHROOM_BLOCK
        || type == Material.RED_MUSHROOM_BLOCK
        || type == Material.MANGROVE_ROOTS
        || type == Material.MUDDY_MANGROVE_ROOTS
        || name.endsWith("_LOG")
        || name.endsWith("_WOOD");
  }

  @ConfigDescription("Break bulk wood at once while sneaking.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blocks for the Axe Wood Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxBlocks = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Range for the Axe Wood Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseRange = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.95;
      initialCost = 4;
    }
  }
}
