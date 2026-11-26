package art.arcane.adapt.client.hud;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudLayoutCodecTest {
    private static HudLayout customLayout() {
        HudProfile base = HudDefaults.profile("Main");
        HudWidgetConfig skills = base.widget("skills");
        HudProfile edited = base.withScale(1.4)
                .with(skills.withPlacement(new HudPlacement(HudAnchor.MIDDLE_RIGHT, -12.5, 30, 1.75, 0.6, HudOrientation.VERTICAL, false))
                        .withOptions(new HudOptions.Skills(List.of("agility", "swords"), 2, 7000L, true, 14)))
                .with(new HudWidgetConfig("resources_2", HudWidgetKind.RESOURCES, HudDefaults.placement(HudWidgetKind.RESOURCES),
                        new HudOptions.Resources(false, false, true, false)))
                .with(base.widget("toasts").withOptions(new HudOptions.Toasts(7000L, 2)))
                .with(base.widget("abilities").withOptions(((HudOptions.Abilities) HudDefaults.options(HudWidgetKind.ABILITIES))
                        .with(GlyphArrangement.BAR).withSizes(20, 12).withLimits(5, 2, 9, 0.4).withSort(GlyphSort.READY)
                        .withKinds(true, false, true, false).withSkills(List.of("rift")).withFlags(true, true)));
        return new HudLayout("Main", true, List.of(edited, HudDefaults.profile("Second")));
    }

    @Test
    void roundTripKeepsEverything() {
        HudLayout layout = customLayout();
        assertEquals(layout, HudLayoutCodec.read(HudLayoutCodec.write(layout)));
        assertEquals(HudDefaults.layout(), HudLayoutCodec.read(HudLayoutCodec.write(HudDefaults.layout())));
    }

    @Test
    void toastDurationAndCountRoundTrip() {
        HudLayout layout = customLayout();
        HudOptions.Toasts toasts = (HudOptions.Toasts) HudLayoutCodec.read(HudLayoutCodec.write(layout)).activeProfile().widget("toasts").options();
        assertEquals(7000L, toasts.durationMillis());
        assertEquals(2, toasts.maxVisible());
        HudProfile shared = HudLayoutCodec.importCode(HudLayoutCodec.export(layout.activeProfile())).orElseThrow();
        assertEquals(layout.activeProfile().widget("toasts").options(), shared.widget("toasts").options());
        HudOptions.Toasts imported = (HudOptions.Toasts) HudLayoutCodec.read(SCHEMA_1_DEFAULTS).activeProfile().widget("toasts").options();
        assertEquals(HudDefaults.options(HudWidgetKind.TOASTS), imported);
    }

    @Test
    void aSchema3ToastAtTheOldDefaultsTakesTheNewOnesAndAChosenValueStays() {
        HudOptions.Toasts untouched = schema3Toasts(5000L, 1);
        assertEquals(HudDefaults.options(HudWidgetKind.TOASTS), untouched);
        HudOptions.Toasts chosen = schema3Toasts(4500L, 2);
        assertEquals(4500L, chosen.durationMillis());
        assertEquals(2, chosen.maxVisible());
        HudOptions.Toasts mixed = schema3Toasts(5000L, 4);
        assertEquals(3000L, mixed.durationMillis());
        assertEquals(4, mixed.maxVisible());
        assertEquals(1, schema3Toasts(5000L, 1).maxVisible());
        HudOptions.Toasts tooLong = schema3Toasts(15000L, 1);
        assertEquals(HudOptions.Toasts.MAX_DURATION, tooLong.durationMillis());
        HudLayout current = HudLayoutCodec.read(HudLayoutCodec.write(customLayout()));
        assertEquals(customLayout(), current);
    }

    private static HudOptions.Toasts schema3Toasts(long duration, int shown) {
        String json = HudLayoutCodec.write(HudDefaults.layout())
                .replace("\"schema\": " + HudLayout.SCHEMA, "\"schema\": 3")
                .replace("\"durationMillis\": 3000", "\"durationMillis\": " + duration + ", \"width\": 160")
                .replace("\"maxVisible\": 1", "\"maxVisible\": " + shown);
        assertTrue(json.contains("\"schema\": 3") && json.contains("\"durationMillis\": " + duration), json);
        return (HudOptions.Toasts) HudLayoutCodec.read(json).activeProfile().widget("toasts").options();
    }

    @Test
    void writesASchemaVersion() {
        assertTrue(HudLayoutCodec.write(HudDefaults.layout()).contains("\"schema\": " + HudLayout.SCHEMA));
    }

    @Test
    void unreadableTextGivesTheDefaults() {
        assertEquals(HudDefaults.layout(), HudLayoutCodec.read(null));
        assertEquals(HudDefaults.layout(), HudLayoutCodec.read(""));
        assertEquals(HudDefaults.layout(), HudLayoutCodec.read("{not json"));
        assertEquals(HudDefaults.layout(), HudLayoutCodec.read("[1,2,3]"));
        assertEquals(HudDefaults.layout(), HudLayoutCodec.read("{\"profiles\": []}"));
        assertEquals(HudDefaults.layout(), HudLayoutCodec.read("{\"profiles\": [{\"name\": \"\"}]}"));
    }

    @Test
    void unknownFieldsAndFutureSchemasAreTolerated() {
        String json = """
                {"schema": 9, "future": {"a": 1}, "active": "P", "reducedMotion": true,
                 "profiles": [{"name": "P", "scale": 1.2, "theme": "dark", "widgets": [
                   {"id": "skills", "kind": "skills", "anchor": "bottom_left", "x": 3, "y": -7, "glow": true,
                    "options": {"maxRows": 2, "sparkles": 5}},
                   {"id": "radar", "kind": "radar", "anchor": "top_left"}
                 ]}]}
                """;
        HudLayout layout = HudLayoutCodec.read(json);
        HudProfile profile = layout.activeProfile();
        assertEquals("P", profile.name());
        assertTrue(layout.reducedMotion());
        assertEquals(1.2, profile.scale());
        HudWidgetConfig skills = profile.widget("skills");
        assertEquals(HudAnchor.BOTTOM_LEFT, skills.placement().anchor());
        assertEquals(3.0, skills.placement().offsetX());
        assertEquals(-7.0, skills.placement().offsetY());
        assertEquals(new HudOptions.Skills(List.of(), 2, 3000L, false, 10), skills.options());
        assertNull(profile.widget("radar"));
    }

    @Test
    void missingWidgetsAndFieldsTakeTheirDefaults() {
        String json = "{\"profiles\": [{\"name\": \"Old\", \"widgets\": [{\"id\": \"toasts\", \"kind\": \"toasts\", \"x\": \"left\"}]}]}";
        HudProfile profile = HudLayoutCodec.read(json).activeProfile();
        assertEquals(HudWidgetKind.values().length, profile.widgets().size());
        for (HudWidgetKind kind : HudWidgetKind.values()) {
            HudWidgetConfig widget = profile.widget(kind.id());
            assertNotNull(widget, kind.id());
            assertEquals(HudDefaults.widget(kind), widget);
        }
        assertEquals(1.0, profile.scale());
        assertTrue(profile.autoFit());
    }

    @Test
    void autoFitRoundTrips() {
        HudLayout layout = new HudLayout("P", false, List.of(HudDefaults.profile("P").withAutoFit(false)));
        assertFalse(HudLayoutCodec.read(HudLayoutCodec.write(layout)).activeProfile().autoFit());
    }

    @Test
    void invalidValuesAreClampedOrReplaced() {
        String json = """
                {"schema": 2, "profiles": [{"name": "X", "scale": 50, "widgets": [
                  {"id": "skills", "kind": "skills", "anchor": "nowhere", "scale": -3, "opacity": 7, "orientation": "diagonal",
                   "options": {"maxRows": 400, "lingerMillis": 5, "iconSize": 99}},
                  {"id": "xp_feed", "kind": "xp_feed"},
                  {"id": "resources_x", "kind": "resources"},
                  {"id": "resources_3", "kind": "resources"},
                  {"id": "resources_3", "kind": "resources", "anchor": "top_left"}
                ]}]}
                """;
        HudProfile profile = HudLayoutCodec.read(json).activeProfile();
        assertEquals(HudProfile.MAX_SCALE, profile.scale());
        HudWidgetConfig skills = profile.widget("skills");
        assertEquals(HudDefaults.placement(HudWidgetKind.SKILLS).anchor(), skills.placement().anchor());
        assertEquals(HudPlacement.MIN_SCALE, skills.placement().scale());
        assertEquals(1.0, skills.placement().opacity());
        assertEquals(HudOrientation.VERTICAL, skills.placement().orientation());
        assertEquals(new HudOptions.Skills(List.of(), HudOptions.Skills.MAX_ROWS, HudOptions.Skills.MIN_LINGER, false,
                HudOptions.Skills.MAX_ICON), skills.options());
        assertNull(profile.widget("xp_feed"));
        assertNull(profile.widget("resources_x"));
        assertEquals(HudDefaults.placement(HudWidgetKind.RESOURCES).anchor(), profile.widget("resources_3").placement().anchor());
    }

    @Test
    void duplicateProfileNamesKeepTheFirstAndAMissingActiveFallsBack() {
        String json = "{\"active\": \"Gone\", \"profiles\": [{\"name\": \"A\", \"scale\": 1.5}, {\"name\": \"A\", \"scale\": 2}, {\"name\": \"B\"}]}";
        HudLayout layout = HudLayoutCodec.read(json);
        assertEquals(2, layout.profiles().size());
        assertEquals("A", layout.activeProfile().name());
        assertEquals(1.5, layout.activeProfile().scale());
    }

    @Test
    void exportAndImportRoundTripAProfile() {
        HudProfile profile = customLayout().activeProfile();
        String code = HudLayoutCodec.export(profile);
        assertTrue(code.startsWith(HudLayoutCodec.EXPORT_PREFIX));
        assertFalse(code.contains("\n"));
        assertTrue(code.length() < HudLayoutCodec.write(customLayout()).length());
        assertEquals(Optional.of(profile), HudLayoutCodec.importCode("  " + code + "\n"));
    }

    @Test
    void importRejectsAnythingElse() {
        assertEquals(Optional.empty(), HudLayoutCodec.importCode(null));
        assertEquals(Optional.empty(), HudLayoutCodec.importCode("hello"));
        assertEquals(Optional.empty(), HudLayoutCodec.importCode(HudLayoutCodec.EXPORT_PREFIX + "!!!"));
        assertEquals(Optional.empty(), HudLayoutCodec.importCode(HudLayoutCodec.EXPORT_PREFIX + "AAAA"));
        String code = HudLayoutCodec.export(HudDefaults.profile("Cut"));
        assertEquals(Optional.empty(), HudLayoutCodec.importCode(code.substring(0, code.length() / 2)));
    }

    @Test
    void namesAreCleaned() {
        assertEquals("Raid", HudLayoutCodec.sanitizeName("  Raid\n"));
        assertEquals("Red", HudLayoutCodec.sanitizeName("\u00a7cRed"));
        assertEquals(HudProfile.MAX_NAME_LENGTH, HudLayoutCodec.sanitizeName("x".repeat(80)).length());
        assertEquals("", HudLayoutCodec.sanitizeName("\t\t"));
    }

    @Test
    void storeWritesAtomicallyAndReadsBack(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("config").resolve("adapt-hud.json");
        HudLayoutStore store = new HudLayoutStore(file);
        assertEquals(HudDefaults.layout(), store.load());
        store.save(customLayout());
        assertEquals(customLayout(), store.load());
        store.save(HudDefaults.layout());
        assertEquals(HudDefaults.layout(), store.load());
        try (Stream<Path> files = Files.list(file.getParent())) {
            assertEquals(List.of(file), files.toList());
        }
    }

    @Test
    void storeFallsBackToDefaultsForACorruptFile(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("adapt-hud.json");
        Files.writeString(file, "{\"profiles\": [", StandardCharsets.UTF_8);
        assertEquals(HudDefaults.layout(), new HudLayoutStore(file).load());
    }

    private static final String SCHEMA_1_DEFAULTS = """
            {"schema": 1, "active": "Default", "reducedMotion": false, "profiles": [{"name": "Default", "scale": 1.0,
             "autoFit": true, "widgets": [
              {"id": "xp_feed", "kind": "xp_feed", "anchor": "top_left", "x": 4.0, "y": 72.0, "scale": 1.0, "opacity": 1.0,
               "orientation": "vertical", "visible": true, "options": {"maxLines": 4, "durationMillis": 4000, "compact": false}},
              {"id": "skills", "kind": "skills", "anchor": "top_left", "x": 4.0, "y": 4.0, "scale": 1.0, "opacity": 1.0,
               "orientation": "vertical", "visible": true,
               "options": {"skills": [], "recentCount": 3, "showNumbers": true, "showKnowledge": false}},
              {"id": "resources", "kind": "resources", "anchor": "bottom_right", "x": -4.0, "y": -4.0, "scale": 1.0,
               "opacity": 1.0, "orientation": "vertical", "visible": true,
               "options": {"power": true, "master": true, "wisdom": true, "knowledge": true}},
              {"id": "abilities", "kind": "abilities", "anchor": "bottom_center", "x": 0.0, "y": -74.0, "scale": 1.0,
               "opacity": 1.0, "orientation": "horizontal", "visible": true, "options": {"radial": true, "maxShown": 8}},
              {"id": "notices", "kind": "notices", "anchor": "top_center", "x": 0.0, "y": 90.0, "scale": 1.0, "opacity": 1.0,
               "orientation": "vertical", "visible": true, "options": {"maxLines": 2}},
              {"id": "toasts", "kind": "toasts", "anchor": "top_center", "x": 0.0, "y": 54.0, "scale": 1.0, "opacity": 1.0,
               "orientation": "vertical", "visible": true, "options": {"durationMillis": 5000, "maxVisible": 1, "stackUp": false}}
             ]}]}
            """;

    @Test
    void anUntouchedSchema1LayoutBecomesTheNewDefaults() {
        assertEquals(HudDefaults.layout(), HudLayoutCodec.read(SCHEMA_1_DEFAULTS));
    }

    @Test
    void schema1CustomisationsSurviveTheMigration() {
        String json = SCHEMA_1_DEFAULTS
                .replace("\"skills\": [], \"recentCount\": 3", "\"skills\": [\"agility\"], \"recentCount\": 3")
                .replace("\"anchor\": \"bottom_right\", \"x\": -4.0", "\"anchor\": \"bottom_right\", \"x\": -40.0")
                .replace("\"anchor\": \"bottom_center\", \"x\": 0.0, \"y\": -74.0", "\"anchor\": \"top_right\", \"x\": -8.0, \"y\": 60.0")
                .replace("\"radial\": true, \"maxShown\": 8", "\"radial\": false, \"maxShown\": 5");
        HudProfile profile = HudLayoutCodec.read(json).activeProfile();
        assertNull(profile.widget("xp_feed"));
        HudOptions.Skills skills = (HudOptions.Skills) profile.widget("skills").options();
        assertEquals(List.of("agility"), skills.pinned());
        HudWidgetConfig resources = profile.widget("resources");
        assertTrue(resources.placement().visible());
        assertEquals(-40.0, resources.placement().offsetX());
        HudWidgetConfig abilities = profile.widget("abilities");
        assertEquals(HudAnchor.TOP_RIGHT, abilities.placement().anchor());
        assertEquals(HudDefaults.options(HudWidgetKind.ABILITIES), abilities.options());
        assertEquals(HudWidgetKind.values().length, profile.widgets().size());
    }

    @Test
    void schema1ExtraTrackersWithoutPinsAreDroppedAndPinnedOnesKept() {
        String json = SCHEMA_1_DEFAULTS.replace("{\"id\": \"notices\"",
                "{\"id\": \"skills_2\", \"kind\": \"skills\", \"options\": {\"skills\": []}},"
                        + "{\"id\": \"skills_3\", \"kind\": \"skills\", \"options\": {\"skills\": [\"swords\"]}},"
                        + "{\"id\": \"notices\"");
        HudProfile profile = HudLayoutCodec.read(json).activeProfile();
        assertNull(profile.widget("skills_2"));
        assertEquals(List.of("swords"), ((HudOptions.Skills) profile.widget("skills_3").options()).pinned());
    }

    @Test
    void aCurrentExportCodeRoundTripsWithoutMigration() {
        HudProfile current = customLayout().activeProfile();
        assertEquals(current, HudLayoutCodec.importCode(HudLayoutCodec.export(current)).orElseThrow());
    }

    @Test
    void schema2GlyphsMoveToTheProtectedRowAndKeepTheirSize() {
        String json = """
                {"schema": 2, "profiles": [{"name": "P", "widgets": [
                  {"id": "abilities", "kind": "abilities", "anchor": "middle_center", "x": 0.0, "y": 20.0, "scale": 1.0,
                   "opacity": 1.0, "orientation": "horizontal", "visible": true,
                   "options": {"iconSize": 20, "maxShown": 6, "showNames": true}},
                  {"id": "skills", "kind": "skills", "options": {"pinned": ["agility"], "maxRows": 2}}
                ]}]}
                """;
        HudProfile profile = HudLayoutCodec.read(json).activeProfile();
        HudWidgetConfig abilities = profile.widget("abilities");
        assertEquals(HudDefaults.placement(HudWidgetKind.ABILITIES), abilities.placement());
        HudOptions.Abilities options = (HudOptions.Abilities) abilities.options();
        assertEquals(20, options.iconSize());
        assertEquals(GlyphArrangement.ROW, options.arrangement());
        assertTrue(options.showNames());
        assertEquals(List.of("agility"), ((HudOptions.Skills) profile.widget("skills").options()).pinned());
        assertEquals(HudProfile.DEFAULT_PROTECT, profile.protect());
    }

    @Test
    void movedSchema2GlyphsKeepTheirPlaceAndExtraGlyphWidgetsRoundTrip() {
        String json = """
                {"schema": 2, "profiles": [{"name": "P", "widgets": [
                  {"id": "abilities", "kind": "abilities", "anchor": "top_right", "x": -8.0, "y": 60.0}
                ]}]}
                """;
        HudProfile profile = HudLayoutCodec.read(json).activeProfile();
        assertEquals(HudAnchor.TOP_RIGHT, profile.widget("abilities").placement().anchor());
        HudProfile split = HudEditorOptions.addGlyphs(profile.withProtect(0.25));
        HudProfile read = HudLayoutCodec.read(HudLayoutCodec.write(new HudLayout("P", false, List.of(split)))).activeProfile();
        assertEquals(split, read);
        assertEquals(GlyphArrangement.GRID, ((HudOptions.Abilities) read.widget("abilities_2").options()).arrangement());
    }
}
