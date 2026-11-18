package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Map;

public final class ConfigMessages {
  public static final TextKey NO_PERMISSION = TextKey.of("config.gui.no_permission", "You do not have permission to use the config menu.");
  public static final TextKey UNABLE_OPEN_SECTION = TextKey.of("config.gui.unable_open_section", "Unable to open config section: &f{path}");
  public static final TextKey FAILED_SET_VALUE = TextKey.of("config.gui.failed_set_value", "Failed to set config value at &f{path}");
  public static final TextKey FAILED_PERSIST_UPDATE = TextKey.of("config.gui.failed_persist_update", "Failed to persist config update: &f{error}");
  public static final TextKey RELOAD_REVERTED = TextKey.of("config.gui.reload_reverted", "Config reload failed. Reverted file changes.");
  public static final TextKey UPDATED_VALUE = TextKey.of("config.gui.updated_value", "Updated &f{path}&7 [{before}&7 -> {after}&7]");
  public static final TextKey TYPE_BOOLEAN = TextKey.of("config.type.boolean", "Boolean");
  public static final TextKey TYPE_NUMBER = TextKey.of("config.type.number", "Number");
  public static final TextKey TYPE_TEXT = TextKey.of("config.type.text", "Text");
  public static final TextKey TYPE_ENUM = TextKey.of("config.type.enum", "Enum");
  public static final TextKey TYPE_SECTION = TextKey.of("config.type.section", "Section");
  public static final TextKey TYPE_MAP = TextKey.of("config.type.map", "Map");
  public static final TextKey TYPE_LIST = TextKey.of("config.type.list", "List");
  public static final TextKey TYPE_UNSUPPORTED = TextKey.of("config.type.unsupported", "Unsupported");
  public static final TextKey TYPE_UNKNOWN = TextKey.of("config.type.unknown", "unknown");
  public static final TextKey VALUE = TextKey.of("config.gui.value", "Value: &b{value}");
  public static final TextKey PATH = TextKey.of("config.gui.path", "Path: {path}");
  public static final PluralKey CONTAINS_SETTINGS = PluralKey.of("config.gui.contains_settings", "count", Map.of(
      "one", "Contains {count} setting",
      "other", "Contains {count} settings"
  ));
  public static final TextKey CATEGORY = TextKey.of("config.gui.category", "Category: {category}");
  public static final TextKey STATE_ENABLED = TextKey.of("config.gui.state_enabled", "State: Enabled");
  public static final TextKey STATE_DISABLED = TextKey.of("config.gui.state_disabled", "State: Disabled");
  public static final TextKey LEFT_CLICK_TOGGLE = TextKey.of("config.gui.left_click_toggle", "Left click: toggle");
  public static final TextKey LEFT_CLICK_NEXT_VALUE = TextKey.of("config.gui.left_click_next_value", "Left click: next value");
  public static final TextKey RIGHT_CLICK_PREVIOUS_VALUE = TextKey.of("config.gui.right_click_previous_value", "Right click: previous value");
  public static final TextKey LEFT_CLICK_EDIT_CHAT = TextKey.of("config.gui.left_click_edit_chat", "Left click: edit in chat");
  public static final TextKey INPUT_UNAVAILABLE = TextKey.of("config.gui.input_unavailable", "Config input service is unavailable.");
  public static final TextKey LEFT_CLICK_OPEN_SECTION = TextKey.of("config.gui.left_click_open_section", "Left click: open section");
  public static final TextKey READ_ONLY = TextKey.of("config.gui.read_only", "Read-only");
  public static final TextKey UNSUPPORTED_TYPE = TextKey.of("config.gui.unsupported_type", "Unsupported type");
  public static final TextKey CATEGORY_UI = TextKey.of("config.category.ui", "UI");
  public static final TextKey CATEGORY_AUDIO = TextKey.of("config.category.audio", "Audio");
  public static final TextKey CATEGORY_LOCALIZATION = TextKey.of("config.category.localization", "Localization");
  public static final TextKey CATEGORY_STORAGE = TextKey.of("config.category.storage", "Storage");
  public static final TextKey CATEGORY_PROGRESSION = TextKey.of("config.category.progression", "Progression");
  public static final TextKey CATEGORY_WORLD = TextKey.of("config.category.world", "World");
  public static final TextKey CATEGORY_PERFORMANCE = TextKey.of("config.category.performance", "Performance");
  public static final TextKey CATEGORY_ACCESS = TextKey.of("config.category.access", "Access");
  public static final TextKey CATEGORY_DEBUG = TextKey.of("config.category.debug", "Debug");
  public static final TextKey CATEGORY_GENERAL = TextKey.of("config.category.general", "General");
  public static final TextKey NO_SETTINGS = TextKey.of("config.gui.no_settings", "No settings in this section");
  public static final TextKey CONFIGURE_PATH = TextKey.of("config.gui.configure_path", "Configure: &f{path}");
  public static final PluralKey OPEN_SETTINGS = PluralKey.of("config.gui.open_settings", "count", Map.of(
      "one", "Open {count} setting",
      "other", "Open {count} settings"
  ));
  public static final TextKey GENERAL_SETTINGS = TextKey.of("config.gui.general_settings", "General Settings");
  public static final PluralKey OPEN_GLOBAL_OPTIONS = PluralKey.of("config.gui.open_global_options", "count", Map.of(
      "one", "Open {count} global option",
      "other", "Open {count} global options"
  ));
  public static final TextKey ADAPTATIONS = TextKey.of("config.gui.adaptations", "Adaptations");
  public static final TextKey CORE = TextKey.of("config.gui.core", "Core");
  public static final TextKey SKILLS = TextKey.of("config.gui.skills", "Skills");
  public static final TextKey CONFIGURE_ADAPTATIONS = TextKey.of("config.gui.configure_adaptations", "Configure adaptation settings");
  public static final TextKey CONFIGURE_CORE = TextKey.of("config.gui.configure_core", "Configure global Adapt settings");
  public static final TextKey CONFIGURE_SKILLS = TextKey.of("config.gui.configure_skills", "Configure skill settings");
  public static final TextKey CONFIGURE_ADAPT = TextKey.of("config.gui.configure_adapt", "Configure Adapt");
  public static final TextKey CONFIGURE_TARGET = TextKey.of("config.gui.configure_target", "Configure {target}");
  public static final TextKey ALL_ADAPTATIONS = TextKey.of("config.gui.all_adaptations", "All Adaptations (A-Z)");
  public static final TextKey BROWSE_ALL_ADAPTATIONS = TextKey.of("config.gui.browse_all_adaptations", "Browse every adaptation alphabetically");
  public static final PluralKey BROWSE_ADAPTATIONS = PluralKey.of("config.gui.browse_adaptations", "count", Map.of(
      "one", "Browse {count} adaptation",
      "other", "Browse {count} adaptations"
  ));
  public static final TextKey UNKNOWN_SKILL = TextKey.of("config.gui.unknown_skill", "Unknown skill for adaptation config: &f{skill}");
  public static final TextKey OPEN_TARGET = TextKey.of("config.gui.open_target", "Open {target}");
  public static final TextKey SKILL_TARGET = TextKey.of("config.gui.skill_target", "Skill: {skill}");
  public static final TextKey NO_ENTRIES = TextKey.of("config.gui.no_entries", "No entries");
  public static final TextKey OVERVIEW = TextKey.of("config.gui.overview", "Overview");
  public static final TextKey HELP = TextKey.of("config.gui.help", "Help");
  public static final TextKey DIRECTORY = TextKey.of("config.gui.directory", "Directory");
  public static final TextKey NAVIGATION = TextKey.of("config.gui.navigation", "Navigation");
  public static final TextKey ROOT = TextKey.of("config.gui.root", "root");
  public static final TextKey SECTIONS_COUNT = TextKey.of("config.gui.sections_count", "Sections: {count}");
  public static final TextKey EDITABLE_COUNT = TextKey.of("config.gui.editable_count", "Editable: {count}");
  public static final TextKey ENTRIES_COUNT = TextKey.of("config.gui.entries_count", "Entries: {count}");
  public static final TextKey PAGE = TextKey.of("config.gui.page", "Page: {page}/{pages}");
  public static final TextKey HELP_LEFT_CLICK = TextKey.of("config.gui.help_left_click", "LMB: open/edit/toggle");
  public static final TextKey HELP_RIGHT_CLICK = TextKey.of("config.gui.help_right_click", "RMB: enum prev / page jump");
  public static final TextKey HELP_ESCAPE = TextKey.of("config.gui.help_escape", "ESC: back to parent page");
  public static final TextKey NAV_OPEN_SECTION = TextKey.of("config.gui.nav_open_section", "LMB: open section");
  public static final TextKey NAV_JUMP_PAGES = TextKey.of("config.gui.nav_jump_pages", "RMB on arrows: jump pages");
  public static final TextKey ENTER_VALUE = TextKey.of("config.input.enter_value", "&bEnter value for &f{label}");
  public static final TextKey INPUT_PATH = TextKey.of("config.input.path", "&bPath: &f{path}");
  public static final TextKey EXPECTED_TYPE = TextKey.of("config.input.expected_type", "&bExpected type: &f{type}");
  public static final TextKey TYPE_CANCEL = TextKey.of("config.input.type_cancel", "&7Type &fcancel &7to abort.");
  public static final TextKey INPUT_TIMED_OUT = TextKey.of("config.input.timed_out", "Config input timed out.");
  public static final TextKey EDIT_CANCELLED = TextKey.of("config.input.edit_cancelled", "Config edit cancelled.");
  public static final TextKey TRY_AGAIN_OR_CANCEL = TextKey.of("config.input.try_again_or_cancel", "&7Try again or type &fcancel&7.");
  public static final TextKey UNKNOWN_TARGET_TYPE = TextKey.of("config.input.unknown_target_type", "Unknown target type.");
  public static final TextKey EXPECTED_ONE_CHARACTER = TextKey.of("config.input.expected_one_character", "Expected exactly one character.");
  public static final TextKey EXPECTED_BOOLEAN = TextKey.of("config.input.expected_boolean", "Expected boolean value: true/false.");
  public static final TextKey EXPECTED_ONE_OF = TextKey.of("config.input.expected_one_of", "Expected one of: {values}");
  public static final TextKey EXPECTED_FINITE_NUMBER = TextKey.of("config.input.expected_finite_number", "Expected a finite number.");
  public static final TextKey INVALID_VALUE_FOR_TYPE = TextKey.of("config.input.invalid_value_for_type", "Invalid value for type {type}.");
  public static final TextKey UNSUPPORTED_INPUT_TYPE = TextKey.of("config.input.unsupported_type", "Unsupported type: {type}.");
  public static final TextKey INVALID_VALUE = TextKey.of("config.input.invalid_value", "Invalid value.");
  public static final TextKey UNNAMED = TextKey.of("config.summary.unnamed", "Unnamed");
  public static final TextKey NULL = TextKey.of("config.summary.null", "null");
  public static final TextKey MAP_SIZE = TextKey.of("config.summary.map", "map({count})");
  public static final TextKey LIST_SIZE = TextKey.of("config.summary.list", "list({count})");
  public static final TextKey ARRAY = TextKey.of("config.summary.array", "array");

