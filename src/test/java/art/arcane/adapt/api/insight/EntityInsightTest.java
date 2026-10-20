package art.arcane.adapt.api.insight;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityInsightTest {
  private final EntityInsight.Provider names = (viewer, target) -> List.of("Clank");
  private final EntityInsight.Provider status = (viewer, target) -> List.of("|||||", "Guardian");
  private final EntityInsight.Display display = mock(EntityInsight.Display.class);

  @AfterEach
  void tearDown() {
    EntityInsight.unregister(names);
    EntityInsight.unregister(status);
    EntityInsight.detach(display);
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
  void attachingAndDetachingTheDisplayAnswersForIt() {
    UUID viewerId = UUID.randomUUID();
    when(display.showing(viewerId)).thenReturn(true);
    assertThat(EntityInsight.showing(viewerId)).isFalse();

    EntityInsight.attach(display);
    assertThat(EntityInsight.showing(viewerId)).isTrue();

    EntityInsight.detach(mock(EntityInsight.Display.class));
    assertThat(EntityInsight.showing(viewerId)).isTrue();

    EntityInsight.detach(display);
    assertThat(EntityInsight.showing(viewerId)).isFalse();
  }
}
