package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class AnglerRules {
  static final String FISH = "fish";
  static final String JUNK_KEY = "discardJunk";

  private static final String ON = "true";
  private static final double TICKS_PER_SECOND = 20D;
  private static final double MIN_RAIN_MULTIPLIER = 0.05D;
  private static final int MAX_ENCHANT_LEVELS = 30;

  private AnglerRules() {
  }

  static boolean unlocked(int level, int required) {
    return level >= 1 && level >= required;
  }

  static int catchSeconds(List<Integer> byLevel, int level) {
    return Math.max(1, AnimusBindingRules.atLevel(byLevel, Math.max(1, level), 1));
  }

  static int luck(List<Integer> byLevel, int level) {
    return Math.max(0, AnimusBindingRules.atLevel(byLevel, Math.max(1, level), 0));
  }

  static int intervalTicks(int seconds, double multiplier) {
    return (int) Math.max(1L, Math.round(seconds * TICKS_PER_SECOND * multiplier));
  }

  static int seats(int maxAnglersPerChunk) {
    return Math.clamp(maxAnglersPerChunk, 1, AnglerWater.MAX_SEATS);
  }

  static int seatOf(GolemTask task, NodeKey key, int seats) {
    GolemTarget target = task.target();
    for (int index = 0; index < seats; index++) {
      int[] seat = AnglerWater.seat(key, index);
      if (target.blockX() == seat[0] && target.blockY() == seat[1] && target.blockZ() == seat[2]) {
        return index;
      }
    }
    return -1;
  }

  static int fishers(List<GolemTask> tasks, UUID except, long nowMillis) {
    int fishers = 0;
    for (GolemTask task : tasks) {
      if (!FISH.equals(task.type()) || task.nodeType().orElse(null) != NodeType.FISHING) {
        continue;
      }
      Optional<UUID> holder = task.holder(nowMillis);
      if (holder.isPresent() && !holder.get().equals(except)) {
        fishers++;
      }
    }
    return fishers;
  }

  static int openSeats(List<GolemTask> tasks, long nowMillis) {
    int open = 0;
    for (GolemTask task : tasks) {
      if (FISH.equals(task.type()) && task.nodeType().orElse(null) == NodeType.FISHING && task.holder(nowMillis).isEmpty()) {
        open++;
      }
    }
    return open;
  }

  static boolean discardJunk(GolemAnglerCore.Config config, int level, NodeRecord node) {
    return unlocked(level, config.junkFilterLevel) && ON.equals(node.data().get(JUNK_KEY));
  }

  static NodeRecord toggleJunk(NodeRecord node) {
    Map<String, String> data = new LinkedHashMap<>(node.data());
    if (ON.equals(data.get(JUNK_KEY))) {
      data.remove(JUNK_KEY);
    } else {
      data.put(JUNK_KEY, ON);
    }
    return node.toBuilder().data(data).build();
  }

  static AnglerLoot.Weights weights(GolemAnglerCore.Config config) {
    return new AnglerLoot.Weights(new AnglerLoot.Weight(config.fishWeight, config.fishLuckShift),
        new AnglerLoot.Weight(config.junkWeight, config.junkLuckShift), new AnglerLoot.Weight(config.treasureWeight, config.treasureLuckShift));
  }

  static double rainMultiplier(double value) {
    return Double.isFinite(value) ? Math.clamp(value, MIN_RAIN_MULTIPLIER, 1D) : 1D;
  }

  static double fraction(double value) {
    return Double.isFinite(value) ? Math.clamp(value, 0D, 1D) : 0D;
  }

  static double nonNegative(double value) {
    return Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }

  static int enchantLevels(int value) {
    return Math.clamp(value, 1, MAX_ENCHANT_LEVELS);
  }

  static int atLeastOne(int value) {
    return Math.max(1, value);
  }
}
