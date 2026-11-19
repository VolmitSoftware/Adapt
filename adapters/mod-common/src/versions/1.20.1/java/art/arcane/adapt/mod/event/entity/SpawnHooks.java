package art.arcane.adapt.mod.event.entity;

import art.arcane.adapt.mod.event.CauseStack;
import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent.Reason;
import art.arcane.adapt.platform.event.entity.ItemSpawnEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * Creature and item spawns. Vanilla's spawn reason is read where the entity is created or finalized and
 * kept on the entity, then mapped to Paper's reasons with the context vanilla does not pass on: which
 * mob converted, and whether a golem pattern was just completed.
 */
public final class SpawnHooks {
    private static final Set<String> RAIDERS = Set.of("pillager", "vindicator", "evoker", "witch", "ravager", "illusioner");
    private static final CauseStack<Mob> CONVERSIONS = new CauseStack<>();
    private static int golemBuilds;

    private SpawnHooks() {
    }

    public static void created(Entity entity, MobSpawnType reason) {
        if (entity instanceof SpawnTagged tagged && reason != null) {
            tagged.adapt$spawnReason(reason, reason(entity, reason));
        }
    }

    /** An entity built without a reason: a golem pattern being completed or a mob converting gives it one. */
    public static void created(Entity entity) {
        if (golemBuilds > 0) {
            created(entity, MobSpawnType.TRIGGERED);
        } else if (CONVERSIONS.current() != null) {
            created(entity, MobSpawnType.CONVERSION);
        }
    }

    /** A later finalize with the reason already recorded keeps the context read at creation. */
    public static void finalizing(Mob mob, MobSpawnType reason) {
        if (reason == null || !(mob instanceof SpawnTagged tagged) || tagged.adapt$vanillaSpawnReason() == reason) {
            return;
        }
        tagged.adapt$spawnReason(reason, reason(mob, reason));
    }

    public static void converting(Mob source) {
        CONVERSIONS.push(source, false);
    }

    public static void converted() {
        CONVERSIONS.pop();
    }

    public static void golemBuildStarted() {
        golemBuilds++;
    }

    public static void golemBuildEnded() {
        golemBuilds = Math.max(0, golemBuilds - 1);
    }

    /** Called from the level's add-entity hook. True when a handler cancelled the spawn. */
    public static boolean spawning(ServerLevel level, Entity entity) {
        if (entity instanceof ItemEntity item) {
            if (!ModEvents.listening(ItemSpawnEvent.class)) {
                return false;
            }
            return ModEvents.post(new ItemSpawnEvent((ItemEntityRef) ModRefs.entity(item),
                ModRefs.loc(level, item.position(), item.getYRot(), item.getXRot()))).isCancelled();
        }
        if (!(entity instanceof LivingEntity living) || entity instanceof Player || !ModEvents.listening(CreatureSpawnEvent.class)) {
            return false;
        }
        Reason reason = entity instanceof SpawnTagged tagged && tagged.adapt$spawnReason() != null ? tagged.adapt$spawnReason() : Reason.DEFAULT;
        return ModEvents.post(new CreatureSpawnEvent(ModRefs.living(living),
            ModRefs.loc(level, living.position(), living.getYRot(), living.getXRot()), reason)).isCancelled();
    }

    static Reason reason(Entity entity, MobSpawnType reason) {
        String type = type(entity);
        return switch (reason) {
            case NATURAL -> Reason.NATURAL;
            case CHUNK_GENERATION -> Reason.CHUNK_GEN;
            case STRUCTURE -> type.equals("zombified_piglin") ? Reason.NETHER_PORTAL : Reason.CHUNK_GEN;
            case SPAWNER -> Reason.SPAWNER;
            case BREEDING -> type.equals("allay") ? Reason.DUPLICATION : Reason.BREEDING;
            case MOB_SUMMONED -> switch (type) {
                case "vex" -> Reason.SPELL;
                case "iron_golem" -> Reason.VILLAGE_DEFENSE;
                default -> Reason.DEFAULT;
            };
            case JOCKEY -> Reason.JOCKEY;
            case EVENT -> event(type);
            case CONVERSION -> conversion();
            case REINFORCEMENT -> Reason.REINFORCEMENTS;
            case TRIGGERED -> triggered(type);
            case BUCKET -> Reason.BUCKET;
            case SPAWN_EGG -> Reason.SPAWNER_EGG;
            case COMMAND -> Reason.COMMAND;
            case DISPENSER -> Reason.DISPENSE_EGG;
            case PATROL -> Reason.PATROL;
            default -> Reason.DEFAULT;
        };
    }

    private static Reason event(String type) {
        if (RAIDERS.contains(type)) {
            return Reason.RAID;
        }
        return switch (type) {
            case "zombie" -> Reason.VILLAGE_INVASION;
            case "skeleton_horse" -> Reason.LIGHTNING;
            case "wandering_trader", "trader_llama" -> Reason.NATURAL;
            default -> Reason.DEFAULT;
        };
    }

    private static Reason conversion() {
        Mob source = CONVERSIONS.current();
        if (source == null) {
            return Reason.DEFAULT;
        }
        return switch (type(source)) {
            case "villager" -> Reason.INFECTION;
            case "zombie_villager" -> Reason.CURED;
            case "zombie", "husk" -> Reason.DROWNED;
            case "piglin", "piglin_brute", "hoglin" -> Reason.PIGLIN_ZOMBIFIED;
            case "skeleton" -> Reason.FROZEN;
            case "tadpole" -> Reason.METAMORPHOSIS;
            case "pig" -> Reason.LIGHTNING;
            case "mooshroom" -> Reason.SHEARED;
            default -> Reason.DEFAULT;
        };
    }

    private static Reason triggered(String type) {
        if (golemBuilds > 0) {
            switch (type) {
                case "snow_golem":
                    return Reason.BUILD_SNOWMAN;
                case "iron_golem":
                    return Reason.BUILD_IRONGOLEM;
                case "copper_golem":
                    return Reason.BUILD_COPPERGOLEM;
                default:
                    break;
            }
        }
        if (CONVERSIONS.current() != null) {
            return Reason.SLIME_SPLIT;
        }
        return switch (type) {
            case "wither" -> Reason.BUILD_WITHER;
            case "chicken" -> Reason.EGG;
            case "endermite" -> Reason.ENDER_PEARL;
            case "silverfish" -> Reason.SILVERFISH_BLOCK;
            case "slime" -> Reason.POTION_EFFECT;
            case "skeleton_horse", "skeleton" -> Reason.TRAP;
            default -> Reason.DEFAULT;
        };
    }

    private static String type(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
    }
}
