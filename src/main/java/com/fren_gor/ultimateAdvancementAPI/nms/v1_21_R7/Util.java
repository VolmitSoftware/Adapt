package com.fren_gor.ultimateAdvancementAPI.nms.v1_21_R7;

import art.arcane.adapt.Adapt;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Util {
    private Util() {
        throw new UnsupportedOperationException("Utility class.");
    }

    /**
     * Values are NMS Criterion instances created reflectively because Criterion and
     * ImpossibleTrigger live in net.minecraft.advancements(.criterion) on 26.1.x and in
     * net.minecraft.advancements.triggers on 26.2+. Keeping the map value type Object
     * keeps both packages out of the compiled bytecode.
     */
    @NotNull
    public static Map<String, Object> getAdvancementCriteria(@Range(from = 1, to = Integer.MAX_VALUE) int maxProgression) {
        Preconditions.checkArgument(maxProgression >= 1, "Max progression must be >= 1.");

        Map<String, Object> advCriteria = Maps.newHashMapWithExpectedSize(maxProgression);
        for (int i = 0; i < maxProgression; i++) {
            advCriteria.put(String.valueOf(i), ImpossibleCriteria.create());
        }

        return advCriteria;
    }

    @NotNull
    public static AdvancementRequirements getAdvancementRequirements(@NotNull Map<String, Object> advCriteria) {
        Preconditions.checkNotNull(advCriteria, "Advancement criteria map is null.");

        List<List<String>> list = new ArrayList<>(advCriteria.size());
        for (String name : advCriteria.keySet()) {
            list.add(List.of(name));
        }

        return new AdvancementRequirements(list);
    }

    @NotNull
    public static AdvancementProgress getAdvancementProgress(@NotNull AdvancementHolder mcAdv, @Range(from = 0, to = Integer.MAX_VALUE) int progression) {
        Preconditions.checkNotNull(mcAdv, "NMS Advancement is null.");
        Preconditions.checkArgument(progression >= 0, "Progression must be >= 0.");

        AdvancementProgress advPrg = new AdvancementProgress();
        advPrg.update(mcAdv.value().requirements());

        for (int i = 0; i < progression; i++) {
            CriterionProgress criteriaPrg = advPrg.getCriterion(String.valueOf(i));
            if (criteriaPrg != null) {
                criteriaPrg.grant();
            }
        }

        return advPrg;
    }

    @NotNull
    public static Component fromString(@NotNull String string) {
        if (string.isEmpty()) {
            return CommonComponents.EMPTY;
        }

        return CraftChatMessage.fromStringOrNull(string, true);
    }

    @NotNull
    public static Component fromComponent(@NotNull BaseComponent component) {
        Component base = CraftChatMessage.fromJSONOrNull(ComponentSerializer.toString(component));
        return base == null ? CommonComponents.EMPTY : base;
    }

    @Nullable
    public static ClientAsset.ResourceTexture parseBackgroundTexture(@Nullable String backgroundTexture) {
        if (backgroundTexture == null) {
            return null;
        }

        Identifier texturePath = Identifier.parse(backgroundTexture);
        if (!texturePath.getPath().startsWith("textures/") || !texturePath.getPath().endsWith(".png")) {
            Adapt.error("Invalid advancement background texture \"" + backgroundTexture
                    + "\"; expected textures/**.png.");
            return null;
        }

        Identifier id = texturePath.withPath(path -> path.substring("textures/".length(), path.length() - ".png".length()));
        return new ClientAsset.ResourceTexture(id, texturePath);
    }

    public static void sendTo(@NotNull Player player, @NotNull Packet<?> packet) {
        Preconditions.checkNotNull(player, "Player is null.");
        Preconditions.checkNotNull(packet, "Packet is null.");
        ((CraftPlayer) player).getHandle().connection.send(packet);
    }

    /**
     * Dual-location resolution for the impossible-criterion types:
     * 26.2+ has net.minecraft.advancements.triggers.{Criterion, ImpossibleTrigger};
     * 26.1.x has net.minecraft.advancements.Criterion and
     * net.minecraft.advancements.criterion.ImpossibleTrigger. MethodHandles are
     * resolved once at class initialization.
     */
    private static final class ImpossibleCriteria {
        private static final MethodHandle CRITERION_CONSTRUCTOR;
        private static final MethodHandle TRIGGER_CONSTRUCTOR;
        private static final MethodHandle INSTANCE_CONSTRUCTOR;

        static {
            try {
                Class<?> criterionClass = firstClass(
                        "net.minecraft.advancements.triggers.Criterion",
                        "net.minecraft.advancements.Criterion");
                Class<?> triggerClass = firstClass(
                        "net.minecraft.advancements.triggers.ImpossibleTrigger",
                        "net.minecraft.advancements.criterion.ImpossibleTrigger");
                Class<?> instanceClass = firstClass(
                        "net.minecraft.advancements.triggers.ImpossibleTrigger$TriggerInstance",
                        "net.minecraft.advancements.criterion.ImpossibleTrigger$TriggerInstance");
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                CRITERION_CONSTRUCTOR = lookup.unreflectConstructor(criterionClass.getDeclaredConstructors()[0]);
                TRIGGER_CONSTRUCTOR = lookup.findConstructor(triggerClass, MethodType.methodType(void.class));
                INSTANCE_CONSTRUCTOR = lookup.findConstructor(instanceClass, MethodType.methodType(void.class));
            } catch (ReflectiveOperationException error) {
                throw new ExceptionInInitializerError(error);
            }
        }

        private ImpossibleCriteria() {
        }

        static Object create() {
            try {
                return CRITERION_CONSTRUCTOR.invoke(TRIGGER_CONSTRUCTOR.invoke(), INSTANCE_CONSTRUCTOR.invoke());
            } catch (Throwable error) {
                throw new IllegalStateException("Unable to create an impossible advancement criterion.", error);
            }
        }

        private static Class<?> firstClass(String preferred, String fallback) throws ClassNotFoundException {
            try {
                return Class.forName(preferred);
            } catch (ClassNotFoundException absent) {
                return Class.forName(fallback);
            }
        }
    }
}
