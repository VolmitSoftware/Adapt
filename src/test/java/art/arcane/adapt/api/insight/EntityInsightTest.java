package art.arcane.adapt.api.insight;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityInsightTest {
  private final EntityInsight.Provider names = (viewer, target) -> List.of("Clank");
  private final EntityInsight.Provider status = (viewer, target) -> List.of("|||||", "Guardian");
  private final EntityInsight.Display display = mock(EntityInsight.Display.class);
  private final AtomicInteger changes = new AtomicInteger();
  private final Runnable listener = changes::incrementAndGet;

  @AfterEach
  void tearDown() {
    EntityInsight.unregister(names);
    EntityInsight.unregister(status);
    EntityInsight.detach(display);
    EntityInsight.removeListener(listener);
  }

  @Test
  void providersAddTheirLinesInRegistrationOrderAndOnlyOnce() {
    Player viewer = mock(Player.class);
    LivingEntity target = mock(LivingEntity.class);
    assertThat(EntityInsight.details(viewer, target)).isEmpty();

    EntityInsight.register(names);
    EntityInsight.register(status);
    EntityInsight.register(names);

    assertThat(EntityInsight.details(viewer, target)).containsExactly("Clank", "|||||", "Guardian");

    EntityInsight.unregister(names);
    assertThat(EntityInsight.details(viewer, target)).containsExactly("|||||", "Guardian");
  }

  @Test
  void attachingAndDetachingTheDisplayTellsListenersAndAnswersForIt() {
    Player viewer = mock(Player.class);
    UUID viewerId = UUID.randomUUID();
    when(display.activeFor(viewer)).thenReturn(true);
    when(display.showing(viewerId)).thenReturn(true);
    EntityInsight.addListener(listener);
    assertThat(EntityInsight.online()).isFalse();
    assertThat(EntityInsight.activeFor(viewer)).isFalse();
    assertThat(EntityInsight.showing(viewerId)).isFalse();

    EntityInsight.attach(display);

    assertThat(changes).hasValue(1);
    assertThat(EntityInsight.online()).isTrue();
    assertThat(EntityInsight.activeFor(viewer)).isTrue();
    assertThat(EntityInsight.showing(viewerId)).isTrue();

    EntityInsight.detach(mock(EntityInsight.Display.class));
    assertThat(changes).hasValue(1);
    assertThat(EntityInsight.online()).isTrue();

    EntityInsight.detach(display);
    assertThat(changes).hasValue(2);
    assertThat(EntityInsight.online()).isFalse();
  }
}
