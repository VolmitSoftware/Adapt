package art.arcane.adapt.api.content;

import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.preference.PreferencePolicy;
import org.jetbrains.annotations.ApiStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What every skill config has. A skill's own config extends this and adds its XP amounts and rewards as public
 * fields. The file is {@code <data>/skills/<namespace>/<path>.toml}.
 */
@ApiStatus.AvailableSince("1.0.0")
public class SkillConfig {
  @ConfigDoc(value = "Server defaults and permitted choices for this skill's player preferences.", impact = "Each registered control can be locked or restricted without changing another player's saved choices.")
  public Map<String, PreferencePolicy> playerPreferences = new LinkedHashMap<>();
  @ConfigDoc(value = "Enables or disables this skill and everything registered under it.", impact = "Set to false to remove the skill from menus and stop its XP and adaptations.")
  public boolean enabled = true;
  @ConfigDoc(value = "Skill colour as #RRGGBB, used for its name and pages.", impact = "Leave empty for the colour the skill was written with.")
  public String skillColor = "";
  @ConfigDoc(value = "XP below which an award in this skill is not shown to the player.", impact = "Higher values hide more small awards from the XP ticker.")
  public double minXp = 100;
  @ConfigDoc(value = "Milliseconds between ticks of this skill, when it ticks.", impact = "Lower values react faster and cost more server time; 0 keeps the skill's own interval.")
  public long setInterval = 0L;
}
