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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
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
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class PickaxeTunnelBore extends SimpleAdaptation<PickaxeTunnelBore.Config> {
  private static final Set<Material> BORE_BLOCKS = EnumSet.of(
      Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
      Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF,
      Material.CALCITE, Material.ANDESITE, Material.DIORITE, Material.GRANITE);
  private static final int[] SINGLE = {0};
  private static final int[] PAIR = {0, 1};
  private static final int[] TRIPLE = {-1, 0, 1};

  public PickaxeTunnelBore() {
    super("pickaxe-tunnel-bore");
    registerConfiguration(PickaxeTunnelBore.Config.class);
    setIcon(Material.COBBLESTONE);
    setInterval(8123);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_PICKAXE)
        .key("challenge_pickaxe_tunnelbore_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_tunnelbore_10k", "pickaxe.tunnel-bore.blocks-bored", 10000, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(PickaxeMessages.TUNNEL_BORE_LORE1));
    statLore(v, C.GREEN, "", getBoreWidth(level) + "x" + getBoreHeight(level), 2);
    statLore(v, C.RED, "- ", getConfig().durabilityPerBonusBlock, 3);
  }

  private int getBoreWidth(int level) {
    return level >= 2 ? 3 : 1;
  }

  private int getBoreHeight(int level) {
    return level >= 3 ? 3 : 2;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Block block = e.getBlock();
    Material originType = block.getType();
    if (!BORE_BLOCKS.contains(originType)) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking() || !isPickaxe(p.getInventory().getItemInMainHand())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, block.getLocation());
    if (context == null) {
      return;
    }

    int boreWidth = getBoreWidth(context.level());
    int boreHeight = getBoreHeight(context.level());
    List<Block> targets = collectPlane(p, block, boreWidth, boreHeight);
    if (targets.isEmpty()) {
      return;
    }

    J.runEntity(
        p,
        () -> breakBorePlane(p, block, originType, boreWidth, boreHeight, targets),
        1
    );
  }

  private void breakBorePlane(Player player, Block origin, Material originType, int boreWidth,
                              int boreHeight, List<Block> targets) {
    Location originLocation = origin.getLocation();
    if (!player.isOnline()
        || player.getWorld() != origin.getWorld()
        || (J.isFoliaThreading()
        && (!J.isOwnedByCurrentRegion(player) || !J.isOwnedByCurrentRegion(originLocation)))
        || origin.getType() == originType) {
      return;
    }

    ItemStack tool = player.getInventory().getItemInMainHand();
    if (!isPickaxe(tool)) {
      return;
    }

    Location center = originLocation.add(0.5, 0.5, 0.5);
    fx(center, FxPriority.GAMEPLAY)
        .ring(Particle.CRIT, 0.9D, 12, 0.1D)
        .sound(Sound.BLOCK_STONE_BREAK, 0.5f, 0.7f);
    if (boreWidth * boreHeight >= 9) {
      fx(center, FxPriority.TRANSITION)
          .particle(Particle.CLOUD, 12, 0, 0.3, 0, 0.5, 0.02)
          .chord(Sound.BLOCK_GRAVEL_BREAK, 0.6f, 0.5f, Sound.ENTITY_RAVAGER_STEP, 0.4f, 0.6f);
    }

    int index = 0;
    int broken = 0;
    for (Block target : targets) {
      Location location = target.getLocation();
      if ((J.isFoliaThreading()
          && (!J.isOwnedByCurrentRegion(player) || !J.isOwnedByCurrentRegion(location)))
          || !BORE_BLOCKS.contains(target.getType())
          || !canBlockBreak(player, location)
          || !ProtectionEventProbe.attemptBlockBreakProbe(player, target)) {
        index++;
        continue;
      }

      if ((index & 1) == 0) {
        BlockData blockData = target.getBlockData();
        fx(location.add(0.5, 0.5, 0.5), FxPriority.TRAIL)
            .particle(Particles.BLOCK_CRACK, 3, 0, 0, 0, 0.2, 0.0, blockData);
      }
      if (target.breakNaturally(tool)) {
        broken++;
      }
      index++;
    }

    if (broken > 0) {
      damageHand(player, broken * getConfig().durabilityPerBonusBlock);
      addStat(player, "pickaxe.tunnel-bore.blocks-bored", broken);
    }
  }

  private List<Block> collectPlane(Player p, Block origin, int width, int height) {
    List<Block> targets = new ArrayList<>(8);
    int[] wOffsets = width == 1 ? SINGLE : TRIPLE;
    int[] hOffsets = height == 2 ? PAIR : TRIPLE;
    BlockFace facing = p.getFacing();
    float pitch = p.getLocation().getPitch();
    boolean steep = pitch > 60F || pitch < -60F;
    int fx = facing.getModX();
    int fz = facing.getModZ();
    boolean widthOnX = fz != 0;
    for (int h : hOffsets) {
      for (int w : wOffsets) {
        if (h == 0 && w == 0) {
          continue;
        }

        Block b;
        if (steep) {
          b = origin.getRelative((h * fx) - (w * fz), 0, (h * fz) + (w * fx));
        } else {
          b = origin.getRelative(widthOnX ? w : 0, h, widthOnX ? 0 : w);
        }

        Location location = b.getLocation();
        if ((J.isFoliaThreading() && !J.isOwnedByCurrentRegion(location))
            || !BORE_BLOCKS.contains(b.getType())) {
          continue;
        }

        if (!canBlockBreak(p, location)) {
          continue;
        }

        targets.add(b);
      }
    }

    return targets;
  }


  @ConfigDescription("Sneak-mine stone-type blocks to bore a tunnel plane oriented by your facing.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra pickaxe durability damage taken per bonus block bored.", impact = "Higher values wear the pickaxe out faster while tunnel boring.")
    int durabilityPerBonusBlock = 1;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 3;
      initialCost = 4;
    }
  }
}
