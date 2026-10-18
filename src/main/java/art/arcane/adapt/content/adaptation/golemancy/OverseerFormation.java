package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemFormation;
import art.arcane.adapt.api.golem.GolemHandle;
import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

final class OverseerFormation implements GolemFormation.Formation {
  private final Map<UUID, List<UUID>> rings = new ConcurrentHashMap<>();
  private final DoubleSupplier ringRadius;

  OverseerFormation(DoubleSupplier ringRadius) {
    this.ringRadius = Objects.requireNonNull(ringRadius);
  }

  void update(UUID owner, Collection<UUID> members) {
    rings.compute(owner, (key, previous) -> {
      List<UUID> ring = OverseerRules.ring(previous == null ? List.of() : previous, members);
      return ring.isEmpty() ? null : ring;
    });
  }

  void forget(UUID owner) {
    rings.remove(owner);
  }

  void retain(Set<UUID> owners) {
    rings.keySet().retainAll(owners);
  }

  void clear() {
    rings.clear();
  }

  @Override
  public Location slot(GolemHandle handle, Location ownerAt) {
    List<UUID> ring = rings.get(handle.owner());
    int index = ring == null ? -1 : ring.indexOf(handle.id());
    if (index < 0) {
      return null;
    }
    OverseerRules.Offset offset = OverseerRules.slot(index, ring.size(), ringRadius.getAsDouble());
    return ownerAt.clone().add(offset.x(), 0D, offset.z());
  }
}
