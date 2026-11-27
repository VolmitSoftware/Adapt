package art.arcane.adapt.command;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerAdaptation;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.collection.KList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class CommandAdaptPowerReportTest extends AdaptTestBase {
  @Test
  void reportListsEveryStoredAdaptationAndWhetherItUsesPower() {
    SkillRegistry registry = mock(SkillRegistry.class);
    AdaptServer server = mock(AdaptServer.class);
    lenient().when(server.getSkillRegistry()).thenReturn(registry);
    doReturn(server).when(core).getAdaptServer();
    Skill<?> axes = mock(Skill.class);
    lenient().when(axes.getId()).thenReturn(Id.adapt("axes"));
    lenient().when(axes.isEnabled()).thenReturn(true);
    Adaptation<?> chop = mock(Adaptation.class);
    lenient().when(chop.getId()).thenReturn(Id.adapt("axe/chop"));
    lenient().when(chop.isEnabled()).thenReturn(true);
    lenient().doReturn(axes).when(chop).getSkill();
    lenient().when(axes.getAdaptations()).thenReturn(new KList<>(List.of(chop)));
    lenient().doReturn(axes).when(registry).getSkill("axes");
    lenient().doReturn(axes).when(registry).getSkill(Id.parseAdapt("axes").toString());
    lenient().doReturn(axes).when(registry).getSkill(Id.parseAdapt("axes"));
    PlayerData data = new PlayerData();
    data.setRegionPowerBonus(3);
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("adapt:axes");
    data.getSkillLines().put("adapt:axes", line);
    store(line, "adapt:axe/chop", 2);
    store(line, "kinetics-dive", 1);

    List<String> report = new ArrayList<>();
    for (String text : CommandAdapt.powerReport(data, "Steve")) {
      report.add(C.stripColor(text));
    }

    assertThat(report).containsExactly(
        "Steve uses 1 of " + data.getMaxPower() + " power.",
        "adapt:axes: adapt:axe/chop at level 2 uses 1 power.",
        "adapt:axes: kinetics-dive at level 1 uses no power."
    );
  }

  private static void store(PlayerSkillLine line, String name, int level) {
    PlayerAdaptation stored = new PlayerAdaptation();
    stored.setId(name);
    stored.setLevel(level);
    line.getAdaptations().put(name, stored);
  }
}
