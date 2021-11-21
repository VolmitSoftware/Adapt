package com.volmit.adapt.api.world;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.KMap;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;

@Data
@NoArgsConstructor
public class PlayerData {
    private final KMap<String, PlayerSkillLine> skillLines = new KMap<>();
    private String last = "none";
    private Discovery<Biome> seenBiomes = new Discovery<>();
    private Discovery<EntityType> seenMobs = new Discovery<>();
    private Discovery<Material> seenFoods = new Discovery<>();
    private Discovery<Material> seenItems = new Discovery<>();
    private Discovery<String> seenEnchants = new Discovery<>();
    private Discovery<String> seenWorlds = new Discovery<>();
    private Discovery<String> seenPeople = new Discovery<>();
    private Discovery<World.Environment> seenEnvironments = new Discovery<>();
    private Discovery<String> seenPotionEffects = new Discovery<>();
    private Discovery<String> seenBlocks = new Discovery<>();
    private long wisdom = 0;

    public void update(AdaptPlayer p) {
        for(String i : skillLines.k()) {
            if(getSkillLine(i) == null) {
                skillLines.remove(i);
                Adapt.warn("Removed unknown skill line '" + i + "' from " + p.getPlayer().getName());
                continue;
            }

            if(getSkillLine(i).getXp() == 0 && getSkillLine(i).getKnowledge() == 0) {
                skillLines.remove(i);
                continue;
            }

            getSkillLine(i).update(p, i);
        }
    }

    public PlayerSkillLine getSkillLine(String skillLine) {
        if(Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(skillLine) == null) {
            return null;
        }

        synchronized(skillLines) {
            try {
                PlayerSkillLine s = skillLines.get(skillLine);

                if(s != null) {
                    return s;
                }
            } catch(Throwable e) {
                e.printStackTrace();
                Adapt.error("Failed to get skill line " + skillLine);
            }

            PlayerSkillLine s = new PlayerSkillLine();
            s.setLine(skillLine);
            skillLines.put(skillLine, s);
            return s;
        }
    }

    public void addWisdom() {
        wisdom++;
    }
}
