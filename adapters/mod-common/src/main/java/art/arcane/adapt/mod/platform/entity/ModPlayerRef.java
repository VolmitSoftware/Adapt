package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.ModText;
import art.arcane.adapt.mod.platform.data.AdaptCooldowns;
import art.arcane.adapt.mod.platform.data.AdaptFixedPose;
import art.arcane.adapt.mod.mixin.gapblock.CompoundContainerAccess;
import art.arcane.adapt.mod.platform.mixin.FoodDataAccess;
import art.arcane.adapt.mod.mixin.gapblock.MerchantMenuAccess;
import art.arcane.adapt.mod.platform.mixin.MinecraftServerStorageAccess;
import art.arcane.adapt.mod.platform.mixin.PlayerAccess;
import art.arcane.adapt.platform.entity.CraftingStation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.Pose;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.permission.PermissionHost;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.scores.PlayerTeam;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.Map;
import art.arcane.adapt.platform.entity.ProjectileRef;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Player handle. One instance per online player, rebound by UUID when vanilla replaces the player
 * object on respawn.
 */
public final class ModPlayerRef extends ModLivingRef implements PlayerRef {
    private static final Map<UUID, Loc> COMPASS_TARGETS = new ConcurrentHashMap<>();
    private final String name;
    private volatile ServerPlayer current;

    public ModPlayerRef(ServerPlayer player) {
        super(player);
        this.current = player;
        this.name = player.getGameProfile().name();
    }

    /** Rebinds to a newer player object for the same UUID. */
    public void rebind(ServerPlayer player) {
        if (player.getUUID().equals(uuid())) {
            current = player;
        }
    }

    @Override
    public ServerPlayer handle() {
        ServerPlayer player = current;
        if (player.isRemoved() || player.hasDisconnected()) {
            MinecraftServer server = ModPlatform.server();
            ServerPlayer live = server == null ? null : server.getPlayerList().getPlayer(uuid());
            if (live != null && live != player) {
                current = live;
                return live;
            }
        }
        return player;
    }

    @Override
    public boolean isValid() {
        ServerPlayer player = handle();
        return isOnline() && !player.isRemoved() && player.isAlive();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isOnline() {
        MinecraftServer server = ModPlatform.server();
        if (server == null) {
            return false;
        }
        ServerPlayer live = server.getPlayerList().getPlayer(uuid());
        return live != null && !live.hasDisconnected();
    }

    @Override
    public boolean isFakePlayer() {
        return handle().getClass() != ServerPlayer.class;
    }

    @Override
    public String locale() {
        return handle().clientInformation().language();
    }

    @Override
    public GameMode gameMode() {
        return ModConversions.gameMode(handle().gameMode());
    }

    @Override
    public void setGameMode(GameMode mode) {
        handle().setGameMode(ModConversions.gameType(mode));
    }

    @Override
    public boolean isSneaking() {
        return handle().isShiftKeyDown();
    }

    @Override
    public boolean isSprinting() {
        return handle().isSprinting();
    }

    @Override
    public void setSprinting(boolean sprinting) {
        handle().setSprinting(sprinting);
    }

    @Override
    public boolean isFlying() {
        return handle().getAbilities().flying;
    }

    @Override
    public void setFlying(boolean flying) {
        ServerPlayer player = handle();
        Abilities abilities = player.getAbilities();
        if (flying && !abilities.mayfly) {
            throw new IllegalArgumentException("Player is not allowed to fly");
        }
        abilities.flying = flying;
        player.onUpdateAbilities();
    }

    @Override
    public boolean allowFlight() {
        return handle().getAbilities().mayfly;
    }

    @Override
    public void setAllowFlight(boolean allow) {
        ServerPlayer player = handle();
        Abilities abilities = player.getAbilities();
        abilities.mayfly = allow;
        if (!allow) {
            abilities.flying = false;
        }
        player.onUpdateAbilities();
    }

    @Override
    public boolean isBlocking() {
        return handle().isBlocking();
    }

    @Override
    public boolean isOnTeamWith(PlayerRef other) {
        ServerPlayer owner = handle();
        PlayerTeam team = owner.level().getScoreboard().getPlayersTeam(owner.getScoreboardName());
        return team != null && team.getPlayers().contains(other.name());
    }

    @Override
    public Pose pose() {
        net.minecraft.world.entity.Pose pose = handle().getPose();
        if (pose == net.minecraft.world.entity.Pose.CROUCHING) {
            return Pose.SNEAKING;
        }
        for (Pose candidate : Pose.values()) {
            if (candidate.name().equals(pose.name())) {
                return candidate;
            }
        }
        return Pose.STANDING;
    }

    @Override
    public boolean hasFixedPose() {
        return ((AdaptFixedPose) handle()).adapt$fixedPose();
    }

    @Override
    public void setPose(Pose pose, boolean fixed) {
        ServerPlayer player = handle();
        AdaptFixedPose holder = (AdaptFixedPose) player;
        holder.adapt$setFixedPose(false);
        player.setPose(vanillaPose(pose));
        holder.adapt$setFixedPose(fixed);
    }

    @Override
    public boolean sendVerticalMotion(double verticalVelocity) {
        ModClientMotion.sendVerticalMotion(handle(), verticalVelocity);
        return true;
    }

    @Override
    public boolean sendClimbableTagOverride(Id block) {
        return ModClientMotion.sendClimbableTagOverride(handle(), block);
    }

    private static net.minecraft.world.entity.Pose vanillaPose(Pose pose) {
        if (pose == Pose.SNEAKING) {
            return net.minecraft.world.entity.Pose.CROUCHING;
        }
        for (net.minecraft.world.entity.Pose candidate : net.minecraft.world.entity.Pose.values()) {
            if (candidate.name().equals(pose.name())) {
                return candidate;
            }
        }
        return net.minecraft.world.entity.Pose.STANDING;
    }

    @Override
    public boolean isSleeping() {
        return handle().isSleeping();
    }

    @Override
    public float walkSpeed() {
        return handle().getAbilities().getWalkingSpeed() * 2.0F;
    }

    @Override
    public void setWalkSpeed(float speed) {
        ServerPlayer player = handle();
        player.getAbilities().setWalkingSpeed(speed / 2.0F);
        player.onUpdateAbilities();
        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            movement.setBaseValue(speed / 2.0F);
        }
    }

