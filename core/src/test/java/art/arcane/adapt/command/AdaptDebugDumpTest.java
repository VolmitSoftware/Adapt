package art.arcane.adapt.command;

import art.arcane.adapt.api.internal.HostPlatform;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.PlatformFamily;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptDebugDumpTest extends AdaptTestBase {
  private final List<CommandMenu> menus = new ArrayList<>();
  private final List<String> uploads = new ArrayList<>();
  private CommandSource console;

  @BeforeEach
  void installPlatform() {
    HostPlatform platform = core.getPlatform();
    PlatformHost host = platform.host();
    when(host.platformName()).thenReturn("Fabric");
    when(host.family()).thenReturn(PlatformFamily.FABRIC);
    when(host.minecraftVersion()).thenReturn("26.3");
    when(host.adaptVersion()).thenReturn("2.2.7");
    when(host.dataDirectory()).thenReturn(dataFolder.toPath());
    when(host.onlinePlayers()).thenReturn(List.of(mock(PlayerRef.class), mock(PlayerRef.class)));
    WorldRef overworld = mock(WorldRef.class);
    when(overworld.environment()).thenReturn(Environment.NORMAL);
    WorldRef nether = mock(WorldRef.class);
    when(nether.environment()).thenReturn(Environment.NETHER);
    when(host.worlds()).thenReturn(List.of(overworld, nether));
    when(platform.scheduler().runGlobal(any(Runnable.class))).thenAnswer(call -> {
      call.<Runnable>getArgument(0).run();
      return true;
    });
    doAnswer(call -> {
      call.<Runnable>getArgument(0).run();
      return null;
    }).when(platform.scheduler()).runAsync(any(Runnable.class));
    AdaptPorts.install(CommandHelpPort.class, new CommandHelpPort() {
      @Override
      public boolean help(CommandSource source, DirectorRuntimeEngine engine, List<String> arguments) {
        return false;
      }

      @Override
      public void menu(CommandSource source, CommandMenu menu) {
        menus.add(menu);
      }

      @Override
      public String version(String plugin, String version) {
        return plugin + " v" + version;
      }
    });
    console = mock(CommandSource.class);
    when(console.player()).thenReturn(Optional.empty());
    when(console.hasPermission(AdaptDebugDump.PERMISSION)).thenReturn(true);
  }

  @AfterEach
  void removePorts() {
    AdaptPorts.uninstall(CommandHelpPort.class);
  }

  @Test
  void writesTheReportIntoTheDebugFolderWithTheVolmLibFileName() throws IOException {
    new AdaptDebugDump(this::upload).request(console, false);

    List<Path> reports = reports();
    assertThat(reports).hasSize(1);
    assertThat(reports.getFirst().getFileName().toString()).matches("adapt-v2\\.2\\.7-debugdump-\\d{4}(-\\d{2}){5}\\.txt");
    String report = Files.readString(reports.getFirst(), StandardCharsets.UTF_8);
    assertThat(report).startsWith("== Adapt diagnostic report ==\n");
    assertThat(report).contains("Format: 1\n", "Plugin: Adapt\n", "Version: 2.2.7\n", "Command sender type: console-or-other\n",
        "== Server ==\n", "Implementation: Fabric\n", "Minecraft: 26.3\n", "Players online: 2\n", "Loaded worlds: 2\n",
        "Worlds nether: 1\n", "Worlds normal: 1\n", "TPS: unavailable\n", "Language: ", "== Java runtime ==",
        "== Memory ==", "== Plugin configuration files ==", "== Plugin artifact ==");
    assertThat(uploads).isEmpty();
    assertThat(menus.getLast().entries()).anyMatch(line -> line.contains(reports.getFirst().toAbsolutePath().normalize().toString()));
    assertThat(menus).allMatch(menu -> menu.command().equals("/adapt debug dump") && menu.parentCommand().equals("/adapt debug"));
  }

  @Test
  void uploadsTheSameReportWhenAsked() throws IOException {
    new AdaptDebugDump(this::upload).request(console, true);

    assertThat(uploads).containsExactly(Files.readString(reports().getFirst(), StandardCharsets.UTF_8));
    assertThat(menus.getLast().entries()).anyMatch(line -> line.contains("https://mclo.gs/test"));
  }

  @Test
  void aFailedUploadStillKeepsTheLocalReport() throws IOException {
    new AdaptDebugDump((String content, String source, String client) -> {
      throw new IOException("offline");
    }).request(console, true);

    assertThat(reports()).hasSize(1);
    assertThat(menus.getLast().entries()).hasSize(3);
  }

  @Test
  void withoutPermissionNothingIsWritten() throws IOException {
    when(console.hasPermission(AdaptDebugDump.PERMISSION)).thenReturn(false);

    new AdaptDebugDump(this::upload).request(console, true);

    assertThat(reports()).isEmpty();
    assertThat(menus).hasSize(1);
    assertThat(menus.getFirst().entries().getFirst()).contains(AdaptDebugDump.PERMISSION);
  }

  @Test
  void aSecondReportInTheSameSecondGetsASuffix() throws IOException {
    Instant now = Instant.parse("2026-11-15T10:00:00Z");

    Path first = AdaptDebugDump.write(dataFolder.toPath(), "2.2.7", now, "a");
    Path second = AdaptDebugDump.write(dataFolder.toPath(), "2.2.7", now, "b");

    assertThat(first.getFileName().toString()).isEqualTo("adapt-v2.2.7-debugdump-2026-11-15-10-00-00.txt");
    assertThat(second.getFileName().toString()).isEqualTo("adapt-v2.2.7-debugdump-2026-11-15-10-00-00-2.txt");
  }

  private URI upload(String content, String source, String client) {
    uploads.add(content);
    assertThat(source).isEqualTo("VolmitSoftware - Adapt - v2.2.7");
    assertThat(client).isEqualTo("VolmitSoftware/Adapt/2.2.7");
    return URI.create("https://mclo.gs/test");
  }

  private List<Path> reports() throws IOException {
    Path directory = dataFolder.toPath().resolve("debug");
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files.toList();
    }
  }
}
