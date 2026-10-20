package art.arcane.adapt.command;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.gui.RosterGui;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.world.AdaptDebugMode;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionMode;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorParameterDescriptor;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeParameter;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandGolemTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID FIRST = UUID.fromString("3f2a91c0-0000-0000-0000-000000000001");
  private static final UUID SECOND = UUID.fromString("3f2b0000-0000-0000-0000-000000000002");
  private static final UUID OPERATOR = UUID.fromString("00000000-0000-0000-0000-0000000000b3");

  private CommandSender admin;
  private Player target;
  private PlayerData data;
  private AdaptServer server;
  private GolemRegistry registry;
  private MockedStatic<J> scheduling;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<GolemItems> items;
  private final Map<ItemStack, String> labels = new HashMap<>();
  private final List<ItemStack> given = new ArrayList<>();
  private World targetWorld;
  private Location targetLocation;
  private int inventoryCapacity;

  @BeforeAll
  static void initializeSounds() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<Sound> sounds = Registry.SOUNDS;
      doAnswer(call -> mock(Sound.class)).when(sounds).getOrThrow(any(Key.class));
      assertThat(Sound.BLOCK_DEEPSLATE_BREAK).isNotNull();
    }
  }

  @BeforeEach
  void prepareTarget() {
    admin = mock(CommandSender.class);
    when(admin.hasPermission(CommandGolem.ADMIN_PERMISSION)).thenReturn(true);
    target = mock(Player.class);
    when(target.getUniqueId()).thenReturn(OWNER);
    when(target.getName()).thenReturn("Steve");
    when(target.getPlayer()).thenReturn(target);
    data = new PlayerData();
    server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getOnlineAdaptPlayer(OWNER)).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(target);
    when(adaptPlayer.getData()).thenReturn(data);
    registry = mock(GolemRegistry.class);
    scheduling = mockStatic(J.class);
    scheduling.when(J::isFoliaThreading).thenReturn(false);
    scheduling.when(J::isPrimaryThread).thenReturn(true);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    inventoryCapacity = Integer.MAX_VALUE;
    targetWorld = mock(World.class);
    targetLocation = mock(Location.class);
    when(target.getWorld()).thenReturn(targetWorld);
    when(target.getLocation()).thenReturn(targetLocation);
    PlayerInventory targetInventory = recordingInventory(given);
    when(target.getInventory()).thenReturn(targetInventory);
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.resolve(any(NamespacedKey.class))).thenAnswer(call -> {
      String id = call.<NamespacedKey>getArgument(0).getKey();
      return stack(id, id.equals("crook") ? 1 : id.equals("blank_node") ? 64 : 16);
    });
    items.when(GolemItems::masterworkHeart).thenAnswer(call -> stack("masterwork_heart", 1));
    BukkitDirectorContext.touch(admin);
  }

  @AfterEach
  void clearContext() {
    AdaptDebugMode.setActive(OPERATOR, false);
    BukkitDirectorContext.remove();
    items.close();
    runtime.close();
    scheduling.close();
  }

  @Test
  void theGolemGroupOpensTheRosterForPlayersAndKeysEveryOptionalAdminArgument() {
    DirectorRuntimeEngine engine = engine();

    DirectorRuntimeNode golem = child(engine.getRoot(), "golem");

    DirectorRuntimeNode roster = child(golem, "roster");
    assertThat(roster.getDescriptor().getOrigin()).isEqualTo(DirectorOrigin.PLAYER);
    assertThat(roster.getParameters()).isEmpty();
    assertThat(parameters(child(golem, "list"))).containsExactly("player=");
    assertThat(parameters(child(golem, "release"))).containsExactly("id", "player=");
    assertThat(parameters(child(golem, "wake"))).containsExactly("player=");
    assertThat(parameters(child(golem, "give"))).containsExactly("item", "amount=", "player=");
  }

  @Test
  void giveTabCompletesEveryItemIdAndAll() {
    DirectorRuntimeEngine engine = engine();

    List<String> everything = engine.tabComplete(new DirectorInvocation(mock(DirectorSender.class), "adapt", List.of("golem", "give", "item=")));
    List<String> runes = engine.tabComplete(new DirectorInvocation(mock(DirectorSender.class), "adapt", List.of("golem", "give", "item=rune/might")));

    assertThat(everything).hasSize(45).contains("item=crook", "item=blank_core", "item=core/guardian", "item=blank_node", "item=node/fishing",
        "item=blank_rune", "item=rune/swiftness/1", "item=rune/bulwark/3", "item=masterwork_heart", "item=all");
    assertThat(everything).noneMatch(entry -> entry.contains("dormant") || entry.contains("shattered"));
    assertThat(runes).containsExactly("item=rune/might/1", "item=rune/might/2", "item=rune/might/3");
  }

  @Test
  void rosterOpensTheSendersRosterGui() {
    BukkitDirectorContext.touch(target);
    try (MockedStatic<RosterGui> gui = mockStatic(RosterGui.class)) {
      new CommandGolem().roster();

      gui.verify(() -> RosterGui.open(target));
    }
  }

  @Test
  void adminCommandsRefuseSendersWithoutTheGolemancyAdminPermission() {
    CommandSender player = mock(CommandSender.class);
    BukkitDirectorContext.touch(player);
    CommandGolem command = new CommandGolem();

    command.list(target);
    command.release("3f2a", target);
    command.wake(target);

    assertThat(messages(player)).hasSize(3).allSatisfy(message -> assertThat(message).contains(CommandGolem.ADMIN_PERMISSION));
    verify(registry, never()).releaseFor(any(), any());
    verify(registry, never()).wake(any());
  }

  @Test
  void listShowsEveryGolemInTheTargetsRoster() {
    data.getGolemancy().add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, GolemRoster.chunkKey(2, -1), 0L));
    data.getGolemancy().add(new GolemRoster.Binding(SECOND, GolemBody.FROST, GolemCoreType.BLANK, WORLD, 0L, 0L));
    data.getGolemancy().stow(SECOND, GolemRoster.Status.PACKED, "{\"packed\":true}");
    World world = mock(World.class);
    when(world.getName()).thenReturn("overworld");

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(world);
      new CommandGolem().list(target);
    }

    List<String> sent = messages(admin);
    assertThat(sent).anySatisfy(message -> assertThat(message).contains("Steve").contains("2"));
    assertThat(sent).anySatisfy(message -> assertThat(message).contains("3f2a91c0").contains("Iron").contains("Guardian").contains("Active")
        .contains("overworld").contains("40").contains("-8"));
    assertThat(sent).anySatisfy(message -> assertThat(message).contains("3f2b0000").contains("Frost").contains("Packed"));
  }

  @Test
  void listUsesTheSingularHeaderForOneGolem() {
    data.getGolemancy().add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      new CommandGolem().list(target);
    }

    List<String> sent = messages(admin);
    assertThat(sent).anySatisfy(message -> assertThat(message).contains("Steve has 1 golem:"));
    assertThat(sent).noneSatisfy(message -> assertThat(message).contains("1 golems"));
  }

  @Test
  void listReportsAnEmptyRoster() {
    new CommandGolem().list(target);

    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("Steve").contains("no golems"));
  }

  @Test
  void releaseResolvesTheStartOfTheIdAndReleasesThroughTheRegistry() {
    data.getGolemancy().add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    data.getGolemancy().add(new GolemRoster.Binding(SECOND, GolemBody.FROST, GolemCoreType.BLANK, WORLD, 0L, 0L));
    when(registry.releaseFor(OWNER, FIRST)).thenReturn(CompletableFuture.completedFuture(GolemRegistry.AdminRelease.RELEASED));

    new CommandGolem().release("3F2A", target);

    verify(registry).releaseFor(OWNER, FIRST);
    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("Released").contains("3f2a91c0").contains("Steve"));
  }

  @Test
  void aReleaseTheRegistryRefusesIsReported() {
    data.getGolemancy().add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    when(registry.releaseFor(OWNER, FIRST)).thenReturn(CompletableFuture.completedFuture(GolemRegistry.AdminRelease.REFUSED));

    new CommandGolem().release(FIRST.toString(), target);

    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("could not be released").contains("3f2a91c0"));
  }

  @Test
  void anActiveGolemInAnUnloadedChunkIsReportedInsteadOfReleased() {
    data.getGolemancy().add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    when(registry.releaseFor(OWNER, FIRST)).thenReturn(CompletableFuture.completedFuture(GolemRegistry.AdminRelease.ACTIVE_UNLOADED));

    new CommandGolem().release("3f2a", target);

    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("3f2a91c0").contains("unloaded chunk"));
  }

  @Test
  void listReadsAnOfflinePlayersRosterWithoutLoadingThem() {
    UUID offlineId = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    OfflinePlayer offline = mock(OfflinePlayer.class);
    when(offline.getUniqueId()).thenReturn(offlineId);
    when(offline.getName()).thenReturn("Alex");
    PlayerData stored = new PlayerData();
    stored.getGolemancy().add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    when(server.peekData(offlineId)).thenReturn(stored);
    scheduling.when(() -> J.a(any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    });

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      new CommandGolem().list(offline);
    }

    scheduling.verify(() -> J.a(any(Runnable.class)));
    List<String> sent = messages(admin);
    assertThat(sent).anySatisfy(message -> assertThat(message).contains("Alex").contains("1"));
    assertThat(sent).anySatisfy(message -> assertThat(message).contains("3f2a91c0").contains("Guardian"));
  }

  @Test
  void rosterRefusesWhileGolemancyIsNotRunning() {
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.empty());
    BukkitDirectorContext.touch(target);
    try (MockedStatic<RosterGui> gui = mockStatic(RosterGui.class)) {
      new CommandGolem().roster();

      gui.verify(() -> RosterGui.open(any(Player.class)), never());
    }
    assertThat(messages(target)).singleElement().satisfies(message -> assertThat(message).contains("golemancy").contains("disabled"));
  }

  @Test
  void releaseRefusesAnUnknownOrAmbiguousIdWithoutTouchingTheRegistry() {
    data.getGolemancy().add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    data.getGolemancy().add(new GolemRoster.Binding(SECOND, GolemBody.FROST, GolemCoreType.BLANK, WORLD, 0L, 0L));
    CommandGolem command = new CommandGolem();

    command.release("ffff", target);
    command.release("3f2", target);

    List<String> sent = messages(admin);
    assertThat(sent).hasSize(2);
    assertThat(sent.getFirst()).contains("ffff").contains("Steve");
    assertThat(sent.getLast()).contains("3f2").contains("More than one");
    verify(registry, never()).releaseFor(any(), any());
  }

  @Test
  void releaseAndWakeNeedGolemancyRunning() {
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.empty());
    data.getGolemancy().add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    CommandGolem command = new CommandGolem();

    command.release("3f2a", target);
    command.wake(target);

    assertThat(messages(admin)).hasSize(2).allSatisfy(message -> assertThat(message).contains("golemancy").contains("disabled"));
  }

  @Test
  void wakeAsksTheRegistryToWakeTheTargetsGolems() {
    when(registry.wake(OWNER)).thenReturn(3);

    new CommandGolem().wake(target);

    verify(registry).wake(OWNER);
    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("Steve").contains("3"));
  }

  @Test
  void consoleAdminsMustNameAPlayer() {
    CommandGolem command = new CommandGolem();

    command.list(null);
    command.release("3f2a", null);
    command.wake(null);

    assertThat(messages(admin)).hasSize(3);
    verify(registry, never()).wake(any());
    verify(registry, never()).releaseFor(any(), any());
  }

  @Test
  void giveHandsTheItemToTheTargetAndConfirmsWhatAndToWhom() {
    new CommandGolem().give("core/guardian", 1, target);

    assertThat(given()).containsExactly("core/guardian x1");
    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("Gave 1").contains("core/guardian").contains("Steve"));
  }

  @Test
  void giveBuildsEveryKindOfItemThroughGolemItems() {
    CommandGolem command = new CommandGolem();

    for (String id : List.of("crook", "blank_core", "core/gatherer", "blank_node", "node/deposit", "blank_rune", "rune/might/3", "masterwork_heart")) {
      command.give(id, 1, target);
    }

    assertThat(given()).containsExactly("crook x1", "blank_core x1", "core/gatherer x1", "blank_node x1", "node/deposit x1", "blank_rune x1",
        "rune/might/3 x1", "masterwork_heart x1");
    items.verify(() -> GolemItems.resolve(new NamespacedKey("golemancy", "core/gatherer")));
    items.verify(() -> GolemItems.resolve(new NamespacedKey("golemancy", "rune/might/3")));
    items.verify(GolemItems::masterworkHeart);
  }

  @Test
  void giveAcceptsIdsInAnyCaseWithSurroundingSpaces() {
    new CommandGolem().give("  Core/Guardian ", 1, target);

    assertThat(given()).containsExactly("core/guardian x1");
  }

  @Test
  void giveAllHandsOneOfEveryItemWithEveryRuneTierAndIgnoresTheAmount() {
    new CommandGolem().give("all", 9, target);

    List<String> ids = GolemGiveCatalog.ids();
    assertThat(ids).hasSize(44);
    assertThat(given()).hasSize(44).containsExactlyInAnyOrderElementsOf(ids.stream().map(id -> id + " x1").toList());
    assertThat(given()).contains("crook x1", "masterwork_heart x1", "rune/swiftness/1 x1", "rune/swiftness/2 x1", "rune/swiftness/3 x1",
        "rune/bulwark/3 x1", "blank_rune x1", "node/fishing x1", "core/angler x1");
    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("44").contains("Steve"));
  }

  @Test
  void giveClampsTheAmountToOneThroughSixtyFour() {
    CommandGolem command = new CommandGolem();

    command.give("blank_node", 1000, target);
    command.give("blank_node", 0, target);
    command.give("blank_node", -5, target);
    command.give("blank_node", 64, target);
    command.give("blank_node", 12, target);

    assertThat(given()).containsExactly("blank_node x64", "blank_node x1", "blank_node x1", "blank_node x64", "blank_node x12");
    List<String> sent = messages(admin);
    assertThat(sent).hasSize(5);
    assertThat(sent.get(0)).contains("Gave 64");
    assertThat(sent.get(1)).contains("Gave 1 ");
    assertThat(sent.get(2)).contains("Gave 1 ");
    assertThat(sent.get(3)).contains("Gave 64");
    assertThat(sent.get(4)).contains("Gave 12");
  }

  @Test
  void giveSplitsTheAmountIntoTheItemsOwnStacksAndSingleStackItemsStaySeparate() {
    CommandGolem command = new CommandGolem();

    command.give("core/guardian", 40, target);
    assertThat(given()).containsExactly("core/guardian x16", "core/guardian x16", "core/guardian x8");

    given.clear();
    command.give("crook", 3, target);
    assertThat(given()).containsExactly("crook x1", "crook x1", "crook x1");

    given.clear();
    command.give("crook", 1000, target);
    assertThat(given()).hasSize(64).allMatch("crook x1"::equals);
  }

  @Test
  void giveDropsWhatDoesNotFitAtTheTargetsFeet() {
    inventoryCapacity = 2;

    new CommandGolem().give("crook", 5, target);

    assertThat(given()).hasSize(2);
    ArgumentCaptor<ItemStack> dropped = ArgumentCaptor.forClass(ItemStack.class);
    verify(targetWorld, times(3)).dropItemNaturally(same(targetLocation), dropped.capture());
    assertThat(dropped.getAllValues()).allSatisfy(stack -> assertThat(labels.get(stack)).isEqualTo("crook")).doesNotContainAnyElementsOf(given);
    List<String> sent = messages(admin);
    assertThat(sent).hasSize(2);
    assertThat(sent.getFirst()).contains("Gave 5").contains("crook").contains("Steve");
    assertThat(sent.getLast()).contains("3").contains("Steve").contains("feet");
  }

  @Test
  void giveAllDropsTheLeftoversThatDoNotFit() {
    inventoryCapacity = 40;

    new CommandGolem().give("all", 1, target);

    assertThat(given()).hasSize(40);
    verify(targetWorld, times(4)).dropItemNaturally(same(targetLocation), any(ItemStack.class));
    assertThat(messages(admin)).hasSize(2).anySatisfy(message -> assertThat(message).contains("44"))
        .anySatisfy(message -> assertThat(message).contains("4").contains("feet"));
  }

  @Test
  void giveRefusesUnknownAndOwnerBoundItemIds() {
    CommandGolem command = new CommandGolem();

    command.give("dormant_golem", 1, target);
    command.give("shattered_core", 1, target);
    command.give("core/blank", 1, target);
    command.give("rune/might/4", 1, target);
    command.give("nonsense", 1, target);

    assertThat(given()).isEmpty();
    items.verify(() -> GolemItems.resolve(any(NamespacedKey.class)), never());
    items.verify(GolemItems::masterworkHeart, never());
    assertThat(messages(admin)).hasSize(5);
    assertThat(messages(admin).get(0)).contains("dormant_golem");
    assertThat(messages(admin).get(1)).contains("shattered_core");
    assertThat(messages(admin).get(2)).contains("core/blank");
    assertThat(messages(admin).get(3)).contains("rune/might/4");
    assertThat(messages(admin).get(4)).contains("nonsense").contains("item=");
  }

  @Test
  void giveNeedsTheGolemancyAdminPermission() {
    CommandSender nobody = mock(CommandSender.class);
    BukkitDirectorContext.touch(nobody);

    new CommandGolem().give("crook", 1, target);

    assertThat(given()).isEmpty();
    assertThat(messages(nobody)).singleElement().satisfies(message -> assertThat(message).contains(CommandGolem.ADMIN_PERMISSION));
  }

  @Test
  void aPlayerInDebugModeStillNeedsThePermissionToGive() {
    Player operator = operator(false);
    AdaptDebugMode.setActive(OPERATOR, true);
    BukkitDirectorContext.touch(operator);

    new CommandGolem().give("crook", 1, target);

    assertThat(given()).isEmpty();
    assertThat(messages(operator)).singleElement().satisfies(message -> assertThat(message).contains(CommandGolem.ADMIN_PERMISSION));
  }

  @Test
  void aPlayerWithoutDebugModeIsRefusedAndToldHowToEnableIt() {
    Player operator = operator(true);
    BukkitDirectorContext.touch(operator);

    new CommandGolem().give("crook", 1, target);
    new CommandGolem().give("all", 1, null);

    assertThat(given()).isEmpty();
    items.verifyNoInteractions();
    assertThat(messages(operator)).hasSize(2).allSatisfy(message -> assertThat(message).contains("/adapt debug mode"));
  }

  @Test
  void aPlayerInDebugModeGivesToThemselvesByDefaultAndToOthersByName() {
    List<ItemStack> own = new ArrayList<>();
    Player operator = operator(true);
    PlayerInventory ownInventory = recordingInventory(own);
    World ownWorld = mock(World.class);
    Location ownLocation = mock(Location.class);
    when(operator.getInventory()).thenReturn(ownInventory);
    when(operator.getWorld()).thenReturn(ownWorld);
    when(operator.getLocation()).thenReturn(ownLocation);
    AdaptDebugMode.setActive(OPERATOR, true);
    BukkitDirectorContext.touch(operator);
    CommandGolem command = new CommandGolem();

    command.give("blank_core", 2, null);
    command.give("crook", 1, target);

    assertThat(labelsOf(own)).containsExactly("blank_core x2");
    assertThat(given()).containsExactly("crook x1");
    assertThat(messages(operator)).hasSize(2);
    assertThat(messages(operator).get(0)).contains("Gave 2").contains("blank_core").contains("Alex");
    assertThat(messages(operator).get(1)).contains("Gave 1").contains("crook").contains("Steve");
  }

  @Test
  void theConsoleGivesWithoutDebugModeButMustNameAPlayer() {
    CommandGolem command = new CommandGolem();

    command.give("crook", 1, null);
    assertThat(given()).isEmpty();
    assertThat(messages(admin)).hasSize(1);

    command.give("crook", 1, target);
    assertThat(given()).containsExactly("crook x1");
    assertThat(messages(admin)).hasSize(2);
  }

  @Test
  void giveChangesTheInventoryOnTheTargetsOwnScheduler() {
    scheduling.when(J::isFoliaThreading).thenReturn(true);
    scheduling.when(J::isPrimaryThread).thenReturn(false);
    scheduling.when(() -> J.runEntity(same(target), any(Runnable.class))).thenReturn(true);

    new CommandGolem().give("crook", 1, target);

    assertThat(given()).isEmpty();
    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runEntity(same(target), task.capture()));
    task.getValue().run();
    assertThat(given()).containsExactly("crook x1");
  }

  private DirectorRuntimeEngine engine() {
    return DirectorEngineFactory.create(new CommandAdapt(), DirectorEngineOptions.builder()
        .contexts(new DirectorContextRegistry())
        .dispatcher((DirectorExecutionMode mode, Runnable runnable) -> runnable.run())
        .legacyHandlers(DirectorHelpRenderTest.resolveHandlers(new StringBuilder()))
        .build());
  }

  private Player operator(boolean permitted) {
    Player operator = mock(Player.class);
    when(operator.getUniqueId()).thenReturn(OPERATOR);
    when(operator.getName()).thenReturn("Alex");
    when(operator.hasPermission(CommandGolem.ADMIN_PERMISSION)).thenReturn(permitted);
    return operator;
  }

  private PlayerInventory recordingInventory(List<ItemStack> sink) {
    PlayerInventory inventory = mock(PlayerInventory.class);
    when(inventory.addItem(any(ItemStack[].class))).thenAnswer(call -> {
      Map<Integer, ItemStack> overflow = new HashMap<>();
      Object[] stacks = call.getArguments();
      for (int index = 0; index < stacks.length; index++) {
        ItemStack stack = (ItemStack) stacks[index];
        if (sink.size() < inventoryCapacity) {
          sink.add(stack);
        } else {
          overflow.put(index, stack);
        }
      }
      return overflow;
    });
    return inventory;
  }

  private ItemStack stack(String label, int maxStackSize) {
    ItemStack item = mock(ItemStack.class);
    AtomicInteger amount = new AtomicInteger(1);
    labels.put(item, label);
    when(item.getMaxStackSize()).thenReturn(maxStackSize);
    when(item.getAmount()).thenAnswer(call -> amount.get());
    doAnswer(call -> {
      amount.set(call.getArgument(0));
      return null;
    }).when(item).setAmount(anyInt());
    when(item.clone()).thenAnswer(call -> stack(label, maxStackSize));
    return item;
  }

  private List<String> given() {
    return labelsOf(given);
  }

  private List<String> labelsOf(List<ItemStack> stacks) {
    List<String> described = new ArrayList<>(stacks.size());
    for (ItemStack stack : stacks) {
      described.add(labels.get(stack) + " x" + stack.getAmount());
    }
    return described;
  }

  private static DirectorRuntimeNode child(DirectorRuntimeNode parent, String name) {
    for (DirectorRuntimeNode node : parent.getChildren()) {
      if (node.getDescriptor().getName().equals(name)) {
        return node;
      }
    }
    throw new AssertionError(parent.path() + " has no " + name);
  }

  private static List<String> parameters(DirectorRuntimeNode node) {
    List<String> names = new ArrayList<>();
    for (DirectorRuntimeParameter parameter : node.getParameters()) {
      DirectorParameterDescriptor descriptor = parameter.getDescriptor();
      names.add(descriptor.isRequired() ? descriptor.getName() : descriptor.getName() + "=");
    }
    return names;
  }

  private static List<String> messages(CommandSender sender) {
    List<String> sent = new ArrayList<>();
    for (Invocation invocation : Mockito.mockingDetails(sender).getInvocations()) {
      String method = invocation.getMethod().getName();
      if ((method.equals("sendRichMessage") || method.equals("sendMessage")) && invocation.getArguments().length == 1
          && invocation.getArgument(0) instanceof String text) {
        sent.add(text);
      }
    }
    return sent;
  }
}
