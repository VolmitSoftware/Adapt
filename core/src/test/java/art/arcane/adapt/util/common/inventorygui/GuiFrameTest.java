package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.misc.CustomModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GuiFrameTest {
  private static final Id BAR = Id.minecraft("black_stained_glass_pane");

  private MockedStatic<CustomModel> models;
  private MockedStatic<AdaptLanguage> language;
  private PlayerRef viewer;

  @BeforeEach
  void prepare() {
    models = mockStatic(CustomModel.class, invocation -> {
      Id material = invocation.getArgument(0);
      CustomModel model = mock(CustomModel.class);
      when(model.material()).thenReturn(material);
      return model;
    });
    language = mockStatic(AdaptLanguage.class,
        invocation -> invocation.getMethod().getReturnType() == String.class ? "Text" : null);
    viewer = mock(PlayerRef.class);
    when(viewer.uuid()).thenReturn(UUID.randomUUID());
    when(viewer.name()).thenReturn("Viewer");
  }

  @AfterEach
  void restore() {
    GuiFrame.setMenusOffer(null);
    models.close();
    language.close();
  }

  @Test
  void theFooterSlotsAreTheSameColumnsAtEveryHeight() {
    for (int body = 1; body <= GuiFrame.MAX_BODY_ROWS; body++) {
      GuiWindow window = new GuiWindow(viewer);
      GuiFrame.apply(window, "test", body);
      for (GuiFrame.Slot slot : GuiFrame.Slot.values()) {
        GuiFrame.footer(window, slot, new GuiElement(slot.name()).setMaterial(Id.minecraft("stone")));
      }
      GuiFrame.subject(window, new GuiElement("subject").setMaterial(Id.minecraft("book")));
      ChestMenu menu = window.build();

      assertThat(menu.rows()).isEqualTo(body + 2);
      int footer = (body + 1) * GuiFrame.COLUMNS;
      for (GuiFrame.Slot slot : GuiFrame.Slot.values()) {
        assertThat(menu.button(footer + slot.column()).icon().type()).as(slot.name()).isEqualTo(Id.minecraft("stone"));
      }
      assertThat(menu.button(GuiFrame.SUBJECT_COLUMN).icon().type()).isEqualTo(Id.minecraft("book"));
    }
    assertThat(GuiFrame.Slot.values()).extracting(GuiFrame.Slot::column).doesNotHaveDuplicates()
        .allMatch((Integer column) -> column >= 0 && column < GuiFrame.COLUMNS);
    assertThat(GuiFrame.Slot.BACK.column()).isZero();
    assertThat(GuiFrame.Slot.MENUS.column()).isEqualTo(8);
  }

  @Test
  void onlyTheBarsCarryFiller() {
    GuiWindow window = new GuiWindow(viewer);
    GuiFrame.apply(window, "test", 3);
    ChestMenu menu = window.build();

    for (int column = 0; column < GuiFrame.COLUMNS; column++) {
      assertThat(menu.button(column).icon().type()).isEqualTo(BAR);
      assertThat(menu.button(4 * GuiFrame.COLUMNS + column).icon().type()).isEqualTo(BAR);
      for (int row = 1; row <= 3; row++) {
        assertThat(menu.button(row * GuiFrame.COLUMNS + column)).isNull();
      }
    }
  }

  @Test
  void thePagerShowsOnlyTheTurnsThatExist() {
    GuiWindow single = new GuiWindow(viewer);
    GuiFrame.apply(single, "test", 1);
    GuiFrame.pager(single, 0, 1, 5, (int page) -> {
    });
    assertThat(single.build().button(2 * 9 + 4).icon().type()).isEqualTo(BAR);

    List<Integer> opened = new ArrayList<>();
    GuiWindow first = new GuiWindow(viewer);
    GuiFrame.apply(first, "test", 1);
    GuiFrame.pager(first, 0, 3, 30, opened::add);
    ChestMenu menu = first.build();
    assertThat(menu.button(18 + 3).icon().type()).isEqualTo(BAR);
    assertThat(menu.button(18 + 4).icon().type()).isEqualTo(Id.minecraft("paper"));
    assertThat(menu.button(18 + 4).icon().amount()).isEqualTo(1);
    assertThat(menu.button(18 + 5).icon().type()).isEqualTo(Id.minecraft("spectral_arrow"));

    GuiWindow last = new GuiWindow(viewer);
    GuiFrame.apply(last, "test", 1);
    GuiFrame.pager(last, 2, 3, 30, opened::add);
    ChestMenu end = last.build();
    assertThat(end.button(18 + 3).icon().type()).isEqualTo(Id.minecraft("spectral_arrow"));
    assertThat(end.button(18 + 4).icon().amount()).isEqualTo(3);
    assertThat(end.button(18 + 5).icon().type()).isEqualTo(BAR);
  }

  @Test
  void theMenusOfferTakesItsCornerOnlyWhenItHasAnItem() {
    GuiFrame.setMenusOffer((PlayerRef shown) -> shown == viewer ? new GuiElement("offer").setMaterial(Id.minecraft("writable_book")) : null);
    GuiWindow window = new GuiWindow(viewer);
    GuiFrame.apply(window, "test", 2);
    assertThat(window.build().button(3 * 9 + 8).icon().type()).isEqualTo(Id.minecraft("writable_book"));

    GuiWindow other = new GuiWindow(mock(PlayerRef.class));
    GuiFrame.apply(other, "test", 2);
    assertThat(other.build().button(3 * 9 + 8).icon().type()).isEqualTo(BAR);

    GuiFrame.setMenusOffer((PlayerRef shown) -> {
      throw new IllegalStateException("broken");
    });
    GuiWindow failing = new GuiWindow(viewer);
    GuiFrame.apply(failing, "test", 2);
    assertThat(failing.build().button(3 * 9 + 8).icon().type()).isEqualTo(BAR);
  }

  @Test
  void aGridCentresEachRowAndPagesEveryFourRows() {
    List<Integer> items = new ArrayList<>();
    for (int index = 0; index < 31; index++) {
      items.add(index);
    }
    GuiFrame.Layout<Integer> layout = GuiFrame.grid(items, 7);

    assertThat(layout.pageCount()).isEqualTo(2);
    assertThat(layout.bodyRows()).isEqualTo(4);
    assertThat(layout.page(0).items()).hasSize(28);
    assertThat(layout.page(0).cells()).filteredOn((GuiFrame.Cell<Integer> cell) -> cell.row() == 0)
        .extracting(GuiFrame.Cell::column).containsExactly(1, 2, 3, 4, 5, 6, 7);
    assertThat(layout.page(1).cells()).extracting(GuiFrame.Cell::column).containsExactly(3, 4, 5);
    assertThat(layout.page(9)).isSameAs(layout.page(1));
    assertThat(layout.page(-1)).isSameAs(layout.page(0));
  }

  @Test
  void aGridPagesAtTheRowsItIsGiven() {
    List<Integer> items = new ArrayList<>();
    for (int index = 0; index < 15; index++) {
      items.add(index);
    }
    GuiFrame.Layout<Integer> two = GuiFrame.grid(items, 7, 2);
    assertThat(two.pageCount()).isEqualTo(2);
    assertThat(two.bodyRows()).isEqualTo(2);
    assertThat(two.page(0).items()).hasSize(14);
    assertThat(GuiFrame.grid(items, 7, 0).bodyRows()).isEqualTo(1);
    assertThat(GuiFrame.grid(items, 7, 9).pageCount()).isEqualTo(1);
  }

  @Test
  void aGridKeepsTheGapOfAMissingItem() {
    GuiFrame.Layout<String> layout = GuiFrame.grid(java.util.Arrays.asList("a", null, "c"), 7);
    assertThat(layout.page(0).cells()).extracting(GuiFrame.Cell::column).containsExactly(3, 5);
  }

  @Test
  void anEmptyBodyIsOneEmptyRow() {
    GuiFrame.Layout<String> layout = GuiFrame.grid(List.of(), 7);
    assertThat(layout.pageCount()).isEqualTo(1);
    assertThat(layout.bodyRows()).isEqualTo(1);
    assertThat(layout.page(0).cells()).isEmpty();
    assertThat(GuiFrame.groups(List.of(List.of(), List.of()), true).page(0).cells()).isEmpty();
  }

  @Test
  void labelledGroupsStartTheirOwnRowWithAHeading() {
    GuiFrame.Layout<String> layout = GuiFrame.groups(List.of(names("a", 3), names("b", 10), List.of(), names("c", 5)), true);

    GuiFrame.Page<String> page = layout.page(0);
    assertThat(layout.pageCount()).isEqualTo(1);
    assertThat(page.rows()).isEqualTo(4);
    assertThat(headings(page)).containsExactly("0:0", "1:1", "3:3");
    assertThat(row(page, 0)).containsExactly("a0", "a1", "a2");
    assertThat(columns(page, 0)).containsExactly(0, 1, 2, 3);
    assertThat(row(page, 1)).hasSize(8).allMatch((String name) -> name.startsWith("b"));
    assertThat(columns(page, 2)).containsExactly(1, 2);
    assertThat(row(page, 3)).containsExactly("c0", "c1", "c2", "c3", "c4");
  }

  @Test
  void aGroupMovesWholeToTheNextPageAndOnlyAnOversizedGroupSplits() {
    GuiFrame.Layout<String> layout = GuiFrame.groups(List.of(names("a", 20), names("b", 9), names("c", 40)), true);

    assertThat(layout.page(0).items()).hasSize(20).allMatch((String name) -> name.startsWith("a"));
    assertThat(layout.page(1).items()).hasSize(9).allMatch((String name) -> name.startsWith("b"));
    assertThat(layout.page(2).items()).hasSize(32);
    assertThat(layout.page(3).items()).hasSize(8);
    assertThat(headings(layout.page(3))).isEmpty();
    int total = 0;
    for (GuiFrame.Page<String> page : layout.pages()) {
      total += page.items().size();
      assertThat(page.rows()).isBetween(1, GuiFrame.MAX_BODY_ROWS);
    }
    assertThat(total).isEqualTo(69);
  }

  @Test
  void unlabelledGroupsCentreEveryRowAcrossTheWholeChest() {
    GuiFrame.Layout<String> layout = GuiFrame.groups(List.of(names("a", 12), names("b", 1)), false);
    GuiFrame.Page<String> page = layout.page(0);

    assertThat(headings(page)).isEmpty();
    assertThat(columns(page, 0)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8);
    assertThat(columns(page, 1)).containsExactly(3, 4, 5);
    assertThat(columns(page, 2)).containsExactly(4);
  }

  private static List<String> names(String prefix, int count) {
    List<String> names = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      names.add(prefix + index);
    }
    return names;
  }

  private static List<String> headings(GuiFrame.Page<String> page) {
    List<String> headings = new ArrayList<>();
    for (GuiFrame.Cell<String> cell : page.cells()) {
      if (cell.heading()) {
        assertThat(cell.column()).isZero();
        headings.add(cell.row() + ":" + cell.group());
      }
    }
    return headings;
  }

  private static List<String> row(GuiFrame.Page<String> page, int row) {
    List<String> items = new ArrayList<>();
    for (GuiFrame.Cell<String> cell : page.cells()) {
      if (cell.row() == row && !cell.heading()) {
        items.add(cell.item());
      }
    }
    return items;
  }

  private static List<Integer> columns(GuiFrame.Page<String> page, int row) {
    List<Integer> columns = new ArrayList<>();
    for (GuiFrame.Cell<String> cell : page.cells()) {
      if (cell.row() == row) {
        columns.add(cell.column());
      }
    }
    return columns;
  }
}
