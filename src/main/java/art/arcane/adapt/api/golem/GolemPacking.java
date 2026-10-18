package art.arcane.adapt.api.golem;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class GolemPacking {
  private static final AtomicReference<Packer> PACKER = new AtomicReference<>();

  private GolemPacking() {
  }

  public static void register(Packer packer) {
    PACKER.set(Objects.requireNonNull(packer));
  }

  public static boolean unregister(Packer packer) {
    return PACKER.compareAndSet(packer, null);
  }

  public static Optional<Packer> packer() {
    return Optional.ofNullable(PACKER.get());
  }

  public static boolean packs(Player player) {
    return packer().map(packer -> packer.packs(player)).orElse(false);
  }

  public static boolean recalls(Player player) {
    return packer().map(packer -> packer.recalls(player)).orElse(false);
  }

  public interface Packer {
    boolean packs(Player player);

    void pack(Player player, GolemHandle handle);

    boolean recalls(Player player);

    void recall(Player player, GolemHandle handle);
  }
}
