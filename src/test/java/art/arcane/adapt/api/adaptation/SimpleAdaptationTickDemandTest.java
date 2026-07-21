package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.volmlib.util.inventorygui.Element;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimpleAdaptationTickDemandTest extends AdaptTestBase {
  @Test
  void learnerBoundTickerSleepsWithoutLearnersAndResumesImmediately() {
    AdaptServer server = mock(AdaptServer.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    TestAdaptation adaptation = new TestAdaptation();

    when(server.hasOnlineLearner(adaptation.getName())).thenReturn(false, true);

    assertThat(adaptation.hasTickDemand()).isFalse();
    assertThat(adaptation.hasTickDemand()).isTrue();

    when(server.hasOnlineLearner(adaptation.getName())).thenReturn(false);
    adaptation.retick();

    assertThat(adaptation.hasTickDemand()).isTrue();
  }

  private static final class TestAdaptation extends SimpleAdaptation<AdaptationConfig> {
    private TestAdaptation() {
      super("tick-demand-test");
    }

    @Override
    protected boolean usesLearnerBoundTicking() {
      return true;
    }

    @Override
    public void addStats(int level, Element element) {
    }
  }
}
