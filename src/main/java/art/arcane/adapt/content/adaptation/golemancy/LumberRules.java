package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class LumberRules {
  static final double MIN_LOGS_PER_SECOND = 0.05D;
  static final double MAX_LOGS_PER_SECOND = 64D;

  private static final double EPSILON = 1.0E-9D;

  private LumberRules() {
  }

  static Plan plan(GolemLumberCore.Config config, int level) {
    return new Plan(level, logsPerSecond(config.logsPerSecondByLevel, level), GolemancyRules.unlocked(level, config.replantLevel),
        GolemancyRules.unlocked(level, config.leafSweepLevel), GolemancyRules.unlocked(level, config.megaTreeLevel), GolemancyRules.unlocked(level, config.netherLevel),
        Math.max(treeCap(config.copperTreeCapByLevel, level), treeCap(config.ironTreeCapByLevel, level)));
  }

  static double logsPerSecond(List<Double> byLevel, int level) {
    double value = AnimusBindingRules.atLevel(byLevel, Math.max(1, level), 1D);
    return Double.isFinite(value) ? Math.clamp(value, MIN_LOGS_PER_SECOND, MAX_LOGS_PER_SECOND) : MIN_LOGS_PER_SECOND;
  }

  static int logsThisAct(double perSecond, int actIndex) {
    if (actIndex < 1) {
      return 0;
    }
    return (int) Math.floor(actIndex * perSecond + EPSILON) - (int) Math.floor((actIndex - 1) * perSecond + EPSILON);
  }

  static int treeCap(List<Integer> byLevel, int level) {
    return Math.max(1, AnimusBindingRules.atLevel(byLevel, Math.max(1, level), 1));
  }

  static int cap(GolemLumberCore.Config config, GolemBody body, int level) {
    return treeCap(body == GolemBody.IRON ? config.ironTreeCapByLevel : config.copperTreeCapByLevel, level);
  }

  static Set<GolemBody> bodies(GolemLumberCore.Config config, int level, int logs) {
    Set<GolemBody> bodies = EnumSet.noneOf(GolemBody.class);
    for (GolemBody body : GolemBody.values()) {
      if (cap(config, body, level) >= logs) {
        bodies.add(body);
      }
    }
    return bodies;
  }

  record Plan(int level, double logsPerSecond, boolean replant, boolean sweep, boolean mega, boolean nether, int largestCap) {
    LumberTrees.Reach reach(int cap) {
      return new LumberTrees.Reach(mega, nether, cap);
    }
  }
}
