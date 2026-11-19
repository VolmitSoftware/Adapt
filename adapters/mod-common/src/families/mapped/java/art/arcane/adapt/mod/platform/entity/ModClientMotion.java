package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.ModContentAliases;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Client-only motion and block tag packets for {@link ModPlayerRef}. Tags are read fresh on every send. */
final class ModClientMotion {
    private static volatile MethodHandle networkPayloadConstructor;

    private ModClientMotion() {
    }

    static void sendVerticalMotion(ServerPlayer player, double verticalVelocity) {
        Vec3 known = player.getKnownMovement();
        Vec3 target = new Vec3(known.x, verticalVelocity, known.z);
        player.setDeltaMovement(target);
        player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), target));
    }

    static boolean sendClimbableTagOverride(ServerPlayer player, Id block) {
        Registry<Block> registry = player.server.registryAccess().registryOrThrow(Registries.BLOCK);
        int suppressedId = -1;
        if (block != null) {
            ResourceLocation location = ModContentAliases.location(registry, RegistryKind.BLOCK, block);
            Optional<Block> found = location == null ? Optional.empty() : registry.getOptional(location);
            if (found.isEmpty()) {
                return false;
            }
            suppressedId = registry.getId(found.get());
        }

        ResourceLocation climbable = BlockTags.CLIMBABLE.location();
        Map<ResourceLocation, IntList> tags = new HashMap<>();
        for (Pair<TagKey<Block>, HolderSet.Named<Block>> pair : registry.getTags().toList()) {
            HolderSet.Named<Block> named = pair.getSecond();
            boolean filter = suppressedId >= 0 && named.key().location().equals(climbable);
            IntList ids = new IntArrayList(named.size());
            for (Holder<Block> holder : named) {
                int id = registry.getId(holder.value());
                if (!filter || id != suppressedId) {
                    ids.add(id);
                }
            }
            tags.put(named.key().location(), ids);
        }

        Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> registryTags = new HashMap<>(1);
        registryTags.put(Registries.BLOCK, networkPayload(Map.copyOf(tags)));
        player.connection.send(new ClientboundUpdateTagsPacket(registryTags));
        return true;
    }

    /** The NetworkPayload(Map) constructor is package-private on this version. */
    private static TagNetworkSerialization.NetworkPayload networkPayload(Map<ResourceLocation, IntList> tags) {
        try {
            MethodHandle constructor = networkPayloadConstructor;
            if (constructor == null) {
                Constructor<TagNetworkSerialization.NetworkPayload> declared =
                        TagNetworkSerialization.NetworkPayload.class.getDeclaredConstructor(Map.class);
                declared.setAccessible(true);
                constructor = MethodHandles.lookup().unreflectConstructor(declared);
                networkPayloadConstructor = constructor;
            }
            return (TagNetworkSerialization.NetworkPayload) constructor.invoke(tags);
        } catch (Throwable error) {
            throw new IllegalStateException("Unable to construct a client block tag payload.", error);
        }
    }
}
