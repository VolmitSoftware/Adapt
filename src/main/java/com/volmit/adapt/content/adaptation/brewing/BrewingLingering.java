/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.adapt.content.adaptation.brewing;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.data.WorldData;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerData;
import com.volmit.adapt.content.matter.BrewingStandOwner;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.collection.KList;
import com.volmit.adapt.util.reflect.registries.ItemFlags;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class BrewingLingering extends SimpleAdaptation<BrewingLingering.Config> {
    private static final Function<PotionEffectType, TextColor> getColor;
    private static final Function<PotionEffectType, Map<Attribute, AttributeModifier>> getEffectAttributes;
    private static final Function3<PotionEffectType, Attribute, Integer, Double> getAttributeModifierAmount;
    private static final DecimalFormat ATTRIBUTE_MODIFIER_FORMAT = new DecimalFormat("#.##");

    public BrewingLingering() {
        super("brewing-lingering");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("brewing.lingering.description"));
        setDisplayName(Localizer.dLocalize("brewing.lingering.name"));
        setIcon(Material.CLOCK);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(4788);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LINGERING_POTION)
                .key("challenge_brewing_lingering_200")
                .title(Localizer.dLocalize("advancement.challenge_brewing_lingering_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_brewing_lingering_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DRAGON_BREATH)
                        .key("challenge_brewing_lingering_5k")
                        .title(Localizer.dLocalize("advancement.challenge_brewing_lingering_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_brewing_lingering_5k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_brewing_lingering_200").goal(200).stat("brewing.lingering.potions-extended").reward(300).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_brewing_lingering_5k").goal(5000).stat("brewing.lingering.potions-extended").reward(1000).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.duration((long) getDurationBoost(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("brewing.lingering.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getPercentBoost(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("brewing.lingering.lore2"));
    }

    public double getDurationBoost(double factor) {
        return (getConfig().durationBoostFactorTicks * factor) + getConfig().baseDurationBoostTicks;
    }

    public double getPercentBoost(double factor) {
        return 1 + ((factor * factor * getConfig().durationMultiplierFactor) + getConfig().baseDurationMultiplier);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BrewEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!e.getBlock().getType().equals(Material.BREWING_STAND)) {
            return;
        }
        BrewingStandOwner owner = WorldData.of(e.getBlock().getWorld()).get(e.getBlock(), BrewingStandOwner.class);

        if (owner == null) {
            Adapt.verbose("No Owner");
            return;
        }

        PlayerData data = null;
        var results = e.getResults();
        boolean ef = false;
        for (int i = 0; i < results.size(); i++) {
            ItemStack is = results.get(i);

            if (is == null || is.getItemMeta() == null || !(is.getItemMeta() instanceof PotionMeta p))
                continue;

            data = data == null ? getServer().peekData(owner.getOwner()) : data;

            if (data.getSkillLines().containsKey(getSkill().getName()) && data.getSkillLine(getSkill().getName()).getAdaptations().containsKey(getName())) {
                PlayerAdaptation a = data.getSkillLine(getSkill().getName()).getAdaptations().get(getName());

                if (a.getLevel() > 0) {
                    double factor = getLevelPercent(a.getLevel());
                    boolean enhanced = enhance(factor, is, p);
                    if (enhanced) {
                        data.addStat("brewing.lingering.potions-extended", 1);
                    }
                    ef = enhanced || ef;
                    results.set(i, is);
                }
            }
        }

        if (ef) {
            SoundPlayer spw = SoundPlayer.of(e.getBlock().getWorld());
            spw.play(e.getBlock().getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1f, 0.75f);
            spw.play(e.getBlock().getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1f, 1.75f);
        }
    }

    private boolean enhance(double factor, ItemStack is, PotionMeta p) {
        var effects = p.getBasePotionType().getPotionEffects();
        if (effects.stream()
                .map(PotionEffect::getType)
                .allMatch(PotionEffectType::isInstant))
            return false;

        p.clearCustomEffects();
        for (final PotionEffect effect : effects) {
            if (effect.getType().isInstant()) {
                p.addCustomEffect(effect, true);
                continue;
            }

            p.addCustomEffect(new PotionEffect(
                    effect.getType(),
                    (int) (getDurationBoost(factor) + (effect.getDuration() * getPercentBoost(factor))),
                    effect.getAmplifier()
            ), true);
        }

        p.addItemFlags(ItemFlags.HIDE_POTION_EFFECTS);
        is.setItemMeta(p);

        if (getConfig().useCustomLore) {
            KList<Component> lore = new KList<>();
            KList<Modifier> modifiers = new KList<>();
            for (var effect : p.getCustomEffects()) {
                var type = effect.getType();
                var key = type.getKey();
                var name = Component.translatable("effect." + key.getNamespace() + "." + key.getKey());
                if (effect.getAmplifier() > 0) {
                    name = Component.translatable("potion.withAmplifier", name,
                            Component.translatable("potion.potency." + effect.getAmplifier()));
                }

                if (effect.getDuration() > 20) {
                    name = Component.translatable("potion.withDuration", name, formatDuration(effect));
                }

                lore.add(name.color(getColor.apply(type)));
                getEffectAttributes.apply(type)
                        .entrySet()
                        .stream()
                        .map(Modifier::new)
                        .map(m -> m.adjust(type, effect.getAmplifier()))
                        .filter(m -> m.amount != 0)
                        .forEach(modifiers::add);
            }

            if (!modifiers.isEmpty()) {
                lore.add(Component.empty());
                lore.add(Component.translatable("potion.whenDrank").color(NamedTextColor.DARK_PURPLE));

                for (Modifier modifier : modifiers) {
                    double amount = modifier.amount;
                    var formatted = Component.text(ATTRIBUTE_MODIFIER_FORMAT.format(modifier.operation == AttributeModifier.Operation.ADD_NUMBER ? amount : amount * 100d));
                    var name = Component.translatable("attribute.name." + modifier.attribute.getKey().getKey());

                    if (amount > 0) {
                        lore.add(Component.translatable("attribute.modifier.plus." + modifier.operation.ordinal(), formatted, name)
                                .color(NamedTextColor.BLUE));
                    } else {
                        lore.add(Component.translatable("attribute.modifier.take." + modifier.operation.ordinal(), formatted, name)
                                .color(NamedTextColor.RED));
                    }
                }
            }
            lore.replaceAll(c -> c.decoration(TextDecoration.ITALIC, false));

            Adapt.platform.editItem(is)
                    .lore(lore)
                    .build();
        }

        return true;
    }

    private Component formatDuration(PotionEffect effect) {
        if (effect.isInfinite()) {
            return Component.translatable("effect.duration.infinite");
        } else {
            int seconds = effect.getDuration() / 20;
            int minutes = seconds / 60;
            seconds %= 60;
            int hours = minutes / 60;
            minutes %= 60;
            return Component.text(hours > 0 ?
                    "%02d:%02d:%02d".formatted(hours, minutes, seconds) :
                    "%02d:%02d".formatted(minutes, seconds));
        }
    }


    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Brewed potions last longer.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.75;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Duration Boost Ticks for the Brewing Lingering adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseDurationBoostTicks = 100;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Duration Boost Factor Ticks for the Brewing Lingering adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double durationBoostFactorTicks = 500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Duration Multiplier Factor for the Brewing Lingering adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double durationMultiplierFactor = 0.45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Duration Multiplier for the Brewing Lingering adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseDurationMultiplier = 0.05;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Use Custom Lore for the Brewing Lingering adaptation.", impact = "True enables this behavior and false disables it.")
        boolean useCustomLore = true;
    }

    private record Modifier(Attribute attribute, AttributeModifier.Operation operation, double amount) {
        private Modifier(Map.Entry<Attribute, AttributeModifier> entry) {
            this(entry.getKey(), entry.getValue());
        }

        private Modifier(Attribute attribute, AttributeModifier modifier) {
            this(attribute, modifier.getOperation(), modifier.getAmount());
        }

        private Modifier adjust(PotionEffectType type, int amplifier) {
            return new Modifier(
                    attribute,
                    operation,
                    getAttributeModifierAmount.apply(type, attribute, amplifier)
            );
        }
    }

    static {
        var lookup = MethodHandles.lookup();
        MethodHandle getCategory;
        try {
            var method = PotionEffectType.class.getDeclaredMethod("getCategory");
            getCategory = lookup.unreflect(method);
        } catch (Throwable ignored) {
            getCategory = null;
        }

        MethodHandle modifiersHandle;
        MethodHandle amountHandle;
        try {
            modifiersHandle = lookup.findVirtual(PotionEffectType.class, "getEffectAttributes", MethodType.methodType(Map.class));
            amountHandle = lookup.findVirtual(PotionEffectType.class, "getAttributeModifierAmount", MethodType.methodType(double.class, Attribute.class, int.class));
        } catch (Throwable ignored) {
            Adapt.verbose("Failed to find attributes for potion effect type");
            modifiersHandle = null;
            amountHandle = null;
        }

        if (getCategory != null) {
            MethodHandle handle = getCategory;
            getColor = type -> {
                try {
                    return ((Enum<?>) handle.invoke(type)).ordinal() == 1 ? NamedTextColor.RED : NamedTextColor.BLUE;
                } catch (Throwable err) {
                    throw new RuntimeException(err);
                }
            };
        } else getColor = $ -> NamedTextColor.BLUE;

        if (modifiersHandle != null) {
            MethodHandle handle = modifiersHandle;
            getEffectAttributes = type -> {
                try {
                    return (Map<Attribute, AttributeModifier>) handle.invoke(type);
                } catch (Throwable err) {
                    throw new RuntimeException(err);
                }
            };
        } else getEffectAttributes = $ -> Map.of();

        if (amountHandle != null) {
            MethodHandle handle = amountHandle;
            getAttributeModifierAmount = (type, attribute, level) -> {
                try {
                    return (double) handle.invoke(type, attribute, level);
                } catch (Throwable err) {
                    throw new RuntimeException(err);
                }
            };
        } else getAttributeModifierAmount = ($, $$, $$$) -> 0d;

        ATTRIBUTE_MODIFIER_FORMAT.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ROOT));
    }
}
