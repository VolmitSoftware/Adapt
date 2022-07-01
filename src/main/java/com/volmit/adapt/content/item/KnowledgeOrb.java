package com.volmit.adapt.content.item;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.item.DataItem;
import com.volmit.adapt.util.C;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

@AllArgsConstructor
@Data
public class KnowledgeOrb implements DataItem<KnowledgeOrb.Data> {
    public static KnowledgeOrb io = new KnowledgeOrb();

    @Override
    public Material getMaterial() {
        return Material.SNOWBALL;
    }

    @Override
    public Class<Data> getType() {
        return KnowledgeOrb.Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        lore.add(C.WHITE + "Contains "  + C.UNDERLINE + C.WHITE + ""+ data.knowledge + " Knowledge");
        lore.add(C.LIGHT_PURPLE + "Right Click " + C.GRAY + "to gain this knowledge");
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {
        meta.addEnchant(Enchantment.BINDING_CURSE, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setDisplayName(Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(data.skill).getDisplayName() + " Knowledge Orb");
    }

    public static Data get(ItemStack is)
    {
        return io.getData(is);
    }

    public static String getSkill(ItemStack stack) {
        if(io.getData(stack) != null) {
            return io.getData(stack).getSkill();
        }

        return null;
    }

    public static long getKnowledge(ItemStack stack) {
        if(io.getData(stack) != null) {
            return io.getData(stack).getKnowledge();
        }

        return 0;
    }

    public static void set(ItemStack item, String skill, int knowledge) {
        io.setData(item, new Data(skill, knowledge));
    }

    public static ItemStack with(String skill, int knowledge) {
        return io.withData(new Data(skill, knowledge));
    }

    @AllArgsConstructor
    @lombok.Data
    public static class Data {
        private String skill;
        private int knowledge;

        public void apply(Player p) {
            Adapt.instance.getAdaptServer().getPlayer(p).getSkillLine(skill).giveKnowledge(knowledge);
        }
    }
}
