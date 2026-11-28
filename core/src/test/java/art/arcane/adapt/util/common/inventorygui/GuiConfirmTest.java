package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.api.internal.HostRecipes;
import art.arcane.adapt.api.internal.HostPermissions;
import art.arcane.adapt.api.internal.HostMenus;
import art.arcane.adapt.api.internal.HostPlatform;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.client.server.panel.PanelViews;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.viewer.ViewerHost;
import art.arcane.adapt.testkit.ManualScheduler;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuiConfirmTest {
  private MockedStatic<CustomModel> models;
  private MockedStatic<Tasks> scheduler;
  private MockedStatic<AdaptLanguage> language;
  private HostMenus menus;
  private PlayerRef player;

  @BeforeEach
  void prepare() {
    models = mockStatic(CustomModel.class, invocation -> {
      Id material = invocation.getArgument(0);
      CustomModel model = mock(CustomModel.class);
      when(model.material()).thenReturn(material);
      return model;
    });
    scheduler = SchedulerForwarding.mockTasks();
    scheduler.when(Tasks::isPrimaryThread).thenReturn(true);
    language = mockStatic(AdaptLanguage.class,
        invocation -> invocation.getMethod().getReturnType() == String.class ? "Text" : null);
    AdaptCore core = TestCores.install();
    menus = mock(HostMenus.class);
    TestCores.setField(core, "platform", new HostPlatform(mock(PlatformHost.class), new ManualScheduler(),
        mock(FxHost.class), mock(ViewerHost.class), menus, mock(HostPermissions.class), mock(ProtectionHost.class),
        mock(HostRecipes.class), mock(AdvancementHost.class)));
    player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
  }

  @AfterEach
  void restore() {
    models.close();
    scheduler.close();
    language.close();
    TestCores.clear();
  }

  @Test
  void theDialogKeepsItsChestAndReadsAsMessageThenActions() {
    GuiConfirm.open(player, "Delete", "Gone for good", () -> {
    }, () -> {
    });
    ArgumentCaptor<ChestMenu> opened = ArgumentCaptor.forClass(ChestMenu.class);
    verify(menus).open(eq(player), opened.capture());
    ChestMenu menu = opened.getValue();

    assertThat(menu.rows()).isEqualTo(3);
    assertThat(menu.title()).isEqualTo("Text");
    assertThat(menu.buttons()).hasSize(20);
    MenuButton message = menu.button(4);
    assertThat(message.icon().type()).isEqualTo(Id.minecraft("paper"));
    assertThat(message.icon().name()).isEqualTo(C.WHITE + "Delete");
    assertThat(message.icon().lore()).containsExactly(C.GRAY + "Gone for good");
    assertThat(message.clickable()).isFalse();
    assertThat(menu.button(11).icon().type()).isEqualTo(Id.minecraft("lime_stained_glass_pane"));
    assertThat(menu.button(11).clickable()).isTrue();
    assertThat(menu.button(15).icon().type()).isEqualTo(Id.minecraft("red_stained_glass_pane"));
    assertThat(menu.button(15).clickable()).isTrue();
    for (int slot : new int[]{0, 8, 18, 22, 26}) {
      assertThat(menu.button(slot).icon().type()).isEqualTo(GuiFrame.BAR);
      assertThat(menu.button(slot).clickable()).isFalse();
    }
    for (int slot : new int[]{9, 10, 12, 13, 14, 16, 17}) {
      assertThat(menu.button(slot)).isNull();
    }

    PanelView view = PanelViews.view(1, 0, menu);
    assertThat(view.layout()).isEqualTo(PanelLayout.LIST);
    assertThat(tile(view, 4).role()).isEqualTo(TileRole.HEADER);
    assertThat(tile(view, 11).role()).isEqualTo(TileRole.ACTION);
    assertThat(tile(view, 11).hint().kind()).isEqualTo(HintKind.DESTRUCTIVE);
    assertThat(tile(view, 15).role()).isEqualTo(TileRole.ACTION);
    assertThat(tile(view, 0).role()).isEqualTo(TileRole.DECORATION);
    assertThat(tile(view, 18).role()).isEqualTo(TileRole.DECORATION);
  }

  private static PanelTile tile(PanelView view, int slot) {
    return view.tiles().stream().filter((PanelTile tile) -> tile.slot() == slot).findFirst().orElseThrow();
  }
}