    @Override
    public PlayerInventoryRef inventory() {
        return new ModPlayerInventoryRef(this);
    }

    @Override
    public InventoryRef enderChest() {
        return new ModInventoryRef(() -> handle().getEnderChestInventory());
    }

    @Override
    public void closeInventory() {
        handle().closeContainer();
    }

    @Override
    public void updateInventory() {
        handle().containerMenu.sendAllDataToRemote();
    }

    @Override
    public boolean hasOpenContainer() {
        ServerPlayer player = handle();
        return player.containerMenu != player.inventoryMenu;
    }

    @Override
    public void openStation(CraftingStation station) {
        ModStations.open(handle(), station);
    }

    @Override
    public EntityRef openMerchant() {
        ServerPlayer player = handle();
        return player.containerMenu instanceof MerchantMenuAccess menu && menu.adapt$trader() instanceof Entity trader
            ? ModRefs.entity(trader) : null;
    }

    @Override
    public InventoryRef openMenuInventory() {
        ServerPlayer player = handle();
        return player.containerMenu == player.inventoryMenu ? null : new ModMenuInventoryRef(player.containerMenu);
    }

    @Override
    public InventoryRef openBlockInventory(BlockRef block) {
        ServerPlayer player = handle();
        ServerLevel level = ModRefs.world(block.world());
        BlockPos pos = ModRefs.pos(block.pos());
        MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
        if (provider == null || player.openMenu(provider).isEmpty()) {
            return null;
        }
        Set<BlockEntity> containers = new HashSet<>();
        for (BlockRef half : block.containerHalves()) {
            BlockEntity entity = level.getBlockEntity(ModRefs.pos(half.pos()));
            if (entity != null) {
                containers.add(entity);
            }
        }
        BlockEntity clicked = level.getBlockEntity(pos);
        if (clicked != null) {
            containers.add(clicked);
        }
        ModRemoteContainers.open(player, player.containerMenu, containers);
        return new ModMenuInventoryRef(player.containerMenu);
    }

    @Override
    public InventoryRef openEnderChest() {
        ServerPlayer player = handle();
        PlayerEnderChestContainer container = player.getEnderChestInventory();
        container.setActiveChest(null);
        OptionalInt opened = player.openMenu(new SimpleMenuProvider(
            (int containerId, Inventory inventory, Player viewer) -> ChestMenu.threeRows(containerId, inventory, container),
            Component.translatable("container.enderchest")));
        return opened.isEmpty() ? null : new ModMenuInventoryRef(player.containerMenu);
    }

