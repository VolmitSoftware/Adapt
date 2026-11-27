package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.content.golem.ChannelMask;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.content.golem.GolemRuneType;
import art.arcane.adapt.content.golem.gui.GolemGuiActions;
import art.arcane.adapt.content.golem.gui.MenuReplay;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.content.golem.gui.GolemElement;
import art.arcane.adapt.content.golem.gui.GolemTheme;
import art.arcane.adapt.content.golem.gui.GolemWindow;
import java.util.HashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BoneLatticeGuiTest extends AdaptTestBase {
  private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

  private MockedConstruction<GolemWindow> windows;
  private MockedStatic<GolemTheme> themes;
  private MockedStatic<AdaptApi> scheduling;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
  private PlayerRef player;
  private GolemBoneFrame frame;

  @BeforeEach
  void setUp() {
    player = mock(PlayerRef.class);
    lenient().when(player.uuid()).thenReturn(VIEWER);
    lenient().when(player.isOnline()).thenReturn(true);
    GolemRoster roster = new GolemRoster();
    roster.setServantChannel("RED");
    PlayerProfile adaptPlayer = mock(PlayerProfile.class);
    lenient().when(adaptPlayer.section(GolemRoster.section())).thenReturn(roster);
    frame = mock(GolemBoneFrame.class);
    lenient().when(frame.adaptPlayer(player)).thenReturn(Optional.of(adaptPlayer));
    lenient().when(frame.ownerLevel(VIEWER)).thenReturn(2);
    lenient().when(frame.latticeSlots(anyInt())).thenReturn(1);
    lenient().when(frame.mostLatticeSlots()).thenReturn(2);
    lenient().when(frame.slotUnlockLevel(anyInt())).thenReturn(4);
    lenient().when(frame.aspects()).thenReturn(Set.of(GolemRuneType.values()[0]));
    lenient().when(frame.runicAugments()).thenReturn(Optional.empty());
    scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    when(adaptScheduler.owns(any(PlayerRef.class))).thenReturn(true);
    themes = mockStatic(GolemTheme.class, invocation -> "element".equals(invocation.getMethod().getName())
        ? new GolemElement(invocation.getArgument(0)).setMaterial(invocation.getArgument(1))
        : null);
    windows = mockConstruction(GolemWindow.class);
  }

  @AfterEach
  void tearDown() {
    windows.close();
    themes.close();
    scheduling.close();
  }

  @Test
  void theLatticeKeepsItsGridAndMarksSlotsAndTheServantChannel() {
    BoneLatticeGui.open(frame, player, 0);

    PanelView view = MenuReplay.view(windows.constructed().getFirst());

    assertThat(view.layout()).isEqualTo(PanelLayout.GRID);
    assertThat(view.breadcrumb()).containsExactly(Text.key("adapt.golemancy.roster.title").resolve());
    PanelTile empty = MenuReplay.tile(view, -1, 0);
    assertThat(empty.role()).isEqualTo(TileRole.ACTION);
    assertThat(empty.icon().itemId()).isEqualTo(Id.minecraft("magenta_stained_glass_pane").toString());
    assertThat(MenuReplay.actions(empty)).containsOnlyKeys(ClickKind.LEFT);
    assertThat(MenuReplay.actions(empty).get(ClickKind.LEFT)).startsWith("Pick up a rune");
    assertThat(empty.lore().getFirst()).startsWith("§7Pick up a rune");
    PanelTile locked = MenuReplay.tile(view, 1, 0);
    assertThat(locked.role()).isEqualTo(TileRole.INFO);
    assertThat(locked.actions()).isEmpty();
    String selected = Text.key("adapt.golemancy.gui.channel_selected").resolve();
    assertThat(view.tiles().stream().filter(tile -> selected.equals(tile.value()))).hasSize(1);
    PanelTile none = MenuReplay.tile(view, 4, 0);
    assertThat(none.role()).isEqualTo(TileRole.ENTRY);
    assertThat(none.value()).isEmpty();
    assertThat(none.glint()).isFalse();
  }

  @Test
  void theChestTilesKeepTheirInstructionLore() {
    BoneLatticeGui.open(frame, player, 0);
    GolemWindow window = windows.constructed().getFirst();

    PanelView view = MenuReplay.view(window);

    PanelTile red = view.tiles().stream().filter(PanelTile::glint).findFirst().orElseThrow();
    assertThat(red.lore()).containsExactly("§a" + Text.key("adapt.golemancy.gui.channel_selected").resolve());
    assertThat(red.description()).isEmpty();
    Set<String> channels = new HashSet<>();
    channels.add(Text.key("adapt.golemancy.channel.none").resolve());
    for (DyeColor color : DyeColor.values()) {
      channels.add(GolemGuiActions.channelName(ChannelMask.of(color)).resolve());
    }
    assertThat(view.tiles().stream().filter(tile -> tile.role() == TileRole.ENTRY && channels.contains(tile.name()))).hasSize(17);
  }
}
