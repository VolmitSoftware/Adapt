package art.arcane.adapt.content.adaptation.taming.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/** Holds for a few seconds after one of the player's pets hits a living entity or is hit by one other than them. */
public class TamingState extends StateBlock<TamingState.Config> {
  private final Map<UUID, Long> lastFight = playerState();

  public TamingState() {
    super(SkillTaming.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("wolf_spawn_egg"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    PlayerRef attacking = TamingTrigger.owner(e.damager());
    if (attacking != null && e.entity() instanceof LivingRef && !e.entity().uuid().equals(attacking.uuid())) {
      mark(attacking);
    }
    PlayerRef defending = TamingTrigger.owner(e.entity());
    if (defending != null && e.damager() != null && !e.damager().uuid().equals(defending.uuid())) {
      mark(defending);
    }
  }

  private void mark(PlayerRef owner) {
    if (hasActiveAdaptation(owner)) {
      lastFight.put(owner.uuid(), System.currentTimeMillis());
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    Long fought = lastFight.get(player.uuid());
    return fought != null && System.currentTimeMillis() - fought <= getConfig().windowMillis;
  }

  @ConfigDescription("A pet of yours is fighting.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long after a pet's last hit given or taken it still counts as fighting, in milliseconds.", impact = "Higher values keep the State longer.")
    long windowMillis = 5_000L;
  }
}
