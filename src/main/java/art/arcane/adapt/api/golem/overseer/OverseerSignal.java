package art.arcane.adapt.api.golem.overseer;

import art.arcane.adapt.api.golem.ChannelMask;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public record OverseerSignal(UUID owner, GolemSignal signal, Location origin, double radius, DyeColor channel, LivingEntity target,
                             long commandUntilMillis, Rally rally, Consumer<Entity> pulse) {
  public OverseerSignal {
    Objects.requireNonNull(owner);
    Objects.requireNonNull(signal);
    origin = Objects.requireNonNull(origin).clone();
    Objects.requireNonNull(pulse);
  }

  @Override
  public Location origin() {
    return origin.clone();
  }

  public boolean hears(int channels) {
    return channel == null || ChannelMask.contains(channels, channel);
  }

  public boolean sameWorld(Location at) {
    World from = origin.getWorld();
    return at != null && from != null && from.equals(at.getWorld());
  }

  public boolean inRange(Location at) {
    return sameWorld(at) && origin.distanceSquared(at) <= radius * radius;
  }

  public boolean claimRally() {
    return rally != null && rally.claim();
  }

  public void reached(Entity entity) {
    pulse.accept(entity);
  }

  public interface Rally {
    boolean claim();
  }
}
