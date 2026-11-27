package art.arcane.adapt.pack.layout;

import art.arcane.adapt.pack.surface.PackSlot;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackLayoutReaderTest {
  private final PackLayout layout = PackLayoutReader.builtIn();

  @Test
  void theGridMatchesTheVanillaChest() {
    PackGrid grid = layout.grid();
    assertThat(grid.x(PackSlot.top(0))).isEqualTo(8);
    assertThat(grid.y(PackSlot.top(0))).isEqualTo(18);
    assertThat(grid.x(PackSlot.top(53))).isEqualTo(152);
    assertThat(grid.y(PackSlot.top(53))).isEqualTo(108);
    assertThat(grid.y(PackSlot.lower(0))).isEqualTo(139);
    assertThat(grid.y(PackSlot.lower(26))).isEqualTo(175);
    assertThat(grid.y(PackSlot.lower(27))).isEqualTo(197);
  }

  @Test
  void theSkillIdentityClearsTheFirstRowWithOrWithoutAHeading() {
    for (boolean lower : new boolean[]{true, false}) {
      ScreenLayout skill = layout.screen("skill", lower);
      Card first = skill.cards().get(0);
      int firstRowTop = Math.min(first.text("name").y(), first.text("heading").y());
      for (String role : new String[]{"level", "level.alone"}) {
        assertThat(skill.text(role).y() + 8).as(role).isLessThanOrEqualTo(firstRowTop - 3);
      }
      assertThat(skill.text("name").y()).isLessThan(skill.text("level").y());
    }
  }

  /** The card grids of the plain tome screens start in the body, below the header line (v5 body top y 17). */
  @Test
  void theFirstCardRowStartsBelowTheHeader() {
    for (String id : List.of("skills", "mutate.partners")) {
      for (boolean lower : new boolean[]{true, false}) {
        Card first = layout.screen(id, lower).cards().get(0);
        int top = layout.grid().y(first.icon());
        for (TextSpec text : first.text().values()) {
          top = Math.min(top, text.y());
        }
        for (BarSpec bar : first.bars().values()) {
          top = Math.min(top, bar.y());
        }
        assertThat(top).as(id + (lower ? "" : ".top")).isGreaterThanOrEqualTo(17);
      }
    }
  }

  @Test
  void skillsHaveTwelveSeatsUnderTheControlsOverTheFullCanvasAndSixOnTopRows() {
    ScreenLayout full = layout.screen("skills", true);
    ScreenLayout top = layout.screen("skills", false);
    assertThat(full.cards()).hasSize(12);
    assertThat(top.cards()).hasSize(6);
    Card first = full.cards().get(0);
    assertThat(first.cells()).hasSize(6).contains(PackSlot.top(9), PackSlot.top(20));
    assertThat(first.icon()).isEqualTo(PackSlot.top(9));
    assertThat(first.top()).isEqualTo(36 + PackArt.CHEST_SHIFT);
    assertThat(first.text("name").x()).isEqualTo(8);
    Card straddling = full.cards().get(6);
    assertThat(straddling.cells()).contains(PackSlot.top(45), PackSlot.lower(0));
    assertThat(full.cards().get(9).icon()).isEqualTo(PackSlot.lower(9));
    assertThat(full.cards().get(9).shift()).isEqualTo(PackArt.LOWER_SHIFT);
    assertThat(full.slots("search")).containsExactly(PackSlot.top(0), PackSlot.top(1), PackSlot.top(2));
    assertThat(top.cards().get(0).shift()).isZero();
  }

  @Test
  void theSkillPageFlowsDownTheLeftColumnThenTheRight() {
    ScreenLayout full = layout.screen("skill", true);
    assertThat(full.cards()).hasSize(16);
    assertThat(full.cards().get(0).cells()).containsExactly(PackSlot.top(9), PackSlot.top(10), PackSlot.top(11), PackSlot.top(12));
    assertThat(full.cards().get(5).icon()).isEqualTo(PackSlot.lower(0));
    assertThat(full.cards().get(8).icon()).isEqualTo(PackSlot.top(14));
    assertThat(full.cards().get(0).text("level").x()).isEqualTo(-18);
    assertThat(layout.screen("skill", false).cards()).hasSize(8);
  }

  @Test
  void cardsNeverShareASlot() {
    for (String id : List.of("skills", "skill", "adaptation.settings")) {
      for (boolean lower : List.of(true, false)) {
        Set<PackSlot> seen = new HashSet<>();
        for (Card card : layout.screen(id, lower).cards()) {
          for (PackSlot cell : card.cells()) {
            assertThat(seen.add(cell)).as(id + " " + cell).isTrue();
          }
        }
      }
    }
  }

  @Test
  void everyScreenDegradesToTopRowsWithTheFootBarInChestRowFive() {
    for (String id : List.of("skills", "skill", "adaptation.levels", "adaptation.recipes", "adaptation.settings", "mutate")) {
      ScreenLayout top = layout.screen(id, false);
      assertThat(top.usesLower()).as(id).isFalse();
      for (List<PackSlot> slots : top.slots().values()) {
        assertThat(slots).allMatch((PackSlot slot) -> slot.area() == PackSlot.Area.TOP);
      }
    }
    assertThat(layout.screen("skill", false).slot("foot.back")).isEqualTo(PackSlot.top(45));
    assertThat(layout.screen("skill", true).slot("foot.back")).isEqualTo(PackSlot.lower(27));
    assertThat(layout.screen("mutate", false).slot("seal.mark")).isEqualTo(PackSlot.top(49));
    assertThat(layout.screen("mutate", true).slot("seal.mark")).isEqualTo(PackSlot.lower(22));
    for (int rows = 1; rows <= 6; rows++) {
      assertThat(layout.screen("ledger" + rows, true).usesLower()).isFalse();
    }
  }

  @Test
  void skillPagesShowTheirCrumbRibbonOnlyWhenMutated() {
    for (boolean lower : List.of(true, false)) {
      assertThat(layout.screen("skill", lower).ribbon()).isEqualTo(CrumbRibbon.MUTATED);
      assertThat(layout.screen("skills", lower).ribbon()).isEqualTo(CrumbRibbon.ALWAYS);
      assertThat(layout.screen("adaptation.levels", lower).ribbon()).isEqualTo(CrumbRibbon.ALWAYS);
    }
    assertThat(CrumbRibbon.MUTATED.shows(false)).isFalse();
    assertThat(CrumbRibbon.MUTATED.shows(true)).isTrue();
    assertThat(CrumbRibbon.ALWAYS.shows(false)).isTrue();
  }

  @Test
  void hotbarControlFacesAreThirteenTallFromTheFootFaceArt() {
    List<PackArt.Paint> paint = PackArt.paint(layout);
    PackArt.Paint back = paint.stream().filter((PackArt.Paint piece) -> piece.key().equals(PackArt.controlFace(PackArt.BACK, 197))).findFirst()
        .orElseThrow();
    assertThat(back.source()).isEqualTo(PackArt.FOOT_FACE_ART + "back");
    assertThat(back.height()).isEqualTo(PackArt.FOOT_FACE_HEIGHT);
    PackArt.Paint tab = paint.stream().filter((PackArt.Paint piece) -> piece.key().equals(PackArt.controlFace(PackArt.tab("levels", true), 197)))
        .findFirst().orElseThrow();
    assertThat(tab.source()).isEqualTo(PackArt.FOOT_FACE_ART + "tab_levels_on");
    PackArt.Paint more = paint.stream().filter((PackArt.Paint piece) -> piece.key().equals(PackArt.controlFace(PackArt.MORE, 197))).findFirst()
        .orElseThrow();
    assertThat(more.height()).isEqualTo(16);
  }

  @Test
  void slotRangesExpand() {
    assertThat(PackLayoutReader.slots("top:18-20")).containsExactly(PackSlot.top(18), PackSlot.top(19), PackSlot.top(20));
    assertThat(PackLayoutReader.slots("lower:4")).containsExactly(PackSlot.lower(4));
  }

  @Test
  void unknownScreensAndFormatsAreRefused() {
    assertThatThrownBy(() -> layout.screen("nothing", true)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PackLayoutReader.read(new StringReader("{\"format\": 9}")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
