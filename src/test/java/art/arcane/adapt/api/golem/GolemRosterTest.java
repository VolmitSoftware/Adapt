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

  private static GolemRoster.Binding binding(UUID id, long chunkKey, long seenAt) {
    return new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, chunkKey, seenAt);
  }
}
