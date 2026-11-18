package art.arcane.adapt.api.golem;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class FakeMastery implements GolemMastery.Source {
  final Set<GolemCoreType> unlockedCores = EnumSet.allOf(GolemCoreType.class);
  final List<UUID> asked = new ArrayList<>();
  GolemMastery.Perks perks = GolemMastery.Perks.NONE;
  int maxMasterworks = 1;
  double heartDropChance = 1D;

  @Override
  public GolemMastery.Perks perks(UUID owner) {
    asked.add(owner);
    return perks;
  }

  @Override
  public int maxMasterworks(UUID owner) {
    return maxMasterworks;
  }

  @Override
  public double heartDropChance() {
    return heartDropChance;
  }

  @Override
  public boolean coreUnlocked(UUID owner, GolemCoreType core) {
    return unlockedCores.contains(core);
  }
}
