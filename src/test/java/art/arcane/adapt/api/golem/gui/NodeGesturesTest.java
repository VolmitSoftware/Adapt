package art.arcane.adapt.api.golem.gui;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NodeGesturesTest {
  @ParameterizedTest(name = "{0} sneaking={1} crookMain={2} crookOff={3} main={4} -> {5}")
  @CsvSource({
      "RIGHT, false, true, false, IRON_HOE, OPEN_GUI",
      "LEFT, false, true, false, IRON_HOE, NEXT_CHANNEL",
      "RIGHT, true, true, false, IRON_HOE, CYCLE_PRIORITY",
      "LEFT, true, true, false, IRON_HOE, REMOVE",
      "RIGHT, false, false, true, RED_DYE, SET_CHANNEL",
      "RIGHT, true, false, true, LIME_DYE, SET_CHANNEL",
      "RIGHT, false, false, true, COBBLESTONE, TOGGLE_FILTER",
      "RIGHT, true, false, true, WHEAT, TOGGLE_FILTER",
      "RIGHT, false, false, true, AIR, NONE",
      "LEFT, false, false, true, RED_DYE, NONE",
      "LEFT, true, false, true, COBBLESTONE, NONE",
      "RIGHT, false, false, false, RED_DYE, NONE",
      "LEFT, false, false, false, AIR, NONE",
      "RIGHT, false, true, true, IRON_HOE, OPEN_GUI",
      "LEFT, true, true, true, IRON_HOE, REMOVE"
  })
  void theRoutingTableMapsEveryInputToItsAction(NodeGestures.Click click, boolean sneaking, boolean crookInMainHand, boolean crookInOffHand,
                                                Material mainHand, NodeGestures.Action expected) {
    assertThat(NodeGestures.route(click, sneaking, crookInMainHand, crookInOffHand, mainHand)).isEqualTo(expected);
  }

  @Test
  void anEmptyMainHandAsNullRoutesNowhere() {
    assertThat(NodeGestures.route(NodeGestures.Click.RIGHT, false, false, true, null)).isEqualTo(NodeGestures.Action.NONE);
  }
}
