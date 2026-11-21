package art.arcane.adapt.client.server;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.client.link.ClientLink;
import art.arcane.adapt.client.protocol.ClientCapability;
import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ClientProtocolException;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.FrameCodec;
import art.arcane.adapt.client.protocol.FrameFragments;
import art.arcane.adapt.client.protocol.FrameLimits;
import art.arcane.adapt.client.protocol.ItemDescriptor;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.server.panel.PanelFrameHandler;
import art.arcane.adapt.client.view.CatalogViews;
import art.arcane.adapt.client.view.PreferenceKey;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.PreferenceValue;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.command.PlayerCommandSource;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.AdaptLanguageSwitcher;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.input.ReportedJumps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ClientUiServiceTest {
  @TempDir
  Path data;

  private final RecordingLink link = new RecordingLink();
  private final MenuHost menus = mock(MenuHost.class);
  private TestAdapt adapt;
  private TestPlatform platform;
  private FakePlayer player;
  private AdaptPlayer profile;
  private ClientUiService service;
  private SkillRegistry registry;
  private Adaptation<?> blink;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).menus(menus).build();
    player = platform.player("Viewer", 0.5D, 64D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillRift.class, SkillAgility.class));
    AdaptPorts.install(ClientLink.class, link);
    profile = adapt.join(player);
    service = adapt.core().getClientUi();
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    blink = ClientUiService.adaptation(registry, "rift-blink");
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void negotiationSendsAcceptThenCatalogThenPlayer() throws ClientProtocolException {
    hello(ClientProtocol.ALL);

    List<ServerFrame> frames = link.frames(player.uuid());
    assertThat(frames.get(0)).isInstanceOf(ServerFrame.Accept.class);
    assertThat(((ServerFrame.Catalog) frames.get(1)).catalog().revision()).isEqualTo(registry.getCatalogRevision());
    assertThat(frames.get(2)).isInstanceOf(ServerFrame.Player.class);
    assertThat(frames.subList(3, frames.size())).isNotEmpty().allMatch(ServerFrame.CustomItems.class::isInstance);
    assertThat(service.has(player.uuid(), ClientProtocol.SCREENS)).isTrue();
  }

  @Test
  void anItemsClientGetsTheViewerItemsAfterThePlayerViewEndedByAnEmptyFrame() throws ClientProtocolException {
    hello(ClientProtocol.ALL);

    List<ServerFrame.CustomItems> items = itemFrames();
    assertThat(items.get(items.size() - 1).items()).isEmpty();
    List<String> ids = items.stream().flatMap((ServerFrame.CustomItems frame) -> frame.items().stream())
        .map(ItemDescriptor::id).toList();
    assertThat(ids).startsWith(CustomItemCatalog.KNOWLEDGE_ORB, CustomItemCatalog.EXPERIENCE_ORB)
        .contains("adapt:rift-remote-access").doesNotHaveDuplicates();
  }

  @Test
  void aClientWithoutTheItemsCapabilityGetsNoItems() throws ClientProtocolException {
    hello(ClientProtocol.ALL & ~ClientProtocol.ITEMS);

    assertThat(link.frames(player.uuid())).hasSize(3).noneMatch(ServerFrame.CustomItems.class::isInstance);
  }

  @Test
  void unchangedItemsAreNotSentAgain() throws ClientProtocolException {
    hello(ClientProtocol.ALL);
    link.clear();

    line().setAdaptation(blink, 1);
    ClientUiService.changed(profile);
    platform.scheduler().advance(2);

    assertThat(link.frames(player.uuid())).isNotEmpty().noneMatch(ServerFrame.CustomItems.class::isInstance);
  }

  @Test
  void reloadedMessagesResendTheCatalogAndItemsInTheSameLocale() throws ClientProtocolException {
    hello(ClientProtocol.ALL);
    link.clear();

    assertThat(AdaptLanguage.reloadPassive()).isTrue();
    ClientUiService.changed(profile);
    platform.scheduler().advance(2);

    assertThat(only(ServerFrame.Catalog.class).catalog().locale()).isEqualTo(AdaptLanguage.locale(player.uuid()));
    assertThat(link.frames(player.uuid())).anyMatch(ServerFrame.CustomItems.class::isInstance);
  }

  @Test
  void unchangedMessagesDoNotResendTheCatalog() throws ClientProtocolException {
    hello(ClientProtocol.ALL);
    link.clear();

    ClientUiService.changed(profile);
    platform.scheduler().advance(2);

    assertThat(link.frames(player.uuid())).noneMatch(ServerFrame.Catalog.class::isInstance);
  }

  @Test
  void onlyAPanelClientOpensTheNativeLanguageEditor() throws ClientProtocolException {
    PlayerCommandSource source = new PlayerCommandSource(player);
    String[] edit = {"server", "edit"};
    assertThat(AdaptLanguageSwitcher.opensNativeEditor(source, edit)).isFalse();

    hello(ClientProtocol.ALL);

    assertThat(AdaptLanguageSwitcher.opensNativeEditor(source, edit)).isTrue();
    assertThat(AdaptLanguageSwitcher.opensNativeEditor(source, new String[] {"server", "edit", "de_DE"})).isTrue();
    assertThat(AdaptLanguageSwitcher.opensNativeEditor(source, new String[] {"server", "de_DE"})).isFalse();
    assertThat(AdaptLanguageSwitcher.opensNativeEditor(source, new String[] {"server"})).isFalse();
  }

  @Test
  void aClientWithoutPanelsKeepsThePlatformLanguageEditor() throws ClientProtocolException {
    hello(ClientProtocol.ALL & ~ClientProtocol.PANEL);

    assertThat(AdaptLanguageSwitcher.opensNativeEditor(new PlayerCommandSource(player), new String[] {"server", "edit"})).isFalse();
  }

  @Test
  void aPlayerWithoutTheClientGetsNothing() {
    ClientUiService.changed(profile);
    platform.scheduler().advance(2);

    assertThat(link.frames(player.uuid())).isEmpty();
    assertThat(service.has(player.uuid(), ClientProtocol.SCREENS)).isFalse();
  }

  @Test
  void learningAnAffordableAdaptationUpdatesThePlayer() throws ClientProtocolException {
    affordLearning();
    hello(ClientProtocol.ALL);
    link.clear();

    client(new ClientFrame.Learn("rift-blink", 1, registry.getCatalogRevision()));

    assertThat(line().getAdaptationLevel("rift-blink")).isEqualTo(1);
    ServerFrame.PlayerUpdate update = only(ServerFrame.PlayerUpdate.class);
    assertThat(update.delta().adaptationLevels()).containsEntry("rift-blink", 1);
    assertThat(only(ServerFrame.Feedback.class).kind()).isEqualTo(FeedbackKind.SUCCESS);
  }

  @Test
  void learningWithoutKnowledgeFailsAndChangesNothing() throws ClientProtocolException {
    hello(ClientProtocol.ALL);
    link.clear();

    client(new ClientFrame.Learn("rift-blink", 1, registry.getCatalogRevision()));

    assertThat(line().getAdaptationLevel("rift-blink")).isZero();
    assertThat(only(ServerFrame.Feedback.class).kind()).isEqualTo(FeedbackKind.FAILURE);
    assertThat(link.frames(player.uuid())).noneMatch(ServerFrame.PlayerUpdate.class::isInstance);
  }

  @Test
  void aStaleCatalogRevisionIsRefused() throws ClientProtocolException {
    affordLearning();
    hello(ClientProtocol.ALL);
    link.clear();

    client(new ClientFrame.Learn("rift-blink", 1, registry.getCatalogRevision() - 1));

    assertThat(line().getAdaptationLevel("rift-blink")).isZero();
    ServerFrame.Feedback feedback = only(ServerFrame.Feedback.class);
    assertThat(feedback.kind()).isEqualTo(FeedbackKind.FAILURE);
    assertThat(feedback.text()).isEqualTo(AdaptLanguage.text(GuiMessages.CLIENT_CATALOG_CHANGED));
  }

  @Test
  void anUnknownAdaptationOrLevelIsRefused() throws ClientProtocolException {
    affordLearning();
    hello(ClientProtocol.ALL);
    link.clear();

    client(new ClientFrame.Learn("rift-nothing", 1, registry.getCatalogRevision()));
    client(new ClientFrame.Learn("rift-blink", blink.getMaxLevel() + 1, registry.getCatalogRevision()));

    assertThat(line().getAdaptationLevel("rift-blink")).isZero();
    assertThat(link.frames(player.uuid())).hasSize(2).allSatisfy((ServerFrame frame) ->
        assertThat(((ServerFrame.Feedback) frame).kind()).isEqualTo(FeedbackKind.FAILURE));
  }

  @Test
  void unlearningRefundsAndReportsTheLowerLevel() throws ClientProtocolException {
    affordLearning();
    hello(ClientProtocol.ALL);
    client(new ClientFrame.Learn("rift-blink", 1, registry.getCatalogRevision()));
    link.clear();

    client(new ClientFrame.Unlearn("rift-blink", 0, registry.getCatalogRevision()));

    assertThat(line().getAdaptationLevel("rift-blink")).isZero();
    assertThat(only(ServerFrame.PlayerUpdate.class).delta().adaptationLevels()).containsEntry("rift-blink", 0);
    assertThat(only(ServerFrame.Feedback.class).kind()).isEqualTo(FeedbackKind.SUCCESS);
  }

  @Test
  void preferencesSetAndResetThroughThePolicy() throws ClientProtocolException {
    line().setAdaptation(blink, 1);
    hello(ClientProtocol.ALL);
    link.clear();
    PreferenceKey landing = new PreferenceKey(PreferenceOwner.ADAPTATION, "rift-blink", "landing");

    client(new ClientFrame.SetPreference(PreferenceOwner.ADAPTATION, "rift-blink", "landing", "NEAR"));

    assertThat(profile.getData().getPreferences().get("rift-blink", "landing")).isEqualTo("NEAR");
    assertThat(only(ServerFrame.PlayerUpdate.class).delta().preferences()).contains(new PreferenceValue(landing, "NEAR"));
    assertThat(only(ServerFrame.Feedback.class).kind()).isEqualTo(FeedbackKind.SUCCESS);
    link.clear();

    client(new ClientFrame.ResetPreference(PreferenceOwner.ADAPTATION, "rift-blink"));

    assertThat(profile.getData().getPreferences().get("rift-blink", "landing")).isNull();
    assertThat(only(ServerFrame.Feedback.class).kind()).isEqualTo(FeedbackKind.SUCCESS);
  }

  @Test
  void aLockedPreferenceIsDenied() throws ClientProtocolException {
    line().setAdaptation(blink, 1);
    AdaptationConfig config = (AdaptationConfig) blink.getConfig();
    Map<String, PreferencePolicy> previous = config.playerPreferences;
    PreferencePolicy locked = new PreferencePolicy();
    locked.playerEditable = false;
    locked.defaultValue = "SERVER";
    locked.allowedValues = List.of("SERVER", "NEAR", "NONE");
    config.playerPreferences = Map.of("landing", locked);
    try {
      hello(ClientProtocol.ALL);
      link.clear();

      client(new ClientFrame.SetPreference(PreferenceOwner.ADAPTATION, "rift-blink", "landing", "NEAR"));
      client(new ClientFrame.SetPreference(PreferenceOwner.ADAPTATION, "rift-blink", "landing", "BOGUS"));
      client(new ClientFrame.SetPreference(PreferenceOwner.SKILL, "rift", "landing", "ON"));

      assertThat(profile.getData().getPreferences().get("rift-blink", "landing")).isNull();
      assertThat(link.frames(player.uuid())).hasSize(3).allSatisfy((ServerFrame frame) ->
          assertThat(((ServerFrame.Feedback) frame).kind()).isEqualTo(FeedbackKind.FAILURE));
    } finally {
      config.playerPreferences = previous;
    }
  }

  @Test
  void manyChangesInOneTickSendOneUpdate() throws ClientProtocolException {
    hello(ClientProtocol.ALL);
    link.clear();

    line().giveKnowledge(3L);
    line().giveKnowledge(4L);
    line().setAdaptation(blink, 1);
    assertThat(link.frames(player.uuid())).isEmpty();
    platform.scheduler().advance(1);

    ServerFrame.PlayerUpdate update = only(ServerFrame.PlayerUpdate.class);
    assertThat(update.delta().adaptationLevels()).containsEntry("rift-blink", 1);
    assertThat(update.delta().skills()).anySatisfy((SkillLineView skill) ->
        assertThat(skill.knowledge()).isEqualTo(7L));
    platform.scheduler().advance(2);
    assertThat(link.frames(player.uuid())).hasSize(1);
  }

  @Test
  void screensRouteNativelyUntilTheClientWithdraws() throws ClientProtocolException {
    hello(ClientProtocol.ALL);
    link.clear();

    AdaptScreens.openSkills(player);
    AdaptScreens.openAdaptation(player, blink);

    assertThat(link.frames(player.uuid())).containsExactly(
        new ServerFrame.OpenRoute(new Route.Skills()),
        new ServerFrame.OpenRoute(new Route.Adaptation("rift-blink")));
    verify(menus, never()).open(eq(player), any(ChestMenu.class));

    hello(0);
    link.clear();
    AdaptScreens.openSkills(player);

    assertThat(link.frames(player.uuid())).isEmpty();
    verify(menus).open(eq(player), any(ChestMenu.class));
    assertThat(service.has(player.uuid(), ClientProtocol.SCREENS)).isFalse();
  }

  @Test
  void aClientWithoutScreensGetsTheChest() throws ClientProtocolException {
    hello(ClientProtocol.HUD);
    link.clear();
    clearInvocations(menus);

    AdaptScreens.openSkills(player);

    assertThat(link.frames(player.uuid())).isEmpty();
    verify(menus).open(eq(player), any(ChestMenu.class));
  }

  @Test
  void quittingForgetsTheClient() throws ClientProtocolException {
    hello(ClientProtocol.ALL);

    adapt.post(new PlayerQuitEvent(player));

    assertThat(service.capability(player.uuid())).isEmpty();
  }

  @Test
  void panelTrafficReachesTheInstalledHandler() throws ClientProtocolException {
    List<String> calls = new ArrayList<>();
    service.panels(new PanelFrameHandler() {
      @Override
      public void click(UUID viewer, ClientFrame.PanelClick click) {
        calls.add("click " + click.slot());
      }

      @Override
      public void input(UUID viewer, ClientFrame.PanelInput input) {
        calls.add("input " + input.text());
      }

      @Override
      public void closed(UUID viewer, ClientFrame.PanelClosed closed) {
        calls.add("closed " + closed.sessionId());
      }

      @Override
      public void quit(UUID viewer) {
        calls.add("quit");
      }

      @Override
      public void withdrawn(UUID viewer) {
        calls.add("withdrawn");
      }
    });
    hello(ClientProtocol.ALL);

    client(new ClientFrame.PanelClick(4, 1, 13, ClickKind.LEFT));
    client(new ClientFrame.PanelInput(4, 1, 13, "12"));
    client(new ClientFrame.PanelClosed(4));
    adapt.post(new PlayerQuitEvent(player));

    assertThat(calls).containsExactly("click 13", "input 12", "closed 4", "withdrawn", "quit");
  }

  private void affordLearning() {
    line().giveKnowledge(500L);
    profile.getData().giveMasterXp(XP.getXpForLevel(25));
  }

  @Test
  void jumpReportsAreServedOnlyWhereTheServerSeesNoJumpKey() throws ClientProtocolException {
    hello(ClientProtocol.ALL);

    ServerFrame.Accept accept = (ServerFrame.Accept) link.frames(player.uuid()).get(0);
    assertThat(accept.server().capabilityMask() & ClientProtocol.JUMPS).isZero();
    assertThat(ReportedJumps.reporting(player.uuid())).isFalse();
    platform.host().runGame(FakeGameContent.v1_20_1());
    assertThat(ClientUiService.servedCapabilities(platform.host())).isEqualTo(ClientProtocol.ALL);
  }

  @Test
  void everyCapabilityBitHasItsOwnName() {
    assertThat(ClientUiService.capabilityNames(ClientProtocol.ALL))
        .containsExactly("screens", "panel", "hud", "toasts", "items", "insight", "jumps");
    assertThat(ClientUiService.capabilityNames(ClientProtocol.INSIGHT | ClientProtocol.JUMPS))
        .containsExactly("insight", "jumps");
  }

  @Test
  void aJumpReportingClientFeedsReportedJumpsUntilItWithdraws() {
    service.negotiated(player.uuid(), new ClientCapability(1, ClientProtocol.JUMPS));
    assertThat(ReportedJumps.reporting(player.uuid())).isTrue();

    service.handle(player.uuid(), new ClientFrame.JumpKey(true));
    assertThat(ReportedJumps.down(player.uuid())).isTrue();
    service.handle(player.uuid(), new ClientFrame.JumpKey(false));
    assertThat(ReportedJumps.presses(player.uuid())).isEqualTo(1L);

    service.withdrawn(player.uuid());
    assertThat(ReportedJumps.reporting(player.uuid())).isFalse();
  }

  @Test
  void aFrameTooLargeForTheLinkWarnsOnceAndFallsBackWithoutRebuilding() throws ClientProtocolException {
    link.maxPayload = 64;
    try (MockedStatic<CatalogViews> builds = mockStatic(CatalogViews.class, CALLS_REAL_METHODS)) {
      hello(ClientProtocol.ALL);
      everySecond(5);

      builds.verify(() -> CatalogViews.build(any(), any()), times(1));
    }
    assertThat(sendWarnings()).hasSize(1);
    assertThat(service.has(player.uuid(), ClientProtocol.SCREENS)).isFalse();
    assertThat(link.frames(player.uuid())).hasSize(1).allMatch(ServerFrame.Accept.class::isInstance);
  }

  @Test
  void aClientWhoseChannelIsClosedCostsNoCatalogBuildUntilItOpens() throws ClientProtocolException {
    link.open = false;
    try (MockedStatic<CatalogViews> builds = mockStatic(CatalogViews.class, CALLS_REAL_METHODS)) {
      hello(ClientProtocol.ALL);
      everySecond(5);
      builds.verify(() -> CatalogViews.build(any(), any()), never());

      link.open = true;
      everySecond(1);
      builds.verify(() -> CatalogViews.build(any(), any()), times(1));
    }
    assertThat(sendWarnings()).isEmpty();
    assertThat(link.frames(player.uuid())).anyMatch(ServerFrame.Catalog.class::isInstance);
  }

  @Test
  void aTransportThrowingMidFragmentTrainWarnsOnceAndFallsBackWithoutRebuilding() throws ClientProtocolException {
    link.maxPayload = 4096;
    link.failSecondFragment = true;
    try (MockedStatic<CatalogViews> builds = mockStatic(CatalogViews.class, CALLS_REAL_METHODS)) {
      hello(ClientProtocol.ALL);
      everySecond(5);

      builds.verify(() -> CatalogViews.build(any(), any()), times(1));
    }
    assertThat(link.failures).isEqualTo(1);
    assertThat(sendWarnings()).hasSize(1).allMatch((String warning) -> warning.contains("channel gone"));
    assertThat(platform.log().errors()).isEmpty();
    assertThat(service.has(player.uuid(), ClientProtocol.HUD)).isFalse();
    assertThat(ClientUiService.activeSessions().send(player.uuid(), new ServerFrame.HudClearAll())).isFalse();
  }

  private void everySecond(int seconds) {
    for (int second = 0; second < seconds; second++) {
      ClientUiService.changed(profile);
      platform.scheduler().advance(20);
    }
  }

  private List<String> sendWarnings() {
    return platform.log().warnings().stream().filter((String warning) -> warning.contains("Could not send")).toList();
  }

  private PlayerSkillLine line() {
    return profile.getData().getSkillLine("rift");
  }

  private void hello(int mask) throws ClientProtocolException {
    service.receive(player.uuid(), FrameCodec.encodeClient(new ClientFrame.Hello(mask == 0 ? 0 : 1, mask)));
  }

  private void client(ClientFrame frame) throws ClientProtocolException {
    service.receive(player.uuid(), FrameCodec.encodeClient(frame));
  }

  private List<ServerFrame.CustomItems> itemFrames() {
    List<ServerFrame.CustomItems> items = new ArrayList<>();
    for (ServerFrame frame : link.frames(player.uuid())) {
      if (frame instanceof ServerFrame.CustomItems chunk) {
        items.add(chunk);
      }
    }
    return items;
  }

  private <T extends ServerFrame> T only(Class<T> type) {
    List<T> matches = new ArrayList<>();
    for (ServerFrame frame : link.frames(player.uuid())) {
      if (type.isInstance(frame)) {
        matches.add(type.cast(frame));
      }
    }
    assertThat(matches).describedAs(type.getSimpleName()).hasSize(1);
    return matches.get(0);
  }

  private static final class RecordingLink implements ClientLink {
    private final Map<UUID, List<ServerFrame>> frames = new ConcurrentHashMap<>();
    private final FrameFragments.Assembler assembler = new FrameFragments.Assembler(FrameLimits.MAX_S2C_BYTES);
    private volatile boolean open = true;
    private volatile int maxPayload = FrameLimits.MAX_S2C_BYTES;
    private volatile boolean failSecondFragment;
    private int failures;

    @Override
    public boolean open(UUID playerId) {
      return open;
    }

    @Override
    public int maxPayloadBytes() {
      return maxPayload;
    }

    @Override
    public void send(UUID playerId, byte[] frame) {
      if (failSecondFragment && frame[0] == (byte) FrameFragments.OPCODE && frame[2] == 1) {
        failures++;
        throw new IllegalStateException("channel gone");
      }
      try {
        byte[] whole = assembler.accept(frame);
        if (whole != null) {
          frames.computeIfAbsent(playerId, (UUID ignored) -> new ArrayList<>()).add(FrameCodec.decodeServer(whole));
        }
      } catch (ClientProtocolException exception) {
        throw new AssertionError(exception);
      }
    }

    List<ServerFrame> frames(UUID playerId) {
      return List.copyOf(frames.getOrDefault(playerId, List.of()));
    }

    void clear() {
      frames.clear();
    }
  }
}
