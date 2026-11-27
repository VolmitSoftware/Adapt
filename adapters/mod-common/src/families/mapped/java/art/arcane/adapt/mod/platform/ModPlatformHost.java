package art.arcane.adapt.mod.platform;

import art.arcane.adapt.mod.AdaptModIdentity;
import art.arcane.adapt.mod.platform.entity.ModAnimalOps;
import art.arcane.adapt.mod.platform.item.ModItemOps;
import art.arcane.adapt.platform.Capability;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.PlatformFamily;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import com.mojang.authlib.GameProfile;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.Entity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

public final class ModPlatformHost implements PlatformHost {
    private final MinecraftServer server;
    private final PlatformFamily family;
    private final String platformName;
    private final Path dataDirectory;
    private final PlatformLog log;
    private final Predicate<String> modPresent;
    private final RegistryLookup registries = new ModRegistryLookup();
    private final ItemOps items = new ModItemOps();
    private final AnimalOps animals = new ModAnimalOps();

    public ModPlatformHost(MinecraftServer server, PlatformFamily family, String platformName, Path dataDirectory, PlatformLog log, Predicate<String> modPresent) {
        this.server = Objects.requireNonNull(server, "server");
        this.family = Objects.requireNonNull(family, "family");
        this.platformName = Objects.requireNonNull(platformName, "platformName");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.log = Objects.requireNonNull(log, "log");
        this.modPresent = Objects.requireNonNull(modPresent, "modPresent");
    }

    @Override
    public PlatformFamily family() {
        return family;
    }

    @Override
    public String platformName() {
        return platformName;
    }

    @Override
    public String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    @Override
    public boolean supports(EngineFeature feature) {
        return ModEngineFeatures.supports(server, feature);
    }

    @Override
    public String adaptVersion() {
        return AdaptModIdentity.VERSION;
    }

    @Override
    public boolean has(Capability capability) {
        return ModCapabilities.has(capability, this::isPresent);
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public PlatformLog log() {
        return log;
    }

    @Override
    public Collection<PlayerRef> onlinePlayers() {
        return ModRefs.cachedPlayers();
    }

    @Override
    public PlayerRef player(UUID id) {
        return ModRefs.cachedPlayer(id);
    }

    @Override
    public PlayerRef player(String name) {
        if (name == null) {
            return null;
        }
        for (PlayerRef player : ModRefs.cachedPlayers()) {
            if (player.name().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    @Override
    public UUID knownPlayerId(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        GameProfileCache cache = server.getProfileCache();
        return cache == null ? null : cache.get(name).map(GameProfile::getId).orElse(null);
    }

    @Override
    public String knownPlayerName(UUID id) {
        if (id == null) {
            return null;
        }
        GameProfileCache cache = server.getProfileCache();
        return cache == null ? null : cache.get(id).map(GameProfile::getName).orElse(null);
    }

    @Override
    public EntityRef entity(UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return ModRefs.entity(entity);
            }
        }
        return null;
    }

    @Override
    public Collection<WorldRef> worlds() {
        List<WorldRef> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            out.add(ModRefs.world(level));
        }
        return out;
    }

    @Override
    public WorldRef world(UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            WorldRef world = ModRefs.world(level);
            if (world.uid().equals(id)) {
                return world;
            }
        }
        return null;
    }

    @Override
    public WorldRef world(String name) {
        if (name == null) {
            return null;
        }
        Id key = Id.tryParse(name);
        for (ServerLevel level : server.getAllLevels()) {
            WorldRef world = ModRefs.world(level);
            if (world.name().equalsIgnoreCase(name) || world.key().equals(key)) {
                return world;
            }
        }
        return null;
    }

    @Override
    public WorldRef world(Id dimension) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ModConversions.identifier(dimension)));
        return level == null ? null : ModRefs.world(level);
    }

    @Override
    public boolean isPresent(String pluginOrModId) {
        return pluginOrModId != null && modPresent.test(pluginOrModId);
    }

    @Override
    public RegistryLookup registries() {
        return registries;
    }

    @Override
    public ItemOps items() {
        return items;
    }

    @Override
    public AnimalOps animals() {
        return animals;
    }

    @Override
    public long currentTick() {
        return server.getTickCount();
    }

    @Override
    public int viewDistance() {
        return server.getPlayerList().getViewDistance();
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        String line = command.startsWith("/") ? command.substring(1) : command;
        Runnable run = () -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), line);
        if (server.isSameThread()) {
            run.run();
        } else {
            server.execute(run);
        }
    }
}
