package art.arcane.adapt.papi;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderSnapshot;
import art.arcane.volmlib.util.bukkit.papi.PlayerSnapshotStore;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptPapiExpansionTest {
  private static final UUID ONLINE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID OFFLINE = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final UUID POWER_SPENT = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
  private static final UUID POWER_EDGE = UUID.fromString("00000000-0000-0000-0000-0000000000d4");
  private static final String UNAVAILABLE = "---";

  private PlayerSnapshotStore<AdaptPlayerSnapshot> players;
  private PlaceholderSnapshot<AdaptCatalogSnapshot> catalog;
  private AdaptCatalogSnapshot catalogSnapshot;
  private AdaptPapiExpansion expansion;

  @BeforeEach
  void publishSnapshots() {
    players = new PlayerSnapshotStore<>();
    catalog = new PlaceholderSnapshot<>();
    expansion = new AdaptPapiExpansion(players, catalog, Logger.getLogger("adapt-papi-test"));
    catalogSnapshot = AdaptPapiFixtures.catalog();
    catalog.publish(catalogSnapshot);
    players.publish(ONLINE, AdaptPapiFixtures.player());
  }

  private String resolve(String path) {
    return expansion.onRequest(offlinePlayer(ONLINE), path);
  }

  private String resolveOffline(String path) {
    return expansion.onRequest(offlinePlayer(OFFLINE), path);
  }

  private String resolveFor(UUID id, String path) {
    return expansion.onRequest(offlinePlayer(id), path);
  }

  private static OfflinePlayer offlinePlayer(UUID id) {
    OfflinePlayer player = mock(OfflinePlayer.class);
    when(player.getUniqueId()).thenReturn(id);
    return player;
  }

  @Test
  void shouldExposeStableMetadataAndTheOwningPluginName() {
    assertEquals("adapt", expansion.getIdentifier());
    assertEquals("Volmit Software", expansion.getAuthor());
    assertEquals("1.0.0", expansion.getVersion());
    assertEquals("Adapt", expansion.getRequiredPlugin());
    assertTrue(expansion.persist());
  }

  @Test
  void shouldPublishTheReservedAvailableKeyAndEveryGroup() {
    List<String> keys = expansion.getPlaceholders();
    assertTrue(keys.contains("available"), keys.toString());
    assertTrue(keys.contains("skill.*"), keys.toString());
    assertTrue(keys.contains("adaptation.*"), keys.toString());
    assertTrue(keys.contains("catalog.skills"), keys.toString());
  }

  @Test
  void shouldAnswerAvailableFromThePerPlayerSnapshot() {
    assertEquals("true", resolve("available"));
    assertEquals("false", resolveOffline("available"));
  }

  @Test
  void shouldResolvePlayerPowerAndProgressionFromTheSnapshot() {
    assertEquals("10", resolve("player.level"));
    assertEquals("1000", resolve("player.max-level"));
    assertEquals("100.00", resolve("player.master-xp"));
    assertEquals("1.25", resolve("player.multiplier"));
    assertEquals("3", resolve("player.wisdom"));
    assertEquals("10", resolve("player.power-max"));
    assertEquals("2", resolve("player.power-used"));
    assertEquals("8", resolve("player.power"));
    assertEquals("1", resolve("player.known-skills"));
    assertEquals("2", resolve("player.learned-adaptations"));
  }

  @Test
  void shouldKeepPowerInternallyConsistentSoAvailablePowerIsNeverNegative() {
    int max = Integer.parseInt(resolve("player.power-max"));
    int used = Integer.parseInt(resolve("player.power-used"));
    int available = Integer.parseInt(resolve("player.power"));
    assertEquals(max - used, available);
    assertTrue(available >= 0, "available power must not go negative");
  }

  @Test
  void shouldResolvePerSkillLevelXpAndProgress() {
    assertEquals("5", resolve("skill.mining.level"));
    assertEquals("30.25", resolve("skill.mining.xp"));
    assertEquals("10", resolve("skill.mining.knowledge"));
    assertEquals("1.50", resolve("skill.mining.multiplier"));
    assertEquals("0.50", resolve("skill.mining.progress"));
    assertEquals("50.00", resolve("skill.mining.progress-percent"));
    assertEquals("5.50", resolve("skill.mining.xp-to-next"));
    assertEquals("25.00", resolve("skill.mining.current-level-xp"));
    assertEquals("36.00", resolve("skill.mining.next-level-xp"));
    assertEquals("2", resolve("skill.mining.learned-adaptations"));
    assertEquals("true", resolve("skill.mining.known"));
    assertEquals("Mining", resolve("skill.mining.name"));
    assertEquals("true", resolve("skill.mining.enabled"));
    assertEquals("2", resolve("skill.mining.adaptations"));
  }

  @Test
  void shouldResolveASkillTheOwnerHasNeverTrainedAsAGenuineZero() {
    assertEquals("0", resolve("skill.hunter.level"));
    assertEquals("0.00", resolve("skill.hunter.xp"));
    assertEquals("0", resolve("skill.hunter.knowledge"));
    assertEquals("false", resolve("skill.hunter.known"));
    assertEquals("false", resolve("skill.hunter.enabled"));
    assertEquals("0", resolve("skill.hunter.learned-adaptations"));
  }

  @Test
  void shouldAnswerHasLevelWithAParsedNumericArgument() {
    assertEquals("true", resolve("skill.mining.has-level.5"));
    assertEquals("true", resolve("skill.mining.has-level.0"));
    assertEquals("false", resolve("skill.mining.has-level.6"));
  }

  @Test
  void shouldResolveAdaptationUnlockStateAndCosts() {
    assertEquals("1", resolve("adaptation.mining-vein.level"));
    assertEquals("3", resolve("adaptation.mining-vein.max-level"));
    assertEquals("Vein Miner", resolve("adaptation.mining-vein.name"));
    assertEquals("mining", resolve("adaptation.mining-vein.skill"));
    assertEquals("true", resolve("adaptation.mining-vein.enabled"));
    assertEquals("true", resolve("adaptation.mining-vein.learned"));
    assertEquals("true", resolve("adaptation.mining-vein.can-use"));
    assertEquals("7", resolve("adaptation.mining-vein.cost-next"));
    assertEquals("1", resolve("adaptation.mining-vein.power-next"));
    assertEquals("15", resolve("adaptation.mining-vein.cost-to.3"));
    assertEquals("2", resolve("adaptation.mining-vein.power-to.3"));
  }

  @Test
  void shouldReportADisabledAdaptationAsUnusableEvenWhenLearned() {
    assertEquals("1", resolve("adaptation.mining-ore-scan.level"));
    assertEquals("false", resolve("adaptation.mining-ore-scan.enabled"));
    assertEquals("true", resolve("adaptation.mining-ore-scan.learned"));
    assertEquals("false", resolve("adaptation.mining-ore-scan.can-use"));
  }

  @Test
  void shouldAnswerCanClaimNextOnlyWhenBothKnowledgeAndAbilityPowerAffordTheNextLevel() {
    players.publish(POWER_SPENT, AdaptPapiFixtures.powerBoundPlayer(AdaptPapiFixtures.MAX_POWER));

    assertEquals("8", resolve("player.power"));
    assertEquals("10", resolve("skill.mining.knowledge"));
    assertEquals("true", resolve("adaptation.mining-vein.can-claim-next"));

    assertEquals("0", resolveFor(POWER_SPENT, "player.power"));
    assertEquals("1000", resolveFor(POWER_SPENT, "skill.mining.knowledge"));
    assertEquals("7", resolveFor(POWER_SPENT, "adaptation.mining-vein.cost-next"));
    assertEquals("1", resolveFor(POWER_SPENT, "adaptation.mining-vein.power-next"));
    assertEquals(
        "false",
        resolveFor(POWER_SPENT, "adaptation.mining-vein.can-claim-next"),
        "ample knowledge must not claim a level the owner has no ability power for"
    );
  }

  @Test
  void shouldAnswerCanClaimFalseWhenTheTargetLevelCostsMoreKnowledgeThanTheOwnerHas() {
    assertEquals("false", resolve("adaptation.mining-vein.can-claim.3"));
    assertEquals("true", resolve("adaptation.mining-vein.can-claim.2"));
  }

  @Test
  void shouldAnswerCanClaimFalseWhenTheTargetLevelCostsMoreAbilityPowerThanTheOwnerHas() {
    players.publish(POWER_SPENT, AdaptPapiFixtures.powerBoundPlayer(AdaptPapiFixtures.MAX_POWER));

    assertEquals("15", resolveFor(POWER_SPENT, "adaptation.mining-vein.cost-to.3"));
    assertEquals("2", resolveFor(POWER_SPENT, "adaptation.mining-vein.power-to.3"));
    assertEquals("false", resolveFor(POWER_SPENT, "adaptation.mining-vein.can-claim.3"));
    assertEquals("false", resolveFor(POWER_SPENT, "adaptation.mining-vein.can-claim.2"));
    assertEquals("true", resolveFor(POWER_SPENT, "adaptation.mining-vein.can-claim.1"));
  }

  @Test
  void shouldClaimExactlyAsFarAsTheRemainingAbilityPowerReaches() {
    players.publish(POWER_EDGE, AdaptPapiFixtures.powerBoundPlayer(AdaptPapiFixtures.MAX_POWER - 1));

    assertEquals("1", resolveFor(POWER_EDGE, "player.power"));
    assertEquals("1000", resolveFor(POWER_EDGE, "skill.mining.knowledge"));
    assertEquals("true", resolveFor(POWER_EDGE, "adaptation.mining-vein.can-claim.2"));
    assertEquals(
        "false",
        resolveFor(POWER_EDGE, "adaptation.mining-vein.can-claim.3"),
        "one spare power buys one level, never two"
    );
  }

  @Test
  void shouldRefuseToRefundAPermanentAdaptation() {
    assertEquals("false", resolve("adaptation.mining-ore-scan.can-claim.0"));
    assertEquals("true", resolve("adaptation.mining-vein.can-claim.0"));
    assertEquals("true", resolve("adaptation.mining-vein.can-claim.1"));
  }

  @Test
  void shouldStillRefundAnAdaptationWhenNoAbilityPowerIsLeft() {
    players.publish(POWER_SPENT, AdaptPapiFixtures.powerBoundPlayer(AdaptPapiFixtures.MAX_POWER));

    assertEquals("0", resolveFor(POWER_SPENT, "player.power"));
    assertEquals("true", resolveFor(POWER_SPENT, "adaptation.mining-vein.can-claim.0"));
    assertEquals("false", resolveFor(POWER_SPENT, "adaptation.mining-ore-scan.can-claim.0"));
  }

  @Test
  void shouldNeverMatchAnAttributeByPrefix() {
    assertNull(resolve("adaptation.mining-vein.can-claim-nextra"));
    assertNull(resolve("adaptation.mining-vein.can-claim.next"));
    assertNull(resolve("adaptation.mining-vein.can-claim."));
    assertNull(resolve("adaptation.mining-vein.cost-to.x"));
    assertNull(resolve("adaptation.mining-vein.cost-to.-1"));
    assertNull(resolve("skill.mining.has-level.five"));
    assertNull(resolve("skill.mining.has-levelx.5"));
  }

  @Test
  void shouldReturnNullForAnUnknownPathSoPapiEmitsTheLiteral() {
    assertNull(resolve("skil.mining.level"));
    assertNull(resolve("skill.mynng.level"));
    assertNull(resolve("skill.mining.levle"));
    assertNull(resolve("player.levle"));
    assertNull(resolve("adaptation.mining-bogus.level"));
    assertNull(resolve("bogus"));
    assertNull(resolve("skill"));
    assertNull(resolve("skill.mining"));
  }

  @Test
  void shouldNeverReturnZeroForAnUnknownPath() {
    List<String> typos = List.of(
        "skill.mining.levle",
        "player.levle",
        "adaptation.mining-vein.costnext",
        "catalog.skils"
    );

    for (String typo : typos) {
      assertNull(resolve(typo), "unknown path must be null, not a plausible datum: " + typo);
    }
  }

  @Test
  void shouldReturnTheUnavailableSentinelForAPlayerWithNoSnapshot() {
    assertEquals(UNAVAILABLE, resolveOffline("player.level"));
    assertEquals(UNAVAILABLE, resolveOffline("skill.mining.level"));
    assertEquals(UNAVAILABLE, resolveOffline("adaptation.mining-vein.level"));
    assertNull(resolveOffline("skill.mynng.level"), "a typo must stay a typo for an offline player");
  }

  @Test
  void shouldStillAnswerCatalogKeysForAPlayerWithNoSnapshot() {
    assertEquals("true", resolveOffline("catalog.available"));
    assertEquals("2", resolveOffline("catalog.skills"));
    assertEquals("2", resolveOffline("catalog.adaptations"));
  }

  @Test
  void shouldServeTheLastSnapshotInsideTheQuitGraceWindowAndNothingAfterIt() {
    players.evictAfterGrace(ONLINE, AdaptPlaceholders.QUIT_GRACE_MS);
    assertEquals("10", resolve("player.level"));

    players.evictAfterGrace(ONLINE, 0L);
    assertEquals(UNAVAILABLE, resolve("player.level"));
    assertEquals("false", resolve("available"));
  }

  @Test
  void shouldLowercaseTheWholePathBeforeDispatch() {
    assertEquals("10", resolve("PLAYER.LEVEL"));
    assertEquals("5", resolve("Skill.Mining.Level"));
    assertEquals("true", resolve("Adaptation.Mining-Vein.Can-Claim-Next"));
  }

  @Test
  void shouldReturnNullForBlankParamsRatherThanASentinel() {
    OfflinePlayer player = offlinePlayer(ONLINE);
    assertNull(expansion.onRequest(player, ""));
    assertNull(expansion.onRequest(player, "   "));
    assertNull(expansion.onRequest(player, null));
  }

  @Test
  void shouldNeverEmitAPercentOrLegacyColourCodeInAnyValue() {
    List<String> paths = List.of(
        "available",
        "catalog.skills",
        "player.level",
        "player.multiplier",
        "skill.mining.name",
        "skill.mining.progress-percent",
        "skill.mining.xp",
        "adaptation.mining-vein.name",
        "adaptation.mining-vein.cost-next"
    );

    for (String path : paths) {
      String value = resolve(path);
      assertNotNull(value, path);
      assertFalse(value.indexOf('%') >= 0, "value for " + path + " contains '%': " + value);
      assertFalse(value.indexOf('§') >= 0, "value for " + path + " contains a legacy colour code: " + value);
      assertFalse(value.indexOf(',') >= 0, "value for " + path + " contains a grouping separator: " + value);
    }
  }

  @Test
  void shouldNeverThrowForHostileParams() {
    OfflinePlayer player = offlinePlayer(ONLINE);
    List<String> hostile = List.of(
        "_",
        "___",
        ".",
        "..",
        "....",
        "skill.",
        ".mining.level",
        "skill..level",
        "adaptation.mining-vein.cost-to.99999999999999999999",
        "skill.mining.has-level.99999",
        "%",
        " ☃",
        "a".repeat(4096)
    );

    for (String params : hostile) {
      assertDoesNotThrow(() -> expansion.onRequest(player, params), "params: " + params);
    }

    assertDoesNotThrow(() -> expansion.onRequest(null, "player.level"));
  }

  @Test
  void shouldReturnUnavailableWhenTheCatalogHasNotBeenPublishedYet() {
    PlaceholderSnapshot<AdaptCatalogSnapshot> empty = new PlaceholderSnapshot<>();
    AdaptPapiExpansion cold = new AdaptPapiExpansion(players, empty, Logger.getLogger("adapt-papi-cold"));
    assertEquals("false", cold.onRequest(offlinePlayer(ONLINE), "catalog.available"));
    assertEquals(UNAVAILABLE, cold.onRequest(offlinePlayer(ONLINE), "catalog.skills"));
    assertEquals(UNAVAILABLE, cold.onRequest(offlinePlayer(ONLINE), "skill.mining.level"));
    assertEquals("10", cold.onRequest(offlinePlayer(ONLINE), "player.level"));
  }
}
