package art.arcane.adapt.command;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.LanguageSwitcherPort;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.permission.PermissionDefault;
import art.arcane.adapt.platform.permission.PermissionHost;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.director.DirectorSystem;
import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextMap;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionMode;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionResult;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorInvocationHook;
import art.arcane.volmlib.util.director.runtime.DirectorRoute;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * The {@code /adapt} command on every platform: the platform hands each command and tab request here with a
 * {@link CommandSource}; the words run through the Director tree rooted at {@link CommandAdapt}.
 */
public final class AdaptCommands implements DirectorInvocationHook {
  public static final String ROOT_COMMAND = "adapt";
  public static final String ROOT_PERMISSION = "adapt.main";
  static final String LANGUAGE = "language";
  private static final String PACK = "pack";
  private static final String UNMUTATE = "unmutate";

  private final ThreadLocal<Deque<LanguageAudience.Scope>> languageScopes = ThreadLocal.withInitial(ArrayDeque::new);
  private volatile DirectorRuntimeEngine engine;
  private volatile long grafted = -1;
  private final boolean pack;

  /** The command tree of a platform that serves the Adapt resource pack, {@code /adapt pack} included. */
  public AdaptCommands() {
    this(true);
  }

  private AdaptCommands(boolean pack) {
    this.pack = pack;
  }

  /** The command tree of a platform without the Adapt resource pack: no {@code /adapt pack}. */
  public static AdaptCommands withoutPack() {
    return new AdaptCommands(false);
  }

  /** The command permissions with the defaults plugin.yml gives them on Bukkit, for platforms without plugin.yml. */
  public static void registerPermissionDefaults(PermissionHost permissions) {
    permissions.register("adapt.main", "Allows for you to use the main command", PermissionDefault.OP);
    permissions.register("adapt.debugdump", "Allows creating and optionally uploading diagnostic reports.", PermissionDefault.OP);
    permissions.register("adapt.language.self", "Allows choosing your own language for this plugin.", PermissionDefault.TRUE);
    permissions.register("adapt.idontknowwhatimdoingiswear", "This is for the Adapt Testing command DEVELOPERS ONLY", PermissionDefault.OP);
    permissions.register("adapt.cheatitem", "Allows for you to create a Cheat Item (one time use Xp Items)", PermissionDefault.OP);
    permissions.register("adapt.boost", "Allows for you to use the Boost command to boost XP gains", PermissionDefault.OP);
    permissions.register("adapt.boost.global", "Allows boosting XP gains for every player on the server", PermissionDefault.OP);
    permissions.register("adapt.gui", "Allows opening the Adapt GUI via /adapt gui", PermissionDefault.OP);
    permissions.register("adapt.determine", "Allows directly assigning skill lines and adaptations to players", PermissionDefault.OP);
    permissions.register("adapt.debug", "Allows Adapt debug tools including debug mode and config migration", PermissionDefault.OP);
    permissions.register("adapt.configurator", "Allows using the config editor and resetting configs to defaults", PermissionDefault.OP);
    permissions.register("adapt.effects", "Allows toggling Adapt effect visibility for yourself", PermissionDefault.TRUE);
    permissions.register("adapt.mutate", "Allows merging two of your own skills into a mutated skill", PermissionDefault.TRUE);
    permissions.register("adapt.mutate.reset", "Allows removing a player's skill mutations", PermissionDefault.OP);
    permissions.register("adapt.clear", "Allows clearing player progression data", PermissionDefault.OP);
    permissions.register(CommandOrphans.PERMISSION, "Allows listing, refunding and deleting progress of missing addons", PermissionDefault.OP);
  }

