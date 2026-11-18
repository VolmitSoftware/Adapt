package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.item.ItemRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SorterSurveyTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000026c1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000026c2");
  private static final String LOGS = "#minecraft:logs";
  private static final NodeTagLookup TAGS = new NodeTagLookup() {
    @Override
    public boolean exists(String tag) {
      return LOGS.equals(tag);
    }

    @Override
    public boolean tagged(String tag, Id material) {
      return LOGS.equals(tag) && Set.of(Id.minecraft("oak_log"), Id.minecraft("birch_log")).contains(material);
    }
  };
  private static final Map<Id, String> GROUPS = Map.of(Id.minecraft("oak_log"), "logs", Id.minecraft("birch_log"), "logs");

  private final SorterItems items = new SorterItems();
  private final GolemSorterCore.Config config = new GolemSorterCore.Config();
  private final List<SorterDestination> destinations = new ArrayList<>();
  private SorterDestination input = destination(0, SorterDestination.Kind.INPUT, null);

  @Test
  void itemsGoToTheNearestContainerThatAlreadyHoldsThemAndSkipFullOnes() {
    SorterDestination far = plain(3, items.item(Id.minecraft("cobblestone"), 10));
    SorterDestination near = plain(2, items.item(Id.minecraft("cobblestone"), 3));
    plain(1, items.item(Id.minecraft("dirt"), 1));
    plainFull(1, Id.minecraft("cobblestone"));
    SorterSurvey survey = survey(SorterRules.Match.EXACT, 1);

    assertThat(survey.choose(items.item(Id.minecraft("cobblestone"), 1))).containsSame(near);
    assertThat(survey.choose(items.item(Id.minecraft("stone"), 1))).isEmpty();
    destinations.remove(near);
    assertThat(survey(SorterRules.Match.EXACT, 1).choose(items.item(Id.minecraft("cobblestone"), 1))).containsSame(far);
  }

  @Test
  void itemGroupsAddContainersHoldingAnItemOfTheSameGroup() {
    SorterDestination logs = plain(2, items.item(Id.minecraft("oak_log"), 5));

    assertThat(survey(SorterRules.Match.EXACT, 2).choose(items.item(Id.minecraft("birch_log"), 1))).isEmpty();
    assertThat(survey(SorterRules.Match.GROUPS, 2).choose(items.item(Id.minecraft("birch_log"), 1))).containsSame(logs);
    assertThat(survey(SorterRules.Match.GROUPS, 2).choose(items.item(Id.minecraft("stone"), 1))).isEmpty();
  }

  @Test
  void filterNodesComeFirstByPriorityThenDistanceFromTheirLevel() {
    SorterDestination low = filter(4, filterRecord(3, true, items.item(Id.minecraft("cobblestone"), 1)));
    SorterDestination high = filter(5, filterRecord(5, true, items.item(Id.minecraft("cobblestone"), 1)));
    SorterDestination holding = plain(1, items.item(Id.minecraft("cobblestone"), 2));

    assertThat(survey(SorterRules.Match.GROUPS, 3).choose(items.item(Id.minecraft("cobblestone"), 1))).containsSame(high);
    assertThat(survey(SorterRules.Match.GROUPS, 2).choose(items.item(Id.minecraft("cobblestone"), 1))).containsSame(holding);
    destinations.remove(high);
    assertThat(survey(SorterRules.Match.GROUPS, 3).choose(items.item(Id.minecraft("cobblestone"), 1))).containsSame(low);
  }

  @Test
  void filterTagsMatchByTypeAndAnEmptyFilterReceivesNothingUnlessItIsTheOverflow() {
    SorterDestination tagged = filter(3, filterRecord(3, true).toBuilder().filterTags(List.of(LOGS)).build());
    filter(1, filterRecord(3, true));
    SorterDestination overflow = destination(6, SorterDestination.Kind.OVERFLOW, filterRecord(3, true));
    SorterDestination holding = plain(2, items.item(Id.minecraft("cobblestone"), 2));
    SorterSurvey survey = survey(SorterRules.Match.EXACT, 3);

    assertThat(survey.choose(items.item(Id.minecraft("oak_log"), 1))).containsSame(tagged);
    assertThat(survey.choose(items.item(Id.minecraft("cobblestone"), 1))).containsSame(holding);
    assertThat(survey.choose(items.item(Id.minecraft("stone"), 1))).containsSame(overflow);
    assertThat(survey(SorterRules.Match.EXACT, 2).choose(items.item(Id.minecraft("stone"), 1))).isEmpty();
  }

  @Test
  void anOverflowNodeStillAppliesItsOwnFilter() {
    destination(6, SorterDestination.Kind.OVERFLOW, filterRecord(3, false, items.item(Id.minecraft("dirt"), 1)));
    SorterSurvey survey = survey(SorterRules.Match.EXACT, 3);

    assertThat(survey.choose(items.item(Id.minecraft("dirt"), 1))).isEmpty();
    assertThat(survey.choose(items.item(Id.minecraft("stone"), 1))).isPresent();
  }

  @Test
  void preciseMatchingSeparatesEnchantedBooksAndPotionsFromLevelFour() {
    SorterDestination books = plain(2, items.item(Id.minecraft("enchanted_book"), "mending", 1));
    SorterDestination healing = filter(3, filterRecord(3, true, items.item(Id.minecraft("potion"), "healing", 1)));
    ItemRef sharpness = items.item(Id.minecraft("enchanted_book"), "sharpness", 1);
    ItemRef swiftness = items.item(Id.minecraft("potion"), "swiftness", 1);

    assertThat(survey(SorterRules.Match.GROUPS, 4).choose(sharpness)).containsSame(books);
    assertThat(survey(SorterRules.Match.GROUPS, 4).choose(swiftness)).containsSame(healing);
    assertThat(survey(SorterRules.Match.PRECISE, 4).choose(sharpness)).isEmpty();
    assertThat(survey(SorterRules.Match.PRECISE, 4).choose(swiftness)).isEmpty();
    assertThat(survey(SorterRules.Match.PRECISE, 4).choose(items.item(Id.minecraft("enchanted_book"), "mending", 1))).containsSame(books);
    assertThat(survey(SorterRules.Match.PRECISE, 4).choose(items.item(Id.minecraft("potion"), "healing", 1))).containsSame(healing);
  }

  @Test
  void shulkerBoxesMoveOnlyFromTheirLevel() {
    SorterDestination boxes = plain(2, items.item(Id.minecraft("shulker_box"), 1));

    assertThat(survey(SorterRules.Match.GROUPS, 3).choose(items.item(Id.minecraft("shulker_box"), 1))).isEmpty();
    assertThat(survey(SorterRules.Match.PRECISE, 4).choose(items.item(Id.minecraft("shulker_box"), 1))).containsSame(boxes);
  }

  @Test
  void aShulkerBoxNeverGoesIntoAShulkerBox() {
    SorterDestination box = destination(1, SorterDestination.Kind.OVERFLOW, filterRecord(3, true), true);
    SorterDestination chest = destination(4, SorterDestination.Kind.OVERFLOW, filterRecord(3, true), false);
    SorterSurvey survey = survey(SorterRules.Match.PRECISE, 4);

    assertThat(survey.choose(items.item(Id.minecraft("shulker_box"), 1))).containsSame(chest);
    assertThat(survey.choose(items.item(Id.minecraft("stone"), 1))).containsSame(box);
    destinations.remove(chest);
    assertThat(survey(SorterRules.Match.PRECISE, 4).choose(items.item(Id.minecraft("red_shulker_box"), 1))).isEmpty();
  }

  @Test
  void aShulkerBoxFilterNodeNeverAsksToBeRestockedWithShulkerBoxes() {
    destination(1, SorterDestination.Kind.FILTER, restocking(4, filterRecord(3, true, items.item(Id.minecraft("shulker_box"), 1),
        items.item(Id.minecraft("torch"), 1))), true);
    plain(5, items.item(Id.minecraft("shulker_box"), 3), items.item(Id.minecraft("torch"), 10));

    assertThat(survey(SorterRules.Match.PRECISE, 4).restockNeeds()).extracting(need -> need.item().type()).containsExactly(Id.minecraft("torch"));
  }

  @Test
  void aRestockingFilterTakesOnlyItsItemsUpToItsTarget() {
    NodeRecord restock = restocking(32, filterRecord(3, true, items.item(Id.minecraft("torch"), 1)).toBuilder().filterTags(List.of(LOGS)).build());
    SorterDestination workshop = filter(3, restock, items.item(Id.minecraft("torch"), 20));
    SorterDestination storage = plain(5, items.item(Id.minecraft("torch"), 64), items.item(Id.minecraft("oak_log"), 1));
    SorterSurvey survey = survey(SorterRules.Match.PRECISE, 4);

    assertThat(survey.choose(items.item(Id.minecraft("torch"), 1))).containsSame(workshop);
    assertThat(survey.cap(workshop, items.item(Id.minecraft("torch"), 1))).isEqualTo(12);
    assertThat(survey.cap(storage, items.item(Id.minecraft("torch"), 1))).isEqualTo(Integer.MAX_VALUE);
    assertThat(survey.choose(items.item(Id.minecraft("oak_log"), 1))).containsSame(storage);

    destinations.remove(workshop);
    filter(3, restock, items.item(Id.minecraft("torch"), 32));
    assertThat(survey(SorterRules.Match.PRECISE, 4).choose(items.item(Id.minecraft("torch"), 1))).containsSame(storage);
    assertThat(survey(SorterRules.Match.PRECISE, 3).cap(workshop, items.item(Id.minecraft("torch"), 1))).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void restockNeedsListWhatIsMissingAndTakeFromTheNearestContainerThatIsNotAnotherRestockForIt() {
    SorterDestination workshop = filter(1, restocking(64, filterRecord(3, true, items.item(Id.minecraft("torch"), 1), items.item(Id.minecraft("bread"), 1))),
        items.item(Id.minecraft("torch"), 10), items.item(Id.minecraft("bread"), 64));
    SorterDestination rival = filter(2, restocking(64, filterRecord(3, true, items.item(Id.minecraft("torch"), 1))), items.item(Id.minecraft("torch"), 100));
    SorterDestination storage = plain(6, items.item(Id.minecraft("torch"), 30));
    input = destination(0, SorterDestination.Kind.INPUT, null, items.item(Id.minecraft("torch"), 5));
    SorterSurvey survey = survey(SorterRules.Match.PRECISE, 4);

    assertThat(survey.restockNeeds()).extracting(need -> need.target().id().x() + " " + need.item().type().legacyName() + " " + need.deficit())
        .containsExactly("1 TORCH 54");
    SorterSurvey.RestockNeed need = survey.restockNeeds().getFirst();
    assertThat(survey.restockSource(need)).containsSame(input);
    input = destination(0, SorterDestination.Kind.INPUT, null);
    assertThat(survey(SorterRules.Match.PRECISE, 4).restockSource(survey(SorterRules.Match.PRECISE, 4).restockNeeds().getFirst())).containsSame(storage);
    assertThat(survey(SorterRules.Match.PRECISE, 3).restockNeeds()).isEmpty();
    assertThat(survey.destination(rival.id())).isPresent();
  }

  @Test
  void fetchSourcesHoldTheExactItemAndTheListingAddsUpEveryServedContainer() {
    SorterDestination first = plain(4, items.item(Id.minecraft("enchanted_book"), "mending", 1), items.item(Id.minecraft("cobblestone"), 20));
    SorterDestination second = plain(2, items.item(Id.minecraft("cobblestone"), 64), items.item(Id.minecraft("enchanted_book"), "sharpness", 1));
    input = destination(0, SorterDestination.Kind.INPUT, null, items.item(Id.minecraft("cobblestone"), 1));
    SorterSurvey survey = survey(SorterRules.Match.PRECISE, 5);

    assertThat(survey.fetchSource(items.item(Id.minecraft("enchanted_book"), "mending", 1))).containsSame(first);
    assertThat(survey.fetchSource(items.item(Id.minecraft("enchanted_book"), "sharpness", 1))).containsSame(second);
    assertThat(survey.fetchSource(items.item(Id.minecraft("cobblestone"), 1))).containsSame(input);
    assertThat(survey.fetchSource(items.item(Id.minecraft("stone"), 1))).isEmpty();
    assertThat(survey.available()).extracting(entry -> entry.prototype().type().legacyName() + "/" + items.variant(entry.prototype()) + " " + entry.count())
        .containsExactly("COBBLESTONE/ 85", "ENCHANTED_BOOK/mending 1", "ENCHANTED_BOOK/sharpness 1");
  }

  @Test
  void destinationsAreFoundByTheirContainer() {
    SorterDestination holding = plain(2, items.item(Id.minecraft("cobblestone"), 2));
    SorterSurvey survey = survey(SorterRules.Match.EXACT, 1);

    assertThat(survey.destination(holding.id())).containsSame(holding);
    assertThat(survey.destination(input.id())).containsSame(input);
    assertThat(survey.destination(new ContainerId(WORLD, 9, 9, 9))).isEmpty();
    assertThat(survey.input()).isSameAs(input);
  }

  private SorterSurvey survey(SorterRules.Match mode, int level) {
    SorterMatcher matcher = new SorterMatcher(mode, mode == SorterRules.Match.EXACT ? Map.of() : GROUPS, TAGS, items::variant);
    return new SorterSurvey(matcher, config, level, input, List.copyOf(destinations));
  }

  private SorterDestination plain(int x, ItemRef... contents) {
    return destination(x, SorterDestination.Kind.PLAIN, null, contents);
  }

  private void plainFull(int x, Id type) {
    ItemRef[] full = new ItemRef[3];
    for (int i = 0; i < full.length; i++) {
      full[i] = items.item(type, 64);
    }
    destinations.add(new SorterDestination(new ContainerId(WORLD, x, 70, 0), mock(BlockRef.class), SorterStock.read(items.inventory(3, full), 0L),
        SorterDestination.Kind.PLAIN, null, 0.5D, false));
  }

  private SorterDestination filter(int x, NodeRecord record, ItemRef... contents) {
    return destination(x, SorterDestination.Kind.FILTER, record, contents);
  }

  private SorterDestination destination(int x, SorterDestination.Kind kind, NodeRecord record, ItemRef... contents) {
    return destination(x, kind, record, false, contents);
  }

  private SorterDestination destination(int x, SorterDestination.Kind kind, NodeRecord record, boolean shulkerBox, ItemRef... contents) {
    SorterDestination destination = new SorterDestination(new ContainerId(WORLD, x, 64, 0), mock(BlockRef.class),
        SorterStock.read(items.inventory(27, contents), 0L), kind, record, (double) x * x, shulkerBox);
    if (kind != SorterDestination.Kind.INPUT) {
      destinations.add(destination);
    }
    return destination;
  }

  private static NodeRecord restocking(int target, NodeRecord record) {
    return record.toBuilder().data(Map.of(SorterRules.RESTOCK_TARGET, Integer.toString(target))).build();
  }

  private static NodeRecord filterRecord(int priority, boolean whitelist, ItemRef... entries) {
    return NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.UP).priority(priority).whitelist(whitelist).filterItems(List.of(entries)).build();
  }
}
