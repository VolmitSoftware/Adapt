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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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
    setDescription(Localizer.dLocalize("pickaxe.tunnel_bore.description"));
    setDisplayName(Localizer.dLocalize("pickaxe.tunnel_bore.name"));
    setIcon(Material.COBBLESTONE);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(8123);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_PICKAXE)
        .key("challenge_pickaxe_tunnelbore_10k")
        .title(Localizer.dLocalize("advancement.challenge_pickaxe_tunnelbore_10k.title"))
        .description(Localizer.dLocalize("advancement.challenge_pickaxe_tunnelbore_10k.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_pickaxe_tunnelbore_10k", "pickaxe.tunnel-bore.blocks-bored", 10000, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("pickaxe.tunnel_bore.lore1"));
    v.addLore(C.GREEN + "" + getBoreWidth(level) + "x" + getBoreHeight(level) + C.GRAY + " " + Localizer.dLocalize("pickaxe.tunnel_bore.lore2"));
    v.addLore(C.RED + "- " + getConfig().durabilityPerBonusBlock + C.GRAY + " " + Localizer.dLocalize("pickaxe.tunnel_bore.lore3"));
  }

  private int getBoreWidth(int level) {
    return level >= 2 ? 3 : 1;
  }

  private int getBoreHeight(int level) {
    return level >= 3 ? 3 : 2;
  }

  @EventHandler
  public void on(BlockBreakEvent e) {
    Block block = e.getBlock();
    if (!BORE_BLOCKS.contains(block.getType())) {
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

    List<Block> targets = collectPlane(p, block, getBoreWidth(context.level()), getBoreHeight(context.level()));
    if (targets.isEmpty()) {
      return;
    }

    ItemStack tool = p.getInventory().getItemInMainHand().clone();
    damageHand(p, targets.size() * getConfig().durabilityPerBonusBlock);
    getPlayer(p).getData().addStat("pickaxe.tunnel-bore.blocks-bored", targets.size());
    J.runEntity(p, () -> {
      for (Block b : targets) {
        if (!BORE_BLOCKS.contains(b.getType())) {
          continue;
        }

        b.breakNaturally(tool);
      }
    });
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

        if (!BORE_BLOCKS.contains(b.getType())) {
          continue;
        }

        if (!canBlockBreak(p, b.getLocation())) {
          continue;
        }

        targets.add(b);
      }
    }

    return targets;
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public void onTick() {
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Sneak-mine stone-type blocks to bore a tunnel plane oriented by your facing.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra pickaxe durability damage taken per bonus block bored.", impact = "Higher values wear the pickaxe out faster while tunnel boring.")
    int durabilityPerBonusBlock = 1;
  }
}
