package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.kit.KitDraw;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every locale's widest adaptation names fit a cell of the three-column grid whole: on up to three lines, at the font's
 * size, words broken only with four letters on each side, never cut short and never into the gutter, at GUI scale 2 and 3.
 */
class CardNameLocalesTest {
    private static final Path LOCALES = Path.of("../../src/main/resources");
    private static final Pattern TABLE = Pattern.compile("^\\[([a-z_]+)\\.([a-z0-9_]+)]$");
    private static final Pattern NAME = Pattern.compile("^name = \"(.*)\"$");
    private static final Pattern SHORT_NAME = Pattern.compile("^short_name = \"(.*)\"$");
    private static final List<String> SKILLS = List.of("agility", "architect", "axe", "axes", "blocking", "brewing", "chronos",
            "crafting", "discovery", "enchanting", "excavation", "golemancy", "herbalism", "hunter", "kinetics", "nether", "pickaxe",
            "ranged", "rift", "seaborne", "stealth", "sword", "swords", "taming", "tragoul", "unarmed");
    private static final int WIDEST = 12;

    @Test
    void theWidestNamesOfEveryLocaleFitACardWhole() throws IOException {
        List<Path> files;
        try (Stream<Path> listed = Files.list(LOCALES)) {
            files = listed.filter((Path file) -> file.toString().endsWith(".toml")).sorted().toList();
        }
        assertEquals(17, files.size(), "locales");
        for (int scale : new int[] {2, 3}) {
            KitDraw draw = KitDraw.vanilla(640 * 3 / scale, 360 * 3 / scale, scale);
            int room = AdaptationTile.nameRoom(AdaptationGrid.cardWidth(212, SkillScreen.COLUMNS));
            for (Path file : files) {
                List<String> names = names(file);
                assertTrue(names.size() > 200, file + " has " + names.size() + " adaptation names");
                names.sort(Comparator.comparingInt(draw::textWidth).reversed());
                for (String name : names.subList(0, WIDEST)) {
                    List<String> fit = CardName.fit(draw, name, room, AdaptationTile.Look.FULL.lines());
                    if (fit == null) {
                        fit = CardName.fit(draw, name, room, AdaptationTile.Look.TALL.lines());
                    }
                    String where = file.getFileName() + " @" + scale + " " + name + " " + fit;
                    assertNotNull(fit, where + " does not fit");
                    for (String line : fit) {
                        assertTrue(draw.textWidth(line) - 1 <= room, where + ": " + line + " runs into the gutter");
                    }
                    assertEquals(letters(name), letters(String.join("", fit)), where + " is cut short");
                    if (!NameLines.ideographic(name)) {
                        AdaptationGridTest.assertWholeWordsOrFourLetters(name, fit);
                    }
                }
            }
        }
    }

    /** The names cards show for the locale's adaptation tables, {@code [skill.adaptation]}: the short name where it has one. */
    private static List<String> names(Path file) throws IOException {
        List<String> names = new ArrayList<>();
        boolean adaptation = false;
        for (String line : Files.readAllLines(file)) {
            Matcher table = TABLE.matcher(line.trim());
            if (line.startsWith("[")) {
                adaptation = table.matches() && SKILLS.contains(table.group(1)) && !table.group(2).equals("item");
                continue;
            }
            Matcher name = NAME.matcher(line.trim());
            Matcher shortName = SHORT_NAME.matcher(line.trim());
            if (adaptation && name.matches()) {
                names.add(name.group(1).replace("\\\"", "\""));
            } else if (adaptation && shortName.matches()) {
                names.set(names.size() - 1, shortName.group(1).replace("\\\"", "\""));
            }
        }
        return names;
    }

    private static String letters(String text) {
        return text.replace(" ", "").replace("-", "");
    }
}
