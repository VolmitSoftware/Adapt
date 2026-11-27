package art.arcane.adapt.content.adaptation.unarmed.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.content.skill.SkillUnarmed;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/**
 * Holds while both hands are empty and the player hit or was hit by a living thing in the last few seconds. Asked for
 * a recent window, it answers whether the player landed a bare-hand hit within it.
 */
public class UnarmedState extends StateBlock<UnarmedState.Config> {
  private final Map<UUID, Long> lastCombat = playerState();
  private final Map<UUID, Long> lastBareHit = playerState();

  public UnarmedState() {
    super(SkillUnarmed.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("rabbit_foot"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    long now = System.currentTimeMillis();
    MeleeContext combat = resolveMeleeContext(e);
    if (combat != null) {
      PlayerRef attacker = combat.attacker();
      lastCombat.put(attacker.uuid(), now);
      if (!ItemKinds.isItem(attacker.inventory().mainHand())) {
        lastBareHit.put(attacker.uuid(), now);
      }
    }
    EntityRef source = e.causingEntity();
    if (e.entity() instanceof PlayerRef victim && source instanceof LivingRef && !source.uuid().equals(victim.uuid())
        && hasActiveAdaptation(victim)) {
      lastCombat.put(victim.uuid(), now);
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    Long fought = lastCombat.get(player.uuid());
    return fought != null && System.currentTimeMillis() - fought <= getConfig().combatMillis
        && !ItemKinds.isItem(player.inventory().mainHand()) && !ItemKinds.isItem(player.inventory().offHand());
  }

  @Override
  public boolean heldWithin(PlayerRef player, long windowMillis) {
    Long hit = lastBareHit.get(player.uuid());
    return hit != null && System.currentTimeMillis() - hit <= windowMillis;
  }

  @ConfigDescription("Your hands are empty and you are in combat.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long after hitting or being hit you count as in combat, in milliseconds.", impact = "Higher values keep the State through longer lulls.")
    long combatMillis = 6_000L;
  }
}
