package art.arcane.adapt.command;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.AddonState;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.command.AdaptCommand;
import art.arcane.adapt.api.command.ArgumentTypes;
import art.arcane.adapt.api.command.CommandArgument;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Commands an addon adds below {@code /adapt}: routing, typed arguments, completion, permissions, help, removal. */
class AddonCommandsTest {
  private static final CommandArgument<Integer> AMOUNT =
      CommandArgument.required("amount", ArgumentTypes.integer(1, 10), Text.literal("How many"));
  private static final CommandArgument<String> LABEL =
      CommandArgument.optional("label", ArgumentTypes.choice("small", "large"), Text.literal("Which size"));

  @TempDir
  Path data;

  private TestAdapt adapt;
  private final List<AddonHandle> handles = new ArrayList<>();

  @AfterEach
  void stop() {
    handles.forEach(AddonHandle::uninstall);
    AdaptPorts.uninstall(CommandHelpPort.class);
    if (adapt != null) {
      adapt.close();
    }
  }

  @Test
  void anAddonCommandRunsWithTypedArgumentsWithoutAdaptsRootPermission() {
    RocksAddon addon = start();
    RecordingSource source = new RecordingSource(Set.of());

    new AdaptCommands().execute(source, "adapt", new String[] {"rocks", "count", "3", "large"});
    new AdaptCommands().execute(source, "adapt", new String[] {"rocks", "count", "label=small", "4"});
    new AdaptCommands().execute(source, "adapt", new String[] {"rocks", "count", "7"});

    assertThat(addon.runs).containsExactly("3:large", "4:small", "7:none");
    assertThat(source.messages).containsExactly("counted", "counted", "counted");
  }

  @Test
  void anInvalidOrMissingArgumentIsReportedAndNothingRuns() {
    RocksAddon addon = start();
    RecordingSource source = new RecordingSource(Set.of());

    new AdaptCommands().execute(source, "adapt", new String[] {"rocks", "count", "99"});
    new AdaptCommands().execute(source, "adapt", new String[] {"rocks", "count"});
    new AdaptCommands().execute(source, "adapt", new String[] {"rocks", "count", "2", "huge"});
    new AdaptCommands().execute(source, "adapt", new String[] {"rocks", "count", "2", "small", "extra"});

    assertThat(addon.runs).isEmpty();
    assertThat(source.messages).hasSize(4);
    assertThat(source.messages.get(0)).contains("99").contains("amount");
    assertThat(source.messages.get(1)).contains("amount");
    assertThat(source.messages.get(2)).contains("huge").contains("label");
    assertThat(source.messages.get(3)).contains("extra");
  }

  @Test
  void aPermissionOnASubcommandIsCheckedAndRegistered() {
    RocksAddon addon = start();
    RecordingSource without = new RecordingSource(Set.of());
    RecordingSource with = new RecordingSource(Set.of("rockcmd.secret"));

    new AdaptCommands().execute(without, "adapt", new String[] {"rocks", "secret"});
    new AdaptCommands().execute(with, "adapt", new String[] {"rocks", "secret"});

    assertThat(without.messages).singleElement().asString().contains("rockcmd.secret");
    assertThat(addon.runs).containsExactly("secret");
    assertThat(((TestPlatform.Permissions) adapt.platform().platform().permissions()).registered()).contains("rockcmd.secret");
    assertThat(new AdaptCommands().complete(without, "adapt", new String[] {"rocks", "secret", ""})).isEmpty();
  }

  @Test
  void tabCompletionOffersSubcommandsAndArgumentValues() {
    start();
    RecordingSource source = new RecordingSource(Set.of());
    AdaptCommands commands = new AdaptCommands();

    assertThat(commands.complete(source, "adapt", new String[] {"roc"})).contains("rocks");
    assertThat(commands.complete(source, "adapt", new String[] {"rocks", ""})).contains("count", "secret");
    assertThat(commands.complete(source, "adapt", new String[] {"rocks", "count", ""})).containsExactly("1");
    assertThat(commands.complete(source, "adapt", new String[] {"rocks", "count", "2", "l"})).containsExactly("large");
    assertThat(commands.complete(source, "adapt", new String[] {"rocks", "count", "label="}))
        .containsExactly("label=small", "label=large");
  }

