package com.volmit.adapt;

import com.google.common.collect.Maps;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.world.PlayerData;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.util.Localizer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PapiExpansion extends PlaceholderExpansion {

    private final Map<String, Function<PlayerSkillLine, String>> skillMap = Maps.newHashMap();
    private final Map<String, Function<PlayerData, String>> playerMap = Maps.newHashMap();
    private final Map<String, BiFunction<PlayerData, Adaptation<?>, String>> adaptationMap = Maps.newHashMap();

    public PapiExpansion() {
        // this should be %adapt_skill_level%, %adapt_skill_knowledge%, %adapt_skill_xp%, %adapt_skill_freshness%, %adapt_skill_multiplier%, %adapt_skill_name%
        // where skill is the id of the skill eg: %adapt_herbalism_level%
        skillMap.put("level", skill -> String.valueOf(skill.getLevel()).equals("-5000") ? "0" : String.valueOf(skill.getLevel()));
        skillMap.put("knowledge", skill -> String.valueOf(skill.getKnowledge()).equals("-5000") ? "0" : String.valueOf(skill.getKnowledge()));
        skillMap.put("xp", skill -> String.format("%.2f", skill.getXp()).equals("-5000.00") ? "0" : String.format("%.2f", skill.getXp()));
        skillMap.put("freshness", skill -> String.valueOf(skill.getFreshness()).equals("-5000") ? "0" : String.valueOf(skill.getFreshness()));
        skillMap.put("multiplier", skill -> String.valueOf(skill.getMultiplier()).equals("-5000") ? "0" : String.valueOf(skill.getMultiplier()));
        skillMap.put("name", skill -> Localizer.dLocalize("skill", skill.getLine(), "name"));

        // this should be %adapt_player_level%, %adapt_player_multiplier%, %adapt_player_availablepower%, %adapt_player_maxpower%, %adapt_player_usedpower%, %adapt_player_wisdom%, %adapt_player_masterxp%, %adapt_player_seenthings%
        // the player is provided by the ingame context
        playerMap.put("level", playerData -> String.valueOf(playerData.getMultiplier()).equals("-5000") ? "0" : String.valueOf(playerData.getLevel()));
        playerMap.put("multiplier", playerData -> String.valueOf(playerData.getMultiplier()).equals("-5000") ? "0" : String.valueOf(playerData.getMultiplier()));
        playerMap.put("availablepower", playerData -> String.valueOf(playerData.getAvailablePower()).equals("-5000") ? "0" : String.valueOf(playerData.getAvailablePower()));
        playerMap.put("maxpower", playerData -> String.valueOf(playerData.getMaxPower()).equals("-5000") ? "0" : String.valueOf(playerData.getMaxPower()));
        playerMap.put("usedpower", playerData -> String.valueOf(playerData.getUsedPower()).equals("-5000") ? "0" : String.valueOf(playerData.getUsedPower()));
        playerMap.put("wisdom", playerData -> String.valueOf(playerData.getWisdom()).equals("-5000") ? "0" : String.valueOf(playerData.getWisdom()));
        playerMap.put("masterxp", playerData -> String.valueOf(playerData.getMasterXp()).equals("-5000") ? "0" : String.valueOf(playerData.getMasterXp()));
        playerMap.put("seenthings", playerData -> String.valueOf(playerData.getSeenBlocks().getSeen().size()
                + playerData.getSeenBiomes().getSeen().size()
                + playerData.getSeenEnchants().getSeen().size()
                + playerData.getSeenEnvironments().getSeen().size()
                + playerData.getSeenFoods().getSeen().size()
                + playerData.getSeenItems().getSeen().size()
                + playerData.getSeenMobs().getSeen().size()
                + playerData.getSeenPeople().getSeen().size()
                + playerData.getSeenPotionEffects().getSeen().size() + playerData.getSeenRecipes().getSeen().size()
                + playerData.getSeenPotionEffects().getSeen().size() + playerData.getSeenWorlds().getSeen().size()));

        // this should be %adapt_adaptation_<ID>_level%, %adapt_adaptation_<ID>_maxlevel%
        // where adaptation is the adaptation id (e.g. %adapt_adaptation_stealth-ghost-armor_level%)
        adaptationMap.put("maxlevel", (playerData, adaptation) -> String.valueOf(adaptation.getMaxLevel()));
        adaptationMap.put("level", (playerData, adaptation) -> String.valueOf(getAdaptionLevel(adaptation, playerData)));
        adaptationMap.put("name", (playerData, adaptation) -> String.valueOf(getAdaptionLocalizedName(adaptation)));
    }

    private Integer getAdaptionLevel(Adaptation<?> adaptation, PlayerData playerData) {
        List<Skill<?>> skills = Adapt.instance.getAdaptServer().getSkillRegistry().getSkills();
        for (Skill<?> skill : skills) {
            List<Adaptation<?>> adaptations = skill.getAdaptations();
            for (Adaptation<?> a : adaptations) {
                if (a.equals(adaptation)) {
                    return playerData.getSkillLine(skill.getName()).getAdaptationLevel(adaptation.getName());
                }
            }
        }
        return 0;
    }

    private String getAdaptionLocalizedName(Adaptation<?> adaptation) {
        List<Skill<?>> skills = Adapt.instance.getAdaptServer().getSkillRegistry().getSkills();
        for (Skill<?> skill : skills) {
            List<Adaptation<?>> adaptations = skill.getAdaptations();
            for (Adaptation<?> a : adaptations) {
                if (a.equals(adaptation)) {
                    return Localizer.dLocalize(skill.getId(), adaptation.getDisplayName(), "name");
                }
            }
        }
        return "Unknown";
    }

    @Override
    public @NotNull String getIdentifier() {
        return Adapt.instance.getDescription().getName().toLowerCase();
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
        String[] args = params.split("_");
        PlayerData p = Adapt.instance.getAdaptServer().peekData(player.getUniqueId());
        String key = args[0];

        // Handle player attributes
        if (key.equals("player")) {
            String playerAttr = args.length > 1 ? args[1] : "";
            if (playerMap.containsKey(playerAttr)) {
                return playerMap.get(playerAttr).apply(p);
            }
        }

        // Handle skill attributes
        if (key.equals("skill")) {
            String skillID = args.length > 1 ? args[1] : "";
            PlayerSkillLine line = p.getSkillLine(skillID);
            String skillAttr = args.length > 2 ? args[2] : "";
            if (line != null && skillMap.containsKey(skillAttr)) {
                return skillMap.get(skillAttr).apply(line);
            }
        }

        // Handle adaptation attributes
        if (key.equals("adaptation")) {
            String adaptID = args.length > 1 ? args[1] : "";
            String adaptAttr = args.length > 2 ? args[2] : "";
            Adapt.verbose("Triggered adaptation Lookup: " + adaptID + " " + adaptAttr);
            List<Skill<?>> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getSkills();

            for (Skill<?> s : skill) {
                List<Adaptation<?>> adaptations = s.getAdaptations();
                for (Adaptation<?> a : adaptations) {
                    String adaptationIdWithoutUUID = a.getId().substring(37);
                    Adapt.verbose(adaptID + " " + adaptationIdWithoutUUID);
                    if (adaptationIdWithoutUUID.equals(adaptID)) {
                        Adapt.verbose("Found adaptation: " + a.getId());
                        if ("level".equalsIgnoreCase(adaptAttr)) {
                            Adapt.verbose("Doing Level Lookup");
                            return adaptationMap.get("level").apply(p, a);
                        } else if ("maxlevel".equalsIgnoreCase(adaptAttr)) {
                            Adapt.verbose("Doing MaxLevel Lookup");
                            return adaptationMap.get("maxlevel").apply(p, a);
                        } else if ("name".equalsIgnoreCase(adaptAttr)) {
                            Adapt.verbose("Doing Name Lookup");
                            return adaptationMap.get("name").apply(p, a);
                        }
                    }
                }
            }
        }
        return null;
    }
}

