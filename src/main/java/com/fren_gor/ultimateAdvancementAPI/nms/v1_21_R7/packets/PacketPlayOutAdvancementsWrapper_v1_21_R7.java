package com.fren_gor.ultimateAdvancementAPI.nms.v1_21_R7.packets;

import com.fren_gor.ultimateAdvancementAPI.nms.util.ListSet;
import com.fren_gor.ultimateAdvancementAPI.nms.v1_21_R7.Util;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.MinecraftKeyWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.packets.PacketPlayOutAdvancementsWrapper;
import com.google.common.collect.Maps;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class PacketPlayOutAdvancementsWrapper_v1_21_R7 extends PacketPlayOutAdvancementsWrapper {
    private static final MethodHandle POSITIONED_ADVANCEMENT = positionedAdvancementConstructor();
    private static final MethodHandle PACKET_CONSTRUCTOR = packetConstructor();

    private final ClientboundUpdateAdvancementsPacket packet;

    public PacketPlayOutAdvancementsWrapper_v1_21_R7() {
        this.packet = createPacket(true, Collections.emptyList(), Collections.emptySet(), Collections.emptyMap());
    }

    public PacketPlayOutAdvancementsWrapper_v1_21_R7(@NotNull Map<AdvancementWrapper, Integer> toSend) {
        Map<Identifier, AdvancementProgress> map = Maps.newHashMapWithExpectedSize(toSend.size());
        List<Object> additions = new ArrayList<>(toSend.size());
        for (Entry<AdvancementWrapper, Integer> e : toSend.entrySet()) {
            AdvancementWrapper adv = e.getKey();
            AdvancementHolder holder = (AdvancementHolder) adv.toNMS();
            map.put((Identifier) adv.getKey().toNMS(), Util.getAdvancementProgress(holder, e.getValue()));
            additions.add(positionAdvancement(holder, adv.getDisplay().getX(), adv.getDisplay().getY()));
        }

        this.packet = createPacket(false, additions, Collections.emptySet(), map);
    }

    @SuppressWarnings("unchecked")
    public PacketPlayOutAdvancementsWrapper_v1_21_R7(@NotNull Set<MinecraftKeyWrapper> toRemove) {
        this.packet = createPacket(false, Collections.emptyList(), (Set<Identifier>) ListSet.fromWrapperSet(toRemove), Collections.emptyMap());
    }

    @Override
    public void sendTo(@NotNull Player player) {
        Util.sendTo(player, packet);
    }

    private static MethodHandle positionedAdvancementConstructor() {
        try {
            Class<?> positionedType = Class.forName("net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket$PositionedAdvancement");
            return MethodHandles.publicLookup().findConstructor(positionedType,
                    MethodType.methodType(void.class, AdvancementHolder.class, float.class, float.class));
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static MethodHandle packetConstructor() {
        try {
            return MethodHandles.publicLookup().findConstructor(ClientboundUpdateAdvancementsPacket.class,
                    MethodType.methodType(void.class, boolean.class,
                            POSITIONED_ADVANCEMENT == null ? Collection.class : List.class,
                            Set.class, Map.class, boolean.class));
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static Object positionAdvancement(AdvancementHolder advancement, float x, float y) {
        if (POSITIONED_ADVANCEMENT == null) {
            return advancement;
        }
        try {
            return POSITIONED_ADVANCEMENT.invoke(advancement, x, y);
        } catch (Throwable error) {
            throw new IllegalStateException("Unable to position the advancement packet.", error);
        }
    }

    private static ClientboundUpdateAdvancementsPacket createPacket(boolean reset, List<Object> additions,
            Set<Identifier> removals, Map<Identifier, AdvancementProgress> progress) {
        try {
            return (ClientboundUpdateAdvancementsPacket) PACKET_CONSTRUCTOR.invoke(reset, additions, removals, progress, true);
        } catch (Throwable error) {
            throw new IllegalStateException("Unable to create the advancement update packet.", error);
        }
    }
}
