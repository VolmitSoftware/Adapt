package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerDataUnlearnTest extends AdaptTestBase {
  @ParameterizedTest
  @ValueSource(strings = {"adaptations", "xp", "all"})
  void clearingProgressionNotifiesLearnedAdaptationsAfterAllLevelsAreCleared(String operation) {
    PlayerData data = new PlayerData();
    PlayerSkillLine tragoul = line(data, "tragoul");
    store(tragoul, "tragoul-skeletal-servant", 2);
    store(tragoul, "unlearned", 0);
    PlayerSkillLine golemancy = line(data, "golemancy");
    store(golemancy, "golem-bone-frame", 1);
    Adaptation<?> servant = adaptation("tragoul-skeletal-servant");
    Adaptation<?> frame = adaptation("golem-bone-frame");
    Adaptation<?> unlearned = adaptation("unlearned");
    SkillRegistry registry = mock(SkillRegistry.class);
    register(registry, "tragoul", List.of(servant, unlearned));
    register(registry, "golemancy", List.of(frame));
    AdaptServer server = mock(AdaptServer.class);
    when(server.getSkillRegistry()).thenReturn(registry);
    AdaptPlayer owner = mock(AdaptPlayer.class);
    Player player = mock(Player.class);
    when(owner.getPlayer()).thenReturn(player);
    when(owner.getServer()).thenReturn(server);
    data.bindRuntimeOwner(owner);
    AdaptAttributeService attributes = mock(AdaptAttributeService.class);
    doAnswer(call -> {
      assertThat(tragoul.getAdaptations()).isEmpty();
      assertThat(golemancy.getAdaptations()).isEmpty();
      line(data, "added-by-callback");
      return null;
    }).when(servant).onLevelChanged(player, 0);

    try (MockedStatic<AdaptAttributeService> service = mockStatic(AdaptAttributeService.class)) {
      service.when(AdaptAttributeService::get).thenReturn(attributes);
      switch (operation) {
        case "adaptations" -> data.clearAdaptations();
        case "xp" -> data.clearXp();
        case "all" -> data.clearAll();
        default -> throw new IllegalArgumentException(operation);
      }
      data.clearAdaptations();
    }

    verify(servant).onLevelChanged(player, 0);
    verify(frame).onLevelChanged(player, 0);
    verify(unlearned, never()).onLevelChanged(eq(player), anyInt());
    verify(attributes).removeAll(player, "tragoul-skeletal-servant");
    verify(attributes).removeAll(player, "golem-bone-frame");
    verify(attributes, never()).removeAll(player, "unlearned");
    assertThat(tragoul.getAdaptations()).isEmpty();
    assertThat(golemancy.getAdaptations()).isEmpty();
  }

  @Test
  void offlineClearingNeedsNoRuntimeRegistry() {
    PlayerData data = new PlayerData();
    PlayerSkillLine line = line(data, "tragoul");
    store(line, "tragoul-skeletal-servant", 3);

    data.clearAdaptations();

    assertThat(line.getAdaptations()).isEmpty();
  }

  private static PlayerSkillLine line(PlayerData data, String name) {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine(name);
    data.getSkillLines().put(name, line);
    return line;
  }

  private static void store(PlayerSkillLine line, String name, int level) {
    PlayerAdaptation stored = new PlayerAdaptation();
    stored.setId(name);
    stored.setLevel(level);
    line.getAdaptations().put(name, stored);
  }

  private static Adaptation<?> adaptation(String name) {
    Adaptation<?> adaptation = mock(Adaptation.class);
    when(adaptation.getName()).thenReturn(name);
    return adaptation;
  }

  private static void register(SkillRegistry registry, String name, List<Adaptation<?>> adaptations) {
    Skill<?> skill = mock(Skill.class);
    when(skill.getAdaptations()).thenReturn(new KList<>(adaptations));
    doReturn(skill).when(registry).getSkill(name);
  }
}
