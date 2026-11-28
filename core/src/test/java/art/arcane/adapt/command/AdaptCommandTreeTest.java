package art.arcane.adapt.command;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.content.golem.command.GolemCommand;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.permission.PermissionDefault;
import art.arcane.adapt.platform.permission.PermissionHost;
import art.arcane.adapt.platform.permission.PermissionRegistration;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRoute;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

class AdaptCommandTreeTest extends AdaptTestBase {
  private static final List<String> COMMANDS = List.of(
      "boost", "claim-adaptation", "claim-skill", "client", "determine", "effects", "experience", "global-boost", "gui", "knowledge", "mutate", "mutation-reset", "unmutate",
      "language", "version", "configure",
      "clear adaptations", "clear all", "clear discoveries", "clear knowledge", "clear stats", "clear xp",
      "debug dump", "debug mode", "debug pap", "debug particle", "debug perf", "debug psp", "debug sound", "debug verbose", "debug version",
      "default adaptation", "default all", "default skill",
      "golem give", "golem list", "golem release", "golem returns", "golem roster", "golem wake",
      "pack offer",
      "orphans list", "orphans purge", "orphans refund",
      "reset confirm");

  @BeforeEach
  void installGolemCommands() {
    AddonCommands.install("adapt", List.of(new GolemCommand().tree()), null);
  }

  @AfterEach
  void removeGolemCommands() {
    AddonCommands.uninstall("adapt", null);
  }

  @Test
  void everySubcommandRoutesToItsInvocableNode() {
    DirectorRuntimeEngine engine = new AdaptCommands().engine();
    List<String> found = new ArrayList<>();
    collect(engine.getRoot(), "", found);

    assertThat(found).containsExactlyInAnyOrderElementsOf(COMMANDS);
    for (String command : COMMANDS) {
      DirectorRoute route = engine.route(DirectorInvocation.pretokenized(new Console(), "adapt", List.of(command.split(" "))));
      assertThat(route.invocable()).describedAs(command).isTrue();
      assertThat(path(route.node())).describedAs(command).isEqualTo(command);
    }
  }

  @Test
  void platformsWithoutThePackHaveNoPackCommand() {
    DirectorRuntimeEngine engine = AdaptCommands.withoutPack().engine();
    List<String> found = new ArrayList<>();
    collect(engine.getRoot(), "", found);

    assertThat(found).containsExactlyInAnyOrderElementsOf(COMMANDS.stream().filter((String command) -> !command.startsWith("pack ")).toList());
    assertThat(found).noneMatch((String command) -> command.startsWith("pack"));
  }

  @Test
  void aliasesRouteLikeOnBukkit() {
    DirectorRuntimeEngine engine = new AdaptCommands().engine();

    assertThat(path(engine.route(DirectorInvocation.pretokenized(new Console(), "adapt", List.of("config"))).node())).isEqualTo("configure");
    assertThat(path(engine.route(DirectorInvocation.pretokenized(new Console(), "adapt", List.of("cfg"))).node())).isEqualTo("configure");
    assertThat(path(engine.route(DirectorInvocation.pretokenized(new Console(), "adapt", List.of("dev", "verbose"))).node())).isEqualTo("debug verbose");
  }

  @Test
  void aSourceWithPermissionRunsACommandThroughTheTree() {
    RecordingSource source = new RecordingSource(Set.of(AdaptCommands.ROOT_PERMISSION, "adapt.idontknowwhatimdoingiswear"), Optional.empty());
    boolean verbose = AdaptConfig.get().isVerbose();
    try {
      new AdaptCommands().execute(source, "adapt", new String[]{"debug", "verbose"});

      assertThat(AdaptConfig.get().isVerbose()).isEqualTo(!verbose);
      assertThat(source.sections).singleElement().satisfies(line -> assertThat(line).contains("Verbose"));
    } finally {
      AdaptConfig.get().setVerbose(verbose);
    }
  }

  @Test
  void aCommandIsGatedByItsOwnNodeOnly() {
    RecordingSource source = new RecordingSource(Set.of(), Optional.empty());

    new AdaptCommands().execute(source, "adapt", new String[]{"debug", "verbose"});
    new AdaptCommands().execute(source, "adapt", new String[]{"debug", "dump"});
    new AdaptCommands().execute(source, "adapt", new String[]{"debug", "version"});

    assertThat(source.sections).hasSize(3);
    assertThat(source.sections.get(0)).contains("adapt.idontknowwhatimdoingiswear");
    assertThat(source.sections.get(1)).contains("adapt.debugdump");
    assertThat(source.sections.get(2)).contains(AdaptCommands.ROOT_PERMISSION);
  }

