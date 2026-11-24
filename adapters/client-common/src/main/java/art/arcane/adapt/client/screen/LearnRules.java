package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.RequirementView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What the server will accept for a learn or unlearn, worked out from the catalog and the player view so the screens
 * can explain a refusal before the player asks. The checks run in the server's order (requirements, power,
 * knowledge) and follow the player's {@link PlayerAccess}: debug mode skips power, knowledge, permanence and
 * dependents and pays no refund, and no-refund servers pay none either. The server still decides, and economy costs
 * are only known there.
 */
public final class LearnRules {
    private LearnRules() {
    }

    /** Knowledge to go from {@code from} to {@code to}: the sum of the per-level costs in between. */
    public static int knowledgeCost(AdaptationView adaptation, int from, int to) {
        return sum(adaptation.knowledgeCosts(), from, to);
    }

    /** Power the move from {@code from} to {@code to} takes: 1 to start holding the adaptation, nothing for further levels. */
    public static int powerCost(AdaptationView adaptation, int from, int to) {
        return sum(adaptation.powerCosts(), from, to);
    }

    /** Knowledge given back when going down from {@code from} to {@code to}. */
    public static int refund(AdaptationView adaptation, int to, int from) {
        return sum(adaptation.refunds(), to, from);
    }

    public static long knowledge(PlayerView player, String skillId) {
        SkillLineView line = CatalogIndex.line(player, skillId);
        return line == null ? 0L : line.knowledge();
    }

    public static int freePower(PlayerView player) {
        return player == null ? 0 : Math.max(0, player.header().powerMax() - player.header().powerUsed());
    }

    /** Requirements of {@code adaptation} the player does not meet, in declared order. */
    public static List<RequirementView> missing(PlayerView player, AdaptationView adaptation) {
        List<RequirementView> missing = new ArrayList<>();
        for (RequirementView requirement : adaptation.requirements()) {
            if (CatalogIndex.level(player, requirement.adaptationId()) < requirement.level()) {
                missing.add(requirement);
            }
        }
        return missing;
    }

    /** Whether learning up to {@code target} would be accepted, and what it costs. */
    public static LearnCheck learn(CatalogIndex catalog, PlayerView player, AdaptationView adaptation, int target) {
        int from = CatalogIndex.level(player, adaptation.id());
        int to = Math.max(0, Math.min(target, adaptation.maxLevel()));
        boolean free = CatalogIndex.access(player).debug();
        long knowledge = knowledge(player, adaptation.skillId());
        int freePower = freePower(player);
        int cost = free ? 0 : knowledgeCost(adaptation, from, to);
        int power = powerCost(adaptation, from, to);
        List<RequirementView> missing = missing(player, adaptation);
        LearnBlock block;
        if (!enabled(catalog, adaptation)) {
            block = LearnBlock.DISABLED;
        } else if (!CatalogIndex.access(player).shows(adaptation.skillId(), adaptation.id())) {
            block = LearnBlock.DENIED;
        } else if (to <= from) {
            block = LearnBlock.LEARNED;
        } else if (!missing.isEmpty()) {
            block = LearnBlock.REQUIREMENTS;
        } else if (!free && freePower < power) {
            block = LearnBlock.POWER;
        } else if (knowledge < cost) {
            block = LearnBlock.KNOWLEDGE;
        } else {
            block = LearnBlock.NONE;
        }
        return new LearnCheck(from, to, block, free, cost, power, knowledge, freePower, missing);
    }