  private ConfigMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(NO_PERMISSION);
    builder.add(UNABLE_OPEN_SECTION);
    builder.add(FAILED_SET_VALUE);
    builder.add(FAILED_PERSIST_UPDATE);
    builder.add(RELOAD_REVERTED);
    builder.add(UPDATED_VALUE);
    builder.add(TYPE_BOOLEAN);
    builder.add(TYPE_NUMBER);
    builder.add(TYPE_TEXT);
    builder.add(TYPE_ENUM);
    builder.add(TYPE_SECTION);
    builder.add(TYPE_MAP);
    builder.add(TYPE_LIST);
    builder.add(TYPE_UNSUPPORTED);
    builder.add(TYPE_UNKNOWN);
    builder.add(VALUE);
    builder.add(PATH);
    builder.add(CONTAINS_SETTINGS);
    builder.add(CATEGORY);
    builder.add(STATE_ENABLED);
    builder.add(STATE_DISABLED);
    builder.add(LEFT_CLICK_TOGGLE);
    builder.add(LEFT_CLICK_NEXT_VALUE);
    builder.add(RIGHT_CLICK_PREVIOUS_VALUE);
    builder.add(LEFT_CLICK_EDIT_CHAT);
    builder.add(INPUT_UNAVAILABLE);
    builder.add(LEFT_CLICK_OPEN_SECTION);
    builder.add(READ_ONLY);
    builder.add(UNSUPPORTED_TYPE);
    builder.add(CATEGORY_UI);
    builder.add(CATEGORY_AUDIO);
    builder.add(CATEGORY_LOCALIZATION);
    builder.add(CATEGORY_STORAGE);
    builder.add(CATEGORY_PROGRESSION);
    builder.add(CATEGORY_WORLD);
    builder.add(CATEGORY_PERFORMANCE);
    builder.add(CATEGORY_ACCESS);
    builder.add(CATEGORY_DEBUG);
    builder.add(CATEGORY_GENERAL);
    builder.add(NO_SETTINGS);
    builder.add(CONFIGURE_PATH);
    builder.add(OPEN_SETTINGS);
    builder.add(GENERAL_SETTINGS);
    builder.add(OPEN_GLOBAL_OPTIONS);
    builder.add(ADAPTATIONS);
    builder.add(CORE);
    builder.add(SKILLS);
    builder.add(CONFIGURE_ADAPTATIONS);
    builder.add(CONFIGURE_CORE);
    builder.add(CONFIGURE_SKILLS);
    builder.add(CONFIGURE_ADAPT);
    builder.add(CONFIGURE_TARGET);
    builder.add(ALL_ADAPTATIONS);
    builder.add(BROWSE_ALL_ADAPTATIONS);
    builder.add(BROWSE_ADAPTATIONS);
    builder.add(UNKNOWN_SKILL);
    builder.add(OPEN_TARGET);
    builder.add(SKILL_TARGET);
    builder.add(NO_ENTRIES);
    builder.add(OVERVIEW);
    builder.add(HELP);
    builder.add(DIRECTORY);
    builder.add(NAVIGATION);
    builder.add(ROOT);
    builder.add(SECTIONS_COUNT);
    builder.add(EDITABLE_COUNT);
    builder.add(ENTRIES_COUNT);
    builder.add(PAGE);
    builder.add(HELP_LEFT_CLICK);
    builder.add(HELP_RIGHT_CLICK);
    builder.add(HELP_ESCAPE);
    builder.add(NAV_OPEN_SECTION);
    builder.add(NAV_JUMP_PAGES);
    builder.add(ENTER_VALUE);
    builder.add(INPUT_PATH);
    builder.add(EXPECTED_TYPE);
    builder.add(TYPE_CANCEL);
    builder.add(INPUT_TIMED_OUT);
    builder.add(EDIT_CANCELLED);
    builder.add(TRY_AGAIN_OR_CANCEL);
    builder.add(UNKNOWN_TARGET_TYPE);
    builder.add(EXPECTED_ONE_CHARACTER);
    builder.add(EXPECTED_BOOLEAN);
    builder.add(EXPECTED_ONE_OF);
    builder.add(EXPECTED_FINITE_NUMBER);
    builder.add(INVALID_VALUE_FOR_TYPE);
    builder.add(UNSUPPORTED_INPUT_TYPE);
    builder.add(INVALID_VALUE);
    builder.add(UNNAMED);
    builder.add(NULL);
    builder.add(MAP_SIZE);
    builder.add(LIST_SIZE);
    builder.add(ARRAY);
  }
}
