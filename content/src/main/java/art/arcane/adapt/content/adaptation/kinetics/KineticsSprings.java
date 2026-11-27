package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.content.skill.kinetics.KineticsMotion;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

/**
 * Springy soles: a standing bounciness bonus and a timed springload after landing on a bouncy block, held in the
 * owning adaptation's attribute slots. Both are inert where the bounciness attribute does not exist.
 */
public final class KineticsSprings {
  private static final String SLOT_SOLE = "sole";
  private static final String SLOT_SPRINGLOAD = "springload";

  private final Adaptation<?> owner;

  public KineticsSprings(Adaptation<?> owner) {
    this.owner = owner;
  }

  /** Keeps the standing bounciness at {@code bounciness} while {@code wanted}, removes it otherwise. */
  public void maintainSole(PlayerRef player, boolean wanted, double bounciness) {
    if (!player.hasAttribute(Attributes.BOUNCINESS) || !player.isOnline() || player.isDead()) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (!wanted) {
      attributes.remove(player, owner.getId().toString(), SLOT_SOLE, Attributes.BOUNCINESS);
      return;
    }
    attributes.apply(player, owner.getId().toString(), SLOT_SOLE, Attributes.BOUNCINESS, bounciness, AttributeOperation.ADD_VALUE);
  }

  /** True when the player stands on a bouncy block that can springload them. */
  public boolean canSpringload(PlayerRef p) {
    return p.hasAttribute(Attributes.BOUNCINESS) && KineticsMotion.isBouncySurface(landingSurface(p));
  }

  public void springload(PlayerRef p, double bonus, long windowTicks) {
    AdaptAttributeService.get().applyTimed(p, owner.getId().toString(), SLOT_SPRINGLOAD, Attributes.BOUNCINESS, bonus, AttributeOperation.ADD_VALUE, windowTicks);
    KineticsRubberSoul.springloadFx(owner, p.location());
  }

  private static Id landingSurface(PlayerRef p) {
    BlockRef feet = p.world().blockAt(p.location());
    Id feetType = feet.type();
    if (KineticsMotion.isBouncySurface(feetType)) {
      return feetType;
    }
    return feet.relative(BlockFace.DOWN).type();
  }
}
