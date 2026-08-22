package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.volmlib.util.inventorygui.Element;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimpleAdaptationTickDemandTest extends AdaptTestBase {
  @Test
  void learnerBoundTickerSleepsWithoutLearnersAndResumesImmediately() {
    AdaptServer server = mock(AdaptServer.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    TestAdaptation adaptation = new TestAdaptation();

    when(server.getLearnerIndexRevision()).thenReturn(1L, 2L);
    when(server.hasOnlineLearner(adaptation.getName())).thenReturn(false, true);

    assertThat(adaptation.hasTickDemand()).isFalse();
    assertThat(adaptation.hasTickDemand()).isTrue();

    adaptation.retick();

    assertThat(adaptation.hasTickDemand()).isTrue();
  }

  @Test
  void learnerLookupIsReusedUntilTheLearnerIndexChanges() {
    AdaptServer server = mock(AdaptServer.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    TestAdaptation adaptation = new TestAdaptation();

    when(server.getLearnerIndexRevision()).thenReturn(7L);
    when(server.hasOnlineLearner(adaptation.getName())).thenReturn(true);

    assertThat(adaptation.hasTickDemand()).isTrue();
    assertThat(adaptation.hasTickDemand()).isTrue();
    assertThat(adaptation.hasTickDemand()).isTrue();
    verify(server, times(1)).hasOnlineLearner(adaptation.getName());

    when(server.getLearnerIndexRevision()).thenReturn(8L);
    when(server.hasOnlineLearner(adaptation.getName())).thenReturn(false);

    assertThat(adaptation.hasTickDemand()).isFalse();
    verify(server, times(2)).hasOnlineLearner(adaptation.getName());
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
