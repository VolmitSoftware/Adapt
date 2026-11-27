package art.arcane.adapt.content.golem.item;

import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemRune;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Objects;

public record DormantGolem(PackedGolem packed, Id block, List<GolemCoreType> cores, List<GolemRune> runes, double health, double maxHealth) {
  public DormantGolem {
    Objects.requireNonNull(packed);
    if (!GolemItems.isDormantBlock(block)) {
      throw new IllegalArgumentException("Dormant golem block must be snow, copper, iron or bone, found " + block);
    }
    cores = List.copyOf(cores);
    runes = List.copyOf(runes);
  }
}
