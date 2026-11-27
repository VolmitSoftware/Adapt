package art.arcane.adapt.gameplay;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import art.arcane.adapt.content.item.ChronoTimeBottle;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.FurnaceRef;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ChronosFixtures {
    private static final JsonArray interactions = new JsonArray();
    private static final Id AMETHYST_SHARD = Id.minecraft("amethyst_shard");
    private static final Id COW = Id.minecraft("cow");
    private static UUID actorId;
    private static UUID targetId;
    private static final List<Loc> FURNACES = new ArrayList<>();

    private ChronosFixtures() {
    }

    public static void install() {
        Fixtures.register(new ChronosFixtures());
    }

    public static boolean stage(String name, WorldRef arena, PlayerRef actor, PlayerRef opponent) {
        if (name.equals("chronos-recall")) {
            Fixtures.give(actor, "clock", 2);
            Fixtures.gameRule(arena, "natural_health_regeneration", "false");
            return true;
        }
        if (name.equals("chronos-stasis") || name.equals("chronos-bomb")) {
            actorId = actor.uuid();
            while (interactions.size() > 0) interactions.remove(0);
            Fixtures.give(actor, name.equals("chronos-stasis")
                    ? Fixtures.item("amethyst_shard", 2) : ChronoTimeBombItem.withData());
            EntityRef target = Fixtures.spawn(arena, "cow", 2.5, 100, 2.5);
            targetId = target.uuid();
            return true;
        }
        if (name.equals("chronos-bottle")) {
            Fixtures.give(actor, ChronoTimeBottle.withStoredSeconds(120));
            Fixtures.gameRule(arena, "random_tick_speed", "0");
            Fixtures.set(arena, 2, 99, 0, "farmland[moisture=7]");
            Fixtures.set(arena, 2, 100, 0, "wheat");
            return true;
        }
        if (name.equals("adapt:chronos/accelerate")) {
            FURNACES.clear();
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    if (Math.abs(x) <= 1 && Math.abs(z) <= 1) continue;
                    Fixtures.set(arena, x, 100, z, "furnace");
                    BlockRef furnace = arena.blockAt(x, 100, z);
                    furnace.container().setItem(0, Fixtures.item("raw_iron", 64));
                    furnace.container().setItem(1, Fixtures.item("coal", 64));
                    FURNACES.add(furnace.location());
                }
            }
            return true;
        }
        if (name.equals("chronos-fall")) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -10; z <= 3; z++) {
                    Fixtures.set(arena, x, 99, z, "stone");
                    Fixtures.set(arena, x, 100, z, "water");
                }
            }
            Fixtures.set(arena, 0, 129, 0, "stone");
            Fixtures.teleport(actor, arena, 0.5, 130, 0.5);
            Fixtures.give(actor, "clock", 1);
            return true;
        }
        if (!name.equals("chronos-combat") && !name.equals("chronos-lethal")) {
            return false;
        }
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        if (name.equals("chronos-lethal")) actor.setHealth(1);
        Fixtures.give(actor, "iron_sword", 1);
        Fixtures.give(opponent, "iron_sword", 1);
        return true;
    }

    @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void beforeInteraction(PlayerInteractEvent event) {
        recordInteraction(event, "before");
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void afterInteraction(PlayerInteractEvent event) {
        recordInteraction(event, "after");
    }

    private void recordInteraction(PlayerInteractEvent event, String phase) {
        PlayerRef player = event.player();
        ItemRef item = event.item();
        if (!player.uuid().equals(actorId) || item == null || item.isEmpty()
                || !item.type().equals(AMETHYST_SHARD) && !ChronoTimeBombItem.isBindableItem(item)) return;
        JsonObject entry = new JsonObject();
        entry.addProperty("phase", phase);
        entry.addProperty("action", event.action().name());
        entry.addProperty("hand", event.hand() == null ? "null" : event.hand() == Hand.MAIN_HAND ? "HAND" : "OFF_HAND");
        entry.addProperty("blockUse", event.useBlock().name());
        entry.addProperty("itemUse", event.useItem().name());
        entry.addProperty("sneaking", player.isSneaking());
        for (Skill<?> skill : Fixtures.core().getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (!adaptation.getId().toString().equals(item.type().equals(AMETHYST_SHARD) ? "adapt:chronos/stasis_field" : "adapt:chronos/time_bomb")) continue;
                entry.addProperty("activeLevel", adaptation.getActiveLevel(player));
                entry.addProperty("canInteract", adaptation.canInteract(player, player.location()));
            }
        }
        if (interactions.size() >= 32) interactions.remove(0);
        interactions.add(entry);
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        result.add("interactions", interactions);
        double stored = 0;
        for (ItemRef item : player.inventory().contents()) {
            if (item != null && !item.isEmpty() && ChronoTimeBottle.isBindableItem(item)) stored += ChronoTimeBottle.getStoredSeconds(item);
        }
        result.addProperty("storedSeconds", stored);
        BlockStateRef crop = player.world().blockAt(2, 100, 0).state();
        String age = crop.property("age");
        if (age != null) {
            result.addProperty("cropAge", Integer.parseInt(age));
        }
        int cooked = 0;
        int progress = 0;
        for (Loc location : FURNACES) {
            if (!location.world().uid().equals(player.world().uid())) continue;
            BlockRef block = location.world().blockAt(location);
            Optional<FurnaceRef> furnace = block.furnace();
            if (furnace.isEmpty()) continue;
            ItemRef output = block.container().item(2);
            cooked += output == null || output.isEmpty() ? 0 : output.amount();
            progress += furnace.get().cookTime();
        }
        result.addProperty("cooked", cooked);
        result.addProperty("cookProgress", progress);
        result.addProperty("serverTick", Fixtures.host().currentTick());
        EntityRef target = targetId == null ? null : Fixtures.entity(targetId);
        if (target instanceof LivingRef cow && cow.type().equals(COW)) {
            result.addProperty("ai", cow.hasAi());
            result.addProperty("movementSpeed", cow.attributeValue(Id.minecraft("movement_speed")));
            Id jumpStrength = Id.minecraft("jump_strength");
            if (cow.hasAttribute(jumpStrength)) {
                result.addProperty("jumpStrength", cow.attributeValue(jumpStrength));
            }
            result.addProperty("targetId", cow.entityId());
        }
        return result;
    }
}
