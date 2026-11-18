package art.arcane.adapt.api.golem.overseer;

import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public record OverseerSignal(UUID owner, GolemSignal signal, Loc origin, double radius, DyeColor channel, LivingRef target,
                             long commandUntilMillis, Rally rally, Consumer<EntityRef> pulse) {
  public OverseerSignal {
    Objects.requireNonNull(owner);
    Objects.requireNonNull(signal);
    Objects.requireNonNull(origin);
    Objects.requireNonNull(pulse);
  }

  public boolean hears(int channels) {
    return channel == null || ChannelMask.contains(channels, channel);
  }

  public boolean sameWorld(Loc at) {
    return at != null && origin.world() != null && origin.sameWorld(at);
  }

  public boolean inRange(Loc at) {
    return sameWorld(at) && origin.distanceSquared(at) <= radius * radius;
  }

  public boolean claimRally() {
    return rally != null && rally.claim();
  }

  public void reached(EntityRef entity) {
    pulse.accept(entity);
  }

  public interface Rally {
    boolean claim();
  }
}
