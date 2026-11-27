package art.arcane.adapt.content.adaptation.axe.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;

/**
 * Fires on a fully charged melee hit with an axe. The platform does not expose the attack charge, so a hit counts as
 * charged when its damage reaches the share of the attacker's attack damage that only a near-full swing deals.
 */
public class AxesTrigger extends TriggerBlock<AxesTrigger.Config> {
  public AxesTrigger() {
    super(SkillAxes.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_axe"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, ItemKinds::isAxe);
    if (combat == null) {
      return;
    }
    double attackDamage = combat.attacker().attributeValue(Attributes.ATTACK_DAMAGE);
    if (!(attackDamage > 0D) || e.damage() < attackDamage * getConfig().chargedDamageRatio) {
      return;
    }
    fire(combat.attacker(), combat.target(), combat.target().location(),
        Mutations.farmed(combat.attacker(), combat.target()));
  }

  @ConfigDescription("Land a fully charged axe hit.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Share of your attack damage a hit must deal to count as fully charged.", impact = "Lower values accept hits swung before the axe has fully recharged.")
    double chargedDamageRatio = 0.95D;
  }
}
