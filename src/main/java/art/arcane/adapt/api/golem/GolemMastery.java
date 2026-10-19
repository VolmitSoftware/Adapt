package art.arcane.adapt.api.golem;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class GolemMastery {
  public static final double DEFAULT_HEART_DROP_CHANCE = 1D;
  private static final AtomicReference<Source> SOURCE = new AtomicReference<>();

  private GolemMastery() {
  }

  public static void register(Source source) {
    SOURCE.set(Objects.requireNonNull(source));
  }

  public static boolean unregister(Source source) {
    return SOURCE.compareAndSet(source, null);
  }

  public static Perks perks(GolemHandle handle) {
    return perks(handle.owner(), handle.state().masterwork());
  }

  public static Perks perks(UUID owner, boolean masterwork) {
    Source current = SOURCE.get();
    if (!masterwork || current == null) {
      return Perks.NONE;
    }
    return Objects.requireNonNullElse(current.perks(owner), Perks.NONE);
  }

  public static List<GolemCoreType> cores(GolemHandle handle) {
    List<GolemCoreType> slotted = handle.state().cores();
    if (slotted.isEmpty()) {
      return List.of(GolemCoreType.BLANK);
    }
    if (slotted.size() == 1 || perks(handle).dualCore()) {
      return slotted;
    }
    return List.of(slotted.getFirst());
  }

  public static int maxMasterworks(UUID owner) {
    Source current = SOURCE.get();
    return current == null ? 0 : Math.max(0, current.maxMasterworks(owner));
  }

  public static double heartDropChance() {
    Source current = SOURCE.get();
    if (current == null) {
      return DEFAULT_HEART_DROP_CHANCE;
    }
    double chance = current.heartDropChance();
    return Double.isFinite(chance) ? Math.clamp(chance, 0D, 1D) : 0D;
  }

  public static boolean coreUnlocked(UUID owner, GolemCoreType core) {
    Source current = SOURCE.get();
    return current != null && current.coreUnlocked(owner, core);
  }

  public static Optional<CoreRefusal> coreRefusal(List<GolemCoreType> cores, GolemCoreType candidate, boolean dualCore, boolean allowed,
                                                  boolean unlocked) {
    if (!dualCore || cores.size() >= GolemState.MAX_CORES) {
      return Optional.of(CoreRefusal.NO_SLOT);
    }
    if (candidate == GolemCoreType.BLANK) {
      return Optional.of(CoreRefusal.BLANK);
    }
    if (cores.contains(candidate)) {
      return Optional.of(CoreRefusal.DUPLICATE);
    }
    if (!allowed) {
      return Optional.of(CoreRefusal.NOT_ALLOWED);
    }
    return unlocked ? Optional.empty() : Optional.of(CoreRefusal.LOCKED);
  }

  public enum CoreRefusal {
    UNAVAILABLE,
    NO_SLOT,
    BLANK,
    DUPLICATE,
    NOT_ALLOWED,
    LOCKED
  }

  public interface Source {
    Perks perks(UUID owner);

    int maxMasterworks(UUID owner);

    double heartDropChance();

    boolean coreUnlocked(UUID owner, GolemCoreType core);
  }

  public record Perks(int extraRuneSlots, boolean dualCore, boolean tierUp, double tierThreeBoost) {
    public static final Perks NONE = new Perks(0, false, false, 0D);

    public Perks {
      extraRuneSlots = Math.clamp(extraRuneSlots, 0, GolemRuneLayout.MAX_SLOTS);
      tierThreeBoost = Double.isFinite(tierThreeBoost) ? Math.max(0D, tierThreeBoost) : 0D;
    }
  }
}
