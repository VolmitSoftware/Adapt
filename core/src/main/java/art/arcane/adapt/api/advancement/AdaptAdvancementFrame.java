package art.arcane.adapt.api.advancement;

import art.arcane.adapt.platform.advancement.AdvancementFrame;

public enum AdaptAdvancementFrame {
  TASK(AdvancementFrame.TASK),
  GOAL(AdvancementFrame.GOAL),
  CHALLENGE(AdvancementFrame.CHALLENGE);

  private final AdvancementFrame platform;

  AdaptAdvancementFrame(AdvancementFrame platform) {
    this.platform = platform;
  }

  public AdvancementFrame platform() {
    return platform;
  }
}
