package art.arcane.adapt.api.golem;

import java.util.Optional;

/**
 * Golem-side work source for one core, asked for work when the golem's world board has none for it.
 * <p>
 * An act that carries items from a target entity must remove that entity, or shrink its stack by the amount carried,
 * in the same act, so no other golem, hopper or player can take the same items.
 */
public interface GolemCoreBehavior extends GolemActor {
  /**
   * Finds one private job for this golem, at most once per second per core, or returns empty.
   * <p>
   * Return only targets within the work radius of the golem's home ({@link GolemContext#inWorkRadius},
   * {@link GolemContext#home()}, {@link GolemContext#workRadius()}), and pass over targets for which
   * {@link GolemContext#skips} is true: those this golem backs off and those another golem has reserved. Before the
   * golem walks, the worker reserves the task's key on the world board for {@link GolemTaskBoard#RESERVATION_MILLIS},
   * renewed while the golem pursues it and released when the job ends. A target outside the work radius, backed off
   * or reserved by another golem is ignored. A FAILED act backs the key off for 10 seconds.
   */
  Optional<GolemTask> seek(GolemContext golem);
}
