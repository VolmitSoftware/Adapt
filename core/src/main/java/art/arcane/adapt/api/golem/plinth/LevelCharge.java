package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LevelCharge implements PlinthCharge {
  public static final String COST_KEY = "infusion";

  private final Adaptation<?> adaptation;
  private final PlayerRef player;
  private final UUID activationId;
  private final int vanillaLevels;

  private LevelCharge(Adaptation<?> adaptation, PlayerRef player, UUID activationId, int vanillaLevels) {
    this.adaptation = Objects.requireNonNull(adaptation);
    this.player = Objects.requireNonNull(player);
    this.activationId = Objects.requireNonNull(activationId);
    this.vanillaLevels = vanillaLevels;
  }

  public static Optional<PlinthCharge> take(Adaptation<?> adaptation, PlayerRef player, int levels) {
    if (levels <= 0) {
      return Optional.of(FREE);
    }
    AtomicBoolean spent = new AtomicBoolean();
    AbilityCharge charge = adaptation.payExperienceCostDeferred(player, COST_KEY, levels, () -> {
      if (player.level() < levels) {
        return false;
      }
      player.giveExpLevels(-levels);
      spent.set(true);
      return true;
    });
    if (!charge.allowed()) {
      return Optional.empty();
    }
    return Optional.of(new LevelCharge(adaptation, player, charge.activationId(), spent.get() ? levels : 0));
  }

  @Override
  public void settle() {
    adaptation.settleCost(activationId);
  }

  @Override
  public void refund() {
    adaptation.refundCost(activationId, AbilityRefundReason.ACTIVATION_FAILED);
    if (vanillaLevels > 0) {
      player.giveExpLevels(vanillaLevels);
    }
  }
}