    /** Whether going down to {@code target} would be accepted, and what it refunds. */
    public static UnlearnCheck unlearn(CatalogIndex catalog, PlayerView player, AdaptationView adaptation, int target) {
        int from = CatalogIndex.level(player, adaptation.id());
        int to = Math.max(0, target);
        PlayerAccess access = CatalogIndex.access(player);
        boolean refunded = access.refunds() && !access.debug();
        int refund = refunded && to < from ? refund(adaptation, to, from) : 0;
        List<AdaptationView> dependents = access.debug() ? List.of() : dependents(catalog, player, adaptation, to);
        UnlearnBlock block;
        if (!enabled(catalog, adaptation)) {
            block = UnlearnBlock.DISABLED;
        } else if (!access.shows(adaptation.skillId(), adaptation.id())) {
            block = UnlearnBlock.DENIED;
        } else if (to >= from) {
            block = UnlearnBlock.NOT_LEARNED;
        } else if (adaptation.permanent() && !access.debug()) {
            block = UnlearnBlock.PERMANENT;
        } else if (!dependents.isEmpty()) {
            block = UnlearnBlock.DEPENDENTS;
        } else {
            block = UnlearnBlock.NONE;
        }
        return new UnlearnCheck(from, to, block, refunded, refund, dependents);
    }

    /** Learned adaptations whose requirement on {@code adaptation} would break if it went down to {@code target}. */
    public static List<AdaptationView> dependents(CatalogIndex catalog, PlayerView player, AdaptationView adaptation, int target) {
        List<AdaptationView> broken = new ArrayList<>();
        for (AdaptationView other : catalog.adaptations()) {
            if (other == adaptation || CatalogIndex.level(player, other.id()) <= 0) {
                continue;
            }
            for (RequirementView requirement : other.requirements()) {
                if (requirement.adaptationId().equals(adaptation.id()) && requirement.level() > target) {
                    broken.add(other);
                    break;
                }
            }
        }
        return broken;
    }

    /** How many adaptations of {@code skill} the player can learn the next level of now. */
    public static int affordable(CatalogIndex catalog, PlayerView player, SkillView skill) {
        int count = 0;
        for (AdaptationView adaptation : skill.adaptations()) {
            if (nextAffordable(catalog, player, adaptation)) {
                count++;
            }
        }
        return count;
    }

    public static boolean nextAffordable(CatalogIndex catalog, PlayerView player, AdaptationView adaptation) {
        int level = CatalogIndex.level(player, adaptation.id());
        return level < adaptation.maxLevel() && learn(catalog, player, adaptation, level + 1).allowed();
    }

    private static boolean enabled(CatalogIndex catalog, AdaptationView adaptation) {
        SkillView skill = catalog.skill(adaptation.skillId());
        return adaptation.enabled() && skill != null && skill.enabled();
    }

    private static int sum(List<Integer> perLevel, int from, int to) {
        long total = 0L;
        for (int level = Math.max(0, from); level < Math.min(to, perLevel.size()); level++) {
            total += Math.max(0, perLevel.get(level));
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public enum LearnBlock {
        NONE,
        DISABLED,
        DENIED,
        LEARNED,
        REQUIREMENTS,
        POWER,
        KNOWLEDGE
    }

    public enum UnlearnBlock {
        NONE,
        DISABLED,
        DENIED,
        NOT_LEARNED,
        PERMANENT,
        DEPENDENTS
    }

    /** A learn from {@code from} to {@code to}: its block, its cost ({@code free} in debug mode) and what the player has. */
    public record LearnCheck(int from, int to, LearnBlock block, boolean free, int knowledgeCost, int powerCost, long knowledge, int freePower,
                             List<RequirementView> missing) {
        public LearnCheck {
            Objects.requireNonNull(block, "block");
            missing = List.copyOf(missing);
        }

        public boolean allowed() {
            return block == LearnBlock.NONE;
        }

        public long knowledgeShort() {
            return Math.max(0L, knowledgeCost - knowledge);
        }

        public int powerShort() {
            return Math.max(0, powerCost - freePower);
        }
    }

    /**
     * An unlearn from {@code from} down to {@code to}: its block, its refund ({@code refunded} false when the server pays
     * none) and the adaptations that need it.
     */
    public record UnlearnCheck(int from, int to, UnlearnBlock block, boolean refunded, int refund, List<AdaptationView> dependents) {
        public UnlearnCheck {
            Objects.requireNonNull(block, "block");
            dependents = List.copyOf(dependents);
        }

        public boolean allowed() {
            return block == UnlearnBlock.NONE;
        }
    }
}