  @Test
  void theHelpMenuSeesTheCommandInAdaptsOwnTree() {
    start();
    List<DirectorRuntimeEngine> engines = new CopyOnWriteArrayList<>();
    AdaptPorts.install(CommandHelpPort.class, new CommandHelpPort() {
      @Override
      public boolean help(CommandSource source, DirectorRuntimeEngine engine, List<String> arguments) {
        engines.add(engine);
        return arguments.size() == 1;
      }

      @Override
      public void menu(CommandSource source, CommandMenu menu) {
      }

      @Override
      public String version(String plugin, String version) {
        return version;
      }
    });
    RecordingSource source = new RecordingSource(Set.of());

    new AdaptCommands().execute(source, "adapt", new String[] {"rocks"});

    DirectorRuntimeNode rocks = child(engines.get(0).getRoot(), "rocks").orElseThrow();
    assertThat(rocks.isGroup()).isTrue();
    assertThat(rocks.getDescriptor().getDescription()).isEqualTo("Rock tools");
    DirectorRuntimeNode count = child(rocks, "count").orElseThrow();
    assertThat(count.isInvocable()).isTrue();
    assertThat(count.getDescriptor().getParameters()).extracting(parameter -> parameter.getName())
        .containsExactly("amount", "label");
    assertThat(source.messages).isEmpty();
  }

  @Test
  void aCommandWordTakenByAdaptOrAnotherAddonRejectsTheAddon() {
    start();

    AddonHandle builtin = install(new SingleCommandAddon("thief", "gui"));
    AddonHandle rival = install(new SingleCommandAddon("rival", "rocks"));

    assertThat(builtin.state()).isEqualTo(AddonState.REJECTED);
    assertThat(builtin.rejection()).get().asString().contains("gui").contains("Adapt");
    assertThat(rival.state()).isEqualTo(AddonState.REJECTED);
    assertThat(rival.rejection()).get().asString().contains("rocks").contains("rockcmd");
  }

  @Test
  void uninstallingTheAddonRemovesItsCommandsAndPermissions() {
    RocksAddon addon = start();
    RecordingSource source = new RecordingSource(Set.of());
    AdaptCommands commands = new AdaptCommands();
    commands.execute(source, "adapt", new String[] {"rocks", "count", "1"});

    handles.get(0).uninstall();
    commands.execute(source, "adapt", new String[] {"rocks", "count", "2"});

    assertThat(addon.runs).containsExactly("1:none");
    assertThat(child(commands.engine().getRoot(), "rocks")).isEmpty();
    assertThat(AddonCommands.installed()).doesNotContainKey("rockcmd");
    assertThat(((TestPlatform.Permissions) adapt.platform().platform().permissions()).registered()).doesNotContain("rockcmd.secret");
  }

  private RocksAddon start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    RocksAddon addon = new RocksAddon();
    assertThat(install(addon).state()).isEqualTo(AddonState.ACTIVE);
    return addon;
  }

  private AddonHandle install(AdaptAddon addon) {
    AddonHandle handle = AdaptApi.addons().install(addon);
    handles.add(handle);
    return handle;
  }

  private static Optional<DirectorRuntimeNode> child(DirectorRuntimeNode node, String name) {
    for (DirectorRuntimeNode child : node.getChildren()) {
      if (child.getDescriptor().getName().equals(name)) {
        return Optional.of(child);
      }
    }
    return Optional.empty();
  }

  private static final class RocksAddon implements AdaptAddon {
    private final List<String> runs = new CopyOnWriteArrayList<>();

    @Override
    public String namespace() {
      return "rockcmd";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.command(AdaptCommand.named("rocks")
          .help(Text.literal("Rock tools"))
          .then(AdaptCommand.named("count")
              .help(Text.literal("Count rocks"))
              .argument(AMOUNT)
              .argument(LABEL)
              .executes(invocation -> {
                runs.add(invocation.get(AMOUNT) + ":" + invocation.find(LABEL).orElse("none"));
                invocation.caller().send(Text.literal("counted"));
              })
              .build())
          .then(AdaptCommand.named("secret")
              .help(Text.literal("Secret rocks"))
              .permission("rockcmd.secret")
              .executes(invocation -> runs.add("secret"))
              .build())
          .build());
    }
  }

  private record SingleCommandAddon(String namespace, String word) implements AdaptAddon {
    @Override
    public void register(ContentRegistrar registrar) {
      registrar.command(AdaptCommand.named(word).help(Text.literal(word)).executes(invocation -> {
      }).build());
    }
  }

  private static final class RecordingSource implements CommandSource {
    private final Set<String> permissions;
    private final List<String> messages = new CopyOnWriteArrayList<>();

    private RecordingSource(Set<String> permissions) {
      this.permissions = permissions;
    }

    @Override
    public String name() {
      return "Tester";
    }

    @Override
    public Optional<PlayerRef> player() {
      return Optional.empty();
    }

    @Override
    public boolean hasPermission(String node) {
      return permissions.contains(node);
    }

    @Override
    public boolean isOp() {
      return false;
    }

    @Override
    public void sendSection(String sectionText) {
      messages.add(sectionText);
    }

    @Override
    public void sendLiteral(String plainText) {
      messages.add(plainText);
    }

    @Override
    public void sendMarkup(String markup) {
      messages.add(markup);
    }
  }
}
