package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.blocks.TriggerKind;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.Nullable;

/**
 * The action a skill reacts to. A subclass listens with {@code @Handle} methods, returns early unless
 * {@code hasActiveAdaptation(player)} (true only while the player holds a mutated adaptation this Trigger sets off),
 * and calls {@link #fire} or {@link #fireKill} once the action happened. Fight, work or either comes from the catalog.
 */
public abstract class TriggerBlock<C extends MutationBlock.Config> extends MutationBlock<C> {
  protected TriggerBlock(String id) {
    super(id, BlockSlot.TRIGGER);
  }

  public TriggerKind getKind() {
    return SkillBlockCatalog.require(getSkillId()).triggerKind();
  }

  /** The action happened; {@code farmed} stops every block that pays out. */
  protected final void fire(PlayerRef player, @Nullable EntityRef target, @Nullable Loc at, boolean farmed) {
    if (usable()) {
      MutationEngine.current().fire(this, new TriggerEvent(player, target, at, false, farmed));
    }
  }

  /** The player killed {@code victim}; a farmed or minion mob or the player's own pet pays nothing. */
  protected final void fireKill(PlayerRef killer, EntityRef victim) {
    if (usable()) {
      MutationEngine.current().fire(this, new TriggerEvent(killer, victim, victim.location(), true,
          MutationPayouts.farmed(killer, victim)));
    }
  }
}
