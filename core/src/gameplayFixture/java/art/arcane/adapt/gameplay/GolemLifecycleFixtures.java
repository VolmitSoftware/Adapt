package art.arcane.adapt.gameplay;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemEquipmentSlot;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.support.PlatformSupport;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public final class GolemLifecycleFixtures {
    private static final Id SKELETON = Id.minecraft("skeleton");
    private static final Id MAX_HEALTH = Id.minecraft("max_health");

    private GolemLifecycleFixtures() {
    }

    public static JsonObject execute(PlayerRef player, String[] args) {
        switch (args[2]) {
            case "prepare" -> prepare(player);
            case "copper" -> copper(player);
            case "bind" -> bindServant(player);
            case "fight" -> fight(player);
            case "unlearn" -> GolemControlsFixtures.learn(player, "tragoul-skeletal-servant", 0);
            case "snapshot" -> {
            }
            default -> throw new IllegalArgumentException("Unknown golem lifecycle action: " + args[2]);
        }
        return snapshot(player);
    }

    private static void prepare(PlayerRef player) {
        GolemControlsFixtures.learn(player, "golem-animus-binding", 5);
        if (PlatformSupport.leftOut().stream().noneMatch((PlatformSupport.LeftOut out) -> out.name().equals("golem-copper-frame"))) {
            GolemControlsFixtures.learn(player, "golem-copper-frame", 1);
        }
        GolemControlsFixtures.learn(player, "golem-bone-frame", 1);
        GolemControlsFixtures.learn(player, "golem-stasis", 2);
        GolemControlsFixtures.learn(player, "tragoul-skeletal-servant", 1);
        Fixtures.clearInventory(player);
        player.inventory().setItem(0, Fixtures.item("bone", 32));
        player.inventory().setItem(1, GolemItems.crook());
        player.inventory().setHeldSlot(0);
        Fixtures.commandIn(player.world(), "time set 6000");
        Fixtures.block(player.world(), 0, 99, 2).setType(Fixtures.id("stone"), true);
    }

    private static void copper(PlayerRef player) {
        MobRef golem = (MobRef) Fixtures.spawn(player.world(), "copper_golem", 2.5, 100, 0.5);
        GolemHandle handle = registry().bind(player, golem, GolemBody.COPPER, GolemCoreType.BLANK);
        handle.setMode(GolemMode.STAY);
        ItemRef rod = Fixtures.item("fishing_rod");
        handle.setHeldEncoded(GolemState.encodeItem(rod));
        golem.setEquipment(EquipmentSlot.MAIN_HAND, rod);
        player.inventory().setHeldSlot(8);
    }

    private static List<MobRef> servants(PlayerRef player) {
        List<MobRef> servants = new ArrayList<>();
        for (EntityRef entity : player.world().entities()) {
            if (entity.type().equals(SKELETON) && entity instanceof MobRef mob
                && player.uuid().equals(TragoulSkeletalServant.getServantOwnerId(entity))) {
                servants.add(mob);
            }
        }
        return servants;
    }

    private static void bindServant(PlayerRef player) {
        MobRef skeleton = servants(player).stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("Summon a skeletal servant first"));
        Fixtures.teleport(skeleton, player.world(), 2.5, 100, 0.5);
        if (!ServantFrame.surrender(skeleton)) {
            throw new IllegalStateException("Servant could not leave its pack");
        }
        GolemHandle handle = registry().bind(player, skeleton, GolemBody.BONE, GolemCoreType.BLANK);
        GolemancyRuntime.bindListener().bound(player, handle);
        handle.setMode(GolemMode.STAY);
        equip(handle, GolemEquipmentSlot.MAIN_HAND, "iron_sword", 11);
        equip(handle, GolemEquipmentSlot.CHESTPLATE, "iron_chestplate", 7);
        equip(handle, GolemEquipmentSlot.HELMET, "iron_helmet", 3);
        handle.setRunes(List.of(new GolemRune(GolemRuneType.MIGHT, 1)));
    }

    private static void equip(GolemHandle handle, GolemEquipmentSlot slot, String type, int damage) {
        ItemRef item = Fixtures.item(type);
        item.setDamage(damage);
        if (!handle.equip(slot, item)) {
            throw new IllegalStateException("Equipment refused: " + slot);
        }
    }

    private static void fight(PlayerRef player) {
        MobRef bone = bone(player).entity();
        Loc at = bone.location();
        LivingRef target = Fixtures.spawnStill(player.world(), "husk", at.x() + 1, at.y(), at.z());
        Fixtures.platform().meleeAttack(bone, target);
        bone.setNoDamageTicks(0);
        bone.damage(8, target);
        target.remove();
    }

    private static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        result.addProperty("maxHealth", player.attributeValue(MAX_HEALTH));
        result.addProperty("servants", (long) servants(player).size());
        JsonArray golems = new JsonArray();
        for (GolemHandle handle : registry().loadedOf(player.uuid())) {
            JsonObject golem = new JsonObject();
            golem.addProperty("id", handle.id().toString());
            golem.addProperty("entityId", handle.entity().entityId());
            golem.addProperty("body", handle.body().name());
            golem.addProperty("homeChest", handle.state().homeChest() != null);
            golem.addProperty("held", handle.state().held() != null);
            if (handle.entity().type().equals(SKELETON)) {
                golem.addProperty("burnsInDaylight", Fixtures.platform().burnsInDaylight(handle.entity()));
            }
            golem.add("mainHand", item(handle.entity().equipment(EquipmentSlot.MAIN_HAND)));
            JsonObject equipment = new JsonObject();
            for (GolemEquipmentSlot slot : GolemEquipmentSlot.values()) {
                equipment.add(slot.name(), item(handle.entity().equipment(slot.slot())));
            }
            golem.add("equipment", equipment);
            JsonArray stored = new JsonArray();
            for (ItemRef stack : handle.equipmentItems()) {
                stored.add(item(stack));
            }
            golem.add("storedEquipment", stored);
            golems.add(golem);
        }
        result.add("golems", golems);
        JsonArray drops = new JsonArray();
        for (EntityRef entity : player.world().entities()) {
            if (entity instanceof ItemEntityRef drop) {
                drops.add(item(drop.item()));
            }
        }
        result.add("drops", drops);
        JsonArray inventory = new JsonArray();
        for (int slot = 0; slot < player.inventory().size(); slot++) {
            ItemRef stack = player.inventory().item(slot);
            if (stack != null && !stack.isEmpty()) {
                inventory.add(item(stack));
            }
        }
        result.add("inventory", inventory);
        return result;
    }

    private static JsonObject item(ItemRef stack) {
        JsonObject item = new JsonObject();
        boolean empty = stack == null || stack.isEmpty();
        item.addProperty("type", empty ? "AIR" : GolemControlsFixtures.name(stack.type()));
        item.addProperty("amount", empty ? 0 : stack.amount());
        item.addProperty("damage", empty || stack.maxDamage() <= 0 ? 0 : stack.damage());
        if (!empty) {
            GolemItems.coreOf(stack).ifPresent(core -> item.addProperty("core", core.name()));
            GolemItems.runeOf(stack).ifPresent(rune -> item.addProperty("rune", rune.aspect().name()));
        }
        item.addProperty("packed", !empty && GolemItems.packedGolemOf(stack).isPresent());
        if (!empty) {
            GolemItems.packedGolemOf(stack).ifPresent(packed -> item.add("packedEquipment", packedEquipment(packed.state())));
        }
        return item;
    }

    private static JsonObject packedEquipment(String state) {
        JsonObject stored = JsonParser.parseString(state).getAsJsonObject().getAsJsonObject("equipment");
        JsonObject equipment = new JsonObject();
        for (GolemEquipmentSlot slot : GolemEquipmentSlot.values()) {
            if (stored != null && stored.has(slot.id())) {
                equipment.add(slot.name(), item(GolemState.decodeItem(stored.get(slot.id()).getAsString())));
            }
        }
        return equipment;
    }

    private static GolemHandle bone(PlayerRef player) {
        return registry().loadedOf(player.uuid()).stream().filter(handle -> handle.body() == GolemBody.BONE)
            .findFirst().orElseThrow(() -> new IllegalStateException("No bound bone golem"));
    }

    private static GolemRegistry registry() {
        return GolemancyRuntime.registry().orElseThrow();
    }
}
