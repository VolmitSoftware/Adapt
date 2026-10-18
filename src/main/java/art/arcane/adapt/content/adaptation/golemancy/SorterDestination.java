package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.block.Block;

import java.util.Objects;

record SorterDestination(ContainerId id, Block block, SorterStock stock, Kind kind, NodeRecord filter, double distanceSquared, boolean shulkerBox) {
  SorterDestination {
    Objects.requireNonNull(id);
    Objects.requireNonNull(block);
    Objects.requireNonNull(stock);
    Objects.requireNonNull(kind);
    if ((kind == Kind.FILTER || kind == Kind.OVERFLOW) != (filter != null)) {
      throw new IllegalArgumentException("Filter destinations need their node record and other containers have none, got " + kind);
    }
  }

  int priority() {
    return filter == null ? 0 : filter.priority();
  }

  enum Kind {
    INPUT,
    PLAIN,
    FILTER,
    OVERFLOW
  }
}