  /** The Director tree: Adapt's own commands with every installed addon command grafted in, rebuilt when those change. */
  public DirectorRuntimeEngine engine() {
    DirectorRuntimeEngine current = engine;
    long revision = AddonCommands.revision();
    if (current != null && grafted == revision) {
      return current;
    }
    synchronized (this) {
      if (engine == null || grafted != revision) {
        DirectorRuntimeEngine built = DirectorEngineFactory.create(new CommandAdapt(), DirectorEngineOptions.builder()
            .contexts(contexts())
            .dispatcher(AdaptCommands::dispatch)
            .invocationHook(this)
            .legacyHandlers(DirectorSystem.handlers)
            .textResolver(AdaptLanguage.directorResolver())
            .build());
        if (!pack) {
          built.getRoot().getChildren().removeIf((DirectorRuntimeNode node) -> node.allNames().contains(PACK));
        }
        AddonCommandRunner.graftAll(built.getRoot());
        engine = built;
        grafted = revision;
      }
      return engine;
    }
  }

  /** Runs {@code /label args...}. */
  public void execute(CommandSource source, String label, String[] args) {
    AdaptLog.verbose(() -> "Received command from %s: /%s %s".formatted(source.name(), label, String.join(" ", args)));
    if (args.length > 0 && args[0].equalsIgnoreCase(LANGUAGE)) {
      language().command(source, Arrays.copyOfRange(args, 1, args.length));
      return;
    }
    Optional<DirectorRoute> addon = addonRoute(source, label, args);
    if (addon.isPresent()) {
      if (sendHelpIfRequested(source, args) || AddonCommandRunner.run(source, addon.get())) {
        playSuccessSound(source);
      } else {
        playFailureSound(source);
      }
      return;
    }
    if (!(args.length > 1 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("dump"))
        && !(pack && args.length > 0 && args[0].equalsIgnoreCase(PACK) && source.hasPermission(CommandPack.PERMISSION))
        && !source.hasPermission(ROOT_PERMISSION)) {
      source.sendSection(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", ROOT_PERMISSION)));
      return;
    }

    if (sendHelpIfRequested(source, args)) {
      playSuccessSound(source);
      return;
    }

    DirectorExecutionResult result = runDirector(source, label, args);
    if (result.isSuccess()) {
      playSuccessSound(source);
      return;
    }

    playFailureSound(source);
    if (result.getMessage() == null || result.getMessage().trim().isEmpty()) {
      source.sendSection(C.RED + AdaptLanguage.text(CommandRuntimeMessages.UNKNOWN_ADAPT_COMMAND));
    }
  }

  /** Suggestions for the last of {@code args}. */
  public List<String> complete(CommandSource source, String alias, String[] args) {
    List<String> suggestions;
    if (args.length > 0 && args[0].equalsIgnoreCase(LANGUAGE)) {
      suggestions = language().complete(source, Arrays.copyOfRange(args, 1, args.length));
    } else if (args.length > 1 && addonRoute(source, alias, Arrays.copyOf(args, args.length - 1)).isPresent()) {
      suggestions = addonSuggestions(source, alias, args);
    } else if (args.length == 3 && args[0].equalsIgnoreCase(UNMUTATE)) {
      suggestions = CommandAdapt.mutationSuggestions(source, args[1], args[2]);
    } else {
      suggestions = runDirectorTab(source, alias, args);
    }

    Optional<PlayerRef> player = source.player();
    if (player.isPresent()) {
      SoundPlayer.of(player.get()).play(player.get().location(), Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, RNG.r.f(0.125f, 1.95f));
    }
    return suggestions;
  }

  @Override
  public void beforeInvoke(DirectorInvocation invocation, DirectorRuntimeNode node) {
    if (invocation.getSender() instanceof SourceSender sender) {
      languageScopes.get().push(LanguageAudience.open(audience(sender.source())));
      CommandContext.touch(sender.source());
    }
  }

  @Override
  public void afterInvoke(DirectorInvocation invocation, DirectorRuntimeNode node) {
    CommandContext.remove();
    Deque<LanguageAudience.Scope> scopes = languageScopes.get();
    if (!scopes.isEmpty()) {
      scopes.pop().close();
    }
    if (scopes.isEmpty()) {
      languageScopes.remove();
    }
  }

  /** The route of {@code args} when it ends on an addon command. */
  private Optional<DirectorRoute> addonRoute(CommandSource source, String label, String[] args) {
    if (args.length == 0 || AddonCommands.installed().isEmpty()) {
      return Optional.empty();
    }
    DirectorRoute route = engine().route(new DirectorInvocation(new SourceSender(source), label, Arrays.asList(args)));
    return AddonCommandRunner.command(route).isPresent() ? Optional.of(route) : Optional.empty();
  }

  /** Subcommand names of an addon group come from Director; argument values of a command that runs come from here. */
  private List<String> addonSuggestions(CommandSource source, String alias, String[] args) {
    DirectorRoute route = addonRoute(source, alias, Arrays.copyOf(args, args.length - 1)).orElseThrow();
    if (AddonCommandRunner.command(route).orElseThrow().isGroup()) {
      return runDirectorTab(source, alias, args);
    }
    return AddonCommandRunner.complete(source, route, args[args.length - 1]);
  }

  private static LanguageSwitcherPort language() {
    return AdaptPorts.get(LanguageSwitcherPort.class, LanguageSwitcherPort.NONE);
  }

  private static DirectorContextRegistry contexts() {
    DirectorContextRegistry contexts = new DirectorContextRegistry();
    contexts.register(CommandSource.class, (DirectorInvocation invocation, DirectorContextMap map) ->
        invocation.getSender() instanceof SourceSender sender ? sender.source() : null);
    return contexts;
  }

  private static void dispatch(DirectorExecutionMode mode, Runnable runnable) {
    if (mode == DirectorExecutionMode.SYNC) {
      Tasks.s(runnable);
    } else {
      runnable.run();
    }
  }

  private boolean sendHelpIfRequested(CommandSource source, String[] args) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(audience(source))) {
      return AdaptPorts.get(CommandHelpPort.class, CommandHelpPort.NONE).help(source, engine(), Arrays.asList(args));
    }
  }

  private DirectorExecutionResult runDirector(CommandSource source, String label, String[] args) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(audience(source))) {
      return engine().execute(new DirectorInvocation(new SourceSender(source), label, Arrays.asList(args)));
    } catch (Throwable e) {
      AdaptLog.warn("Director command execution failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
      return DirectorExecutionResult.notHandled();
    }
  }

  private List<String> runDirectorTab(CommandSource source, String alias, String[] args) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(audience(source))) {
      return engine().tabComplete(new DirectorInvocation(new SourceSender(source), alias, Arrays.asList(args)));
    } catch (Throwable e) {
      AdaptLog.warn("Director tab completion failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
      return List.of();
    }
  }

  private static UUID audience(CommandSource source) {
    return source.player().map(PlayerRef::uuid).orElse(null);
  }

  private static void playFailureSound(CommandSource source) {
    Optional<PlayerRef> player = source.player();
    if (player.isPresent()) {
      SoundPlayer sp = SoundPlayer.of(player.get());
      sp.play(player.get().location(), Sounds.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.77f, 0.25f);
      sp.play(player.get().location(), Sounds.BLOCK_BEACON_DEACTIVATE, 0.2f, 0.45f);
    }
  }

  private static void playSuccessSound(CommandSource source) {
    Optional<PlayerRef> player = source.player();
    if (player.isPresent()) {
      SoundPlayer sp = SoundPlayer.of(player.get());
      sp.play(player.get().location(), Sounds.BLOCK_AMETHYST_CLUSTER_BREAK, 0.77f, 1.65f);
      sp.play(player.get().location(), Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.125f, 2.99f);
    }
  }

  /** Director's own refusals reach the source as plain text. */
  private record SourceSender(CommandSource source) implements DirectorSender {
    @Override
    public String getName() {
      return source.name();
    }

    @Override
    public boolean isPlayer() {
      return source.player().isPresent();
    }

    @Override
    public void sendMessage(String message) {
      if (message != null && !message.trim().isEmpty()) {
        source.sendLiteral(message);
      }
    }
  }
}
