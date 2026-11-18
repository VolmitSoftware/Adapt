package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeAnimals;

import java.util.List;

import static org.mockito.Mockito.lenient;

final class PeacefulAnimals {
  private static final List<String> TYPES = List.of("cow", "mooshroom", "sheep", "pig", "chicken", "goat", "rabbit", "horse", "donkey",
      "mule", "llama", "trader_llama", "camel", "sniffer", "armadillo", "turtle", "frog", "fox", "wolf", "cat", "ocelot", "panda",
      "polar_bear", "bee", "axolotl", "strider", "parrot", "skeleton_horse", "zombie_horse");

  private PeacefulAnimals() {
  }

  static boolean isPeaceful(Id type) {
    return type != null && type.isMinecraft() && TYPES.contains(type.path());
  }

  static FakeAnimals install() {
    FakeAnimals animals = new FakeAnimals();
    for (String type : TYPES) {
      animals.peaceful(Id.minecraft(type));
    }
    lenient().when(AdaptCore.platform().host().animals()).thenReturn(animals);
    return animals;
  }
}
