package art.arcane.adapt;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.util.common.format.Localizer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class PapiExpansion extends PlaceholderExpansion {
    private static final Locale LOCALE = Locale.ROOT;

    @Override
    public @NotNull String getIdentifier() {
        return Adapt.instance.getDescription().getName().toLowerCase(LOCALE);
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", Adapt.instance.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return Adapt.instance.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getUniqueId() == null || params == null || params.isBlank()) {
            return "";
        }
        if (Adapt.instance == null || Adapt.instance.getAdaptServer() == null || Adapt.instance.getAdaptServer().getSkillRegistry() == null) {
            return "";
        }

        PlayerData playerData = Adapt.instance.getAdaptServer().peekData(player.getUniqueId());
        if (playerData == null) {
            return "";
        }

        String[] args = params.split("_");
        if (args.length == 0) {
            return "";
        }

        String root = args[0].toLowerCase(LOCALE);
        if ("player".equals(root)) {
            String attr = args.length > 1 ? join(args, 1) : "";
            return resolvePlayerAttribute(playerData, attr);
        }

        if ("skill".equals(root)) {
            if (args.length < 3) {
                return "0";
            }
            String skillId = args[1];
            String attr = join(args, 2);
            Skill<?> skill = resolveSkill(skillId);
            if (skill == null) {
                return "0";
            }
            PlayerSkillLine line = playerData.getSkillLineNullable(skill.getName());
            return resolveSkillAttribute(playerData, skill, line, attr);
        }

        if ("adaptation".equals(root)) {
            if (args.length < 3) {
                return "0";
            }
            String adaptationId = args[1];
            String attr = join(args, 2);
            Adaptation<?> adaptation = resolveAdaptation(adaptationId);
            if (adaptation == null) {
                return "0";
            }
            return resolveAdaptationAttribute(playerData, adaptation, attr);
        }

        if ("skills".equals(root) && args.length > 1 && "count".equalsIgnoreCase(args[1])) {
            return String.valueOf(countSkills());
        }

        if ("adaptations".equals(root) && args.length > 1 && "count".equalsIgnoreCase(args[1])) {
            return String.valueOf(countAdaptations());
        }

        if (args.length >= 2) {
            Skill<?> directSkill = resolveSkill(args[0]);
            if (directSkill != null) {
                String attr = join(args, 1);
                PlayerSkillLine line = playerData.getSkillLineNullable(directSkill.getName());
                return resolveSkillAttribute(playerData, directSkill, line, attr);
            }

            Adaptation<?> directAdaptation = resolveAdaptation(args[0]);
            if (directAdaptation != null) {
                String attr = join(args, 1);
                return resolveAdaptationAttribute(playerData, directAdaptation, attr);
            }
        }

        return null;
    }

    private String resolvePlayerAttribute(PlayerData playerData, String rawAttr) {
        String attr = rawAttr == null ? "" : rawAttr.toLowerCase(LOCALE);
        return switch (attr) {
            case "level" -> String.valueOf(playerData.getLevel());
            case "multiplier" -> format2(playerData.getMultiplier());
            case "availablepower" -> String.valueOf(playerData.getAvailablePower());
            case "maxpower" -> String.valueOf(playerData.getMaxPower());
            case "usedpower" -> String.valueOf(playerData.getUsedPower());
            case "wisdom" -> String.valueOf(playerData.getWisdom());
            case "masterxp" -> format2(playerData.getMasterXp());
            case "seenthings" -> String.valueOf(countSeenThings(playerData));
            case "skills", "skillcount" -> String.valueOf(countSkills());
            case "knownskills" -> String.valueOf(countKnownSkills(playerData));
            case "adaptations", "adaptationcount" -> String.valueOf(countAdaptations());
            case "learnedadaptations" -> String.valueOf(countLearnedAdaptations(playerData));
            default -> "0";
        };
    }

    private String resolveSkillAttribute(PlayerData playerData, Skill<?> skill, PlayerSkillLine line, String rawAttr) {
        String attr = rawAttr == null ? "" : rawAttr.toLowerCase(LOCALE);
        int currentLevel = line == null ? 0 : line.getLevel();

        if (attr.startsWith("can_claim_") || attr.startsWith("has_level_")) {
            Integer targetLevel = parseTrailingInt(attr);
            if (targetLevel == null) {
                return "false";
            }
            return String.valueOf(targetLevel >= 0 && targetLevel <= 100 && currentLevel >= targetLevel);
        }

        int learnedAdaptations = 0;
        if (line != null) {
            for (PlayerAdaptation adaptation : line.getAdaptations().values()) {
                if (adaptation != null && adaptation.getLevel() > 0) {
                    learnedAdaptations++;
                }
            }
        }

        return switch (attr) {
            case "id" -> skill.getName();
            case "enabled" -> String.valueOf(skill.isEnabled());
            case "name" -> Localizer.dLocalize("skill." + skill.getName() + ".name");
            case "display_name", "displayname" -> skill.getDisplayName();
            case "short_name", "shortname" -> skill.getShortName();
            case "level" -> String.valueOf(currentLevel);
            case "knowledge" -> String.valueOf(line == null ? 0 : line.getKnowledge());
            case "xp" -> format2(line == null ? 0 : line.getXp());
            case "freshness" -> format2(line == null ? 0 : line.getFreshness());
            case "multiplier" -> format2(line == null ? 0 : line.getMultiplier());
            case "absolute_level", "absolutelevel" -> format4(line == null ? 0 : line.getAbsoluteLevel());
            case "progress" -> format4(line == null ? 0 : line.getLevelProgress());
            case "progress_percent", "progresspercent" -> format2((line == null ? 0 : line.getLevelProgress()) * 100D);
            case "xp_to_next", "xptonext", "xp_needed", "xpneeded" -> format2(line == null ? 0 : XP.getXpUntilLevelUp(line.getXp()));
            case "current_level_xp", "currentlevelxp" -> format2(XP.getXpForLevel(currentLevel));
            case "next_level_xp", "nextlevelxp" -> format2(XP.getXpForLevel(currentLevel + 1));
            case "adaptation_count", "adaptationcount" -> String.valueOf(skill.getAdaptations().size());
            case "learned_adaptations", "learnedadaptations" -> String.valueOf(learnedAdaptations);
            case "can_use" -> String.valueOf(currentLevel > 0);
            case "line" -> skill.getName();
            case "maxlevel", "max_level" -> String.valueOf(AdaptConfig.get().experienceMaxLevel);
            default -> "0";
        };
    }

    private String resolveAdaptationAttribute(PlayerData playerData, Adaptation<?> adaptation, String rawAttr) {
        String attr = rawAttr == null ? "" : rawAttr.toLowerCase(LOCALE);
        PlayerSkillLine line = playerData.getSkillLineNullable(adaptation.getSkill().getName());
        int currentLevel = line == null ? 0 : line.getAdaptationLevel(adaptation.getName());

        if (attr.startsWith("can_claim_")) {
            Integer target = parseTrailingInt(attr);
            if (target == null) {
                return "false";
            }
            int targetLevel = clampAdaptationTarget(adaptation, target);
            return String.valueOf(canClaimTarget(playerData, line, adaptation, currentLevel, targetLevel));
        }

        if (attr.startsWith("cost_to_") || attr.startsWith("knowledge_to_")) {
            Integer target = parseTrailingInt(attr);
            if (target == null) {
                return "0";
            }
            int targetLevel = clampAdaptationTarget(adaptation, target);
            return String.valueOf(adaptation.getCostFor(targetLevel, currentLevel));
        }

        if (attr.startsWith("power_to_")) {
            Integer target = parseTrailingInt(attr);
            if (target == null) {
                return "0";
            }
            int targetLevel = clampAdaptationTarget(adaptation, target);
            int powerCost = adaptation.getPowerCostFor(targetLevel, currentLevel);
            return String.valueOf(Math.max(0, powerCost));
        }

        if (attr.startsWith("refund_to_")) {
            Integer target = parseTrailingInt(attr);
            if (target == null) {
                return "0";
            }
            int targetLevel = clampAdaptationTarget(adaptation, target);
            return String.valueOf(adaptation.getRefundCostFor(targetLevel, currentLevel));
        }

        int nextLevel = Math.min(adaptation.getMaxLevel(), currentLevel + 1);
        return switch (attr) {
            case "id" -> adaptation.getName();
            case "level" -> String.valueOf(currentLevel);
            case "maxlevel", "max_level" -> String.valueOf(adaptation.getMaxLevel());
            case "name", "display_name", "displayname" -> adaptation.getDisplayName();
            case "raw_name", "rawname" -> adaptation.getName();
            case "skill", "skill_id", "skillid" -> adaptation.getSkill().getName();
            case "enabled" -> String.valueOf(adaptation.isEnabled());
            case "learned" -> String.valueOf(currentLevel > 0);
            case "can_use" -> String.valueOf(currentLevel > 0 && adaptation.isEnabled() && adaptation.getSkill().isEnabled());
            case "cost_next", "costnext", "knowledge_next", "knowledgenext" -> String.valueOf(currentLevel >= adaptation.getMaxLevel() ? 0 : adaptation.getCostFor(nextLevel, currentLevel));
            case "power_next", "powernext" -> String.valueOf(currentLevel >= adaptation.getMaxLevel() ? 0 : Math.max(0, adaptation.getPowerCostFor(nextLevel, currentLevel)));
            case "refund_next", "refundnext" -> String.valueOf(currentLevel <= 0 ? 0 : adaptation.getRefundCostFor(Math.max(0, currentLevel - 1), currentLevel));
            case "can_claim_next", "canclaimnext" -> String.valueOf(canClaimTarget(playerData, line, adaptation, currentLevel, nextLevel));
            default -> "0";
        };
    }

    private boolean canClaimTarget(PlayerData playerData, PlayerSkillLine line, Adaptation<?> adaptation, int currentLevel, int targetLevel) {
        if (targetLevel == currentLevel) {
            return true;
        }

        if (targetLevel > currentLevel) {
            int powerCost = Math.max(0, adaptation.getPowerCostFor(targetLevel, currentLevel));
            int knowledgeCost = adaptation.getCostFor(targetLevel, currentLevel);
            if (!playerData.hasPowerAvailable(powerCost)) {
                return false;
            }
            return line != null && line.getKnowledge() >= knowledgeCost;
        }

        if (adaptation.isPermanent()) {
            return false;
        }

        return currentLevel > 0;
    }

    private int clampAdaptationTarget(Adaptation<?> adaptation, int requested) {
        int clamped = Math.max(0, Math.min(requested, 100));
        return Math.min(clamped, adaptation.getMaxLevel());
    }

    private Skill<?> resolveSkill(String rawSkillId) {
        if (rawSkillId == null || rawSkillId.isBlank()) {
            return null;
        }

        String skillId = rawSkillId.trim();
        Skill<?> direct = Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill(skillId);
        if (direct != null) {
            return direct;
        }

        List<Skill<?>> allSkills = Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills();
        for (Skill<?> skill : allSkills) {
            if (skill.getName().equalsIgnoreCase(skillId)) {
                return skill;
            }
        }

        return null;
    }

    private Adaptation<?> resolveAdaptation(String rawAdaptationId) {
        if (rawAdaptationId == null || rawAdaptationId.isBlank()) {
            return null;
        }

        String adaptationId = rawAdaptationId.trim().toLowerCase(LOCALE);
        List<Skill<?>> allSkills = Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills();
        for (Skill<?> skill : allSkills) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (matchesAdaptationIdentifier(adaptation, adaptationId)) {
                    return adaptation;
                }
            }
        }

        return null;
    }

    private boolean matchesAdaptationIdentifier(Adaptation<?> adaptation, String normalizedTarget) {
        String name = adaptation.getName();
        if (name != null && name.equalsIgnoreCase(normalizedTarget)) {
            return true;
        }

        String fullId = adaptation.getId();
        if (fullId != null && fullId.equalsIgnoreCase(normalizedTarget)) {
            return true;
        }

        if (fullId != null && fullId.length() > 37) {
            String legacyId = fullId.substring(37);
            if (legacyId.equalsIgnoreCase(normalizedTarget)) {
                return true;
            }
        }

        String scoped = adaptation.getSkill().getName() + ":" + adaptation.getName();
        return scoped.equalsIgnoreCase(normalizedTarget);
    }

    private int countSeenThings(PlayerData playerData) {
        int total = 0;
        total += playerData.getSeenBlocks().getSeen().size();
        total += playerData.getSeenBiomes().getSeen().size();
        total += playerData.getSeenEnchants().getSeen().size();
        total += playerData.getSeenEnvironments().getSeen().size();
        total += playerData.getSeenFoods().getSeen().size();
        total += playerData.getSeenItems().getSeen().size();
        total += playerData.getSeenMobs().getSeen().size();
        total += playerData.getSeenPeople().getSeen().size();
        total += playerData.getSeenPotionEffects().getSeen().size();
        total += playerData.getSeenRecipes().getSeen().size();
        total += playerData.getSeenWorlds().getSeen().size();
        return total;
    }

    private int countSkills() {
        return Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills().size();
    }

    private int countKnownSkills(PlayerData playerData) {
        int total = 0;
        for (PlayerSkillLine line : playerData.getSkillLines().values()) {
            if (line != null && line.getLevel() > 0) {
                total++;
            }
        }
        return total;
    }

    private int countAdaptations() {
        int total = 0;
        List<Skill<?>> skills = Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills();
        for (Skill<?> skill : skills) {
            total += skill.getAdaptations().size();
        }
        return total;
    }

    private int countLearnedAdaptations(PlayerData playerData) {
        int total = 0;
        for (PlayerSkillLine line : playerData.getSkillLines().values()) {
            if (line == null) {
                continue;
            }
            for (PlayerAdaptation adaptation : line.getAdaptations().values()) {
                if (adaptation != null && adaptation.getLevel() > 0) {
                    total++;
                }
            }
        }
        return total;
    }

    private Integer parseTrailingInt(String attr) {
        int index = attr.lastIndexOf('_');
        if (index == -1 || index >= attr.length() - 1) {
            return null;
        }

        try {
            return Integer.parseInt(attr.substring(index + 1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String join(String[] args, int start) {
        if (args == null || start >= args.length) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append('_');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private String format2(double value) {
        return String.format(LOCALE, "%.2f", value);
    }

    private String format4(double value) {
        return String.format(LOCALE, "%.4f", value);
    }
}
