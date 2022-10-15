package com.volmit.adapt.api.potion;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.List;

public class PotionBuilder {

    private final List<PotionEffect> effects = Lists.newArrayList();
    private final Type type;

    private String name;
    private Color color;
    private boolean upgraded, extended;
    private PotionType baseType = PotionType.UNCRAFTABLE;

    private PotionBuilder(Type type) {
        this.type = type;
    }

    public static ItemStack vanilla(Type type, PotionType potion, boolean extended, boolean upgraded) {
        return of(type)
                .setFlags(extended, upgraded)
                .setBaseType(potion)
                .build();
    }

    public static PotionBuilder of(Type type) {
        return new PotionBuilder(type);
    }

    public PotionBuilder setColor(Color color) {
        this.color = color;
        return this;
    }

    public PotionBuilder addEffect(PotionEffectType type, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {
        effects.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
        return this;
    }

    public PotionBuilder setFlags(boolean extended, boolean upgraded) {
        this.upgraded = upgraded;
        this.extended = extended;
        return this;
    }

    public PotionBuilder setName(String name) {
        this.name = name;
        return this;
    }

    private PotionBuilder setBaseType(PotionType data) {
        this.baseType = data;
        return this;
    }

    @SuppressWarnings("ConstantConditions")
    public ItemStack build() {
        ItemStack stack = new ItemStack(type.material);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        effects.forEach(e -> meta.addCustomEffect(e, true));
        if (color != null)
            meta.setColor(color);
        meta.setBasePotionData(new PotionData(baseType, extended, upgraded));
        if (name != null)
            meta.setDisplayName("§r" + name);
        stack.setItemMeta(meta);
        return stack;
    }

    @AllArgsConstructor
    public enum Type {
        REGULAR(Material.POTION),
        SPLASH(Material.SPLASH_POTION),
        LINGERING(Material.LINGERING_POTION);

        private final Material material;
    }
}
