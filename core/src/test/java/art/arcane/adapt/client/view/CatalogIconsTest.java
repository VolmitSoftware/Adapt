package art.arcane.adapt.client.view;

import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Every skill and adaptation the catalog sends must name an item the client of that version can draw. */
class CatalogIconsTest {
  @TempDir
  Path data;

  @Test
  void everyCatalogIconIsADrawableItemOnEveryVersion() {
    List<FakeGameContent> games = List.of(FakeGameContent.v1_20_1(), FakeGameContent.v1_21_1(),
        FakeGameContent.v26_1_2(), FakeGameContent.v26_3());
    for (FakeGameContent game : games) {
      TestPlatform platform = TestPlatform.builder(data.resolve(game.version().toString())).build();
      platform.host().runGame(game);
      List<String> missing = new ArrayList<>();
      try (TestAdapt adapt = TestAdapt.start(platform, new BuiltinAddon())) {
        CatalogView catalog = CatalogViews.build(adapt.core().getAdaptServer().getSkillRegistry(), UUID.randomUUID(), List.of(), false);
        for (SkillView skill : catalog.skills()) {
          require(game, "skill " + skill.id(), skill.icon(), missing);
          for (AdaptationView adaptation : skill.adaptations()) {
            require(game, "adaptation " + adaptation.id(), adaptation.icon(), missing);
          }
        }
        assertThat(catalog.skills()).isNotEmpty();
      }
      assertThat(missing).as("icons without an item on " + game.version()).isEmpty();
    }
  }

  private static void require(FakeGameContent game, String owner, IconRef icon, List<String> missing) {
    Id item = Id.tryParse(icon.itemId());
    if (item == null || !game.exists(RegistryKind.ITEM, item)) {
      missing.add(owner + " -> " + icon.itemId());
    }
  }
}
