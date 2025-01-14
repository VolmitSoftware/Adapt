package com.volmit.adapt.api.potion;

import com.google.common.collect.Lists;
import com.volmit.adapt.api.version.Version;
import com.volmit.adapt.util.reflect.Reflect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.List;

@Getter
public class PotionBuilder {

    private final List<PotionEffect> effects = Lists.newArrayList();
    private final Type type;

    private String name;
    private Color color;
    private boolean upgraded, extended;
    private PotionType baseType = Reflect.getEnum(PotionType.class, "UNCRAFTABLE").orElse(null);

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

    public ItemStack build() {
        return Version.get().buildPotion(this);
    }

    @Getter
    @AllArgsConstructor
    public enum Type {
        REGULAR(Material.POTION),
        SPLASH(Material.SPLASH_POTION),
        LINGERING(Material.LINGERING_POTION);

        private final Material material;
    }
}
