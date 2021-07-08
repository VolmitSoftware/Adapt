package com.volmit.adapt.api.world;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.util.KMap;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import javax.management.RuntimeErrorException;

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

    public void update(AdaptPlayer p)
    {
        for(String i : skillLines.k())
        {
            if(getSkillLine(i).getXp() == 0 && getSkillLine(i).getKnowledge() == 0)
            {
                skillLines.remove(i);
                continue;
            }

            getSkillLine(i).update(p, i);
        }
    }

    public PlayerSkillLine getSkillLine(String skillLine)
    {
        if(Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(skillLine) == null)
        {
            Adapt.error("Incorrect usage of getSkillLine(). MUST BE A SKILL. See below.");
            throw new RuntimeException();
        }

        synchronized (skillLines)
        {
            try
            {
                PlayerSkillLine s = skillLines.get(skillLine);

                if(s != null)
                {
                    return s;
                }
            }

            catch(Throwable e)
            {
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
