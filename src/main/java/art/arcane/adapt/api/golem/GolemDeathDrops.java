package art.arcane.adapt.api.golem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

public final class GolemDeathDrops {
  private GolemDeathDrops() {
  }

  public static Roll roll(List<GolemCoreType> cores, List<GolemRune> runes, double coreChance, double runeChance, DoubleSupplier random) {
    List<GolemCoreType> droppedCores = new ArrayList<>(cores.size());
    for (GolemCoreType core : cores) {
      if (random.getAsDouble() < coreChance) {
        droppedCores.add(core);
      }
    }
    List<GolemRune> droppedRunes = new ArrayList<>(runes.size());
    for (GolemRune rune : runes) {
      if (random.getAsDouble() < runeChance) {
        droppedRunes.add(rune);
      }
    }
    return new Roll(List.copyOf(droppedCores), List.copyOf(droppedRunes));
  }

  public record Roll(List<GolemCoreType> cores, List<GolemRune> runes) {
  }
}
