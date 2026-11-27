package art.arcane.adapt.pack.bake;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackValidatorTest {
    private static final byte[] MCMETA = "{\"pack\":{\"description\":\"t\",\"min_format\":84,\"max_format\":97}}".getBytes(StandardCharsets.UTF_8);

    private static Map<String, byte[]> pack(String path, String json) {
        Map<String, byte[]> files = new TreeMap<>();
        files.put("pack.mcmeta", MCMETA);
        files.put(path, json.getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private static byte[] png(int width, int height) {
        return new art.arcane.adapt.pack.raster.Raster(width, height).png();
    }

    private static void assertProblem(Map<String, byte[]> files, String fragment) {
        List<String> problems = PackValidator.check(files);
        assertTrue(problems.stream().anyMatch((String problem) -> problem.contains(fragment)), problems.toString());
    }

    @Test
    void wrongFormatIsReported() {
        Map<String, byte[]> files = new TreeMap<>();
        files.put("pack.mcmeta", "{\"pack\":{\"description\":\"t\",\"pack_format\":15}}".getBytes(StandardCharsets.UTF_8));
        assertProblem(files, "formats are not 84 to 97");
    }

    @Test
    void missingGlyphTextureIsReported() {
        assertProblem(pack("assets/adapt/font/f.json",
                "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"adapt:font/g.png\",\"height\":8,\"ascent\":7,\"chars\":[\"\\ue000\"]}]}"), "missing texture");
    }

    @Test
    void ascentAboveHeightIsReported() {
        Map<String, byte[]> files = pack("assets/adapt/font/f.json",
                "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"adapt:font/g.png\",\"height\":8,\"ascent\":9,\"chars\":[\"\\ue000\"]}]}");
        files.put("assets/adapt/textures/font/g.png", png(8, 8));
        assertProblem(files, "ascent 9 above height 8");
    }

    @Test
    void oversizedGlyphIsReported() {
        Map<String, byte[]> files = pack("assets/adapt/font/f.json",
                "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"adapt:font/g.png\",\"height\":8,\"ascent\":7,\"chars\":[\"\\ue000\"]}]}");
        files.put("assets/adapt/textures/font/g.png", png(300, 8));
        assertProblem(files, "larger than 256");
    }

    @Test
    void duplicateCodepointIsReported() {
        Map<String, byte[]> files = pack("assets/adapt/font/f.json", "{\"providers\":["
                + "{\"type\":\"bitmap\",\"file\":\"adapt:font/g.png\",\"height\":8,\"ascent\":7,\"chars\":[\"\\ue000\"]},"
                + "{\"type\":\"bitmap\",\"file\":\"adapt:font/g.png\",\"height\":8,\"ascent\":7,\"chars\":[\"\\ue000\"]}]}");
        files.put("assets/adapt/textures/font/g.png", png(8, 8));
        assertProblem(files, "declared twice");
    }

    @Test
    void unresolvedItemModelIsReported() {
        assertProblem(pack("assets/adapt/items/x.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"adapt:item/x\"}}"), "missing model");
    }

    @Test
    void modelWithMissingTextureIsReported() {
        assertProblem(pack("assets/adapt/models/item/x.json", "{\"textures\":{\"0\":\"adapt:item/x\"}}"), "missing texture");
    }

    @Test
    void stripNotWholeFramesIsReported() {
        Map<String, byte[]> files = pack("assets/adapt/textures/item/x.png.mcmeta", "{\"animation\":{\"width\":4,\"height\":5}}");
        files.put("assets/adapt/textures/item/x.png", png(4, 12));
        assertProblem(files, "not whole");
    }

    @Test
    void frameIndexPastTheStripIsReported() {
        Map<String, byte[]> files = pack("assets/adapt/textures/item/x.png.mcmeta",
                "{\"animation\":{\"width\":4,\"height\":4,\"frames\":[{\"index\":3,\"time\":1}]}}");
        files.put("assets/adapt/textures/item/x.png", png(4, 12));
        assertProblem(files, "past the strip");
    }

    private static Map<String, byte[]> hosted(int slot, String definition, String model) {
        PackManifest.Region region = new PackManifest.Region("assets/adapt/textures/item/r0.png", 0, 0, 4, 4, 1, 1, "exact", 1, 0, 0);
        PackManifest.Item item = new PackManifest.Item(slot, "adapt:page/s" + slot, List.of(region), List.of());
        PackManifest.PageFont page = new PackManifest.PageFont("adapt:kit", "single", "A", "adapt:page", 0, 0, 0, 0, List.of(), List.of(item), List.of());
        PackManifest manifest = new PackManifest(PackManifest.SCHEMA, "adapt:ui/space", Map.of(), List.of(), List.of(page),
                Map.of("A", List.of(22, 31, 40, 85)), List.of(), List.of(), List.of(), List.of(), List.of(), null, "hash", null,
                new PackManifest.Build(PackBake.VERSION, "", ""));
        Map<String, byte[]> files = pack(PackManifest.PATH, manifest.toJson());
        if (definition != null) {
            files.put("assets/adapt/items/page/s" + slot + ".json", definition.getBytes(StandardCharsets.UTF_8));
        }
        if (model != null) {
            files.put("assets/adapt/models/item/r0.json", model.getBytes(StandardCharsets.UTF_8));
        }
        return files;
    }

    private static final String COMPOSITE = "{\"model\":{\"type\":\"minecraft:composite\",\"models\":[{\"type\":\"minecraft:model\","
            + "\"model\":\"adapt:item/r0\"}]},\"oversized_in_gui\":true}";

    @Test
    void hostItemInANonHostSlotIsReported() {
        assertProblem(hosted(23, COMPOSITE, "{\"elements\":[]}"), "not a host of mode A");
    }

    @Test
    void missingHostItemIsReported() {
        assertProblem(hosted(22, null, null), "missing host item");
    }

    @Test
    void missingHostModelIsReported() {
        assertProblem(hosted(22, COMPOSITE, null), "missing host model");
    }

    @Test
    void hostModelWithABaseLayerIsReported() {
        assertProblem(hosted(22, COMPOSITE, "{\"parent\":\"minecraft:item/generated\"}"), "has a base layer");
    }

    @Test
    void wellFormedHostPassesTheHostChecks() {
        assertEquals(List.of(), PackValidator.check(hosted(22, COMPOSITE, "{\"elements\":[]}")));
    }

    @Test
    void hudGlyphWithAClearLastColumnIsReported() {
        Map<String, byte[]> files = pack("assets/adapt/font/hud.json",
                "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"adapt:font/hud/g.png\",\"height\":4,\"ascent\":4,\"chars\":[\"\\ue020\"]}]}");
        files.put("assets/adapt/textures/font/hud/g.png", png(60, 4));
        assertProblem(files, "clear last column");
    }

    @Test
    void cleanPackHasNoProblems() {
        Map<String, byte[]> files = pack("assets/adapt/textures/item/x.png.mcmeta",
                "{\"animation\":{\"width\":4,\"height\":4,\"frames\":[{\"index\":2,\"time\":1}]}}");
        files.put("assets/adapt/textures/item/x.png", png(4, 12));
        assertEquals(List.of(), PackValidator.check(files));
    }
}
