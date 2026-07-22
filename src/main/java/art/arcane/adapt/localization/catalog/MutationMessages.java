package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class MutationMessages {
  public static final TextKey RUNTIME_UNAVAILABLE = TextKey.of("mutation.runtime.unavailable", "Mutation runtime is not available.");
  public static final TextKey FEATURE_OFF = TextKey.of("mutation.runtime.feature_off", "Experimental Mutations are turned off on this server.");
  public static final TextKey TYPE_OFF = TextKey.of("mutation.runtime.type_off", "{mutation} is turned off in server settings.");
  public static final TextKey MISSING_PERMISSION = TextKey.of("mutation.runtime.missing_permission", "Missing permission {permission}.");
  public static final TextKey DUPLICATE_SLOTS = TextKey.of("mutation.runtime.duplicate_slots", "The same Mutation cannot be used in both slots.");
  public static final TextKey DUPLICATE_OCCUPANCY = TextKey.of("mutation.runtime.duplicate_occupancy", "The same Mutation cannot occupy both slots.");
  public static final TextKey CONFLICT_OTHER = TextKey.of("mutation.runtime.conflict_other", "This choice conflicts with your other Mutation.");
  public static final TextKey CONFLICTS_WITH = TextKey.of("mutation.runtime.conflicts_with", "{mutation} conflicts with {other}.");
  public static final TextKey SLOT_LOCKED_CURRENT_LEVEL = TextKey.of("mutation.runtime.slot_locked_current_level", "This slot is locked at your current level.");
  public static final TextKey FIRST_SLOT_UNLOCKS = TextKey.of("mutation.runtime.first_slot_unlocks", "The first Mutation slot unlocks at level {level}.");
  public static final TextKey WORLD_UNAVAILABLE = TextKey.of("mutation.runtime.world_unavailable", "This Mutation does not work in {world}.");
  public static final TextKey ACTIVE_PERFECT = TextKey.of("mutation.runtime.active_perfect", "Active with no downside.");
  public static final TextKey ACTIVE_BURDEN = TextKey.of("mutation.runtime.active_burden", "Active; downside applies.");
  public static final TextKey READY_TO_USE = TextKey.of("mutation.runtime.ready_to_use", "Ready to use.");
  public static final TextKey REQUIREMENTS_UNAVAILABLE = TextKey.of("mutation.runtime.requirements_unavailable", "Mutation requirements are unavailable.");
  public static final TextKey LEARN_BOTH_DOMAINS = TextKey.of("mutation.runtime.learn_both_domains", "Learn an Adaptation from both the {first} and {second} skill groups.");
  public static final TextKey LEARN_DOMAIN_LEVEL = TextKey.of("mutation.runtime.learn_domain_level", "Learn a {domain} Adaptation at level {level} or higher.");
  public static final TextKey READY = TextKey.of("mutation.runtime.ready", "Ready");
  public static final TextKey INVALID_SELECTION = TextKey.of("mutation.selection.invalid", "Invalid Mutation selection.");
  public static final TextKey PLAYER_DATA_UNAVAILABLE = TextKey.of("mutation.selection.player_data_unavailable", "Player Mutation data is unavailable.");
  public static final TextKey SLOT_CHANGED_CONFIRM = TextKey.of("mutation.selection.slot_changed_confirm", "The Mutation slot changed while confirmation was open; review it again.");
  public static final TextKey LOADOUT_CHANGED_CONFIRM = TextKey.of("mutation.selection.loadout_changed_confirm", "The Mutation loadout changed while confirmation was open; review it again.");
  public static final TextKey ALREADY_EQUIPPED = TextKey.of("mutation.selection.already_equipped", "{mutation} is already equipped in slot {slot}.");
  public static final TextKey EQUIPPED = TextKey.of("mutation.selection.equipped", "Equipped {mutation} in slot {slot}.");
  public static final TextKey INVALID_SLOT = TextKey.of("mutation.selection.invalid_slot", "Invalid Mutation slot.");
  public static final TextKey SLOT_ALREADY_EMPTY = TextKey.of("mutation.selection.slot_already_empty", "Mutation slot {slot} is already empty.");
  public static final TextKey CLEARED_SLOT = TextKey.of("mutation.selection.cleared_slot", "Cleared Mutation slot {slot}.");
  public static final TextKey CHANGES_OFF = TextKey.of("mutation.selection.changes_off", "Mutation changes are turned off on this server.");
  public static final TextKey BOOKSHELF_REQUIRED = TextKey.of("mutation.selection.bookshelf_required", "Return to the Adapt bookshelf before changing a Mutation slot.");
  public static final TextKey SLOT_UNLOCKS = TextKey.of("mutation.selection.slot_unlocks", "Mutation slot {slot} unlocks at level {level}.");
  public static final TextKey PERMANENT_CHOICES = TextKey.of("mutation.selection.permanent_choices", "This server makes Mutation choices permanent.");
  public static final TextKey SLOT_COOLDOWN = TextKey.of("mutation.selection.slot_cooldown", "Mutation slot {slot} cannot be changed yet.");
  public static final TextKey COMBAT_COOLDOWN = TextKey.of("mutation.selection.combat_cooldown", "You cannot change Mutations during combat.");
  public static final TextKey MUTATION_DISABLED = TextKey.of("mutation.selection.mutation_disabled", "That Mutation is disabled.");
  public static final TextKey WORLD_DISABLED = TextKey.of("mutation.selection.world_disabled", "That Mutation does not work in this world.");
  public static final TextKey LOGIN_REQUIREMENTS = TextKey.of("mutation.runtime.login_requirements", "Log in to check requirements.");
  public static final TextKey EQUIPPED_OFFLINE = TextKey.of("mutation.runtime.equipped_offline", "Equipped but inactive while the player is offline.");
  public static final TextKey FIRST_SLOT_LOCKED = TextKey.of("mutation.runtime.first_slot_locked", "The first Mutation slot is locked.");
  public static final TextKey TOGGLE_USAGE = TextKey.of("mutation.command.toggle_usage", "Use enabled=on, enabled=off, or omit it to toggle.");
  public static final TextKey COOPERATIVE_ENABLED = TextKey.of("mutation.command.cooperative_enabled", "Cooperative Mutation effects are enabled.");
  public static final TextKey COOPERATIVE_DISABLED = TextKey.of("mutation.command.cooperative_disabled", "Cooperative Mutation effects are disabled.");
  public static final TextKey COOPERATIVE_ENABLED_OFF = TextKey.of("mutation.command.cooperative_enabled_off", "Cooperative Mutation effects are enabled. Preference saved; experimental Mutations are currently off.");
  public static final TextKey COOPERATIVE_DISABLED_OFF = TextKey.of("mutation.command.cooperative_disabled_off", "Cooperative Mutation effects are disabled. Preference saved; experimental Mutations are currently off.");
  public static final TextKey CHOICE_SAVED_OFF = TextKey.of("mutation.command.choice_saved_off", "The choice was saved but stays inactive while experimental Mutations are off.");
  public static final TextKey DISCOVERED = TextKey.of("mutation.command.discovered", "Discovered {mutation} for {player}.");
  public static final TextKey UNDISCOVERED = TextKey.of("mutation.command.undiscovered", "Undiscovered {mutation} for {player}.");
  public static final TextKey COOLDOWNS_CLEARED = TextKey.of("mutation.command.cooldowns_cleared", "Cleared Mutation switching cooldowns for {player}.");
  public static final TextKey STATE_REFRESHED = TextKey.of("mutation.command.state_refreshed", "Refreshed Mutation state for {player}.");
  public static final TextKey SLOT_RANGE = TextKey.of("mutation.command.slot_range", "Mutation slot must be 1 or 2.");
  public static final TextKey OVERRIDE_USAGE = TextKey.of("mutation.command.override_usage", "Use enabled=on, enabled=off, or enabled=clear.");
  public static final TextKey SLOT_NORMAL = TextKey.of("mutation.command.slot_normal", "Mutation slot {slot} is now using its normal level requirement for {player}.");
  public static final TextKey SLOT_FORCED_UNLOCKED = TextKey.of("mutation.command.slot_forced_unlocked", "Mutation slot {slot} is now forced unlocked for {player}.");
  public static final TextKey SLOT_FORCED_LOCKED = TextKey.of("mutation.command.slot_forced_locked", "Mutation slot {slot} is now forced locked for {player}.");
  public static final TextKey DATA_CLEARED = TextKey.of("mutation.command.data_cleared", "Cleared Mutation data for {player} without changing XP, skills, Knowledge, Adaptations, or advancements.");
  public static final TextKey PERFECT_NORMAL = TextKey.of("mutation.command.perfect_normal", "Perfect Adaptation is now using its normal level requirement for {player}.");
  public static final TextKey PERFECT_FORCED_ON = TextKey.of("mutation.command.perfect_forced_on", "Perfect Adaptation is now forced on for {player}.");
  public static final TextKey PERFECT_FORCED_OFF = TextKey.of("mutation.command.perfect_forced_off", "Perfect Adaptation is now forced off for {player}.");
  public static final TextKey MUTATIONS_UNAVAILABLE = TextKey.of("mutation.command.unavailable", "Mutations are not available right now.");
  public static final TextKey RELOAD_FAILED = TextKey.of("mutation.command.reload_failed", "Mutation configuration reload failed; previous settings remain active.");
  public static final TextKey RELOAD_ENABLED = TextKey.of("mutation.command.reload_enabled", "Mutation configuration reloaded. Experimental Mutations are enabled; online slots are being refreshed.");
  public static final TextKey RELOAD_DISABLED = TextKey.of("mutation.command.reload_disabled", "Mutation configuration reloaded. Experimental Mutations are disabled; saved choices are retained.");
  public static final TextKey UNKNOWN_MUTATION = TextKey.of("mutation.command.unknown_mutation", "Unknown Mutation: {mutation}");
  public static final TextKey SNAPSHOT_TITLE = TextKey.of("mutation.command.snapshot_title", "Experimental Mutations for {player}");
  public static final TextKey FEATURE_ENABLED = TextKey.of("mutation.command.feature_enabled", "Feature: enabled");
  public static final TextKey FEATURE_DISABLED = TextKey.of("mutation.command.feature_disabled", "Feature: disabled");
  public static final TextKey PERFECT_ACTIVE = TextKey.of("mutation.command.perfect_active", "Perfect Adaptation: active");
  public static final TextKey PERFECT_INACTIVE = TextKey.of("mutation.command.perfect_inactive", "Perfect Adaptation: inactive");
  public static final TextKey COOPERATIVE_STATUS_ENABLED = TextKey.of("mutation.command.cooperative_status_enabled", "Cooperative effects: enabled");
  public static final TextKey COOPERATIVE_STATUS_DISABLED = TextKey.of("mutation.command.cooperative_status_disabled", "Cooperative effects: disabled");
  public static final TextKey STORED_RESOURCES = TextKey.of("mutation.command.stored_resources", "Stored resources: Deep Charge {deepCharge} • Root Charge {rootCharge} • Craft/Brew/Enchant steps {steps}");
  public static final TextKey LINKED_GEAR = TextKey.of("mutation.command.linked_gear", "Linked gear: Temperbound {temperbound} • Masterwork {masterwork} • Deepblood Tool {deepblood} • Trophy {trophy}");
  public static final TextKey YES = TextKey.of("mutation.command.yes", "yes");
  public static final TextKey NO = TextKey.of("mutation.command.no", "no");
  public static final TextKey STATE_READY = TextKey.of("mutation.state.ready", "ready");
  public static final TextKey STATE_ACTIVE = TextKey.of("mutation.state.active", "active");
  public static final TextKey STATE_INACTIVE = TextKey.of("mutation.state.inactive", "inactive");
  public static final TextKey STATE_LOCKED = TextKey.of("mutation.state.locked", "locked");
  public static final TextKey STATE_OFF = TextKey.of("mutation.state.off", "off");
  public static final TextKey STATE_UNAVAILABLE = TextKey.of("mutation.state.unavailable", "unavailable");
  public static final TextKey STATE_BLOCKED = TextKey.of("mutation.state.blocked", "blocked");
  public static final TextKey SLOT_LOCKED = TextKey.of("mutation.command.slot_locked", "Slot {slot}: locked");
  public static final TextKey SLOT_EMPTY = TextKey.of("mutation.command.slot_empty", "Slot {slot}: empty");
  public static final TextKey SLOT_UNAVAILABLE = TextKey.of("mutation.command.slot_unavailable", "Slot {slot}: {mutation} (unavailable)");
  public static final TextKey SLOT_VALUE = TextKey.of("mutation.command.slot_value", "Slot {slot}: {mutation} [{state}]");
  public static final TextKey SLOT_VALUE_REASON = TextKey.of("mutation.command.slot_value_reason", "Slot {slot}: {mutation} [{state}] - {reason}");
  public static final TextKey RESULT_UPDATED = TextKey.of("mutation.command.result_updated", "Mutation state updated.");
  public static final TextKey RESULT_NOT_CHANGED = TextKey.of("mutation.command.result_not_changed", "Mutation state was not changed.");
  public static final TextKey REMAINING_COOLDOWN = TextKey.of("mutation.command.remaining_cooldown", "{message} Remaining cooldown: {duration}");
  public static final TextKey SLOT_UNLOCKED_TITLE = TextKey.of("mutation.progression.slot_unlocked_title", "Mutation Slot Unlocked");
  public static final TextKey SLOT_UNLOCKED_SUBTITLE = TextKey.of("mutation.progression.slot_unlocked_subtitle", "Your first Mutation slot is now unlocked. Visit an Adapt bookshelf.");
  public static final TextKey SECOND_SLOT_TITLE = TextKey.of("mutation.progression.second_slot_title", "Second Mutation Slot");
  public static final TextKey SECOND_SLOT_SUBTITLE = TextKey.of("mutation.progression.second_slot_subtitle", "Your second Mutation slot is now unlocked.");
  public static final TextKey PERFECT_TITLE = TextKey.of("mutation.progression.perfect_title", "Perfect Adaptation");
  public static final TextKey PERFECT_SUBTITLE = TextKey.of("mutation.progression.perfect_subtitle", "Your equipped Mutations no longer have downsides.");
  public static final TextKey PERFECT_LOST = TextKey.of("mutation.progression.perfect_lost", "Perfect Adaptation is no longer active; Mutation downsides apply again.");
  public static final TextKey GUI_DISABLED = TextKey.of("mutation.gui.disabled", "Mutations are experimental and disabled on this server.");
  public static final TextKey GUI_TITLE = TextKey.of("mutation.gui.title", "Experimental Mutations • L{level}");
  public static final TextKey GUI_PERFECT = TextKey.of("mutation.gui.perfect", "Perfect Adaptation");
  public static final TextKey GUI_PERFECT_ACTIVE = TextKey.of("mutation.gui.perfect_active", "Both equipped Mutations lose their downsides.");
  public static final TextKey GUI_PERFECT_LOCKED = TextKey.of("mutation.gui.perfect_locked", "Unlocks at the configured Adapt level.");
  public static final TextKey GUI_COOPERATIVE_ON = TextKey.of("mutation.gui.cooperative_on", "Cooperative Effects: On");
  public static final TextKey GUI_COOPERATIVE_OFF = TextKey.of("mutation.gui.cooperative_off", "Cooperative Effects: Off");
  public static final TextKey GUI_COOPERATIVE_ALLOW = TextKey.of("mutation.gui.cooperative_allow", "Allow shared Mutation effects from nearby players.");
  public static final TextKey GUI_COOPERATIVE_OPT_OUT = TextKey.of("mutation.gui.cooperative_opt_out", "Click to opt out.");
  public static final TextKey GUI_COOPERATIVE_OPT_IN = TextKey.of("mutation.gui.cooperative_opt_in", "Click to opt in.");
  public static final TextKey GUI_CHANGES_AVAILABLE = TextKey.of("mutation.gui.changes_available", "Changes Available");
  public static final TextKey GUI_VIEW_ONLY = TextKey.of("mutation.gui.view_only", "View Only");
  public static final TextKey GUI_MAY_CHANGE = TextKey.of("mutation.gui.may_change", "You may change your Mutation slots here.");
  public static final TextKey GUI_VISIT_BOOKSHELF = TextKey.of("mutation.gui.visit_bookshelf", "Visit and interact with a valid Adapt bookshelf to change slots.");
  public static final TextKey GUI_UNAVAILABLE_DISCOVERIES = TextKey.of("mutation.gui.unavailable_discoveries", "Unavailable Discoveries");
  public static final TextKey GUI_SAVED_NOT_INSTALLED = TextKey.of("mutation.gui.saved_not_installed", "Saved Mutations that are not currently installed:");
  public static final TextKey GUI_SLOT_VALUE = TextKey.of("mutation.gui.slot_value", "Slot {slot}: {mutation}");
  public static final TextKey GUI_SLOT_LOCKED = TextKey.of("mutation.gui.slot_locked", "Slot {slot}: Locked");
  public static final TextKey GUI_RIGHT_CLICK_CLEAR = TextKey.of("mutation.gui.right_click_clear", "Right click to clear this slot.");
  public static final TextKey GUI_SKILL_GROUPS = TextKey.of("mutation.gui.skill_groups", "Skill groups: {first} + {second}");
  public static final TextKey GUI_DISCOVERED = TextKey.of("mutation.gui.discovered", "Discovered");
  public static final TextKey GUI_UNDISCOVERED = TextKey.of("mutation.gui.undiscovered", "Undiscovered");
  public static final TextKey GUI_EQUIPPED_SLOT = TextKey.of("mutation.gui.equipped_slot", "Equipped in slot {slot}");
  public static final TextKey GUI_CLICK_INSPECT = TextKey.of("mutation.gui.click_inspect", "Click to inspect");
  public static final TextKey GUI_REQUIRES = TextKey.of("mutation.gui.requires", "Requires: one learned Adaptation from each skill group at level {level}+");
  public static final TextKey GUI_BENEFIT = TextKey.of("mutation.gui.benefit", "What it does: {benefit}");
  public static final TextKey GUI_BURDEN = TextKey.of("mutation.gui.burden", "Downside: {burden}");
  public static final TextKey GUI_PERFECT_RESULT = TextKey.of("mutation.gui.perfect_result", "At level 200: {result}");
  public static final TextKey GUI_TELL = TextKey.of("mutation.gui.tell", "What you see: {tell}");
  public static final TextKey GUI_CONTROL = TextKey.of("mutation.gui.control", "How to use: {control}");
  public static final TextKey GUI_PVP = TextKey.of("mutation.gui.pvp", "Affects PvP");
  public static final TextKey GUI_NO_PVP = TextKey.of("mutation.gui.no_pvp", "Does not directly affect PvP");
  public static final TextKey GUI_STATUS = TextKey.of("mutation.gui.status", "Status: {state}");
  public static final TextKey GUI_MATCHING_NONE = TextKey.of("mutation.gui.matching_none", "Matching Adaptations: none currently learned");
  public static final TextKey GUI_MATCHING = TextKey.of("mutation.gui.matching", "Matching Adaptations: {adaptations}");
  public static final TextKey GUI_SLOT_LOCKED_NAME = TextKey.of("mutation.gui.slot_locked_name", "Slot {slot} Locked");
  public static final TextKey GUI_SLOT_VIEW_ONLY = TextKey.of("mutation.gui.slot_view_only", "Slot {slot} • View Only");
  public static final TextKey GUI_BOOKSHELF_BEFORE_CHANGE = TextKey.of("mutation.gui.bookshelf_before_change", "Use a valid Adapt bookshelf before changing this slot.");
  public static final TextKey GUI_EQUIPPED_IN_SLOT = TextKey.of("mutation.gui.equipped_in_slot", "Equipped in Slot {slot}");
  public static final TextKey GUI_ALREADY_EQUIPPED_SLOT = TextKey.of("mutation.gui.already_equipped_slot", "Already Equipped in Slot {slot}");
  public static final TextKey GUI_CONFLICTS_WITH = TextKey.of("mutation.gui.conflicts_with", "Conflicts with {mutation}");
  public static final TextKey GUI_CLEAR_OTHER_FIRST = TextKey.of("mutation.gui.clear_other_first", "Clear or replace slot {slot} before selecting this Mutation.");
  public static final TextKey GUI_UNAVAILABLE_SLOT = TextKey.of("mutation.gui.unavailable_slot", "Unavailable for Slot {slot}");
  public static final TextKey GUI_EQUIP_SLOT = TextKey.of("mutation.gui.equip_slot", "Equip in Slot {slot}");
  public static final TextKey GUI_CURRENT = TextKey.of("mutation.gui.current", "Current: {mutation}");
  public static final TextKey GUI_CLICK_PREVIEW = TextKey.of("mutation.gui.click_preview", "Click to preview and confirm this change.");
  public static final TextKey GUI_CONFIRM_SLOT = TextKey.of("mutation.gui.confirm_slot", "{mutation} → Slot {slot}");
  public static final TextKey GUI_REPLACING = TextKey.of("mutation.gui.replacing", "Replacing: {mutation}");
  public static final TextKey GUI_OTHER_SLOT = TextKey.of("mutation.gui.other_slot", "Other slot: {mutation}");
  public static final TextKey GUI_WITH_OTHER = TextKey.of("mutation.gui.with_other", "With other slot: {interaction}");
  public static final TextKey GUI_SLOT_COOLDOWN = TextKey.of("mutation.gui.slot_cooldown", "Slot cooldown: {duration}");
  public static final TextKey GUI_CONFIRM_CHANGE = TextKey.of("mutation.gui.confirm_change", "Confirm Change");
  public static final TextKey GUI_CONFIRM_CHANGE_TITLE = TextKey.of("mutation.gui.confirm_change_title", "Confirm Mutation Change");
  public static final TextKey GUI_CLEAR_SLOT = TextKey.of("mutation.gui.clear_slot", "Clear Slot {slot}");
  public static final TextKey GUI_UNEQUIP = TextKey.of("mutation.gui.unequip", "Unequip {mutation}?");
  public static final TextKey GUI_NORMAL_COOLDOWN = TextKey.of("mutation.gui.normal_cooldown", "This starts the normal slot cooldown.");
  public static final TextKey GUI_CONFIRM_CLEAR = TextKey.of("mutation.gui.confirm_clear", "Confirm Clear");
  public static final TextKey GUI_LINK_ARMOR = TextKey.of("mutation.gui.link_armor", "Link Current Armor");
  public static final TextKey GUI_LINK_ARMOR_LORE = TextKey.of("mutation.gui.link_armor_lore", "Wear four durable armor pieces, then click to link them.");
  public static final TextKey GUI_UNLINK_ARMOR = TextKey.of("mutation.gui.unlink_armor", "Unlink Armor");
  public static final TextKey GUI_UNLINK_ARMOR_LORE = TextKey.of("mutation.gui.unlink_armor_lore", "Unlink the current four-piece armor set.");
  public static final TextKey GUI_REVIEW_CONFIRM = TextKey.of("mutation.gui.review_confirm", "Click to review and confirm.");
  public static final TextKey GUI_UNLINK_MASTERWORK = TextKey.of("mutation.gui.unlink_masterwork", "Unlink Masterwork");
  public static final TextKey GUI_UNLINK_MASTERWORK_LORE = TextKey.of("mutation.gui.unlink_masterwork_lore", "Unlink the current Masterwork tool.");
  public static final TextKey GUI_REPLACEMENT_COOLDOWN_BEGINS = TextKey.of("mutation.gui.replacement_cooldown_begins", "A replacement cooldown begins after confirmation.");
  public static final TextKey GUI_BIND_HELD_TOOL = TextKey.of("mutation.gui.bind_held_tool", "Bind Held Tool");
  public static final TextKey GUI_DEEPBLOOD_BIND_LORE = TextKey.of("mutation.gui.deepblood_bind_lore", "Link the durable tool in your main hand to Deepblood break protection.");
  public static final TextKey GUI_DEEPBLOOD_REPLACE_LORE = TextKey.of("mutation.gui.deepblood_replace_lore", "Linking another tool replaces your current Deepblood tool.");
  public static final TextKey GUI_BIND_COOLING = TextKey.of("mutation.gui.bind_cooling", "Bind Held Tool • Cooling Down");
  public static final TextKey GUI_MASTERWORK_BIND_LORE = TextKey.of("mutation.gui.masterwork_bind_lore", "Hold a durable tool you personally crafted.");
  public static final TextKey GUI_REPLACEMENT_AVAILABLE = TextKey.of("mutation.gui.replacement_available", "Replacement available in {duration}.");
  public static final TextKey GUI_EQUIPMENT_CHANGED = TextKey.of("mutation.gui.equipment_changed", "That linked equipment changed before it could be confirmed.");
  public static final TextKey GUI_UNLINK_TEMPERBOUND = TextKey.of("mutation.gui.unlink_temperbound", "Unlink Temperbound Armor?");
  public static final TextKey GUI_TEMPERBOUND_UNLINKED = TextKey.of("mutation.gui.temperbound_unlinked", "The current four-piece armor set will be unlinked.");
  public static final TextKey GUI_NO_REPAIR_RETURN = TextKey.of("mutation.gui.no_repair_return", "This does not repair, protect, or return any armor.");
  public static final TextKey GUI_UNLINK_MASTERWORK_QUESTION = TextKey.of("mutation.gui.unlink_masterwork_question", "Unlink Masterwork Tool?");
  public static final TextKey GUI_MASTERWORK_UNLINKED = TextKey.of("mutation.gui.masterwork_unlinked", "The current Masterwork tool will be unlinked.");
  public static final TextKey GUI_REPLACEMENT_COOLDOWN = TextKey.of("mutation.gui.replacement_cooldown", "Replacement cooldown: {duration}");
  public static final TextKey GUI_CONFIRM_EQUIPMENT_TITLE = TextKey.of("mutation.gui.confirm_equipment_title", "Confirm Equipment Change");
  public static final TextKey GUI_OPTION_UNAVAILABLE = TextKey.of("mutation.gui.option_unavailable", "This option is no longer available.");
  public static final TextKey GUI_EQUIPMENT_CHANGED_REVIEW = TextKey.of("mutation.gui.equipment_changed_review", "That linked equipment changed while confirmation was open; review it again.");
  public static final TextKey GUI_CONTROLS_UNAVAILABLE = TextKey.of("mutation.gui.controls_unavailable", "Mutation equipment controls are not available right now.");
  public static final TextKey EQUIPMENT_TEMPERBOUND_SUCCESS = TextKey.of("mutation.equipment.temperbound_success", "Your current armor set is now Temperbound.");
  public static final TextKey EQUIPMENT_TEMPERBOUND_UNLINKED = TextKey.of("mutation.equipment.temperbound_unlinked", "Your Temperbound armor is now unlinked.");
  public static final TextKey EQUIPMENT_MASTERWORK_SUCCESS = TextKey.of("mutation.equipment.masterwork_success", "The held tool is now your Masterwork.");
  public static final TextKey EQUIPMENT_DEEPBLOOD_SUCCESS = TextKey.of("mutation.equipment.deepblood_success", "The held tool is now linked to your Deep Charge reserve.");
  public static final TextKey EQUIPMENT_MASTERWORK_UNLINKED_DELAY = TextKey.of("mutation.equipment.masterwork_unlinked_delay", "Your Masterwork tool was unlinked. A replacement can be linked in {duration}.");
  public static final TextKey EQUIPMENT_MASTERWORK_UNLINKED = TextKey.of("mutation.equipment.masterwork_unlinked", "Your Masterwork tool was unlinked.");
  public static final TextKey EQUIPMENT_REPLACEMENT_DELAY = TextKey.of("mutation.equipment.replacement_delay", "A replacement Masterwork can be linked in {duration}.");
  public static final TextKey EQUIPMENT_TEMPERBOUND_REQUIREMENT = TextKey.of("mutation.equipment.temperbound_requirement", "Wear a complete four-piece durable armor set that is not already linked.");
  public static final TextKey EQUIPMENT_TEMPERBOUND_FAILURE = TextKey.of("mutation.equipment.temperbound_failure", "The Temperbound armor could not be unlinked.");
  public static final TextKey EQUIPMENT_MASTERWORK_REQUIREMENT = TextKey.of("mutation.equipment.masterwork_requirement", "Hold a durable tool you personally crafted with no linked Masterwork tool.");
  public static final TextKey EQUIPMENT_MASTERWORK_FAILURE = TextKey.of("mutation.equipment.masterwork_failure", "The Masterwork tool could not be unlinked yet.");
  public static final TextKey EQUIPMENT_DEEPBLOOD_REQUIREMENT = TextKey.of("mutation.equipment.deepblood_requirement", "Hold a durable tool in your main hand before linking it to Deepblood.");
  public static final TextKey GUI_PAGE_COUNT = TextKey.of("mutation.gui.page_count", "Page {page}/{pages} • {count} Mutations");
  public static final TextKey GUI_NO_OTHER_SELECTED = TextKey.of("mutation.gui.no_other_selected", "No other Mutation selected");
  public static final TextKey GUI_SAME_NOT_ALLOWED = TextKey.of("mutation.gui.same_not_allowed", "The same Mutation cannot occupy both slots");
  public static final TextKey GUI_COMPATIBLE_WITH = TextKey.of("mutation.gui.compatible_with", "Compatible with {mutation}");
  public static final TextKey GUI_UNKNOWN = TextKey.of("mutation.gui.unknown", "Unknown");
  public static final TextKey GUI_STATE_READY = TextKey.of("mutation.gui.state_ready", "Ready");
  public static final TextKey GUI_STATE_ACTIVE = TextKey.of("mutation.gui.state_active", "Active");
  public static final TextKey GUI_STATE_INACTIVE = TextKey.of("mutation.gui.state_inactive", "Inactive");
  public static final TextKey GUI_STATE_LOCKED = TextKey.of("mutation.gui.state_locked", "Locked");
  public static final TextKey GUI_STATE_OFF = TextKey.of("mutation.gui.state_off", "Off");
  public static final TextKey GUI_STATE_UNAVAILABLE = TextKey.of("mutation.gui.state_unavailable", "Unavailable");
  public static final TextKey GUI_STATE_BLOCKED = TextKey.of("mutation.gui.state_blocked", "Blocked");
  public static final TextKey TROPHY_CLEAR_HINT = TextKey.of("mutation.runtime.trophy_clear_hint", "Right-click the crafting table again with an empty hand to clear your prepared trophy.");
  public static final TextKey TROPHY_CLEARED = TextKey.of("mutation.runtime.trophy_cleared", "Your prepared trophy has been cleared.");
  public static final TextKey RETURN_ECHO_NAME = TextKey.of("mutation.runtime.return_echo_name", "Return Echo");
  public static final TextKey MODEL_NO_SELECTION = TextKey.of("mutation.model.no_selection", "No Mutation selected");
  public static final TextKey MODEL_SAVED_UNAVAILABLE = TextKey.of("mutation.model.saved_unavailable", "This saved Mutation is not available");
  public static final TextKey MODEL_SLOT_LOCKED = TextKey.of("mutation.model.slot_locked", "The needed slot is locked");
  public static final TextKey MODEL_READY = TextKey.of("mutation.model.ready", "Ready to use");
  public static final TextKey MODEL_ACTIVE = TextKey.of("mutation.model.active", "Active");
  public static final TextKey MODEL_INACTIVE = TextKey.of("mutation.model.inactive", "Equipped but inactive");
  public static final TextKey MODEL_DISABLED = TextKey.of("mutation.model.disabled", "Turned off by the server");
  public static final TextKey MODEL_RESTRICTED = TextKey.of("mutation.model.restricted", "Not available here");
  public static final TextKey MODEL_CONFLICT = TextKey.of("mutation.model.conflict", "Blocked by the other Mutation");
  public static final TextKey MODEL_EMPTY = TextKey.of("mutation.model.empty", "Empty");

  private static final List<MessageKey> KEYS = List.of(
      RUNTIME_UNAVAILABLE, FEATURE_OFF, TYPE_OFF, MISSING_PERMISSION, DUPLICATE_SLOTS, DUPLICATE_OCCUPANCY,
      CONFLICT_OTHER, CONFLICTS_WITH, SLOT_LOCKED_CURRENT_LEVEL, FIRST_SLOT_UNLOCKS, WORLD_UNAVAILABLE,
      ACTIVE_PERFECT, ACTIVE_BURDEN, READY_TO_USE, REQUIREMENTS_UNAVAILABLE, LEARN_BOTH_DOMAINS,
      LEARN_DOMAIN_LEVEL, READY, INVALID_SELECTION, PLAYER_DATA_UNAVAILABLE, SLOT_CHANGED_CONFIRM,
      LOADOUT_CHANGED_CONFIRM, ALREADY_EQUIPPED, EQUIPPED, INVALID_SLOT, SLOT_ALREADY_EMPTY, CLEARED_SLOT,
      CHANGES_OFF, BOOKSHELF_REQUIRED, SLOT_UNLOCKS, PERMANENT_CHOICES, SLOT_COOLDOWN, COMBAT_COOLDOWN,
      MUTATION_DISABLED, WORLD_DISABLED, LOGIN_REQUIREMENTS, EQUIPPED_OFFLINE, FIRST_SLOT_LOCKED, TOGGLE_USAGE,
      COOPERATIVE_ENABLED, COOPERATIVE_DISABLED, COOPERATIVE_ENABLED_OFF, COOPERATIVE_DISABLED_OFF,
      CHOICE_SAVED_OFF, DISCOVERED, UNDISCOVERED, COOLDOWNS_CLEARED, STATE_REFRESHED, SLOT_RANGE,
      OVERRIDE_USAGE, SLOT_NORMAL, SLOT_FORCED_UNLOCKED, SLOT_FORCED_LOCKED, DATA_CLEARED, PERFECT_NORMAL,
      PERFECT_FORCED_ON, PERFECT_FORCED_OFF, MUTATIONS_UNAVAILABLE, RELOAD_FAILED, RELOAD_ENABLED,
      RELOAD_DISABLED, UNKNOWN_MUTATION, SNAPSHOT_TITLE, FEATURE_ENABLED, FEATURE_DISABLED, PERFECT_ACTIVE,
      PERFECT_INACTIVE, COOPERATIVE_STATUS_ENABLED, COOPERATIVE_STATUS_DISABLED, STORED_RESOURCES, LINKED_GEAR,
      YES, NO, STATE_READY, STATE_ACTIVE, STATE_INACTIVE, STATE_LOCKED, STATE_OFF, STATE_UNAVAILABLE,
      STATE_BLOCKED, SLOT_LOCKED, SLOT_EMPTY, SLOT_UNAVAILABLE, SLOT_VALUE, SLOT_VALUE_REASON, RESULT_UPDATED,
      RESULT_NOT_CHANGED, REMAINING_COOLDOWN, SLOT_UNLOCKED_TITLE, SLOT_UNLOCKED_SUBTITLE, SECOND_SLOT_TITLE,
      SECOND_SLOT_SUBTITLE, PERFECT_TITLE, PERFECT_SUBTITLE, PERFECT_LOST, TROPHY_CLEAR_HINT, TROPHY_CLEARED,
      RETURN_ECHO_NAME, MODEL_NO_SELECTION, MODEL_SAVED_UNAVAILABLE, MODEL_SLOT_LOCKED, MODEL_READY,
      MODEL_ACTIVE, MODEL_INACTIVE, MODEL_DISABLED, MODEL_RESTRICTED, MODEL_CONFLICT, MODEL_EMPTY
  );
  private static final List<MessageKey> GUI_KEYS = List.of(
      GUI_DISABLED, GUI_TITLE, GUI_PERFECT, GUI_PERFECT_ACTIVE, GUI_PERFECT_LOCKED, GUI_COOPERATIVE_ON,
      GUI_COOPERATIVE_OFF, GUI_COOPERATIVE_ALLOW, GUI_COOPERATIVE_OPT_OUT, GUI_COOPERATIVE_OPT_IN,
      GUI_CHANGES_AVAILABLE, GUI_VIEW_ONLY, GUI_MAY_CHANGE, GUI_VISIT_BOOKSHELF, GUI_UNAVAILABLE_DISCOVERIES,
      GUI_SAVED_NOT_INSTALLED, GUI_SLOT_VALUE, GUI_SLOT_LOCKED, GUI_RIGHT_CLICK_CLEAR, GUI_SKILL_GROUPS,
      GUI_DISCOVERED, GUI_UNDISCOVERED, GUI_EQUIPPED_SLOT, GUI_CLICK_INSPECT, GUI_REQUIRES, GUI_BENEFIT,
      GUI_BURDEN, GUI_PERFECT_RESULT, GUI_TELL, GUI_CONTROL, GUI_PVP, GUI_NO_PVP, GUI_STATUS,
      GUI_MATCHING_NONE, GUI_MATCHING, GUI_SLOT_LOCKED_NAME, GUI_SLOT_VIEW_ONLY, GUI_BOOKSHELF_BEFORE_CHANGE,
      GUI_EQUIPPED_IN_SLOT, GUI_ALREADY_EQUIPPED_SLOT, GUI_CONFLICTS_WITH, GUI_CLEAR_OTHER_FIRST,
      GUI_UNAVAILABLE_SLOT, GUI_EQUIP_SLOT, GUI_CURRENT, GUI_CLICK_PREVIEW, GUI_CONFIRM_SLOT, GUI_REPLACING,
      GUI_OTHER_SLOT, GUI_WITH_OTHER, GUI_SLOT_COOLDOWN, GUI_CONFIRM_CHANGE, GUI_CONFIRM_CHANGE_TITLE,
      GUI_CLEAR_SLOT, GUI_UNEQUIP, GUI_NORMAL_COOLDOWN, GUI_CONFIRM_CLEAR, GUI_LINK_ARMOR,
      GUI_LINK_ARMOR_LORE, GUI_UNLINK_ARMOR, GUI_UNLINK_ARMOR_LORE, GUI_REVIEW_CONFIRM,
      GUI_UNLINK_MASTERWORK, GUI_UNLINK_MASTERWORK_LORE, GUI_REPLACEMENT_COOLDOWN_BEGINS, GUI_BIND_HELD_TOOL,
      GUI_DEEPBLOOD_BIND_LORE, GUI_DEEPBLOOD_REPLACE_LORE, GUI_BIND_COOLING, GUI_MASTERWORK_BIND_LORE,
      GUI_REPLACEMENT_AVAILABLE, GUI_EQUIPMENT_CHANGED, GUI_UNLINK_TEMPERBOUND, GUI_TEMPERBOUND_UNLINKED,
      GUI_NO_REPAIR_RETURN, GUI_UNLINK_MASTERWORK_QUESTION, GUI_MASTERWORK_UNLINKED, GUI_REPLACEMENT_COOLDOWN,
      GUI_CONFIRM_EQUIPMENT_TITLE, GUI_OPTION_UNAVAILABLE, GUI_EQUIPMENT_CHANGED_REVIEW,
      GUI_CONTROLS_UNAVAILABLE, EQUIPMENT_TEMPERBOUND_SUCCESS, EQUIPMENT_TEMPERBOUND_UNLINKED,
      EQUIPMENT_MASTERWORK_SUCCESS, EQUIPMENT_DEEPBLOOD_SUCCESS, EQUIPMENT_MASTERWORK_UNLINKED_DELAY,
      EQUIPMENT_MASTERWORK_UNLINKED, EQUIPMENT_REPLACEMENT_DELAY, EQUIPMENT_TEMPERBOUND_REQUIREMENT,
      EQUIPMENT_TEMPERBOUND_FAILURE, EQUIPMENT_MASTERWORK_REQUIREMENT, EQUIPMENT_MASTERWORK_FAILURE,
      EQUIPMENT_DEEPBLOOD_REQUIREMENT, GUI_PAGE_COUNT, GUI_NO_OTHER_SELECTED, GUI_SAME_NOT_ALLOWED,
      GUI_COMPATIBLE_WITH, GUI_UNKNOWN, GUI_STATE_READY, GUI_STATE_ACTIVE, GUI_STATE_INACTIVE,
      GUI_STATE_LOCKED, GUI_STATE_OFF, GUI_STATE_UNAVAILABLE, GUI_STATE_BLOCKED
  );

  private MutationMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.addAll(KEYS);
    builder.addAll(GUI_KEYS);
  }
}
