package art.arcane.adapt.util.config;

import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.testkit.MockedHosts;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.engine.gui.ConfigGui;
import art.arcane.adapt.engine.gui.SkillsGui;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.inventorygui.GuiSessions;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.collection.KList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigHotloadGuiRefreshTest extends AdaptTestBase {
  private final UUID playerId = UUID.randomUUID();
  private PlayerRef player;
  private GuiWindow window;
  private ConfigHotload hotload;
  private MockedHosts hosts;

  @BeforeEach
  void openWindow() {
    player = mock(PlayerRef.class);
    lenient().when(player.uuid()).thenReturn(playerId);
    lenient().when(player.isOnline()).thenReturn(true);
    window = mock(GuiWindow.class);
    lenient().when(window.isVisible()).thenReturn(true);
    lenient().when(window.getViewer()).thenReturn(player);
    GuiSessions.track(window);
    lenient().when(core.getPlatform().host().player(playerId)).thenReturn(player);
    hotload = new ConfigHotload();
    hosts = new MockedHosts();
  }

  @AfterEach
  void clearSessions() {
    GuiSessions.clear();
    hosts.close();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"golemancy-golem", "golemancy-node", "golemancy-roster", "golemancy-fetch", "confirm"})
  void windowWithATagTheServiceCannotRebuildStaysOpenAndUntouched(String tag) throws Exception {
    when(window.getTag()).thenReturn(tag);

    try (MockedStatic<SkillsGui> skills = mockStatic(SkillsGui.class);
         MockedStatic<ConfigGui> config = mockStatic(ConfigGui.class)) {
      refresh();

      skills.verify(() -> SkillsGui.open(any(PlayerRef.class)), never());
      config.verify(() -> ConfigGui.reopenFromTag(any(PlayerRef.class), anyString()), never());
    }
    verify(window, never()).close();
    assertThat(GuiSessions.snapshot().get(playerId)).isSameAs(window);
  }

  @Test
  void rootTagStillReopensTheSkillsGui() throws Exception {
    when(window.getTag()).thenReturn("/");

    try (MockedStatic<SkillsGui> skills = mockStatic(SkillsGui.class)) {
      refresh();

      skills.verify(() -> SkillsGui.open(player));
    }
  }

  @Test
  void configTagStillReopensTheConfigGui() throws Exception {
    when(window.getTag()).thenReturn("config/general");

    try (MockedStatic<ConfigGui> config = mockStatic(ConfigGui.class)) {
      refresh();

      config.verify(() -> ConfigGui.reopenFromTag(player, "config/general"));
    }
  }

  @Test
  void skillTagStillReopensThatSkill() throws Exception {
    Skill<?> skill = skill("rift");
    SkillHost host = hosts.skill(skill);
    when(window.getTag()).thenReturn("skill/rift");

    refresh();

    verify(host).openGui(player);
  }

  @Test
  void adaptationTagStillReopensThatAdaptation() throws Exception {
    Skill<?> skill = skill("rift");
    Adaptation<?> adaptation = mock(Adaptation.class);
    lenient().when(adaptation.getId()).thenReturn(Id.adapt("rift/access"));
    when(adaptation.isEnabled()).thenReturn(true);
    KList<Adaptation<?>> adaptations = new KList<>();
    adaptations.add(adaptation);
    when(skill.getAdaptations()).thenReturn(adaptations);
    AdaptationHost host = hosts.adaptation(adaptation);
    when(window.getTag()).thenReturn("skill/adapt:rift|adapt:rift/access");

    refresh();

    verify(host).openGui(player);
  }

  private Skill<?> skill(String name) {
    Skill<?> skill = mock(Skill.class);
    when(skill.isEnabled()).thenReturn(true);
    SkillRegistry registry = mock(SkillRegistry.class);
    doReturn(skill).when(registry).getSkill(name);
    lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt(name).toString());
    lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt(name));
    AdaptServer server = mock(AdaptServer.class);
    when(server.getSkillRegistry()).thenReturn(registry);
    doReturn(server).when(core).getAdaptServer();
    return skill;
  }

  private void refresh() throws Exception {
    Method refresh = ConfigHotload.class.getDeclaredMethod("refreshOpenAdaptGuis");
    refresh.setAccessible(true);
    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.s(any(Runnable.class))).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(0).run();
        return null;
      });
      refresh.invoke(hotload);
    }
  }
}
