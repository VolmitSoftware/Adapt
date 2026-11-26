package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.UUID;
import java.util.function.DoubleConsumer;

/** Spends a bone from the player's inventory, honouring their bone reserve, to absorb part of a hit. */
public final class TragoulMarrow {
  private static final Id BONE = Id.minecraft("bone");

  private final Adaptation<?> owner;
  private final Cooldowns clickFx;

  public TragoulMarrow(Adaptation<?> owner, Cooldowns clickFx) {
    this.owner = owner;
    this.clickFx = clickFx;
  }

  /**
   * Absorbs {@code fraction} of the hit when a bone could be spent, passing the absorbed damage to {@code onAbsorbed}.
   * Returns false when no bone was spent.
   */
  public boolean absorb(PlayerRef p, EntityDamageEvent e, double fraction, DoubleConsumer onAbsorbed) {
    UUID id = p.uuid();
    ItemRef bone = AdaptCore.platform().host().items().create(BONE);
    if (!p.inventory().containsAtLeast(bone, owner.preference(p, TragoulPreferences.BONES).required(1))) {
      if (clickFx.isReady(id, 1500L)) {
        clickFx.mark(id);
        TragoulMarrowArmor.noBoneFx(owner, p);
      }
      return false;
    }

    if (!owner.payItemCost(p, "absorb", bone, 1, () -> {
      p.inventory().removeSimilar(bone, 1);
      return true;
    })) {
      return false;
    }
    double absorbed = e.damage() * fraction;
    e.setDamage(Math.max(0, e.damage() - absorbed));
    TragoulMarrowArmor.shieldFx(owner, p);
    onAbsorbed.accept(absorbed);
    return true;
  }
}
