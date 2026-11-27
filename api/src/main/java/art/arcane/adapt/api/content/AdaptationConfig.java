package art.arcane.adapt.api.content;

import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.preference.PreferencePolicy;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What every adaptation config has. An adaptation's own config extends this, adds its public fields, and sets its
 * prices in its no-argument constructor:
 *
 * <pre>{@code
 * public static final class Config extends AdaptationConfig {
 *   @ConfigDoc("Chance per level to transmute a drop.")
 *   public double chancePerLevel = 0.04D;
 *
 *   public Config() {
 *     baseCost = 3;
 *     maxLevel = 5;
 *   }
 * }
 * }</pre>
 *
 * <p>The file is {@code <data>/adaptations/<namespace>/<path>.toml}, written with these defaults on first start and
 * reloaded when it changes. Fields may be public or package-private; a config class needs a public no-argument
 * constructor.
 */
@ApiStatus.AvailableSince("1.0.0")
public class AdaptationConfig {
  @ConfigDoc(value = "Server defaults and permitted choices for this adaptation's player preferences.", impact = "Each registered control can be locked or restricted without changing another player's saved choices.")
  public Map<String, PreferencePolicy> playerPreferences = Map.of();
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
  @ConfigDoc(value = "Adaptations the player must already have learned, each written as adaptationId@level; an id without a namespace is in this adaptation's namespace.", impact = "Learning or leveling this adaptation is refused until every listed adaptation is learned at that level, and those adaptations cannot be unlearned below it while this one is learned.")
  public List<String> requires = new ArrayList<>();
  @ConfigDoc(value = "Milliseconds between ticks of this adaptation, when it ticks.", impact = "Lower values react faster and cost more server time; 0 keeps the adaptation's own interval.")
  public long setInterval = 0L;
}
