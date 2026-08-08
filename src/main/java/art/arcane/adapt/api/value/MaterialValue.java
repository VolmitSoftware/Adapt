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

package art.arcane.adapt.api.value;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import com.google.gson.JsonParseException;
import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class MaterialValue {
  private static final String RUNTIME_CACHE_SIGNATURE = UUID.randomUUID().toString();
  private static volatile MaterialValue valueCache = null;

  private Map<Material, Double> value = new ConcurrentHashMap<>();
  @Getter(AccessLevel.NONE)
  private String configurationSignature = currentConfigurationSignature();

  public static void save() {
    MaterialValue cache = valueCache;
    if (cache == null) {
      return;
    }

    File l = Adapt.instance.getDataFile("data", "value-cache.json");
    try {
      IO.writeAll(l, Json.toJson(cache, true));
    } catch (IOException e) {
      Adapt.verbose("Failed to save value cache");
    }
  }

  public static synchronized void invalidateCache() {
    valueCache = null;
  }

  public static MaterialValue get() {
    MaterialValue cache = valueCache;
    if (cache != null) {
      return cache;
    }

    synchronized (MaterialValue.class) {
      cache = valueCache;
      if (cache != null) {
        return cache;
      }
      MaterialValue dummy = new MaterialValue();
      File l = Adapt.instance.getDataFile("data", "value-cache.json");

      if (!l.exists()) {
        try {
          IO.writeAll(l, Json.toJson(dummy, true));
        } catch (IOException e) {
          e.printStackTrace();
          valueCache = dummy;
          return dummy;
        }
      }

      MaterialValue loaded;
      try {
        String raw = IO.readAll(l);
        loaded = raw.contains("\"configurationSignature\"")
            ? Json.fromJson(raw, MaterialValue.class)
            : new MaterialValue();
      } catch (IOException | JsonParseException e) {
        e.printStackTrace();
        loaded = new MaterialValue();
      }

      if (loaded == null || !currentConfigurationSignature().equals(loaded.configurationSignature)) {
        loaded = new MaterialValue();
      }
      loaded.value = toConcurrentValueMap(loaded.value);
      valueCache = loaded;
      return loaded;
    }
  }

  public static void debugValue(Material m) {
    debugValue(m, 0, 1, new HashSet<>());
  }

  private static void debugValue(Material m, int ind, int x, Set<MaterialRecipe> ignore) {
    PrecisionStopwatch p = PrecisionStopwatch.start();
    Adapt.verbose(Form.repeat("  ", ind) + m.name() + ": " + getValue(m) + (x == 1 ? "" : " (x" + x + ")"));

    int r = 0;
    for (MaterialRecipe i : getRecipes(m)) {
      if (ignore.contains(i)) {
        continue;
      }

      ignore.add(i);
      if (ignore.size() > AdaptConfig.get().getMaxRecipeListPrecaution()) {
        Adapt.verbose("Avoiding infinite loop");
        return;
      }

      int o = i.getOutput().getAmount();
      Adapt.verbose(Form.repeat("  ", ind) + "# Recipe [" + ind + "x" + r + (o == 1 ? "]" : "] (x" + o + ") "));

      for (MaterialCount j : i.getInput()) {
        debugValue(j.getMaterial(), ind + 1, j.getAmount(), ignore);
      }

      r++;
    }
    Adapt.verbose(Form.repeat("  ", ind) + " took " + Form.duration(p.getMilliseconds(), 0));
  }

  static double getMultiplier(Material material, Map<String, Double> multipliers) {
    if (material == null || multipliers == null) {
      return 1D;
    }
    Double exact = multipliers.get(material.name());
    if (exact != null) {
      return exact;
    }
    for (Map.Entry<String, Double> entry : multipliers.entrySet()) {
      if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(material.name()) && entry.getValue() != null) {
        return entry.getValue();
      }
    }
    return 1D;
  }

  static double applyMultiplier(Material material, double resolvedValue, Map<String, Double> multipliers) {
    return resolvedValue * getMultiplier(material, multipliers);
  }

  static String currentConfigurationSignature() {
    AdaptConfig config = AdaptConfig.get();
    Map<String, Double> configured = config.getValue().getValueMultipliers();
    Map<String, Double> ordered = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    if (configured != null) {
      ordered.putAll(configured);
    }
    return RUNTIME_CACHE_SIGNATURE + "|" + config.getValue().getBaseValue() + "|"
        + config.getMaxRecipeListPrecaution() + "|" + ordered;
  }

  static Map<Material, Double> toConcurrentValueMap(Map<Material, Double> source) {
    Map<Material, Double> concurrent = new ConcurrentHashMap<>();
    if (source == null) {
      return concurrent;
    }
    for (Map.Entry<Material, Double> entry : source.entrySet()) {
      if (entry.getKey() != null && entry.getValue() != null) {
        concurrent.put(entry.getKey(), entry.getValue());
      }
    }
    return concurrent;
  }

  public static double getValue(Material m) {
    try {
      return getValue(m, new HashSet<>(), get());
    } catch (Exception ignored) {
      return 1;
    }
  }

  private static double getValue(Material m, Set<MaterialRecipe> ignore, MaterialValue cache) {
    if (cache.value.containsKey(m)) {
      if (m.isBlock() && m.getHardness() == 0) {
        return 0;
      }
      return cache.value.get(m);
    }
    double v = AdaptConfig.get().getValue().getBaseValue();
    List<MaterialRecipe> recipes = getRecipes(m);
    double resolvedValue;
    if (recipes.isEmpty()) {
      resolvedValue = v;
    } else {
      List<Double> d = new ArrayList<>();
      for (MaterialRecipe i : recipes) {
        if (ignore.contains(i)) {
          continue;
        }
        ignore.add(i);
        double vx = v;
        for (MaterialCount j : i.getInput()) {
          vx += getValue(j.getMaterial(), ignore, cache);
        }
        d.add(vx / i.getOutput().getAmount());
      }
      if (d.size() > 0) {
        v += d.stream().mapToDouble(i -> i).average().getAsDouble();
      }
      if (v > AdaptConfig.get().getMaxRecipeListPrecaution()) {
        resolvedValue = v / 10 + 1;
      } else {
        resolvedValue = v;
      }
    }
    cache.value.put(m, applyMultiplier(m, resolvedValue, AdaptConfig.get().getValue().getValueMultipliers()));
    if (m.isBlock() && m.getHardness() == 0) {
      return 0;
    }
    return cache.value.get(m);
  }

  private static List<MaterialRecipe> getRecipes(Material mat) {
    List<MaterialRecipe> r = new ArrayList<>();
    try {
      ItemStack is = new ItemStack(mat);
      try {
        is.setDurability((short) -1);
      } catch (Throwable e) {
        Adapt.verbose("Failed to set durability of " + mat.name());
      }
      Bukkit.getRecipesFor(is).forEach(i -> {
        if (i instanceof AdaptRecipe) {
          Adapt.verbose("Skipping Adapt Recipe to prevent duplicates, " + mat.name() + " -> " + ((AdaptRecipe) i).getKey() + "");
          return;
        }
        MaterialRecipe rx = toMaterial(i);
        if (rx != null) {
          r.add(rx);
        }
      });
    } catch (Throwable e) {
      Adapt.verbose("Failed to get recipes for " + mat.name());
    }
    return r;
  }

  private static MaterialRecipe toMaterial(Recipe r) {
    try {
      if (r instanceof ShapelessRecipe recipe) {
        return MaterialRecipe.builder()
            .input(new ArrayList<>(recipe.getIngredientList().stream().map(i -> new MaterialCount(i.getType(), 1)).toList()))
            .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
            .build();
      } else if (r instanceof ShapedRecipe recipe) {
        MaterialRecipe re = MaterialRecipe.builder()
            .input(new ArrayList<>())
            .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
            .build();
        Map<Material, Integer> f = new HashMap<>();
        for (ItemStack i : recipe.getIngredientMap().values()) {
          if (i == null || i.getType().isAir()) {
            continue;
          }

          f.compute(i.getType(), (k, v) -> v == null ? 1 : v + 1);
        }

        f.forEach((k, v) -> re.getInput().add(new MaterialCount(k, v)));

        return re;
      } else if (r instanceof CookingRecipe recipe) {
        List<MaterialCount> a = new ArrayList<>();
        a.add(new MaterialCount(recipe.getInput().getType(), 1));

        return MaterialRecipe.builder()
            .input(a)
            .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
            .build();
      } else if (r instanceof MerchantRecipe recipe) {
        return MaterialRecipe.builder()
            .input(new ArrayList<>(recipe.getIngredients().stream().map(i -> new MaterialCount(i.getType(), 1)).toList()))
            .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
            .build();
      } else if (r instanceof StonecuttingRecipe recipe) {
        List<MaterialCount> a = new ArrayList<>();
        a.add(new MaterialCount(recipe.getInput().getType(), 1));

        return MaterialRecipe.builder()
            .input(a)
            .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
            .build();
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }

    return null;
  }
}
