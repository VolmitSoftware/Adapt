package art.arcane.adapt.client.theme;

import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.client.ui.UiSprites;
import art.arcane.adapt.platform.value.Id;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skills' page themes, read from the resource packs ({@code <namespace>:themes/<path>.json} with its atlas
 * {@code <namespace>:textures/gui/theme/<path>.png}, from the skill id's own namespace and path) the first time each is
 * asked for and again after the packs reload. A skill with no theme file, or a broken one, has no theme and keeps the
 * plain book.
 */
public final class ThemeLibrary {
    private final Map<Id, Loaded> kits = new HashMap<>();
    private final Map<Id, String> dataIds = new HashMap<>();
    private final Map<String, PageTheme> themes = new HashMap<>();
    /** Each skill key's parents: a base skill's own id, or a mutated skill's lead and partner; empty for no skill. */
    private final Map<String, List<Id>> parents = new HashMap<>();

    private record Loaded(byte[] source, ThemeKit kit) {
    }

    /** The theme of base skill {@code skill}, or null when it has none. */
    public ThemeKit kit(UiSprites sprites, Id skill) {
        if (skill == null) {
            return null;
        }
        byte[] source = sprites.data(dataIds.computeIfAbsent(skill, (Id id) -> ThemeKit.dataId(id.namespace(), id.path())));
        if (source == null) {
            return null;
        }
        Loaded loaded = kits.get(skill);
        if (loaded == null || loaded.source() != source) {
            ThemeKit kit;
            try {
                kit = ThemeKit.parse(skill.namespace(), new String(source, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException broken) {
                kit = null;
            }
            loaded = new Loaded(source, kit != null && sprites.exists(kit.texture().id()) ? kit : null);
            kits.put(skill, loaded);
        }
        return loaded.kit();
    }

    /**
     * The theme of a skill by its line key as the server sends it: an id such as {@code adapt:swords} or
     * {@code alchemist:alchemy} (a bare path is in {@code adapt}), or a mutated skill's {@code <lead id>+<partner id>},
     * whose theme is the lead's and the partner's, each from its own namespace. Null when the skill, or either parent,
     * has no theme.
     */
    public PageTheme theme(UiSprites sprites, String skillKey) {
        if (skillKey == null) {
            return null;
        }
        List<Id> ids = parents.computeIfAbsent(skillKey, ThemeLibrary::parents);
        if (ids.isEmpty()) {
            return null;
        }
        boolean mutated = ids.size() == 2;
        ThemeKit lead = kit(sprites, ids.get(0));
        ThemeKit partner = mutated ? kit(sprites, ids.get(1)) : null;
        if (lead == null || mutated && partner == null) {
            return null;
        }
        PageTheme known = themes.get(skillKey);
        if (known == null || known.lead() != lead || known.partner() != partner) {
            known = new PageTheme(lead, partner);
            themes.put(skillKey, known);
        }
        return known;
    }

    private static List<Id> parents(String skillKey) {
        SkillPair mutation = PairKeys.parse(skillKey);
        if (mutation != null) {
            return List.of(mutation.lead(), mutation.partner());
        }
        Id id = Id.tryParseAdapt(skillKey);
        return id == null ? List.of() : List.of(id);
    }
}
