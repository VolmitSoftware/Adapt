package art.arcane.adapt.api.golem.item;

import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemRune;
import org.bukkit.Material;

import java.util.List;
import java.util.Objects;

public record DormantGolem(PackedGolem packed, Material block, List<GolemCoreType> cores, List<GolemRune> runes, double health, double maxHealth) {
  public DormantGolem {
    Objects.requireNonNull(packed);
    if (!GolemItems.isDormantBlock(block)) {
      throw new IllegalArgumentException("Dormant golem block must be snow, copper or iron, found " + block);
    }
    cores = List.copyOf(cores);
    runes = List.copyOf(runes);
  }
}
