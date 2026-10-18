package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class GolemRunes {
  private static final AtomicReference<Augments> AUGMENTS = new AtomicReference<>();
  private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

  private GolemRunes() {
  }

  public static void register(Augments augments) {
    AUGMENTS.set(Objects.requireNonNull(augments));
    FAILURE_LOGGED.set(false);
  }

  public static boolean unregister(Augments augments) {
    return AUGMENTS.compareAndSet(augments, null);
  }

  public static Optional<Augments> augments() {
    return Optional.ofNullable(AUGMENTS.get());
  }

  public static GolemRuneEffects effects(GolemHandle handle) {
    Augments current = AUGMENTS.get();
    if (current == null) {
      return GolemRuneEffects.NONE;
    }
    try {
      return Objects.requireNonNull(current.effects(handle), "Runic Augments returned no rune effects");
    } catch (RuntimeException failure) {
      if (FAILURE_LOGGED.compareAndSet(false, true)) {
        Adapt.warn("Rune effects failed for bound golem " + handle.id() + "; its runes are treated as inert.", failure);
      }
      return GolemRuneEffects.NONE;
    }
  }

  public static GolemRuneLayout layout(GolemBody body, UUID owner) {
    Augments current = AUGMENTS.get();
    return current == null ? GolemRuneLayout.NONE : current.layout(body, owner);
  }

  public static int channels(GolemHandle handle) {
    return effects(handle).channels(handle.state().channels());
  }

  public interface Augments {
    GolemRuneLayout layout(GolemBody body, UUID owner);

    GolemRuneEffects effects(GolemHandle handle);
  }
}
