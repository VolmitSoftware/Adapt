package art.arcane.adapt.command;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.gui.RosterGui;
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
import art.arcane.volmlib.util.director.runtime.DirectorParameterDescriptor;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeParameter;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandGolemTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID FIRST = UUID.fromString("3f2a91c0-0000-0000-0000-000000000001");
  private static final UUID SECOND = UUID.fromString("3f2b0000-0000-0000-0000-000000000002");

  private CommandSender admin;
  private Player target;
  private PlayerData data;
  private AdaptServer server;
  private GolemRegistry registry;
  private MockedStatic<J> scheduling;
  private MockedStatic<GolemancyRuntime> runtime;

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
    BukkitDirectorContext.touch(admin);
  }

  @AfterEach
  void clearContext() {
    BukkitDirectorContext.remove();
    runtime.close();
    scheduling.close();
  }

  @Test
  void theGolemGroupOpensTheRosterForPlayersAndKeysEveryOptionalAdminArgument() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new CommandAdapt(), DirectorEngineOptions.builder()
        .contexts(new DirectorContextRegistry())
        .dispatcher((DirectorExecutionMode mode, Runnable runnable) -> runnable.run())
        .legacyHandlers(DirectorHelpRenderTest.resolveHandlers(new StringBuilder()))
        .build());

    DirectorRuntimeNode golem = child(engine.getRoot(), "golem");

    DirectorRuntimeNode roster = child(golem, "roster");
    assertThat(roster.getDescriptor().getOrigin()).isEqualTo(DirectorOrigin.PLAYER);
    assertThat(roster.getParameters()).isEmpty();
    assertThat(parameters(child(golem, "list"))).containsExactly("player=");
    assertThat(parameters(child(golem, "release"))).containsExactly("id", "player=");
    assertThat(parameters(child(golem, "wake"))).containsExactly("player=");
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
