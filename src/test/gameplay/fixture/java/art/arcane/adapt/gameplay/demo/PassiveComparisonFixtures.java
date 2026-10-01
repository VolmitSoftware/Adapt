package art.arcane.adapt.gameplay.demo;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.world.PlayerSkillLine.RewardStalenessState;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XpNovelty;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PassiveComparisonFixtures {
    private static final float MARATHONER_EXHAUSTION = 3.0F;
    private static final Map<UUID, Comparison> COMPARISONS = new ConcurrentHashMap<>();

    private PassiveComparisonFixtures() {
    }

    public static String control(Player player, String name, String phase) {
        JsonObject result = switch (phase) {
            case "baseline" -> prepare(player, name, false);
            case "learned" -> prepare(player, name, true);
            case "finish" -> finish(player, name);
            case "clear" -> clear(player);
            default -> throw new IllegalArgumentException("Unknown passive comparison phase " + phase);
        };
        return "ADAPT_QA DEMO PASSIVE " + result;
    }

    private static JsonObject prepare(Player player, String name, boolean learned) {
        Adaptation<?> adaptation = adaptation(name);
        Comparison comparison;
        if (learned) {
            comparison = comparison(player, name);
            if (!comparison.baselineFinished) {
                throw new IllegalStateException("Finish the baseline pass before preparing the learned pass");
            }
        } else {
            clear(player);
            comparison = new Comparison(player, adaptation);
            COMPARISONS.put(player.getUniqueId(), comparison);
        }

        AdaptPlayer runtime = runtimePlayer(player);
        PlayerData data = runtime.getData();
        PlayerSkillLine adaptationLine = Objects.requireNonNull(data.getSkillLine(adaptation.getSkill().getName()));
        adaptationLine.setAdaptation(adaptation, learned ? adaptation.getMaxLevel() : 0);
        comparison.learned = learned;
        comparison.origin = player.getLocation();
        comparison.savedBefore = data.getStat("agility.marathoner.saturation-saved");
        comparison.board.resetScores("Pass: unlearned");
        comparison.board.resetScores("Pass: learned");
        comparison.objective.getScore(learned ? "Pass: learned" : "Pass: unlearned").setScore(0);

        if (name.equals("marathoner")) {
            player.setSprinting(false);
            player.setFoodLevel(20);
            player.setSaturation(0.0F);
            player.setExhaustion(MARATHONER_EXHAUSTION);
            comparison.objective.getScore("Starting hunger").setScore(player.getFoodLevel());
            caption(player, learned ? "With Marathoner" : "Without Marathoner", "Same starting hunger · 80-tick sprint");
        } else {
            if (!learned) {
                prepareQualifyingSkills(data);
            }
            prepareSwordPass(player, runtime, comparison);
            caption(player, learned ? "With Polymath" : "Without Polymath", "Same charged sword hit · actual skill XP");
        }
        player.setScoreboard(comparison.board);
        return state(player, comparison);
    }

    private static JsonObject finish(Player player, String name) {
        Comparison comparison = comparison(player, name);
        PlayerData data = runtimePlayer(player).getData();
        if (name.equals("marathoner")) {
            double distance = horizontalDistance(comparison.origin, player.getLocation());
            if (distance < 18.0D || distance > 27.0D) {
                throw new IllegalStateException("Marathoner sprint travelled " + distance + " blocks; expected an 80-tick sprint");
            }
            int expectedFood = comparison.learned ? 20 : 19;
            if (player.getFoodLevel() != expectedFood) {
                throw new IllegalStateException("Marathoner hunger was " + player.getFoodLevel() + "; expected " + expectedFood);
            }
            if (comparison.learned) {
                if (Math.abs(distance - comparison.baselineDistance) > 2.0D
                        || data.getStat("agility.marathoner.saturation-saved") <= comparison.savedBefore) {
                    throw new IllegalStateException("Marathoner passes did not use matched distances and actual drain savings");
                }
            } else {
                comparison.baselineDistance = distance;
            }
            comparison.objective.getScore(comparison.learned ? "With Marathoner" : "Without Marathoner").setScore(player.getFoodLevel());
        } else {
            PlayerSkillLine sword = Objects.requireNonNull(data.getSkillLine("swords"));
            double earned = sword.getXp() + sword.getPooledXp() - comparison.xpBefore;
            double damage = data.getStat("sword.damage") - comparison.damageBefore;
            if (earned <= 0.0D || damage <= 0.0D) {
                throw new IllegalStateException("Polymath pass did not earn sword skill XP through a real hit");
            }
            if (comparison.learned) {
                if (Math.abs(damage - comparison.baselineDamage) > 0.001D || earned <= comparison.baselineXp * 1.5D) {
                    throw new IllegalStateException("Polymath passes did not show a larger XP award for the same damage: " + earned);
                }
            } else {
                comparison.baselineXp = earned;
                comparison.baselineDamage = damage;
            }
            comparison.objective.getScore(comparison.learned ? "XP with Polymath" : "XP without Polymath").setScore((int) Math.round(earned));
        }
        if (!comparison.learned) {
            comparison.baselineFinished = true;
        }
        return state(player, comparison);
    }

    private static void prepareQualifyingSkills(PlayerData data) {
        int qualifying = 0;
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            if (!skill.isEnabled()) {
                continue;
            }
            PlayerSkillLine line = Objects.requireNonNull(data.getSkillLine(skill.getName()));
            line.setXp(XP.getXpForLevel(5));
            line.setLastLevel(5);
            line.setLastXP(line.getXp());
            qualifying++;
        }
        if (qualifying < 17) {
            throw new IllegalStateException("Polymath comparison requires at least 17 qualifying enabled skills");
        }
    }

    private static void prepareSwordPass(Player player, AdaptPlayer runtime, Comparison comparison) {
        PlayerData data = runtime.getData();
        PlayerSkillLine sword = Objects.requireNonNull(data.getSkillLine("swords"));
        sword.setFreshness(1.0D);
        sword.setRfreshness(1.0D);
        sword.setMonotonyCounter(0);
        sword.setMonotonyMultiplier(1.0D);
        sword.setSkillStaleness(new RewardStalenessState());
        sword.getActivityStaleness().clear();
        XpNovelty.clear(player.getUniqueId());
        if (!comparison.learned) {
            data.removeGlobalXPMultipliers(comparison.adaptation.getName());
        }
        comparison.xpBefore = sword.getXp() + sword.getPooledXp();
        comparison.damageBefore = data.getStat("sword.damage");
        Zombie target = null;
        for (Entity entity : player.getNearbyEntities(8.0D, 4.0D, 8.0D)) {
            if (entity instanceof Zombie zombie) {
                target = zombie;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException("Polymath comparison requires the sparring-set zombie");
        }
        target.setAI(false);
        Objects.requireNonNull(target.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(200.0D);
        target.setHealth(200.0D);
        Objects.requireNonNull(target.getAttribute(Attribute.KNOCKBACK_RESISTANCE)).setBaseValue(1.0D);
    }

    private static JsonObject state(Player player, Comparison comparison) {
        PlayerData data = runtimePlayer(player).getData();
        JsonObject result = new JsonObject();
        result.addProperty("adaptation", comparison.adaptation.getName());
        result.addProperty("learned", comparison.learned);
        result.addProperty("food", player.getFoodLevel());
        result.addProperty("saturation", player.getSaturation());
        result.addProperty("exhaustion", player.getExhaustion());
        result.addProperty("distance", horizontalDistance(comparison.origin, player.getLocation()));
        result.addProperty("saturationSaved", data.getStat("agility.marathoner.saturation-saved") - comparison.savedBefore);
        result.addProperty("xpMultiplier", data.computeXpMultiplier(player));
        result.addProperty("baselineXp", comparison.baselineXp);
        PlayerSkillLine sword = data.getSkillLineNullable("swords");
        if (sword != null && comparison.adaptation.getName().equals("discovery-polymath")) {
            result.addProperty("earnedXp", sword.getXp() + sword.getPooledXp() - comparison.xpBefore);
            result.addProperty("damage", data.getStat("sword.damage") - comparison.damageBefore);
        }
        return result;
    }

    private static JsonObject clear(Player player) {
        Comparison previous = COMPARISONS.remove(player.getUniqueId());
        if (previous != null) {
            player.setScoreboard(previous.previousBoard);
        }
        return new JsonObject();
    }

    private static Comparison comparison(Player player, String name) {
        Comparison comparison = COMPARISONS.get(player.getUniqueId());
        if (comparison == null || !comparison.adaptation.getName().equals(adaptation(name).getName())) {
            throw new IllegalStateException("Prepare the passive comparison first");
        }
        return comparison;
    }

    private static Adaptation<?> adaptation(String name) {
        String id = switch (name) {
            case "marathoner" -> "agility-marathoner";
            case "polymath" -> "discovery-polymath";
            default -> throw new IllegalArgumentException("Unknown passive comparison " + name);
        };
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation.getName().equals(id)) {
                    return adaptation;
                }
            }
        }
        throw new IllegalStateException("Missing adaptation " + id);
    }

    private static AdaptPlayer runtimePlayer(Player player) {
        AdaptPlayer runtime = Adapt.instance.getAdaptServer().getPlayer(player);
        if (runtime == null || !runtime.isRuntimeReady()) {
            throw new IllegalStateException("Adapt player is not ready");
        }
        return runtime;
    }

    private static double horizontalDistance(Location first, Location second) {
        return Math.hypot(second.getX() - first.getX(), second.getZ() - first.getZ());
    }

    private static void caption(Player player, String title, String subtitle) {
        player.showTitle(Title.title(Component.text(title), Component.text(subtitle),
                Title.Times.times(Duration.ofMillis(200L), Duration.ofSeconds(2L), Duration.ofMillis(300L))));
    }

    private static final class Comparison {
        private final Adaptation<?> adaptation;
        private final Scoreboard previousBoard;
        private final Scoreboard board;
        private final Objective objective;
        private boolean learned;
        private boolean baselineFinished;
        private Location origin;
        private double xpBefore;
        private double damageBefore;
        private double savedBefore;
        private double baselineXp;
        private double baselineDamage;
        private double baselineDistance;

        private Comparison(Player player, Adaptation<?> adaptation) {
            this.adaptation = adaptation;
            previousBoard = player.getScoreboard();
            board = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();
            objective = board.registerNewObjective("passive_compare", Criteria.DUMMY, Component.text("Demo comparison"));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
    }
}
