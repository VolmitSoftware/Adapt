package art.arcane.adapt.mod.event;

import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/** Members the event hooks use whose shape differs between the supported Minecraft versions. */
public final class HookCompat {
    private HookCompat() {
    }

    public static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().name();
    }

    static ServerLevel level(ServerPlayer player) {
        return player.level();
    }

    /** Moves the player to a location, in its level or another one. True when the player moved. */
    static boolean teleport(ServerPlayer player, ServerLevel level, Loc to) {
        return player.teleport(new TeleportTransition(level, new Vec3(to.x(), to.y(), to.z()), Vec3.ZERO, to.yaw(), to.pitch(),
            TeleportTransition.DO_NOTHING)) != null;
    }

    /** Returns the transition to use, or null when a handler cancelled the teleport. */
    public static TeleportTransition teleporting(ServerPlayer player, TeleportTransition transition) {
        if (!PlayerHooks.teleportWatched(player)) {
            return transition;
        }
        ServerLevel to = transition.newLevel();
        Set<Relative> relatives = transition.relatives();
        Vec3 position = transition.position();
        double x = relatives.contains(Relative.X) ? player.getX() + position.x : position.x;
        double y = relatives.contains(Relative.Y) ? player.getY() + position.y : position.y;
        double z = relatives.contains(Relative.Z) ? player.getZ() + position.z : position.z;
        float yaw = relatives.contains(Relative.Y_ROT) ? player.getYRot() + transition.yRot() : transition.yRot();
        float pitch = relatives.contains(Relative.X_ROT) ? player.getXRot() + transition.xRot() : transition.xRot();
        Loc target = ModRefs.loc(to, new Vec3(x, y, z), yaw, pitch);
        Loc chosen = PlayerHooks.teleporting(player, to, target);
        if (chosen == null) {
            return null;
        }
        if (chosen == target) {
            return transition;
        }
        ServerLevel level = ModRefs.world(chosen.world());
        if (level == null) {
            return transition;
        }
        return new TeleportTransition(level, new Vec3(chosen.x(), chosen.y(), chosen.z()), transition.deltaMovement(), chosen.yaw(),
            chosen.pitch(), transition.missingRespawnBlock(), transition.asPassenger(), Set.of(), transition.postTeleportTransition());
    }

    /** The cause for a portal the entity is about to pass through. */
    public static TeleportCause portalCause(Portal portal) {
        if (portal instanceof NetherPortalBlock) {
            return TeleportCause.NETHER_PORTAL;
        }
        if (portal instanceof EndGatewayBlock) {
            return TeleportCause.END_GATEWAY;
        }
        if (portal instanceof EndPortalBlock) {
            return TeleportCause.END_PORTAL;
        }
        return null;
    }

    /** The dimension of the player's respawn point, or null when it has none. */
    static ResourceKey<Level> respawnDimension(ServerPlayer player) {
        ServerPlayer.RespawnConfig config = player.getRespawnConfig();
        return config == null ? null : config.respawnData().dimension();
    }

    static BlockPos respawnPosition(ServerPlayer player) {
        ServerPlayer.RespawnConfig config = player.getRespawnConfig();
        return config == null ? null : config.respawnData().pos();
    }

    static int selectedSlot(ServerPlayer player) {
        return player.getInventory().getSelectedSlot();
    }

    static Packet<?> selectedSlotPacket(int slot) {
        return new ClientboundSetHeldSlotPacket(slot);
    }

    static RandomSource random(Entity entity) {
        return entity.getRandom();
    }

    static double blockReach(ServerPlayer player) {
        return player.blockInteractionRange();
    }

    static double entityReach(ServerPlayer player) {
        return player.entityInteractionRange();
    }

    static boolean sameItem(ItemStack first, ItemStack second) {
        return ItemStack.isSameItemSameComponents(first, second);
    }

    static boolean infiniteMaterials(Player player) {
        return player.hasInfiniteMaterials();
    }

    static boolean preventsBlockDrops(Player player) {
        return player.preventsBlockDrops();
    }

    /** Whether the main-hand item lets the player break the block at all (swords in creative do not). */
    static boolean canDestroy(ServerPlayer player, BlockState state, ServerLevel level, BlockPos pos) {
        return player.getMainHandItem().canDestroyBlock(state, level, pos, player);
    }

    /** The entity id a spawner block entity holds, or null when it holds none. */
    static String spawnerEntity(SpawnerBlockEntity spawner, ServerLevel level) {
        CompoundTag saved = spawner.saveWithoutMetadata(level.registryAccess());
        return saved.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity").getString("id").orElse(null);
    }

    static boolean craftingMenu(AbstractContainerMenu menu) {
        return menu instanceof AbstractCraftingMenu;
    }

    static List<Slot> craftingGrid(AbstractContainerMenu menu) {
        return ((AbstractCraftingMenu) menu).getInputGridSlots();
    }

    /** The id of the recipe a result container last crafted, or null. */
    static Id recipeId(Container container) {
        RecipeHolder<?> recipe = container instanceof ResultContainer result ? result.getRecipeUsed() : null;
        return recipe == null ? null : ModRefs.id(recipe.id().identifier());
    }

    static CraftItemEvent.RecipeKind recipeKind(Container container) {
        RecipeHolder<?> recipe = container instanceof ResultContainer result ? result.getRecipeUsed() : null;
        return recipe == null ? CraftItemEvent.RecipeKind.OTHER
            : recipe.value() instanceof ShapedRecipe ? CraftItemEvent.RecipeKind.SHAPED
            : recipe.value() instanceof ShapelessRecipe ? CraftItemEvent.RecipeKind.SHAPELESS
            : CraftItemEvent.RecipeKind.OTHER;
    }

    static Id enchantmentId(EnchantmentInstance instance) {
        return ModConversions.enchantmentId(instance.enchantment());
    }

    static int enchantmentLevel(EnchantmentInstance instance) {
        return instance.level();
    }

    /** An enchantment instance for a registered enchantment, or null for an unknown id. */
    static EnchantmentInstance enchantment(Id id, int level) {
        Holder<Enchantment> enchantment = ModConversions.enchantment(id);
        return enchantment == null ? null : new EnchantmentInstance(enchantment, level);
    }

    static Id id(ResourceKey<?> key) {
        return ModRefs.id(key.identifier());
    }
}
