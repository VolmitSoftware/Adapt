package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlinthServiceTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final PlinthSpot CENTER = new PlinthSpot(WORLD, 8, 64, 8);
  private static final String INTERRUPTED = "warn golemancy.plinth.interrupted";
  private static final String ANY = "ANY";
  private static final int DURATION = InfusionRecipes.MIN_DURATION_TICKS;
  private static final byte[] UNREADABLE_ITEM = "not an item".getBytes(StandardCharsets.US_ASCII);

  private final List<String> events = new ArrayList<>();
  private final FakePlinthPlatform platform = new FakePlinthPlatform(events);
  private final FakeHost host = new FakeHost(events);
  private final Map<PlinthSlot, ItemRef> stock = new HashMap<>();
  private final AtomicInteger resultsBuilt = new AtomicInteger();
  private final ItemRef result = mock(ItemRef.class);
  private final List<LogRecord> logged = new ArrayList<>();
  private final CapturingLog capture = new CapturingLog(logged);
  private final ItemOps items = mock(ItemOps.class);
  private WorldRef world;
  private PlayerRef player;
  private PlayerInventoryRef inventory;
  private PlinthSettings settings;
  private PlinthService service;

  @BeforeEach
  void setUp() {
    RegistryLookup registries = mock(RegistryLookup.class);
    lenient().when(core.getPlatform().host().registries()).thenReturn(registries);
    lenient().when(registries.exists(any(RegistryKind.class), any(Id.class))).thenReturn(true);
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    lenient().when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    platform.worlds.put(WORLD, world);
    build(CENTER);
    stock.put(PlinthSlot.CENTER, item(Id.minecraft("scrape_pottery_sherd")));
    stock.put(PlinthSlot.NORTH, item(Id.minecraft("sugar")));
    stock.put(PlinthSlot.EAST, item(Id.minecraft("feather")));
    stock.put(PlinthSlot.SOUTH, item(Id.minecraft("sugar")));
    stock.put(PlinthSlot.WEST, item(Id.minecraft("feather")));
    settings = new PlinthSettings(PlinthStructure.pedestals(PlinthStructure.DEFAULT_PEDESTALS, material -> true, message -> {
    }), swiftness(1, InfusionRecipe.RUNIC_AUGMENTS));
    player = player();
    lenient().when(core.getPlatform().host().items()).thenReturn(items);
    AdaptEnvironment.install(capture, dataFolder::toPath);
    service = new PlinthService(platform, () -> settings, () -> host);
  }

  @Test
  void sneakClickingAPedestalWithAnItemStoresExactlyOneOfIt() {
    readyChunk();
    ItemRef sugar = item(Id.minecraft("sugar"));
    holding(sugar);

    PlayerInteractEvent click = click(CENTER.slot(PlinthSlot.NORTH), Hand.MAIN_HAND);

    assertThat(click.isCancelled()).isTrue();
    PlinthSlotItem stored = platform.store.get(CENTER.slot(PlinthSlot.NORTH));
    assertThat(stored.item()).isSameAs(sugar);
    assertThat(events).containsSubsequence("take", "write 8,64,6 item", "save player", "effect LOADED");
    assertThat(platform.displays).hasSize(1);
  }

  @Test
  void theLodestoneTakesTheCenterItem() {
    readyChunk();
    ItemRef rune = item(Id.minecraft("scrape_pottery_sherd"));
    holding(rune);

    click(CENTER, Hand.MAIN_HAND);

    assertThat(platform.store.get(CENTER).item()).isSameAs(rune);
  }

  @Test
  void anEmptyHandRetrievesTheItemOnlyAfterThePlateIsSaved() {
    readyChunk();
    holding(item(Id.minecraft("sugar")));
    click(CENTER.slot(PlinthSlot.EAST), Hand.MAIN_HAND);
    ItemDisplayRef display = platform.displays.getFirst();
    holding(null);
    events.clear();

    PlayerInteractEvent click = click(CENTER.slot(PlinthSlot.EAST), Hand.MAIN_HAND);

    assertThat(click.isCancelled()).isTrue();
    assertThat(platform.store).doesNotContainKey(CENTER.slot(PlinthSlot.EAST));
    assertThat(events).containsSubsequence("write 10,64,8 empty", "save 0,0", "give");
    verify(display).remove();
  }

  @Test
  void aFailedSaveWithholdsTheRetrievedItemAndLogsTheStackTrace() {
    readyChunk();
    holding(item(Id.minecraft("sugar")));
    click(CENTER.slot(PlinthSlot.EAST), Hand.MAIN_HAND);
    holding(null);
    platform.saveFailure = new IllegalStateException("disk full");

    click(CENTER.slot(PlinthSlot.EAST), Hand.MAIN_HAND);

    assertThat(events).doesNotContain("give");
    assertThat(logged).anyMatch(record -> record.getThrown() != null);
  }

  @Test
  void aBusyPlateIsRetriedBeforeTheItemIsHandedBack() {
    readyChunk();
    holding(item(Id.minecraft("sugar")));
    click(CENTER.slot(PlinthSlot.EAST), Hand.MAIN_HAND);
    holding(null);
    platform.busySaves = 2;

    click(CENTER.slot(PlinthSlot.EAST), Hand.MAIN_HAND);
    assertThat(events).doesNotContain("give");
    platform.drainAsync();

    assertThat(events).containsSubsequence("save 0,0", "save 0,0", "save 0,0", "give");
  }

  @Test
  void clicksAreLeftAloneWithoutRunicAugmentsOrSneakingOrOnABrokenPlinth() {
    readyChunk();
    holding(item(Id.minecraft("sugar")));
    host.levels.put(InfusionRecipe.RUNIC_AUGMENTS, 0);
    assertThat(click(CENTER.slot(PlinthSlot.NORTH), Hand.MAIN_HAND).isCancelled()).isFalse();

    host.levels.put(InfusionRecipe.RUNIC_AUGMENTS, 1);
    when(player.isSneaking()).thenReturn(false);
    assertThat(click(CENTER.slot(PlinthSlot.NORTH), Hand.MAIN_HAND).isCancelled()).isFalse();

    when(player.isSneaking()).thenReturn(true);
    platform.blocks.put("6,64,8", Id.minecraft("copper_block"));
    assertThat(click(CENTER.slot(PlinthSlot.NORTH), Hand.MAIN_HAND).isCancelled()).isFalse();
    assertThat(platform.store).isEmpty();
  }

  @Test
  void compassBindingOnTheLodestoneIsNeverIntercepted() {
    readyChunk();
    holding(item(Id.minecraft("compass")));
    assertThat(click(CENTER, Hand.MAIN_HAND).isCancelled()).isFalse();

    holding(null);
    ItemRef compass = item(Id.minecraft("compass"));
    when(inventory.offHand()).thenReturn(compass);
    assertThat(click(CENTER, Hand.MAIN_HAND).isCancelled()).isFalse();
    assertThat(click(CENTER, Hand.OFF_HAND).isCancelled()).isFalse();
    assertThat(platform.store).isEmpty();
  }

  @Test
  void theOffHandClickIsSuppressedWhileTheMainHandLoads() {
    readyChunk();
    holding(item(Id.minecraft("sugar")));

    PlayerInteractEvent offHand = click(CENTER.slot(PlinthSlot.SOUTH), Hand.OFF_HAND);

    assertThat(offHand.isCancelled()).isTrue();
    assertThat(platform.store).isEmpty();
  }

  @Test
  void anOccupiedSlotAProtectedPlinthAndAnUnreadyChunkRefuseItems() {
    holding(item(Id.minecraft("sugar")));
    click(CENTER.slot(PlinthSlot.NORTH), Hand.MAIN_HAND);
    assertThat(events).contains("warn golemancy.plinth.loading");

    readyChunk();
    click(CENTER.slot(PlinthSlot.NORTH), Hand.MAIN_HAND);
    events.clear();
    holding(item(Id.minecraft("feather")));
    click(CENTER.slot(PlinthSlot.NORTH), Hand.MAIN_HAND);
    assertThat(events).containsExactly("warn golemancy.plinth.occupied");

    events.clear();
    host.canStore = false;
    click(CENTER.slot(PlinthSlot.SOUTH), Hand.MAIN_HAND);
    assertThat(events).containsExactly("warn golemancy.plinth.protected");
    assertThat(platform.store).hasSize(1);
  }

  @Test
  void aCompletedInfusionChargesLevelsRemovesTheItemsAndSavesBeforeTheResultAppears() {
    stockPlinth();
    holdCrook();

    PlayerInteractEvent start = click(CENTER, Hand.MAIN_HAND);
    tick(DURATION);

    assertThat(start.isCancelled()).isTrue();
    assertThat(events).containsSubsequence("pay golem-runic-augments 2", "write 8,64,8 empty", "save 0,0", "pop 8,65,8", "settle",
        "notify golemancy.plinth.complete", "infused swiftness_1");
    assertThat(events).doesNotContain("refund");
    assertThat(events.stream().filter(event -> event.startsWith("write") && event.endsWith("empty"))).hasSize(5);
    assertThat(platform.store).isEmpty();
    assertThat(resultsBuilt.get()).isEqualTo(1);
    for (ItemDisplayRef display : platform.displays) {
      verify(display).remove();
    }
  }

  @Test
  void aCrashBeforeTheSaveCompletesNeverCreatesTheResult() {
    stockPlinth();
    holdCrook();
    platform.deferAsync = true;

    click(CENTER, Hand.MAIN_HAND);
    tick(DURATION);

    assertThat(platform.store).isEmpty();
    assertThat(events).doesNotContain("save 0,0");
    assertThat(events.stream().filter(event -> event.startsWith("pop"))).isEmpty();
    assertThat(resultsBuilt.get()).isZero();
  }

  @Test
  void aFailedSaveRefundsTheChargeRestoresTheItemsAndWithholdsTheResult() {
    stockPlinth();
    Map<PlinthSpot, PlinthSlotItem> before = new HashMap<>(platform.store);
    holdCrook();
    platform.saveFailure = new IllegalStateException("disk full");

    click(CENTER, Hand.MAIN_HAND);
    tick(DURATION);

    assertThat(events).containsSubsequence("pay golem-runic-augments 2", "save 0,0", "refund", "warn golemancy.plinth.failed");
    assertThat(events).doesNotContain("settle");
    assertThat(events.stream().filter(event -> event.startsWith("pop"))).isEmpty();
    assertThat(resultsBuilt.get()).isZero();
    assertThat(platform.store).isEqualTo(before);
    assertThat(platform.displays).hasSize(10);
    assertThat(events.stream().filter(event -> event.startsWith("drop"))).isEmpty();
    assertThat(logged).anyMatch(record -> record.getThrown() != null);

    platform.saveFailure = null;
    events.clear();
    click(CENTER, Hand.MAIN_HAND);
    tick(DURATION);

    assertThat(events).contains("pop 8,65,8", "settle");
    assertThat(platform.store).isEmpty();
  }

  @Test
  void aSlotRefilledDuringAFailedSaveDropsItsOldItemInstead() {
    stockPlinth();
    PlinthSpot north = CENTER.slot(PlinthSlot.NORTH);
    holdCrook();
    platform.deferAsync = true;
    platform.saveFailure = new IllegalStateException("disk full");
    click(CENTER, Hand.MAIN_HAND);
    tick(DURATION);
    PlinthSlotItem refill = PlinthSlotItem.of(UUID.randomUUID(), item(Id.minecraft("stone")));
    platform.store.put(north, refill);

    platform.drainAsync();

    assertThat(platform.store.get(north)).isEqualTo(refill);
    assertThat(platform.store).hasSize(5);
    assertThat(events).contains("drop 8,64,6", "refund");
  }

  @Test
  void aRemovalThatFailsAtCompletionRefundsAndRestoresInsteadOfDelivering() {
    stockPlinth();
    holdCrook();
    click(CENTER, Hand.MAIN_HAND);
    tick(DURATION - 1);
    PlinthSpot east = CENTER.slot(PlinthSlot.EAST);
    PlinthSlotItem intruder = PlinthSlotItem.of(UUID.randomUUID(), item(Id.minecraft("stone")));
    platform.swapAfterReads(east, intruder, 1);

    tick(1);

    assertThat(events).contains("pay golem-runic-augments 2", "refund", INTERRUPTED);
    assertThat(events).doesNotContain("save 0,0", "settle");
    assertThat(events.stream().filter(event -> event.startsWith("pop"))).isEmpty();
    assertThat(platform.store).hasSize(5).containsEntry(east, intruder);

    events.clear();
    service.on(new BlockBreakEvent(mock(PlayerRef.class), block(east), true, 0));
    service.on(new BlockBreakEvent(mock(PlayerRef.class), block(CENTER), true, 0));
    platform.blocks.put("10,64,8", Id.minecraft("air"));
    platform.blocks.put("8,64,8", Id.minecraft("air"));
    tick(1);

    assertThat(events).contains("drop 10,64,8", "drop 8,64,8");
  }

  @Test
  void creativePlayersPayNothing() {
    stockPlinth();
    holdCrook();
    when(player.gameMode()).thenReturn(GameMode.CREATIVE);
    when(player.level()).thenReturn(0);

    click(CENTER, Hand.MAIN_HAND);
    tick(DURATION);

    assertThat(events.stream().filter(event -> event.startsWith("pay"))).isEmpty();
    assertThat(events).contains("pop 8,65,8");
  }

  @Test
  void walkingMoreThanEightBlocksAwayAbortsWithoutCharging() {
    stockPlinth();
    holdCrook();
    click(CENTER, Hand.MAIN_HAND);
    tick(1);

    when(player.location()).thenReturn(new Loc(world, 8.5D, 64D, 17.6D));
    tick(5);

    assertAbortedWithoutCharge(true);
  }

  @Test
  void loggingOutAbortsQuietly() {
    stockPlinth();
    holdCrook();
    click(CENTER, Hand.MAIN_HAND);

    service.on(new PlayerQuitEvent(player));
    tick(5);

    assertAbortedWithoutCharge(false);
  }

  @Test
  void breakingAPlinthBlockAbortsAtOnce() {
    stockPlinth();
    holdCrook();
    click(CENTER, Hand.MAIN_HAND);

    service.on(new BlockBreakEvent(mock(PlayerRef.class), block(CENTER.slot(PlinthSlot.WEST)), true, 0));

    assertThat(events).contains(INTERRUPTED);
    assertThat(events.stream().filter(event -> event.startsWith("pay"))).isEmpty();
  }

  @Test
  void aStructureChangeWithoutAnEventAbortsOnTheNextPulse() {
    stockPlinth();
    holdCrook();
    click(CENTER, Hand.MAIN_HAND);

    platform.blocks.put("8,64,10", Id.minecraft("copper_block"));
    tick(5);

    assertThat(events).contains(INTERRUPTED);
    assertThat(events.stream().filter(event -> event.startsWith("pay"))).isEmpty();
  }

  @Test
  void takingAnItemOffAPedestalAbortsTheRitual() {
    stockPlinth();
    holdCrook();
    click(CENTER, Hand.MAIN_HAND);
    tick(1);

    holding(null);
    when(player.isSneaking()).thenReturn(true);
    click(CENTER.slot(PlinthSlot.NORTH), Hand.MAIN_HAND);
    tick(5);

    assertThat(events).contains(INTERRUPTED);
    assertThat(events.stream().filter(event -> event.startsWith("pay"))).isEmpty();
    assertThat(platform.store).hasSize(4);
    assertThat(events.stream().filter(event -> event.startsWith("pop"))).isEmpty();
  }

  @Test
  void aRitualNeedsAMatchingUnlockedPermittedAffordableRecipe() {
    stock.put(PlinthSlot.NORTH, item(Id.minecraft("feather")));
    stockPlinth();
    holdCrook();
    click(CENTER, Hand.MAIN_HAND);
    assertThat(events).containsExactly("warn golemancy.plinth.no_recipe");

    settings = new PlinthSettings(settings.pedestals(), recipes(Map.of("anything", new InfusionRecipe().result("golemancy:rune/order/3")
        .center(ANY).pedestals(ANY, ANY, ANY, ANY).essence(10).duration(DURATION).unlock(InfusionRecipe.RUNIC_AUGMENTS, 5))));
    host.levels.put(InfusionRecipe.RUNIC_AUGMENTS, 3);
    events.clear();
    click(CENTER, Hand.MAIN_HAND);
    assertThat(events).containsExactly("warn golemancy.plinth.locked");

    host.levels.put(InfusionRecipe.RUNIC_AUGMENTS, 5);
    host.canInfuse = false;
    events.clear();
    click(CENTER, Hand.MAIN_HAND);
    assertThat(events).containsExactly("warn golemancy.plinth.protected");

    host.canInfuse = true;
    when(player.level()).thenReturn(9);
    events.clear();
    click(CENTER, Hand.MAIN_HAND);
    assertThat(events).containsExactly("warn golemancy.plinth.levels", "denied");
    tick(5);
    assertThat(platform.store).hasSize(5);
  }

  @Test
  void loadingTheLastItemOfAnAffordableInfusionShowsItsResultAndCost() {
    stockPlinthExcept(PlinthSlot.WEST);
    holding(stock.get(PlinthSlot.WEST));

    click(CENTER.slot(PlinthSlot.WEST), Hand.MAIN_HAND);

    assertThat(platform.told).hasSize(1);
    FakePlinthPlatform.Told preview = platform.told.getFirst();
    assertThat(preview.warning()).isFalse();
    assertThat(preview.key()).isEqualTo(GolemancyMessages.PLINTH_PREVIEW);
    assertThat(preview.values()).containsExactly(entry("result", "Result swiftness_1"), entry("levels", 2));
    assertThat(AdaptLanguage.text(preview.key(), preview.arguments())).isEqualTo("Result swiftness_1 costs 2 levels");
  }

  @Test
  void anUnaffordableInfusionPreviewIsRedAndStatesTheLevelsYouHave() {
    when(player.level()).thenReturn(1);
    stockPlinthExcept(PlinthSlot.WEST);
    holding(stock.get(PlinthSlot.WEST));

    click(CENTER.slot(PlinthSlot.WEST), Hand.MAIN_HAND);

    assertThat(platform.told).hasSize(1);
    FakePlinthPlatform.Told preview = platform.told.getFirst();
    assertThat(preview.warning()).isTrue();
    assertThat(preview.key()).isEqualTo(GolemancyMessages.PLINTH_PREVIEW_SHORT);
    assertThat(preview.values()).containsExactly(entry("result", "Result swiftness_1"), entry("levels", 2), entry("have", 1));
    assertThat(AdaptLanguage.text(preview.key(), preview.arguments())).isEqualTo("Result swiftness_1 costs 2 levels and you have 1");
    assertThat(events).doesNotContain("denied");
  }

  @Test
  void creativePlayersSeeTheInfusionIsFree() {
    when(player.gameMode()).thenReturn(GameMode.CREATIVE);
    when(player.level()).thenReturn(0);
    stockPlinthExcept(PlinthSlot.WEST);
    holding(stock.get(PlinthSlot.WEST));

    click(CENTER.slot(PlinthSlot.WEST), Hand.MAIN_HAND);

    assertThat(platform.told).hasSize(1);
    FakePlinthPlatform.Told preview = platform.told.getFirst();
    assertThat(preview.warning()).isFalse();
    assertThat(preview.key()).isEqualTo(GolemancyMessages.PLINTH_PREVIEW_FREE);
    assertThat(preview.values()).containsExactly(entry("result", "Result swiftness_1"));
    assertThat(AdaptLanguage.text(preview.key(), preview.arguments())).isEqualTo("Result swiftness_1 is free");
  }

  @Test
  void aRecipeThatCostsNothingIsFreeToEveryone() {
    settings = new PlinthSettings(settings.pedestals(), recipes(Map.of("swiftness_1", new InfusionRecipe().result("golemancy:rune/swiftness/1")
        .center("SCRAPE_POTTERY_SHERD").pedestals("SUGAR", "SUGAR", "FEATHER", "FEATHER").essence(0).duration(DURATION).unlock(InfusionRecipe.RUNIC_AUGMENTS, 1))));
    when(player.level()).thenReturn(0);
    stockPlinthExcept(PlinthSlot.WEST);
    holding(stock.get(PlinthSlot.WEST));

    click(CENTER.slot(PlinthSlot.WEST), Hand.MAIN_HAND);

    assertThat(platform.told).extracting(FakePlinthPlatform.Told::key).containsExactly(GolemancyMessages.PLINTH_PREVIEW_FREE);
  }

  @Test
  void loadingAnItemThatFormsNoInfusionShowsNoCost() {
    stockPlinthExcept(PlinthSlot.WEST);
    holding(item(Id.minecraft("sugar")));

    click(CENTER.slot(PlinthSlot.WEST), Hand.MAIN_HAND);

    assertThat(platform.store).hasSize(5);
    assertThat(platform.told).isEmpty();
  }

  @Test
  void anInfusionYouHaveNotUnlockedShowsNoCost() {
    settings = new PlinthSettings(settings.pedestals(), swiftness(5, InfusionRecipe.RUNIC_AUGMENTS));
    host.levels.put(InfusionRecipe.RUNIC_AUGMENTS, 3);
    stockPlinthExcept(PlinthSlot.WEST);
    holding(stock.get(PlinthSlot.WEST));

    click(CENTER.slot(PlinthSlot.WEST), Hand.MAIN_HAND);

    assertThat(platform.store).hasSize(5);
    assertThat(platform.told).isEmpty();
  }

  @Test
  void takingAnItemOffAFullPlinthShowsNoCost() {
    stockPlinth();
    holding(null);

    click(CENTER.slot(PlinthSlot.WEST), Hand.MAIN_HAND);

    assertThat(platform.store).hasSize(4);
    assertThat(events).contains("give");
    assertThat(platform.told).isEmpty();
  }

  @Test
  void startingARitualShowsTheCostAndTakesNoLevelsUntilItCompletes() {
    stockPlinth();
    holdCrook();

    click(CENTER, Hand.MAIN_HAND);
    tick(DURATION - 1);

    assertThat(platform.told).extracting(FakePlinthPlatform.Told::key).containsExactly(GolemancyMessages.PLINTH_PREVIEW);
    assertThat(events.stream().filter(event -> event.startsWith("pay"))).isEmpty();
    verify(player, never()).giveExpLevels(anyInt());

    tick(1);

    assertThat(events.stream().filter(event -> event.startsWith("pay"))).containsExactly("pay golem-runic-augments 2");
  }

  @Test
  void startingAnUnaffordableInfusionRefusesWithTheCostAndYourLevelsAndChargesNothing() {
    when(player.level()).thenReturn(1);
    stockPlinth();
    holdCrook();

    click(CENTER, Hand.MAIN_HAND);
    tick(DURATION);

    assertThat(events).containsExactly("warn golemancy.plinth.levels", "denied");
    assertThat(platform.told).hasSize(1);
    FakePlinthPlatform.Told refusal = platform.told.getFirst();
    assertThat(refusal.warning()).isTrue();
    assertThat(refusal.key()).isEqualTo(GolemancyMessages.PLINTH_LEVELS);
    assertThat(refusal.values()).containsExactly(entry("levels", 2), entry("have", 1));
    assertThat(AdaptLanguage.text(refusal.key(), refusal.arguments()))
        .isEqualTo("Not enough experience: this infusion costs 2 levels and you have 1");
    assertThat(platform.store).hasSize(5);
    verify(player, never()).giveExpLevels(anyInt());
  }

  @Test
  void levelsLostDuringTheRitualRefuseAtCompletionWithoutCharging() {
    stockPlinth();
    holdCrook();
    click(CENTER, Hand.MAIN_HAND);
    tick(DURATION - 1);
    when(player.level()).thenReturn(1);

    tick(1);

    assertThat(events).contains("warn golemancy.plinth.levels", "denied");
    assertThat(events.stream().filter(event -> event.startsWith("pay") || event.startsWith("pop"))).isEmpty();
    FakePlinthPlatform.Told refusal = platform.told.getLast();
    assertThat(refusal.key()).isEqualTo(GolemancyMessages.PLINTH_LEVELS);
    assertThat(refusal.values()).containsExactly(entry("levels", 2), entry("have", 1));
    assertThat(platform.store).hasSize(5);
    verify(player, never()).giveExpLevels(anyInt());
  }

  @Test
  void aChargeRefusedAtCompletionStillRefusesWithTheCostArguments() {
    stockPlinth();
    host.refuseCharge = true;
    holdCrook();

    click(CENTER, Hand.MAIN_HAND);
    tick(DURATION);

    FakePlinthPlatform.Told refusal = platform.told.getLast();
    assertThat(refusal.key()).isEqualTo(GolemancyMessages.PLINTH_LEVELS);
    assertThat(refusal.values()).containsExactly(entry("levels", 2), entry("have", 30));
    assertThat(events).contains("denied");
    assertThat(events).doesNotContain("settle");
    assertThat(platform.store).hasSize(5);
  }

  @Test
  void anIdenticalPreviewDoesNotRepeatInsideTheThrottleWindow() {
    stockPlinthExcept(PlinthSlot.WEST);
    PlinthSpot west = CENTER.slot(PlinthSlot.WEST);
    ItemRef feather = stock.get(PlinthSlot.WEST);
    holding(feather);
    click(west, Hand.MAIN_HAND);
    holding(null);
    click(west, Hand.MAIN_HAND);
    holding(feather);

    platform.now += PlinthService.PREVIEW_THROTTLE_MILLIS - 1L;
    click(west, Hand.MAIN_HAND);

    assertThat(platform.told).hasSize(1);
    assertThat(platform.store).hasSize(5);
    holding(null);
    click(west, Hand.MAIN_HAND);
    holding(feather);

    platform.now += 1L;
    click(west, Hand.MAIN_HAND);

    assertThat(platform.told).extracting(FakePlinthPlatform.Told::key)
        .containsExactly(GolemancyMessages.PLINTH_PREVIEW, GolemancyMessages.PLINTH_PREVIEW);
  }

  @Test
  void aPreviewThatChangedShowsAtOnceInsideTheThrottleWindow() {
    stockPlinthExcept(PlinthSlot.WEST);
    PlinthSpot west = CENTER.slot(PlinthSlot.WEST);
    ItemRef feather = stock.get(PlinthSlot.WEST);
    holding(feather);
    click(west, Hand.MAIN_HAND);
    holding(null);
    click(west, Hand.MAIN_HAND);
    holding(feather);
    when(player.level()).thenReturn(1);

    click(west, Hand.MAIN_HAND);

    assertThat(platform.told).extracting(FakePlinthPlatform.Told::key)
        .containsExactly(GolemancyMessages.PLINTH_PREVIEW, GolemancyMessages.PLINTH_PREVIEW_SHORT);
  }

  @Test
  void aViewerWhoLeavesStartsWithoutAThrottledPreview() {
    stockPlinthExcept(PlinthSlot.WEST);
    PlinthSpot west = CENTER.slot(PlinthSlot.WEST);
    ItemRef feather = stock.get(PlinthSlot.WEST);
    holding(feather);
    click(west, Hand.MAIN_HAND);
    holding(null);
    click(west, Hand.MAIN_HAND);
    holding(feather);
    service.on(new PlayerQuitEvent(player));

    click(west, Hand.MAIN_HAND);

    assertThat(platform.told).hasSize(2);
  }

  @Test
  void aPlinthRunsOneRitualAtATime() {
    stockPlinth();
    holdCrook();
    click(CENTER, Hand.MAIN_HAND);
    events.clear();

    click(CENTER, Hand.MAIN_HAND);

    assertThat(events).containsExactly("warn golemancy.plinth.busy");
  }

  @Test
  void aLockedMasterworkHeartStaysLockedWhenTheAdaptationIsMissing() {
    settings = new PlinthSettings(settings.pedestals(), swiftness(1, InfusionRecipe.MASTERWORK));
    stockPlinth();
    holdCrook();

    click(CENTER, Hand.MAIN_HAND);

    assertThat(events).containsExactly("warn golemancy.plinth.locked");
  }

  @Test
  void aMasterworkHeartInfusesEndToEndOnceMasterworkIsLearned() {
    InfusionRecipe heart = InfusionRecipe.defaults().get("masterwork_heart");
    settings = new PlinthSettings(settings.pedestals(), recipes(Map.of("masterwork_heart", heart)));
    stock.put(PlinthSlot.CENTER, item(Id.minecraft("heart_of_the_sea")));
    stock.put(PlinthSlot.NORTH, item(Id.minecraft("diamond_block")));
    stock.put(PlinthSlot.EAST, item(Id.minecraft("iron_block")));
    stock.put(PlinthSlot.SOUTH, item(Id.minecraft("copper_block")));
    stock.put(PlinthSlot.WEST, item(Id.minecraft("snow_block")));
    stockPlinth();
    holdCrook();
    click(CENTER, Hand.MAIN_HAND);
    assertThat(events).containsExactly("warn golemancy.plinth.locked");

    host.levels.put(InfusionRecipe.MASTERWORK, 1);
    events.clear();
    click(CENTER, Hand.MAIN_HAND);
    tick(heart.getDurationTicks() - 1);
    assertThat(resultsBuilt.get()).isZero();
    tick(1);

    assertThat(events).containsSubsequence("pay golem-masterwork 15", "save 0,0", "pop 8,65,8", "settle", "infused masterwork_heart");
    assertThat(resultsBuilt.get()).isEqualTo(1);
    assertThat(platform.store).isEmpty();
  }

  @Test
  void breakingAPedestalDropsItsItemAfterThePlateIsSaved() {
    stockPlinth();
    events.clear();
    PlinthSpot west = CENTER.slot(PlinthSlot.WEST);

    service.on(new BlockBreakEvent(mock(PlayerRef.class), block(west), true, 0));
    platform.blocks.put("6,64,8", Id.minecraft("air"));
    tick(1);

    assertThat(events).containsSubsequence("write 6,64,8 empty", "save 0,0", "drop 6,64,8");
    assertThat(platform.store).doesNotContainKey(west);
    assertThat(platform.store).hasSize(4);
  }

  @Test
  void breakingTheLodestoneDropsOnlyTheCenterItem() {
    stockPlinth();
    events.clear();

    service.on(new BlockBreakEvent(mock(PlayerRef.class), block(CENTER), true, 0));
    platform.blocks.put("8,64,8", Id.minecraft("air"));
    tick(1);

    assertThat(platform.store).doesNotContainKey(CENTER).hasSize(4);
    assertThat(events.stream().filter(event -> event.startsWith("drop"))).containsExactly("drop 8,64,8");
  }

  @Test
  void aCancelledOrUnrelatedBreakDropsNothing() {
    stockPlinth();
    events.clear();

    service.on(new BlockBreakEvent(mock(PlayerRef.class), block(CENTER.slot(PlinthSlot.NORTH)), true, 0));
    tick(1);
    service.on(new BlockBreakEvent(mock(PlayerRef.class), block(new PlinthSpot(WORLD, 3, 64, 3)), true, 0));
    tick(1);

    assertThat(platform.store).hasSize(5);
    assertThat(events.stream().filter(event -> event.startsWith("drop"))).isEmpty();
  }

  @Test
  void loadingAChunkRespawnsDisplaysAndDropsSlotsWhoseBlockIsGone() {
    PlinthSpot stray = new PlinthSpot(WORLD, 1, 70, 1);
    platform.blocks.put("1,70,1", Id.minecraft("stone"));
    platform.store.put(stray, PlinthSlotItem.of(UUID.randomUUID(), item(Id.minecraft("stone"))));
    for (Map.Entry<PlinthSlot, ItemRef> entry : stock.entrySet()) {
      platform.store.put(CENTER.slot(entry.getKey()), PlinthSlotItem.of(UUID.randomUUID(), entry.getValue()));
    }

    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, false));

    assertThat(platform.displays).hasSize(5);
    assertThat(platform.store).doesNotContainKey(stray).hasSize(5);
    assertThat(events).containsSubsequence("write 1,70,1 empty", "save 0,0", "drop 1,70,1");
  }

  @Test
  void anItemOnAPedestalOfABrokenPlinthStaysUntilItsOwnBlockGoes() {
    platform.blocks.put("8,64,8", Id.minecraft("air"));

    stockPlinth();

    assertThat(platform.store).hasSize(4).doesNotContainKey(CENTER);
    assertThat(platform.displays).hasSize(4);
  }

  @Test
  void aSlotWhoseItemAnEarlierSaveLostIsPurgedWithoutADrop() {
    platform.store.put(CENTER, PlinthSlotItem.stored(UUID.randomUUID(), new byte[0]));

    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, false));

    assertThat(platform.store).isEmpty();
    assertThat(events.stream().filter(event -> event.startsWith("drop"))).isEmpty();
    assertThat(platform.displays).isEmpty();
  }

  @Test
  void anUnreadableItemKeepsItsSlotAndItsBytesWhenItsChunkLoads() {
    PlinthSlotItem unreadable = unreadable();
    platform.store.put(CENTER.slot(PlinthSlot.NORTH), unreadable);

    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, false));

    assertThat(platform.store).containsOnlyKeys(CENTER.slot(PlinthSlot.NORTH));
    assertThat(platform.store.get(CENTER.slot(PlinthSlot.NORTH))).isSameAs(unreadable);
    assertThat(unreadable.stored().bytes()).isEqualTo(UNREADABLE_ITEM);
    assertThat(events).isEmpty();
    assertThat(platform.displays).isEmpty();
  }

  @Test
  void anUnreadableItemIsWarnedAboutOnceWithItsPosition() {
    platform.store.put(CENTER.slot(PlinthSlot.NORTH), unreadable());

    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, false));
    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.UNLOAD, false));
    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, false));
    holding(null);
    click(CENTER.slot(PlinthSlot.NORTH), Hand.MAIN_HAND);

    assertThat(logged).filteredOn(record -> record.getMessage().contains("could not be read")).singleElement().satisfies(record -> {
      assertThat(record.getMessage()).contains("8, 64, 6").contains("stays stored");
      assertThat(record.getThrown()).isNotNull();
    });
    assertThat(platform.store).hasSize(1);
  }

  @Test
  void anUnreadableItemIsNeverHandedOutAndKeepsItsPedestalOccupied() {
    PlinthSpot north = CENTER.slot(PlinthSlot.NORTH);
    PlinthSlotItem unreadable = unreadable();
    platform.store.put(north, unreadable);
    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, false));
    events.clear();

    holding(null);
    PlayerInteractEvent emptyHand = click(north, Hand.MAIN_HAND);
    assertThat(emptyHand.isCancelled()).isTrue();
    assertThat(events).containsExactly("warn golemancy.plinth.unreadable");

    events.clear();
    holding(item(Id.minecraft("sugar")));
    click(north, Hand.MAIN_HAND);
    assertThat(events).containsExactly("warn golemancy.plinth.occupied");

    assertThat(platform.store).containsOnlyKeys(north);
    assertThat(platform.store.get(north)).isSameAs(unreadable);
    assertThat(platform.displays).isEmpty();
  }

  @Test
  void anUnreadableItemStaysStoredWhenItsPedestalBreaks() {
    PlinthSpot west = CENTER.slot(PlinthSlot.WEST);
    PlinthSlotItem unreadable = unreadable();
    platform.store.put(west, unreadable);
    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, false));
    events.clear();

    service.on(new BlockBreakEvent(mock(PlayerRef.class), block(west), true, 0));
    platform.blocks.put("6,64,8", Id.minecraft("air"));
    tick(1);
    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.UNLOAD, false));
    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, false));

    assertThat(platform.store.get(west)).isSameAs(unreadable);
    assertThat(events.stream().filter(event -> event.startsWith("drop") || event.startsWith("write") || event.equals("give"))).isEmpty();
  }

  @Test
  void anUnreadableItemBlocksTheInfusionAndIsNeverConsumed() {
    PlinthSpot north = CENTER.slot(PlinthSlot.NORTH);
    PlinthSlotItem unreadable = unreadable();
    stockPlinthExcept(PlinthSlot.NORTH);
    platform.store.put(north, unreadable);
    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.UNLOAD, false));
    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, false));
    settings = new PlinthSettings(settings.pedestals(), recipes(Map.of("anything", new InfusionRecipe().result("golemancy:rune/order/3")
        .center(ANY).pedestals(ANY, ANY, ANY, ANY).essence(1).duration(DURATION).unlock(InfusionRecipe.RUNIC_AUGMENTS, 1))));
    events.clear();

    holdCrook();
    click(CENTER, Hand.MAIN_HAND);
    tick(DURATION + 5);

    assertThat(events).contains("warn golemancy.plinth.no_recipe");
    assertThat(events.stream().filter(event -> event.startsWith("pay") || event.startsWith("pop") || event.startsWith("write"))).isEmpty();
    assertThat(platform.store).hasSize(5);
    assertThat(platform.store.get(north)).isSameAs(unreadable);
  }

  @Test
  void aReadableItemIsDecodedWhenItsChunkLoadsNotBefore() {
    PlinthSpot north = CENTER.slot(PlinthSlot.NORTH);
    ItemRef sugar = item(Id.minecraft("sugar"));
    when(items.decode("sugar")).thenReturn(sugar);
    platform.store.put(north, PlinthSlotItem.stored(UUID.randomUUID(), "sugar".getBytes(StandardCharsets.US_ASCII)));
    verify(items, never()).decode(anyString());

    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, false));
    holding(null);
    click(north, Hand.MAIN_HAND);

    verify(items, times(1)).decode("sugar");
    assertThat(platform.displays).hasSize(1);
    assertThat(events).containsSubsequence("write 8,64,6 empty", "save 0,0", "give");
    assertThat(platform.store).isEmpty();
  }

  @Test
  void newChunksAreNeverRead() {
    readyChunk();

    assertThat(platform.chunkReads).isZero();
  }

  @Test
  void anOffHandCompassStillBindsWhileTheCrookStartsTheRitual() {
    stockPlinth();
    holdCrook();
    ItemRef compass = item(Id.minecraft("compass"));
    when(inventory.offHand()).thenReturn(compass);

    PlayerInteractEvent main = click(CENTER, Hand.MAIN_HAND);
    PlayerInteractEvent offHand = click(CENTER, Hand.OFF_HAND);

    assertThat(main.isCancelled()).isTrue();
    assertThat(offHand.isCancelled()).isFalse();

    ItemRef torch = item(Id.minecraft("torch"));
    when(inventory.offHand()).thenReturn(torch);
    assertThat(click(CENTER, Hand.OFF_HAND).isCancelled()).isTrue();
  }

  @Test
  void plinthBlocksAreTheLodestoneAndPedestalsOfACompletePlinth() {
    assertThat(service.isPlinthBlock(block(CENTER))).isTrue();
    for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
      assertThat(service.isPlinthBlock(block(CENTER.slot(slot)))).as("%s", slot).isTrue();
    }
    assertThat(service.isPlinthBlock(block(new PlinthSpot(WORLD, 9, 64, 8)))).isFalse();

    platform.blocks.put("8,64,6", Id.minecraft("copper_block"));
    assertThat(service.isPlinthBlock(block(CENTER))).isFalse();
    assertThat(service.isPlinthBlock(block(CENTER.slot(PlinthSlot.EAST)))).isFalse();
  }

  @Test
  void idleDisplaysTurnEveryTwentyTicksAndRitualsTurnThemEveryFive() {
    stockPlinth();
    assertThat(platform.entityDelays).containsOnly(20);
    ItemDisplayRef center = platform.displays.getFirst();
    tick(20);
    verify(center, atLeastOnce()).setInterpolationDuration(20);

    platform.entityDelays.clear();
    holdCrook();
    click(CENTER, Hand.MAIN_HAND);
    tick(1);

    assertThat(platform.entityDelays).contains(5);
  }

  @Test
  void aRitualDrawsOneSwirlFramePerTickThenCollidesAsTheResultPops() {
    stockPlinth();
    holdCrook();
    click(CENTER, Hand.MAIN_HAND);

    tick(DURATION);

    List<String> frames = new ArrayList<>();
    for (String event : events) {
      if (event.startsWith("swirl")) {
        frames.add(event);
      }
    }
    assertThat(frames).hasSize(DURATION).first().isEqualTo("swirl 1");
    assertThat(frames).last().isEqualTo("swirl " + DURATION);
    assertThat(events).containsSubsequence("swirl " + DURATION, "pay golem-runic-augments 2", "pop 8,65,8", "collide", "notify golemancy.plinth.complete");
    assertThat(events.stream().filter(event -> event.equals("collide"))).hasSize(1);
    assertThat(events).noneMatch(event -> event.startsWith("fizzle"));
  }

  @Test
  void anAbortSwitchesTheSwirlToAFizzle() {
    stockPlinth();
    holdCrook();
    click(CENTER, Hand.MAIN_HAND);
    tick(5);

    when(player.location()).thenReturn(new Loc(world, 8.5D, 64D, 17.6D));
    tick(1);
    int stoppedAt = events.size();
    tick(10);

    assertThat(events).containsSubsequence("swirl 5", "fizzle 5", INTERRUPTED);
    assertThat(events.stream().filter(event -> event.startsWith("fizzle"))).hasSize(1);
    assertThat(events.subList(stoppedAt, events.size())).isEmpty();
    assertThat(events).noneMatch(event -> event.equals("collide"));
  }

  @Test
  void aCompletionRefusedAtTheEndFizzlesInsteadOfColliding() {
    stockPlinth();
    holdCrook();
    host.refuseCharge = true;
    click(CENTER, Hand.MAIN_HAND);

    tick(DURATION);

    assertThat(events).contains("fizzle " + DURATION);
    assertThat(events).noneMatch(event -> event.equals("collide"));
  }

  @Test
  void shutdownAbortsRitualsAndRemovesDisplays() {
    stockPlinth();
    holdCrook();
    click(CENTER, Hand.MAIN_HAND);

    service.shutdown();
    tick(5);

    assertThat(events.stream().filter(event -> event.startsWith("pay"))).isEmpty();
    assertThat(platform.store).hasSize(5);
    for (ItemDisplayRef display : platform.displays) {
      verify(display).remove();
    }
  }

  @Test
  void shutdownWithHaltedRegionsForgetsDisplaysWithoutTouchingThem() {
    stockPlinth();
    platform.regionsHalted = true;
    int scheduledBefore = platform.entityDelays.size();

    service.shutdown();
    tick(40);

    assertThat(platform.displays).hasSize(5);
    assertThat(platform.entityDelays).hasSize(scheduledBefore);
    for (ItemDisplayRef display : platform.displays) {
      verify(display, never()).remove();
    }
    assertThat(platform.store).hasSize(5);
  }

  private void assertAbortedWithoutCharge(boolean announced) {
    if (announced) {
      assertThat(events).contains(INTERRUPTED);
    } else {
      assertThat(events).doesNotContain(INTERRUPTED);
    }
    assertThat(events).anyMatch(event -> event.startsWith("fizzle"));
    assertThat(events.stream().filter(event -> event.startsWith("pay"))).isEmpty();
    assertThat(events.stream().filter(event -> event.startsWith("pop"))).isEmpty();
    assertThat(platform.store).hasSize(5);
    verify(player, never()).giveExpLevels(anyInt());
  }

  private InfusionRecipes swiftness(int unlockLevel, String unlockAdaptation) {
    return recipes(Map.of("swiftness_1", new InfusionRecipe().result("golemancy:rune/swiftness/1").center("SCRAPE_POTTERY_SHERD")
        .pedestals("SUGAR", "SUGAR", "FEATHER", "FEATHER").essence(2).duration(DURATION).unlock(unlockAdaptation, unlockLevel)));
  }

  private InfusionRecipes recipes(Map<String, InfusionRecipe> specs) {
    return InfusionRecipes.parse(specs, new StockResolver(), message -> {
      throw new AssertionError(message);
    });
  }

  private ItemRef buildResult() {
    resultsBuilt.incrementAndGet();
    return result;
  }

  private void stockPlinth() {
    for (Map.Entry<PlinthSlot, ItemRef> entry : stock.entrySet()) {
      platform.store.put(CENTER.slot(entry.getKey()), PlinthSlotItem.of(UUID.randomUUID(), entry.getValue()));
    }
    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, false));
    events.clear();
    platform.told.clear();
  }

  private void stockPlinthExcept(PlinthSlot missing) {
    for (Map.Entry<PlinthSlot, ItemRef> entry : stock.entrySet()) {
      if (entry.getKey() != missing) {
        platform.store.put(CENTER.slot(entry.getKey()), PlinthSlotItem.of(UUID.randomUUID(), entry.getValue()));
      }
    }
    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, false));
    events.clear();
    platform.told.clear();
  }

  private void readyChunk() {
    service.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, true));
  }

  private void tick(int ticks) {
    for (int tick = 0; tick < ticks; tick++) {
      platform.now += 50L;
      platform.tick();
    }
  }

  private void build(PlinthSpot center) {
    platform.blocks.put(center.x() + "," + center.y() + "," + center.z(), Id.minecraft("lodestone"));
    for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
      PlinthSpot spot = center.slot(slot);
      platform.blocks.put(spot.x() + "," + spot.y() + "," + spot.z(), Id.minecraft("chiseled_copper"));
    }
  }

  private void holding(ItemRef item) {
    platform.held.put(player, item);
    ItemRef shown = item == null ? item(Id.minecraft("air")) : item;
    when(inventory.mainHand()).thenReturn(shown);
  }

  private void holdCrook() {
    ItemRef crook = item(Id.minecraft("iron_hoe"));
    platform.crooks.add(crook);
    holding(crook);
    when(player.isSneaking()).thenReturn(false);
  }

  private PlayerInteractEvent click(PlinthSpot spot, Hand hand) {
    ItemRef held = hand == Hand.MAIN_HAND ? inventory.mainHand() : inventory.offHand();
    PlayerInteractEvent event = new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, hand, held, block(spot), BlockFace.UP,
        null, UseResult.DEFAULT, UseResult.DEFAULT);
    service.on(event);
    return event;
  }

  private BlockRef block(PlinthSpot spot) {
    BlockRef block = mock(BlockRef.class);
    lenient().when(block.type()).thenAnswer(invocation -> platform.blockType(world, spot.x(), spot.y(), spot.z()));
    lenient().when(block.world()).thenReturn(world);
    lenient().when(block.x()).thenReturn(spot.x());
    lenient().when(block.y()).thenReturn(spot.y());
    lenient().when(block.z()).thenReturn(spot.z());
    lenient().when(block.location()).thenReturn(new Loc(world, spot.x(), spot.y(), spot.z()));
    return block;
  }

  private PlayerRef player() {
    PlayerRef mocked = mock(PlayerRef.class);
    inventory = mock(PlayerInventoryRef.class);
    lenient().when(mocked.uuid()).thenReturn(PLAYER);
    lenient().when(mocked.inventory()).thenReturn(inventory);
    lenient().when(mocked.world()).thenReturn(world);
    lenient().when(mocked.location()).thenReturn(new Loc(world, 8.5D, 64D, 11.5D));
    lenient().when(mocked.isOnline()).thenReturn(true);
    lenient().when(mocked.isValid()).thenReturn(true);
    lenient().when(mocked.isSneaking()).thenReturn(true);
    lenient().when(mocked.gameMode()).thenReturn(GameMode.SURVIVAL);
    lenient().when(mocked.level()).thenReturn(30);
    ItemRef emptyMain = item(Id.minecraft("air"));
    ItemRef emptyOff = item(Id.minecraft("air"));
    lenient().when(inventory.mainHand()).thenReturn(emptyMain);
    lenient().when(inventory.offHand()).thenReturn(emptyOff);
    return mocked;
  }

  // Bytes the real item decoder rejects, as an item saved by another platform or a missing plugin would be.
  private static PlinthSlotItem unreadable() {
    return PlinthSlotItem.stored(UUID.randomUUID(), UNREADABLE_ITEM);
  }

  private static ItemRef item(Id material) {
    ItemRef item = mock(ItemRef.class);
    lenient().when(item.type()).thenReturn(material);
    lenient().when(item.isEmpty()).thenReturn(material.isAir());
    lenient().when(item.copy()).thenReturn(item);
    lenient().when(item.copyWithAmount(1)).thenReturn(item);
    return item;
  }

  private final class StockResolver implements InfusionRecipes.Resolver {
    @Override
    public InfusionIngredient material(String name) {
      if (name.equals(ANY)) {
        return item -> item != null;
      }
      Id material = Id.minecraft(name.toLowerCase(Locale.ROOT));
      return item -> item != null && item.type().equals(material);
    }

    @Override
    public InfusionIngredient exact(Id id) {
      return null;
    }

    @Override
    public Supplier<ItemRef> result(Id id) {
      return PlinthServiceTest.this::buildResult;
    }
  }

  private static final class FakeHost implements PlinthHost {
    private final List<String> events;
    private final Map<String, Integer> levels = new HashMap<>(Map.of(InfusionRecipe.RUNIC_AUGMENTS, 5));
    private boolean canStore = true;
    private boolean canInfuse = true;
    private boolean refuseCharge;

    private FakeHost(List<String> events) {
      this.events = events;
    }

    @Override
    public int level(PlayerRef player, String adaptationId) {
      return levels.getOrDefault(adaptationId, 0);
    }

    @Override
    public boolean canStore(PlayerRef player, String adaptationId, Loc at) {
      return canStore;
    }

    @Override
    public boolean canInfuse(PlayerRef player, String adaptationId, Loc at) {
      return canInfuse;
    }

    @Override
    public Optional<PlinthCharge> chargeLevels(PlayerRef player, String adaptationId, int levels) {
      events.add("pay " + adaptationId + " " + levels);
      if (refuseCharge) {
        return Optional.empty();
      }
      return Optional.of(new PlinthCharge() {
        @Override
        public void settle() {
          events.add("settle");
        }

        @Override
        public void refund() {
          events.add("refund");
        }
      });
    }

    @Override
    public FxEmitter fx(Loc at, FxPriority priority) {
      throw new UnsupportedOperationException("effects go through the platform");
    }

    @Override
    public FxTimeline timeline(Loc at) {
      throw new UnsupportedOperationException("effects go through the platform");
    }

    @Override
    public void infused(PlayerRef player, Infusion infusion, Loc at) {
      events.add("infused " + infusion.id());
    }
  }

  // A warning followed by its cause is one entry, the way AdaptLog.warn(message, error) emits them together.
  private static final class CapturingLog implements PlatformLog {
    private final List<LogRecord> records;

    private CapturingLog(List<LogRecord> records) {
      this.records = records;
    }

    @Override
    public synchronized void info(String message) {
      records.add(new LogRecord(Level.INFO, message));
    }

    @Override
    public synchronized void warn(String message) {
      records.add(new LogRecord(Level.WARNING, message));
    }

    @Override
    public synchronized void error(String message, Throwable error) {
      LogRecord last = records.isEmpty() ? null : records.getLast();
      if (error != null && last != null && last.getLevel() == Level.WARNING && last.getThrown() == null) {
        last.setThrown(error);
        return;
      }
      LogRecord record = new LogRecord(Level.SEVERE, message);
      record.setThrown(error);
      records.add(record);
    }

    @Override
    public void verbose(String message) {
    }
  }
}
