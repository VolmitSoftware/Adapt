package art.arcane.adapt.api.mutation;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * The action a skill reacts to. A subclass listens with {@code @Handle} methods, returns early unless
 * {@code hasActiveAdaptation(player)} (true only while the player holds a mutated adaptation this Trigger sets
 * off), and calls {@link #fire} or {@link #fireKill} once the action happened.
 */
@ApiStatus.Experimental
public abstract non-sealed class TriggerBlock<C extends MutationBlock.Config> extends MutationBlock<C> {
  /**
   * @param skill the skill this block belongs to
   */
  protected TriggerBlock(Id skill) {
    super(skill, BlockSlot.TRIGGER);
  }

  /** Fight, work or either, as the skill's kit declares it. */
  public final TriggerKind getKind() {
    return getKit().triggerKind();
  }

  /**
   * The action happened. Runs every Graft, Echo and Convergence of the player's mutations that this Trigger sets off,
   * each only when the player's active level in it is above 0, all its blocks are usable, its cooldown is over and
   * its States hold. A fight Strike hits {@code target} when it is a living entity other than the player, else the
   * nearest hostile mob in the server's mutation radius; a work Strike centres on {@code at}, else the player. Does
   * nothing while another mutation effect of the player is running.
   *
   * @param farmed true when the action is farmed (a spawner mob, a placed or bone-mealed block); no block that pays
   *               out then runs
   */
  protected final void fire(PlayerRef player, @Nullable EntityRef target, @Nullable Loc at, boolean farmed) {
    if (usable()) {
      Engine.bridge().mutations().fire(this, player, target, at, false, farmed);
    }
  }

  /**
   * The player killed {@code victim}; a farmed or minion mob or the player's own pet pays nothing. A fight Strike then
   * hits the nearest other hostile mob in the server's mutation radius.
   */
  protected final void fireKill(PlayerRef killer, EntityRef victim) {
    if (usable()) {
      Engine.bridge().mutations().fire(this, killer, victim, victim.location(), true,
          Mutations.farmed(killer, victim));
    }
  }
}
