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

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.value.Id;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
public class MaterialChar {
  private char character;
  private Ingredient ingredient;
  private transient RecipeChoice choice;

  public MaterialChar(char character, Ingredient ingredient) {
    this.character = character;
    this.ingredient = ingredient;
  }

  public MaterialChar(char character, RecipeChoice choice) {
    this(character, ingredient(choice));
    this.choice = choice;
  }

  public MaterialChar(char character, Tag<Material> tag) {
    this(character, anyOf(tag.getValues().toArray(new Material[0])));
  }

  public MaterialChar(char character, Material... material) {
    this(character, anyOf(material));
  }

  public void setIngredient(Ingredient ingredient) {
    this.ingredient = ingredient;
    this.choice = null;
  }

  public RecipeChoice getChoice() {
    return choice != null ? choice : choice(ingredient);
  }

  public void setChoice(RecipeChoice choice) {
    this.ingredient = ingredient(choice);
    this.choice = choice;
  }

  private static Ingredient anyOf(Material... materials) {
    Set<Id> types = new LinkedHashSet<>();
    for (Material material : materials) {
      types.add(BukkitRefs.id(material));
    }
    return new Ingredient.AnyOf(types);
  }

  private static Ingredient ingredient(RecipeChoice choice) {
    if (choice == null) {
      return null;
    }
    if (choice instanceof RecipeChoice.MaterialChoice materials) {
      return anyOf(materials.getChoices().toArray(new Material[0]));
    }
    if (choice instanceof RecipeChoice.ExactChoice exact && exact.getChoices().size() == 1) {
      return new Ingredient.Exact(BukkitRefs.item(exact.getChoices().getFirst().clone()));
    }
    ItemStack example = choice.getItemStack();
    return new Ingredient.Matching(BukkitRefs.id(example.getType()), BukkitRefs.item(example),
        (ItemRef item) -> choice.test(BukkitRefs.item(item)));
  }

  private static RecipeChoice choice(Ingredient ingredient) {
    return switch (ingredient) {
      case null -> null;
      case Ingredient.AnyOf anyOf -> {
        List<Material> materials = new ArrayList<>();
        for (Id type : anyOf.types()) {
          Material material = BukkitRefs.material(type);
          if (material != null) {
            materials.add(material);
          }
        }
        yield new RecipeChoice.MaterialChoice(materials);
      }
      case Ingredient.Tag tag -> new RecipeChoice.MaterialChoice(
          Bukkit.getTag(Tag.REGISTRY_ITEMS, BukkitRefs.key(tag.tag()), Material.class));
      case Ingredient.Exact exact -> new RecipeChoice.ExactChoice(BukkitRefs.item(exact.item()));
      case Ingredient.Matching matching -> new RecipeChoice.ExactChoice(BukkitRefs.item(matching.example()));
    };
  }
}
