package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

final class AnglerLoot {
  static final List<String> DEFAULT_FISH = List.of("COD:60", "SALMON:25", "TROPICAL_FISH:2", "PUFFERFISH:13");
  static final List<String> DEFAULT_JUNK = List.of("LILY_PAD:17", "LEATHER_BOOTS:10", "LEATHER:10", "BONE:10", "STRING:5", "FISHING_ROD:2",
      "BOWL:10", "STICK:5", "INK_SAC:1:10:10", "TRIPWIRE_HOOK:10", "ROTTEN_FLESH:10");
  static final List<String> DEFAULT_TREASURE = List.of("NAME_TAG:1", "SADDLE:1", "BOW:1", "FISHING_ROD:1", "ENCHANTED_BOOK:1", "NAUTILUS_SHELL:1");

  private static final int MAX_AMOUNT = 64;
  private static final String SEPARATOR = ":";

  private AnglerLoot() {
  }

  static Table parse(List<String> raw, Predicate<Material> item, Consumer<String> rejected) {
    if (raw == null) {
      return Table.of(List.of());
    }
    List<Entry> entries = new ArrayList<>(raw.size());
    for (String line : raw) {
      Entry entry = entry(line, item);
      if (entry == null) {
        rejected.accept(String.valueOf(line));
        continue;
      }
      entries.add(entry);
    }
    return Table.of(entries);
  }

  static Optional<Catch> roll(Tables tables, Weights weights, int luck, DoubleSupplier random) {
    int fish = tables.fish().isEmpty() ? 0 : weights.fish().at(luck);
    int junk = tables.junk().isEmpty() ? 0 : weights.junk().at(luck);
    int treasure = tables.treasure().isEmpty() ? 0 : weights.treasure().at(luck);
    int total = fish + junk + treasure;
    if (total <= 0) {
      return Optional.empty();
    }
    double pick = random.getAsDouble() * total;
    if (pick < fish) {
      return Optional.of(pick(Category.FISH, tables.fish(), random));
    }
    return Optional.of(pick < fish + junk ? pick(Category.JUNK, tables.junk(), random) : pick(Category.TREASURE, tables.treasure(), random));
  }

  static Material base(Catch caught) {
    return caught.category() == Category.TREASURE && caught.type() == Material.ENCHANTED_BOOK ? Material.BOOK : caught.type();
  }

  static boolean enchanted(Catch caught, int maxDurability) {
    return caught.category() == Category.TREASURE && (caught.type() == Material.ENCHANTED_BOOK || maxDurability > 0);
  }

  private static Catch pick(Category category, Table table, DoubleSupplier random) {
    double pick = random.getAsDouble() * table.totalWeight();
    List<Entry> entries = table.entries();
    Entry chosen = entries.getLast();
    int reached = 0;
    for (Entry entry : entries) {
      reached += entry.weight();
      if (pick < reached) {
        chosen = entry;
        break;
      }
    }
    int amount = chosen.max() > chosen.min()
        ? Math.min(chosen.max(), chosen.min() + (int) Math.floor(random.getAsDouble() * (chosen.max() - chosen.min() + 1)))
        : chosen.min();
    return new Catch(category, chosen.type(), amount);
  }

  private static Entry entry(String line, Predicate<Material> item) {
    if (line == null) {
      return null;
    }
    String[] parts = line.trim().split(SEPARATOR);
    if (parts.length < 2 || parts.length > 4) {
      return null;
    }
    Material type = Material.matchMaterial(parts[0].trim());
    Integer weight = number(parts[1]);
    if (type == null || weight == null || weight <= 0 || !item.test(type)) {
      return null;
    }
    Integer min = parts.length > 2 ? number(parts[2]) : Integer.valueOf(1);
    Integer max = parts.length > 3 ? number(parts[3]) : min;
    if (min == null || max == null) {
      return null;
    }
    int low = Math.clamp(min, 1, MAX_AMOUNT);
    int high = Math.clamp(Math.max(low, max), 1, MAX_AMOUNT);
    return new Entry(type, weight, low, high);
  }

  private static Integer number(String raw) {
    try {
      return Integer.valueOf(raw.trim());
    } catch (NumberFormatException invalid) {
      return null;
    }
  }

  enum Category {
    FISH,
    JUNK,
    TREASURE
  }

  record Entry(Material type, int weight, int min, int max) {
  }

  record Table(List<Entry> entries, int totalWeight) {
    static Table of(List<Entry> entries) {
      int total = 0;
      for (Entry entry : entries) {
        total += entry.weight();
      }
      return new Table(List.copyOf(entries), total);
    }

    boolean isEmpty() {
      return entries.isEmpty();
    }
  }

  record Tables(Table fish, Table junk, Table treasure) {
  }

  record Weight(int base, int luckShift) {
    int at(int luck) {
      return Math.max(0, base + luckShift * luck);
    }
  }

  record Weights(Weight fish, Weight junk, Weight treasure) {
  }

  record Catch(Category category, Material type, int amount) {
  }
}
