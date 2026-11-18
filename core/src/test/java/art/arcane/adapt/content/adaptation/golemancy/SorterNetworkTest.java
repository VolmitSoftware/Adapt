package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import art.arcane.adapt.api.golem.node.RedstoneMode;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SorterNetworkTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000026e1");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000026e2");
  private static final NodeKey INPUT = new NodeKey(SorterYard.WORLD, 0, 64, 0, BlockFace.UP);
  private static final long SCAN_MILLIS = 2_000L;
  private static final NodeTagLookup NO_TAGS = new NodeTagLookup() {
    @Override
    public boolean exists(String tag) {
      return false;
    }

    @Override
    public boolean tagged(String tag, Id material) {
      return false;
    }
  };

  private final SorterYard yard = new SorterYard();
  private final GolemSorterCore.Config config = new GolemSorterCore.Config();
  private final SorterIndex index = new SorterIndex(() -> 64, () -> 30_000L);
  private SorterNetwork network;
  private GolemSorterCore core;
  private NodeRecord input = NodeRecord.builder(OWNER, NodeType.INPUT, BlockFace.UP).build();

  @BeforeEach
  void setUp() {
    core = mock(GolemSorterCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(core.groups()).thenReturn(Map.of(Id.minecraft("oak_log"), "logs", Id.minecraft("birch_log"), "logs"));
    yard.permitThrough(core);
    network = new SorterNetwork(core, yard.space(), index, NO_TAGS, yard.items::variant, () -> SCAN_MILLIS);
    yard.chest(0, 64, 0, yard.items.item(Id.minecraft("cobblestone"), 5));
  }

  @Test
  void theInputServesAccessibleContainersInsideItsRadiusOnce() {
    yard.chest(3, 64, 0, yard.items.item(Id.minecraft("cobblestone"), 1));
    yard.doubleChest(0, 1, 64, 5, yard.items.item(Id.minecraft("dirt"), 1));
    yard.chest(20, 64, 0, yard.items.item(Id.minecraft("stone"), 1));
    yard.chest(0, 64, -3, yard.items.item(Id.minecraft("stone"), 1));
    yard.denied.add(yard.block(0, 64, -3));
    yard.chest(-3, 64, 0, yard.items.item(Id.minecraft("stone"), 1));
    yard.foreign.add(yard.block(-3, 64, 0));

    SorterSurvey survey = survey(1).orElseThrow();

    assertThat(served(survey, 3, 64, 0)).contains(SorterDestination.Kind.PLAIN);
    assertThat(served(survey, 0, 64, 5)).contains(SorterDestination.Kind.PLAIN);
    assertThat(served(survey, 1, 64, 5)).isEmpty();
    assertThat(served(survey, 20, 64, 0)).isEmpty();
    assertThat(served(survey, 0, 64, -3)).isEmpty();
    assertThat(served(survey, -3, 64, 0)).isEmpty();
    assertThat(survey.input().id()).isEqualTo(new ContainerId(SorterYard.WORLD, 0, 64, 0));
    assertThat(survey.input().stock().count(item -> item.type().equals(Id.minecraft("cobblestone")))).isEqualTo(5);
    assertThat(served(survey(5).orElseThrow(), 20, 64, 0)).contains(SorterDestination.Kind.PLAIN);
  }

  @Test
  void anInputsOwnDoubleChestPartnerAndOtherInputNodesContainersAreNeverDestinations() {
    yard.doubleChest(0, 1, 64, 0, yard.items.item(Id.minecraft("cobblestone"), 5));
    yard.chest(3, 64, 0, yard.items.item(Id.minecraft("cobblestone"), 1));
    yard.chest(0, 64, 3, yard.items.item(Id.minecraft("cobblestone"), 1));
    yard.node(3, 64, 0, NodeRecord.builder(STRANGER, NodeType.INPUT, BlockFace.NORTH).build());

    SorterSurvey survey = survey(1).orElseThrow();

    assertThat(served(survey, 0, 64, 0)).contains(SorterDestination.Kind.INPUT);
    assertThat(served(survey, 3, 64, 0)).isEmpty();
    assertThat(served(survey, 0, 64, 3)).contains(SorterDestination.Kind.PLAIN);
    assertThat(survey.choose(yard.items.item(Id.minecraft("cobblestone"), 1)).map(SorterDestination::id))
        .contains(new ContainerId(SorterYard.WORLD, 0, 64, 3));
  }

  @Test
  void filterNodesServeTheirOwnersInputsOnTheirChannelWhileActiveFromTheirLevel() {
    input = input.toBuilder().channel(DyeColor.BLUE).build();
    yard.chest(2, 64, 2);
    yard.node(2, 64, 2, filter(OWNER).build());
    yard.chest(-2, 64, 2);
    yard.node(-2, 64, 2, filter(OWNER).channel(DyeColor.RED).build());
    yard.chest(2, 64, -2);
    yard.node(2, 64, -2, filter(STRANGER).build());
    yard.chest(-2, 64, -2);
    yard.node(-2, 64, -2, filter(OWNER).channel(DyeColor.BLUE).data(Map.of(SorterRules.OVERFLOW, "true")).build());
    yard.chest(0, 64, 3);
    yard.node(0, 64, 3, filter(OWNER).redstone(RedstoneMode.POWERED).build());

    SorterSurvey filtering = survey(3).orElseThrow();
    assertThat(served(filtering, 2, 64, 2)).contains(SorterDestination.Kind.FILTER);
    assertThat(served(filtering, -2, 64, -2)).contains(SorterDestination.Kind.OVERFLOW);
    assertThat(served(filtering, -2, 64, 2)).isEmpty();
    assertThat(served(filtering, 2, 64, -2)).isEmpty();
    assertThat(served(filtering, 0, 64, 3)).isEmpty();
    SorterSurvey early = survey(2).orElseThrow();
    assertThat(served(early, 2, 64, 2)).isEmpty();
    assertThat(served(early, -2, 64, -2)).isEmpty();
  }

  @Test
  void anInputsRadiusSettingNarrowsWhatItServes() {
    yard.chest(5, 64, 0, yard.items.item(Id.minecraft("cobblestone"), 1));
    yard.chest(2, 64, 0, yard.items.item(Id.minecraft("cobblestone"), 1));
    input = input.toBuilder().data(Map.of(SorterRules.RADIUS, "4")).build();

    SorterSurvey survey = survey(5).orElseThrow();
    assertThat(served(survey, 2, 64, 0)).contains(SorterDestination.Kind.PLAIN);
    assertThat(served(survey, 5, 64, 0)).isEmpty();
  }

  @Test
  void containerReadsAreSharedForOneScanBelowTheIndexLevelAndLastLongerFromIt() {
    InventoryRef storage = yard.chest(3, 64, 0, yard.items.item(Id.minecraft("cobblestone"), 1));
    InventoryRef hub = yard.inventory(0, 64, 0);

    survey(4, 1_000L);
    survey(4, 1_000L + SCAN_MILLIS - 1L);
    verify(storage, times(1)).contents();
    verify(hub, times(2)).contents();
    survey(4, 1_000L + SCAN_MILLIS);
    verify(storage, times(2)).contents();

    survey(5, 1_000L + SCAN_MILLIS + 20_000L);
    verify(storage, times(2)).contents();
    survey(5, 1_000L + SCAN_MILLIS + 30_000L);
    verify(storage, times(3)).contents();
  }

  @Test
  void anInputServesItsNearestContainersUpToTheLimitAndRemembersWhereTheyAre() {
    config.maxServedContainers = 2;
    yard.chest(5, 64, 0, yard.items.item(Id.minecraft("cobblestone"), 1));
    yard.chest(2, 64, 0, yard.items.item(Id.minecraft("cobblestone"), 1));
    yard.chest(0, 64, 3, yard.items.item(Id.minecraft("cobblestone"), 1));

    SorterSurvey survey = survey(1).orElseThrow();
    assertThat(served(survey, 2, 64, 0)).isPresent();
    assertThat(served(survey, 0, 64, 3)).isPresent();
    assertThat(served(survey, 5, 64, 0)).isEmpty();

    config.maxServedContainers = 8;
    yard.chest(1, 64, 1, yard.items.item(Id.minecraft("cobblestone"), 1));
    assertThat(served(survey(1, 1_000L + SorterNetwork.SERVED_MILLIS - 1L).orElseThrow(), 1, 64, 1)).isEmpty();
    assertThat(served(survey(1, 1_000L + SorterNetwork.SERVED_MILLIS).orElseThrow(), 1, 64, 1)).isPresent();
  }

  @Test
  void containerAccessIsAskedOncePerContainerForAFewSeconds() {
    yard.chest(3, 64, 0, yard.items.item(Id.minecraft("cobblestone"), 1));

    survey(1, 1_000L);
    survey(1, 1_000L + SorterNetwork.ACCESS_MILLIS - 1L);
    verify(core, times(1)).canAccessChest(yard.owner, new Loc(yard.world, 3, 64, 0));
    survey(1, 1_000L + SorterNetwork.ACCESS_MILLIS);
    verify(core, times(2)).canAccessChest(yard.owner, new Loc(yard.world, 3, 64, 0));
  }

  @Test
  void thereIsNoSurveyWithoutAnAccessibleInputContainer() {
    yard.denied.add(yard.block(0, 64, 0));
    assertThat(survey(1)).isEmpty();
    yard.denied.clear();
    assertThat(network.survey(yard.access(), yard.world, new NodeKey(SorterYard.WORLD, 9, 64, 9, BlockFace.UP), input, 1, 0L)).isEmpty();
  }

  @Test
  void theMatchModeFollowsTheInputAndTheLevel() {
    yard.chest(3, 64, 0, yard.items.item(Id.minecraft("oak_log"), 1), yard.items.item(Id.minecraft("enchanted_book"), "mending", 1));
    ItemRef birch = yard.items.item(Id.minecraft("birch_log"), 1);
    ItemRef sharpness = yard.items.item(Id.minecraft("enchanted_book"), "sharpness", 1);

    assertThat(survey(1).orElseThrow().choose(birch)).isEmpty();
    assertThat(survey(2).orElseThrow().choose(birch)).isPresent();
    assertThat(survey(3).orElseThrow().choose(sharpness)).isPresent();
    assertThat(survey(4).orElseThrow().choose(sharpness)).isEmpty();
    input = input.toBuilder().data(Map.of(SorterRules.MATCH, "groups")).build();
    assertThat(survey(5).orElseThrow().choose(sharpness)).isPresent();
    input = input.toBuilder().data(Map.of(SorterRules.MATCH, "exact")).build();
    assertThat(survey(5).orElseThrow().choose(birch)).isEmpty();
  }

  private Optional<SorterSurvey> survey(int level) {
    return survey(level, 1_000L);
  }

  private Optional<SorterSurvey> survey(int level, long now) {
    return network.survey(yard.access(), yard.world, INPUT, input, level, now);
  }

  private static NodeRecord.Builder filter(UUID owner) {
    return NodeRecord.builder(owner, NodeType.FILTER, BlockFace.UP).filterItems(List.of());
  }

  private static Optional<SorterDestination.Kind> served(SorterSurvey survey, int x, int y, int z) {
    return survey.destination(new ContainerId(SorterYard.WORLD, x, y, z)).map(SorterDestination::kind);
  }
}
