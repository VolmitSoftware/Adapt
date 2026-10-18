package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.overseer.OverseerReceiver;
import art.arcane.adapt.api.golem.overseer.OverseerSignal;
import art.arcane.adapt.api.minion.ServantOrder;
import art.arcane.adapt.api.minion.ServantPack;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.entity.Skeleton;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

final class OverseerServants implements OverseerReceiver {
  private final ToIntFunction<UUID> frameLevel;
  private final ToIntFunction<UUID> channels;
  private final Supplier<Optional<ServantPack>> pack;

  OverseerServants(ToIntFunction<UUID> frameLevel, ToIntFunction<UUID> channels, Supplier<Optional<ServantPack>> pack) {
    this.frameLevel = Objects.requireNonNull(frameLevel);
    this.channels = Objects.requireNonNull(channels);
    this.pack = Objects.requireNonNull(pack);
  }

  @Override
  public void receive(OverseerSignal signal) {
    UUID owner = signal.owner();
    if (!BoneFrameRules.obeys(signal.signal()) || frameLevel.applyAsInt(owner) < 1 || !signal.hears(channels.applyAsInt(owner))) {
      return;
    }
    Optional<ServantPack> servants = pack.get();
    if (servants.isEmpty()) {
      return;
    }
    for (Skeleton servant : servants.get().servants(owner)) {
      J.runEntity(servant, () -> answer(servants.get(), servant, signal));
    }
  }

  private static void answer(ServantPack servants, Skeleton servant, OverseerSignal signal) {
    if (!servant.isValid() || servant.isDead()) {
      return;
    }
    Location at = servant.getLocation();
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

  private static void steer(Skeleton servant, ServantOrder previous, ServantOrder next) {
    if (next.commanded()) {
      servant.setTarget(next.target());
      return;
    }
    if (next.mode() == ServantOrder.Mode.HOLD) {
      servant.setTarget(null);
      PaperCompat.stopPathfinding(servant);
      return;
    }
    if (previous.commanded() && servant.getTarget() == previous.target()) {
      servant.setTarget(null);
    }
  }
}
