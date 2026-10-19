package art.arcane.adapt.api.golem.overseer;

import art.arcane.adapt.Adapt;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class Overseer {
  private static final CopyOnWriteArrayList<OverseerReceiver> RECEIVERS = new CopyOnWriteArrayList<>();
  private static final Set<OverseerReceiver> FAILED = ConcurrentHashMap.newKeySet();
  private static final AtomicReference<Commander> COMMANDER = new AtomicReference<>();

  private Overseer() {
  }

  public static void register(OverseerReceiver receiver) {
    OverseerReceiver added = Objects.requireNonNull(receiver);
    FAILED.remove(added);
    RECEIVERS.addIfAbsent(added);
  }

  public static boolean unregister(OverseerReceiver receiver) {
    FAILED.remove(receiver);
    return RECEIVERS.remove(receiver);
  }

  public static void dispatch(OverseerSignal signal) {
    for (OverseerReceiver receiver : RECEIVERS) {
      try {
        receiver.receive(signal);
      } catch (RuntimeException failure) {
        if (FAILED.add(receiver)) {
          Adapt.warn("Overseer receiver " + receiver.getClass().getName() + " failed on a " + signal.signal().id() + " signal from "
              + signal.owner() + "; other receivers still hear signals.", failure);
        }
      }
    }
  }

  public static void setCommander(Commander commander) {
    COMMANDER.set(Objects.requireNonNull(commander));
  }

  public static boolean clearCommander(Commander commander) {
    return COMMANDER.compareAndSet(commander, null);
  }

  public static Optional<Commander> commander() {
    return Optional.ofNullable(COMMANDER.get());
  }

  public interface Commander {
    void cycle(Player player);

    void send(Player player);

    boolean filtersChannels(Player player);
  }
}
