package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.util.config.ConfigDoc;

public class AdaptationConfig {
  @ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
  public boolean enabled = true;
  @ConfigDoc(value = "Prevents normal player unlearning after this adaptation is purchased.", impact = "The first purchase still uses the configured cost and confirmation; administrative bypass may lower it without a refund.")
  public boolean permanent = false;
  @ConfigDoc(value = "Shows the particle effects for this adaptation.", impact = "True enables this behavior and false disables it.")
  public boolean showParticles = true;
  @ConfigDoc(value = "Plays the sound effects for this adaptation.", impact = "True enables this behavior and false disables it.")
  public boolean showSounds = true;
  @ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
  public int baseCost = 4;
  @ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
  public double costFactor = 0.45;
  @ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
  public int maxLevel = 5;
  @ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
  public int initialCost = 2;
}
