package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.value.MaterialNames;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A weighted loot table read from config entries formatted as {@code MATERIAL:weight:min:max}. The parsed table is
 * cached until the config list instance changes.
 */
public final class ExcavationLoot {
  private volatile TableCache tableCache;

  /** One drawn entry: the item, how many, and the entry's weight. */
  public record Roll(Id material, int amount, double weight) {
  }

  /** Draws one entry from {@code source}, or null when no entry is valid. */
  public Roll roll(List<String> source, ThreadLocalRandom random) {
    TableCache table = table(source);
    if (table.entries().isEmpty()) {
      return null;
    }

    LootEntry entry = pickWeighted(table, random.nextDouble() * table.totalWeight());
    int amount = entry.min() >= entry.max() ? entry.min() : random.nextInt(entry.min(), entry.max() + 1);
    return new Roll(entry.material(), amount, entry.weight());
  }

  private static LootEntry pickWeighted(TableCache table, double roll) {
    List<LootEntry> entries = table.entries();
    double remaining = roll;
    for (LootEntry entry : entries) {
      remaining -= entry.weight();
      if (remaining <= 0) {
        return entry;
      }
    }

    return entries.get(entries.size() - 1);
  }

  private TableCache table(List<String> source) {
    TableCache local = tableCache;
    if (local != null && local.source() == source) {
      return local;
    }

    List<LootEntry> parsed = new ArrayList<>(source.size());
    double totalWeight = 0;
    for (String raw : source) {
      String[] parts = raw.split(":");
      if (parts.length < 2) {
        continue;
      }

      Id material = MaterialNames.match(parts[0].trim());
      if (material == null || !AdaptCore.platform().host().items().isItem(material)) {
        continue;
      }

      double weight = parseDouble(parts[1]);
      if (weight <= 0) {
        continue;
      }

      int min = parts.length > 2 ? Math.max(1, (int) parseDouble(parts[2])) : 1;
      int max = parts.length > 3 ? Math.max(min, (int) parseDouble(parts[3])) : min;
      parsed.add(new LootEntry(material, weight, min, max));
      totalWeight += weight;
    }

    TableCache built = new TableCache(source, parsed, totalWeight);
    tableCache = built;
    return built;
  }

  private static double parseDouble(String raw) {
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private record LootEntry(Id material, double weight, int min, int max) {
  }

  private record TableCache(List<String> source, List<LootEntry> entries, double totalWeight) {
  }
}
