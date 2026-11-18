package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class ServantFrame {
  private static final AtomicReference<Frame> FRAME = new AtomicReference<>();
  private static final Id SKELETON = Id.minecraft("skeleton");

  private ServantFrame() {
  }

  public static void register(Frame frame) {
    FRAME.set(Objects.requireNonNull(frame));
  }

  public static boolean unregister(Frame frame) {
    return FRAME.compareAndSet(frame, null);
  }

  public static Optional<Frame> frame() {
    return Optional.ofNullable(FRAME.get());
  }

  public static boolean servantMenuUnlocked(PlayerRef player) {
    return frame().map(frame -> frame.servantMenuUnlocked(player)).orElse(false);
  }

  public static boolean surrender(MobRef body) {
    return SKELETON.equals(body.type()) && frame().map(frame -> frame.surrender(body)).orElse(false);
  }

  public static void crumble(MobRef body) {
    Optional<Frame> frame = frame();
    if (SKELETON.equals(body.type()) && frame.isPresent()) {
      frame.get().crumble(body);
      return;
    }
    body.remove();
  }

  public interface Frame {
    int level(UUID owner);

    int channels(UUID owner);

    double labelRange();

    List<MobRef> servants(UUID owner);

    long expiresAt(MobRef servant);

    void summoned(PlayerRef owner, MobRef servant);

    void struck(MobRef servant, LivingRef victim);

    double share(UUID owner);

    boolean servantMenuUnlocked(PlayerRef player);

    void openLattice(PlayerRef player, int rosterPage);

    boolean surrender(MobRef servant);

    void crumble(MobRef servant);
  }
}
