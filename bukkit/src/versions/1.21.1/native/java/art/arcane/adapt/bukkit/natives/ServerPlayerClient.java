package art.arcane.adapt.bukkit.natives;

import art.arcane.volmlib.nativelib.player.ClientBlockTags;
import art.arcane.volmlib.nativelib.player.PlayerClientAccess;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Client-only motion and block tags (Ladder Slide): a vertical velocity sent to the player alone, and the block tags
 * resent with one block left out of the climbable tag so the client stops treating it as a ladder.
 */
final class ServerPlayerClient implements PlayerClientAccess {
  private static volatile MethodHandle networkPayloadConstructor;

  @Override
  public boolean isServerPlayerClass(Class<?> playerClass) {
    return ServerHandles.isServerPlayerClass(playerClass);
  }

  @Override
  public boolean sendVerticalMotion(Player player, double verticalVelocity) {
    ServerPlayer handle = ServerHandles.player(player);
    if (handle == null) {
      return false;
    }
    Vec3 known = ServerHandles.knownMovement(handle);
    Vec3 target = new Vec3(known.x, verticalVelocity, known.z);
    handle.setDeltaMovement(target);
    handle.connection.send(new ClientboundSetEntityMotionPacket(handle.getId(), target));
    return true;
  }

  @Override
  public ClientBlockTags createBlockTags() {
    return new ClientBlockTags() {
      @Override
      public boolean sendClimbingState(Player player, Material suppressedMaterial) {
        ServerPlayer handle = ServerHandles.player(player);
        return handle != null && sendClimbable(handle, suppressedMaterial);
      }

      @Override
      public void invalidate() {
      }
    };
  }

  /** Tags are read fresh on every send, so a datapack reload is picked up. */
  private static boolean sendClimbable(ServerPlayer player, Material suppressedMaterial) {
    Registry<Block> registry = player.server.registryAccess().registryOrThrow(Registries.BLOCK);
    int suppressedId = -1;
    if (suppressedMaterial != null) {
      NamespacedKey key = suppressedMaterial.getKey();
      Optional<Block> block = registry.getOptional(ResourceLocation.tryBuild(key.getNamespace(), key.getKey()));
      if (block.isEmpty()) {
        throw new IllegalStateException("Missing client block registry entry " + key);
      }
      suppressedId = registry.getId(block.get());
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
    player.connection.send(ServerPackets.blockTags(networkPayload(Map.copyOf(tags))));
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
