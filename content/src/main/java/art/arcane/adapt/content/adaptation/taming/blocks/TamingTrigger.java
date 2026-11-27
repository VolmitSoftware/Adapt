package art.arcane.adapt.content.adaptation.taming.blocks;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.content.adaptation.taming.TamePets;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.UUID;

/**
 * Fires when one of the player's pets damages a living entity. Any tamed pet counts, however it was obtained; a hit on
 * a spawner, egg or bred mob, or on another of the owner's pets, is farmed.
 */
public class TamingTrigger extends TriggerBlock<TamingTrigger.Config> {
  public TamingTrigger() {
    super(SkillTaming.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bone"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    PlayerRef owner = owner(e.damager());
    if (owner == null || !(e.entity() instanceof LivingRef target) || target.uuid().equals(owner.uuid())
        || !hasActiveAdaptation(owner)) {
      return;
    }
    fire(owner, target, target.location(), Mutations.farmed(owner, target));
  }

  /** The online owner of {@code pet} when it is a tamed pet, else null. */
  static PlayerRef owner(EntityRef pet) {
    UUID ownerId = pet == null ? null : pet.tameOwner();
    if (ownerId == null || !TamePets.isOwnedPet(pet, ownerId)) {
      return null;
    }
    PlayerRef owner = AdaptApi.platform().host().player(ownerId);
    return owner != null && owner.isOnline() ? owner : null;
  }

  @ConfigDescription("Have a pet land a hit.")
  public static class Config extends MutationBlock.Config {
  }
}
