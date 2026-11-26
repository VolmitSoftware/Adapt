package art.arcane.adapt.client.theme;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The composer's placement rules on game-sized books, for every skill and a range of mutations and screens. */
class DressingRulesTest {
    /** A 460 by 260 book centred on a screen, laid out as AdaptScreen lays it out, with its content keep-out boxes. */
    static BookGeometry book(int leaf, int screenWidth, int screenHeight) {
        int width = leaf == 0 ? 460 : 230;
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - 260) / 2;
        BookGeometry.Box box = new BookGeometry.Box(x, y, x + width, y + 260);
        int top = y + 8;
        int bodyTop = top + 22;
        int fold = leaf == 0 ? x + 230 : Integer.MIN_VALUE;
        BookGeometry.Page left = leaf == 2 ? null : new BookGeometry.Page(x + 8, x + 222);
        BookGeometry.Page right = leaf == 1 ? null : leaf == 2 ? new BookGeometry.Page(x + 8, x + 222) : new BookGeometry.Page(x + 238, x + 452);
        List<BookGeometry.Box> keep = new ArrayList<>();
        for (BookGeometry.Page page : new BookGeometry.Page[]{left, right}) {
            if (page != null) {
                keep.add(new BookGeometry.Box(page.x() - 1, bodyTop - 1, page.right() + 1, box.y1() - 17));
                keep.add(new BookGeometry.Box(page.x(), top + 15, page.right(), top + 22));
            }
        }
        keep.add(new BookGeometry.Box(x + 7, top - 1, x + 140, top + 17));
        if (right != null && leaf == 0) {
            keep.add(new BookGeometry.Box(right.x() + 8, top - 1, right.right() + 1, top + 17));
        }
        return new BookGeometry(leaf, box, top, bodyTop, top + 17, fold, left, right, 4, x + 143, x + 220, keep, screenWidth, screenHeight);
    }

    private static List<PageTheme> themes() {
        List<PageTheme> themes = new ArrayList<>();
        for (String skill : ThemeFixtures.SKILLS) {
            themes.add(ThemeFixtures.theme(skill, null));
        }
        for (int index = 0; index < ThemeFixtures.SKILLS.size(); index++) {
            themes.add(ThemeFixtures.theme(ThemeFixtures.SKILLS.get(index), ThemeFixtures.SKILLS.get((index * 7 + 5) % ThemeFixtures.SKILLS.size())));
        }
        return themes;
    }

    @Test
    void nothingCoversContentOrLeavesTheScreen() {
        int[][] screens = {{640, 360}, {480, 270}, {470, 266}, {427, 240}, {960, 540}};
        for (int[] screen : screens) {
            for (int leaf = 0; leaf < 3; leaf++) {
                BookGeometry geometry = book(leaf, screen[0], screen[1]);
                for (PageTheme theme : themes()) {
                    Dressing dressing = Dressing.compose(geometry, theme);
                    for (Dressing.Piece piece : dressing.pieces()) {
                        DressSprite sprite = piece.sprite();
                        String at = theme.lead().skill() + "/" + (theme.partner() == null ? "" : theme.partner().skill()) + " leaf " + leaf + " on "
                                + screen[0] + "x" + screen[1] + ": " + piece.slot().key() + " at " + piece.x() + "," + piece.y();
                        assertTrue(piece.x() >= 1 && piece.y() >= 1 && piece.x() + sprite.width() <= screen[0] - 1
                                && piece.y() + sprite.height() <= screen[1] - 1, at + " leaves the screen");
                        for (BookGeometry.Box box : geometry.keep()) {
                            for (int y = 0; y < sprite.height(); y++) {
                                for (int x = 0; x < sprite.width(); x++) {
                                    int px = piece.x() + x;
                                    int py = piece.y() + y;
                                    boolean inside = px >= box.x0() && px < box.x1() && py >= box.y0() && py < box.y1();
                                    assertFalse(inside && sprite.opaque(piece.flip(), x, y), at + " covers content");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void composesTheSameBookTheSameWay() {
        BookGeometry geometry = book(0, 640, 360);
        for (PageTheme theme : themes()) {
            assertEquals(Dressing.compose(geometry, theme).pieces(), Dressing.compose(geometry, theme).pieces());
        }
    }

    @Test
    void aTightScreenDropsWholePiecesInsteadOfCuttingThem() {
        PageTheme theme = ThemeFixtures.theme("herbalism", null);
        Dressing roomy = Dressing.compose(book(0, 640, 360), theme);
        Dressing tight = Dressing.compose(book(0, 464, 264), theme);
        assertTrue(tight.pieces().size() < roomy.pieces().size(), "a book touching the screen edge keeps fewer pieces");
        for (Dressing.Piece piece : tight.pieces()) {
            assertTrue(piece.x() >= 1 && piece.x() + piece.sprite().width() <= 463);
        }
    }
}
