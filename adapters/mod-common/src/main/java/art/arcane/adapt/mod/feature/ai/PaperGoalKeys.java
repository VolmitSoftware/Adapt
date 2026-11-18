package art.arcane.adapt.mod.feature.ai;

import art.arcane.adapt.platform.value.Id;
import com.google.common.base.CaseFormat;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vanilla goal keys exactly as Paper's MobGoalHelper derives them, so golemancy's goal lists match on every
 * platform: the class name minus Goal/TargetGoal/Abstract, prefixed with the Bukkit mob type the goal's
 * constructor takes, in snake case.
 */
final class PaperGoalKeys {
    private static final String RANGED_ENTITY = "RangedEntity";
    private static final Map<String, String> BUKKIT_NAMES = Map.ofEntries(
            Map.entry("AgeableMob", "Ageable"),
            Map.entry("AmbientCreature", "Ambient"),
            Map.entry("Animal", "Animals"),
            Map.entry("PathfinderMob", "Creature"),
            Map.entry("AbstractFish", "Fish"),
            Map.entry("AbstractSchoolingFish", "SchoolableFish"),
            Map.entry("AbstractGolem", "Golem"),
            Map.entry("AbstractChestedHorse", "ChestedHorse"),
            Map.entry("AbstractIllager", "Illager"),
            Map.entry("SpellcasterIllager", "Spellcaster"),
            Map.entry("PatrollingMonster", "Raider"),
            Map.entry("ShoulderRidingEntity", "Parrot"),
            Map.entry("ZombifiedPiglin", "PigZombie"),
            Map.entry("Pufferfish", "PufferFish"),
            Map.entry("SnowGolem", "Snowman"),
            Map.entry("TamableAnimal", "Tameable"),
            Map.entry("WaterAnimal", "WaterMob"),
            Map.entry("WitherBoss", "Wither"),
            Map.entry("AbstractPiglin", "PiglinAbstract"),
            Map.entry("AgeableWaterCreature", "Squid"));
    private static final Set<String> NO_SPECIFIER = Set.of(
            "Mob", "Creature", "Animals", RANGED_ENTITY, "Tameable", "Monster", "PufferFish", "AbstractCubeMob");
    private static final Map<String, String> RENAMES = Map.of(
            "AbstractSkeleton$1", "AbstractSkeletonMelee",
            "TraderLlama$TraderLlamaDefendWanderingTraderGoal", "TraderLlamaDefendWanderingTraderGoal",
            "AbstractIllager$RaiderOpenDoorGoal", "RaiderOpenDoorGoal");
    private static final Comparator<Constructor<?>> VISIBILITY = Comparator.comparingInt(PaperGoalKeys::visibilityRank);
    private static final Map<Class<?>, Id> CACHE = new ConcurrentHashMap<>();

    private PaperGoalKeys() {
    }

    static Id key(Class<? extends Goal> goalClass) {
        Id cached = CACHE.get(goalClass);
        if (cached != null) {
            return cached;
        }
        Id created = Id.minecraft(pathName(mobType(goalClass), topLevel(goalClass), goalClass.getName()));
        CACHE.put(goalClass, created);
        return created;
    }

    static String pathName(String type, Class<?> holder, String name) {
        String pathName = name.substring(name.lastIndexOf('.') + 1);
        boolean needRename = false;
        int firstInner = pathName.indexOf('$');
        if (firstInner != -1) {
            String innerNames = pathName.substring(firstInner + 1);
            for (String inner : innerNames.split("\\$")) {
                if (!inner.isEmpty() && inner.chars().allMatch(Character::isDigit)) {
                    needRename = true;
                    break;
                }
            }
            if (!needRename && !RENAMES.containsKey(pathName)) {
                pathName = innerNames;
            }
        }
        if (!RENAMES.containsKey(pathName)) {
            if (needRename) {
                pathName = pathName.replace("$", "");
            }
            String prefix = null;
            if (type != null && !NO_SPECIFIER.contains(type)) {
                prefix = type;
            } else if (!Mob.class.isAssignableFrom(holder)) {
                prefix = holder.getSimpleName();
            }
            if (prefix != null && !pathName.startsWith(prefix)) {
                pathName = prefix + pathName;
            }
        } else {
            pathName = RENAMES.get(pathName);
        }
        pathName = pathName.replace("TargetGoal", "");
        pathName = pathName.replace("Goal", "");
        pathName = pathName.replace("Abstract", "");
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, pathName);
    }

    private static String mobType(Class<?> goalClass) {
        Constructor<?>[] constructors = goalClass.getDeclaredConstructors();
        Arrays.sort(constructors, VISIBILITY);
        for (Constructor<?> constructor : constructors) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                if (Mob.class.isAssignableFrom(parameter)) {
                    String simple = parameter.getSimpleName();
                    return BUKKIT_NAMES.getOrDefault(simple, simple);
                }
                if (RangedAttackMob.class.isAssignableFrom(parameter)) {
                    return RANGED_ENTITY;
                }
            }
        }
        return null;
    }

    private static Class<?> topLevel(Class<?> type) {
        Class<?> top = type;
        Class<?> upper = type.getEnclosingClass();
        while (upper != null) {
            top = upper;
            upper = upper.getEnclosingClass();
        }
        return top;
    }

    private static int visibilityRank(Constructor<?> constructor) {
        int modifiers = constructor.getModifiers();
        if (Modifier.isPublic(modifiers)) {
            return 0;
        }
        if (Modifier.isProtected(modifiers)) {
            return 1;
        }
        if (!Modifier.isPrivate(modifiers)) {
            return 2;
        }
        return 3;
    }
}
