package art.arcane.adapt.service;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.content.gui.ConfigGui;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

class HotloadSVCGuiRefreshTest extends AdaptTestBase {
  private final ConcurrentHashMap<String, UIWindow> leftovers = new ConcurrentHashMap<>();
  private final UUID playerId = UUID.randomUUID();
  private Player player;
  private UIWindow window;
  private HotloadSVC hotload;

  @BeforeEach
  void openWindow() {
    player = mock(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(playerId);
    lenient().when(player.isOnline()).thenReturn(true);
    window = mock(UIWindow.class);
    lenient().when(window.isVisible()).thenReturn(true);
    lenient().when(window.getViewer()).thenReturn(player);
    leftovers.put(playerId.toString(), window);
    lenient().when(plugin.getGuiLeftovers()).thenReturn(leftovers);
    hotload = new HotloadSVC();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"golemancy-golem", "golemancy-node", "golemancy-roster", "golemancy-fetch", "confirm"})
  void windowWithATagTheServiceCannotRebuildStaysOpenAndUntouched(String tag) throws Exception {
    when(window.getTag()).thenReturn(tag);

    try (MockedStatic<SkillsGui> skills = mockStatic(SkillsGui.class);
         MockedStatic<ConfigGui> config = mockStatic(ConfigGui.class)) {
      refresh();

      skills.verify(() -> SkillsGui.open(any(Player.class)), never());
      config.verify(() -> ConfigGui.reopenFromTag(any(Player.class), anyString()), never());
    }
    verify(window, never()).close();
    assertThat(leftovers.get(playerId.toString())).isSameAs(window);
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
    when(window.getTag()).thenReturn("skill/rift");

    refresh();

    verify(skill).openGui(player);
  }

  @Test
  void adaptationTagStillReopensThatAdaptation() throws Exception {
    Skill<?> skill = skill("rift");
    Adaptation<?> adaptation = mock(Adaptation.class);
    when(adaptation.getName()).thenReturn("rift-access");
    when(adaptation.isEnabled()).thenReturn(true);
    KList<Adaptation<?>> adaptations = new KList<>();
    adaptations.add(adaptation);
    when(skill.getAdaptations()).thenReturn(adaptations);
    when(window.getTag()).thenReturn("skill/rift/rift-access");

    refresh();

    verify(adaptation).openGui(player);
  }

  private Skill<?> skill(String name) {
    Skill<?> skill = mock(Skill.class);
    when(skill.isEnabled()).thenReturn(true);
    SkillRegistry registry = mock(SkillRegistry.class);
    doReturn(skill).when(registry).getSkill(name);
    AdaptServer server = mock(AdaptServer.class);
    when(server.getSkillRegistry()).thenReturn(registry);
    when(plugin.getAdaptServer()).thenReturn(server);
    return skill;
  }

  private void refresh() throws Exception {
    Method refresh = HotloadSVC.class.getDeclaredMethod("refreshOpenAdaptGuis");
    refresh.setAccessible(true);
    try (MockedStatic<J> scheduling = mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      scheduling.when(() -> J.s(any(Runnable.class))).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(0).run();
        return null;
      });
      bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
      refresh.invoke(hotload);
    }
  }
}
