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

import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

final class CraftingIngredients {
  private CraftingIngredients() {
  }

  static List<RecipeChoice> perCraftChoices(Recipe recipe) {
    if (recipe instanceof ShapelessRecipe shapeless) {
      return List.copyOf(shapeless.getChoiceList());
    }
    if (recipe instanceof ShapedRecipe shaped) {
      List<RecipeChoice> choices = new ArrayList<>();
      Map<Character, RecipeChoice> ingredients = shaped.getChoiceMap();
      for (String row : shaped.getShape()) {
        for (int column = 0; column < row.length(); column++) {
          RecipeChoice choice = ingredients.get(row.charAt(column));
          if (choice != null && choice != RecipeChoice.empty()) {
            choices.add(choice);
          }
        }
      }
      return List.copyOf(choices);
    }
    return List.of();
  }

  static BatchPlan plan(List<RecipeChoice> choices, ItemStack[] storage, int maxCrafts, int reserve) {
    if (choices.isEmpty() || maxCrafts <= 0) {
      return new BatchPlan(0, new int[storage.length]);
    }
    Map<RecipeChoice, Integer> requirements = new LinkedHashMap<>();
    for (RecipeChoice choice : choices) {
      requirements.merge(choice, 1, Integer::sum);
    }
    Map<Material, Integer> available = new EnumMap<>(Material.class);
    long total = 0;
    for (ItemStack item : storage) {
      if (item != null && item.getAmount() > 0 && item.getType() != Material.AIR
          && item.getType() != Material.CAVE_AIR && item.getType() != Material.VOID_AIR) {
        available.merge(item.getType(), item.getAmount(), Integer::sum);
        total += item.getAmount();
      }
    }
    int upper = (int) Math.min(maxCrafts, total / choices.size());
    BatchPlan maximum = allocate(requirements, storage, available, upper, reserve);
    if (maximum != null) {
      return maximum;
    }
    BatchPlan best = new BatchPlan(0, new int[storage.length]);
    int lower = 1;
    upper--;
    while (lower <= upper) {
      int crafts = lower + (upper - lower) / 2;
      BatchPlan candidate = allocate(requirements, storage, available, crafts, reserve);
      if (candidate == null) {
        upper = crafts - 1;
      } else {
        best = candidate;
        lower = crafts + 1;
      }
    }
    return best;
  }

  private static BatchPlan allocate(Map<RecipeChoice, Integer> requirements, ItemStack[] storage,
                                    Map<Material, Integer> available, int crafts, int reserve) {
    int firstSlot = 1 + requirements.size();
    int firstMaterial = firstSlot + storage.length;
    int sink = firstMaterial + available.size();
    FlowNetwork network = new FlowNetwork(sink + 1);
    Map<Material, Integer> materialNodes = new EnumMap<>(Material.class);
    int materialIndex = firstMaterial;
    for (Map.Entry<Material, Integer> entry : available.entrySet()) {
      materialNodes.put(entry.getKey(), materialIndex);
      network.connect(materialIndex++, sink, Math.max(0, entry.getValue() - Math.max(0, reserve)));
    }
    for (int slot = 0; slot < storage.length; slot++) {
      ItemStack item = storage[slot];
      if (item != null && materialNodes.containsKey(item.getType())) {
        network.connect(firstSlot + slot, materialNodes.get(item.getType()), Math.max(0, item.getAmount()));
      }
    }
    int ingredientNode = 1;
    int required = 0;
    for (Map.Entry<RecipeChoice, Integer> entry : requirements.entrySet()) {
      int demand = entry.getValue() * crafts;
      required += demand;
      network.connect(0, ingredientNode, demand);
      for (int slot = 0; slot < storage.length; slot++) {
        ItemStack item = storage[slot];
        if (item != null && item.getAmount() > 0 && entry.getKey().test(item)
            && (!(entry.getKey() instanceof RecipeChoice.MaterialChoice) || !item.hasItemMeta())) {
          network.connect(ingredientNode, firstSlot + slot, demand);
        }
      }
      ingredientNode++;
    }
    if (network.flow(0, sink) != required) {
      return null;
    }
    int[] costs = new int[storage.length];
    for (int slot = 0; slot < storage.length; slot++) {
      ItemStack item = storage[slot];
      if (item != null && materialNodes.containsKey(item.getType())) {
        costs[slot] = Math.max(0, item.getAmount()) - network.remaining(firstSlot + slot, materialNodes.get(item.getType()));
      }
    }
    return new BatchPlan(crafts, costs);
  }

  private static final class FlowNetwork {
    private final int[][] capacities;
    private final int[][] neighbors;
    private final int[] degrees;

    private FlowNetwork(int size) {
      capacities = new int[size][size];
      neighbors = new int[size][size];
      degrees = new int[size];
    }

    private void connect(int from, int to, int capacity) {
      if (capacity <= 0) {
        return;
      }
      if (capacities[from][to] == 0 && capacities[to][from] == 0) {
        neighbors[from][degrees[from]++] = to;
        neighbors[to][degrees[to]++] = from;
      }
      capacities[from][to] += capacity;
    }

    private int remaining(int from, int to) {
      return capacities[from][to];
    }

    private int flow(int source, int sink) {
      int total = 0;
      int[] parents = new int[degrees.length];
      int[] queue = new int[degrees.length];
      while (findPath(source, sink, parents, queue)) {
        int amount = Integer.MAX_VALUE;
        for (int node = sink; node != source; node = parents[node]) {
          amount = Math.min(amount, capacities[parents[node]][node]);
        }
        for (int node = sink; node != source; node = parents[node]) {
          capacities[parents[node]][node] -= amount;
          capacities[node][parents[node]] += amount;
        }
        total += amount;
      }
      return total;
    }

    private boolean findPath(int source, int sink, int[] parents, int[] queue) {
      Arrays.fill(parents, -1);
      parents[source] = source;
      queue[0] = source;
      int head = 0;
      int tail = 1;
      while (head < tail) {
        int node = queue[head++];
        for (int index = 0; index < degrees[node]; index++) {
          int next = neighbors[node][index];
          if (parents[next] >= 0 || capacities[node][next] <= 0) {
            continue;
          }
          parents[next] = node;
          if (next == sink) {
            return true;
          }
          queue[tail++] = next;
        }
      }
      return false;
    }
  }

  record BatchPlan(int crafts, int[] costs) {
  }
}
