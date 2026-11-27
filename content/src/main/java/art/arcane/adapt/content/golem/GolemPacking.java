package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.PlayerRef;

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

  public static boolean packs(PlayerRef player) {
    return packer().map(packer -> packer.packs(player)).orElse(false);
  }

  public static boolean recalls(PlayerRef player) {
    return packer().map(packer -> packer.recalls(player)).orElse(false);
  }

  public interface Packer {
    boolean packs(PlayerRef player);

    void pack(PlayerRef player, GolemHandle handle);

    boolean recalls(PlayerRef player);

    void recall(PlayerRef player, GolemHandle handle);
  }
}
