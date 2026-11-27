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
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CraftingIngredients {
  private static final Map<Id, Id> REMAINDERS = Map.of(
      Id.minecraft("milk_bucket"), Id.minecraft("bucket"),
      Id.minecraft("water_bucket"), Id.minecraft("bucket"),
      Id.minecraft("lava_bucket"), Id.minecraft("bucket"),
      Id.minecraft("powder_snow_bucket"), Id.minecraft("bucket"),
      Id.minecraft("honey_bottle"), Id.minecraft("glass_bottle"),
      Id.minecraft("dragon_breath"), Id.minecraft("glass_bottle"));

  private CraftingIngredients() {
  }

  public static List<RecipeChoice> perCraftChoices(RecipeView recipe) {
    if (recipe.kind() == RecipeView.Kind.SHAPELESS) {
      return List.copyOf(recipe.choices());
    }
    if (recipe.kind() == RecipeView.Kind.SHAPED) {
      List<RecipeChoice> choices = new ArrayList<>();
      Map<Character, RecipeChoice> ingredients = recipe.shapedChoices();
      for (String row : recipe.shape()) {
        for (int column = 0; column < row.length(); column++) {
          RecipeChoice choice = ingredients.get(row.charAt(column));
          if (choice != null) {
            choices.add(choice);
          }
        }
      }
      return List.copyOf(choices);
    }
    return List.of();
  }

  static BatchPlan plan(List<RecipeChoice> choices, ItemRef[] storage, int maxCrafts, int reserve) {
    if (choices.isEmpty() || maxCrafts <= 0) {
      return new BatchPlan(0, new int[storage.length]);
    }
    Map<RecipeChoice, Integer> requirements = new LinkedHashMap<>();
    for (RecipeChoice choice : choices) {
      requirements.merge(choice, 1, Integer::sum);
    }
    Map<Id, Integer> available = new LinkedHashMap<>();
    long total = 0;
    for (ItemRef item : storage) {
      if (item != null && item.amount() > 0 && !item.type().isAir()) {
        available.merge(item.type(), item.amount(), Integer::sum);
        total += item.amount();
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

  private static BatchPlan allocate(Map<RecipeChoice, Integer> requirements, ItemRef[] storage,
                                    Map<Id, Integer> available, int crafts, int reserve) {
    int firstSlot = 1 + requirements.size();
    int firstMaterial = firstSlot + storage.length;
    int sink = firstMaterial + available.size();
    FlowNetwork network = new FlowNetwork(sink + 1);
    Map<Id, Integer> materialNodes = new LinkedHashMap<>();
    int materialIndex = firstMaterial;
    for (Map.Entry<Id, Integer> entry : available.entrySet()) {
      materialNodes.put(entry.getKey(), materialIndex);
      network.connect(materialIndex++, sink, Math.max(0, entry.getValue() - Math.max(0, reserve)));
    }
    for (int slot = 0; slot < storage.length; slot++) {
      ItemRef item = storage[slot];
      if (item != null && materialNodes.containsKey(item.type())) {
        network.connect(firstSlot + slot, materialNodes.get(item.type()), Math.max(0, item.amount()));
      }
    }
    int ingredientNode = 1;
    int required = 0;
    for (Map.Entry<RecipeChoice, Integer> entry : requirements.entrySet()) {
      int demand = entry.getValue() * crafts;
      required += demand;
      network.connect(0, ingredientNode, demand);
      for (int slot = 0; slot < storage.length; slot++) {
        ItemRef item = storage[slot];
        if (item != null && item.amount() > 0 && entry.getKey().test(item)
            && (entry.getKey().isExact() || isPlain(item))) {
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
      ItemRef item = storage[slot];
      if (item != null && materialNodes.containsKey(item.type())) {
        costs[slot] = Math.max(0, item.amount()) - network.remaining(firstSlot + slot, materialNodes.get(item.type()));
      }
    }
    return new BatchPlan(crafts, costs);
  }

  /** A stack without components of its own, the stacks a type-only ingredient may take. */
  static boolean isPlain(ItemRef item) {
    return item.isSimilar(AdaptApi.platform().host().items().create(item.type(), Math.max(1, item.amount())));
  }

  /** The containers a table hands back for {@code used} ingredients of {@code type}, or null when it keeps none. */
  static @Nullable ItemRef remainder(Id type, int used) {
    Id container = REMAINDERS.get(type);
    return container == null || used <= 0 ? null : AdaptApi.platform().host().items().create(container, used);
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
