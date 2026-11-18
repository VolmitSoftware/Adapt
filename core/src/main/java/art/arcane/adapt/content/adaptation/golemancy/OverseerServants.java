package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.overseer.OverseerReceiver;
import art.arcane.adapt.api.golem.overseer.OverseerSignal;
import art.arcane.adapt.api.minion.ServantOrder;
import art.arcane.adapt.api.minion.ServantPack;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

final class OverseerServants implements OverseerReceiver {
  private final Predicate<UUID> obeys;
  private final ToIntFunction<UUID> channels;
  private final Supplier<Optional<ServantPack>> pack;

  OverseerServants(Predicate<UUID> obeys, ToIntFunction<UUID> channels, Supplier<Optional<ServantPack>> pack) {
    this.obeys = Objects.requireNonNull(obeys);
    this.channels = Objects.requireNonNull(channels);
    this.pack = Objects.requireNonNull(pack);
  }

  @Override
  public void receive(OverseerSignal signal) {
    UUID owner = signal.owner();
    if (!BoneFrameRules.obeys(signal.signal()) || !obeys.test(owner) || !signal.hears(channels.applyAsInt(owner))) {
      return;
    }
    Optional<ServantPack> servants = pack.get();
    if (servants.isEmpty()) {
      return;
    }
    for (MobRef servant : servants.get().servants(owner)) {
      Tasks.runEntity(servant, () -> answer(servants.get(), servant, signal));
    }
  }

  private static void answer(ServantPack servants, MobRef servant, OverseerSignal signal) {
    if (!servant.isValid() || servant.isDead()) {
      return;
    }
    Loc at = servant.location();
    if (!signal.inRange(at)) {
      return;
    }
    ServantOrder previous = servants.order(servant);
    Optional<ServantOrder> next = BoneFrameRules.answer(previous, signal.signal(), at, signal.target(), signal.commandUntilMillis());
    if (next.isEmpty() || !servants.order(servant, next.get())) {
      return;
    }
    steer(servant, previous, next.get());
    signal.reached(servant);
  }

  private static void steer(MobRef servant, ServantOrder previous, ServantOrder next) {
    if (next.commanded()) {
      servant.setTarget(next.target());
      return;
    }
    if (next.mode() == ServantOrder.Mode.HOLD) {
      servant.setTarget(null);
      servant.stopPathing();
      return;
    }
    LivingRef current = servant.target();
    if (previous.commanded() && current != null && current.equals(previous.target())) {
      servant.setTarget(null);
    }
  }
}
