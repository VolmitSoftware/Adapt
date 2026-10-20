package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
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
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.List;
import java.util.Objects;

public final class GolemLifecycleFixtures {
    private GolemLifecycleFixtures() {
    }

    public static JsonObject execute(Player player, String[] args) {
        switch (args[2]) {
            case "prepare" -> prepare(player);
            case "copper" -> copper(player);
            case "bind" -> bindServant(player);
            case "fight" -> fight(player);
            case "unlearn" -> learn(player, "tragoul-skeletal-servant", 0);
            case "snapshot" -> {
            }
            default -> throw new IllegalArgumentException("Unknown golem lifecycle action: " + args[2]);
        }
        return snapshot(player);
    }

    private static void prepare(Player player) {
        learn(player, "golem-animus-binding", 5);
        learn(player, "golem-copper-frame", 1);
        learn(player, "golem-bone-frame", 1);
        learn(player, "golem-stasis", 2);
        learn(player, "tragoul-skeletal-servant", 1);
        player.getInventory().clear();
        player.getInventory().setItem(0, new ItemStack(Material.BONE, 32));
        player.getInventory().setItem(1, GolemItems.crook());
        player.getInventory().setHeldItemSlot(0);
        player.getWorld().setTime(6000);
        player.getWorld().getBlockAt(0, 99, 2).setType(Material.STONE);
    }

    private static void copper(Player player) {
        CopperGolem golem = player.getWorld().spawn(new Location(player.getWorld(), 2.5, 100, 0.5), CopperGolem.class);
        GolemHandle handle = registry().bind(player, golem, GolemBody.COPPER, GolemCoreType.BLANK);
        handle.setMode(GolemMode.STAY);
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        handle.setHeldEncoded(GolemState.encodeItem(rod));
        Objects.requireNonNull(golem.getEquipment()).setItemInMainHand(rod);
        player.getInventory().setHeldItemSlot(8);
    }

    private static void bindServant(Player player) {
        Skeleton skeleton = player.getWorld().getEntitiesByClass(Skeleton.class).stream()
            .filter(entity -> player.getUniqueId().equals(TragoulSkeletalServant.getServantOwnerId(entity)))
            .findFirst().orElseThrow(() -> new IllegalStateException("Summon a skeletal servant first"));
        skeleton.teleport(new Location(player.getWorld(), 2.5, 100, 0.5));
        if (!ServantFrame.surrender(skeleton)) {
            throw new IllegalStateException("Servant could not leave its pack");
        }
        GolemHandle handle = registry().bind(player, skeleton, GolemBody.BONE, GolemCoreType.BLANK);
        GolemancyRuntime.bindListener().bound(player, handle);
        handle.setMode(GolemMode.STAY);
        equip(handle, GolemEquipmentSlot.MAIN_HAND, Material.IRON_SWORD, 11);
        equip(handle, GolemEquipmentSlot.CHESTPLATE, Material.IRON_CHESTPLATE, 7);
        equip(handle, GolemEquipmentSlot.HELMET, Material.IRON_HELMET, 3);
        handle.setRunes(List.of(new GolemRune(GolemRuneType.MIGHT, 1)));
    }

    private static void equip(GolemHandle handle, GolemEquipmentSlot slot, Material type, int damage) {
        ItemStack item = new ItemStack(type);
        Damageable meta = (Damageable) item.getItemMeta();
        meta.setDamage(damage);
        item.setItemMeta(meta);
        if (!handle.equip(slot, item)) {
            throw new IllegalStateException("Equipment refused: " + slot);
        }
    }

    private static void fight(Player player) {
        Mob bone = bone(player).entity();
        Husk target = player.getWorld().spawn(bone.getLocation().clone().add(1, 0, 0), Husk.class);
        target.setAI(false);
        bone.attack(target);
        bone.setNoDamageTicks(0);
        bone.damage(8, target);
        target.remove();
    }

    private static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.addProperty("maxHealth", Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue());
        result.addProperty("servants", player.getWorld().getEntitiesByClass(Skeleton.class).stream()
            .filter(entity -> player.getUniqueId().equals(TragoulSkeletalServant.getServantOwnerId(entity))).count());
        JsonArray golems = new JsonArray();
        for (GolemHandle handle : registry().loadedOf(player.getUniqueId())) {
            JsonObject golem = new JsonObject();
            golem.addProperty("id", handle.id().toString());
            golem.addProperty("entityId", handle.entity().getEntityId());
            golem.addProperty("body", handle.body().name());
            golem.addProperty("homeChest", handle.state().homeChest() != null);
            golem.addProperty("held", handle.state().held() != null);
            if (handle.entity() instanceof Skeleton skeleton) {
                golem.addProperty("burnsInDaylight", skeleton.shouldBurnInDay());
            }
            golem.add("mainHand", item(Objects.requireNonNull(handle.entity().getEquipment()).getItemInMainHand()));
            JsonObject equipment = new JsonObject();
            for (GolemEquipmentSlot slot : GolemEquipmentSlot.values()) {
                equipment.add(slot.name(), item(handle.entity().getEquipment().getItem(slot.slot())));
            }
            golem.add("equipment", equipment);
            JsonArray stored = new JsonArray();
            for (ItemStack stack : handle.equipmentItems()) {
                stored.add(item(stack));
            }
            golem.add("storedEquipment", stored);
            golems.add(golem);
        }
        result.add("golems", golems);
        JsonArray drops = new JsonArray();
        for (Item entity : player.getWorld().getEntitiesByClass(Item.class)) {
            drops.add(item(entity.getItemStack()));
        }
        result.add("drops", drops);
        JsonArray inventory = new JsonArray();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && !stack.getType().isAir()) {
                inventory.add(item(stack));
            }
        }
        result.add("inventory", inventory);
        return result;
    }

    private static JsonObject item(ItemStack stack) {
        JsonObject item = new JsonObject();
        item.addProperty("type", stack.getType().name());
        item.addProperty("amount", stack.getAmount());
        item.addProperty("damage", stack.getItemMeta() instanceof Damageable meta ? meta.getDamage() : 0);
        GolemItems.coreOf(stack).ifPresent(core -> item.addProperty("core", core.name()));
        GolemItems.runeOf(stack).ifPresent(rune -> item.addProperty("rune", rune.aspect().name()));
        item.addProperty("packed", GolemItems.packedGolemOf(stack).isPresent());
        return item;
    }

    private static GolemHandle bone(Player player) {
        return registry().loadedOf(player.getUniqueId()).stream().filter(handle -> handle.body() == GolemBody.BONE)
            .findFirst().orElseThrow(() -> new IllegalStateException("No bound bone golem"));
    }

    private static GolemRegistry registry() {
        return GolemancyRuntime.registry().orElseThrow();
    }

    private static void learn(Player player, String name, int level) {
        AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation.getName().equals(name)) {
                    runtime.getData().getSkillLine(skill.getName()).setAdaptation(adaptation, level);
                    return;
                }
            }
        }
        throw new IllegalArgumentException("Unknown adaptation: " + name);
    }
}
