package art.arcane.adapt.content.adaptation.unarmed.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedCombo;
import art.arcane.adapt.content.skill.SkillUnarmed;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;

/**
 * Counts bare-hand hits in a row with Combo Chain's combo tracker and fires on every third; a hit with an item or a
 * missed swing starts the count over.
 */
public class UnarmedTrigger extends TriggerBlock<UnarmedTrigger.Config> {
  private final UnarmedCombo combo = new UnarmedCombo(this, playerState());

  public UnarmedTrigger() {
    super(SkillUnarmed.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("leather"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e);
    if (combat == null) {
      return;
    }
    PlayerRef player = combat.attacker();
    if (ItemKinds.isItem(player.inventory().mainHand())) {
      combo.spend(player);
      return;
    }
    int hits = getConfig().hitsInARow;
    if (combo.build(player, hits, getConfig().comboWindowMillis, System.currentTimeMillis()) < hits) {
      return;
    }
    combo.spend(player);
    fire(player, combat.target(), combat.target().location(), Mutations.farmed(player, combat.target()));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    PlayerRef player = e.player();
    if (e.action().isLeftClick() && hasActiveAdaptation(player)) {
      combo.missed(player, getConfig().missGraceMillis, System.currentTimeMillis());
    }
  }

  @ConfigDescription("Land a third bare-hand hit in a row.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Bare-hand hits in a row that set the Trigger off.", impact = "Higher values need a longer combo.")
    int hitsInARow = 3;
    @ConfigDoc(value = "Longest gap between two hits of the same combo, in milliseconds.", impact = "Higher values forgive slower punching.")
    long comboWindowMillis = 2_000L;
    @ConfigDoc(value = "How long after a hit a missed swing is still forgiven, in milliseconds.", impact = "Higher values forgive more stray swings.")
    long missGraceMillis = 280L;
  }
}
