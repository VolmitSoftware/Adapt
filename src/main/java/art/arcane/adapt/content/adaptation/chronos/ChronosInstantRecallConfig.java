package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.util.config.ConfigDescription;

@ConfigDescription("Click with a clock to rewind to a recent snapshot with health and hunger restored.")
public class ChronosInstantRecallConfig {
  @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
  boolean permanent = false;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
  boolean enabled = true;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Instant Recall adaptation.", impact = "True enables this behavior and false disables it.")
  boolean playClockSounds = true;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Consumes one clock from the casting hand when a recall successfully starts.", impact = "True makes each recall cost a clock; false keeps recalls item-free.")
  boolean consumeClock = true;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of current health lost when a recall completes; never lethal, health is floored at 1.0.", impact = "Higher values make recalls cost more health; 0 disables the health cost.")
  double healthCostFraction = 0.5;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Rewind Trace Particles for the Chronos Instant Recall adaptation.", impact = "True enables this behavior and false disables it.")
  boolean showRewindTraceParticles = true;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Rewind Trace Points for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  int rewindTracePoints = 18;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Target rewind animation duration in milliseconds.", impact = "Higher values make recall rewind visuals slower/smoother; lower values make them faster.")
  int rewindAnimationDurationMillis = 1000;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Legacy fallback rewind animation ticks used when duration is invalid.", impact = "Retained for backward compatibility with older configs.")
  int rewindAnimationTicks = 18;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Temporarily switches to spectator during rewind animation for smoother camera movement through obstacles.", impact = "True improves rewind smoothness; false keeps player in survival during rewind.")
  boolean rewindUseTemporarySpectator = true;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Uses a client-side spectator camera anchor during rewind so server position only updates at the end.", impact = "True reduces jitter and rubber-banding by avoiding per-tick player teleports.")
  boolean rewindUseClientCamera = true;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Extra ticks to suppress teleport XP/stat tracking after recall rewinds.", impact = "Higher values make it safer against teleport-XP side effects from other skills.")
  int rewindTeleportXpSuppressExtraTicks = 10;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Enables direct click-with-clock activation for instant recall.", impact = "True allows recall by clicking with a valid recall clock; false disables direct clock-click activation.")
  boolean enableClockClickTrigger = true;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Allows left-click to activate recall when clock-click trigger is enabled.", impact = "True allows left-click activation; false blocks left-click activation.")
  boolean clockClickLeftClick = true;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Allows right-click to activate recall when clock-click trigger is enabled.", impact = "True allows right-click activation; false blocks right-click activation.")
  boolean clockClickRightClick = true;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Enables sprint + click activation for instant recall with a valid recall clock.", impact = "True allows sprint-click activation; false disables sprint-click activation.")
  boolean enableSprintClickTrigger = false;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Allows left-click for sprint-click recall trigger.", impact = "True enables left-click sprint activation; false disables it.")
  boolean sprintClickLeftClick = false;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Allows right-click for sprint-click recall trigger.", impact = "True enables right-click sprint activation; false disables it.")
  boolean sprintClickRightClick = true;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Allows click-in-air interactions for recall click triggers.", impact = "True lets air-clicks activate enabled click modes; false blocks air-click activations.")
  boolean allowAirClicks = true;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Allows click-on-block interactions for recall click triggers.", impact = "True lets block-clicks activate enabled click modes; false blocks block-click activations.")
  boolean allowBlockClicks = true;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Enables single-sneak activation for instant recall.", impact = "True allows pressing sneak once to trigger recall.")
  boolean enableSingleSneakTrigger = false;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Require sprinting for single-sneak instant recall trigger.", impact = "True requires sprint state when using single-sneak activation.")
  boolean singleSneakRequiresSprint = false;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Require holding a valid recall clock in either hand for single-sneak trigger.", impact = "True keeps single-sneak recall tied to clock usage.")
  boolean singleSneakRequiresClockInHand = true;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Enables double-tap jump activation for instant recall.", impact = "True allows jump-based recall trigger; false disables jump trigger.")
  boolean enableDoubleJumpTrigger = false;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Require sprinting while double-jumping to trigger recall.", impact = "True requires sprint state for double-jump trigger; false allows it without sprint.")
  boolean doubleJumpRequiresSprint = false;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Require holding a valid recall clock in either hand for double-jump trigger.", impact = "True requires clock-in-hand for jump trigger; false allows jump trigger without holding a clock.")
  boolean doubleJumpRequiresClockInHand = true;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum milliseconds allowed between jump taps for double-jump recall.", impact = "Higher values make double-tap detection easier; lower values make it stricter.")
  int doubleJumpWindowMillis = 450;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum upward velocity required to arm double-jump recall.", impact = "Higher values reduce accidental arm events; lower values increase sensitivity.")
  double doubleJumpMinVerticalVelocity = 0.2;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
  int baseCost = 3;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
  int maxLevel = 5;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
  int initialCost = 3;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
  double costFactor = 0.45;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Rewind Seconds for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double baseRewindSeconds = 3.5;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Rewind Seconds Per Level for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double rewindSecondsPerLevel = 0.35;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap for recall rewind duration in seconds.", impact = "Prevents high levels/config values from exceeding this rewind window.")
  double maxRewindSeconds = 5;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Padding Seconds for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  int cooldownPaddingSeconds = 1;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Snapshot Interval Millis for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  int snapshotIntervalMillis = 50;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls History Padding Seconds for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  int historyPaddingSeconds = 2;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Rewind Protection Ticks for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  int rewindProtectionTicks = 25;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Distance Block for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double xpPerDistanceBlock = 0.35;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Health Point for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double xpPerHealthPoint = 0.85;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Hunger Point for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double xpPerHungerPoint = 0.7;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Saturation Point for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double xpPerSaturationPoint = 0.18;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Level Multiplier Per Level for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double xpLevelMultiplierPerLevel = 0.08;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Min Raw Reward for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double xpMinRawReward = 1.35;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Min Award for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double xpMinAward = 0.5;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Max Award for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double xpMaxAward = 36;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Cross World Distance Credit for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double xpCrossWorldDistanceCredit = 16;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Diminish Window Millis for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  long xpDiminishWindowMillis = 45000;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Diminish Min Multiplier for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double xpDiminishMinMultiplier = 0.18;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Repeat Window Millis for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  long xpRepeatWindowMillis = 180000;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Repeat Source Radius for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double xpRepeatSourceRadius = 3.5;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Repeat Target Radius for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double xpRepeatTargetRadius = 3.5;
  @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Repeat Penalty Multiplier for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
  double xpRepeatPenaltyMultiplier = 0.2;
}
