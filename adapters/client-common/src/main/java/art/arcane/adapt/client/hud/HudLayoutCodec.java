package art.arcane.adapt.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Reads and writes {@link HudLayout} as JSON. Reading never fails: unknown fields are ignored, missing or invalid
 * values take the widget's defaults, unknown widget kinds are dropped and base widgets missing from a profile (for
 * example ones added in a later version) are filled in. Older layouts are migrated by {@link HudLayoutMigration}: a
 * widget the player never moved or changed takes the new default.
 */
public final class HudLayoutCodec {
    public static final String EXPORT_PREFIX = "adapt-hud1:";
    private static final int MAX_IMPORT_BYTES = 64 * 1024;
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private HudLayoutCodec() {
    }

    public static String write(HudLayout layout) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", HudLayout.SCHEMA);
        root.addProperty("active", layout.active());
        root.addProperty("reducedMotion", layout.reducedMotion());
        JsonArray profiles = new JsonArray();
        for (HudProfile profile : layout.profiles()) {
            profiles.add(profile(profile));
        }
        root.add("profiles", profiles);
        return PRETTY.toJson(root);
    }

    public static HudLayout read(String json) {
        JsonObject root = object(parse(json));
        if (root == null) {
            return HudDefaults.layout();
        }
        List<HudProfile> profiles = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int schema = (int) number(root.get("schema"), 1);
        JsonArray array = array(root.get("profiles"));
        if (array != null) {
            for (JsonElement element : array) {
                HudProfile profile = profile(object(element), schema);
                if (profile != null && names.add(profile.name())) {
                    profiles.add(profile);
                }
            }
        }
        if (profiles.isEmpty()) {
            return HudDefaults.layout();
        }
        String active = string(root.get("active"), profiles.get(0).name());
        if (!names.contains(active)) {
            active = profiles.get(0).name();
        }
        return new HudLayout(active, bool(root.get("reducedMotion"), false), profiles);
    }

    /** A single-line code holding one profile, for the clipboard. */
    public static String export(HudProfile profile) {
        byte[] json = COMPACT.toJson(profile(profile)).getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(json);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(json.length);
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return EXPORT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    /** The profile in an {@link #export} code, or empty when the text is not one. */
    public static Optional<HudProfile> importCode(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith(EXPORT_PREFIX)) {
            return Optional.empty();
        }
        try {
            byte[] packed = Base64.getUrlDecoder().decode(trimmed.substring(EXPORT_PREFIX.length()));
            Inflater inflater = new Inflater();
            inflater.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length * 4);
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                out.write(buffer, 0, count);
                if (out.size() > MAX_IMPORT_BYTES) {
                    inflater.end();
                    return Optional.empty();
                }
            }
            boolean complete = inflater.finished();
            inflater.end();
            if (!complete) {
                return Optional.empty();
            }
            return Optional.ofNullable(profile(object(parse(out.toString(StandardCharsets.UTF_8))), 1));
        } catch (IllegalArgumentException | DataFormatException exception) {
            return Optional.empty();
        }
    }

    private static JsonObject profile(HudProfile profile) {
        JsonObject object = new JsonObject();
        object.addProperty("schema", HudLayout.SCHEMA);
        object.addProperty("name", profile.name());
        object.addProperty("scale", profile.scale());
        object.addProperty("autoFit", profile.autoFit());
        object.addProperty("protect", profile.protect());
        JsonArray widgets = new JsonArray();
        for (HudWidgetConfig widget : profile.widgets()) {
            widgets.add(widget(widget));
        }
        object.add("widgets", widgets);
        return object;
    }

    private static HudProfile profile(JsonObject object, int layoutSchema) {
        if (object == null) {
            return null;
        }
        int schema = (int) number(object.get("schema"), layoutSchema);
        String name = sanitizeName(string(object.get("name"), ""));
        if (name.isEmpty()) {
            return null;
        }
        List<HudWidgetConfig> widgets = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        JsonArray array = array(object.get("widgets"));
        if (array != null) {
            for (JsonElement element : array) {
                HudWidgetConfig widget = widget(object(element), schema);
                if (widget != null && ids.add(widget.id())) {
                    widgets.add(widget);
                }
            }
        }
        for (HudWidgetKind kind : HudWidgetKind.values()) {
            if (!ids.contains(kind.id())) {
                widgets.add(HudDefaults.widget(kind));
            }
        }
        return new HudProfile(name, number(object.get("scale"), 1.0), bool(object.get("autoFit"), true),
                number(object.get("protect"), HudProfile.DEFAULT_PROTECT), widgets);
    }

    /** Trims, drops control characters and caps the length; empty when nothing usable is left. */
    public static String sanitizeName(String name) {
        StringBuilder builder = new StringBuilder(Math.min(name.length(), HudProfile.MAX_NAME_LENGTH));
        for (int index = 0; index < name.length() && builder.length() < HudProfile.MAX_NAME_LENGTH; index++) {
            char character = name.charAt(index);
            if (character == '\u00a7') {
                index++;
            } else if (character >= ' ' && character != 0x7f) {
                builder.append(character);
            }
        }
        return builder.toString().trim();
    }

    private static JsonObject widget(HudWidgetConfig widget) {
        JsonObject object = new JsonObject();
        HudPlacement placement = widget.placement();
        object.addProperty("id", widget.id());
        object.addProperty("kind", widget.kind().id());
        object.addProperty("anchor", placement.anchor().id());
        object.addProperty("x", placement.offsetX());
        object.addProperty("y", placement.offsetY());
        object.addProperty("scale", placement.scale());
        object.addProperty("opacity", placement.opacity());
        object.addProperty("orientation", placement.orientation().id());
        object.addProperty("visible", placement.visible());
        object.add("options", options(widget.options()));
        return object;
    }

    private static HudWidgetConfig widget(JsonObject object, int schema) {
        if (object == null) {
            return null;
        }
        HudWidgetKind kind = HudWidgetKind.parse(string(object.get("kind"), null));
        String id = string(object.get("id"), null);
        if (kind == null || id == null || !validId(id, kind)) {
            return null;
        }
        HudPlacement fallback = HudDefaults.placement(kind);
        HudPlacement placement = new HudPlacement(
                HudAnchor.parse(string(object.get("anchor"), null), fallback.anchor()),
                number(object.get("x"), fallback.offsetX()),
                number(object.get("y"), fallback.offsetY()),
                number(object.get("scale"), fallback.scale()),
                number(object.get("opacity"), fallback.opacity()),
                HudOrientation.parse(string(object.get("orientation"), null), fallback.orientation()),
                bool(object.get("visible"), fallback.visible()));
        JsonObject options = object(object.get("options"));
        if (schema < HudLayout.SCHEMA) {
            return HudLayoutMigration.migrate(schema, id, kind, placement, options == null ? new JsonObject() : options);
        }
        return new HudWidgetConfig(id, kind, placement, options(kind, options));
    }

    private static boolean validId(String id, HudWidgetKind kind) {
        if (id.equals(kind.id())) {
            return true;
        }
        if (!kind.splittable() || !id.startsWith(kind.id() + "_")) {
            return false;
        }
        String suffix = id.substring(kind.id().length() + 1);
        if (suffix.isEmpty() || suffix.length() > 4) {
            return false;
        }
        for (int index = 0; index < suffix.length(); index++) {
            if (!Character.isDigit(suffix.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static JsonObject options(HudOptions options) {
        JsonObject object = new JsonObject();
        switch (options) {
            case HudOptions.Skills skills -> {
                JsonArray ids = new JsonArray();
                for (String skill : skills.pinned()) {
                    ids.add(skill);
                }
                object.add("pinned", ids);
                object.addProperty("maxRows", skills.maxRows());
                object.addProperty("lingerMillis", skills.lingerMillis());
                object.addProperty("showNames", skills.showNames());
                object.addProperty("iconSize", skills.iconSize());
            }
            case HudOptions.Resources resources -> {
                object.addProperty("power", resources.power());
                object.addProperty("master", resources.master());
                object.addProperty("wisdom", resources.wisdom());
                object.addProperty("knowledge", resources.knowledge());
            }
            case HudOptions.Abilities abilities -> {
                object.addProperty("arrangement", abilities.arrangement().id());
                object.addProperty("growth", abilities.growth().id());
                object.addProperty("iconSize", abilities.iconSize());
                object.addProperty("minIconSize", abilities.minIconSize());
                object.addProperty("maxShown", abilities.maxShown());
                object.addProperty("maxRows", abilities.maxRows());
                object.addProperty("maxColumns", abilities.maxColumns());
                object.addProperty("sort", abilities.sort().id());
                object.addProperty("cooldowns", abilities.cooldowns());
                object.addProperty("charges", abilities.charges());
                object.addProperty("durations", abilities.durations());
                object.addProperty("toggles", abilities.toggles());
                JsonArray skills = new JsonArray();
                for (String skill : abilities.skills()) {
                    skills.add(skill);
                }
                object.add("skills", skills);
                object.addProperty("collapseGroups", abilities.collapseGroups());
                object.addProperty("footprint", abilities.footprint());
                object.addProperty("showNames", abilities.showNames());
            }
            case HudOptions.Notices notices -> object.addProperty("maxLines", notices.maxLines());
            case HudOptions.Toasts toasts -> {
                object.addProperty("durationMillis", toasts.durationMillis());
                object.addProperty("maxVisible", toasts.maxVisible());
            }
        }
        return object;
    }

    static HudOptions options(HudWidgetKind kind, JsonObject object) {
        HudOptions fallback = HudDefaults.options(kind);
        JsonObject source = object == null ? new JsonObject() : object;
        return switch (fallback) {
            case HudOptions.Skills skills -> new HudOptions.Skills(
                    strings(source.get("pinned"), skills.pinned()),
                    (int) number(source.get("maxRows"), skills.maxRows()),
                    (long) number(source.get("lingerMillis"), skills.lingerMillis()),
                    bool(source.get("showNames"), skills.showNames()),
                    (int) number(source.get("iconSize"), skills.iconSize()));
            case HudOptions.Resources resources -> new HudOptions.Resources(
                    bool(source.get("power"), resources.power()),
                    bool(source.get("master"), resources.master()),
                    bool(source.get("wisdom"), resources.wisdom()),
                    bool(source.get("knowledge"), resources.knowledge()));
            case HudOptions.Abilities abilities -> new HudOptions.Abilities(
                    GlyphArrangement.parse(string(source.get("arrangement"), null), abilities.arrangement()),
                    GlyphGrowth.parse(string(source.get("growth"), null), abilities.growth()),
                    (int) number(source.get("iconSize"), abilities.iconSize()),
                    (int) number(source.get("minIconSize"), abilities.minIconSize()),
                    (int) number(source.get("maxShown"), abilities.maxShown()),
                    (int) number(source.get("maxRows"), abilities.maxRows()),
                    (int) number(source.get("maxColumns"), abilities.maxColumns()),
                    GlyphSort.parse(string(source.get("sort"), null), abilities.sort()),
                    bool(source.get("cooldowns"), abilities.cooldowns()),
                    bool(source.get("charges"), abilities.charges()),
                    bool(source.get("durations"), abilities.durations()),
                    bool(source.get("toggles"), abilities.toggles()),
                    strings(source.get("skills"), abilities.skills()),
                    bool(source.get("collapseGroups"), abilities.collapseGroups()),
                    number(source.get("footprint"), abilities.footprint()),
                    bool(source.get("showNames"), abilities.showNames()));
            case HudOptions.Notices notices -> new HudOptions.Notices((int) number(source.get("maxLines"), notices.maxLines()));
            case HudOptions.Toasts toasts -> new HudOptions.Toasts(
                    (long) number(source.get("durationMillis"), toasts.durationMillis()),
                    (int) number(source.get("maxVisible"), toasts.maxVisible()));
        };
    }

    private static JsonElement parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return JsonParser.parseString(json);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static JsonPrimitive primitive(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsJsonPrimitive() : null;
    }

    static String string(JsonElement element, String fallback) {
        JsonPrimitive primitive = primitive(element);
        return primitive != null && primitive.isString() ? primitive.getAsString() : fallback;
    }

    static double number(JsonElement element, double fallback) {
        JsonPrimitive primitive = primitive(element);
        if (primitive == null || !primitive.isNumber()) {
            return fallback;
        }
        double value = primitive.getAsDouble();
        return Double.isFinite(value) ? value : fallback;
    }

    static boolean bool(JsonElement element, boolean fallback) {
        JsonPrimitive primitive = primitive(element);
        return primitive != null && primitive.isBoolean() ? primitive.getAsBoolean() : fallback;
    }

    static List<String> strings(JsonElement element, List<String> fallback) {
        JsonArray array = array(element);
        if (array == null) {
            return fallback;
        }
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement entry : array) {
            String value = string(entry, null);
            if (value != null && !value.isEmpty() && value.length() <= 128 && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }
}
