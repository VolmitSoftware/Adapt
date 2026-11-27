package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.testkit.MockedEngine;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.engine.world.AdaptServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimpleAdaptationTickDemandTest {
  private MockedEngine engine;

  @AfterEach
  void clearCore() {
    if (engine != null) {
      engine.close();
    }
    TestCores.clear();
  }

  @Test
  void learnerBoundTickerSleepsWithoutLearnersAndResumesImmediately() {
    AdaptServer server = mock(AdaptServer.class);
    AdaptCore core = TestCores.install();
    TestCores.setField(core, "adaptServer", server);
    engine = new MockedEngine(core);
    TestAdaptation adaptation = TestContent.adaptation(Id.adapt("tick"), TestAdaptation::new);

    when(server.getLearnerIndexRevision()).thenReturn(1L, 2L);
    when(server.hasOnlineLearner(adaptation.getId().toString())).thenReturn(false, true);

    assertThat(adaptation.hasTickDemand()).isFalse();
    assertThat(adaptation.hasTickDemand()).isTrue();

    adaptation.retick();

    assertThat(adaptation.hasTickDemand()).isTrue();
  }

  @Test
  void learnerLookupIsReusedUntilTheLearnerIndexChanges() {
    AdaptServer server = mock(AdaptServer.class);
    AdaptCore core = TestCores.install();
    TestCores.setField(core, "adaptServer", server);
    engine = new MockedEngine(core);
    TestAdaptation adaptation = TestContent.adaptation(Id.adapt("tick"), TestAdaptation::new);

    when(server.getLearnerIndexRevision()).thenReturn(7L);
    when(server.hasOnlineLearner(adaptation.getId().toString())).thenReturn(true);

    assertThat(adaptation.hasTickDemand()).isTrue();
    assertThat(adaptation.hasTickDemand()).isTrue();
    assertThat(adaptation.hasTickDemand()).isTrue();
    verify(server, times(1)).hasOnlineLearner(adaptation.getId().toString());

    when(server.getLearnerIndexRevision()).thenReturn(8L);
    when(server.hasOnlineLearner(adaptation.getId().toString())).thenReturn(false);

    assertThat(adaptation.hasTickDemand()).isFalse();
    verify(server, times(2)).hasOnlineLearner(adaptation.getId().toString());
  }

  private static final class TestAdaptation extends SimpleAdaptation<AdaptationConfig> {
    private TestAdaptation() {
      super(Id.adapt("tick-demand-test"));
      registerConfiguration(AdaptationConfig.class);
    }

    @Override
    protected boolean usesLearnerBoundTicking() {
      return true;
    }

    @Override
    public void addStats(int level, StatLines element) {
    }
  }
}
