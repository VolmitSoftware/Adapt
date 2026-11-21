package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.gui.MenuReplay;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

class SorterFetchGuiPanelTest extends AdaptTestBase {
  private static final NodeKey KEY = new NodeKey(UUID.fromString("00000000-0000-0000-0000-00000000c002"), 3, 64, 3, BlockFace.UP);

  private MockedConstruction<GuiWindow> windows;
  private MockedStatic<GuiTheme> themes;
  private PlayerRef player;
  private SorterFetchGui gui;

  @BeforeEach
  void setUp() {
    player = mock(PlayerRef.class);
    lenient().when(player.uuid()).thenReturn(UUID.fromString("00000000-0000-0000-0000-00000000c003"));
    lenient().when(player.isOnline()).thenReturn(true);
    themes = mockStatic(GuiTheme.class, invocation -> "element".equals(invocation.getMethod().getName())
        ? new GuiElement(invocation.getArgument(0)).setMaterial(invocation.getArgument(1))
        : null);
    windows = mockConstruction(GuiWindow.class);
    gui = new SorterFetchGui(mock(GolemSorterCore.class), mock(SorterNetwork.class), mock(SorterFetches.class));
  }

  @AfterEach
  void tearDown() {
    windows.close();
    themes.close();
  }

  @Test
  void theFetchListIsAPagedListOfItemsWithTheirStock() throws ReflectiveOperationException {
    List<SorterSurvey.Available> listing = new ArrayList<>();
    for (int index = 0; index < 50; index++) {
      listing.add(new SorterSurvey.Available(new FakeItem(Id.minecraft("cobblestone"), 1), 100 + index));
    }

    render(listing, 1, 16);
    PanelView view = MenuReplay.view(windows.constructed().getFirst());

    assertThat(view.layout()).isEqualTo(PanelLayout.LIST);
    assertThat(view.paging().page()).isEqualTo(1);
    assertThat(view.paging().pages()).isEqualTo(2);
    assertThat(view.paging().total()).isEqualTo(50);
    PanelTile first = MenuReplay.tile(view, -4, 0);
    assertThat(first.role()).isEqualTo(TileRole.ENTRY);
    assertThat(first.value()).isEqualTo("145");
    assertThat(first.amount()).isEqualTo(64);
    String request = AdaptLanguage.text(GolemancyMessages.SORTER_CORE_FETCH_REQUEST, trusted("amount", 16));
    assertThat(first.lore()).containsExactly("§7" + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_FETCH_AVAILABLE,
        trusted("count", 145)), "§a" + request);
    assertThat(first.description()).containsExactly(first.lore().getFirst());
    assertThat(MenuReplay.actions(first)).containsExactly(Map.entry(ClickKind.LEFT, request));
    List<PanelTile> selected = view.tiles().stream()
        .filter(tile -> AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECTED).equals(tile.value())).toList();
    assertThat(selected).hasSize(1);
    assertThat(selected.getFirst().amount()).isEqualTo(16);
    assertThat(selected.getFirst().glint()).isTrue();
  }

  @Test
  void anEmptyNetworkShowsAPlainNotice() throws ReflectiveOperationException {
    render(List.of(), 0, 64);
    PanelView view = MenuReplay.view(windows.constructed().getFirst());

    PanelTile empty = MenuReplay.tile(view, 0, 0);
    assertThat(empty.role()).isEqualTo(TileRole.INFO);
    assertThat(empty.value()).isEmpty();
    assertThat(view.paging().total()).isZero();
  }

  private void render(List<SorterSurvey.Available> listing, int page, int amount) throws ReflectiveOperationException {
    Method render = SorterFetchGui.class.getDeclaredMethod("render", PlayerRef.class, NodeKey.class, List.class, int.class, int.class);
    render.setAccessible(true);
    render.invoke(gui, player, KEY, listing, page, amount);
  }
}
