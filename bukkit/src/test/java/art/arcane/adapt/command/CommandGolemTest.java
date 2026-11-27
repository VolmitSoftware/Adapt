package art.arcane.adapt.command;

import art.arcane.adapt.engine.addon.GolemData;
import art.arcane.adapt.TestVersion;
import art.arcane.adapt.content.golem.command.GolemGiveCatalog;
import art.arcane.adapt.content.golem.command.GolemCommand;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.command.CommandCaller;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.bukkit.command.BukkitCommandSource;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.localization.BuiltInText;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.gui.RosterGui;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeService;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.engine.world.AdaptDebugMode;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.runtime.DirectorParameterDescriptor;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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
  private static final UUID TARGET_WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
  private static final double FEET_X = 12.5D;
  private static final double FEET_Y = 70.0D;
  private static final double FEET_Z = -3.5D;

  private final GolemCommand command = new GolemCommand();
  private CommandCaller caller;
  private CommandSender admin;
  private Player target;
  private PlayerRef targetRef;
  private AdaptScheduler scheduler;
  private PlayerData data;
  private AdaptServer server;
  private GolemRegistry registry;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<GolemItems> items;
  private final Map<ItemStack, String> labels = new HashMap<>();
  private final List<ItemStack> given = new ArrayList<>();
  private World targetWorld;
  private Location targetLocation;
  private int inventoryCapacity;

  @BeforeAll
  static void initializeSounds() {
    TestVersion.mockSoundRegistry();
  }

  @BeforeEach
  void declareGolemData() {
    GolemData.declare();
  }

  @BeforeEach
  void prepareTarget() {
    BuiltInText.install();
    admin = mock(CommandSender.class);
    when(admin.hasPermission(GolemCommand.ADMIN_PERMISSION)).thenReturn(true);
    target = mock(Player.class);
    when(target.getUniqueId()).thenReturn(OWNER);
    when(target.getName()).thenReturn("Steve");
    when(target.getPlayer()).thenReturn(target);
    data = new PlayerData();
    server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    doReturn(server).when(core).getAdaptServer();
    when(server.getOnlineAdaptPlayer(OWNER)).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    targetRef = BukkitRefs.player(target);
    when(core.getPlatform().host().player(OWNER)).thenReturn(targetRef);
    when(adaptPlayer.getPlayer()).thenReturn(targetRef);
    when(adaptPlayer.getData()).thenReturn(data);
    registry = mock(GolemRegistry.class);
    scheduler = core.getPlatform().scheduler();
    when(scheduler.isRegionized()).thenReturn(false);
    when(scheduler.isTickThread()).thenReturn(true);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    inventoryCapacity = Integer.MAX_VALUE;
    targetWorld = mock(World.class);
    targetLocation = mock(Location.class);
    when(targetWorld.getUID()).thenReturn(TARGET_WORLD);
    when(targetLocation.getWorld()).thenReturn(targetWorld);
    when(targetLocation.getX()).thenReturn(FEET_X);
    when(targetLocation.getY()).thenReturn(FEET_Y);
    when(targetLocation.getZ()).thenReturn(FEET_Z);
    when(target.getWorld()).thenReturn(targetWorld);
    when(target.getLocation()).thenReturn(targetLocation);
    PlayerInventory targetInventory = recordingInventory(given);
    when(target.getInventory()).thenReturn(targetInventory);
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.resolve(any(Id.class))).thenAnswer(call -> {
      String id = call.<Id>getArgument(0).path();
      return BukkitRefs.item(stack(id, id.equals("crook") ? 1 : id.equals("blank_node") ? 64 : 16));
    });
    items.when(GolemItems::masterworkHeart).thenAnswer(call -> BukkitRefs.item(stack("masterwork_heart", 1)));
    caller = caller(admin);
    AddonCommands.install("adapt", List.of(command.tree()), null);
  }

  @AfterEach
  void clearContext() {
    BuiltInText.uninstall();
    AdaptDebugMode.setActive(OPERATOR, false);
    AddonCommands.uninstall("adapt", null);
    items.close();
    runtime.close();
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
    when(admin.hasPermission(AdaptCommands.ROOT_PERMISSION)).thenReturn(true);
    BukkitCommandSource source = new BukkitCommandSource(admin);

    List<String> everything = new AdaptCommands().complete(source, "adapt", new String[]{"golem", "give", "item="});
    List<String> runes = new AdaptCommands().complete(source, "adapt", new String[]{"golem", "give", "item=rune/might"});

    assertThat(everything).hasSize(45).contains("item=crook", "item=blank_core", "item=core/guardian", "item=blank_node", "item=node/fishing",
        "item=blank_rune", "item=rune/swiftness/1", "item=rune/bulwark/3", "item=masterwork_heart", "item=all");
    assertThat(everything).noneMatch(entry -> entry.contains("dormant") || entry.contains("shattered"));
    assertThat(runes).containsExactly("item=rune/might/1", "item=rune/might/2", "item=rune/might/3");
  }

  @Test
  void rosterOpensTheSendersRosterGui() {
    caller = caller(target);
    try (MockedStatic<RosterGui> gui = mockStatic(RosterGui.class)) {
      command.roster(caller);

      gui.verify(() -> RosterGui.open(targetRef));
    }
  }

  @Test
  void adminCommandsRefuseSendersWithoutTheGolemancyAdminPermission() {
    CommandSender player = mock(CommandSender.class);
    caller = caller(player);

    command.list(caller, new GolemCommand.KnownTarget(OWNER, "Steve"));
    command.release(caller, "3f2a", targetRef);
    command.wake(caller, targetRef);

    assertThat(messages(player)).hasSize(3).allSatisfy(message -> assertThat(message).contains(GolemCommand.ADMIN_PERMISSION));
    verify(registry, never()).releaseFor(any(), any());
    verify(registry, never()).wake(any());
  }

  @Test
  void nodeReturnRecoveryRequiresOperatorStatusEvenWithAdminPermission() {
    NodeService nodes = mock(NodeService.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(nodes));

    command.returns(caller, "---", "list");

    verify(nodes, never()).unresolvedReturns();
    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("operators"));
  }

  @Test
  void nodeReturnRecoveryListsStableIdentifiersAndDispatchesOnlyTheMatchingOwnersReturn() {
    when(admin.isOp()).thenReturn(true);
    NodeService nodes = mock(NodeService.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(nodes));
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    NodeService.ReturnRecord pending = new NodeService.ReturnRecord(key, OWNER, NodeType.GUARD);
    when(nodes.unresolvedReturns()).thenReturn(List.of(pending));
    when(nodes.resolveReturn(OWNER, key, false)).thenReturn(true);
    when(scheduler.runAt(any(Loc.class), any(Runnable.class), anyLong())).thenAnswer(call -> {
      call.<Runnable>getArgument(1).run();
      return true;
    });
    WorldRef world = mock(WorldRef.class);
    when(core.getPlatform().host().world(WORLD)).thenReturn(world);
    command.returns(caller, "---", "list");
    assertThat(messages(admin)).anySatisfy(message -> assertThat(message).contains(NodeService.returnId(key)).contains(OWNER.toString()));
    command.returns(caller, NodeService.returnId(key), "missing");

    verify(nodes).resolveReturn(OWNER, key, false);
  }

  @Test
  void anInvalidNodeReturnOutcomeDoesNotResolveAnything() {
    when(admin.isOp()).thenReturn(true);
    NodeService nodes = mock(NodeService.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(nodes));
    when(nodes.unresolvedReturns()).thenReturn(List.of());

    command.returns(caller, "---", "retry");

    verify(nodes, never()).resolveReturn(any(), any(), anyBoolean());
    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("confirming no item"));
  }

  @Test
  void listShowsEveryGolemInTheTargetsRoster() {
    data.section(GolemRoster.section()).add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, GolemRoster.chunkKey(2, -1), 0L));
    data.section(GolemRoster.section()).add(new GolemRoster.Binding(SECOND, GolemBody.FROST, GolemCoreType.BLANK, WORLD, 0L, 0L));
    data.section(GolemRoster.section()).stow(SECOND, GolemRoster.Status.PACKED, "{\"packed\":true}");
    WorldRef world = mock(WorldRef.class);
    when(world.name()).thenReturn("overworld");
    when(core.getPlatform().host().world(WORLD)).thenReturn(world);

    command.list(caller, new GolemCommand.KnownTarget(OWNER, "Steve"));

    List<String> sent = messages(admin);
    assertThat(sent).anySatisfy(message -> assertThat(message).contains("Steve").contains("2"));
    assertThat(sent).anySatisfy(message -> assertThat(message).contains("3f2a91c0").contains("Iron").contains("Guardian").contains("Active")
        .contains("overworld").contains("40").contains("-8"));
    assertThat(sent).anySatisfy(message -> assertThat(message).contains("3f2b0000").contains("Frost").contains("Packed"));
  }

  @Test
  void listUsesTheSingularHeaderForOneGolem() {
    data.section(GolemRoster.section()).add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));

    command.list(caller, new GolemCommand.KnownTarget(OWNER, "Steve"));

    List<String> sent = messages(admin);
    assertThat(sent).anySatisfy(message -> assertThat(message).contains("Steve has 1 golem:"));
    assertThat(sent).noneSatisfy(message -> assertThat(message).contains("1 golems"));
  }

  @Test
  void listReportsAnEmptyRoster() {
    command.list(caller, new GolemCommand.KnownTarget(OWNER, "Steve"));

    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("Steve").contains("no golems"));
  }

  @Test
  void releaseResolvesTheStartOfTheIdAndReleasesThroughTheRegistry() {
    data.section(GolemRoster.section()).add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    data.section(GolemRoster.section()).add(new GolemRoster.Binding(SECOND, GolemBody.FROST, GolemCoreType.BLANK, WORLD, 0L, 0L));
    when(registry.releaseFor(OWNER, FIRST)).thenReturn(CompletableFuture.completedFuture(GolemRegistry.AdminRelease.RELEASED));

    command.release(caller, "3F2A", targetRef);

    verify(registry).releaseFor(OWNER, FIRST);
    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("Released").contains("3f2a91c0").contains("Steve"));
  }

  @Test
  void aReleaseTheRegistryRefusesIsReported() {
    data.section(GolemRoster.section()).add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    when(registry.releaseFor(OWNER, FIRST)).thenReturn(CompletableFuture.completedFuture(GolemRegistry.AdminRelease.REFUSED));

    command.release(caller, FIRST.toString(), targetRef);

    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("could not be released").contains("3f2a91c0"));
  }

  @Test
  void anActiveGolemInAnUnloadedChunkIsReportedInsteadOfReleased() {
    data.section(GolemRoster.section()).add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    when(registry.releaseFor(OWNER, FIRST)).thenReturn(CompletableFuture.completedFuture(GolemRegistry.AdminRelease.ACTIVE_UNLOADED));

    command.release(caller, "3f2a", targetRef);

    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("3f2a91c0").contains("unloaded chunk"));
  }

  @Test
  void listReadsAnOfflinePlayersRosterWithoutLoadingThem() {
    UUID offlineId = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    GolemCommand.KnownTarget offline = new GolemCommand.KnownTarget(offlineId, "Alex");
    PlayerData stored = new PlayerData();
    stored.section(GolemRoster.section()).add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    when(server.peekData(offlineId)).thenReturn(stored);
    doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(scheduler).runAsync(any(Runnable.class), anyLong());

    command.list(caller, offline);

    verify(scheduler).runAsync(any(Runnable.class), anyLong());
    List<String> sent = messages(admin);
    assertThat(sent).anySatisfy(message -> assertThat(message).contains("Alex").contains("1"));
    assertThat(sent).anySatisfy(message -> assertThat(message).contains("3f2a91c0").contains("Guardian"));
  }

  @Test
  void rosterRefusesWhileGolemancyIsNotRunning() {
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.empty());
    caller = caller(target);
    try (MockedStatic<RosterGui> gui = mockStatic(RosterGui.class); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getWorld(TARGET_WORLD)).thenReturn(targetWorld);
      command.roster(caller);

      gui.verify(() -> RosterGui.open(any(PlayerRef.class)), never());
    }
    assertThat(messages(target)).singleElement().satisfies(message -> assertThat(message).contains("golemancy").contains("disabled"));
  }

  @Test
  void releaseRefusesAnUnknownOrAmbiguousIdWithoutTouchingTheRegistry() {
    data.section(GolemRoster.section()).add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    data.section(GolemRoster.section()).add(new GolemRoster.Binding(SECOND, GolemBody.FROST, GolemCoreType.BLANK, WORLD, 0L, 0L));

    command.release(caller, "ffff", targetRef);
    command.release(caller, "3f2", targetRef);

    List<String> sent = messages(admin);
    assertThat(sent).hasSize(2);
    assertThat(sent.getFirst()).contains("ffff").contains("Steve");
    assertThat(sent.getLast()).contains("3f2").contains("More than one");
    verify(registry, never()).releaseFor(any(), any());
  }

  @Test
  void releaseAndWakeNeedGolemancyRunning() {
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.empty());
    data.section(GolemRoster.section()).add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));

    command.release(caller, "3f2a", targetRef);
    command.wake(caller, targetRef);

    assertThat(messages(admin)).hasSize(2).allSatisfy(message -> assertThat(message).contains("golemancy").contains("disabled"));
  }

  @Test
  void wakeAsksTheRegistryToWakeTheTargetsGolems() {
    when(registry.wake(OWNER)).thenReturn(3);

    command.wake(caller, targetRef);

    verify(registry).wake(OWNER);
    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("Steve").contains("3"));
  }

  @Test
  void consoleAdminsMustNameAPlayer() {

    command.list(caller, null);
    command.release(caller, "3f2a", null);
    command.wake(caller, null);

    assertThat(messages(admin)).hasSize(3);
    verify(registry, never()).wake(any());
    verify(registry, never()).releaseFor(any(), any());
  }

  @Test
  void giveHandsTheItemToTheTargetAndConfirmsWhatAndToWhom() {
    command.give(caller, "core/guardian", 1, targetRef);

    assertThat(given()).containsExactly("core/guardian x1");
    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("Gave 1").contains("core/guardian").contains("Steve"));
  }

  @Test
  void giveBuildsEveryKindOfItemThroughGolemItems() {

    for (String id : List.of("crook", "blank_core", "core/gatherer", "blank_node", "node/deposit", "blank_rune", "rune/might/3", "masterwork_heart")) {
      command.give(caller, id, 1, targetRef);
    }

    assertThat(given()).containsExactly("crook x1", "blank_core x1", "core/gatherer x1", "blank_node x1", "node/deposit x1", "blank_rune x1",
        "rune/might/3 x1", "masterwork_heart x1");
    items.verify(() -> GolemItems.resolve(Id.of("golemancy", "core/gatherer")));
    items.verify(() -> GolemItems.resolve(Id.of("golemancy", "rune/might/3")));
    items.verify(GolemItems::masterworkHeart);
  }

  @Test
  void giveAcceptsIdsInAnyCaseWithSurroundingSpaces() {
    command.give(caller, "  Core/Guardian ", 1, targetRef);

    assertThat(given()).containsExactly("core/guardian x1");
  }

  @Test
  void giveAllHandsOneOfEveryItemWithEveryRuneTierAndIgnoresTheAmount() {
    command.give(caller, "all", 9, targetRef);

    List<String> ids = GolemGiveCatalog.ids();
    assertThat(ids).hasSize(44);
    assertThat(given()).hasSize(44).containsExactlyInAnyOrderElementsOf(ids.stream().map(id -> id + " x1").toList());
    assertThat(given()).contains("crook x1", "masterwork_heart x1", "rune/swiftness/1 x1", "rune/swiftness/2 x1", "rune/swiftness/3 x1",
        "rune/bulwark/3 x1", "blank_rune x1", "node/fishing x1", "core/angler x1");
    assertThat(messages(admin)).singleElement().satisfies(message -> assertThat(message).contains("44").contains("Steve"));
  }

  @Test
  void giveClampsTheAmountToOneThroughSixtyFour() {

    command.give(caller, "blank_node", 1000, targetRef);
    command.give(caller, "blank_node", 0, targetRef);
    command.give(caller, "blank_node", -5, targetRef);
    command.give(caller, "blank_node", 64, targetRef);
    command.give(caller, "blank_node", 12, targetRef);

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

    command.give(caller, "core/guardian", 40, targetRef);
    assertThat(given()).containsExactly("core/guardian x16", "core/guardian x16", "core/guardian x8");

    given.clear();
    command.give(caller, "crook", 3, targetRef);
    assertThat(given()).containsExactly("crook x1", "crook x1", "crook x1");

    given.clear();
    command.give(caller, "crook", 1000, targetRef);
    assertThat(given()).hasSize(64).allMatch("crook x1"::equals);
  }

  @Test
  void giveDropsWhatDoesNotFitAtTheTargetsFeet() {
    inventoryCapacity = 2;

    command.give(caller, "crook", 5, targetRef);

    assertThat(given()).hasSize(2);
    ArgumentCaptor<ItemStack> dropped = ArgumentCaptor.forClass(ItemStack.class);
    verify(targetWorld, times(3)).dropItemNaturally(argThat(this::atTheTargetsFeet), dropped.capture());
    assertThat(dropped.getAllValues()).allSatisfy(stack -> assertThat(labels.get(stack)).isEqualTo("crook")).doesNotContainAnyElementsOf(given);
    List<String> sent = messages(admin);
    assertThat(sent).hasSize(2);
    assertThat(sent.getFirst()).contains("Gave 5").contains("crook").contains("Steve");
    assertThat(sent.getLast()).contains("3").contains("Steve").contains("feet");
  }

  @Test
  void giveAllDropsTheLeftoversThatDoNotFit() {
    inventoryCapacity = 40;

    command.give(caller, "all", 1, targetRef);

    assertThat(given()).hasSize(40);
    verify(targetWorld, times(4)).dropItemNaturally(argThat(this::atTheTargetsFeet), any(ItemStack.class));
    assertThat(messages(admin)).hasSize(2).anySatisfy(message -> assertThat(message).contains("44"))
        .anySatisfy(message -> assertThat(message).contains("4").contains("feet"));
  }

  @Test
  void giveRefusesUnknownAndOwnerBoundItemIds() {

    command.give(caller, "dormant_golem", 1, targetRef);
    command.give(caller, "shattered_core", 1, targetRef);
    command.give(caller, "core/blank", 1, targetRef);
    command.give(caller, "rune/might/4", 1, targetRef);
    command.give(caller, "nonsense", 1, targetRef);

    assertThat(given()).isEmpty();
    items.verify(() -> GolemItems.resolve(any(Id.class)), never());
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
    caller = caller(nobody);

    command.give(caller, "crook", 1, targetRef);

    assertThat(given()).isEmpty();
    assertThat(messages(nobody)).singleElement().satisfies(message -> assertThat(message).contains(GolemCommand.ADMIN_PERMISSION));
  }

  @Test
  void aPlayerInDebugModeStillNeedsThePermissionToGive() {
    Player operator = operator(false);
    AdaptDebugMode.setActive(OPERATOR, true);
    caller = caller(operator);

    command.give(caller, "crook", 1, targetRef);

    assertThat(given()).isEmpty();
    assertThat(messages(operator)).singleElement().satisfies(message -> assertThat(message).contains(GolemCommand.ADMIN_PERMISSION));
  }

  @Test
  void aPlayerWithoutDebugModeIsRefusedAndToldHowToEnableIt() {
    Player operator = operator(true);
    caller = caller(operator);

    command.give(caller, "crook", 1, targetRef);
    command.give(caller, "all", 1, null);

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
    caller = caller(operator);

    command.give(caller, "blank_core", 2, null);
    command.give(caller, "crook", 1, targetRef);

    assertThat(labelsOf(own)).containsExactly("blank_core x2");
    assertThat(given()).containsExactly("crook x1");
    assertThat(messages(operator)).hasSize(2);
    assertThat(messages(operator).get(0)).contains("Gave 2").contains("blank_core").contains("Alex");
    assertThat(messages(operator).get(1)).contains("Gave 1").contains("crook").contains("Steve");
  }

  @Test
  void theConsoleGivesWithoutDebugModeButMustNameAPlayer() {

    command.give(caller, "crook", 1, null);
    assertThat(given()).isEmpty();
    assertThat(messages(admin)).hasSize(1);

    command.give(caller, "crook", 1, targetRef);
    assertThat(given()).containsExactly("crook x1");
    assertThat(messages(admin)).hasSize(2);
  }

  @Test
  void giveChangesTheInventoryOnTheTargetsOwnScheduler() {
    when(scheduler.isRegionized()).thenReturn(true);
    when(scheduler.isTickThread()).thenReturn(false);
    when(scheduler.runEntity(same(targetRef), any(Runnable.class), anyLong(), any())).thenReturn(true);

    command.give(caller, "crook", 1, targetRef);

    assertThat(given()).isEmpty();
    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).runEntity(same(targetRef), task.capture(), anyLong(), any());
    task.getValue().run();
    assertThat(given()).containsExactly("crook x1");
  }

  private DirectorRuntimeEngine engine() {
    return new AdaptCommands().engine();
  }

  private static CommandCaller caller(CommandSender sender) {
    BukkitCommandSource source = new BukkitCommandSource(sender);
    return new CommandCaller() {
      @Override
      public String name() {
        return source.name();
      }

      @Override
      public Optional<PlayerRef> player() {
        return source.player();
      }

      @Override
      public boolean hasPermission(String node) {
        return source.hasPermission(node);
      }

      @Override
      public boolean isOperator() {
        return source.isOp();
      }

      @Override
      public void send(Text text) {
        source.sendSection(text.resolve());
      }
    };
  }

  private Player operator(boolean permitted) {
    Player operator = mock(Player.class);
    when(operator.getUniqueId()).thenReturn(OPERATOR);
    when(operator.getName()).thenReturn("Alex");
    when(operator.hasPermission(GolemCommand.ADMIN_PERMISSION)).thenReturn(permitted);
    return operator;
  }

  private boolean atTheTargetsFeet(Location location) {
    return location != null && location.getWorld() == targetWorld && location.getX() == FEET_X && location.getY() == FEET_Y
        && location.getZ() == FEET_Z;
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
    when(item.getType()).thenReturn(Material.STONE);
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
    for (DirectorParameterDescriptor descriptor : node.getDescriptor().getParameters()) {
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
