package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.api.batch.Payment;
import art.arcane.adapt.api.batch.Payments;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pays a build batch from the player's hands and inventory, then from the bundles, backpacks and shulker boxes they
 * carry: a stack pulled out of a container goes into the inventory and the batch takes from it.
 */
public final class ArchitectSupplyPayment implements Payment {
  private final PlayerRef player;
  private final Payment hands;
  private final ArchitectSupplyLine supply;

  public ArchitectSupplyPayment(PlayerRef player, ArchitectSupplyLine supply) {
    this.player = player;
    this.hands = Payments.player(player);
    this.supply = supply;
  }

  @Override
  public @Nullable ItemRef take(ItemRef unit) {
    ItemRef taken = hands.take(unit);
    if (taken != null || supply == null) {
      return taken;
    }
    ItemRef pulled = supply.pullFromContainers(player, unit);
    if (pulled == null || pulled.isEmpty()) {
      return null;
    }
    ItemRef one = pulled.copyWithAmount(1);
    pulled.setAmount(pulled.amount() - 1);
    if (pulled.amount() > 0) {
      Payments.give(player, List.of(pulled));
    }
    return one;
  }

  @Override
  public void giveBack(ItemRef taken) {
    hands.giveBack(taken);
  }
}
