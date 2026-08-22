package art.arcane.adapt.papi;

import art.arcane.adapt.api.skill.Skill;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptPlaceholderWiringTest {
  private static final Path EXPANSION_SOURCE = Path.of("src/main/java/art/arcane/adapt/papi/AdaptPapiExpansion.java");
  private static final Path INSTALLER_SOURCE = Path.of("src/main/java/art/arcane/adapt/papi/AdaptPlaceholderInstaller.java");
  private static final Path PLUGIN_SOURCE = Path.of("src/main/java/art/arcane/adapt/Adapt.java");
  private static final Path PLAYER_SOURCE = Path.of("src/main/java/art/arcane/adapt/api/world/AdaptPlayer.java");
  private static final Path SERVER_SOURCE = Path.of("src/main/java/art/arcane/adapt/api/world/AdaptServer.java");
  private static final Path REGISTRY_SOURCE = Path.of("src/main/java/art/arcane/adapt/api/skill/SkillRegistry.java");

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

  @Test
  void shouldNeverParseAttributesByPrefixOrUnderscoreSplitting() throws Exception {
    String source = Files.readString(EXPANSION_SOURCE).replace("\r\n", "\n");
    assertFalse(source.contains("startsWith("), "attribute dispatch must not use prefix matching");
    assertFalse(source.contains(".split("), "attribute dispatch must not re-split an underscore string");
    assertFalse(source.contains("Adapt.instance"), "the expansion must not read a plugin static");
    assertTrue(source.contains("public static final String IDENTIFIER = \"adapt\";"));
    assertFalse(source.contains("getDescription()"));
  }

  @Test
  void shouldUnregisterTheExpansionBeforeTheDrainLatchSoASecondStopStillUnregisters() throws Exception {
    String source = Files.readString(PLUGIN_SOURCE).replace("\r\n", "\n");
    assertTrue(
        source.contains("  public void stop() {\n    unregisterPapiExpansion();\n    if (!alreadyDrained.compareAndSet(false, true)) {"),
        "unregisterPapiExpansion() must run before the alreadyDrained latch short-circuits stop()"
    );
    assertTrue(source.contains("private volatile PlaceholderRegistration papiRegistration;"));
    assertFalse(
        source.contains("new AdaptPapiExpansion("),
        "the plugin class must not construct the expansion: doing so forces PlaceholderExpansion to load during onEnable, which crashes a server without PlaceholderAPI"
    );
    assertTrue(
        source.contains("if (!PlaceholderRegistration.isPlaceholderApiEnabled()) {"),
        "registration must be guarded before any PlaceholderAPI-derived class is touched"
    );
    assertTrue(
        Files.readString(INSTALLER_SOURCE).replace("\r\n", "\n").contains("new AdaptPapiExpansion("),
        "the installer is the only place the expansion may be constructed"
    );
  }

  @Test
  void shouldPublishThePlayerSnapshotInsideTheExistingOneHertzTickBand() throws Exception {
    String source = Files.readString(PLAYER_SOURCE).replace("\r\n", "\n");
    assertTrue(
        source.contains("      getData().update(this);\n      AdaptPlaceholders.get().publishPlayer(this);\n      nextUpdateAt = now + UPDATE_INTERVAL_MS;"),
        "the snapshot must be published from the existing UPDATE_INTERVAL_MS band on the owning thread"
    );
    assertFalse(source.contains("new Thread("), "the writer must not introduce a scheduler");
  }

  @Test
  void shouldEvictOnQuitAndPushTheCatalogFromTheRevisionBump() throws Exception {
    assertTrue(Files.readString(SERVER_SOURCE).replace("\r\n", "\n").contains("AdaptPlaceholders.get().evictAfterGrace(p);"));

    String registry = Files.readString(REGISTRY_SOURCE).replace("\r\n", "\n");
    assertTrue(registry.contains("publishPlaceholderCatalog(revision);"));
    assertTrue(registry.contains("AdaptPlaceholders.get().publishCatalog(revision, getAllSkills());"));
  }

  @Test
  void shouldNotLeaveTheOldUnderscoreExpansionBehind() {
    assertFalse(Files.exists(Path.of("src/main/java/art/arcane/adapt/PapiExpansion.java")));
  }
}
