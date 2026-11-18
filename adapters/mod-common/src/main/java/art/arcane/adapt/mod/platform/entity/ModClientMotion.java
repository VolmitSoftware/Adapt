package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.platform.value.Id;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
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
        Registry<Block> registry = player.level().getServer().registryAccess().lookupOrThrow(Registries.BLOCK);
        int suppressedId = -1;
        if (block != null) {
            Optional<Holder.Reference<Block>> holder = registry.get(ModConversions.identifier(block));
            if (holder.isEmpty()) {
                return false;
            }
            suppressedId = registry.getId(holder.get().value());
        }

        Identifier climbable = BlockTags.CLIMBABLE.location();
        Map<Identifier, IntList> tags = new HashMap<>();
        for (HolderSet.Named<Block> named : registry.getTags().toList()) {
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

    /** The NetworkPayload(Map) constructor is package-private on 26.1.x and public on 26.2+. */
    private static TagNetworkSerialization.NetworkPayload networkPayload(Map<Identifier, IntList> tags) {
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
