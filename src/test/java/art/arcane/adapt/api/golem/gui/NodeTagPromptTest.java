package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeItemTags;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NodeTagPromptTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final NodeKey KEY = new NodeKey(UUID.fromString("00000000-0000-0000-0000-0000000000c1"), 3, 64, 3, BlockFace.UP);
  private static final NodeTagLookup TAGS = new NodeTagLookup() {
    @Override
    public boolean exists(String tag) {
      return Set.of("#minecraft:logs").contains(tag);
    }

    @Override
    public boolean tagged(String tag, Material material) {
      return false;
    }
  };

  private final List<Runnable> delayed = new ArrayList<>();
  private final AtomicReference<UnaryOperator<NodeRecord>> applied = new AtomicReference<>();
  private MockedStatic<J> scheduling;
  private MockedStatic<NodeGui> gui;
  private MockedStatic<Adapt> messages;
  private Player player;
  private int level = 5;
  private NodeTagPrompt prompt;

  @BeforeEach
  void setUp() {
    player = mock(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(OWNER);
    lenient().when(player.isOnline()).thenReturn(true);
    lenient().when(player.spigot()).thenReturn(mock(Player.Spigot.class));
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), anyInt())).thenAnswer(invocation -> {
      delayed.add(invocation.getArgument(1));
      return true;
    });
    gui = mockStatic(NodeGui.class);
    gui.when(() -> NodeGui.level(player)).thenAnswer(invocation -> level);
    gui.when(() -> NodeGui.filterSlots(anyInt())).thenReturn(3);
    gui.when(() -> NodeGui.openAfter(eq(player), eq(KEY), any(NodeTagPrompt.class), any())).thenAnswer(invocation -> {
      applied.set(invocation.getArgument(3));
      return null;
    });
    messages = mockStatic(Adapt.class);
    prompt = new NodeTagPrompt(TAGS);
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
    AsyncPlayerChatEvent chat = chat(" #Logs ");

    prompt.on(chat);

    verify(chat).setCancelled(true);
    verify(player).closeInventory();
    assertThat(applied.get()).isNotNull();
    NodeRecord record = applied.get().apply(NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).build());
    assertThat(record.filterTags()).containsExactly("#minecraft:logs");
    AsyncPlayerChatEvent after = chat("hello");
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

    gui.verify(() -> NodeGui.open(player, KEY, prompt));
    assertThat(applied.get()).isNull();
  }

  @Test
  void aPlayerBelowLevelFiveOnlyGetsTheMenuBack() {
    level = 4;
    prompt.begin(player, KEY);

    prompt.on(chat("#logs"));

    gui.verify(() -> NodeGui.open(player, KEY, prompt));
    assertThat(applied.get()).isNull();
  }

  @Test
  void thePromptTimesOutAndLetsChatThrough() {
    prompt.begin(player, KEY);
    assertThat(delayed).hasSize(1);

    delayed.getFirst().run();
    AsyncPlayerChatEvent chat = chat("#logs");
    prompt.on(chat);

    verify(chat, never()).setCancelled(true);
    messages.verify(() -> Adapt.messagePlayer(eq(player), anyString()), times(3));
  }

  @Test
  void aStaleTimeoutNeverEndsANewerPrompt() {
    prompt.begin(player, KEY);
    prompt.begin(player, KEY);

    delayed.getFirst().run();
    AsyncPlayerChatEvent chat = chat("#logs");
    prompt.on(chat);

    verify(chat).setCancelled(true);
    assertThat(applied.get()).isNotNull();
  }

  @Test
  void unknownTagsTypedInChatAreNeverCached() {
    NodeItemTags.shared().clear();
    NamespacedKey unknown = NamespacedKey.minecraft("not_a_tag");
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      NodeTagPrompt shared = new NodeTagPrompt(NodeItemTags.shared());
      shared.begin(player, KEY);

      shared.on(chat("#not_a_tag"));
      shared.on(chat("#not_a_tag"));

      bukkit.verify(() -> Bukkit.getTag(eq("items"), eq(unknown), eq(Material.class)), times(2));
      assertThat(applied.get()).isNull();
    } finally {
      NodeItemTags.shared().clear();
    }
  }

  private AsyncPlayerChatEvent chat(String message) {
    AsyncPlayerChatEvent event = mock(AsyncPlayerChatEvent.class);
    lenient().when(event.getPlayer()).thenReturn(player);
    lenient().when(event.getMessage()).thenReturn(message);
    return event;
  }
}
