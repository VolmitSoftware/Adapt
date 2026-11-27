package art.arcane.adapt.gameplay;

import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.content.item.multiItems.OmniTool;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Set;

public final class EarthFixtures {
    private static final Set<String> STAGES = Set.of("earth-burrow", "earth-chisel", "earth-trophy", "earth-quarry", "earth-spelunker", "earth-omni", "earth-repair", "earth-graves", "earth-treasure", "earth-seismic");

    private EarthFixtures() {
    }

    public static boolean stage(String name, WorldRef arena, PlayerRef actor, PlayerRef opponent) {
        if (!STAGES.contains(name)) return false;
        actor.setFoodLevel(20);
        actor.setSaturation(0);
        Fixtures.give(actor, "diamond_pickaxe", 1);
        switch (name) {
            case "earth-graves", "earth-treasure", "earth-seismic" -> {
                Fixtures.give(actor, "diamond_shovel", 1);
                if (name.equals("earth-seismic")) Fixtures.set(arena, 4, 100, 1, "diamond_ore");
                refill(arena, name.equals("earth-treasure") ? "sand" : "dirt");
            }
            case "earth-burrow" -> {
                Fixtures.give(actor, "diamond_shovel", 1);
                Fixtures.set(arena, 0, 93, 0, "stone");
                for (int y = 94; y <= 99; y++) Fixtures.set(arena, 0, y, 0, "dirt");
            }
            case "earth-trophy" -> {
                Fixtures.set(arena, 3, 100, 0, "dragon_head");
                XpProvenance.clearPlacementRecord(arena.blockAt(3, 100, 0));
                actor.giveExpLevels(-(actor.level() + 1));
                actor.setLevel(0);
                actor.setExpProgress(0);
            }
            case "earth-quarry", "earth-spelunker" -> {
                Fixtures.set(arena, 3, 100, 0, "stone");
                Fixtures.set(arena, 3, 100, 1, "diamond_ore");
                Fixtures.give(actor, "glow_berries", 3);
                Fixtures.give(actor, "diamond_ore", 1);
            }
            case "earth-omni" -> {
                Fixtures.give(actor, "diamond_axe", 1);
                Fixtures.set(arena, 3, 100, 0, "oak_log");
            }
            case "earth-repair" -> {
                Fixtures.clearInventory(actor);
                ItemRef tool = Fixtures.item("diamond_pickaxe");
                tool.setDamage(200);
                Fixtures.give(actor, tool);
                for (int y = 100; y <= 102; y++) {
                    for (int z = -2; z <= 2; z++) Fixtures.set(arena, 3, y, z, "stone");
                }
            }
            default -> Fixtures.set(arena, 3, 100, 0, "diamond_ore");
        }
        Fixtures.teleport(opponent, arena, -5.5, 100, 0.5);
        return true;
    }

    public static void refill(WorldRef arena, String soil) {
        for (int y = 100; y <= 102; y++) {
            for (int z = 0; z <= 2; z++) Fixtures.set(arena, 3, y, z, soil);
        }
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        JsonArray tools = new JsonArray();
        OmniTool omni = new OmniTool();
        for (ItemRef item : player.inventory().contents()) {
            if (item == null || item.isEmpty()) continue;
            JsonObject tool = new JsonObject();
            tool.addProperty("type", item.type().toString());
            tool.addProperty("damage", item.damage());
            tool.addProperty("maxDamage", item.maxDamage());
            tool.addProperty("components", omni.explode(item).size());
            tools.add(tool);
        }
        JsonArray drops = new JsonArray();
        for (EntityRef entity : player.world().entities()) {
            if (!(entity instanceof ItemEntityRef item)) continue;
            JsonObject drop = new JsonObject();
            drop.addProperty("type", item.item().type().toString());
            drop.addProperty("amount", item.item().amount());
            drops.add(drop);
        }
        result.add("drops", drops);
        result.add("tools", tools);
        result.addProperty("vanillaXp", player.totalExperience());
        return result;
    }
}
