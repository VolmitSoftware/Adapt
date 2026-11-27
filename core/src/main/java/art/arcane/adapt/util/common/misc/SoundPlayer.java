package art.arcane.adapt.util.common.misc;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.engine.fx.FxViewers;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class SoundPlayer {
  private static final double MIN_CULL_RADIUS = 8.0D;
  private static final double MAX_CULL_RADIUS = 48.0D;
  private static final double CULL_RADIUS_PER_VOLUME = 16.0D;
  private static final AtomicBoolean VIEWER_INDEX_FAILURE_LOGGED = new AtomicBoolean(false);

  private final Collection<PlayerRef> players;

  private SoundPlayer(Collection<PlayerRef> players) {
    this.players = players;
  }

  public static SoundPlayer of(Collection<PlayerRef> players) {
    return new SoundPlayer(players);
  }

  public static SoundPlayer of(PlayerRef player) {
    return new SoundPlayer(List.of(player));
  }

  public static SoundPlayer of(WorldRef world) {
    return new SoundPlayer(null);
  }

  public void play(@NotNull Loc location, @NotNull Id sound) {
    play(location, sound, 1.0f, 1.0f);
  }

  public void play(@NotNull EntityRef entity, @NotNull Id sound, float volume, float pitch) {
    Tasks.runEntity(entity, () -> play(entity.location(), sound, volume, pitch));
  }

  public void play(@NotNull Loc location, @NotNull Id sound, float volume, float pitch) {
    play(location, sound, SoundCategory.MASTER, volume, pitch);
  }

  public void play(@NotNull Loc location, @NotNull Id sound, SoundCategory category, float volume, float pitch) {
    if (!areSoundsEnabled()) {
      return;
    }

    if (players != null) {
      FxViewers.dispatch(players, player -> player.playSound(location, sound, category, volume, pitch));
      return;
    }

    playCulled(location, volume, player -> player.playSound(location, sound, category, volume, pitch));
  }

  private void playCulled(Loc location, float volume, Consumer<PlayerRef> action) {
    double radius = Math.min(MAX_CULL_RADIUS, Math.max(MIN_CULL_RADIUS, CULL_RADIUS_PER_VOLUME * volume));
    try {
      FxViewers.dispatch(location.world(), location.x(), location.y(), location.z(), radius, action);
    } catch (Throwable e) {
      if (VIEWER_INDEX_FAILURE_LOGGED.compareAndSet(false, true)) {
        AdaptLog.warn("Fx viewer index failed while culling a sound; dropping the affected sound.");
        AdaptLog.error(e);
      }
    }
  }

  private boolean areSoundsEnabled() {
    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    return effects == null || effects.isSoundsEnabled();
  }
}
