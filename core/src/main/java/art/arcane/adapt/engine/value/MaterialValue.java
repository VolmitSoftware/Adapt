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

package art.arcane.adapt.engine.value;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.IO;
import com.google.gson.JsonParseException;
import lombok.AccessLevel;
import lombok.Getter;

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

  private Map<Id, Double> value = new ConcurrentHashMap<>();
  @Getter(AccessLevel.NONE)
  private String configurationSignature = currentConfigurationSignature();

  public static void save() {
    MaterialValue cache = valueCache;
    if (cache == null) {
      return;
    }

    File l = AdaptEnvironment.dataFile("data", "value-cache.json");
    try {
      IO.writeAll(l, Json.toJson(CacheFile.of(cache), true));
    } catch (IOException e) {
      AdaptLog.verbose("Failed to save value cache");
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
      File l = AdaptEnvironment.dataFile("data", "value-cache.json");

      if (!l.exists()) {
        try {
          IO.writeAll(l, Json.toJson(CacheFile.of(dummy), true));
        } catch (IOException e) {
          AdaptLog.error(e);
          valueCache = dummy;
          return dummy;
        }
      }

      MaterialValue loaded;
      try {
        String raw = IO.readAll(l);
        loaded = raw.contains("\"configurationSignature\"")
            ? Json.fromJson(raw, CacheFile.class).toValue()
            : new MaterialValue();
      } catch (IOException | JsonParseException e) {
        AdaptLog.error(e);
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

  public static void debugValue(Id m) {
    debugValue(m, 0, 1, new HashSet<>());
  }

  private static void debugValue(Id m, int ind, int x, Set<MaterialRecipe> ignore) {
    long started = System.nanoTime();
    AdaptLog.verbose(Form.repeat("  ", ind) + m + ": " + getValue(m) + (x == 1 ? "" : " (x" + x + ")"));

    int r = 0;
    for (MaterialRecipe i : getRecipes(m)) {
      if (ignore.contains(i)) {
        continue;
      }

      ignore.add(i);
      if (ignore.size() > AdaptConfig.get().getMaxRecipeListPrecaution()) {
        AdaptLog.verbose("Avoiding infinite loop");
        return;
      }

      int o = i.getOutput().getAmount();
      AdaptLog.verbose(Form.repeat("  ", ind) + "# Recipe [" + ind + "x" + r + (o == 1 ? "]" : "] (x" + o + ") "));

      for (MaterialCount j : i.getInput()) {
        debugValue(j.getMaterial(), ind + 1, j.getAmount(), ignore);
      }

      r++;
    }
    AdaptLog.verbose(Form.repeat("  ", ind) + " took " + Form.duration((System.nanoTime() - started) / 1_000_000D, 0));
  }

  /** Multiplier for a material; config keys are legacy constant names ({@code DIAMOND}) or full ids, any case. */
  static double getMultiplier(Id material, Map<String, Double> multipliers) {
    if (material == null || multipliers == null) {
      return 1D;
    }
    String legacyName = material.isMinecraft() ? material.legacyName() : null;
    String fullId = material.toString();
    Double exact = legacyName == null ? null : multipliers.get(legacyName);
    if (exact == null) {
      exact = multipliers.get(fullId);
    }
    if (exact != null) {
      return exact;
    }
    for (Map.Entry<String, Double> entry : multipliers.entrySet()) {
      String key = entry.getKey();
      if (key != null && entry.getValue() != null
          && ((legacyName != null && key.equalsIgnoreCase(legacyName)) || key.equalsIgnoreCase(fullId))) {
        return entry.getValue();
      }
    }
    return 1D;
  }

  static double applyMultiplier(Id material, double resolvedValue, Map<String, Double> multipliers) {
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

  static Map<Id, Double> toConcurrentValueMap(Map<Id, Double> source) {
    Map<Id, Double> concurrent = new ConcurrentHashMap<>();
    if (source == null) {
      return concurrent;
    }
    for (Map.Entry<Id, Double> entry : source.entrySet()) {
      if (entry.getKey() != null && entry.getValue() != null) {
        concurrent.put(entry.getKey(), entry.getValue());
      }
    }
    return concurrent;
  }

  /**
   * Each material is resolved in its own traversal, as if it were the first material valued on a fresh cache, and
   * only that top-level result is stored. Values reached inside a traversal are cut short by the recursion guard, so
   * they are never cached or shared between traversals; the result does not depend on request order.
   */
  public static double getValue(Id m) {
    try {
      MaterialValue cache = get();
      Double stored = cache.value.get(m);
      if (stored == null) {
        Map<Id, Double> traversal = new HashMap<>();
        resolve(m, new HashSet<>(), traversal, new HashMap<>());
        stored = traversal.get(m);
        Double previous = cache.value.putIfAbsent(m, stored);
        if (previous != null) {
          stored = previous;
        }
      }
      return port().breaksInstantly(m) ? 0 : stored;
    } catch (Exception ignored) {
      return 1;
    }
  }

  private static double resolve(Id m, Set<MaterialRecipe> ignore, Map<Id, Double> traversal, Map<Id, List<MaterialRecipe>> recipeLists) {
    Double known = traversal.get(m);
    if (known != null) {
      return port().breaksInstantly(m) ? 0 : known;
    }
    double v = AdaptConfig.get().getValue().getBaseValue();
    List<MaterialRecipe> recipes = recipeLists.computeIfAbsent(m, MaterialValue::getRecipes);
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
          vx += resolve(j.getMaterial(), ignore, traversal, recipeLists) * j.getAmount();
        }
        d.add(vx / i.getOutput().getAmount());
      }
      if (!d.isEmpty()) {
        v += d.stream().mapToDouble((Double i) -> i).average().getAsDouble();
      }
      if (v > AdaptConfig.get().getMaxRecipeListPrecaution()) {
        resolvedValue = v / 10 + 1;
      } else {
        resolvedValue = v;
      }
    }
    double value = applyMultiplier(m, resolvedValue, AdaptConfig.get().getValue().getValueMultipliers());
    traversal.put(m, value);
    return port().breaksInstantly(m) ? 0 : value;
  }

  private static List<MaterialRecipe> getRecipes(Id mat) {
    try {
      List<MaterialRecipe> recipes = port().recipesFor(mat);
      return recipes == null ? List.of() : recipes;
    } catch (Throwable e) {
      AdaptLog.verbose("Failed to get recipes for " + mat);
      return List.of();
    }
  }

  private static MaterialValuePort port() {
    return AdaptPorts.get(MaterialValuePort.class, MaterialValuePort.NONE);
  }

  private static final class CacheFile {
    private Map<String, Double> value;
    private String configurationSignature;

    private static CacheFile of(MaterialValue source) {
      CacheFile file = new CacheFile();
      file.value = new TreeMap<>();
      for (Map.Entry<Id, Double> entry : source.value.entrySet()) {
        file.value.put(entry.getKey().toString(), entry.getValue());
      }
      file.configurationSignature = source.configurationSignature;
      return file;
    }

    private MaterialValue toValue() {
      MaterialValue loaded = new MaterialValue();
      loaded.configurationSignature = configurationSignature;
      if (value != null) {
        for (Map.Entry<String, Double> entry : value.entrySet()) {
          Id id = Id.tryParse(entry.getKey());
          if (id != null && entry.getValue() != null) {
            loaded.value.put(id, entry.getValue());
          }
        }
      }
      return loaded;
    }
  }
}