    @Override
    public ProjectileRef launchProjectile(Id type, Consumer<ProjectileRef> beforeSpawn) {
        ServerPlayer player = handle();
        ServerLevel level = player.level();
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(ModRefs.identifier(type));
        Entity created = entityType.create(level, EntitySpawnReason.TRIGGERED);
        if (!(created instanceof Projectile projectile)) {
            return null;
        }
        projectile.setOwner(player);
        projectile.setPos(player.getX(), player.getEyeY() - 0.1D, player.getZ());
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
        ProjectileRef launched = (ProjectileRef) ModRefs.entity(projectile);
        beforeSpawn.accept(launched);
        if (!level.addFreshEntity(projectile)) {
            return null;
        }
        return launched;
    }

    @Override
    public Loc openMenuLocation() {
        return openContainerBlock().map(BlockRef::location).orElse(null);
    }

    @Override
    public int enchantmentSeed() {
        return handle().getEnchantmentSeed();
    }

    @Override
    public void setEnchantmentSeed(int seed) {
        ((PlayerAccess) handle()).adapt$setEnchantmentSeed(seed);
    }

    @Override
    public Optional<BlockRef> openContainerBlock() {
        ServerPlayer player = handle();
        if (player.containerMenu == player.inventoryMenu) {
            return Optional.empty();
        }
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container instanceof Inventory) {
                continue;
            }
            Container holder = slot.container instanceof CompoundContainer compound
                ? ((CompoundContainerAccess) compound).adapt$first() : slot.container;
            if (holder instanceof BlockEntity blockEntity && blockEntity.getLevel() instanceof ServerLevel level) {
                return Optional.of(ModRefs.block(level, blockEntity.getBlockPos()));
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    public int foodLevel() {
        return handle().getFoodData().getFoodLevel();
    }

    @Override
    public void setFoodLevel(int level) {
        handle().getFoodData().setFoodLevel(level);
    }

    @Override
    public float saturation() {
        return handle().getFoodData().getSaturationLevel();
    }

    @Override
    public void setSaturation(float saturation) {
        handle().getFoodData().setSaturation(saturation);
    }

    @Override
    public float exhaustion() {
        return ((FoodDataAccess) handle().getFoodData()).adapt$exhaustion();
    }

    @Override
    public void setExhaustion(float exhaustion) {
        ((FoodDataAccess) handle().getFoodData()).adapt$setExhaustion(exhaustion);
    }

    @Override
    public int level() {
        return handle().experienceLevel;
    }

    @Override
    public void setLevel(int level) {
        handle().setExperienceLevels(level);
    }

    @Override
    public float expProgress() {
        return handle().experienceProgress;
    }

    @Override
    public void setExpProgress(float progress) {
        ServerPlayer player = handle();
        player.experienceProgress = Math.max(0.0F, Math.min(1.0F, progress));
        player.setExperienceLevels(player.experienceLevel);
    }

    @Override
    public int totalExperience() {
        return handle().totalExperience;
    }

    @Override
    public void giveExp(int amount) {
        handle().giveExperiencePoints(amount);
    }

    @Override
    public void giveExpLevels(int levels) {
        handle().giveExperienceLevels(levels);
    }

    @Override
    public void sendExperienceChange(float progress, int level) {
        if (progress < 0.0F || progress > 1.0F) {
            throw new IllegalArgumentException("Experience progress must be between 0.0 and 1.0 (" + progress + ")");
        }
        if (level < 0) {
            throw new IllegalArgumentException("Experience level must not be negative (" + level + ")");
        }
        ServerPlayer player = handle();
        if (player.connection != null) {
            player.connection.send(new ClientboundSetExperiencePacket(progress, player.totalExperience, level));
        }
    }

    @Override
    public void setCompassTarget(Loc target) {
        ServerPlayer player = handle();
        if (target == null || player.connection == null) {
            return;
        }
        COMPASS_TARGETS.put(player.getUUID(), target);
        ServerLevel level = ModRefs.world(target.world());
        player.connection.send(new ClientboundSetDefaultSpawnPositionPacket(LevelData.RespawnData.of(level.dimension(),
            BlockPos.containing(target.x(), target.y(), target.z()), target.yaw(), target.pitch())));
    }

    public static Loc compassTarget(UUID player) {
        return COMPASS_TARGETS.get(player);
    }

    @Override
    public void saveData() {
        ServerPlayer player = handle();
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            ((MinecraftServerStorageAccess) server).adapt$playerDataStorage().save(player);
        }
    }

