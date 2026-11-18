package art.arcane.adapt.command;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRoute;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

class AdaptCommandTreeTest extends AdaptTestBase {
  private static final List<String> COMMANDS = List.of(
      "boost", "claim-adaptation", "claim-skill", "determine", "effects", "experience", "global-boost", "gui", "knowledge",
      "language", "version", "configure",
      "clear adaptations", "clear all", "clear discoveries", "clear knowledge", "clear stats", "clear xp",
      "debug dump", "debug mode", "debug pap", "debug particle", "debug perf", "debug psp", "debug sound", "debug verbose", "debug version",
      "default adaptation", "default all", "default skill",
      "golem give", "golem list", "golem release", "golem returns", "golem roster", "golem wake",
      "reset confirm");

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
  void theRootPermissionGatesEveryCommandButTheDebugDump() {
    RecordingSource source = new RecordingSource(Set.of(), Optional.empty());
    AdaptScheduler scheduler = core.getPlatform().scheduler();
    doAnswer(call -> {
      call.<Runnable>getArgument(0).run();
      return true;
    }).when(scheduler).runGlobal(any(Runnable.class), anyLong());

    new AdaptCommands().execute(source, "adapt", new String[]{"debug", "verbose"});
    new AdaptCommands().execute(source, "adapt", new String[]{"debug", "dump"});

    assertThat(source.sections).hasSize(2);
    assertThat(source.sections.get(0)).contains(AdaptCommands.ROOT_PERMISSION);
    assertThat(source.sections.get(1)).contains("Unknown Adapt command");
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
    RecordingSource source = new RecordingSource(Set.of(AdaptCommands.ROOT_PERMISSION), Optional.empty());

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
