package art.arcane.adapt.papi;

import art.arcane.adapt.api.skill.Skill;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AdaptPlaceholderWiringTest {
  @AfterEach
  void clearSharedState() {
    AdaptPlaceholders.get().clear();
  }

  @Test
  void shouldPublishTheCatalogOnlyWhenTheRevisionMoves() {
    AdaptPlaceholders placeholders = AdaptPlaceholders.get();
    List<Skill<?>> skills = List.of(AdaptPapiFixtures.skill("mining", true, "Mining"));

    placeholders.publishCatalog(4L, skills);
    AdaptCatalogSnapshot first = placeholders.catalog().get();
    assertNotNull(first);
    assertEquals(4L, first.revision());

    placeholders.publishCatalog(4L, skills);
    assertSame(first, placeholders.catalog().get());

    placeholders.publishCatalog(5L, skills);
    assertEquals(5L, placeholders.catalog().get().revision());
  }

  @Test
  void shouldClearEveryPublishedSnapshotOnUnregister() {
    AdaptPlaceholders placeholders = AdaptPlaceholders.get();
    UUID playerId = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    placeholders.publishCatalog(9L, List.of(AdaptPapiFixtures.skill("mining", true, "Mining")));
    placeholders.players().publish(playerId, AdaptPapiFixtures.player(placeholders.catalog().get()));

    placeholders.clear();

    assertNull(placeholders.catalog().get());
    assertNull(placeholders.players().get(playerId));
  }
}
