package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.reveal.RevealService;
import art.arcane.adapt.api.insight.EntityInsight;
import art.arcane.adapt.api.insight.GlossInsightPort;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.server.PluginStateEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemNamesTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

  private final GlossInsightPort gloss = mock(GlossInsightPort.class);
  private final ItemRef emptyItem = FakeItem.empty();
  private final EntityInsight.Display insight = mock(EntityInsight.Display.class);
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<Tasks> scheduling;
  private MockedStatic<AdaptHud> hud;
  private GolemRegistry registry;
  private RevealService reveal;
  private Collection<PlayerRef> online;
  private GolemNames names;

  @BeforeEach
  void setUp() {
    online = new ArrayList<>();
    PlatformHost host = AdaptCore.platform().host();
    lenient().when(host.onlinePlayers()).thenAnswer(invocation -> online);
    ItemOps items = mock(ItemOps.class);
    lenient().when(items.empty()).thenReturn(emptyItem);
    lenient().when(host.items()).thenReturn(items);
    registry = mock(GolemRegistry.class);
    reveal = mock(RevealService.class);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    runtime.when(GolemancyRuntime::reveal).thenReturn(Optional.of(reveal));
    runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.empty());
    scheduling = mockStatic(Tasks.class);
    scheduling.when(() -> Tasks.isOwnedByCurrentRegion(any(EntityRef.class))).thenReturn(true);
    scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    hud = mockStatic(AdaptHud.class);
    names = new GolemNames(gloss);
  }

  @AfterEach
  void tearDown() {
    EntityInsight.detach(insight);
    hud.close();
    scheduling.close();
    runtime.close();
  }

  @Test
  void glossWithItsOverlayRunningSelectsGloss() {
    PlayerRef probe = player(OWNER);
    online.add(probe);
    when(gloss.available()).thenReturn(true);
    when(gloss.overlayActive(probe)).thenReturn(true);

    names.start();

    assertThat(names.mode()).isEqualTo(GolemNameMode.GLOSS);
    InOrder order = inOrder(registry, reveal);
    order.verify(registry).showNames();
    order.verify(reveal).refreshViewers();
  }

  @Test
  void glossWithItsOverlayOffStaysVanilla() {
    PlayerRef probe = player(OWNER);
    online.add(probe);
    when(gloss.available()).thenReturn(true);
    when(gloss.overlayActive(probe)).thenReturn(false);

    names.start();

    assertThat(names.mode()).isEqualTo(GolemNameMode.VANILLA);
    verify(reveal, never()).refreshViewers();
    verify(registry, never()).showNames();
  }

  @Test
  void anInsightReadoutWithoutTheGlossOverlayLeavesTheNameVanilla() {
    PlayerRef probe = player(OWNER);
    online.add(probe);
    when(gloss.available()).thenReturn(true);
    when(gloss.overlayActive(probe)).thenReturn(false);
    EntityInsight.attach(insight);

    names.start();

    assertThat(names.mode()).isEqualTo(GolemNameMode.VANILLA);
  }

  @Test
  void withoutGlossTheNameStaysVanillaAndNothingIsRefreshed() {
    names.start();

    assertThat(names.mode()).isEqualTo(GolemNameMode.VANILLA);
    verify(reveal, never()).refreshViewers();
    verify(registry, never()).showNames();
    verify(gloss, never()).overlayActive(any());
  }

  @Test
  void withNobodyOnlineGlossCountsAsActiveUntilTheFirstPlayerCanProbeIt() {
    when(gloss.available()).thenReturn(true);
    names.start();
    assertThat(names.mode()).isEqualTo(GolemNameMode.GLOSS);

    PlayerRef first = player(OWNER);
    when(gloss.overlayActive(first)).thenReturn(false);
    names.on(new PlayerJoinEvent(first));
    assertThat(names.mode()).isEqualTo(GolemNameMode.VANILLA);
    verify(registry, times(2)).showNames();

    PlayerRef second = player(STRANGER);
    names.on(new PlayerJoinEvent(second));
    verify(gloss, never()).overlayActive(second);
  }

  @Test
  void glossEnablingAndDisablingReevaluatesTheMode() {
    PlayerRef probe = player(OWNER);
    online.add(probe);
    names.start();
    assertThat(names.mode()).isEqualTo(GolemNameMode.VANILLA);

    when(gloss.available()).thenReturn(true);
    when(gloss.overlayActive(probe)).thenReturn(true);
    names.on(new PluginStateEvent("Other", true));
    assertThat(names.mode()).isEqualTo(GolemNameMode.VANILLA);
    names.on(new PluginStateEvent("Gloss", true));
    assertThat(names.mode()).isEqualTo(GolemNameMode.GLOSS);
    verify(gloss).refresh();
    verify(registry).showNames();

    names.on(new PluginStateEvent("Gloss", false));
    assertThat(names.mode()).isEqualTo(GolemNameMode.VANILLA);
    verify(registry, times(2)).showNames();
  }

  @Test
  void theOwnersNameTagRenamesTheGolemAndIsUsedUp() {
    PlayerRef player = player(OWNER);
    GolemHandle handle = boundGolem();
    ItemRef tag = nameTag(player, Hand.MAIN_HAND, "Clank", 3);
    PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, handle.entity(), Hand.MAIN_HAND, null);

    names.on(event);

    assertThat(event.isCancelled()).isTrue();
    verify(registry).rename(handle, GolemSnapshot.textName("Clank"));
    verify(tag).setAmount(2);
    hud.verify(() -> AdaptHud.actionBar(eq(player), contains("Clank")));
  }

  @Test
  void inGlossModeTheOwnersNameTagStillRenamesTheStoredNameAndIsUsedUp() {
    PlayerRef probe = player(OWNER);
    online.add(probe);
    when(gloss.available()).thenReturn(true);
    when(gloss.overlayActive(probe)).thenReturn(true);
    names.start();
    assertThat(names.mode()).isEqualTo(GolemNameMode.GLOSS);
    PlayerRef player = player(OWNER);
    GolemHandle handle = boundGolem();
    ItemRef tag = nameTag(player, Hand.MAIN_HAND, "Clank", 3);
    PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, handle.entity(), Hand.MAIN_HAND, null);

    names.on(event);

    assertThat(event.isCancelled()).isTrue();
    verify(registry).rename(handle, GolemSnapshot.textName("Clank"));
    verify(tag).setAmount(2);
    hud.verify(() -> AdaptHud.actionBar(eq(player), contains("Clank")));
  }

  @Test
  void inGlossModeAStrangersNameTagIsStillRefusedAndKept() {
    PlayerRef probe = player(OWNER);
    online.add(probe);
    when(gloss.available()).thenReturn(true);
    when(gloss.overlayActive(probe)).thenReturn(true);
    names.start();
    PlayerRef stranger = player(STRANGER);
    GolemHandle handle = boundGolem();
    ItemRef tag = nameTag(stranger, Hand.MAIN_HAND, "Mine Now", 1);
    PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(stranger, handle.entity(), Hand.MAIN_HAND, null);

    names.on(event);

    assertThat(event.isCancelled()).isTrue();
    verify(registry, never()).rename(any(), any());
    verify(tag, never()).setAmount(anyInt());
    verify(stranger.inventory(), never()).setHand(any(Hand.class), any());
  }

  @Test
  void theLastNameTagInTheOffHandIsRemovedAndCreativeKeepsItsTag() {
    PlayerRef player = player(OWNER);
    GolemHandle handle = boundGolem();
    nameTag(player, Hand.OFF_HAND, "Clank", 1);

    names.on(new PlayerInteractEntityEvent(player, handle.entity(), Hand.OFF_HAND, null));

    verify(player.inventory()).setHand(Hand.OFF_HAND, emptyItem);

    PlayerRef creative = player(OWNER);
    when(creative.gameMode()).thenReturn(GameMode.CREATIVE);
    ItemRef kept = nameTag(creative, Hand.MAIN_HAND, "Bolt", 1);
    names.on(new PlayerInteractEntityEvent(creative, handle.entity(), Hand.MAIN_HAND, null));
    verify(registry).rename(handle, GolemSnapshot.textName("Bolt"));
    verify(kept, never()).setAmount(anyInt());
    verify(creative.inventory(), never()).setHand(any(Hand.class), any());
  }

  @Test
  void aStrangersNameTagIsRefusedAndKept() {
    PlayerRef stranger = player(STRANGER);
    GolemHandle handle = boundGolem();
    ItemRef tag = nameTag(stranger, Hand.MAIN_HAND, "Mine Now", 1);
    PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(stranger, handle.entity(), Hand.MAIN_HAND, null);

    names.on(event);

    assertThat(event.isCancelled()).isTrue();
    verify(registry, never()).rename(any(), any());
    verify(tag, never()).setAmount(anyInt());
    verify(stranger.inventory(), never()).setHand(any(Hand.class), any());
    hud.verify(() -> AdaptHud.actionBar(eq(stranger), contains("owner")));
  }

  @Test
  void blankTagsOtherItemsAndUnboundMobsAreLeftToVanilla() {
    PlayerRef player = player(OWNER);
    GolemHandle handle = boundGolem();
    ItemRef blank = nameTag(player, Hand.MAIN_HAND, null, 1);
    PlayerInteractEntityEvent blankEvent = new PlayerInteractEntityEvent(player, handle.entity(), Hand.MAIN_HAND, null);
    names.on(blankEvent);
    assertThat(blankEvent.isCancelled()).isFalse();

    when(blank.type()).thenReturn(Id.minecraft("iron_ingot"));
    PlayerInteractEntityEvent ingot = new PlayerInteractEntityEvent(player, handle.entity(), Hand.MAIN_HAND, null);
    names.on(ingot);
    assertThat(ingot.isCancelled()).isFalse();

    nameTag(player, Hand.MAIN_HAND, "Clank", 1);
    PlayerInteractEntityEvent villager = new PlayerInteractEntityEvent(player, mock(LivingRef.class), Hand.MAIN_HAND, null);
    names.on(villager);
    assertThat(villager.isCancelled()).isFalse();
    verify(registry, never()).rename(any(), any());
    hud.verify(() -> AdaptHud.actionBar(any(PlayerRef.class), anyString()), never());
  }

  private GolemHandle boundGolem() {
    MobRef golem = mock(MobRef.class);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.owner()).thenReturn(OWNER);
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
    return handle;
  }

  private static PlayerRef player(UUID id) {
    PlayerRef player = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    lenient().when(player.uuid()).thenReturn(id);
    lenient().when(player.inventory()).thenReturn(inventory);
    lenient().when(player.gameMode()).thenReturn(GameMode.SURVIVAL);
    lenient().when(player.isOnline()).thenReturn(true);
    return player;
  }

  private static ItemRef nameTag(PlayerRef player, Hand hand, String name, int amount) {
    ItemRef tag = mock(ItemRef.class);
    lenient().when(tag.type()).thenReturn(Id.minecraft("name_tag"));
    lenient().when(tag.amount()).thenReturn(amount);
    lenient().when(tag.hasDisplayName()).thenReturn(name != null);
    lenient().when(tag.displayNameJson()).thenReturn(name == null ? null : GolemSnapshot.textName(name));
    PlayerInventoryRef inventory = player.inventory();
    lenient().when(inventory.hand(Hand.MAIN_HAND)).thenReturn(hand == Hand.MAIN_HAND ? tag : mock(ItemRef.class));
    lenient().when(inventory.hand(Hand.OFF_HAND)).thenReturn(hand == Hand.OFF_HAND ? tag : mock(ItemRef.class));
    return tag;
  }
}