    @Override
    public boolean hasCooldown(Id item) {
        ItemStack stack = stack(item);
        return !stack.isEmpty() && handle().getCooldowns().isOnCooldown(stack);
    }

    @Override
    public int cooldownTicks(Id item) {
        ItemStack stack = stack(item);
        if (stack.isEmpty()) {
            return 0;
        }
        ItemCooldowns cooldowns = handle().getCooldowns();
        if (!cooldowns.isOnCooldown(stack)) {
            return 0;
        }
        return Math.max(1, ((AdaptCooldowns) cooldowns).adapt$remaining(cooldowns.getCooldownGroup(stack)));
    }

    @Override
    public void setCooldown(Id item, int ticks) {
        ItemStack stack = stack(item);
        if (stack.isEmpty()) {
            return;
        }
        ItemCooldowns cooldowns = handle().getCooldowns();
        Identifier group = cooldowns.getCooldownGroup(stack);
        if (ticks <= 0) {
            cooldowns.removeCooldown(group);
        } else {
            cooldowns.addCooldown(group, ticks);
        }
    }

    @Override
    public boolean hasPermission(String node) {
        PermissionHost permissions = ModPlatform.permissions();
        return permissions != null && permissions.has(this, node);
    }

    @Override
    public boolean isOp() {
        return handle().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    @Override
    public void sendMessage(String markup) {
        Component message = ModText.component(markup);
        onServer(() -> handle().sendSystemMessage(message));
    }

    @Override
    public void sendActionBar(String markup) {
        Component message = ModText.component(markup);
        onServer(() -> handle().sendSystemMessage(message, true));
    }

    @Override
    public void sendTitle(String titleMarkup, String subtitleMarkup, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        Component title = ModText.component(titleMarkup == null ? "" : titleMarkup);
        Component subtitle = subtitleMarkup == null ? null : ModText.component(subtitleMarkup);
        onServer(() -> {
            ServerPlayer player = handle();
            player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeInTicks, stayTicks, fadeOutTicks));
            if (subtitle != null) {
                player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            }
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        });
    }

    @Override
    public void playSound(Loc at, Id sound, SoundCategory category, float volume, float pitch) {
        ServerPlayer player = handle();
        player.connection.send(new ClientboundSoundPacket(ModConversions.sound(sound), ModConversions.soundSource(category),
                at.x(), at.y(), at.z(), volume, pitch, player.getRandom().nextLong()));
    }

    @Override
    public Loc respawnLocation() {
        Loc bed = bedSpawnLocation();
        if (bed != null) {
            return bed;
        }
        MinecraftServer server = ModPlatform.server();
        return server == null ? null : loc(server, server.getRespawnData());
    }

    @Override
    public Loc bedSpawnLocation() {
        ServerPlayer.RespawnConfig config = handle().getRespawnConfig();
        MinecraftServer server = ModPlatform.server();
        return config == null || server == null ? null : loc(server, config.respawnData());
    }

    @Override
    public EntityRef spectatorTarget() {
        ServerPlayer player = handle();
        Entity camera = player.getCamera();
        return camera == null || camera == player ? null : ModRefs.entity(camera);
    }

    @Override
    public void setSpectatorTarget(EntityRef target) {
        ServerPlayer player = handle();
        if (!player.isSpectator()) {
            return;
        }
        player.setCamera(target == null ? null : ModRefs.entity(target));
    }

    private static Loc loc(MinecraftServer server, LevelData.RespawnData data) {
        GlobalPos position = data.globalPos();
        ServerLevel level = server.getLevel(position.dimension());
        if (level == null) {
            return null;
        }
        return new Loc(ModRefs.world(level), position.pos().getX() + 0.5D, position.pos().getY(), position.pos().getZ() + 0.5D, data.yaw(), data.pitch());
    }

    private static ItemStack stack(Id item) {
        Item type = BuiltInRegistries.ITEM.getValue(ModConversions.identifier(item));
        return type == null ? ItemStack.EMPTY : new ItemStack(type);
    }

    private static void onServer(Runnable task) {
        MinecraftServer server = ModPlatform.server();
        if (server == null) {
            return;
        }
        if (server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
        }
    }
}
