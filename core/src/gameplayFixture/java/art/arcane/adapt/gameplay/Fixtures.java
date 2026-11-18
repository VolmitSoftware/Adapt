package art.arcane.adapt.gameplay;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AnnotatedHandlers;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Fixtures {
    public static final String STUDIO_ACTOR = "Magic_Psycho";
    private static final String OWNER_PREFIX = "AQA";
    private static final Set<UUID> GENERATED = new HashSet<>();
    private static final Map<UUID, Loc> PENDING_TELEPORTS = new ConcurrentHashMap<>();
    private static final List<Object> LISTENERS = new ArrayList<>();
    private static FixturePlatform platform;

    private Fixtures() {
    }

    static void install(FixturePlatform installed) {
        platform = installed;
    }

    public static FixturePlatform platform() {
        return Objects.requireNonNull(platform, "Gameplay fixture platform is not installed");
    }

    public static AdaptCore core() {
        return AdaptCore.get();
    }

    public static PlatformHost host() {
        return AdaptCore.platform().host();
    }

    public static ItemOps items() {
        return host().items();
    }

    public static boolean ownsDemoState(String playerName) {
        return playerName != null && (STUDIO_ACTOR.equals(playerName) || playerName.startsWith(OWNER_PREFIX));
    }

    public static boolean owns(PlayerRef player) {
        return player != null && ownsDemoState(player.name());
    }

    public static boolean isFixtureWorld(WorldRef world) {
        return GameplayFixture.isFixtureWorld(world);
    }

    public static void register(Object listener) {
        AnnotatedHandlers.register(core().getBus(), listener);
        LISTENERS.add(listener);
    }

    static void unregisterAll() {
        AdaptCore current = AdaptCore.current();
        for (Object listener : LISTENERS) {
            if (current != null) {
                current.getBus().unregisterAll(listener);
            }
        }
        LISTENERS.clear();
    }

    public static Id id(String value) {
        return Id.parse(value.contains(":") ? value : "minecraft:" + value);
    }

    public static ItemRef item(String type, int amount) {
        return items().create(id(type), amount);
    }

    public static ItemRef item(String type) {
        return item(type, 1);
    }

    public static ItemRef enchanted(String type, String enchantment, int level) {
        ItemRef item = item(type);
        item.setEnchantment(id(enchantment), level);
        return item;
    }

    public static void give(PlayerRef player, String type, int amount) {
        player.inventory().addItem(item(type, amount));
    }

    public static void give(PlayerRef player, ItemRef item) {
        player.inventory().addItem(item);
    }

    public static BlockStateRef state(String state) {
        int open = state.indexOf('[');
        BlockStateRef result = items().blockState(id(open < 0 ? state : state.substring(0, open)));
        if (open >= 0) {
            String properties = state.substring(open + 1, state.lastIndexOf(']'));
            for (String property : properties.split(",")) {
                if (property.isBlank()) {
                    continue;
                }
                String[] pair = property.split("=", 2);
                result = result.with(pair[0].trim(), pair[1].trim());
            }
        }
        return result;
    }

    public static BlockRef block(WorldRef world, int x, int y, int z) {
        return world.blockAt(x, y, z);
    }

    public static void set(WorldRef world, int x, int y, int z, String type) {
        BlockRef block = world.blockAt(x, y, z);
        if (type.indexOf('[') >= 0) {
            block.setState(state(type), false);
        } else {
            block.setType(id(type), false);
        }
    }

    public static void fill(WorldRef world, int x1, int y1, int z1, int x2, int y2, int z2, String type) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(world, x, y, z, type);
                }
            }
        }
    }

    public static Loc loc(WorldRef world, double x, double y, double z) {
        return new Loc(world, x, y, z);
    }

    public static Loc loc(WorldRef world, double x, double y, double z, float yaw, float pitch) {
        return new Loc(world, x, y, z, yaw, pitch);
    }

    public static void teleport(EntityRef entity, Loc destination) {
        PENDING_TELEPORTS.put(entity.uuid(), destination);
        platform().teleport(entity, destination);
    }

    public static boolean consumeFixtureTeleport(EntityRef entity, Loc arrival) {
        Loc pending = PENDING_TELEPORTS.get(entity.uuid());
        if (pending == null || arrival == null || !pending.world().uid().equals(arrival.world().uid())
                || Math.abs(pending.x() - arrival.x()) > 0.001D || Math.abs(pending.y() - arrival.y()) > 0.001D
                || Math.abs(pending.z() - arrival.z()) > 0.001D) {
            return false;
        }
        return PENDING_TELEPORTS.remove(entity.uuid(), pending);
    }

    public static void teleport(EntityRef entity, WorldRef world, double x, double y, double z) {
        teleport(entity, loc(world, x, y, z));
    }

    public static EntityRef spawn(WorldRef world, String type, double x, double y, double z) {
        return world.spawn(id(type), loc(world, x, y, z), entity -> { });
    }

    public static LivingRef spawnLiving(WorldRef world, String type, double x, double y, double z) {
        return (LivingRef) spawn(world, type, x, y, z);
    }

    public static LivingRef spawnStill(WorldRef world, String type, double x, double y, double z) {
        LivingRef living = spawnLiving(world, type, x, y, z);
        living.setAi(false);
        return living;
    }

    public static void command(String command) {
        host().dispatchConsoleCommand(command);
    }

    public static void commandIn(WorldRef world, String command) {
        command("execute in " + world.key() + " run " + command);
    }

    public static void gameRule(WorldRef world, String rule, String value) {
        commandIn(world, "gamerule " + rule + " " + value);
    }

    /**
     * Runs {@code task} at {@code site} once every console command queued so far has run. Regionized servers dispatch
     * console commands on the global region, so a stage that places blocks right after a game rule command would
     * otherwise race the rule.
     */
    public static void afterCommands(Loc site, Runnable task) {
        if (!Tasks.isFoliaThreading()) {
            task.run();
            return;
        }
        Tasks.s(() -> Tasks.runAt(site, task));
    }

    public static void setBlockNbt(WorldRef world, int x, int y, int z, String stateWithNbt) {
        commandIn(world, "setblock " + x + " " + y + " " + z + " " + stateWithNbt + " replace");
    }

    public static void mergeNbt(EntityRef entity, String nbt) {
        command("data merge entity " + entity.uuid() + " " + nbt);
    }

    public static EntityRef summon(WorldRef world, String type, double x, double y, double z, String nbt) {
        String body = nbt == null || nbt.isBlank() ? "" : nbt.trim();
        if (!body.isEmpty() && (!body.startsWith("{") || !body.endsWith("}"))) {
            throw new IllegalArgumentException("Summon data must be an NBT compound: " + nbt);
        }
        return platform().summon(world, type, x, y, z, body);
    }

    public static WorldRef generatedWorld(String name, long seed, Environment environment) {
        WorldRef world = platform().generatedWorld(name, seed, environment);
        GENERATED.add(world.uid());
        return world;
    }

    public static void unloadGeneratedWorld(WorldRef world) {
        GENERATED.remove(world.uid());
        platform().unloadWorld(world);
    }

    static boolean isGeneratedWorld(WorldRef world) {
        return GENERATED.contains(world.uid());
    }

    public static void clearInventory(PlayerRef player) {
        PlayerInventoryRef inventory = player.inventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.clear(slot);
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            inventory.setArmor(slot, items().empty());
        }
        inventory.setOffHand(items().empty());
    }

    public static void tag(EntityRef entity, String tag) {
        platform().addTag(entity, tag);
    }

    public static Set<String> tags(EntityRef entity) {
        return platform().tags(entity);
    }

    public static EntityRef entity(UUID id) {
        return host().entity(id);
    }

    public static AdaptPlayer adaptPlayer(PlayerRef player) {
        return core().getAdaptServer().getPlayer(player);
    }

    public static Skill<?> skill(String name) {
        return core().getAdaptServer().getSkillRegistry().getSkill(name);
    }

    public static Adaptation<?> adaptation(String name) {
        for (Skill<?> skill : core().getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation.getName().equals(name)) {
                    return adaptation;
                }
            }
        }
        throw new IllegalArgumentException("Unknown adaptation " + name);
    }

    public static <T extends Adaptation<?>> T adaptation(Class<T> type) {
        for (Skill<?> skill : core().getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (type.isInstance(adaptation)) {
                    return type.cast(adaptation);
                }
            }
        }
        throw new IllegalStateException("Adaptation is not registered: " + type.getSimpleName());
    }
}
