package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.node.NodeItemTags;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeTagLookup;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerChatEvent;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeTagPromptTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final NodeKey KEY = new NodeKey(UUID.fromString("00000000-0000-0000-0000-0000000000c1"), 3, 64, 3, BlockFace.UP);
  private static final NodeTagLookup TAGS = new NodeTagLookup() {
    @Override
    public boolean exists(String tag) {
      return Set.of("#minecraft:logs").contains(tag);
    }

    @Override
    public boolean tagged(String tag, Id material) {
      return false;
    }
  };

  private final List<Runnable> delayed = new ArrayList<>();
  private final AtomicReference<UnaryOperator<NodeRecord>> applied = new AtomicReference<>();
  private MockedStatic<AdaptApi> scheduling;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
  private MockedStatic<NodeGui> gui;
  private MockedStatic<GolemGuiSupport> messages;
  private PlayerRef player;
  private int level = 5;
  private final NodeAreaSelector areas = new NodeAreaSelector();
  private NodeTagPrompt prompt;

  @BeforeEach
  void setUp() {
    player = mock(PlayerRef.class);
    lenient().when(player.uuid()).thenReturn(OWNER);
    lenient().when(player.isOnline()).thenReturn(true);
    scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class), anyLong())).thenAnswer(invocation -> {
      delayed.add(invocation.getArgument(1));
      return true;
    });
    gui = mockStatic(NodeGui.class);
    gui.when(() -> NodeGui.level(player)).thenAnswer(invocation -> level);
    gui.when(() -> NodeGui.filterSlots(anyInt())).thenReturn(3);
    gui.when(() -> NodeGui.openAfter(eq(player), eq(KEY), any(NodeTagPrompt.class), any(NodeAreaSelector.class), any())).thenAnswer(invocation -> {
      applied.set(invocation.getArgument(4));
      return null;
    });
    messages = mockStatic(GolemGuiSupport.class);
    prompt = new NodeTagPrompt(TAGS, areas);
  }

  @AfterEach
  void tearDown() {
    messages.close();
    gui.close();
    scheduling.close();
  }

  @Test
  void aValidTagIsSwallowedFromChatAndAddedToTheFilter() {
    prompt.begin(player, KEY);
    PlayerChatEvent chat = chat(" #Logs ");

    prompt.on(chat);

    verify(chat).setCancelled(true);
    verify(player).closeInventory();
    assertThat(applied.get()).isNotNull();
    NodeRecord record = applied.get().apply(NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).build());
    assertThat(record.filterTags()).containsExactly("#minecraft:logs");
    PlayerChatEvent after = chat("hello");
    prompt.on(after);
    verify(after, never()).setCancelled(true);
  }

  @Test
  void anUnknownTagKeepsThePromptOpenForAnotherTry() {
    prompt.begin(player, KEY);

    prompt.on(chat("#not_a_tag"));
    prompt.on(chat("logs"));

    assertThat(applied.get()).isNotNull();
  }

  @Test
  void cancelReopensTheMenuWithoutChanges() {
    prompt.begin(player, KEY);

    prompt.on(chat("CANCEL"));

    gui.verify(() -> NodeGui.open(player, KEY, prompt, areas));
    assertThat(applied.get()).isNull();
  }

  @Test
  void aPlayerBelowLevelFiveOnlyGetsTheMenuBack() {
    level = 4;
    prompt.begin(player, KEY);

    prompt.on(chat("#logs"));

    gui.verify(() -> NodeGui.open(player, KEY, prompt, areas));
    assertThat(applied.get()).isNull();
  }

  @Test
  void thePromptTimesOutAndLetsChatThrough() {
    prompt.begin(player, KEY);
    assertThat(delayed).hasSize(1);

    delayed.getFirst().run();
    PlayerChatEvent chat = chat("#logs");
    prompt.on(chat);

    verify(chat, never()).setCancelled(true);
    messages.verify(() -> GolemGuiSupport.message(eq(player), anyString()), times(3));
  }

  @Test
  void aStaleTimeoutNeverEndsANewerPrompt() {
    prompt.begin(player, KEY);
    prompt.begin(player, KEY);

    delayed.getFirst().run();
    PlayerChatEvent chat = chat("#logs");
    prompt.on(chat);

    verify(chat).setCancelled(true);
    assertThat(applied.get()).isNotNull();
  }

  @Test
  void unknownTagsTypedInChatAreNeverCached() {
    NodeItemTags.shared().clear();
    Id unknown = Id.minecraft("not_a_tag");
    RegistryLookup registries = mock(RegistryLookup.class);
    lenient().when(AdaptCore.platform().host().registries()).thenReturn(registries);
    try {
      NodeTagPrompt shared = new NodeTagPrompt(NodeItemTags.shared(), areas);
      shared.begin(player, KEY);

      shared.on(chat("#not_a_tag"));
      shared.on(chat("#not_a_tag"));

      verify(registries, times(2)).tagValues(RegistryKind.ITEM, unknown);
      assertThat(applied.get()).isNull();
    } finally {
      NodeItemTags.shared().clear();
    }
  }

  @Test
  void aTagTypedIntoTheMenuIsAddedWithoutAChatPrompt() {
    prompt.input(KEY).input(new MenuClick(player, null, 12, ClickKind.OTHER), " #Logs ");

    assertThat(applied.get()).isNotNull();
    NodeRecord record = applied.get().apply(NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).build());
    assertThat(record.filterTags()).containsExactly("#minecraft:logs");
    verify(player, never()).closeInventory();
    messages.verify(() -> GolemGuiSupport.message(eq(player), anyString()), never());
    PlayerChatEvent chat = chat("hello");
    prompt.on(chat);
    verify(chat, never()).setCancelled(true);
  }

  @Test
  void anUnknownTagTypedIntoTheMenuIsRefusedAndTheMenuStaysOpen() {
    prompt.input(KEY).input(new MenuClick(player, null, 12, ClickKind.OTHER), "#not_a_tag");

    assertThat(applied.get()).isNull();
    gui.verify(() -> NodeGui.open(any(PlayerRef.class), any(NodeKey.class), any(NodeTagPrompt.class), any(NodeAreaSelector.class)), never());
    verify(player, never()).closeInventory();
    messages.verify(() -> GolemGuiSupport.message(eq(player), contains("#not_a_tag is not an item tag")));
    messages.verify(() -> GolemGuiSupport.message(eq(player), anyString()), times(1));
    PlayerChatEvent chat = chat("#logs");
    prompt.on(chat);
    verify(chat, never()).setCancelled(true);
  }

  @Test
  void aTagTypedIntoTheMenuBelowLevelFiveOnlyRedrawsTheMenu() {
    level = 4;

    prompt.input(KEY).input(new MenuClick(player, null, 12, ClickKind.OTHER), "#logs");

    gui.verify(() -> NodeGui.open(player, KEY, prompt, areas));
    assertThat(applied.get()).isNull();
  }

  private PlayerChatEvent chat(String message) {
    return spy(new PlayerChatEvent(player, message, true));
  }
}
