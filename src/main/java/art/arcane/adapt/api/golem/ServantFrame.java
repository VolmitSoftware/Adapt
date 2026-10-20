package art.arcane.adapt.api.golem;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class ServantFrame {
  private static final AtomicReference<Frame> FRAME = new AtomicReference<>();

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

  public static boolean servantMenuUnlocked(Player player) {
    return frame().map(frame -> frame.servantMenuUnlocked(player)).orElse(false);
  }

  public static boolean surrender(Mob body) {
    return body instanceof Skeleton servant && frame().map(frame -> frame.surrender(servant)).orElse(false);
  }

  public static void crumble(Mob body) {
    Optional<Frame> frame = frame();
    if (body instanceof Skeleton servant && frame.isPresent()) {
      frame.get().crumble(servant);
      return;
    }
    body.remove();
  }

  public interface Frame {
    int level(UUID owner);

    int channels(UUID owner);

    double labelRange();

    List<Skeleton> servants(UUID owner);

    long expiresAt(Skeleton servant);

    void summoned(Player owner, Skeleton servant);

    void struck(Skeleton servant, LivingEntity victim);

    double share(UUID owner);

    boolean servantMenuUnlocked(Player player);

    void openLattice(Player player, int rosterPage);

    boolean surrender(Skeleton servant);

    void crumble(Skeleton servant);
  }
}
