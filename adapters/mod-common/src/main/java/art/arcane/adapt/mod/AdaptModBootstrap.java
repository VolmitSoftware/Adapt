package art.arcane.adapt.mod;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ComponentPort;
import art.arcane.adapt.api.ComponentRegistrar;
import art.arcane.adapt.api.adaptation.EntityGuardPort;
import art.arcane.adapt.api.attribute.AdaptAttributeResolver;
import art.arcane.adapt.api.adaptation.ItemCooldownGroupPort;
import art.arcane.adapt.api.data.BlockStorePort;
import art.arcane.adapt.api.data.WorldDataPort;
import art.arcane.adapt.api.protection.ProtectionProbePort;
import art.arcane.adapt.api.fx.TpsPort;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.UsePermissions;
import art.arcane.adapt.api.value.MaterialValuePort;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.command.AdaptCommands;
import art.arcane.adapt.command.AdaptDebugDump;
import art.arcane.adapt.command.CommandHelpPort;
import art.arcane.adapt.command.DebugDumpPort;
import art.arcane.adapt.content.CoreContent;
import art.arcane.adapt.localization.AdaptLanguageSwitcher;
import art.arcane.adapt.localization.LanguageSwitcherPort;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.mod.command.ModCommandHelpPort;
import art.arcane.adapt.mod.command.ModCommandSource;
import art.arcane.adapt.mod.event.world.WorldHooks;
import art.arcane.adapt.mod.feature.ModFeatures;
import art.arcane.adapt.mod.feature.protection.ModProtectionProbePort;
import art.arcane.adapt.mod.feature.recipe.ModMaterialValuePort;
import art.arcane.adapt.mod.platform.MixinAudit;
import art.arcane.adapt.mod.platform.ModAttributeResolver;
import art.arcane.adapt.mod.platform.ModComponentPort;
import art.arcane.adapt.mod.platform.ModCustomModelPort;
import art.arcane.adapt.mod.platform.ModEntityGuardPort;
import art.arcane.adapt.mod.platform.ModItemCooldownGroupPort;
import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.mod.platform.ModPlayerInputPort;
import art.arcane.adapt.mod.platform.ModTpsPort;
import art.arcane.adapt.mod.platform.data.ModBlockStorePort;
import art.arcane.adapt.mod.platform.data.ModWorldDataPort;
import art.arcane.adapt.mod.platform.text.ModMarkup;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.PlatformFamily;
import art.arcane.adapt.util.common.math.PlayerInputPort;
import art.arcane.adapt.util.common.misc.CustomModelPort;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.world.WorldLifecycleEvent;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class AdaptModBootstrap {
    private static final String ARGUMENTS = "arguments";

    private final ModLoader loader;
    private final String loaderVersion;
    private final Path configDirectory;
    private final Predicate<String> modPresent;
    private final AdaptCommands commands = new AdaptCommands();
    private volatile AdaptCore core;
    private static final Object WORLD_DATA_OWNER = new Object();
    private volatile ModWorldDataPort worldData;

    public AdaptModBootstrap(ModLoader loader, String loaderVersion, Path configDirectory, Predicate<String> modPresent) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.loaderVersion = Objects.requireNonNull(loaderVersion, "loaderVersion");
        this.configDirectory = Objects.requireNonNull(configDirectory, "configDirectory");
        this.modPresent = Objects.requireNonNull(modPresent, "modPresent");
        ModFeatures.install();
        WorldHooks.install();
    }

    /** Server thread, while the server is starting. */
    public void serverStarting(MinecraftServer server) {
        ModPlatform.start(server, family(), loader.displayName(), configDirectory, modPresent);
    }

    /** Server thread, once the server finished starting. */
    public void serverStarted(MinecraftServer server) {
        ModPlatform.log().info(versionLine());
        ModPlatform.started(server);
        startCore();
        MixinAudit.runIfRequested();
    }

    /** Server thread, while the server is stopping. */
    public void serverStopping(MinecraftServer server) {
        stopCore();
        ModPlatform.stop(server);
    }

    private void startCore() {
        AdaptPlatform platform = ModPlatform.platform();
        if (platform == null) {
            ModPlatform.log().error("Adapt core not started: the platform is incomplete", null);
            return;
        }
        AdaptPorts.install(MarkupPort.class, new ModMarkup());
        installWorldData();
        AdaptPorts.install(BlockStorePort.class, new ModBlockStorePort());
        AdaptPorts.install(PlayerInputPort.class, new ModPlayerInputPort());
        AdaptPorts.install(CommandHelpPort.class, new ModCommandHelpPort());
        AdaptPorts.install(LanguageSwitcherPort.class, new AdaptLanguageSwitcher());
        AdaptPorts.install(DebugDumpPort.class, new AdaptDebugDump());
        AdaptPorts.install(EntityGuardPort.class, new ModEntityGuardPort());
        AdaptPorts.install(TpsPort.class, new ModTpsPort());
        AdaptPorts.install(ComponentPort.class, new ModComponentPort());
        AdaptPorts.install(ItemCooldownGroupPort.class, new ModItemCooldownGroupPort());
        AdaptPorts.install(CustomModelPort.class, new ModCustomModelPort());
        AdaptPorts.install(MaterialValuePort.class, new ModMaterialValuePort());
        AdaptPorts.install(AdaptAttributeResolver.class, new ModAttributeResolver());
        AdaptPorts.install(ProtectionProbePort.class, new ModProtectionProbePort());
        ComponentRegistrar registrar = new ComponentRegistrar(ModPlatform.bus());
        AdaptCore created = new AdaptCore(platform, ModPlatform.bus(), registrar, CoreContent.skillTypes());
        try {
            created.start();
            core = created;
            AdaptServer server = created.getAdaptServer();
            int skills = server == null ? 0 : server.getSkillRegistry().getAllSkills().size();
            if (server != null) {
                UsePermissions.registerDefaults(platform.permissions(), server.getSkillRegistry().getAllSkills());
            }
            AdaptCommands.registerPermissionDefaults(platform.permissions());
            AdaptationListingHandler.initializeAdaptationListings();
            AdaptLog.info("Adapt core started with " + skills + " skill(s): " + skillNames(created));
        } catch (RuntimeException | LinkageError failure) {
            ModPlatform.log().error("Adapt core failed to start", failure);
            created.stop();
        }
    }

    private void installWorldData() {
        ModWorldDataPort port = new ModWorldDataPort(ModPlatform.server().getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve(AdaptModIdentity.ID).resolve("xp"));
        worldData = port;
        AdaptPorts.install(WorldDataPort.class, port);
        ModPlatform.bus().register(WORLD_DATA_OWNER, WorldLifecycleEvent.class, EventPriority.MONITOR,
                (WorldLifecycleEvent event) -> {
                    if (event.phase() != WorldLifecycleEvent.Phase.LOAD) {
                        port.save(event.world().key());
                    }
                });
    }

    private void closeWorldData() {
        ModPlatform.bus().unregisterAll(WORLD_DATA_OWNER);
        AdaptPorts.uninstall(WorldDataPort.class);
        ModWorldDataPort port = worldData;
        worldData = null;
        if (port != null) {
            port.close();
        }
    }

    private void stopCore() {
        AdaptCore running = core;
        core = null;
        if (running != null) {
            running.stop();
        }
        AdaptPorts.uninstall(MarkupPort.class);
        closeWorldData();
        AdaptPorts.uninstall(BlockStorePort.class);
        AdaptPorts.uninstall(PlayerInputPort.class);
        AdaptPorts.uninstall(CommandHelpPort.class);
        AdaptPorts.uninstall(LanguageSwitcherPort.class);
        AdaptPorts.uninstall(DebugDumpPort.class);
        AdaptPorts.uninstall(EntityGuardPort.class);
        AdaptPorts.uninstall(TpsPort.class);
        AdaptPorts.uninstall(ComponentPort.class);
        AdaptPorts.uninstall(ItemCooldownGroupPort.class);
        AdaptPorts.uninstall(CustomModelPort.class);
        AdaptPorts.uninstall(MaterialValuePort.class);
        AdaptPorts.uninstall(AdaptAttributeResolver.class);
        AdaptPorts.uninstall(ProtectionProbePort.class);
    }

    private static String skillNames(AdaptCore running) {
        StringBuilder names = new StringBuilder();
        for (Skill<?> skill : running.getAdaptServer().getSkillRegistry().getAllSkills()) {
            if (!names.isEmpty()) {
                names.append(", ");
            }
            names.append(skill.getName());
        }
        return names.toString();
    }

    /** {@code /adapt} with every word after it forwarded to the core command tree, as Bukkit runs it. */
    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(AdaptCommands.ROOT_COMMAND)
                .executes((CommandContext<CommandSourceStack> context) -> run(context.getSource(), ""))
                .then(Commands.argument(ARGUMENTS, StringArgumentType.greedyString())
                        .suggests(this::suggest)
                        .executes((CommandContext<CommandSourceStack> context) -> run(context.getSource(),
                                StringArgumentType.getString(context, ARGUMENTS)))));
    }

    private int run(CommandSourceStack source, String text) {
        if (core == null) {
            source.sendSystemMessage(Component.literal(versionLine()));
            source.sendSystemMessage(Component.literal("Adapt core is not running"));
            return 0;
        }
        commands.execute(new ModCommandSource(source), AdaptCommands.ROOT_COMMAND, words(text));
        return 1;
    }

    private CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (core == null) {
            return builder.buildFuture();
        }
        String remaining = builder.getRemaining();
        SuggestionsBuilder last = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        for (String option : commands.complete(new ModCommandSource(context.getSource()), AdaptCommands.ROOT_COMMAND, remaining.split(" ", -1))) {
            last.suggest(option);
        }
        return last.buildFuture();
    }

    /** The words of a command, split as a Bukkit command receives them. */
    private static String[] words(String text) {
        return text.isEmpty() ? new String[0] : text.split(" ", -1);
    }

    public String versionLine() {
        return AdaptModIdentity.NAME + " " + AdaptModIdentity.VERSION + " [" + loader.displayName() + " " + loaderVersion + "] Minecraft " + minecraftVersion();
    }

    private PlatformFamily family() {
        return switch (loader) {
            case FABRIC -> PlatformFamily.FABRIC;
            case FORGE -> PlatformFamily.FORGE;
            case NEOFORGE -> PlatformFamily.NEOFORGE;
        };
    }

    private static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().name();
    }
}
