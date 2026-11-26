package art.arcane.adapt.bukkit.feature.recipe;

import org.bukkit.event.Event;
import org.bukkit.inventory.Recipe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BukkitRecipeRegistryTest {
  private final List<Recipe> recipes = new ArrayList<>(List.of(mock(Recipe.class), mock(Recipe.class)));
  private final AtomicInteger scans = new AtomicInteger();
  private final AtomicLong now = new AtomicLong();

  @Test
  void withTheReloadEventTheTokenChangesOnlyOnInvalidationAndNeverScans() {
    BukkitRecipeRegistry registry = registry(Event.class);
    Object first = registry.generation();
    recipes.add(mock(Recipe.class));
    now.addAndGet(BukkitRecipeRegistry.RECOUNT_MILLIS * 2);

    assertThat(registry.generation()).isSameAs(first);
    registry.invalidate();
    assertThat(registry.generation()).isNotSameAs(first);
    assertThat(scans).hasValue(0);
  }

  @Test
  void withoutTheReloadEventACountChangeIsSeenAtTheNextRecount() {
    BukkitRecipeRegistry registry = registry(null);
    Object first = registry.generation();
    recipes.add(mock(Recipe.class));

    assertThat(registry.generation()).isSameAs(first);
    assertThat(scans).hasValue(1);

    now.addAndGet(BukkitRecipeRegistry.RECOUNT_MILLIS);
    Object recounted = registry.generation();
    assertThat(recounted).isNotSameAs(first);
    assertThat(scans).hasValue(2);

    now.addAndGet(BukkitRecipeRegistry.RECOUNT_MILLIS);
    assertThat(registry.generation()).isSameAs(recounted);
    assertThat(scans).hasValue(3);
  }

  private BukkitRecipeRegistry registry(Class<? extends Event> reloadEvent) {
    return new BukkitRecipeRegistry(() -> {
      scans.incrementAndGet();
      return List.copyOf(recipes).iterator();
    }, reloadEvent, now::get);
  }
}
