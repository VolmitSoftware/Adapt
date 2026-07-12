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

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.HashMap;
import java.util.Map;

final class CraftingIngredients {
  private CraftingIngredients() {
  }

  static Map<Material, Integer> perCraftCounts(Recipe recipe) {
    if (recipe instanceof ShapelessRecipe shapeless) {
      return mergeCounts(shapeless.getIngredientList());
    }
    if (recipe instanceof ShapedRecipe shaped) {
      return mergeCounts(shaped.getIngredientMap().values());
    }
    return null;
  }

  private static Map<Material, Integer> mergeCounts(Iterable<ItemStack> ingredients) {
    Map<Material, Integer> counts = new HashMap<>();
    for (ItemStack ingredient : ingredients) {
      if (ingredient == null || ingredient.getType().isAir()) {
        continue;
      }
      counts.merge(ingredient.getType(), Math.max(1, ingredient.getAmount()), Integer::sum);
    }
    return counts.isEmpty() ? null : counts;
  }
}
