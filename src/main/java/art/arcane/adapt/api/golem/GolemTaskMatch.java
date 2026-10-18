package art.arcane.adapt.api.golem;

import org.bukkit.DyeColor;

import java.util.List;
import java.util.Optional;

public final class GolemTaskMatch {
  private GolemTaskMatch() {
  }

  public static boolean channel(DyeColor nodeChannel, int golemChannels) {
    if (nodeChannel == null) {
      return true;
    }
    return !ChannelMask.isNone(golemChannels) && ChannelMask.contains(golemChannels, nodeChannel);
  }

  public static boolean core(NodeType node, List<GolemCoreType> cores) {
    for (GolemCoreType core : cores) {
      if (consumes(node, core)) {
        return true;
      }
    }
    return false;
  }

  public static Optional<GolemCoreType> consumer(NodeType node, List<GolemCoreType> cores) {
    for (GolemCoreType core : cores) {
      if (consumes(node, core)) {
        return Optional.of(core);
      }
    }
    return Optional.empty();
  }

  private static boolean consumes(NodeType node, GolemCoreType core) {
    return switch (node) {
      case GUARD -> core == GolemCoreType.GUARDIAN;
      case COLLECTION -> core == GolemCoreType.GATHERER;
      case HARVEST -> core == GolemCoreType.HARVESTER;
      case LUMBER -> core == GolemCoreType.LUMBER;
      case BREAKER -> core == GolemCoreType.BREAKER;
      case PASTURE -> core == GolemCoreType.RANCHER;
      case INPUT, FILTER -> core == GolemCoreType.SORTER;
      case FISHING -> core == GolemCoreType.ANGLER;
      case DEPOSIT -> false;
    };
  }
}
