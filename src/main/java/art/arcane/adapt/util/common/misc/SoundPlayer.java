package art.arcane.adapt.util.common.misc;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.fx.FxViewers;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
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

  private final Collection<Player> players;
  private final World world;

  private SoundPlayer(Collection<Player> players, World world) {
    this.players = players;
    this.world = world;
  }

  public static SoundPlayer of(Collection<Player> players) {
    return new SoundPlayer(players, null);
  }

  public static SoundPlayer of(Player player) {
    return new SoundPlayer(List.of(player), null);
  }

  public static SoundPlayer of(World world) {
    return new SoundPlayer(null, world);
  }

  public void play(@NotNull Location location, @NotNull Sound sound) {
    play(location, sound, 1.0f, 1.0f);
  }

  public void play(@NotNull Entity entity, @NotNull Sound sound, float volume, float pitch) {
    J.runEntity(entity, () -> play(entity.getLocation(), sound, volume, pitch));
  }

  public void play(@NotNull Location location, @NotNull Sound sound, float volume, float pitch) {
    if (!areSoundsEnabled()) {
      return;
    }

    Location soundLocation = snapshot(location);
    if (soundLocation == null) {
      return;
    }

    if (players != null) {
      FxViewers.dispatch(players, player -> player.playSound(soundLocation, sound, volume, pitch));
      return;
    }

    playCulled(soundLocation, volume, player -> player.playSound(soundLocation, sound, volume, pitch));
  }

  public void play(@NotNull Location location, @NotNull Sound sound, SoundCategory category, float volume, float pitch) {
    if (!areSoundsEnabled()) {
      return;
    }

    Location soundLocation = snapshot(location);
    if (soundLocation == null) {
      return;
    }

    if (players != null) {
      FxViewers.dispatch(players, player -> player.playSound(soundLocation, sound, category, volume, pitch));
      return;
    }

    playCulled(soundLocation, volume, player -> player.playSound(soundLocation, sound, category, volume, pitch));
  }

  private void playCulled(Location location, float volume, Consumer<Player> action) {
    World target = location.getWorld() == null ? world : location.getWorld();
    if (target == null) {
      return;
    }

    double radius = Math.min(MAX_CULL_RADIUS, Math.max(MIN_CULL_RADIUS, CULL_RADIUS_PER_VOLUME * volume));
    try {
      FxViewers.dispatch(target, location.getX(), location.getY(), location.getZ(), radius, action);
    } catch (Throwable e) {
      if (VIEWER_INDEX_FAILURE_LOGGED.compareAndSet(false, true)) {
        Adapt.warn("Fx viewer index failed while culling a sound; dropping the affected sound.");
        e.printStackTrace();
      }
    }
  }

  private Location snapshot(Location location) {
    World target = location.getWorld() == null ? world : location.getWorld();
    if (target == null) {
      return null;
    }
    return new Location(target, location.getX(), location.getY(), location.getZ());
  }

  private boolean areSoundsEnabled() {
    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    return effects == null || effects.isSoundsEnabled();
  }
}
