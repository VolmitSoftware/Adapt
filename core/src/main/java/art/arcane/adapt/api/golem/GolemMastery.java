package art.arcane.adapt.api.golem;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class GolemMastery {
  public static final double DEFAULT_HEART_DROP_CHANCE = 1D;
  private static final AtomicReference<Source> SOURCE = new AtomicReference<>();
  private static final AtomicReference<Twins> TWINS = new AtomicReference<>();

  private GolemMastery() {
  }

  public static void register(Source source) {
    SOURCE.set(Objects.requireNonNull(source));
  }

  public static boolean unregister(Source source) {
    return SOURCE.compareAndSet(source, null);
  }

  public static void registerTwins(Twins twins) {
    TWINS.set(Objects.requireNonNull(twins));
  }

  public static boolean unregisterTwins(Twins twins) {
    return TWINS.compareAndSet(twins, null);
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
    if (slotted.size() == 1 || perks(handle).dualCore() || twinned(handle)) {
      return slotted;
    }
    return List.of(slotted.getFirst());
  }

  /** Whether the golem may take a second core: a masterwork's own second core, or a twin core its owner can still fit. */
  public static boolean secondCoreOpen(GolemHandle handle) {
    return perks(handle).dualCore() || twinOpen(handle);
  }

  /**
   * Whether the golem runs its second core as a twin core, outside a masterwork's own second core. Twins past the
   * owner's current count, by {@link GolemRegistry#twinRank}, keep their second core but leave it idle.
   */
  public static boolean twinned(GolemHandle handle) {
    Twins twins = TWINS.get();
    if (twins == null || handle.state().cores().size() <= 1) {
      return false;
    }
    int rank = GolemancyRuntime.registry().map((GolemRegistry registry) -> registry.twinRank(handle)).orElse(-1);
    return rank >= 0 && rank < twins.maxTwins(handle.owner(), handle);
  }

  /** Whether a twin core can go into the golem now: its owner holds twin cores and has a twin left, or it is one already. */
  public static boolean twinOpen(GolemHandle handle) {
    return TWINS.get() != null && GolemancyRuntime.registry().map((GolemRegistry registry) -> registry.twinOpen(handle))
        .orElse(false);
  }

  /** Most twin-cored golems {@code owner} may have now; 0 when they hold no twin cores. */
  public static int maxTwins(UUID owner, GolemHandle handle) {
    Twins twins = TWINS.get();
    return twins == null ? 0 : Math.max(0, twins.maxTwins(owner, handle));
  }

  /** Whether one more twin core, with {@code twins} twin-cored golems already, costs a Masterwork Heart. */
  public static boolean twinNeedsHeart(int twins) {
    return twins >= freeTwins();
  }

  /** Twin-cored golems an owner may have before each further one costs a Masterwork Heart; 0 without twin cores. */
  public static int freeTwins() {
    Twins current = TWINS.get();
    return current == null ? 0 : Math.max(0, current.freeTwins());
  }

  /** The work speed of {@code core} in the golem: the twin speed for a twin golem's second core, 1 otherwise. */
  public static double coreSpeed(GolemHandle handle, GolemCoreType core) {
    Twins twins = TWINS.get();
    List<GolemCoreType> slotted = handle.state().cores();
    if (twins == null || slotted.size() < 2 || core != slotted.get(1) || perks(handle).dualCore() || !twinned(handle)) {
      return 1D;
    }
    double speed = twins.secondCoreSpeed();
    return Double.isFinite(speed) && speed > 0D ? Math.min(1D, speed) : 1D;
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
    LOCKED,
    HEART
  }

  public interface Source {
    Perks perks(UUID owner);

    int maxMasterworks(UUID owner);

    double heartDropChance();

    boolean coreUnlocked(UUID owner, GolemCoreType core);
  }

  /** Twin cores: any golem of an owner who holds them may run a second core, up to a count, at a slower pace. */
  public interface Twins {
    /** Most twin-cored golems {@code owner} may have, or {@link Integer#MAX_VALUE}; 0 when they hold none. */
    int maxTwins(UUID owner, GolemHandle handle);

    /** Twin-cored golems an owner may have before each further one costs a Masterwork Heart. */
    int freeTwins();

    /** Work speed of a twin golem's second core, above 0 and at most 1. */
    double secondCoreSpeed();
  }

  public record Perks(int extraRuneSlots, boolean dualCore, boolean tierUp, double tierThreeBoost) {
    public static final Perks NONE = new Perks(0, false, false, 0D);

    public Perks {
      extraRuneSlots = Math.clamp(extraRuneSlots, 0, GolemRuneLayout.MAX_SLOTS);
      tierThreeBoost = Double.isFinite(tierThreeBoost) ? Math.max(0D, tierThreeBoost) : 0D;
    }
  }
}
