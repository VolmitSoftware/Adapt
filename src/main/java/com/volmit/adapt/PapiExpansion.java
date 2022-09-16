package com.volmit.adapt;

import com.google.common.collect.Maps;
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerData;
import com.volmit.adapt.api.world.PlayerSkillLine;
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
    private final Map<String, BiFunction<PlayerSkillLine, PlayerAdaptation, String>> adaptMap = Maps.newHashMap();

    public PapiExpansion() {
        skillMap.put("level", s -> String.valueOf(s.getLevel()));
        skillMap.put("knowledge", s -> String.valueOf(s.getKnowledge()));
        skillMap.put("xp", s -> String.format("%.2f", s.getXp()));
        skillMap.put("freshness", s -> String.valueOf(s.getFreshness()));
        skillMap.put("name", s -> Adapt.dLocalize("Skill", s.getLine().capitalize(), "Name"));

        playerMap.put("multiplier", pd -> String.valueOf(pd.getMultiplier()));

        adaptMap.put("name", (sl, a) -> Adapt.dLocalize(sl.getLine().capitalize(), a.getId().capitalize(), "Name"));
        adaptMap.put("level", (sl, a) -> String.valueOf(a.getLevel()));
    }

    @Override
    public @NotNull String getIdentifier() { return Adapt.instance.getDescription().getName().toLowerCase(); }
    @Override
    public @NotNull String getAuthor() { return String.join(", ", Adapt.instance.getDescription().getAuthors()); }
    @Override
    public @NotNull String getVersion() { return Adapt.instance.getDescription().getVersion(); }
    @Override
    public boolean persist() { return true; }

    /* %adapt_<command/skill line>_[command/adaption]%
    Example for Brian's smooth brain:
    - %adapt_multiplier% - Returns the players multiplier
    - %adapt_hunter_level% - Returns the level of the players hunter skill
    - %adapt_nether_wither-resist% - Returns the level of the Wither Armor Adaptation
    */
    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String[] args = params.split("_");
        PlayerData p = Adapt.instance.getAdaptServer().peekData(player.getUniqueId());

        if(Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(args[0]) == null) {
            for(String k : playerMap.keySet()) {
                if(k.equalsIgnoreCase(args[0])) {
                    return playerMap.get(k).apply(p);
                }
            }
        } else {
            PlayerSkillLine line = p.getSkillLine(args[0]);
            for(String k : skillMap.keySet()) {
                if(k.equalsIgnoreCase(args[1])) {
                    return skillMap.get(k).apply(line);
                }
            }

            String adaptName = args[0] + "-" + args[1];
            if(line.getAdaptations().containsKey(adaptName)) {
                PlayerAdaptation adapt = line.getAdaptation(adaptName);
                for(String k : adaptMap.keySet()) {
                    if(k.equalsIgnoreCase(args[2])) {
                        return adaptMap.get(k).apply(line, adapt);
                    }
                }
            }
        }

        return null;
    }
}
