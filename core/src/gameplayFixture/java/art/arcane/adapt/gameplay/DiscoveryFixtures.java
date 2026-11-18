package art.arcane.adapt.gameplay;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DiscoveryFixtures {
    private static final Set<String> STAGES = Set.of("discovery-qa-brush", "discovery-qa-keen", "discovery-qa-polymath",
            "discovery-qa-trail", "discovery-qa-villager", "discovery-qa-siphon", "discovery-qa-grind", "discovery-qa-lapis", "discovery-qa-soul", "discovery-qa-cartographer", "discovery-qa-sixth");
    private static final Set<String> EXPLORATION_STAGES = Set.of("discovery-qa-cartographer", "discovery-qa-sixth");
    private static final Id JIGSAW = Id.minecraft("jigsaw");
    private static final String NO_COLLIDE_TEAM = "adaptqa_nocollide";
    private static final List<Target> targets = new ArrayList<>();
    private static final List<PlayerRef> hiddenFrom = new ArrayList<>();
    private static PlayerRef hiddenActor;
    private static boolean noCollideTeam;
    private static WorldRef exploration;
    private static Loc structureTarget;
    private static Loc explorationPlatform;
    private static Loc explorationColumn;

    private DiscoveryFixtures() {
    }

    public static boolean stage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        if (!STAGES.contains(name)) return false;
        targets.clear();
        actor.setLevel(100);
        actor.setExpProgress(0);
        actor.setEnchantmentSeed(424242);
        actor.setFoodLevel(17);
        actor.setSaturation(0);
        Fixtures.teleport(opponent, world, 18.5, 100, 18.5);
        switch (name) {
            case "discovery-qa-cartographer", "discovery-qa-sixth" -> {
                prepareExploration(world, actor, opponent);
                if (name.equals("discovery-qa-cartographer")) Fixtures.give(actor, "compass", 1);
            }
            case "discovery-qa-brush" -> {
                Fixtures.give(actor, "brush", 1);
                Fixtures.setBlockNbt(world, 2, 100, 0, "minecraft:suspicious_sand{item:{id:\"minecraft:paper\",count:1}}");
                hideFromWatchers(actor);
            }
            case "discovery-qa-keen" -> Fixtures.set(world, 2, 100, 0, "chest");
            case "discovery-qa-polymath" -> {
                PlayerData data = Fixtures.adaptPlayer(actor).getData();
                data.getMultipliers().clear();
                data.getSkillLine("discovery").setXp(XP.getXpForLevel(5));
            }
            case "discovery-qa-trail" -> Fixtures.commandIn(world, "fillbiome -8 100 -8 8 100 8 minecraft:jagged_peaks");
            case "discovery-qa-villager" -> {
                Fixtures.give(actor, "emerald", 64);
                EntityRef villager = Fixtures.summon(world, "villager", 2.5, 100, 0.5,
                        "{NoAI:1b,Age:0,VillagerData:{profession:\"minecraft:farmer\",level:1,type:\"minecraft:plains\"},"
                                + "Offers:{Recipes:[{buy:{id:\"minecraft:emerald\",count:20},sell:{id:\"minecraft:bread\",count:1},"
                                + "uses:0,maxUses:100,xp:0,priceMultiplier:0.0f,rewardExp:0b}]}}");
                targets.add(new Target(villager.uuid(), villager.entityId(), (LivingRef) villager));
            }
            case "discovery-qa-siphon" -> {
                Fixtures.give(actor, "wooden_axe", 1);
                noCollideTeam = true;
                Fixtures.command("team add " + NO_COLLIDE_TEAM);
                Fixtures.command("team modify " + NO_COLLIDE_TEAM + " collisionRule never");
                for (int i = 0; i < 20; i++) {
                    LivingRef target = Fixtures.spawnStill(world, "husk", 2.5, 100, 0.5);
                    Fixtures.command("team join " + NO_COLLIDE_TEAM + " " + target.uuid());
                    target.setHealth(1);
                    target.setEquipment(EquipmentSlot.HEAD, Fixtures.enchanted("diamond_helmet", "protection", 2));
                    target.setEquipmentDropChance(EquipmentSlot.HEAD, 0);
                    targets.add(new Target(target.uuid(), target.entityId(), target));
                }
            }
            case "discovery-qa-grind", "discovery-qa-lapis" -> {
                boolean grind = name.equals("discovery-qa-grind");
                Fixtures.set(world, 2, 100, 0, grind ? "grindstone" : "enchanting_table");
                for (int i = 0; i < 30; i++) Fixtures.give(actor, grind
                        ? Fixtures.enchanted("iron_sword", "sharpness", 2) : Fixtures.item("iron_sword"));
                if (!grind) Fixtures.give(actor, "lapis_lazuli", 64);
            }
            case "discovery-qa-soul" -> {
                // The modded arena is the overworld: vanilla respawns within 10 blocks of its spawn (the arena centre),
                // so the actor dies outside that square and cannot pick its own death drops back up.
                Fixtures.teleport(actor, world, 0.5, 100, 16.5);
                Fixtures.set(world, 2, 100, 18, "anvil");
                actor.setHealth(2);
                Fixtures.give(actor, Fixtures.enchanted("diamond_pickaxe", "efficiency", 3));
                Fixtures.teleport(opponent, world, 2.5, 100, 16.5);
                Fixtures.give(opponent, "wooden_sword", 1);
                opponent.setFoodLevel(17);
                opponent.setSaturation(0);
            }
            default -> throw new IllegalArgumentException("Unknown discovery fixture: " + name);
        }
        return true;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        PlayerData data = Fixtures.adaptPlayer(player).getData();
        result.addProperty("xpMultiplier", data.computeXpMultiplier(player));
        result.addProperty("senses", data.getStat("discovery.sixth-sense.senses"));
        result.addProperty("food", player.foodLevel());
        Loc compass = Fixtures.platform().compassTarget(player);
        result.addProperty("compassX", compass.x());
        result.addProperty("compassZ", compass.z());
        if (structureTarget != null && exploration != null && player.world().uid().equals(exploration.uid())) {
            result.addProperty("structureX", structureTarget.x());
            result.addProperty("structureZ", structureTarget.z());
        }
        result.addProperty("polymathBoosts", data.getStat("discovery.polymath.boosts"));
        JsonObject qualifyingLevels = new JsonObject();
        for (Map.Entry<String, PlayerSkillLine> entry : data.getSkillLines().entrySet()) {
            if (entry.getValue().getLevel() >= 5) qualifyingLevels.addProperty(entry.getKey(), entry.getValue().getLevel());
        }
        result.add("qualifyingLevels", qualifyingLevels);
        result.addProperty("trailDiscoveries", data.getStat("discovery.trailblazer.discoveries"));
        result.addProperty("biomeDiscovered", data.getStat("discovery.trailblazer.biome.minecraft:jagged_peaks"));
        result.addProperty("trailBiome", player.world().blockAt(player.location()).biome().toString());
        JsonArray targetStates = new JsonArray();
        for (Target entry : targets) {
            LivingRef target = entry.resolve();
            if (!target.world().uid().equals(player.world().uid())) continue;
            JsonObject state = new JsonObject();
            state.addProperty("entityId", entry.entityId());
            state.addProperty("dead", target.isDead());
            state.addProperty("health", target.health());
            targetStates.add(state);
        }
        result.add("targets", targetStates);
        return result;
    }

    // Paper/Folia 26.3 broadcast the live brush stack in set_equipment when brushing starts, then
    // ItemStack.useOn restores its component patch in place while Netty may still be encoding it,
    // which kicks the watching players. Nobody else tracks the actor while it brushes.
    private static void hideFromWatchers(PlayerRef actor) {
        List<PlayerRef> watchers = new ArrayList<>();
        for (PlayerRef viewer : Fixtures.host().onlinePlayers()) {
            if (!viewer.uuid().equals(actor.uuid())) watchers.add(viewer);
        }
        hiddenActor = actor;
        hiddenFrom.addAll(watchers);
        Tasks.runEntity(actor, () -> {
            for (PlayerRef viewer : watchers) AdaptCore.platform().viewers().hideEntity(viewer, actor);
        });
    }

    public static void reset() {
        if (hiddenActor == null) return;
        PlayerRef actor = hiddenActor;
        List<PlayerRef> watchers = List.copyOf(hiddenFrom);
        hiddenActor = null;
        hiddenFrom.clear();
        Tasks.runEntity(actor, () -> {
            for (PlayerRef viewer : watchers) AdaptCore.platform().viewers().showEntity(viewer, actor);
        });
    }

    public static void cleanup() {
        reset();
        if (noCollideTeam) {
            Fixtures.command("team remove " + NO_COLLIDE_TEAM);
            noCollideTeam = false;
        }
        if (exploration == null) return;
        Fixtures.unloadGeneratedWorld(exploration);
        exploration = null;
        structureTarget = null;
        explorationPlatform = null;
        explorationColumn = null;
    }

    static boolean usesExploration(String name) {
        return EXPLORATION_STAGES.contains(name);
    }

    static Loc explorationSite(WorldRef arena) {
        if (exploration == null) {
            exploration = Fixtures.generatedWorld(arena.name() + "_discovery", 424242L, Environment.NORMAL);
            Fixtures.gameRule(exploration, "spawn_mobs", "false");
            Fixtures.gameRule(exploration, "natural_health_regeneration", "false");
        }
        if (explorationPlatform != null) return explorationPlatform;
        if (explorationColumn == null) {
            Loc spawn = exploration.spawnLocation();
            if (!Tasks.isOwnedByCurrentRegion(spawn)) return spawn;
            Loc found = exploration.locateNearestStructure(spawn, JIGSAW, 128, false);
            if (found == null) throw new IllegalStateException("Generated discovery world has no Jigsaw structure within 128 chunks");
            explorationColumn = Fixtures.loc(exploration, found.blockX() + 96 + 0.5, exploration.maxHeight() - 8, found.blockZ() + 0.5);
        }
        return explorationColumn;
    }

    private static void prepareExploration(WorldRef arena, PlayerRef actor, PlayerRef opponent) {
        if (explorationPlatform == null) {
            Loc column = explorationSite(arena);
            int x = column.blockX();
            int z = column.blockZ();
            int y = Math.min(exploration.maxHeight() - 8, Math.max(160, exploration.highestBlockY(x, z) + 12));
            explorationPlatform = Fixtures.loc(exploration, x + 0.5, y, z + 0.5);
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    Fixtures.set(exploration, x + dx, y - 1, z + dz, "stone");
                    for (int dy = 0; dy <= 4; dy++) Fixtures.set(exploration, x + dx, y + dy, z + dz, "air");
                }
            }
            Loc nearest = exploration.locateNearestStructure(explorationPlatform, JIGSAW, 88, false);
            structureTarget = Objects.requireNonNull(nearest, "Generated structure vanished during exploration setup");
        }
        Fixtures.teleport(actor, explorationPlatform);
        Fixtures.teleport(opponent, explorationPlatform.add(4, 0, 4));
        actor.setCompassTarget(exploration.spawnLocation());
    }

    private record Target(UUID id, int entityId, LivingRef handle) {
        LivingRef resolve() {
            return Fixtures.entity(id) instanceof LivingRef live ? live : handle;
        }
    }
}