  @Test
  void everyCoreCommandDeclaresItsPermission() {
    List<String> undeclared = new ArrayList<>();
    collectUndeclared(new AdaptCommands().engine().getRoot(), undeclared);

    assertThat(undeclared).isEmpty();
  }

  @Test
  void aPlayerWithTheDefaultNodesRunsEveryPlayerCommand() {
    RecordingSource player = defaultPlayer();
    AdaptScheduler scheduler = core.getPlatform().scheduler();
    doAnswer(call -> {
      call.<Runnable>getArgument(0).run();
      return true;
    }).when(scheduler).runGlobal(any(Runnable.class), anyLong());

    for (String[] command : List.of(new String[]{"mutate"}, new String[]{"mutate", "axes"}, new String[]{"effects", "enabled=false"},
        new String[]{"pack", "offer"}, new String[]{"golem", "roster"})) {
      new AdaptCommands().execute(player, "adapt", command);
    }

    assertThat(player.sections).noneSatisfy(line -> assertThat(line).contains("lack the permission"));
    assertThat(player.sections).anySatisfy(line -> assertThat(line).contains("does not offer the Adapt pack"));
  }

  @Test
  void operatorCommandsStayRefusedToAPlayer() {
    Map<String, String> refusals = Map.ofEntries(
        Map.entry("boost", "adapt.boost"),
        Map.entry("global-boost", "adapt.boost.global"),
        Map.entry("client", "adapt.debug"),
        Map.entry("gui", "adapt.gui"),
        Map.entry("experience swords", "adapt.cheatitem"),
        Map.entry("knowledge swords", "adapt.cheatitem"),
        Map.entry("claim-skill swords 5", "adapt.determine"),
        Map.entry("claim-adaptation sword/poison_blade 1", "adapt.determine"),
        Map.entry("unmutate Viewer swords", "adapt.mutate.reset"),
        Map.entry("mutation-reset", "adapt.mutate.reset"),
        Map.entry("configure", "adapt.configurator"),
        Map.entry("default all", "adapt.configurator"),
        Map.entry("clear all", "adapt.clear"),
        Map.entry("reset confirm", "adapt.clear"),
        Map.entry("orphans list *", "adapt.orphans"),
        Map.entry("debug mode", "adapt.debug"),
        Map.entry("debug version", AdaptCommands.ROOT_PERMISSION),
        Map.entry("golem list", "adapt.golemancy.admin"));

    for (Map.Entry<String, String> refusal : refusals.entrySet()) {
      RecordingSource player = defaultPlayer();
      new AdaptCommands().execute(player, "adapt", refusal.getKey().split(" "));

      assertThat(player.sections).describedAs(refusal.getKey())
          .singleElement().satisfies(line -> assertThat(line).contains("lack the permission").contains("'" + refusal.getValue() + "'"));
    }
  }

  @Test
  void aPlayerCompletesOnlyTheCommandsTheyMayRun() {
    RecordingSource player = defaultPlayer();

    List<String> root = new AdaptCommands().complete(player, "adapt", new String[]{""});
    List<String> golem = new AdaptCommands().complete(player, "adapt", new String[]{"golem", ""});
    List<String> debug = new AdaptCommands().complete(player, "adapt", new String[]{"debug", ""});

    assertThat(root).containsExactlyInAnyOrder("effects", "golem", "language", "mutate", "pack");
    assertThat(golem).containsExactly("roster");
    assertThat(debug).isEmpty();
  }

  @Test
  void helpListsOnlyTheCommandsAPlayerMayRun() {
    List<String> listed = new ArrayList<>();
    AdaptPorts.install(CommandHelpPort.class, new ListingHelpPort(listed));
    try {
      new AdaptCommands().execute(defaultPlayer(), "adapt", new String[0]);
    } finally {
      AdaptPorts.uninstall(CommandHelpPort.class);
    }

    assertThat(listed).containsExactlyInAnyOrder("effects", "golem", "language", "mutate", "pack");
  }

  @Test
  void aCommandChecksItsOwnPermissionNode() {
    RecordingSource source = new RecordingSource(Set.of(AdaptCommands.ROOT_PERMISSION), Optional.empty());

    new AdaptCommands().execute(source, "adapt", new String[]{"global-boost", "seconds=10", "multiplier=2"});

    assertThat(source.sections).singleElement().satisfies(line -> assertThat(line).contains("adapt.boost.global"));
  }

  @Test
  void anUnknownCommandIsReported() {
    RecordingSource source = new RecordingSource(Set.of(AdaptCommands.ROOT_PERMISSION), Optional.empty());

    new AdaptCommands().execute(source, "adapt", new String[]{"nothing-here"});

    assertThat(source.sections).anySatisfy(line -> assertThat(line).contains("Unknown Adapt command"));
  }

