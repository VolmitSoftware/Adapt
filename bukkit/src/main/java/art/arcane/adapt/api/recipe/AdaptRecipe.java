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

package art.arcane.adapt.api.recipe;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bukkit-typed recipe builders of unmigrated adaptations. Each builds the {@link RecipeSpec} that core registers
 * through the platform recipe host under {@code adapt:<key>}.
 */
public interface AdaptRecipe {
  static Shapeless.ShapelessBuilder shapeless() {
    return Shapeless.builder();
  }

  static Shaped.ShapedBuilder shaped() {
    return Shaped.builder();
  }

  static Smithing.SmithingBuilder smithing() {
    return Smithing.builder();
  }

  static Stonecutter.StonecutterBuilder stonecutter() {
    return Stonecutter.builder();
  }

  static Smoker.SmokerBuilder smoker() {
    return Smoker.builder();
  }

  static Blast.BlastBuilder blast() {
    return Blast.builder();
  }

  static Furnace.FurnaceBuilder furnace() {
    return Furnace.builder();
  }

  static Campfire.CampfireBuilder campfire() {
    return Campfire.builder();
  }

  ItemStack getResult();

  String getKey();

  int getRequiredLevel();

  default NamespacedKey getNSKey() {
    return BukkitRefs.key(id(getKey()));
  }

  default boolean is(Recipe recipe) {
    return recipe instanceof Keyed keyed && keyed.getKey().equals(getNSKey());
  }

  RecipeSpec toSpec();

  default void register() {
    AdaptCore.platform().recipes().register(toSpec());
    Adapt.verbose("Registered " + label(this) + " " + getNSKey());
  }

  default void unregister() {
    AdaptCore.platform().recipes().unregister(id(getKey()));
    Adapt.verbose("Unregistered " + label(this) + " " + getKey());
  }

  private static String label(AdaptRecipe recipe) {
    return switch (recipe) {
      case Smoker ignored -> "Smoker Recipe";
      case Furnace ignored -> "Furnace Recipe";
      case Campfire ignored -> "Campfire Recipe";
      case Blast ignored -> "Blast Furnace Recipe";
      case Shapeless ignored -> "Shapeless Crafting Recipe";
      case Stonecutter ignored -> "Stone Cutter Recipe";
      case Shaped ignored -> "Shaped Crafting Recipe";
      case Smithing ignored -> "Smithing Table Recipe";
      default -> "Recipe";
    };
  }

  private static Id id(String key) {
    return Id.adapt(key);
  }

  private static RecipeSpec.Cooking cooking(AdaptRecipe recipe, RecipeSpec.CookingStation station, Material ingredient, float experience, int cookTime) {
    return new RecipeSpec.Cooking(id(recipe.getKey()), station, Ingredient.of(BukkitRefs.id(ingredient)), BukkitRefs.item(recipe.getResult()),
        experience, cookTime, recipe.getRequiredLevel());
  }

  @Builder
  @Data
  class Smoker implements AdaptRecipe {
    private String key;
    private ItemStack result;
    @Builder.Default
    private int requiredLevel = 1;
    private Material ingredient;
    private float experience;
    private int cookTime;

    @Override
    public RecipeSpec toSpec() {
      return cooking(this, RecipeSpec.CookingStation.SMOKER, ingredient, experience, cookTime);
    }
  }

  @Builder
  @Data
  class Furnace implements AdaptRecipe {
    private String key;
    private ItemStack result;
    @Builder.Default
    private int requiredLevel = 1;
    private Material ingredient;
    private float experience;
    private int cookTime;

    @Override
    public RecipeSpec toSpec() {
      return cooking(this, RecipeSpec.CookingStation.FURNACE, ingredient, experience, cookTime);
    }
  }

  @Builder
  @Data
  class Campfire implements AdaptRecipe {
    private String key;
    private ItemStack result;
    @Builder.Default
    private int requiredLevel = 1;
    private Material ingredient;
    private float experience;
    private int cookTime;

    @Override
    public RecipeSpec toSpec() {
      return cooking(this, RecipeSpec.CookingStation.CAMPFIRE, ingredient, experience, cookTime);
    }
  }

  @Builder
  @Data
  class Blast implements AdaptRecipe {
    private String key;
    private ItemStack result;
    @Builder.Default
    private int requiredLevel = 1;
    private Material ingredient;
    private float experience;
    private int cookTime;

    @Override
    public RecipeSpec toSpec() {
      return cooking(this, RecipeSpec.CookingStation.BLAST_FURNACE, ingredient, experience, cookTime);
    }
  }

  @Builder
  @Data
  class Shapeless implements AdaptRecipe {
    private String key;
    private ItemStack result;
    @Builder.Default
    private int requiredLevel = 1;
    @Singular
    private List<Material> ingredients;

    @Override
    public RecipeSpec toSpec() {
      List<Ingredient> converted = new ArrayList<>(ingredients.size());
      for (Material material : ingredients) {
        converted.add(Ingredient.of(BukkitRefs.id(material)));
      }
      return new RecipeSpec.Shapeless(id(key), converted, BukkitRefs.item(result), requiredLevel);
    }
  }

  @Builder
  @Data
  class Stonecutter implements AdaptRecipe {
    private String key;
    private ItemStack result;
    @Builder.Default
    private int requiredLevel = 1;
    private Material ingredient;

    @Override
    public RecipeSpec toSpec() {
      return new RecipeSpec.Stonecutting(id(key), Ingredient.of(BukkitRefs.id(ingredient)), BukkitRefs.item(result), requiredLevel);
    }
  }

  @Builder
  @Data
  class Shaped implements AdaptRecipe {
    private String key;
    private ItemStack result;
    @Builder.Default
    private int requiredLevel = 1;
    @Singular
    private List<MaterialChar> ingredients;
    @Singular
    private List<String> shapes;

    @Override
    public RecipeSpec toSpec() {
      Map<Character, Ingredient> converted = new LinkedHashMap<>();
      for (MaterialChar ingredient : ingredients) {
        converted.put(ingredient.getCharacter(), ingredient.getIngredient());
      }
      return new RecipeSpec.Shaped(id(key), shapes, converted, BukkitRefs.item(result), requiredLevel);
    }
  }

  @Builder
  @Data
  class Smithing implements AdaptRecipe {
    private String key;
    private ItemStack result;
    @Builder.Default
    private int requiredLevel = 1;
    private Material base;
    private Material addition;

    @Override
    public RecipeSpec toSpec() {
      return new RecipeSpec.Smithing(id(key), null, Ingredient.of(BukkitRefs.id(base)), Ingredient.of(BukkitRefs.id(addition)),
          BukkitRefs.item(result), requiredLevel);
    }
  }
}
