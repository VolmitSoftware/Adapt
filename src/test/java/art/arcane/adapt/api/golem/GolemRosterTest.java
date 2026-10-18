package art.arcane.adapt.api.golem;

import art.arcane.adapt.util.common.io.Json;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GolemRosterTest {
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
  private static final UUID NETHER = UUID.fromString("00000000-0000-0000-0000-0000000000c4");

  @Test
  void bindingAddsAnActiveEntryOnce() {
    GolemRoster roster = new GolemRoster();
    long chunk = GolemRoster.chunkKey(3, -7);

    assertThat(roster.add(binding(GOLEM, chunk, 1_000L))).isTrue();
    assertThat(roster.add(binding(GOLEM, chunk, 2_000L))).isFalse();

    GolemRoster.GolemEntry entry = roster.golem(GOLEM).orElseThrow();
    assertThat(entry.id()).isEqualTo(GOLEM);
    assertThat(entry.body()).isEqualTo(GolemBody.IRON);
    assertThat(entry.core()).isEqualTo(GolemCoreType.GUARDIAN);
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(entry.world()).isEqualTo(WORLD);
    assertThat(entry.chunkKey()).isEqualTo(chunk);
    assertThat(entry.lastSeen()).isEqualTo(1_000L);
    assertThat(roster.golemCount()).isEqualTo(1);
  }

  @Test
  void legalTransitionsFollowTheLifecycle() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));

    assertThat(roster.transition(GOLEM, GolemRoster.Status.ACTIVE, GolemRoster.Status.PACKED)).isTrue();
    assertThat(roster.transition(GOLEM, GolemRoster.Status.PACKED, GolemRoster.Status.ACTIVE)).isTrue();
    assertThat(roster.transition(GOLEM, GolemRoster.Status.ACTIVE, GolemRoster.Status.SHATTERED)).isTrue();
    assertThat(roster.transition(GOLEM, GolemRoster.Status.SHATTERED, GolemRoster.Status.ACTIVE)).isTrue();
    assertThat(roster.transition(GOLEM, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING)).isTrue();
    assertThat(roster.transition(GOLEM, GolemRoster.Status.MISSING, GolemRoster.Status.ACTIVE)).isTrue();
    assertThat(roster.golem(GOLEM).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
  }

  @Test
  void illegalOrStaleTransitionsAreRejected() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));
    roster.transition(GOLEM, GolemRoster.Status.ACTIVE, GolemRoster.Status.PACKED);

    assertThat(roster.transition(GOLEM, GolemRoster.Status.ACTIVE, GolemRoster.Status.PACKED)).isFalse();
    assertThat(roster.transition(GOLEM, GolemRoster.Status.PACKED, GolemRoster.Status.SHATTERED)).isFalse();
    assertThat(roster.transition(GOLEM, GolemRoster.Status.PACKED, GolemRoster.Status.MISSING)).isFalse();
    assertThat(roster.transition(GOLEM, GolemRoster.Status.PACKED, GolemRoster.Status.PACKED)).isFalse();
    assertThat(roster.transition(OTHER, GolemRoster.Status.ACTIVE, GolemRoster.Status.PACKED)).isFalse();
    assertThat(roster.golem(GOLEM).orElseThrow().status()).isEqualTo(GolemRoster.Status.PACKED);
  }

  @Test
  void statusTransitionTableIsExplicit() {
    for (GolemRoster.Status from : GolemRoster.Status.values()) {
      for (GolemRoster.Status to : GolemRoster.Status.values()) {
        boolean expected = switch (from) {
          case ACTIVE -> to == GolemRoster.Status.PACKED || to == GolemRoster.Status.SHATTERED || to == GolemRoster.Status.MISSING;
          case PACKED, SHATTERED, MISSING -> to == GolemRoster.Status.ACTIVE;
        };
        assertThat(from.canBecome(to)).as("%s -> %s", from, to).isEqualTo(expected);
      }
    }
  }

  @Test
  void sightingUpdatesLocationAndRestoresMissingEntries() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, GolemRoster.chunkKey(0, 0), 10L));

    assertThat(roster.seen(GOLEM, NETHER, GolemRoster.chunkKey(5, 6), 20L)).isTrue();
    GolemRoster.GolemEntry moved = roster.golem(GOLEM).orElseThrow();
    assertThat(moved.world()).isEqualTo(NETHER);
    assertThat(moved.chunkKey()).isEqualTo(GolemRoster.chunkKey(5, 6));
    assertThat(moved.lastSeen()).isEqualTo(20L);

    roster.transition(GOLEM, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);
    assertThat(roster.seen(GOLEM, WORLD, GolemRoster.chunkKey(1, 1), 30L)).isTrue();
    assertThat(roster.golem(GOLEM).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
  }

  @Test
  void sightingNeverRevivesPackedShatteredOrUnknownEntries() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 10L));
    roster.transition(GOLEM, GolemRoster.Status.ACTIVE, GolemRoster.Status.PACKED);
    roster.add(binding(OTHER, 0L, 10L));
    roster.transition(OTHER, GolemRoster.Status.ACTIVE, GolemRoster.Status.SHATTERED);

    assertThat(roster.seen(GOLEM, WORLD, 9L, 20L)).isFalse();
    assertThat(roster.seen(OTHER, WORLD, 9L, 20L)).isFalse();
    assertThat(roster.seen(UUID.randomUUID(), WORLD, 9L, 20L)).isFalse();
    assertThat(roster.golem(GOLEM).orElseThrow().lastSeen()).isEqualTo(10L);
    assertThat(roster.golem(GOLEM).orElseThrow().status()).isEqualTo(GolemRoster.Status.PACKED);
  }

  @Test
  void removeReleasesTheEntryAndCapsCountEveryOtherState() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));
    roster.add(binding(OTHER, 0L, 0L));
    roster.transition(OTHER, GolemRoster.Status.ACTIVE, GolemRoster.Status.PACKED);

    assertThat(roster.golemCount()).isEqualTo(2);
    assertThat(roster.remove(GOLEM)).isTrue();
    assertThat(roster.remove(GOLEM)).isFalse();
    assertThat(roster.golem(GOLEM)).isEmpty();
    assertThat(roster.golemCount()).isEqualTo(1);
    assertThat(roster.golems()).extracting(GolemRoster.GolemEntry::id).containsExactly(OTHER);
  }

  @Test
  void conditionalRemoveOnlyRemovesAnEntryInTheExpectedState() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));
    roster.add(binding(OTHER, 0L, 0L));
    roster.transition(OTHER, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);

    assertThat(roster.remove(GOLEM, GolemRoster.Status.MISSING)).isFalse();
    assertThat(roster.golem(GOLEM)).isPresent();
    assertThat(roster.remove(OTHER, GolemRoster.Status.MISSING)).isTrue();
    assertThat(roster.remove(OTHER, GolemRoster.Status.MISSING)).isFalse();
    assertThat(roster.golem(OTHER)).isEmpty();
    assertThat(roster.remove(null, GolemRoster.Status.MISSING)).isFalse();
  }

  @Test
  void chunkKeysPackSignedCoordinates() {
    for (int[] pair : new int[][] {{0, 0}, {1, -1}, {-30000000 / 16, 30000000 / 16}, {Integer.MIN_VALUE, Integer.MAX_VALUE}}) {
      long key = GolemRoster.chunkKey(pair[0], pair[1]);
      assertThat(GolemRoster.chunkX(key)).isEqualTo(pair[0]);
      assertThat(GolemRoster.chunkZ(key)).isEqualTo(pair[1]);
    }
    assertThat(GolemRoster.chunkKey(1, 2)).isNotEqualTo(GolemRoster.chunkKey(2, 1));
  }

  @Test
  void nodesAreUniquePerBlockFace() {
    GolemRoster roster = new GolemRoster();
    GolemState.Anchor block = GolemState.Anchor.of(WORLD, 1, 64, 2);

    assertThat(roster.addNode(GolemRoster.NodeEntry.at(block, BlockFace.UP, NodeType.HARVEST))).isTrue();
    assertThat(roster.addNode(GolemRoster.NodeEntry.at(block, BlockFace.UP, NodeType.DEPOSIT))).isFalse();
    assertThat(roster.addNode(GolemRoster.NodeEntry.at(block, BlockFace.NORTH, NodeType.DEPOSIT))).isTrue();
    assertThat(roster.nodeCount()).isEqualTo(2);

    GolemRoster.NodeEntry node = roster.nodes().stream().filter(entry -> entry.face() == BlockFace.UP).findFirst().orElseThrow();
    assertThat(node.block()).isEqualTo(block);
    assertThat(node.type()).isEqualTo(NodeType.HARVEST);

    assertThat(roster.removeNode(block, BlockFace.UP)).isTrue();
    assertThat(roster.removeNode(block, BlockFace.UP)).isFalse();
    assertThat(roster.nodeCount()).isEqualTo(1);
  }

  @Test
  void rosterSurvivesGson() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, GolemRoster.chunkKey(-4, 9), 77L));
    roster.add(binding(OTHER, 0L, 0L));
    roster.transition(OTHER, GolemRoster.Status.ACTIVE, GolemRoster.Status.SHATTERED);
    roster.addNode(GolemRoster.NodeEntry.at(GolemState.Anchor.of(WORLD, -1, 5, 3), BlockFace.WEST, NodeType.FISHING));

    GolemRoster back = Json.fromJson(Json.toJson(roster, false), GolemRoster.class);

    GolemRoster.GolemEntry entry = back.golem(GOLEM).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(entry.chunkKey()).isEqualTo(GolemRoster.chunkKey(-4, 9));
    assertThat(entry.lastSeen()).isEqualTo(77L);
    assertThat(entry.core()).isEqualTo(GolemCoreType.GUARDIAN);
    assertThat(back.golem(OTHER).orElseThrow().status()).isEqualTo(GolemRoster.Status.SHATTERED);
    assertThat(back.nodes()).singleElement().satisfies(node -> {
      assertThat(node.face()).isEqualTo(BlockFace.WEST);
      assertThat(node.type()).isEqualTo(NodeType.FISHING);
      assertThat(node.block()).isEqualTo(GolemState.Anchor.of(WORLD, -1, 5, 3));
    });
    assertThat(back.add(binding(UUID.randomUUID(), 0L, 0L))).isTrue();
  }

  @Test
  void stowingSealsTheEntryToTheExactItemState() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));

    assertThat(roster.stow(GOLEM, GolemRoster.Status.SHATTERED, "{\"state\":1}")).isTrue();

    GolemRoster.GolemEntry entry = roster.golem(GOLEM).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.SHATTERED);
    assertThat(entry.digest()).isEqualTo(GolemRoster.digest("{\"state\":1}")).hasSize(64).matches("[0-9a-f]+");
    assertThat(entry.holds("{\"state\":1}")).isTrue();
    assertThat(entry.holds("{\"state\":2}")).isFalse();
    assertThat(entry.holds(null)).isFalse();
    assertThat(roster.stow(GOLEM, GolemRoster.Status.PACKED, "{\"state\":2}")).isFalse();
    assertThat(roster.golem(GOLEM).orElseThrow().holds("{\"state\":1}")).isTrue();
  }

  @Test
  void stowingOnlyMovesActiveEntriesToPackedOrShattered() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));

    assertThat(roster.stow(GOLEM, GolemRoster.Status.MISSING, "state")).isFalse();
    assertThat(roster.stow(GOLEM, GolemRoster.Status.ACTIVE, "state")).isFalse();
    assertThat(roster.stow(GOLEM, GolemRoster.Status.SHATTERED, null)).isFalse();
    assertThat(roster.stow(OTHER, GolemRoster.Status.SHATTERED, "state")).isFalse();
    assertThat(roster.stow(GOLEM, GolemRoster.Status.PACKED, "state")).isTrue();
    assertThat(roster.golem(GOLEM).orElseThrow().status()).isEqualTo(GolemRoster.Status.PACKED);
  }

  @Test
  void unstowingNeedsTheSealedStateAndClearsTheDigest() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));
    roster.stow(GOLEM, GolemRoster.Status.SHATTERED, "current");

    assertThat(roster.unstow(GOLEM, GolemRoster.Status.SHATTERED, "stale copy")).isFalse();
    assertThat(roster.unstow(GOLEM, GolemRoster.Status.PACKED, "current")).isFalse();
    assertThat(roster.golem(GOLEM).orElseThrow().status()).isEqualTo(GolemRoster.Status.SHATTERED);

    assertThat(roster.unstow(GOLEM, GolemRoster.Status.SHATTERED, "current")).isTrue();

    GolemRoster.GolemEntry entry = roster.golem(GOLEM).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(entry.digest()).isNull();
    assertThat(entry.sighting()).isEqualTo(1L);
    assertThat(roster.unstow(GOLEM, GolemRoster.Status.SHATTERED, "current")).isFalse();
  }

  @Test
  void anUnsealedOrReactivatedEntryHoldsNoItem() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));
    roster.transition(GOLEM, GolemRoster.Status.ACTIVE, GolemRoster.Status.SHATTERED);
    assertThat(roster.golem(GOLEM).orElseThrow().digest()).isNull();
    assertThat(roster.unstow(GOLEM, GolemRoster.Status.SHATTERED, "anything")).isFalse();

    roster.transition(GOLEM, GolemRoster.Status.SHATTERED, GolemRoster.Status.ACTIVE);
    roster.stow(GOLEM, GolemRoster.Status.SHATTERED, "sealed");
    roster.transition(GOLEM, GolemRoster.Status.SHATTERED, GolemRoster.Status.ACTIVE);

    assertThat(roster.golem(GOLEM).orElseThrow().digest()).isNull();
    assertThat(roster.golem(GOLEM).orElseThrow().holds("sealed")).isFalse();
  }

  @Test
  void theDigestSurvivesGson() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));
    roster.stow(GOLEM, GolemRoster.Status.SHATTERED, "sealed");

    GolemRoster back = Json.fromJson(Json.toJson(roster, false), GolemRoster.class);

    assertThat(back.golem(GOLEM).orElseThrow().holds("sealed")).isTrue();
  }

  @Test
  void onlyActiveEntriesChangeTheirMasterworkMark() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));
    roster.add(binding(OTHER, 0L, 0L));
    roster.transition(OTHER, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);

    assertThat(roster.golem(GOLEM).orElseThrow().masterwork()).isFalse();
    assertThat(roster.markMasterwork(GOLEM, true)).isTrue();
    assertThat(roster.markMasterwork(GOLEM, true)).isFalse();
    assertThat(roster.markMasterwork(OTHER, true)).isFalse();
    assertThat(roster.markMasterwork(UUID.randomUUID(), true)).isFalse();
    assertThat(roster.markMasterwork(null, true)).isFalse();

    assertThat(roster.golem(GOLEM).orElseThrow().masterwork()).isTrue();
    assertThat(roster.golem(OTHER).orElseThrow().masterwork()).isFalse();
    assertThat(roster.markMasterwork(GOLEM, false)).isTrue();
    assertThat(roster.golem(GOLEM).orElseThrow().masterwork()).isFalse();
  }

  @Test
  void theMasterworkCountIncludesUnloadedPackedAndShatteredGolems() {
    GolemRoster roster = new GolemRoster();
    UUID packed = UUID.randomUUID();
    UUID shattered = UUID.randomUUID();
    UUID plain = UUID.randomUUID();
    for (UUID id : new UUID[] {GOLEM, OTHER, packed, shattered, plain}) {
      roster.add(binding(id, 0L, 0L));
    }
    roster.markMasterwork(GOLEM, true);
    roster.markMasterwork(OTHER, true);
    roster.markMasterwork(packed, true);
    roster.markMasterwork(shattered, true);

    roster.transition(OTHER, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);
    roster.stow(packed, GolemRoster.Status.PACKED, "packed");
    roster.stow(shattered, GolemRoster.Status.SHATTERED, "shattered");
    roster.seen(GOLEM, NETHER, GolemRoster.chunkKey(2, 2), 50L);

    assertThat(roster.masterworkCount()).isEqualTo(4);
    assertThat(roster.golem(OTHER).orElseThrow().masterwork()).isTrue();
    assertThat(roster.golem(packed).orElseThrow().masterwork()).isTrue();
    assertThat(roster.golem(shattered).orElseThrow().masterwork()).isTrue();
    assertThat(roster.golem(GOLEM).orElseThrow().masterwork()).isTrue();

    roster.unstow(shattered, GolemRoster.Status.SHATTERED, "shattered");
    roster.remove(packed);

    assertThat(roster.golem(shattered).orElseThrow().masterwork()).isTrue();
    assertThat(roster.masterworkCount()).isEqualTo(3);
  }

  @Test
  void markingAMasterworkWithinTheCapChecksTheCountAndMarksInOneStep() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));
    roster.add(binding(OTHER, 0L, 0L));
    UUID missing = UUID.randomUUID();
    roster.add(binding(missing, 0L, 0L));
    roster.transition(missing, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);

    assertThat(roster.markMasterworkWithin(missing, 3)).isEqualTo(GolemRoster.MasterworkMark.UNAVAILABLE);
    assertThat(roster.markMasterworkWithin(UUID.randomUUID(), 3)).isEqualTo(GolemRoster.MasterworkMark.UNAVAILABLE);
    assertThat(roster.markMasterworkWithin(GOLEM, 0)).isEqualTo(GolemRoster.MasterworkMark.FULL);
    assertThat(roster.markMasterworkWithin(GOLEM, 1)).isEqualTo(GolemRoster.MasterworkMark.MARKED);
    assertThat(roster.markMasterworkWithin(GOLEM, 1)).isEqualTo(GolemRoster.MasterworkMark.UNAVAILABLE);
    assertThat(roster.markMasterworkWithin(OTHER, 1)).isEqualTo(GolemRoster.MasterworkMark.FULL);
    assertThat(roster.golem(OTHER).orElseThrow().masterwork()).isFalse();
    assertThat(roster.markMasterworkWithin(OTHER, 2)).isEqualTo(GolemRoster.MasterworkMark.MARKED);
    assertThat(roster.masterworkCount()).isEqualTo(2);
  }

  @Test
  void aNewEntryStartsWithoutASighting() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));

    assertThat(roster.golem(GOLEM).orElseThrow().sighting()).isZero();
  }

  @Test
  void onlyACopyOlderThanTheLastSightingIsOutdated() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));
    roster.raiseSighting(GOLEM, 5L);
    GolemRoster.GolemEntry entry = roster.golem(GOLEM).orElseThrow();

    assertThat(entry.outdates(4L)).isTrue();
    assertThat(entry.outdates(5L)).isFalse();
    assertThat(entry.outdates(6L)).isFalse();
  }

  @Test
  void aCopyAheadRaisesActiveAndMissingEntriesButNothingElse() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));
    roster.raiseSighting(GOLEM, 4L);
    roster.add(binding(OTHER, 0L, 0L));
    roster.transition(OTHER, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);
    UUID packed = UUID.randomUUID();
    roster.add(binding(packed, 0L, 0L));
    roster.stow(packed, GolemRoster.Status.PACKED, "{\"packed\":true}");

    assertThat(roster.raiseSighting(GOLEM, 4L)).isFalse();
    assertThat(roster.raiseSighting(GOLEM, 3L)).isFalse();
    assertThat(roster.raiseSighting(GOLEM, 9L)).isTrue();
    assertThat(roster.raiseSighting(OTHER, 2L)).isTrue();
    assertThat(roster.raiseSighting(packed, 2L)).isFalse();
    assertThat(roster.raiseSighting(UUID.randomUUID(), 2L)).isFalse();
    assertThat(roster.raiseSighting(null, 2L)).isFalse();

    assertThat(roster.golem(GOLEM).orElseThrow().sighting()).isEqualTo(9L);
    assertThat(roster.golem(OTHER).orElseThrow().sighting()).isEqualTo(2L);
    assertThat(roster.golem(packed).orElseThrow().sighting()).isZero();
  }

  @Test
  void statusSealAndMasterworkChangesKeepTheSightingAndUnstowingAdvancesIt() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));
    roster.raiseSighting(GOLEM, 6L);

    roster.markMasterwork(GOLEM, true);
    roster.seen(GOLEM, NETHER, 3L, 40L);
    roster.stow(GOLEM, GolemRoster.Status.PACKED, "{\"packed\":true}");
    assertThat(roster.golem(GOLEM).orElseThrow().sighting()).isEqualTo(6L);
    roster.unstow(GOLEM, GolemRoster.Status.PACKED, "{\"packed\":true}");
    assertThat(roster.golem(GOLEM).orElseThrow().sighting()).isEqualTo(7L);
    roster.transition(GOLEM, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);

    assertThat(roster.golem(GOLEM).orElseThrow().sighting()).isEqualTo(7L);
  }

  @Test
  void theSightingSurvivesGson() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));
    roster.raiseSighting(GOLEM, 42L);

    GolemRoster back = Json.fromJson(Json.toJson(roster, false), GolemRoster.class);

    assertThat(back.golem(GOLEM).orElseThrow().sighting()).isEqualTo(42L);
    assertThat(back.raiseSighting(GOLEM, 43L)).isTrue();
  }

  @Test
  void golemsMatchTheStartOfTheirIdInAnyCase() {
    GolemRoster roster = new GolemRoster();
    UUID first = UUID.fromString("3f2a91c0-0000-0000-0000-000000000001");
    UUID second = UUID.fromString("3f2b0000-0000-0000-0000-000000000002");
    roster.add(binding(first, 0L, 0L));
    roster.add(binding(second, 0L, 0L));

    assertThat(roster.matching("3f2a")).extracting(GolemRoster.GolemEntry::id).containsExactly(first);
    assertThat(roster.matching(" 3F2A91C0 ")).extracting(GolemRoster.GolemEntry::id).containsExactly(first);
    assertThat(roster.matching(second.toString())).extracting(GolemRoster.GolemEntry::id).containsExactly(second);
    assertThat(roster.matching("3f2")).extracting(GolemRoster.GolemEntry::id).containsExactlyInAnyOrder(first, second);
    assertThat(roster.matching("ffff")).isEmpty();
    assertThat(roster.matching(" ")).isEmpty();
    assertThat(roster.matching(null)).isEmpty();
  }

  @Test
  void theMasterworkMarkSurvivesGson() {
    GolemRoster roster = new GolemRoster();
    roster.add(binding(GOLEM, 0L, 0L));
    roster.markMasterwork(GOLEM, true);

    GolemRoster back = Json.fromJson(Json.toJson(roster, false), GolemRoster.class);

    assertThat(back.golem(GOLEM).orElseThrow().masterwork()).isTrue();
    assertThat(back.masterworkCount()).isEqualTo(1);
  }

  private static GolemRoster.Binding binding(UUID id, long chunkKey, long seenAt) {
    return new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, chunkKey, seenAt);
  }
}
