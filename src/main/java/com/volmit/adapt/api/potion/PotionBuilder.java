package com.volmit.adapt.api.potion;

import com.google.common.collect.Lists;
import com.volmit.adapt.api.version.Version;
import com.volmit.adapt.util.reflect.registries.PotionTypes;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Getter
public class PotionBuilder {

    private final List<PotionEffect> effects = Lists.newArrayList();
    private final Type type;

    private Component name;
    private List<Component> lore;
    private Color color;
    private PotionType baseType = PotionTypes.UNCRAFTABLE;
    private ItemStack baseItem;

    private PotionBuilder(Type type) {
        this.type = type;
    }

    public static ItemStack vanilla(@NotNull Type type, @NotNull PotionType potion) {
        return of(type)
                .setBaseType(potion)
                .build();
    }

    public static PotionBuilder of(@NotNull Type type) {
        return new PotionBuilder(type);
    }

    public static PotionBuilder of(@NotNull ItemStack item) {
        return Version.get().editPotion(item);
    }

    public PotionBuilder setColor(@Nullable Color color) {
        this.color = color;
        return this;
    }

    public PotionBuilder addEffect(@NotNull PotionEffect effect) {
        effects.add(effect);
        return this;
    }

    public PotionBuilder addEffect(PotionEffectType type, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {
        effects.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
        return this;
    }

    public PotionBuilder setName(@Nullable String name) {
        this.name = name != null ? Component.text(name)
                .decoration(TextDecoration.ITALIC, false) : null;
        return this;
    }

    public PotionBuilder setName(@Nullable Component name) {
        this.name = name;
        return this;
    }

    public PotionBuilder addLore(@NotNull Component lore) {
        if(this.lore == null) {
            this.lore = Lists.newArrayList();
        }

        this.lore.add(lore);
        return this;
    }

    public PotionBuilder setLore(@Nullable List<@NotNull Component> lore) {
        this.lore = lore;
        return this;
    }

    public PotionBuilder setBaseItem(@Nullable ItemStack item) {
        this.baseItem = item;
        return this;
    }

    public PotionBuilder setBaseType(@Nullable PotionType data) {
        this.baseType = data;
        return this;
    }

    @SuppressWarnings("ConstantConditions")
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
