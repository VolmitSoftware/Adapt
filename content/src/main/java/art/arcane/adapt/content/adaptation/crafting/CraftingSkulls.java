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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CraftingSkulls extends SimpleAdaptation<CraftingSkulls.Config> {
  public static final Id ID = Id.adapt("crafting/skulls");
  private static final Id SKELETON_SKULL = Id.minecraft("skeleton_skull");
  private static final Id WITHER_SKELETON_SKULL = Id.minecraft("wither_skeleton_skull");
  private static final Id ZOMBIE_HEAD = Id.minecraft("zombie_head");
  private static final Id CREEPER_HEAD = Id.minecraft("creeper_head");
  private static final Id DRAGON_HEAD = Id.minecraft("dragon_head");

  public CraftingSkulls() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("skeleton_skull"));
    setInterval(17776);
    registerRecipe(skull("crafting-skeletonskull", "bone", "skeleton_skull"));
    registerRecipe(skull("crafting-witherskeletonskull", "nether_brick", "wither_skeleton_skull"));
    registerRecipe(skull("crafting-zombieskull", "rotten_flesh", "zombie_head"));
    registerRecipe(skull("crafting-creeperhead", "gunpowder", "creeper_head"));
    registerRecipe(skull("crafting-dragonhead", "dragon_breath", "dragon_head"));
    AdaptAdvancement skulls100 = AdaptAdvancement.builder().key("challenge_crafting_skulls_100").icon(Id.minecraft("wither_skeleton_skull")).title(Text.key("adapt.advancement.challenge_crafting_skulls_100.title")).description(Text.key("adapt.advancement.challenge_crafting_skulls_100.description")).frame(AdvancementFrame.CHALLENGE).visibility(AdvancementVisibility.VANILLA).build();
    AdaptAdvancement skulls10 = AdaptAdvancement.builder().key("challenge_crafting_skulls_10").icon(Id.minecraft("skeleton_skull")).title(Text.key("adapt.advancement.challenge_crafting_skulls_10.title")).description(Text.key("adapt.advancement.challenge_crafting_skulls_10.description")).frame(AdvancementFrame.CHALLENGE).visibility(AdvancementVisibility.VANILLA).child(skulls100).build();
    registerAdvancement(skulls10);
    registerMilestone(skulls10.key(), "crafting.skulls.skulls-crafted", 10, 300);
    registerMilestone(skulls100.key(), "crafting.skulls.skulls-crafted", 100, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + TextFormat.GRAY + Text.key("adapt.crafting.skulls.lore1").resolve());
    v.addLore(TextFormat.YELLOW + "- " + TextFormat.GRAY + Text.key("adapt.crafting.skulls.lore2").resolve());
    v.addLore(TextFormat.YELLOW + "- " + TextFormat.GRAY + Text.key("adapt.crafting.skulls.lore3").resolve());
    v.addLore(TextFormat.YELLOW + "- " + TextFormat.GRAY + Text.key("adapt.crafting.skulls.lore4").resolve());
    v.addLore(TextFormat.YELLOW + "- " + TextFormat.GRAY + Text.key("adapt.crafting.skulls.lore5").resolve());
    v.addLore(TextFormat.YELLOW + "- " + TextFormat.GRAY + Text.key("adapt.crafting.skulls.lore6").resolve());
  }


  private static RecipeSpec skull(String key, String ring, String result) {
    return RecipeSpec.shaped(Id.adapt(key), List.of(
            "III",
            "IXI",
            "III"), Map.of('I', Ingredient.of(Id.minecraft(ring)), 'X', Ingredient.of(Id.minecraft("bone_block"))), AdaptApi.platform().host().items().create(Id.minecraft(result), 1));
  }

  @Handle
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) return;
    Optional<RecipeView> recipe = e.recipeKey() == null ? Optional.empty()
        : AdaptApi.platform().recipes().recipe(e.recipeKey());
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
