package art.arcane.adapt.client.server.panel;

import art.arcane.adapt.client.protocol.FrameCodec;
import art.arcane.adapt.client.protocol.FrameLimits;
import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelPaging;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.TileAction;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuPaging;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PanelViewsTest {
  private static final MenuIcon STONE = MenuIcon.of(Id.minecraft("stone"), "Stone", List.of("a"));
  private static final MenuIcon PANE = MenuIcon.of(Id.minecraft("gray_stained_glass_pane"), " ", List.of());

  @Test
  void unannotatedTilesGetInferredRoles() {
    ChestMenu menu = ChestMenu.builder("Menu", 1)
        .set(0, MenuButton.display(PANE))
        .set(1, MenuButton.display(STONE))
        .set(2, new MenuButton(STONE, (MenuClick click) -> { }))
        .set(3, MenuButton.display(STONE).withHint(MenuHint.toggle(true)))
        .set(4, MenuButton.display(STONE).withHint(MenuHint.navigation("next")))
        .set(5, MenuButton.display(STONE).withHint(MenuHint.destructive("Delete")))
        .build();

    List<PanelTile> tiles = PanelViews.view(1, 0, menu).tiles();

    assertThat(tiles).extracting(PanelTile::role).containsExactly(TileRole.DECORATION, TileRole.INFO, TileRole.ENTRY,
        TileRole.SETTING, TileRole.NAVIGATION, TileRole.ACTION);
    assertThat(PanelViews.chromeTarget(tiles.get(4))).isEqualTo("next");
    assertThat(PanelViews.chromeTarget(tiles.get(2))).isNull();
  }

  @Test
  void namedMenusBecomeListsAndFormsUnlessTheyDeclareAGrid() {
    ChestMenu.Builder list = ChestMenu.builder("List", 2).fill(MenuButton.display(PANE))
        .set(3, new MenuButton(STONE, (MenuClick click) -> { }))
        .set(9, MenuButton.display(STONE).withHint(MenuHint.navigation("back")));
    ChestMenu form = list.build().toBuilder().set(4, MenuButton.display(STONE).withHint(MenuHint.toggle(false))).build();
    ChestMenu unnamed = list.build().toBuilder().set(5, new MenuButton(MenuIcon.of(Id.minecraft("dirt"), "", List.of()),
        (MenuClick click) -> { })).build();

    assertThat(PanelViews.view(1, 0, list.build()).layout()).isEqualTo(PanelLayout.LIST);
    assertThat(PanelViews.view(1, 0, form).layout()).isEqualTo(PanelLayout.FORM);
    assertThat(PanelViews.view(1, 0, unnamed).layout()).isEqualTo(PanelLayout.GRID);
    assertThat(PanelViews.view(1, 0, list.layout(MenuLayout.GRID).build()).layout()).isEqualTo(PanelLayout.GRID);
  }

  @Test
  void presentationReachesTheView() {
    GuiElement element = new GuiElement("speed").setMaterial(Id.minecraft("clock")).setName("Speed")
        .addLore("Value: 3").addLore("Left click: edit in chat")
        .setRole(MenuRole.SETTING).setLabel("Speed setting").setSection("Movement").setValue("3").setDescription(List.of("How fast"))
        .setHint(MenuHint.number("3", 0D, 10D, 1D, (MenuClick click, String text) -> { }))
        .onLeftClick((MenuClick click) -> { }).onShiftRightClick((MenuClick click) -> { })
        .setActionLabel(ClickKind.SHIFT_RIGHT, "Reset");
    ChestMenu menu = ChestMenu.builder("Settings", 1).set(0, element.button())
        .paging(new MenuPaging(1, 3, 25)).breadcrumb(List.of("Adapt", "Core")).build();

    PanelView view = PanelViews.view(4, 2, menu);
    PanelTile tile = view.tiles().get(0);

    assertThat(view.paging()).isEqualTo(new PanelPaging(1, 3, 25));
    assertThat(view.breadcrumb()).containsExactly("Adapt", "Core");
    assertThat(view.layout()).isEqualTo(PanelLayout.FORM);
    assertThat(tile.role()).isEqualTo(TileRole.SETTING);
    assertThat(tile.name()).isEqualTo("Speed setting");
    assertThat(tile.section()).isEqualTo("Movement");
    assertThat(tile.value()).isEqualTo("3");
    assertThat(tile.shownDescription()).containsExactly("How fast");
    assertThat(tile.lore()).containsExactly("Value: 3", "Left click: edit in chat");
    assertThat(tile.actions()).containsExactly(new TileAction(ClickKind.LEFT, ""), new TileAction(ClickKind.SHIFT_RIGHT, "Reset"));
    assertThat(tile.hint().kind()).isEqualTo(HintKind.NUMBER);
    assertThat(tile.hint().typed()).isTrue();
    assertThat(tile.hint().min()).isEqualTo(0D);
    assertThat(tile.hint().max()).isEqualTo(10D);
    assertThat(tile.hint().step()).isEqualTo(1D);
  }

  @Test
  void choicesCarryTheirOptions() {
    MenuButton button = MenuButton.display(STONE)
        .withHint(MenuHint.choice("FAST", List.of("SLOW", "FAST"), (MenuClick click, String text) -> { }));

    PanelTile tile = PanelViews.view(1, 0, ChestMenu.builder("C", 1).set(0, button).build()).tiles().get(0);

    assertThat(tile.hint().kind()).isEqualTo(HintKind.CHOICE);
    assertThat(tile.hint().options()).containsExactly("SLOW", "FAST");
    assertThat(tile.role()).isEqualTo(TileRole.SETTING);
  }

  @Test
  void presentationNeverChangesTheChestIcon() {
    GuiElement plain = new GuiElement("x").setMaterial(Id.minecraft("clock")).setName("Speed").addLore("Value: 3")
        .setCount(4).setEnchanted(true).onLeftClick((MenuClick click) -> { });
    GuiElement annotated = new GuiElement("x").setMaterial(Id.minecraft("clock")).setName("Speed").addLore("Value: 3")
        .setCount(4).setEnchanted(true).onLeftClick((MenuClick click) -> { })
        .setRole(MenuRole.SETTING).setSection("S").setValue("3").setDescription(List.of("d"))
        .setActionLabel(ClickKind.LEFT, "Edit").setHint(MenuHint.text("3", (MenuClick click, String text) -> { }));

    assertThat(annotated.button().icon()).isEqualTo(plain.button().icon());
    assertThat(annotated.button().clickable()).isEqualTo(plain.button().clickable());
  }

  @Test
  void decorationIsMarkedWhereTheFillerIsCreated() {
    GuiElement filler = new GuiElement("bg").setMaterial(Id.minecraft("black_stained_glass_pane")).setName(" ")
        .setRole(MenuRole.DECORATION).onLeftClick((MenuClick click) -> { });

    PanelTile tile = PanelViews.view(1, 0, ChestMenu.builder("D", 1).set(0, filler.button()).build()).tiles().get(0);

    assertThat(tile.role()).isEqualTo(TileRole.DECORATION);
  }

  @Test
  void presentationTextStaysInsideTheFrameLimit() throws Exception {
    String huge = "x".repeat(FrameLimits.MAX_TEXT_BYTES);
    List<String> lines = Collections.nCopies(FrameLimits.MAX_LINES, "y".repeat(4000));
    ChestMenu.Builder builder = ChestMenu.builder(huge, 6);
    for (int slot = 0; slot < 54; slot++) {
      GuiElement element = new GuiElement("e" + slot).setMaterial(Id.minecraft("stone")).setName(huge).addLore(lines)
          .setDescription(lines).setSection(huge).setValue(huge)
          .setHint(MenuHint.choice(huge, Collections.nCopies(100, huge), (MenuClick click, String text) -> { }));
      builder.set(slot, element.button());
    }
    builder.breadcrumb(Collections.nCopies(40, huge));

    PanelView view = PanelViews.view(1, 0, builder.build());
    byte[] encoded = FrameCodec.encodeServer(new ServerFrame.PanelOpen(view));

    assertThat(encoded.length).isLessThanOrEqualTo(FrameLimits.MAX_S2C_BYTES);
    assertThat(view.breadcrumb()).hasSize(FrameLimits.MAX_BREADCRUMB);
    assertThat(view.tiles().get(0).hint().options()).isNotEmpty().hasSizeLessThanOrEqualTo(FrameLimits.MAX_CHOICES);
    assertThat(FrameCodec.decodeServer(encoded)).isEqualTo(new ServerFrame.PanelOpen(view));
  }
}
