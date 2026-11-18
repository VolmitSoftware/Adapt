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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;


public class CraftingReconstruction extends SimpleAdaptation<CraftingReconstruction.Config> {
  private static final Id ANCIENT_DEBRIS = Id.minecraft("ancient_debris");

  public CraftingReconstruction() {
    super("crafting-reconstruction");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("coal_ore"));
    setInterval(80248);
    registerRecipe(ore("reconstruction-iron-ore", "stone", "iron_ingot", "iron_ore"));
    registerRecipe(ore("reconstruction-gold-ore", "stone", "gold_ingot", "gold_ore"));
    registerRecipe(ore("reconstruction-copper-ore", "stone", "copper_ingot", "copper_ore"));
    registerRecipe(ore("reconstruction-lapis-ore", "stone", "lapis_lazuli", "lapis_ore"));
    registerRecipe(ore("reconstruction-redstone-ore", "stone", "redstone", "redstone_ore"));
    registerRecipe(ore("reconstruction-emerald-ore", "stone", "emerald", "emerald_ore"));
    registerRecipe(ore("reconstruction-diamond-ore", "stone", "diamond", "diamond_ore"));
    registerRecipe(ore("reconstruction-coal-ore", "stone", "coal", "coal_ore"));

    registerRecipe(ore("reconstruction-deepslate-iron-ore", "deepslate", "iron_ingot", "deepslate_iron_ore"));
    registerRecipe(ore("reconstruction-deepslate-gold-ore", "deepslate", "gold_ingot", "deepslate_gold_ore"));
    registerRecipe(ore("reconstruction-deepslate-copper-ore", "deepslate", "copper_ingot", "deepslate_copper_ore"));
    registerRecipe(ore("reconstruction-deepslate-lapis-ore", "deepslate", "lapis_lazuli", "deepslate_lapis_ore"));
    registerRecipe(ore("reconstruction-deepslate-redstone-ore", "deepslate", "redstone", "deepslate_redstone_ore"));
    registerRecipe(ore("reconstruction-deepslate-emerald-ore", "deepslate", "emerald", "deepslate_emerald_ore"));
    registerRecipe(ore("reconstruction-deepslate-diamond-ore", "deepslate", "diamond", "deepslate_diamond_ore"));
    registerRecipe(ore("reconstruction-deepslate-coal-ore", "deepslate", "coal", "deepslate_coal_ore"));

    registerRecipe(ore("reconstruction-nether-gold-ore", "nether_bricks", "gold_ingot", "nether_gold_ore"));
    registerRecipe(ore("reconstruction-nether-quartz-ore", "nether_bricks", "quartz", "nether_quartz_ore"));
    registerRecipe(ore("reconstruction-ancient-debris", "nether_bricks", "netherite_scrap", "ancient_debris"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("raw_iron"))
        .key("challenge_crafting_recon_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_crafting_recon_100", "crafting.reconstruction.ores-reconstructed", 100, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(CraftingMessages.RECONSTRUCTION_LORE1));
    v.addLore(C.UNDERLINE + AdaptLanguage.text(CraftingMessages.RECONSTRUCTION_LORE2));
    v.addLore(C.YELLOW + AdaptLanguage.text(CraftingMessages.RECONSTRUCTION_LORE3));
    v.addLore(C.YELLOW + AdaptLanguage.text(CraftingMessages.RECONSTRUCTION_LORE4));
  }

  private static RecipeSpec ore(String key, String base, String unit, String result) {
    List<Ingredient> ingredients = new ArrayList<>(9);
    ingredients.add(Ingredient.of(Id.minecraft(base)));
    for (int index = 0; index < 8; index++) {
      ingredients.add(Ingredient.of(Id.minecraft(unit)));
    }
    return new RecipeSpec.Shapeless(Id.adapt(key), ingredients, items().create(Id.minecraft(result), 1));
  }

  @Handle
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) return;
    Optional<RecipeView> recipe = e.recipeKey() == null ? Optional.empty()
        : AdaptCore.platform().recipes().recipe(e.recipeKey());
    if (recipe.isEmpty()) {
      return;
    }
    ItemRef recipeResult = recipe.get().result();
    Id result = recipeResult.isEmpty() ? Id.minecraft("air") : recipeResult.type();
    if (result.path().toUpperCase(Locale.ROOT).contains("ORE") || result.equals(ANCIENT_DEBRIS)) {
      addStat(p, "crafting.reconstruction.ores-reconstructed", 1);
      reforgeImplosion(p.location().add(0, 1, 0), result);
    }
  }

  private void reforgeImplosion(Loc center, Id result) {
    if (!items().isBlock(result)) {
      return;
    }
    ParticleSpec oreCrack = Particles.blockCrack(items().blockState(result));
    boolean rare = result.equals(Id.minecraft("diamond_ore")) || result.equals(Id.minecraft("deepslate_diamond_ore"))
        || result.equals(Id.minecraft("emerald_ore")) || result.equals(Id.minecraft("deepslate_emerald_ore"))
        || result.equals(ANCIENT_DEBRIS);
    boolean debris = result.equals(ANCIENT_DEBRIS);
    float chimePitch = rare ? 1.8F : 1.2F;
    timeline(center)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20)
        .frame((fx, tick, progress) -> {
          fx.ring(oreCrack, 0.9D - (0.9D * progress), 10, 0.0D);
          if (tick == 0) {
            fx.chord(Sounds.BLOCK_STONE_PLACE, 0.8F, 0.6F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, chimePitch, Sounds.ITEM_LODESTONE_COMPASS_LOCK, 0.4F, 0.8F);
            if (debris) {
              fx.sound(Sounds.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5F, 0.5F);
            }
          }
          if (tick >= 5) {
            fx.burst(Particles.CRIT_MAGIC, 8, 0.2D);
            if (rare) {
              fx.column(Particles.END_ROD, 5, 1.0D);
            }
            if (debris) {
              fx.particle(Particles.REVERSE_PORTAL, 8, 0, 0.4D, 0, 0.3D, 0.05D);
            }
          }
        })
        .start();
  }

  private static ItemOps items() {
    return AdaptCore.platform().host().items();
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
