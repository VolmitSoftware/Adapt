package art.arcane.adapt.client.server;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.client.link.ClientLink;
import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ClientProtocolException;
import art.arcane.adapt.client.protocol.FrameCodec;
import art.arcane.adapt.client.protocol.FrameFragments;
import art.arcane.adapt.client.protocol.FrameLimits;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.view.GroupLabelView;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A client that reads group names is sent every grouped skill's names once per catalog, in its declared order. */
class ClientGroupLabelsTest {
  @TempDir
  Path data;

  private final List<ServerFrame> frames = new ArrayList<>();
  private TestAdapt adapt;
  private TestPlatform platform;
  private FakePlayer player;
  private AdaptPlayer profile;
  private ClientUiService service;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Viewer", 0.5D, 64D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillHunter.class, SkillAgility.class));
    AdaptPorts.install(ClientLink.class, new ClientLink() {
      private final FrameFragments.Assembler assembler = new FrameFragments.Assembler(FrameLimits.MAX_S2C_BYTES);

      @Override
      public boolean open(UUID playerId) {
        return true;
      }

      @Override
      public int maxPayloadBytes() {
        return FrameLimits.MAX_S2C_BYTES;
      }

      @Override
      public void send(UUID playerId, byte[] frame) {
        try {
          byte[] whole = assembler.accept(frame);
          if (whole != null) {
            frames.add(FrameCodec.decodeServer(whole));
          }
        } catch (ClientProtocolException exception) {
          throw new AssertionError(exception);
        }
      }
    });
    profile = adapt.join(player);
    service = adapt.core().getClientUi();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aGroupLabelsClientIsSentTheNamesOfEveryGroupedSkillOnce() throws ClientProtocolException {
    hello(ClientProtocol.ALL);
    everySecond(3);
    List<ServerFrame.GroupLabels> sent = frames.stream().filter(ServerFrame.GroupLabels.class::isInstance)
        .map(ServerFrame.GroupLabels.class::cast).toList();
    assertThat(sent).hasSize(1);
    assertThat(sent.get(0).skills()).containsExactly(new GroupLabelView("adapt:hunter", List.of("When Struck", "The Hunt")));
  }

  @Test
  void aClientWithoutGroupLabelsIsNeverSentThem() throws ClientProtocolException {
    hello(ClientProtocol.ALL & ~ClientProtocol.GROUP_LABELS);
    everySecond(2);
    assertThat(frames).noneMatch(ServerFrame.GroupLabels.class::isInstance);
  }

  private void everySecond(int seconds) {
    for (int second = 0; second < seconds; second++) {
      ClientUiService.changed(profile);
      platform.scheduler().advance(20);
    }
  }

  private void hello(int mask) throws ClientProtocolException {
    service.receive(player.uuid(), FrameCodec.encodeClient(new ClientFrame.Hello(ClientProtocol.VERSION, mask)));
  }
}
