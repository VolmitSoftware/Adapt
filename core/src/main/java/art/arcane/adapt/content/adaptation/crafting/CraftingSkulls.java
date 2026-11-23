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
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AdvancementMessages;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CraftingSkulls extends SimpleAdaptation<CraftingSkulls.Config> {
  private static final Id SKELETON_SKULL = Id.minecraft("skeleton_skull");
  private static final Id WITHER_SKELETON_SKULL = Id.minecraft("wither_skeleton_skull");
  private static final Id ZOMBIE_HEAD = Id.minecraft("zombie_head");
  private static final Id CREEPER_HEAD = Id.minecraft("creeper_head");
  private static final Id DRAGON_HEAD = Id.minecraft("dragon_head");

  public CraftingSkulls() {
    super("crafting-skulls");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("skeleton_skull"));
    setInterval(17776);
    registerRecipe(skull("crafting-skeletonskull", "bone", "skeleton_skull"));
    registerRecipe(skull("crafting-witherskeletonskull", "nether_brick", "wither_skeleton_skull"));
    registerRecipe(skull("crafting-zombieskull", "rotten_flesh", "zombie_head"));
    registerRecipe(skull("crafting-creeperhead", "gunpowder", "creeper_head"));
    registerRecipe(skull("crafting-dragonhead", "dragon_breath", "dragon_head"));
    AdvancementSpec skulls100 = AdvancementSpec.challenge(
        "challenge_crafting_skulls_100",
        Id.minecraft("wither_skeleton_skull"),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_SKULLS_100_TITLE),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_SKULLS_100_DESCRIPTION)
    );
    AdvancementSpec skulls10 = AdvancementSpec.challenge(
        "challenge_crafting_skulls_10",
        Id.minecraft("skeleton_skull"),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_SKULLS_10_TITLE),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_SKULLS_10_DESCRIPTION)
    ).withChild(skulls100);
    registerAdvancementSpec(skulls10);
    registerStatTracker(skulls10.statTracker("crafting.skulls.skulls-crafted", 10, 300));
    registerStatTracker(skulls100.statTracker("crafting.skulls.skulls-crafted", 100, 1000));
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(CraftingMessages.SKULLS_LORE1));
    v.addLore(C.YELLOW + "- " + C.GRAY + AdaptLanguage.text(CraftingMessages.SKULLS_LORE2));
    v.addLore(C.YELLOW + "- " + C.GRAY + AdaptLanguage.text(CraftingMessages.SKULLS_LORE3));
    v.addLore(C.YELLOW + "- " + C.GRAY + AdaptLanguage.text(CraftingMessages.SKULLS_LORE4));
    v.addLore(C.YELLOW + "- " + C.GRAY + AdaptLanguage.text(CraftingMessages.SKULLS_LORE5));
    v.addLore(C.YELLOW + "- " + C.GRAY + AdaptLanguage.text(CraftingMessages.SKULLS_LORE6));
  }


  private static RecipeSpec skull(String key, String ring, String result) {
    return new RecipeSpec.Shaped(Id.adapt(key),
        List.of(
            "III",
            "IXI",
            "III"),
        Map.of('I', Ingredient.of(Id.minecraft(ring)), 'X', Ingredient.of(Id.minecraft("bone_block"))),
        AdaptCore.platform().host().items().create(Id.minecraft(result), 1));
  }

  @Handle
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) return;
    Optional<RecipeView> recipe = e.recipeKey() == null ? Optional.empty()
        : AdaptCore.platform().recipes().recipe(e.recipeKey());
    if (recipe.isPresent()) {
      ItemRef recipeResult = recipe.get().result();
      Id result = recipeResult.isEmpty() ? Id.minecraft("air") : recipeResult.type();
      if (result.equals(SKELETON_SKULL) || result.equals(WITHER_SKELETON_SKULL)
          || result.equals(ZOMBIE_HEAD) || result.equals(CREEPER_HEAD)
          || result.equals(DRAGON_HEAD)) {
        addStat(p, "crafting.skulls.skulls-crafted", 1);
        soulForge(p.location().add(0, 1, 0), result);
      }
    }
  }

  private void soulForge(Loc center, Id result) {
    timeline(center)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20)
        .frame((fx, tick, progress) -> {
          double rise = 0.2D + (progress * 1.2D);
          fx.particle(Particles.SOUL, 2, 0, rise, 0, 0.1D, 0.01D);
          fx.particle(Particles.SMOKE, 2, 0, rise, 0, 0.12D, 0.01D);
          if (tick == 0 || tick == 5) {
            fx.ring(Particles.SOUL, 0.4D, 8, 1.6D);
          }
          if (tick == 0) {
            fx.chord(Sounds.PARTICLE_SOUL_ESCAPE, 0.7F, 0.6F, Sounds.BLOCK_BONE_BLOCK_PLACE, 0.8F, 0.8F, Sounds.ENTITY_SKELETON_AMBIENT, 0.4F, 0.5F);
          }
        })
        .start();
    if (result.equals(CREEPER_HEAD)) {
      fx(center, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 6, 0.3D)
          .sound(Sounds.ENTITY_CREEPER_PRIMED, 0.4F, 1.2F);
    } else if (result.equals(WITHER_SKELETON_SKULL)) {
      fx(center, FxPriority.TRANSITION)
          .burst(Particles.SOUL, 6, 0.3D)
          .sound(Sounds.ENTITY_WITHER_SPAWN, 0.3F, 1.4F);
    } else if (result.equals(DRAGON_HEAD)) {
      fx(center, FxPriority.TRANSITION)
          .particle(Particles.REVERSE_PORTAL, 10, 0, 0.6D, 0, 0.4D, 0.05D)
          .dustRing(new Rgb(30, 30, 40), 1.2D, 20, 1.2F)
          .sound(Sounds.ENTITY_ENDER_DRAGON_GROWL, 0.3F, 0.8F);
    } else if (result.equals(ZOMBIE_HEAD)) {
      fx(center, FxPriority.TRANSITION)
          .sound(Sounds.ENTITY_ZOMBIE_AMBIENT, 0.4F, 0.6F);
    }
  }


  @ConfigDescription("Craft Mob Skulls using materials surrounding a Bone Block.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      initialCost = 2;
      permanent = true;
      baseCost = 2;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
