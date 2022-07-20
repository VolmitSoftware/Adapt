package com.volmit.adapt.content.item;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.item.DataItem;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Form;
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
public class ExperienceOrb implements DataItem<ExperienceOrb.Data> {
    public static ExperienceOrb io = new ExperienceOrb();

    @Override
    public Material getMaterial() {
        return Material.SNOWBALL;
    }

    @Override
    public Class<Data> getType() {
        return ExperienceOrb.Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(data.skill);
        lore.add(C.WHITE + "Contains "  + C.UNDERLINE + C.WHITE + Form.f(data.experience, 0) + " " + skill.getDisplayName() +  C.GRAY+  " XP");
        lore.add(C.LIGHT_PURPLE + "Right Click " + C.GRAY + "to gain this experience");
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {
        meta.addEnchant(Enchantment.BINDING_CURSE, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setDisplayName(Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(data.skill).getDisplayName() + " XP Orb");
    }

    public static Data get(ItemStack is)
    {
        return io.getData(is);
    }

    public static ItemStack set(ItemStack item, String skill, double xp) {
        return io.setData(item, new Data(skill, xp));
    }

    public static ItemStack with(String skill, double xp) {
        return io.withData(new Data(skill, xp));
    }

    @AllArgsConstructor
    @lombok.Data
    public static class Data {
        private String skill;
        private double experience;

        public void apply(Player p) {
            Adapt.instance.getAdaptServer().getPlayer(p).getSkillLine(skill).giveXP(Adapt.instance.getAdaptServer().getPlayer(p).getNot(), experience);
        }
    }
}