  @Test
  void tabCompletionOffersTheVisibleSubcommands() {
    RecordingSource source = new RecordingSource(Set.of(AdaptCommands.ROOT_PERMISSION, "adapt.boost", "adapt.clear",
        "adapt.idontknowwhatimdoingiswear"), Optional.empty());

    List<String> root = new AdaptCommands().complete(source, "adapt", new String[]{""});
    List<String> debug = new AdaptCommands().complete(source, "adapt", new String[]{"debug", "ver"});

    assertThat(root).contains("boost", "clear", "debug", "golem", "reset").doesNotContain("version");
    assertThat(debug).contains("verbose", "version");
  }

  private static void collect(DirectorRuntimeNode node, String prefix, List<String> found) {
    for (DirectorRuntimeNode child : node.getChildren()) {
      String path = prefix.isEmpty() ? child.getDescriptor().getName() : prefix + " " + child.getDescriptor().getName();
      if (child.isInvocable()) {
        found.add(path);
      } else {
        collect(child, path, found);
      }
    }
  }

  private static void collectUndeclared(DirectorRuntimeNode node, List<String> undeclared) {
    for (DirectorRuntimeNode child : node.getChildren()) {
      if (child.isInvocable() && !AddonCommandRunner.isGraft(child) && AdaptCommands.declaredPermission(child).isEmpty()) {
        undeclared.add(path(child));
      }
      collectUndeclared(child, undeclared);
    }
  }

  /** A player holding exactly the nodes Adapt gives every player by default, plus plugin.yml's {@code adapt.pack}. */
  private static RecordingSource defaultPlayer() {
    Set<String> defaults = new HashSet<>(Set.of(CommandPack.PERMISSION));
    AdaptCommands.registerPermissionDefaults(new PermissionHost() {
      @Override
      public boolean has(PlayerRef player, String node) {
        return false;
      }

      @Override
      public boolean isSet(PlayerRef player, String node) {
        return false;
      }

      @Override
      public PermissionRegistration register(String node, String description, PermissionDefault fallback) {
        if (fallback == PermissionDefault.TRUE) {
          defaults.add(node);
        }
        return () -> {
        };
      }
    });
    return new RecordingSource(Set.copyOf(defaults), Optional.of(new FakePlayer("Viewer", new Loc(new FakeWorld("world"), 0, 64, 0))));
  }

  /** Lists the visible top-level commands that bare {@code /adapt} shows. */
  private record ListingHelpPort(List<String> listed) implements CommandHelpPort {
    @Override
    public boolean help(CommandSource source, DirectorRuntimeEngine engine, List<String> arguments, Predicate<DirectorRuntimeNode> visible) {
      for (DirectorRuntimeNode entry : engine.getRoot().getChildren()) {
        if (!entry.getDescriptor().isHidden() && visible.test(entry)) {
          listed.add(entry.getDescriptor().getName());
        }
      }
      return arguments.isEmpty();
    }

    @Override
    public void menu(CommandSource source, CommandMenu menu) {
    }

    @Override
    public String version(String plugin, String version) {
      return version;
    }
  }

  private static String path(DirectorRuntimeNode node) {
    List<String> names = new ArrayList<>();
    for (DirectorRuntimeNode cursor = node; cursor != null && cursor.getParent() != null; cursor = cursor.getParent()) {
      names.addFirst(cursor.getDescriptor().getName());
    }
    return String.join(" ", names);
  }

  private static final class Console implements DirectorSender {
    @Override
    public String getName() {
      return "Console";
    }

    @Override
    public boolean isPlayer() {
      return false;
    }

    @Override
    public void sendMessage(String message) {
    }
  }

  private static final class RecordingSource implements CommandSource {
    private final Set<String> permissions;
    private final Optional<PlayerRef> player;
    private final List<String> sections = new ArrayList<>();
    private final List<String> literals = new ArrayList<>();

    private RecordingSource(Set<String> permissions, Optional<PlayerRef> player) {
      this.permissions = permissions;
      this.player = player;
    }

    @Override
    public String name() {
      return "Console";
    }

    @Override
    public Optional<PlayerRef> player() {
      return player;
    }

    @Override
    public boolean hasPermission(String node) {
      return permissions.contains(node);
    }

    @Override
    public boolean isOp() {
      return true;
    }

    @Override
    public void sendSection(String sectionText) {
      sections.add(sectionText);
    }

    @Override
    public void sendLiteral(String plainText) {
      literals.add(plainText);
    }

    @Override
    public void sendMarkup(String markup) {
      literals.add(markup);
    }
  }
}
