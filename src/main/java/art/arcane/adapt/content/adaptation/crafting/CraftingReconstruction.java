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

package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;


public class CraftingReconstruction extends SimpleAdaptation<CraftingReconstruction.Config> {
  public CraftingReconstruction() {
    super("crafting-reconstruction");
    registerConfiguration(Config.class);
    setIcon(Material.COAL_ORE);
    setInterval(80248);
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-iron-ore")
        .ingredient(Material.STONE)
        .ingredient(Material.IRON_INGOT)
        .ingredient(Material.IRON_INGOT)
        .ingredient(Material.IRON_INGOT)
        .ingredient(Material.IRON_INGOT)
        .ingredient(Material.IRON_INGOT)
        .ingredient(Material.IRON_INGOT)
        .ingredient(Material.IRON_INGOT)
        .ingredient(Material.IRON_INGOT)
        .result(new ItemStack(Material.IRON_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-gold-ore")
        .ingredient(Material.STONE)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .result(new ItemStack(Material.GOLD_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-copper-ore")
        .ingredient(Material.STONE)
        .ingredient(Material.COPPER_INGOT)
        .ingredient(Material.COPPER_INGOT)
        .ingredient(Material.COPPER_INGOT)
        .ingredient(Material.COPPER_INGOT)
        .ingredient(Material.COPPER_INGOT)
        .ingredient(Material.COPPER_INGOT)
        .ingredient(Material.COPPER_INGOT)
        .ingredient(Material.COPPER_INGOT)
        .result(new ItemStack(Material.COPPER_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-lapis-ore")
        .ingredient(Material.STONE)
        .ingredient(Material.LAPIS_LAZULI)
        .ingredient(Material.LAPIS_LAZULI)
        .ingredient(Material.LAPIS_LAZULI)
        .ingredient(Material.LAPIS_LAZULI)
        .ingredient(Material.LAPIS_LAZULI)
        .ingredient(Material.LAPIS_LAZULI)
        .ingredient(Material.LAPIS_LAZULI)
        .ingredient(Material.LAPIS_LAZULI)
        .result(new ItemStack(Material.LAPIS_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-redstone-ore")
        .ingredient(Material.STONE)
        .ingredient(Material.REDSTONE)
        .ingredient(Material.REDSTONE)
        .ingredient(Material.REDSTONE)
        .ingredient(Material.REDSTONE)
        .ingredient(Material.REDSTONE)
        .ingredient(Material.REDSTONE)
        .ingredient(Material.REDSTONE)
        .ingredient(Material.REDSTONE)
        .result(new ItemStack(Material.REDSTONE_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-emerald-ore")
        .ingredient(Material.STONE)
        .ingredient(Material.EMERALD)
        .ingredient(Material.EMERALD)
        .ingredient(Material.EMERALD)
        .ingredient(Material.EMERALD)
        .ingredient(Material.EMERALD)
        .ingredient(Material.EMERALD)
        .ingredient(Material.EMERALD)
        .ingredient(Material.EMERALD)
        .result(new ItemStack(Material.EMERALD_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-diamond-ore")
        .ingredient(Material.STONE)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.DIAMOND)
        .result(new ItemStack(Material.DIAMOND_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-coal-ore")
        .ingredient(Material.STONE)
        .ingredient(Material.COAL)
        .ingredient(Material.COAL)
        .ingredient(Material.COAL)
        .ingredient(Material.COAL)
        .ingredient(Material.COAL)
        .ingredient(Material.COAL)
        .ingredient(Material.COAL)
        .ingredient(Material.COAL)
        .result(new ItemStack(Material.COAL_ORE))
        .build());

    // Use Deepslate
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-deepslate-iron-ore")
        .ingredient(Material.DEEPSLATE)
        .ingredient(Material.IRON_INGOT)
        .ingredient(Material.IRON_INGOT)
        .ingredient(Material.IRON_INGOT)
        .ingredient(Material.IRON_INGOT)
        .ingredient(Material.IRON_INGOT)
        .ingredient(Material.IRON_INGOT)
        .ingredient(Material.IRON_INGOT)
        .ingredient(Material.IRON_INGOT)
        .result(new ItemStack(Material.DEEPSLATE_IRON_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-deepslate-gold-ore")
        .ingredient(Material.DEEPSLATE)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .result(new ItemStack(Material.DEEPSLATE_GOLD_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-deepslate-copper-ore")
        .ingredient(Material.DEEPSLATE)
        .ingredient(Material.COPPER_INGOT)
        .ingredient(Material.COPPER_INGOT)
        .ingredient(Material.COPPER_INGOT)
        .ingredient(Material.COPPER_INGOT)
        .ingredient(Material.COPPER_INGOT)
        .ingredient(Material.COPPER_INGOT)
        .ingredient(Material.COPPER_INGOT)
        .ingredient(Material.COPPER_INGOT)
        .result(new ItemStack(Material.DEEPSLATE_COPPER_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-deepslate-lapis-ore")
        .ingredient(Material.DEEPSLATE)
        .ingredient(Material.LAPIS_LAZULI)
        .ingredient(Material.LAPIS_LAZULI)
        .ingredient(Material.LAPIS_LAZULI)
        .ingredient(Material.LAPIS_LAZULI)
        .ingredient(Material.LAPIS_LAZULI)
        .ingredient(Material.LAPIS_LAZULI)
        .ingredient(Material.LAPIS_LAZULI)
        .ingredient(Material.LAPIS_LAZULI)
        .result(new ItemStack(Material.DEEPSLATE_LAPIS_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-deepslate-redstone-ore")
        .ingredient(Material.DEEPSLATE)
        .ingredient(Material.REDSTONE)
        .ingredient(Material.REDSTONE)
        .ingredient(Material.REDSTONE)
        .ingredient(Material.REDSTONE)
        .ingredient(Material.REDSTONE)
        .ingredient(Material.REDSTONE)
        .ingredient(Material.REDSTONE)
        .ingredient(Material.REDSTONE)
        .result(new ItemStack(Material.DEEPSLATE_REDSTONE_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-deepslate-emerald-ore")
        .ingredient(Material.DEEPSLATE)
        .ingredient(Material.EMERALD)
        .ingredient(Material.EMERALD)
        .ingredient(Material.EMERALD)
        .ingredient(Material.EMERALD)
        .ingredient(Material.EMERALD)
        .ingredient(Material.EMERALD)
        .ingredient(Material.EMERALD)
        .ingredient(Material.EMERALD)
        .result(new ItemStack(Material.DEEPSLATE_EMERALD_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-deepslate-diamond-ore")
        .ingredient(Material.DEEPSLATE)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.DIAMOND)
        .result(new ItemStack(Material.DEEPSLATE_DIAMOND_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-deepslate-coal-ore")
        .ingredient(Material.DEEPSLATE)
        .ingredient(Material.COAL)
        .ingredient(Material.COAL)
        .ingredient(Material.COAL)
        .ingredient(Material.COAL)
        .ingredient(Material.COAL)
        .ingredient(Material.COAL)
        .ingredient(Material.COAL)
        .ingredient(Material.COAL)
        .result(new ItemStack(Material.DEEPSLATE_COAL_ORE))
        .build());

// Use Nether Bricks
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-nether-gold-ore")
        .ingredient(Material.NETHER_BRICKS)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .ingredient(Material.GOLD_INGOT)
        .result(new ItemStack(Material.NETHER_GOLD_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-nether-quartz-ore")
        .ingredient(Material.NETHER_BRICKS)
        .ingredient(Material.QUARTZ)
        .ingredient(Material.QUARTZ)
        .ingredient(Material.QUARTZ)
        .ingredient(Material.QUARTZ)
        .ingredient(Material.QUARTZ)
        .ingredient(Material.QUARTZ)
        .ingredient(Material.QUARTZ)
        .ingredient(Material.QUARTZ)
        .result(new ItemStack(Material.NETHER_QUARTZ_ORE))
        .build());
    registerRecipe(AdaptRecipe.shapeless()
        .key("reconstruction-ancient-debris")
        .ingredient(Material.NETHER_BRICKS)
        .ingredient(Material.NETHERITE_SCRAP)
        .ingredient(Material.NETHERITE_SCRAP)
        .ingredient(Material.NETHERITE_SCRAP)
        .ingredient(Material.NETHERITE_SCRAP)
        .ingredient(Material.NETHERITE_SCRAP)
        .ingredient(Material.NETHERITE_SCRAP)
        .ingredient(Material.NETHERITE_SCRAP)
        .ingredient(Material.NETHERITE_SCRAP)
        .result(new ItemStack(Material.ANCIENT_DEBRIS))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.RAW_IRON)
        .key("challenge_crafting_recon_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_crafting_recon_100", "crafting.reconstruction.ores-reconstructed", 100, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("crafting.reconstruction.lore1"));
    v.addLore(C.UNDERLINE + Localizer.dLocalize("crafting.reconstruction.lore2"));
    v.addLore(C.YELLOW + Localizer.dLocalize("crafting.reconstruction.lore3"));
    v.addLore(C.YELLOW + Localizer.dLocalize("crafting.reconstruction.lore4"));
  }

  @EventHandler
  public void on(CraftItemEvent e) {
    Player p = (Player) e.getWhoClicked();
    if (!hasActiveAdaptation(p)) return;
    if (e.getRecipe() == null) {
      return;
    }
    Material result = e.getRecipe().getResult().getType();
    if (result.name().contains("ORE") || result == Material.ANCIENT_DEBRIS) {
      addStat(p, "crafting.reconstruction.ores-reconstructed", 1);
      reforgeImplosion(p.getLocation().add(0, 1, 0), result);
    }
  }

  private void reforgeImplosion(Location center, Material result) {
    if (!result.isBlock()) {
      return;
    }
    BlockData oreData = result.createBlockData();
    boolean rare = result == Material.DIAMOND_ORE || result == Material.DEEPSLATE_DIAMOND_ORE
        || result == Material.EMERALD_ORE || result == Material.DEEPSLATE_EMERALD_ORE
        || result == Material.ANCIENT_DEBRIS;
    boolean debris = result == Material.ANCIENT_DEBRIS;
    float chimePitch = rare ? 1.8F : 1.2F;
    timeline(center)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.BLOCK_CRACK, 0.9D - (0.9D * progress), 10, 0.0D, oreData);
          if (tick == 0) {
            fx.chord(Sound.BLOCK_STONE_PLACE, 0.8F, 0.6F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, chimePitch, Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.4F, 0.8F);
            if (debris) {
              fx.sound(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5F, 0.5F);
            }
          }
          if (tick >= 5) {
            fx.burst(Particles.CRIT_MAGIC, 8, 0.2D);
            if (rare) {
              fx.column(Particles.END_ROD, 5, 1.0D);
            }
            if (debris) {
              fx.particle(Particle.REVERSE_PORTAL, 8, 0, 0.4D, 0, 0.3D, 0.05D);
            }
          }
        })
        .start();
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Recraft ores from their base smelted components.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 5;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
