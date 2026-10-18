package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlinthServiceTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final PlinthSpot CENTER = new PlinthSpot(WORLD, 8, 64, 8);
  private static final String INTERRUPTED = "warn golemancy.plinth.interrupted";

  private final List<String> events = new ArrayList<>();
  private final FakePlinthPlatform platform = new FakePlinthPlatform(events);
  private final FakeHost host = new FakeHost(events);
  private final Map<PlinthSlot, ItemStack> stock = new HashMap<>();
  private final AtomicInteger resultsBuilt = new AtomicInteger();
  private final ItemStack result = mock(ItemStack.class);
  private final List<LogRecord> logged = new ArrayList<>();
  private final Handler capture = new Handler() {
    @Override
    public void publish(LogRecord record) {
      logged.add(record);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
  };
  private MockedStatic<ComponentLogger> componentLogger;
  private World world;
  private Player player;
  private PlayerInventory inventory;
  private PlinthSettings settings;
  private PlinthService service;

  @BeforeEach
  void setUp() {
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    lenient().when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    platform.worlds.put(WORLD, world);
    build(CENTER);
    stock.put(PlinthSlot.CENTER, item(Material.DISC_FRAGMENT_5));
    stock.put(PlinthSlot.NORTH, item(Material.SUGAR));
    stock.put(PlinthSlot.EAST, item(Material.FEATHER));
    stock.put(PlinthSlot.SOUTH, item(Material.SUGAR));
    stock.put(PlinthSlot.WEST, item(Material.FEATHER));
    settings = new PlinthSettings(PlinthStructure.pedestals(PlinthStructure.DEFAULT_PEDESTALS, material -> true, message -> {
    }), InfusionRecipes.of(List.of(infusion(1, 3))));
    player = player();
    lenient().when(plugin.isEnabled()).thenReturn(true);
    componentLogger = mockStatic(ComponentLogger.class);
    componentLogger.when(ComponentLogger::logger).thenReturn(null);
    Logger.getLogger("AdaptTest").addHandler(capture);
    service = new PlinthService(platform, () -> settings, () -> host);
  }

  @AfterEach
  void tearDown() {
    Logger.getLogger("AdaptTest").removeHandler(capture);
    componentLogger.close();
  }

  @Test
  void sneakClickingAPedestalWithAnItemStoresExactlyOneOfIt() {
    readyChunk();
    ItemStack sugar = item(Material.SUGAR);
    holding(sugar);

    PlayerInteractEvent click = click(CENTER.slot(PlinthSlot.NORTH), EquipmentSlot.HAND);

    assertThat(click.isCancelled()).isTrue();
    PlinthSlotItem stored = platform.store.get(CENTER.slot(PlinthSlot.NORTH));
    assertThat(stored.item()).isSameAs(sugar);
    assertThat(service.slot(CENTER.slot(PlinthSlot.NORTH))).contains(stored);
    assertThat(events).containsSubsequence("take", "write 8,64,6 item", "save player", "effect LOADED");
    assertThat(platform.displays).hasSize(1);
  }

  @Test
  void theLodestoneTakesTheCenterItem() {
    readyChunk();
    ItemStack rune = item(Material.DISC_FRAGMENT_5);
    holding(rune);

    click(CENTER, EquipmentSlot.HAND);

    assertThat(platform.store.get(CENTER).item()).isSameAs(rune);
  }

  @Test
  void anEmptyHandRetrievesTheItemOnlyAfterThePlateIsSaved() {
    readyChunk();
    holding(item(Material.SUGAR));
    click(CENTER.slot(PlinthSlot.EAST), EquipmentSlot.HAND);
    ItemDisplay display = platform.displays.getFirst();
    holding(null);
    events.clear();

    PlayerInteractEvent click = click(CENTER.slot(PlinthSlot.EAST), EquipmentSlot.HAND);

    assertThat(click.isCancelled()).isTrue();
    assertThat(platform.store).doesNotContainKey(CENTER.slot(PlinthSlot.EAST));
    assertThat(service.slot(CENTER.slot(PlinthSlot.EAST))).isEmpty();
    assertThat(events).containsSubsequence("write 10,64,8 empty", "save 0,0", "give");
    verify(display).remove();
  }

  @Test
  void aFailedSaveWithholdsTheRetrievedItemAndLogsTheStackTrace() {
    readyChunk();
    holding(item(Material.SUGAR));
    click(CENTER.slot(PlinthSlot.EAST), EquipmentSlot.HAND);
    holding(null);
    platform.saveFailure = new IllegalStateException("disk full");

    click(CENTER.slot(PlinthSlot.EAST), EquipmentSlot.HAND);

    assertThat(events).doesNotContain("give");
    assertThat(logged).anyMatch(record -> record.getThrown() != null);
  }

  @Test
  void aBusyPlateIsRetriedBeforeTheItemIsHandedBack() {
    readyChunk();
    holding(item(Material.SUGAR));
    click(CENTER.slot(PlinthSlot.EAST), EquipmentSlot.HAND);
    holding(null);
    platform.busySaves = 2;

    click(CENTER.slot(PlinthSlot.EAST), EquipmentSlot.HAND);
    assertThat(events).doesNotContain("give");
    platform.drainAsync();

    assertThat(events).containsSubsequence("save 0,0", "save 0,0", "save 0,0", "give");
  }

  @Test
  void clicksAreLeftAloneWithoutRunicAugmentsOrSneakingOrOnABrokenPlinth() {
    readyChunk();
    holding(item(Material.SUGAR));
    host.levels.put(InfusionRecipe.RUNIC_AUGMENTS, 0);
    assertThat(click(CENTER.slot(PlinthSlot.NORTH), EquipmentSlot.HAND).isCancelled()).isFalse();

    host.levels.put(InfusionRecipe.RUNIC_AUGMENTS, 1);
    when(player.isSneaking()).thenReturn(false);
    assertThat(click(CENTER.slot(PlinthSlot.NORTH), EquipmentSlot.HAND).isCancelled()).isFalse();

    when(player.isSneaking()).thenReturn(true);
    platform.blocks.put("6,64,8", Material.COPPER_BLOCK);
    assertThat(click(CENTER.slot(PlinthSlot.NORTH), EquipmentSlot.HAND).isCancelled()).isFalse();
    assertThat(platform.store).isEmpty();
  }

  @Test
  void compassBindingOnTheLodestoneIsNeverIntercepted() {
    readyChunk();
    holding(item(Material.COMPASS));
    assertThat(click(CENTER, EquipmentSlot.HAND).isCancelled()).isFalse();

    holding(null);
    ItemStack compass = item(Material.COMPASS);
    when(inventory.getItemInOffHand()).thenReturn(compass);
    assertThat(click(CENTER, EquipmentSlot.HAND).isCancelled()).isFalse();
    assertThat(click(CENTER, EquipmentSlot.OFF_HAND).isCancelled()).isFalse();
    assertThat(platform.store).isEmpty();
  }

  @Test
  void theOffHandClickIsSuppressedWhileTheMainHandLoads() {
    readyChunk();
    holding(item(Material.SUGAR));

    PlayerInteractEvent offHand = click(CENTER.slot(PlinthSlot.SOUTH), EquipmentSlot.OFF_HAND);

    assertThat(offHand.isCancelled()).isTrue();
    assertThat(platform.store).isEmpty();
  }

  @Test
  void anOccupiedSlotAProtectedPlinthAndAnUnreadyChunkRefuseItems() {
    holding(item(Material.SUGAR));
    click(CENTER.slot(PlinthSlot.NORTH), EquipmentSlot.HAND);
    assertThat(events).contains("warn golemancy.plinth.loading");

    readyChunk();
    click(CENTER.slot(PlinthSlot.NORTH), EquipmentSlot.HAND);
    events.clear();
    holding(item(Material.FEATHER));
    click(CENTER.slot(PlinthSlot.NORTH), EquipmentSlot.HAND);
    assertThat(events).containsExactly("warn golemancy.plinth.occupied");

    events.clear();
    host.canStore = false;
    click(CENTER.slot(PlinthSlot.SOUTH), EquipmentSlot.HAND);
    assertThat(events).containsExactly("warn golemancy.plinth.protected");
    assertThat(platform.store).hasSize(1);
  }

  @Test
  void aCompletedInfusionChargesLevelsRemovesTheItemsAndSavesBeforeTheResultAppears() {
    stockPlinth();
    holdCrook();

    PlayerInteractEvent start = click(CENTER, EquipmentSlot.HAND);
    tick(3);

    assertThat(start.isCancelled()).isTrue();
    assertThat(events).containsSubsequence("pay golem-runic-augments 2", "write 8,64,8 empty", "save 0,0", "pop 8,65,8", "settle",
        "notify golemancy.plinth.complete", "infused swiftness_1");
    assertThat(events).doesNotContain("refund");
    assertThat(events.stream().filter(event -> event.startsWith("write") && event.endsWith("empty"))).hasSize(5);
    assertThat(platform.store).isEmpty();
    assertThat(resultsBuilt.get()).isEqualTo(1);
    for (ItemDisplay display : platform.displays) {
      verify(display).remove();
    }
  }

  @Test
  void aCrashBeforeTheSaveCompletesNeverCreatesTheResult() {
    stockPlinth();
    holdCrook();
    platform.deferAsync = true;

    click(CENTER, EquipmentSlot.HAND);
    tick(3);

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

    click(CENTER, EquipmentSlot.HAND);
    tick(3);

    assertThat(events).containsSubsequence("pay golem-runic-augments 2", "save 0,0", "refund", "warn golemancy.plinth.failed");
    assertThat(events).doesNotContain("settle");
    assertThat(events.stream().filter(event -> event.startsWith("pop"))).isEmpty();
    assertThat(resultsBuilt.get()).isZero();
    assertThat(platform.store).isEqualTo(before);
    for (PlinthSpot spot : before.keySet()) {
      assertThat(service.slot(spot)).contains(before.get(spot));
    }
    assertThat(platform.displays).hasSize(10);
    assertThat(events.stream().filter(event -> event.startsWith("drop"))).isEmpty();
    assertThat(logged).anyMatch(record -> record.getThrown() != null);
  }

  @Test
  void aSlotRefilledDuringAFailedSaveDropsItsOldItemInstead() {
    stockPlinth();
    PlinthSpot north = CENTER.slot(PlinthSlot.NORTH);
    holdCrook();
    platform.deferAsync = true;
    platform.saveFailure = new IllegalStateException("disk full");
    click(CENTER, EquipmentSlot.HAND);
    tick(3);
    PlinthSlotItem refill = PlinthSlotItem.of(UUID.randomUUID(), item(Material.STONE));
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
    click(CENTER, EquipmentSlot.HAND);
    tick(2);
    PlinthSpot east = CENTER.slot(PlinthSlot.EAST);
    PlinthSlotItem intruder = PlinthSlotItem.of(UUID.randomUUID(), item(Material.STONE));
    platform.swapAfterReads(east, intruder, 1);

    tick(1);

    assertThat(events).contains("pay golem-runic-augments 2", "refund", INTERRUPTED);
    assertThat(events).doesNotContain("save 0,0", "settle");
    assertThat(events.stream().filter(event -> event.startsWith("pop"))).isEmpty();
    assertThat(platform.store).hasSize(5).containsEntry(east, intruder);
    assertThat(service.slot(east)).contains(intruder);
    assertThat(service.slot(CENTER)).isPresent();
  }

  @Test
  void creativePlayersPayNothing() {
    stockPlinth();
    holdCrook();
    when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
    when(player.getLevel()).thenReturn(0);

    click(CENTER, EquipmentSlot.HAND);
    tick(3);

    assertThat(events.stream().filter(event -> event.startsWith("pay"))).isEmpty();
    assertThat(events).contains("pop 8,65,8");
  }

  @Test
  void walkingMoreThanEightBlocksAwayAbortsWithoutCharging() {
    stockPlinth();
    holdCrook();
    click(CENTER, EquipmentSlot.HAND);
    tick(1);

    when(player.getLocation()).thenReturn(new Location(world, 8.5D, 64D, 17.6D));
    tick(5);

    assertAbortedWithoutCharge(true);
  }

  @Test
  void loggingOutAbortsQuietly() {
    stockPlinth();
    holdCrook();
    click(CENTER, EquipmentSlot.HAND);

    service.on(new PlayerQuitEvent(player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));
    tick(5);

    assertAbortedWithoutCharge(false);
  }

  @Test
  void breakingAPlinthBlockAbortsAtOnce() {
    stockPlinth();
    holdCrook();
    click(CENTER, EquipmentSlot.HAND);

    service.on(new BlockBreakEvent(block(CENTER.slot(PlinthSlot.WEST)), mock(Player.class)));

    assertThat(events).contains(INTERRUPTED);
    assertThat(events.stream().filter(event -> event.startsWith("pay"))).isEmpty();
  }

  @Test
  void aStructureChangeWithoutAnEventAbortsOnTheNextPulse() {
    stockPlinth();
    holdCrook();
    click(CENTER, EquipmentSlot.HAND);

    platform.blocks.put("8,64,10", Material.COPPER_BLOCK);
    tick(5);

    assertThat(events).contains(INTERRUPTED);
    assertThat(events.stream().filter(event -> event.startsWith("pay"))).isEmpty();
  }

  @Test
  void takingAnItemOffAPedestalAbortsTheRitual() {
    stockPlinth();
    holdCrook();
    click(CENTER, EquipmentSlot.HAND);
    tick(1);

    holding(null);
    when(player.isSneaking()).thenReturn(true);
    click(CENTER.slot(PlinthSlot.NORTH), EquipmentSlot.HAND);
    tick(5);

    assertThat(events).contains(INTERRUPTED);
    assertThat(events.stream().filter(event -> event.startsWith("pay"))).isEmpty();
    assertThat(platform.store).hasSize(4);
    assertThat(events.stream().filter(event -> event.startsWith("pop"))).isEmpty();
  }

  @Test
  void aRitualNeedsAMatchingUnlockedPermittedAffordableRecipe() {
    stock.put(PlinthSlot.NORTH, item(Material.FEATHER));
    stockPlinth();
    holdCrook();
    click(CENTER, EquipmentSlot.HAND);
    assertThat(events).containsExactly("warn golemancy.plinth.no_recipe");

    settings = new PlinthSettings(settings.pedestals(), InfusionRecipes.of(List.of(new Infusion("anything", NamespacedKey.fromString("golemancy:rune/order/3"),
        this::buildResult, item -> item != null, List.<InfusionIngredient>of(item -> item != null, item -> item != null, item -> item != null, item -> item != null),
        10, 3, InfusionRecipe.RUNIC_AUGMENTS, 5))));
    host.levels.put(InfusionRecipe.RUNIC_AUGMENTS, 3);
    events.clear();
    click(CENTER, EquipmentSlot.HAND);
    assertThat(events).containsExactly("warn golemancy.plinth.locked");

    host.levels.put(InfusionRecipe.RUNIC_AUGMENTS, 5);
    host.canInfuse = false;
    events.clear();
    click(CENTER, EquipmentSlot.HAND);
    assertThat(events).containsExactly("warn golemancy.plinth.protected");

    host.canInfuse = true;
    when(player.getLevel()).thenReturn(9);
    events.clear();
    click(CENTER, EquipmentSlot.HAND);
    assertThat(events).containsExactly("warn golemancy.plinth.levels");
    tick(5);
    assertThat(platform.store).hasSize(5);
  }

  @Test
  void aPlinthRunsOneRitualAtATime() {
    stockPlinth();
    holdCrook();
    click(CENTER, EquipmentSlot.HAND);
    events.clear();

    click(CENTER, EquipmentSlot.HAND);

    assertThat(events).containsExactly("warn golemancy.plinth.busy");
  }

  @Test
  void aLockedMasterworkHeartStaysLockedWhenTheAdaptationIsMissing() {
    settings = new PlinthSettings(settings.pedestals(), InfusionRecipes.of(List.of(infusion(1, 3, InfusionRecipe.MASTERWORK))));
    stockPlinth();
    holdCrook();

    click(CENTER, EquipmentSlot.HAND);

    assertThat(events).containsExactly("warn golemancy.plinth.locked");
  }

  @Test
  void breakingAPedestalDropsItsItemAfterThePlateIsSaved() {
    stockPlinth();
    events.clear();
    PlinthSpot west = CENTER.slot(PlinthSlot.WEST);

    service.on(new BlockBreakEvent(block(west), mock(Player.class)));
    platform.blocks.put("6,64,8", Material.AIR);
    tick(1);

    assertThat(events).containsSubsequence("write 6,64,8 empty", "save 0,0", "drop 6,64,8");
    assertThat(platform.store).doesNotContainKey(west);
    assertThat(platform.store).hasSize(4);
  }

  @Test
  void breakingTheLodestoneDropsOnlyTheCenterItem() {
    stockPlinth();
    events.clear();

    service.on(new BlockBreakEvent(block(CENTER), mock(Player.class)));
    platform.blocks.put("8,64,8", Material.AIR);
    tick(1);

    assertThat(platform.store).doesNotContainKey(CENTER).hasSize(4);
    assertThat(events.stream().filter(event -> event.startsWith("drop"))).containsExactly("drop 8,64,8");
  }

  @Test
  void aCancelledOrUnrelatedBreakDropsNothing() {
    stockPlinth();
    events.clear();

    service.on(new BlockBreakEvent(block(CENTER.slot(PlinthSlot.NORTH)), mock(Player.class)));
    tick(1);
    service.on(new BlockBreakEvent(block(new PlinthSpot(WORLD, 3, 64, 3)), mock(Player.class)));
    tick(1);

    assertThat(platform.store).hasSize(5);
    assertThat(events.stream().filter(event -> event.startsWith("drop"))).isEmpty();
  }

  @Test
  void loadingAChunkRespawnsDisplaysAndDropsSlotsWhoseBlockIsGone() {
    PlinthSpot stray = new PlinthSpot(WORLD, 1, 70, 1);
    platform.blocks.put("1,70,1", Material.STONE);
    platform.store.put(stray, PlinthSlotItem.of(UUID.randomUUID(), item(Material.STONE)));
    for (Map.Entry<PlinthSlot, ItemStack> entry : stock.entrySet()) {
      platform.store.put(CENTER.slot(entry.getKey()), PlinthSlotItem.of(UUID.randomUUID(), entry.getValue()));
    }

    service.on(new ChunkLoadEvent(chunk(0, 0), false));

    assertThat(platform.displays).hasSize(5);
    assertThat(platform.store).doesNotContainKey(stray).hasSize(5);
    assertThat(events).containsSubsequence("write 1,70,1 empty", "save 0,0", "drop 1,70,1");
    assertThat(service.slot(CENTER)).isPresent();
  }

  @Test
  void anItemOnAPedestalOfABrokenPlinthStaysUntilItsOwnBlockGoes() {
    platform.blocks.put("8,64,8", Material.AIR);

    stockPlinth();

    assertThat(platform.store).hasSize(4).doesNotContainKey(CENTER);
    assertThat(platform.displays).hasSize(4);
  }

  @Test
  void unreadableSlotsArePurgedWithoutADrop() {
    platform.store.put(CENTER, PlinthSlotItem.unreadable(UUID.randomUUID()));

    service.on(new ChunkLoadEvent(chunk(0, 0), false));

    assertThat(platform.store).isEmpty();
    assertThat(events.stream().filter(event -> event.startsWith("drop"))).isEmpty();
    assertThat(platform.displays).isEmpty();
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
    ItemStack compass = item(Material.COMPASS);
    when(inventory.getItemInOffHand()).thenReturn(compass);

    PlayerInteractEvent main = click(CENTER, EquipmentSlot.HAND);
    PlayerInteractEvent offHand = click(CENTER, EquipmentSlot.OFF_HAND);

    assertThat(main.isCancelled()).isTrue();
    assertThat(offHand.isCancelled()).isFalse();

    ItemStack torch = item(Material.TORCH);
    when(inventory.getItemInOffHand()).thenReturn(torch);
    assertThat(click(CENTER, EquipmentSlot.OFF_HAND).isCancelled()).isTrue();
  }

  @Test
  void plinthBlocksAreTheLodestoneAndPedestalsOfACompletePlinth() {
    assertThat(service.isPlinthBlock(block(CENTER))).isTrue();
    for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
      assertThat(service.isPlinthBlock(block(CENTER.slot(slot)))).as("%s", slot).isTrue();
    }
    assertThat(service.isPlinthBlock(block(new PlinthSpot(WORLD, 9, 64, 8)))).isFalse();

    platform.blocks.put("8,64,6", Material.COPPER_BLOCK);
    assertThat(service.isPlinthBlock(block(CENTER))).isFalse();
    assertThat(service.isPlinthBlock(block(CENTER.slot(PlinthSlot.EAST)))).isFalse();
  }

  @Test
  void idleDisplaysTurnEveryTwentyTicksAndRitualsTurnThemEveryFive() {
    stockPlinth();
    assertThat(platform.entityDelays).containsOnly(20);
    ItemDisplay center = platform.displays.getFirst();
    tick(20);
    verify(center, atLeastOnce()).setInterpolationDuration(20);

    platform.entityDelays.clear();
    holdCrook();
    click(CENTER, EquipmentSlot.HAND);
    tick(1);

    assertThat(platform.entityDelays).contains(5);
  }

  @Test
  void shutdownAbortsRitualsAndRemovesDisplays() {
    stockPlinth();
    holdCrook();
    click(CENTER, EquipmentSlot.HAND);

    service.shutdown();
    tick(5);

    assertThat(events.stream().filter(event -> event.startsWith("pay"))).isEmpty();
    assertThat(platform.store).hasSize(5);
    for (ItemDisplay display : platform.displays) {
      verify(display).remove();
    }
  }

  private void assertAbortedWithoutCharge(boolean announced) {
    if (announced) {
      assertThat(events).contains(INTERRUPTED);
    } else {
      assertThat(events).doesNotContain(INTERRUPTED);
    }
    assertThat(events).contains("effect FIZZLE");
    assertThat(events.stream().filter(event -> event.startsWith("pay"))).isEmpty();
    assertThat(events.stream().filter(event -> event.startsWith("pop"))).isEmpty();
    assertThat(platform.store).hasSize(5);
    verify(player, never()).giveExpLevels(anyInt());
  }

  private Infusion infusion(int unlockLevel, int duration) {
    return infusion(unlockLevel, duration, InfusionRecipe.RUNIC_AUGMENTS);
  }

  private Infusion infusion(int unlockLevel, int duration, String unlockAdaptation) {
    InfusionIngredient sugar = item -> item != null && item.getType() == Material.SUGAR;
    InfusionIngredient feather = item -> item != null && item.getType() == Material.FEATHER;
    return new Infusion("swiftness_1", NamespacedKey.fromString("golemancy:rune/swiftness/1"), this::buildResult,
        item -> item != null && item.getType() == Material.DISC_FRAGMENT_5, List.of(sugar, sugar, feather, feather), 2, duration, unlockAdaptation, unlockLevel);
  }

  private ItemStack buildResult() {
    resultsBuilt.incrementAndGet();
    return result;
  }

  private void stockPlinth() {
    for (Map.Entry<PlinthSlot, ItemStack> entry : stock.entrySet()) {
      platform.store.put(CENTER.slot(entry.getKey()), PlinthSlotItem.of(UUID.randomUUID(), entry.getValue()));
    }
    service.on(new ChunkLoadEvent(chunk(0, 0), false));
    events.clear();
  }

  private void readyChunk() {
    service.on(new ChunkLoadEvent(chunk(0, 0), true));
  }

  private void tick(int ticks) {
    for (int tick = 0; tick < ticks; tick++) {
      platform.now += 50L;
      platform.tick();
    }
  }

  private void build(PlinthSpot center) {
    platform.blocks.put(center.x() + "," + center.y() + "," + center.z(), Material.LODESTONE);
    for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
      PlinthSpot spot = center.slot(slot);
      platform.blocks.put(spot.x() + "," + spot.y() + "," + spot.z(), Material.CHISELED_COPPER);
    }
  }

  private void holding(ItemStack item) {
    platform.held.put(player, item);
    ItemStack shown = item == null ? item(Material.AIR) : item;
    when(inventory.getItemInMainHand()).thenReturn(shown);
  }

  private void holdCrook() {
    ItemStack crook = item(Material.IRON_HOE);
    platform.crooks.add(crook);
    holding(crook);
    when(player.isSneaking()).thenReturn(false);
  }

  private PlayerInteractEvent click(PlinthSpot spot, EquipmentSlot hand) {
    ItemStack held = hand == EquipmentSlot.HAND ? inventory.getItemInMainHand() : inventory.getItemInOffHand();
    PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, held, block(spot), BlockFace.UP, hand);
    service.on(event);
    return event;
  }

  private Block block(PlinthSpot spot) {
    Block block = mock(Block.class);
    lenient().when(block.getType()).thenAnswer(invocation -> platform.blockType(world, spot.x(), spot.y(), spot.z()));
    lenient().when(block.getWorld()).thenReturn(world);
    lenient().when(block.getX()).thenReturn(spot.x());
    lenient().when(block.getY()).thenReturn(spot.y());
    lenient().when(block.getZ()).thenReturn(spot.z());
    lenient().when(block.getLocation()).thenReturn(new Location(world, spot.x(), spot.y(), spot.z()));
    return block;
  }

  private Chunk chunk(int x, int z) {
    Chunk chunk = mock(Chunk.class);
    lenient().when(chunk.getWorld()).thenReturn(world);
    lenient().when(chunk.getX()).thenReturn(x);
    lenient().when(chunk.getZ()).thenReturn(z);
    return chunk;
  }

  private Player player() {
    Player mocked = mock(Player.class);
    inventory = mock(PlayerInventory.class);
    lenient().when(mocked.getUniqueId()).thenReturn(PLAYER);
    lenient().when(mocked.getInventory()).thenReturn(inventory);
    lenient().when(mocked.getWorld()).thenReturn(world);
    lenient().when(mocked.getLocation()).thenReturn(new Location(world, 8.5D, 64D, 11.5D));
    lenient().when(mocked.isOnline()).thenReturn(true);
    lenient().when(mocked.isValid()).thenReturn(true);
    lenient().when(mocked.isSneaking()).thenReturn(true);
    lenient().when(mocked.getGameMode()).thenReturn(GameMode.SURVIVAL);
    lenient().when(mocked.getLevel()).thenReturn(30);
    ItemStack emptyMain = item(Material.AIR);
    ItemStack emptyOff = item(Material.AIR);
    lenient().when(inventory.getItemInMainHand()).thenReturn(emptyMain);
    lenient().when(inventory.getItemInOffHand()).thenReturn(emptyOff);
    return mocked;
  }

  private static ItemStack item(Material material) {
    ItemStack item = mock(ItemStack.class);
    lenient().when(item.getType()).thenReturn(material);
    return item;
  }

  private static final class FakeHost implements PlinthHost {
    private final List<String> events;
    private final Map<String, Integer> levels = new HashMap<>(Map.of(InfusionRecipe.RUNIC_AUGMENTS, 5));
    private boolean canStore = true;
    private boolean canInfuse = true;

    private FakeHost(List<String> events) {
      this.events = events;
    }

    @Override
    public int level(Player player, String adaptationId) {
      return levels.getOrDefault(adaptationId, 0);
    }

    @Override
    public boolean canStore(Player player, String adaptationId, Location at) {
      return canStore;
    }

    @Override
    public boolean canInfuse(Player player, String adaptationId, Location at) {
      return canInfuse;
    }

    @Override
    public Optional<PlinthCharge> chargeLevels(Player player, String adaptationId, int levels) {
      events.add("pay " + adaptationId + " " + levels);
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
    public FxEmitter fx(Location at, FxPriority priority) {
      throw new UnsupportedOperationException("effects go through the platform");
    }

    @Override
    public void infused(Player player, Infusion infusion, Location at) {
      events.add("infused " + infusion.id());
    }
  }
}
