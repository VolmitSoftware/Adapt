package art.arcane.adapt.content.adaptation.sword.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/** Holds for a few seconds after a melee hit with a sword. */
public class SwordsState extends StateBlock<SwordsState.Config> {
  private final Map<UUID, Long> lastHit = playerState();

  public SwordsState() {
    super(SkillSwords.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("stone_sword"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, ItemKinds::isSword);
    if (combat != null) {
      lastHit.put(combat.attacker().uuid(), System.currentTimeMillis());
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    return heldWithin(player, 0L);
  }

  @Override
  public boolean heldWithin(PlayerRef player, long windowMillis) {
    Long hit = lastHit.get(player.uuid());
    return hit != null && System.currentTimeMillis() - hit <= getConfig().windowMillis + windowMillis;
  }

  @ConfigDescription("You hit an enemy with a sword in the last 4 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long after a sword hit the State holds, in milliseconds.", impact = "Higher values keep the State longer.")
    long windowMillis = 4_000L;
  }
}
