package com.volmit.adapt.api.world;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;

@Data
@NoArgsConstructor
public class PlayerData {
    private KMap<String, PlayerSkillLine> skillLines = new KMap<>();
    private String last = "none";
    private KList<Biome> seenBiomes = new KList<>();
    private KList<EntityType> seenMobs = new KList<>();
    private KList<Material> seenItems = new KList<>();
    private KList<String> seenBlocks = new KList<>();
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
