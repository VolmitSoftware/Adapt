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
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
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
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/** Members the event hooks use whose shape differs between the supported Minecraft versions. */
public final class HookCompat {
    private static final int CRAFTING_GRID_START = 1;
    private static final int CRAFTING_GRID_END = 10;

    private HookCompat() {
    }

    public static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    static ServerLevel level(ServerPlayer player) {
        return player.serverLevel();
    }

    /** Moves the player to a location, in its level or another one. True when the player moved. */
    static boolean teleport(ServerPlayer player, ServerLevel level, Loc to) {
        return player.teleportTo(level, to.x(), to.y(), to.z(), Set.of(), to.yaw(), to.pitch());
    }

    /** A dimension transition of a player. Returns the transition to use, or null when a handler cancelled the teleport. */
    public static DimensionTransition teleporting(ServerPlayer player, DimensionTransition transition) {
        if (!PlayerHooks.teleportWatched(player)) {
            return transition;
        }
        ServerLevel to = transition.newLevel();
        Loc target = ModRefs.loc(to, transition.pos(), transition.yRot(), transition.xRot());
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
        return new DimensionTransition(level, new Vec3(chosen.x(), chosen.y(), chosen.z()), transition.speed(), chosen.yaw(), chosen.pitch(),
            transition.missingRespawnBlock(), transition.postDimensionTransition());
    }

    /**
     * A position teleport of a watched player (commands and anything else using the relative teleport). Posts the event,
     * then runs the chosen teleport without posting again. Returns whether the player moved.
     */
    public static boolean teleportTo(ServerPlayer player, ServerLevel level, double x, double y, double z, Set<RelativeMovement> relatives,
                                     float yaw, float pitch) {
        Loc target = requested(player, level, x, y, z, relatives, yaw, pitch);
        Loc chosen = PlayerHooks.teleporting(player, level, target);
        if (chosen == null) {
            return false;
        }
        ServerLevel destination = ModRefs.world(chosen.world());
        if (chosen == target || destination == null) {
            return PlayerHooks.silently(() -> player.teleportTo(level, x, y, z, relatives, yaw, pitch));
        }
        return PlayerHooks.silently(() -> player.teleportTo(destination, chosen.x(), chosen.y(), chosen.z(), Set.of(), chosen.yaw(), chosen.pitch()));
    }

    /** An absolute teleport of a watched player into a level. Posts the event, then runs the chosen teleport without posting again. */
    public static void teleportTo(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        Loc target = ModRefs.loc(level, new Vec3(x, y, z), yaw, pitch);
        Loc chosen = PlayerHooks.teleporting(player, level, target);
        if (chosen == null) {
            return;
        }
        ServerLevel destination = ModRefs.world(chosen.world());
        Loc applied = chosen == target || destination == null ? target : chosen;
        ServerLevel appliedLevel = destination == null ? level : destination;
        PlayerHooks.silently(() -> {
            player.teleportTo(appliedLevel, applied.x(), applied.y(), applied.z(), applied.yaw(), applied.pitch());
            return null;
        });
    }

    private static Loc requested(ServerPlayer player, ServerLevel level, double x, double y, double z, Set<RelativeMovement> relatives,
                                 float yaw, float pitch) {
        double targetX = relatives.contains(RelativeMovement.X) ? player.getX() + x : x;
        double targetY = relatives.contains(RelativeMovement.Y) ? player.getY() + y : y;
        double targetZ = relatives.contains(RelativeMovement.Z) ? player.getZ() + z : z;
        float targetYaw = relatives.contains(RelativeMovement.Y_ROT) ? player.getYRot() + yaw : yaw;
        float targetPitch = relatives.contains(RelativeMovement.X_ROT) ? player.getXRot() + pitch : pitch;
        return ModRefs.loc(level, new Vec3(targetX, targetY, targetZ), targetYaw, targetPitch);
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
        return player.getRespawnPosition() == null ? null : player.getRespawnDimension();
    }

    static BlockPos respawnPosition(ServerPlayer player) {
        return player.getRespawnPosition();
    }

    static int selectedSlot(ServerPlayer player) {
        return player.getInventory().selected;
    }

    static Packet<?> selectedSlotPacket(int slot) {
        return new ClientboundSetCarriedItemPacket(slot);
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
        return player.getAbilities().instabuild;
    }

    /** Whether the main-hand item lets the player break the block at all (swords in creative do not). */
    static boolean canDestroy(ServerPlayer player, BlockState state, ServerLevel level, BlockPos pos) {
        return player.getMainHandItem().getItem().canAttackBlock(state, level, pos, player);
    }

    /** The entity id a spawner block entity holds, or null when it holds none. */
    static String spawnerEntity(SpawnerBlockEntity spawner, ServerLevel level) {
        CompoundTag entity = spawner.saveWithoutMetadata(level.registryAccess()).getCompound("SpawnData").getCompound("entity");
        return entity.contains("id", Tag.TAG_STRING) ? entity.getString("id") : null;
    }

    static boolean craftingMenu(AbstractContainerMenu menu) {
        return menu instanceof CraftingMenu || menu instanceof InventoryMenu;
    }

    static List<Slot> craftingGrid(AbstractContainerMenu menu) {
        return menu instanceof InventoryMenu
            ? menu.slots.subList(InventoryMenu.CRAFT_SLOT_START, InventoryMenu.CRAFT_SLOT_END)
            : menu.slots.subList(CRAFTING_GRID_START, CRAFTING_GRID_END);
    }

    /** The id of the recipe a result container last crafted, or null. */
    static Id recipeId(Container container) {
        RecipeHolder<?> recipe = container instanceof ResultContainer result ? result.getRecipeUsed() : null;
        return recipe == null ? null : ModRefs.id(recipe.id());
    }

    static CraftItemEvent.RecipeKind recipeKind(Container container) {
        RecipeHolder<?> recipe = container instanceof ResultContainer result ? result.getRecipeUsed() : null;
        return recipe == null ? CraftItemEvent.RecipeKind.OTHER
            : recipe.value() instanceof ShapedRecipe ? CraftItemEvent.RecipeKind.SHAPED
            : recipe.value() instanceof ShapelessRecipe ? CraftItemEvent.RecipeKind.SHAPELESS
            : CraftItemEvent.RecipeKind.OTHER;
    }

    static Id enchantmentId(EnchantmentInstance instance) {
        return ModConversions.enchantmentId(instance.enchantment);
    }

    static int enchantmentLevel(EnchantmentInstance instance) {
        return instance.level;
    }

    /** An enchantment instance for a registered enchantment, or null for an unknown id. */
    static EnchantmentInstance enchantment(Id id, int level) {
        Holder<Enchantment> enchantment = ModConversions.enchantment(id);
        return enchantment == null ? null : new EnchantmentInstance(enchantment, level);
    }

    static Id id(ResourceKey<?> key) {
        return ModRefs.id(key.location());
    }
}
