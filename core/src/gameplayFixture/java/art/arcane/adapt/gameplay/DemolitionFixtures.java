package art.arcane.adapt.gameplay;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Objects;

public final class DemolitionFixtures {
    private static final String TOOL_NAME = "Groundwork Tool";
    private static final String BLOCK_NAME = "Restitution Stone";
    private static final Map<String, Integer> LEARNED_PROFILE = Map.ofEntries(
        Map.entry("agility-air-dash", 4), Map.entry("agility-super-jump", 4), Map.entry("agility-wall-jump", 5),
        Map.entry("architect-foundation", 5), Map.entry("architect-demolition", 5), Map.entry("axe-chop", 5),
        Map.entry("axe-throwing-axe", 4), Map.entry("axe-wood-veinminer", 5), Map.entry("discovery-archaeologist", 6),
        Map.entry("discovery-better-mending", 6), Map.entry("discovery-insight", 4), Map.entry("discovery-keen-eye", 5),
        Map.entry("discovery-sixth-sense", 5), Map.entry("discovery-villager-att", 5), Map.entry("hunter-blood-trail", 5),
        Map.entry("pickaxe-autosmelt", 4), Map.entry("pickaxe-tunnel-bore", 3), Map.entry("pickaxe-unbreakable-pact", 3),
        Map.entry("excavation-grave-digger", 5), Map.entry("excavation-haste", 3), Map.entry("excavation-tunneler", 5)
    );

    private DemolitionFixtures() {
    }

    public static JsonObject execute(PlayerRef player, String[] args) {
        if (args[2].equals("prepare")) prepare(player, args[3]);
        if (args[2].equals("soil")) Fixtures.set(player.world(), 2, 99, 0, "dirt");
        if (args[2].equals("profile")) applyProfile(player);
        return snapshot(player);
    }

    private static void applyProfile(PlayerRef player) {
        AdaptPlayer runtime = Objects.requireNonNull(Fixtures.adaptPlayer(player));
        int matched = 0;
        for (Skill<?> skill : Fixtures.core().getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                Integer configured = LEARNED_PROFILE.get(adaptation.getName());
                int level = configured == null ? 0 : configured;
                if (level > adaptation.getMaxLevel()) throw new IllegalStateException("Profile level exceeds " + adaptation.getName());
                runtime.getData().getSkillLine(skill.getName()).setAdaptation(adaptation, level);
                if (configured != null) matched++;
            }
        }
        if (matched != LEARNED_PROFILE.size()) throw new IllegalStateException("The complete learned profile was not found");
    }

    private static void prepare(PlayerRef player, String mode) {
        WorldRef world = player.world();
        if (!Fixtures.isFixtureWorld(world)) throw new IllegalStateException("Demolition fixture requires the gameplay arena");
        for (EntityRef entity : world.entities()) {
            if (entity instanceof ItemEntityRef) entity.remove();
        }
        Fixtures.clearInventory(player);
        player.inventory().setHeldSlot(0);
        Fixtures.teleport(player, world, 0.5, 100, 0.5);
        PlayerRef opponent = GameplayFixture.get().opponent();
        if (opponent != null && !opponent.uuid().equals(player.uuid())) {
            Fixtures.teleport(opponent, world, -6.5, 100, 0.5);
        }
        Fixtures.set(world, 2, 100, 0, "air");
        Fixtures.set(world, 2, 100, 1, "air");
        Fixtures.set(world, 2, 100, 2, "air");
        Fixtures.set(world, 2, 99, 0, "dirt");
        String material = switch (mode) {
            case "path" -> "diamond_shovel";
            case "till" -> "diamond_hoe";
            case "placed" -> "stone";
            default -> throw new IllegalArgumentException("Unknown demolition fixture mode " + mode);
        };
        ItemRef item = Fixtures.item(material);
        item.setDisplayName(material.equals("stone") ? BLOCK_NAME : TOOL_NAME);
        item.setDamage(37);
        player.inventory().setItem(0, item);
        if (mode.equals("placed")) Fixtures.set(world, 2, 100, 2, "stone");
    }

    private static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        JsonArray items = new JsonArray();
        for (ItemRef item : player.inventory().contents()) add(items, item, "inventory");
        for (EntityRef entity : player.world().entities()) {
            if (entity instanceof ItemEntityRef dropped) add(items, dropped.item(), "dropped");
        }
        result.add("items", items);
        result.addProperty("soil", FixtureItems.material(player.world().blockAt(2, 99, 0).type()));
        result.addProperty("placed", FixtureItems.material(player.world().blockAt(2, 100, 1).type()));
        JsonObject learned = new JsonObject();
        AdaptPlayer runtime = Objects.requireNonNull(Fixtures.adaptPlayer(player));
        for (Skill<?> skill : Fixtures.core().getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                int level = adaptation.getLevel(runtime);
                if (level > 0) learned.addProperty(adaptation.getName(), level);
            }
        }
        result.add("learned", learned);
        return result;
    }

    private static void add(JsonArray items, ItemRef item, String source) {
        if (item == null || item.isEmpty() || item.amount() <= 0) return;
        JsonObject entry = new JsonObject();
        entry.addProperty("material", FixtureItems.material(item.type()));
        entry.addProperty("amount", item.amount());
        entry.addProperty("source", source);
        entry.addProperty("name", FixtureItems.name(item));
        entry.addProperty("damage", item.damage());
        items.add(entry);
    }
}
