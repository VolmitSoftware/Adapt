package com.volmit.adapt;

import com.google.common.collect.Maps;
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerData;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.util.Localizer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PapiExpansion extends PlaceholderExpansion {

    private final Map<String, Function<PlayerSkillLine, String>> skillMap = Maps.newHashMap();
    private final Map<String, Function<PlayerData, String>> playerMap = Maps.newHashMap();
    private final Map<String, BiFunction<PlayerSkillLine, PlayerAdaptation, String>> adaptationMap = Maps.newHashMap();

    public PapiExpansion() {
        skillMap.put("level", skill -> String.valueOf(skill.getLevel()).equals("-5000") ? "0" : String.valueOf(skill.getLevel()));
        skillMap.put("knowledge", skill -> String.valueOf(skill.getKnowledge()).equals("-5000") ? "0" : String.valueOf(skill.getKnowledge()));
        skillMap.put("xp", skill -> String.format("%.2f", skill.getXp()).equals("-5000.00") ? "0" : String.format("%.2f", skill.getXp()));
        skillMap.put("freshness", skill -> String.valueOf(skill.getFreshness()).equals("-5000") ? "0" : String.valueOf(skill.getFreshness()));
        skillMap.put("multiplier", skill -> String.valueOf(skill.getMultiplier()).equals("-5000") ? "0" : String.valueOf(skill.getMultiplier()));
        skillMap.put("name", skill -> Localizer.dLocalize("skill", skill.getLine(), "name"));

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

        adaptationMap.put("name", (skillLine, adaptation) -> Localizer.dLocalize(skillLine.getLine(), adaptation.getId(), "name"));
        adaptationMap.put("level", (skillLine, adaptation) -> String.valueOf(adaptation.getLevel()).equals("-5000") ? "0" : String.valueOf(adaptation.getLevel()));
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

    /* %adapt_<command/skill line>_[command/adaption]%
    Example for Brian's smooth brain:
    - %adapt_multiplier% - Returns the players multiplier
    - %adapt_hunter_level% - Returns the level of the players hunter skill
    - %adapt_nether_witherresist% - Returns the level of the Wither Armor Adaptation
    */
    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String[] args = params.split("_");
        PlayerData p = Adapt.instance.getAdaptServer().peekData(player.getUniqueId());

        if (Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(args[0]) == null) {
            for (String k : playerMap.keySet()) {
                if (k.equalsIgnoreCase(args[0])) {
                    return playerMap.get(k).apply(p);
                }
            }
        } else {
            PlayerSkillLine line = p.getSkillLine(args[0]);
            for (String k : skillMap.keySet()) {
                if (k.equalsIgnoreCase(args[1])) {
                    return skillMap.get(k).apply(line);
                }
            }

            String adaptName = args[0] + "-" + args[1];
            if (line.getAdaptations().containsKey(adaptName)) {
                PlayerAdaptation adapt = line.getAdaptation(adaptName);
                for (String k : adaptationMap.keySet()) {
                    if (k.equalsIgnoreCase(args[2])) {
                        return adaptationMap.get(k).apply(line, adapt);
                    }
                }
            }
        }

        return null;
    }
}
