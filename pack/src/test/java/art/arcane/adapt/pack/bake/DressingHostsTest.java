package art.arcane.adapt.pack.bake;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DressingHostsTest {
    /** The design measures the right and down reach from the slot's far edge, 16 pixels past its corner. */
    private static final DressingHosts.Reach DESIGN_REACH = new DressingHosts.Reach(144, 16 + 184);
    private static final List<String> BUILTIN = List.of("agility", "architect", "axes", "blocking", "brewing", "chronos", "crafting", "discovery",
            "enchanting", "excavation", "golemancy", "herbalism", "hunter", "kinetics", "nether", "pickaxe", "ranged", "rift", "seaborne", "stealth",
            "swords", "taming", "tragoul", "unarmed");

    private static JsonObject resource(String path) {
        try (InputStream in = DressingHostsTest.class.getResourceAsStream(path)) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static List<MotionItems.Area> cells(JsonObject regions, String set) {
        List<MotionItems.Area> cells = new ArrayList<>();
        JsonArray list = regions.getAsJsonObject(set).getAsJsonArray("cells");
        for (int index = 0; index < list.size(); index++) {
            JsonObject cell = list.get(index).getAsJsonObject();
            cells.add(new MotionItems.Area(index, cell.get("x").getAsInt(), cell.get("y").getAsInt(), cell.get("w").getAsInt(), cell.get("h").getAsInt()));
        }
        return cells;
    }

    /** Host slot to cell indices, with the still cells under -1, as the rule gives them. */
    private static Map<Integer, List<Integer>> assign(DressingHosts hosts, List<MotionItems.Area> cells, DressingHosts.Reach reach) {
        Map<Integer, List<Integer>> result = new TreeMap<>();
        for (MotionItems.Area cell : cells) {
            Integer slot = hosts.host(cell, reach, (int any) -> true);
            result.computeIfAbsent(slot == null ? -1 : slot, (Integer key) -> new ArrayList<>()).add(cell.index());
        }
        return result;
    }

    /** The same map read from the spec's per-kit lists. */
    private static Map<Integer, List<Integer>> listed(JsonObject kit) {
        Map<Integer, List<Integer>> result = new TreeMap<>();
        for (Map.Entry<String, JsonElement> host : kit.getAsJsonObject("hosts").entrySet()) {
            List<Integer> indices = new ArrayList<>();
            host.getValue().getAsJsonArray().forEach((JsonElement index) -> indices.add(index.getAsInt()));
            if (!indices.isEmpty()) {
                result.put(Integer.parseInt(host.getKey()), indices);
            }
        }
        List<Integer> still = new ArrayList<>();
        kit.getAsJsonArray("still").forEach((JsonElement index) -> still.add(index.getAsInt()));
        if (!still.isEmpty()) {
            result.put(-1, still);
        }
        return result;
    }

    @Test
    void ruleGivesTheSpecsListsForEveryKitAndMode() {
        DesignSpec design = DesignSpec.builtin();
        JsonObject spec = resource(DesignSpec.RESOURCE).getAsJsonObject("artSlots");
        JsonObject regions = resource("/art/arcane/adapt/pack/design/animation-regions.json");
        int compared = 0;
        for (String mode : List.of(DressingHosts.SINGLE, DressingHosts.FALLBACK)) {
            JsonObject kits = spec.getAsJsonObject(mode).getAsJsonObject("kits");
            assertTrue(kits.keySet().containsAll(BUILTIN), mode);
            DressingHosts hosts = design.hosts(mode);
            for (String kit : kits.keySet()) {
                JsonObject entry = kits.getAsJsonObject(kit);
                List<MotionItems.Area> cells = cells(regions, entry.get("set").getAsString());
                assertEquals(entry.get("cells").getAsInt(), cells.size(), mode + " " + kit);
                assertEquals(listed(entry), assign(hosts, cells, DESIGN_REACH), mode + " " + kit);
                compared++;
            }
        }
        assertEquals(2 * 26, compared);
    }

    @Test
    void bakeReachChangesOnlyCellsTheSpecsHostCannotDraw() {
        DesignSpec design = DesignSpec.builtin();
        JsonObject spec = resource(DesignSpec.RESOURCE).getAsJsonObject("artSlots");
        JsonObject regions = resource("/art/arcane/adapt/pack/design/animation-regions.json");
        for (String mode : List.of(DressingHosts.SINGLE, DressingHosts.FALLBACK)) {
            DressingHosts hosts = design.hosts(mode);
            JsonObject kits = spec.getAsJsonObject(mode).getAsJsonObject("kits");
            for (String kit : kits.keySet()) {
                for (MotionItems.Area cell : cells(regions, kits.getAsJsonObject(kit).get("set").getAsString())) {
                    Integer wide = hosts.host(cell, DESIGN_REACH, (int any) -> true);
                    Integer drawn = hosts.host(cell, DressingHosts.Reach.DRAWABLE, (int any) -> true);
                    if (!Objects.equals(wide, drawn)) {
                        assertFalse(DressingHosts.Reach.DRAWABLE.covers(wide, cell), mode + " " + kit + " " + cell);
                    }
                    if (drawn != null) {
                        assertTrue(MotionItems.transform(drawn, List.of(cell)) != null, mode + " " + kit + " " + cell);
                    }
                }
            }
        }
    }

    @Test
    void modesNameTheSpecsSlots() {
        DesignSpec design = DesignSpec.builtin();
        assertEquals(List.of(22, 31, 40, 85), design.hosts(DressingHosts.SINGLE).slotList());
        assertEquals(List.of(22, 31, 40, 46), design.hosts(DressingHosts.FALLBACK).slotList());
        assertTrue(design.hosts("ledger").slotList().isEmpty());
        assertEquals(design.hosts(DressingHosts.FALLBACK), design.page("A").fallbackHosts());
    }

    @Test
    void bandsPickTheirHostThenFallBackInOrder() {
        DressingHosts single = DesignSpec.builtin().hosts(DressingHosts.SINGLE);
        assertEquals(22, single.host(new MotionItems.Area(0, 100, -30, 10, 10), DressingHosts.Reach.DRAWABLE, (int any) -> true));
        assertEquals(85, single.host(new MotionItems.Area(0, 100, 220, 10, 10), DressingHosts.Reach.DRAWABLE, (int any) -> true));
        assertEquals(31, single.host(new MotionItems.Area(0, -30, 100, 10, 10), DressingHosts.Reach.DRAWABLE, (int any) -> true));
        assertEquals(40, single.host(new MotionItems.Area(0, 200, 100, 10, 10), DressingHosts.Reach.DRAWABLE, (int any) -> true));
        assertEquals(85, single.host(new MotionItems.Area(0, -30, 100, 10, 10), DressingHosts.Reach.DRAWABLE, (int slot) -> slot != 31 && slot != 22));
        assertNull(single.host(new MotionItems.Area(0, -100, 100, 10, 10), DressingHosts.Reach.DRAWABLE, (int any) -> true));
    }
}
