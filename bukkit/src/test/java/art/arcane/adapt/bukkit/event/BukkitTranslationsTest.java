package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.api.internal.HostPlatform;
import art.arcane.adapt.TestVersion;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.item.BrewingRecipe;
import art.arcane.adapt.engine.adaptation.BonusBlockBreaks;
import art.arcane.adapt.bukkit.feature.protection.BukkitProtectionHost;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.adapt.AdaptBrewCompleteEvent;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.event.ProtectionProbe;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitTranslationsTest {
  private final List<Throwable> failures = new ArrayList<>();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> failures.add(error));
  private FakeRefs refs;
  private Player player;
  private Block block;

  @BeforeAll
  static void initializeBlockRegistry() {
    TestVersion.mockBlockRegistry();
  }

  @BeforeEach
  void setUp() {
    refs = new FakeRefs();
    player = mock(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    block = mock(Block.class);
  }

  @AfterEach
  void tearDown() {
    refs.close();
    assertThat(failures).isEmpty();
  }

  @Test
  void blockBreakTranslatesAndWritesBackDropsExperienceAndCancel() {
    org.bukkit.event.block.BlockBreakEvent event = new org.bukkit.event.block.BlockBreakEvent(block, player);
    event.setExpToDrop(3);
    AtomicReference<BlockBreakEvent> seen = new AtomicReference<>();
    bus.register(this, BlockBreakEvent.class, EventPriority.NORMAL, core -> {
      seen.set(core);
      core.setDropItems(false);
      core.setExperience(7);
      core.setCancelled(true);
    });

    BlockTranslations.BREAK.bridge(event, core -> bus.post(core, EventPriority.NORMAL));

    assertThat(seen.get().player()).isSameAs(refs.wrap(player, PlayerRef.class));
    assertThat(seen.get().experience()).isEqualTo(7);
    assertThat(event.isDropItems()).isFalse();
    assertThat(event.getExpToDrop()).isEqualTo(7);
    assertThat(event.isCancelled()).isTrue();
  }

  @Test
  void blockBreakUntouchedLeavesNativeStateAlone() {
    org.bukkit.event.block.BlockBreakEvent event = new org.bukkit.event.block.BlockBreakEvent(block, player);
    event.setExpToDrop(4);
    bus.register(this, BlockBreakEvent.class, EventPriority.MONITOR, core -> {
    });

    BlockTranslations.BREAK.bridge(event, core -> bus.post(core, EventPriority.MONITOR));

    assertThat(event.isDropItems()).isTrue();
    assertThat(event.getExpToDrop()).isEqualTo(4);
    assertThat(event.isCancelled()).isFalse();
  }

  @Test
  void bonusBreaksReachTheBus() {
    Plugin adapt = mock(Plugin.class);
    lenient().when(adapt.isEnabled()).thenReturn(true);
    BukkitEventBridge bridge = new BukkitEventBridge(adapt, bus, List.of(BlockTranslations.BREAK));
    bridge.start();
    try {
      AtomicInteger seen = new AtomicInteger();
      bus.register(this, BlockBreakEvent.class, EventPriority.NORMAL, core -> seen.incrementAndGet());
      when(player.breakBlock(block)).thenAnswer(call -> {
        org.bukkit.event.block.BlockBreakEvent event = new org.bukkit.event.block.BlockBreakEvent(block, player);
        for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
          listener.callEvent(event);
        }
        return true;
      });

      when(block.getType()).thenReturn(Material.STONE);
      HostPlatform mocks = TestCores.mockPlatform();
      TestCores.install(new HostPlatform(mocks.host(), mocks.scheduler(), mocks.fx(), mocks.viewers(), mocks.menus(),
          mocks.permissions(), new BukkitProtectionHost(), mocks.recipes(), mocks.advancements()));

      BonusBlockBreaks.breakBlock(BukkitRefs.player(player), BukkitRefs.block(block), 0);

      assertThat(seen).hasValue(1);
    } finally {
      TestCores.clear();
      bridge.stop();
    }
  }

  @Test
  void brewCompletionReachesTheBusThroughThePublicBukkitEvent() throws EventException {
    Plugin adapt = mock(Plugin.class);
    lenient().when(adapt.isEnabled()).thenReturn(true);
    BukkitEventBridge bridge = new BukkitEventBridge(adapt, bus, List.of(AdaptApiTranslations.BREW_COMPLETE));
    bridge.start();
    List<String> order = new ArrayList<>();
    HandlerList handlers = art.arcane.adapt.engine.potion.AdaptBrewCompleteEvent.getHandlerList();
    RegisteredListener other = new RegisteredListener(new Listener() {
    }, (ignored, event) -> order.add("other"), org.bukkit.event.EventPriority.HIGHEST, mock(Plugin.class), false);
    handlers.register(other);
    try {
      AtomicReference<AdaptBrewCompleteEvent> seen = new AtomicReference<>();
      bus.register(this, AdaptBrewCompleteEvent.class, EventPriority.MONITOR, core -> {
        order.add("adapt");
        seen.set(core);
      });
      UUID brewer = UUID.randomUUID();
      BrewingRecipe recipe = new BrewingRecipe(Id.adapt("adapt-test"), Id.minecraft("nether_wart"), mock(ItemRef.class),
          mock(ItemRef.class), 400, 1);
      art.arcane.adapt.engine.potion.AdaptBrewCompleteEvent event =
          new art.arcane.adapt.engine.potion.AdaptBrewCompleteEvent(block, recipe, brewer, 2);

      for (RegisteredListener listener : handlers.getRegisteredListeners()) {
        listener.callEvent(event);
      }

      assertThat(order).containsExactly("other", "adapt");
      assertThat(seen.get().block()).isSameAs(BukkitRefs.block(block));
      assertThat(seen.get().recipeId()).isEqualTo("adapt:adapt-test");
      assertThat(seen.get().brewerId()).isEqualTo(brewer);
      assertThat(seen.get().brewedPotions()).isEqualTo(2);
    } finally {
      handlers.unregister(other);
      bridge.stop();
    }
  }

  @Test
  void protectionProbesArePostedMarked() throws EventException {
    Plugin adapt = mock(Plugin.class);
    lenient().when(adapt.isEnabled()).thenReturn(true);
    BukkitEventBridge bridge = new BukkitEventBridge(adapt, bus, List.of(PlayerTranslations.INTERACT));
    bridge.start();
    try {
      List<Boolean> probes = new ArrayList<>();
      bus.register(this, PlayerInteractEvent.class, EventPriority.NORMAL, false, core -> probes.add(core.protectionProbe()));
      when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
      org.bukkit.event.player.PlayerInteractEvent probe = ProtectionProbe.blockInteract(player, block, EquipmentSlot.HAND);
      org.bukkit.event.player.PlayerInteractEvent real = new org.bukkit.event.player.PlayerInteractEvent(player,
          Action.RIGHT_CLICK_BLOCK, null, block, org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);

      for (org.bukkit.event.player.PlayerInteractEvent event : List.of(probe, real)) {
        for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
          listener.callEvent(event);
        }
      }

      assertThat(probes).containsExactly(true, false);
    } finally {
      bridge.stop();
    }
  }

  @Test
  void clicksInAnOpenAdaptWindowAreStillPosted() {
    UIWindow window = mock(UIWindow.class);
    lenient().when(window.getViewer()).thenReturn(player);
    lenient().when(window.isVisible()).thenReturn(true);
    Plugin adapt = mock(Plugin.class);
    RegisteredListener listener = new RegisteredListener(window, (ignored, event) -> {
    }, org.bukkit.event.EventPriority.NORMAL, adapt, false);
    org.bukkit.event.inventory.InventoryClickEvent.getHandlerList().register(listener);
    try {
      org.bukkit.event.inventory.InventoryClickEvent click = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
      when(click.getWhoClicked()).thenReturn(player);
      assertThat(InventoryTranslations.CLICK.target(click)).isEqualTo(art.arcane.adapt.platform.event.inventory.InventoryClickEvent.class);
    } finally {
      org.bukkit.event.inventory.InventoryClickEvent.getHandlerList().unregister(listener);
    }
  }

  @Test
  void consumedItemIsWrittenBackWhenTheNativeCopyNoLongerMatches() {
    ItemStack held = mock(ItemStack.class);
    ItemStack copyAfterEdit = mock(ItemStack.class);
    org.bukkit.event.player.PlayerItemConsumeEvent event = mock(org.bukkit.event.player.PlayerItemConsumeEvent.class);
    when(event.getPlayer()).thenReturn(player);
    when(event.getHand()).thenReturn(EquipmentSlot.HAND);
    when(event.getItem()).thenReturn(held, held);
    bus.register(this, PlayerItemConsumeEvent.class, EventPriority.NORMAL, core -> core.setItem(core.item()));

    PlayerTranslations.ITEM_CONSUME.bridge(event, core -> bus.post(core, EventPriority.NORMAL));
    verify(event, never()).setItem(any());

    when(event.getItem()).thenReturn(held, copyAfterEdit);
    PlayerTranslations.ITEM_CONSUME.bridge(event, core -> bus.post(core, EventPriority.NORMAL));
    verify(event).setItem(held);
  }

  @Test
  void tamesByOwnersThatAreNotPlayersArePostedWithTheOwnerId() {
    org.bukkit.entity.Wolf wolf = mock(org.bukkit.entity.Wolf.class);
    org.bukkit.entity.AnimalTamer tamer = mock(org.bukkit.entity.AnimalTamer.class);
    UUID tamerId = UUID.randomUUID();
    when(tamer.getUniqueId()).thenReturn(tamerId);
    org.bukkit.event.entity.EntityTameEvent event = new org.bukkit.event.entity.EntityTameEvent(wolf, tamer);
    List<art.arcane.adapt.platform.event.entity.EntityTameEvent> seen = new ArrayList<>();
    bus.register(this, art.arcane.adapt.platform.event.entity.EntityTameEvent.class, EventPriority.MONITOR, seen::add);

    assertThat(EntityTranslations.TAME.target(event)).isNotNull();
    EntityTranslations.TAME.bridge(event, core -> bus.post(core, EventPriority.MONITOR));

    assertThat(seen).hasSize(1);
    assertThat(seen.getFirst().owner()).isNull();
    assertThat(seen.getFirst().ownerId()).isEqualTo(tamerId);
  }

  @Test
  void recipeBookClicksOnRecipesWithoutAKeyArePosted() {
    org.bukkit.inventory.Recipe recipe = mock(org.bukkit.inventory.Recipe.class);
    org.bukkit.event.player.PlayerRecipeBookClickEvent event = new org.bukkit.event.player.PlayerRecipeBookClickEvent(player, recipe, false);
    List<art.arcane.adapt.platform.event.player.PlayerRecipeBookClickEvent> seen = new ArrayList<>();
    bus.register(this, art.arcane.adapt.platform.event.player.PlayerRecipeBookClickEvent.class, EventPriority.LOWEST, seen::add);

    assertThat(PlayerGapTranslations.RECIPE_BOOK.target(event)).isNotNull();
    PlayerGapTranslations.RECIPE_BOOK.bridge(event, core -> bus.post(core, EventPriority.LOWEST));

    assertThat(seen).hasSize(1);
    assertThat(seen.getFirst().recipe()).isNull();
  }

  @Test
  void interactWritesUseResultsBackAndFollowsBukkitCancelRule() {
    org.bukkit.event.player.PlayerInteractEvent event = new org.bukkit.event.player.PlayerInteractEvent(player,
        Action.RIGHT_CLICK_BLOCK, null, block, org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
    AtomicReference<PlayerInteractEvent> seen = new AtomicReference<>();
    bus.register(this, PlayerInteractEvent.class, EventPriority.NORMAL, core -> {
      seen.set(core);
      core.setUseItem(UseResult.DENY);
    });

    assertThat(PlayerTranslations.INTERACT.cancelled(event)).isFalse();
    PlayerTranslations.INTERACT.bridge(event, core -> bus.post(core, EventPriority.NORMAL));

    assertThat(seen.get().action()).isEqualTo(InteractAction.RIGHT_CLICK_BLOCK);
    assertThat(seen.get().useBlock()).isEqualTo(UseResult.ALLOW);
    assertThat(event.useItemInHand()).isEqualTo(Event.Result.DENY);
    assertThat(event.useInteractedBlock()).isEqualTo(Event.Result.ALLOW);

    event.setUseInteractedBlock(Event.Result.DENY);
    assertThat(PlayerTranslations.INTERACT.cancelled(event)).isTrue();
  }

  @Test
  void interactCancelDeniesBothUses() {
    org.bukkit.event.player.PlayerInteractEvent event = new org.bukkit.event.player.PlayerInteractEvent(player,
        Action.RIGHT_CLICK_BLOCK, null, block, org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
    bus.register(this, PlayerInteractEvent.class, EventPriority.NORMAL, core -> core.setCancelled(true));

    PlayerTranslations.INTERACT.bridge(event, core -> bus.post(core, EventPriority.NORMAL));

    assertThat(event.useInteractedBlock()).isEqualTo(Event.Result.DENY);
    assertThat(event.useItemInHand()).isEqualTo(Event.Result.DENY);
  }

  @Test
  void everyCoreEventTypeHasATranslation() throws IOException, URISyntaxException {
    Set<String> posted = new TreeSet<>();
    for (Translation<?> translation : BukkitTranslations.all()) {
      for (Class<?> type : translation.posted()) {
        posted.add(type.getName());
      }
    }
    Set<String> expected = new TreeSet<>();
    Set<String> catalogued = new TreeSet<>();
    List<String> rows = Files.readAllLines(Path.of("../core/event-catalog.tsv"));
    for (String row : rows.subList(1, rows.size())) {
      String[] columns = row.split("\t");
      catalogued.add(columns[0]);
      if (!columns[1].startsWith("core ") && nativeEventPresent(columns[1])
          && !UntranslatedCoreEvents.NAMES.contains(columns[0])) {
        expected.add(columns[0]);
      }
    }

    assertThat(catalogued).isEqualTo(concreteCoreEvents());
    assertThat(catalogued).containsAll(UntranslatedCoreEvents.NAMES);
    assertThat(posted).isEqualTo(expected);
  }

  private static boolean nativeEventPresent(String source) {
    if (!source.contains("(Spigot only")) {
      return true;
    }
    try {
      Class.forName(source.substring(0, source.indexOf(' ')));
      return true;
    } catch (ClassNotFoundException absent) {
      return false;
    }
  }

  @Test
  void spigotBrushEventTranslatesWhenTheServerHasIt() {
    assertThat(SpigotTranslations.brush("org.bukkit.event.block.NoSuchBrushEvent")).isNull();
    Translation<?> brush = SpigotTranslations.brush(SpigotBrush.class.getName());
    assertThat(brush).isNotNull();
    BlockState newState = mock(BlockState.class);
    SpigotBrush event = new SpigotBrush(block, newState, player);
    List<art.arcane.adapt.platform.event.block.BlockBrushEvent> seen = new ArrayList<>();
    bus.register(this, art.arcane.adapt.platform.event.block.BlockBrushEvent.class, EventPriority.HIGHEST, core -> {
      seen.add(core);
      core.setCancelled(true);
    });

    bridgeUnchecked(brush, event);

    assertThat(seen).hasSize(1);
    assertThat(seen.getFirst().player()).isSameAs(refs.wrap(player, PlayerRef.class));
    assertThat(seen.getFirst().block()).isSameAs(BukkitRefs.block(block));
    assertThat(event.isCancelled()).isTrue();
  }

  @SuppressWarnings("unchecked")
  private <B extends Event> void bridgeUnchecked(Translation<B> translation, Event event) {
    translation.bridge((B) event, core -> bus.post(core, EventPriority.HIGHEST));
  }

  public static final class SpigotBrush extends org.bukkit.event.block.BlockEvent implements org.bukkit.event.Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BlockState newState;
    private final Player player;
    private boolean cancelled;

    public SpigotBrush(Block block, BlockState newState, Player player) {
      super(block);
      this.newState = newState;
      this.player = player;
    }

    public Player getPlayer() {
      return player;
    }

    public BlockState getNewState() {
      return newState;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
      this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
      return HANDLERS;
    }

    public static HandlerList getHandlerList() {
      return HANDLERS;
    }
  }

  private static Set<String> concreteCoreEvents() throws IOException, URISyntaxException {
    Path location = Path.of(AdaptEvent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    Set<String> events = new TreeSet<>();
    if (Files.isDirectory(location)) {
      collectEvents(location, events);
      return events;
    }
    try (FileSystem jar = FileSystems.newFileSystem(location)) {
      collectEvents(jar.getPath("/"), events);
    }
    return events;
  }

  private static void collectEvents(Path root, Set<String> events) throws IOException {
    Path eventPackage = root.resolve("art/arcane/adapt/platform/event");
    try (Stream<Path> files = Files.walk(eventPackage)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
        String relative = root.relativize(file).toString();
        String name = relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
        try {
          Class<?> type = Class.forName(name, false, AdaptEvent.class.getClassLoader());
          if (AdaptEvent.class.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers()) && !type.isAnonymousClass()) {
            events.add(type.getName());
          }
        } catch (ClassNotFoundException missing) {
          throw new IllegalStateException(missing);
        }
      }
    }
  }
}
