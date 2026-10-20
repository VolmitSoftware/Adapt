package art.arcane.adapt.api.golem;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record GolemSettings(
    boolean dormantInvulnerable,
    boolean guardWhileOwnerOffline,
    int serverMaxActiveGolems,
    int maxBoundGolemsPerChunk,
    int hardCapPerPlayer,
    Map<GolemBody, Double> bodyScales,
    Map<GolemBody, Integer> bodyCarry,
    Map<GolemBody, Set<GolemCoreType>> allowedCores,
    double deathDropCoreChance,
    double deathDropRuneChance) {
  private static final Set<GolemCoreType> BONE_CORES = Set.of(GolemCoreType.BLANK, GolemCoreType.GUARDIAN);
  private static final GolemSettings DEFAULTS = new GolemSettings(
      true,
      false,
      1500,
      8,
      32,
      Map.of(GolemBody.FROST, 0.50D, GolemBody.COPPER, 1.00D, GolemBody.IRON, 0.36D, GolemBody.BONE, 0.49D),
      Map.of(GolemBody.FROST, 8, GolemBody.COPPER, 16, GolemBody.IRON, 64),
      Map.of(
          GolemBody.FROST, EnumSet.of(GolemCoreType.BLANK, GolemCoreType.GUARDIAN, GolemCoreType.GATHERER, GolemCoreType.HARVESTER),
          GolemBody.COPPER, EnumSet.of(GolemCoreType.BLANK, GolemCoreType.GATHERER, GolemCoreType.HARVESTER, GolemCoreType.LUMBER,
              GolemCoreType.RANCHER, GolemCoreType.SORTER, GolemCoreType.ANGLER),
          GolemBody.IRON, EnumSet.of(GolemCoreType.BLANK, GolemCoreType.GUARDIAN, GolemCoreType.GATHERER, GolemCoreType.LUMBER,
              GolemCoreType.BREAKER, GolemCoreType.RANCHER, GolemCoreType.ANGLER)),
      0.5D,
      0.5D);

  public GolemSettings {
    serverMaxActiveGolems = Math.max(0, serverMaxActiveGolems);
    maxBoundGolemsPerChunk = Math.max(0, maxBoundGolemsPerChunk);
    hardCapPerPlayer = Math.max(0, hardCapPerPlayer);
    bodyScales = normalizedScales(bodyScales);
    bodyCarry = normalizedCarry(bodyCarry);
    allowedCores = normalizedCores(allowedCores);
    deathDropCoreChance = clampChance(deathDropCoreChance);
    deathDropRuneChance = clampChance(deathDropRuneChance);
  }

  public static GolemSettings defaults() {
    return DEFAULTS;
  }

  public static Set<GolemCoreType> parseCores(List<String> names) {
    Set<GolemCoreType> cores = EnumSet.noneOf(GolemCoreType.class);
    if (names == null) {
      return cores;
    }
    for (String name : names) {
      if (name != null) {
        GolemCoreType.fromId(name.trim().toLowerCase(Locale.ROOT)).ifPresent(cores::add);
      }
    }
    return cores;
  }

  public double bodyScale(GolemBody body) {
    return bodyScales.get(body);
  }

  public int bodyCarry(GolemBody body) {
    return bodyCarry.get(body);
  }

  public Set<GolemCoreType> allowedCores(GolemBody body) {
    return allowedCores.get(body);
  }

  public boolean allows(GolemBody body, GolemCoreType core) {
    return allowedCores(body).contains(core);
  }

  private static Map<GolemBody, Double> normalizedScales(Map<GolemBody, Double> source) {
    Map<GolemBody, Double> scales = new EnumMap<>(GolemBody.class);
    for (GolemBody body : GolemBody.values()) {
      Double configured = source == null ? null : source.get(body);
      scales.put(body, GolemScale.clampScale(configured == null ? 1D : configured));
    }
    return Map.copyOf(scales);
  }

  private static Map<GolemBody, Integer> normalizedCarry(Map<GolemBody, Integer> source) {
    Map<GolemBody, Integer> carry = new EnumMap<>(GolemBody.class);
    for (GolemBody body : GolemBody.values()) {
      Integer configured = source == null || body == GolemBody.BONE ? null : source.get(body);
      carry.put(body, configured == null ? 0 : Math.max(0, configured));
    }
    return Map.copyOf(carry);
  }

  private static Map<GolemBody, Set<GolemCoreType>> normalizedCores(Map<GolemBody, Set<GolemCoreType>> source) {
    Map<GolemBody, Set<GolemCoreType>> cores = new EnumMap<>(GolemBody.class);
    for (GolemBody body : GolemBody.values()) {
      Set<GolemCoreType> configured = source == null ? null : source.get(body);
      Set<GolemCoreType> allowed = body == GolemBody.BONE ? BONE_CORES : configured == null ? Set.of() : configured;
      cores.put(body, Set.copyOf(allowed));
    }
    return Map.copyOf(cores);
  }

  private static double clampChance(double chance) {
    return Double.isFinite(chance) ? Math.max(0D, Math.min(1D, chance)) : 0D;
  }
}
