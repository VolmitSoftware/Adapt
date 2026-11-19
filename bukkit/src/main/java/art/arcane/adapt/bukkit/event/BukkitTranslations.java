package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.Cancellable;
import org.bukkit.event.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** The full translation table and small helpers shared by the per-area translation classes. */
public final class BukkitTranslations {
  private static volatile List<Translation<?>> all;

  private BukkitTranslations() {
  }

  public static List<Translation<?>> all() {
    List<Translation<?>> cached = all;
    if (cached == null) {
      List<Translation<?>> built = new ArrayList<>();
      built.addAll(BlockTranslations.all());
      built.addAll(EntityTranslations.all());
      built.addAll(PlayerTranslations.all());
      built.addAll(InventoryTranslations.all());
      built.addAll(BlockGapTranslations.all());
      built.addAll(EntityGapTranslations.all());
      built.addAll(PlayerGapTranslations.all());
      built.addAll(InventoryGapTranslations.all());
      built.addAll(Paper121Translations.all());
      built.addAll(Paper26Translations.all());
      built.addAll(WorldTranslations.all());
      built.addAll(MountTranslations.all());
      built.addAll(EndermanTranslations.all());
      built.addAll(AdaptApiTranslations.all());
      built.addAll(SpigotTranslations.all());
      cached = List.copyOf(built);
      all = cached;
    }
    return cached;
  }

  static <B extends Event> Translation<B> simple(Class<B> nativeType, Class<? extends AdaptEvent> posted,
                                                 BiConsumer<B, Translation.Poster> bridge) {
    return new Translation<>(nativeType, List.of(posted)) {
      @Override
      public void bridge(B event, Poster poster) {
        bridge.accept(event, poster);
      }
    };
  }

  /** Writes the core cancelled flag back when a handler changed it. */
  static void cancel(org.bukkit.event.Cancellable target, boolean before, Cancellable core) {
    boolean after = core.isCancelled();
    if (after != before) {
      target.setCancelled(after);
    }
  }
}
