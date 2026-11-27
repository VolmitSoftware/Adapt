package art.arcane.adapt.engine.world;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.client.view.PlayerViews;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.volmlib.util.collection.KList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerDataHeldPowerTest extends AdaptTestBase {
  private SkillRegistry registry;
  private Skill<?> axes;
  private Adaptation<?> chop;
  private Adaptation<?> cleave;
  private PlayerData data;
  private PlayerSkillLine axesLine;

  @BeforeEach
  void installRegistry() {
    registry = mock(SkillRegistry.class);
    AdaptServer server = mock(AdaptServer.class);
    lenient().when(server.getSkillRegistry()).thenReturn(registry);
    doReturn(server).when(core).getAdaptServer();
    axes = skill("axes");
    chop = adaptation("adapt:axe/chop", axes);
    cleave = adaptation("adapt:axe/cleave", axes);
    lenient().when(axes.getAdaptations()).thenReturn(new KList<>(List.of(chop, cleave)));
    data = new PlayerData();
    axesLine = line(data, "axes");
  }

  @Test
  @DisplayName("a stored adaptation its skill no longer registers takes no power")
  void unregisteredAdaptationTakesNoPower() {
    store(axesLine, "adapt:axe/chop", 2);
    store(axesLine, "kinetics-dive", 1);

    assertThat(data.getUsedPower()).isEqualTo(1);
  }

  @Test
  @DisplayName("adaptations stored under a skill that is not registered take no power")
  void unregisteredSkillTakesNoPower() {
    store(axesLine, "adapt:axe/chop", 1);
    store(line(data, "kinetics"), "kinetics-dive", 3);

    assertThat(data.getUsedPower()).isEqualTo(1);
  }

  @Test
  @DisplayName("an adaptation disabled in its config takes no power")
  void disabledAdaptationTakesNoPower() {
    store(axesLine, "adapt:axe/chop", 1);
    store(axesLine, "adapt:axe/cleave", 1);
    when(cleave.isEnabled()).thenReturn(false);

    assertThat(data.getUsedPower()).isEqualTo(1);
  }

  @Test
  @DisplayName("adaptations of a disabled skill take no power")
  void disabledSkillTakesNoPower() {
    store(axesLine, "adapt:axe/chop", 1);
    when(axes.isEnabled()).thenReturn(false);

    assertThat(data.getUsedPower()).isZero();
  }

  @Test
  @DisplayName("an adaptation the online player may not use takes no power")
  void forbiddenAdaptationTakesNoPower() {
    store(axesLine, "adapt:axe/chop", 1);
    store(axesLine, "adapt:axe/cleave", 1);
    PlayerRef player = mock(PlayerRef.class);
    when(player.isOnline()).thenReturn(true);
    lenient().when(axes.hasUsePermission(any())).thenReturn(true);
    lenient().when(chop.hasUsePermission(any())).thenReturn(true);
    lenient().when(cleave.hasUsePermission(any())).thenReturn(false);
    AdaptPlayer owner = mock(AdaptPlayer.class);
    when(owner.getPlayer()).thenReturn(player);
    data.bindRuntimeOwner(owner);

    assertThat(data.getUsedPower()).isEqualTo(1);
    assertThat(data.heldAdaptations()).extracting(PlayerData.HeldAdaptation::adaptation).containsExactly(chop);
  }

  @Test
  @DisplayName("the budget trim removes only adaptations that take power")
  void trimLeavesUncountedEntries() {
    store(axesLine, "adapt:axe/chop", 1);
    store(axesLine, "adapt:axe/cleave", 2);
    store(axesLine, "kinetics-dive", 4);
    data.setRegionPowerBonus(-100);

    data.pruneAdaptationsForPowerBudget();

    assertThat(axesLine.getAdaptations()).containsOnlyKeys("kinetics-dive");
    assertThat(data.getUsedPower()).isZero();
  }

  @Test
  @DisplayName("the client view stamp changes when the region power bonus changes")
  void fingerprintFollowsRegionPowerBonus() {
    long before = PlayerViews.fingerprint(data);

    data.setRegionPowerBonus(2);

    assertThat(PlayerViews.fingerprint(data)).isNotEqualTo(before);
  }

  private Skill<?> skill(String name) {
    Skill<?> skill = mock(Skill.class);
    lenient().when(skill.getId()).thenReturn(Id.parseAdapt(name));
    lenient().when(skill.isEnabled()).thenReturn(true);
    lenient().doReturn(skill).when(registry).getSkill(name);
    lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt(name).toString());
    lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt(name));
    return skill;
  }

  private static Adaptation<?> adaptation(String name, Skill<?> skill) {
    Adaptation<?> adaptation = mock(Adaptation.class);
    lenient().when(adaptation.getId()).thenReturn(Id.parseAdapt(name));
    lenient().when(adaptation.isEnabled()).thenReturn(true);
    lenient().doReturn(skill).when(adaptation).getSkill();
    return adaptation;
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
}
