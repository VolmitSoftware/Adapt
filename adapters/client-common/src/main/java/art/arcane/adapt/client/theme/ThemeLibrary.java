package art.arcane.adapt.client.theme;

import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.client.ui.UiSprites;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The 24 skills' page themes, read from the resource packs ({@code adapt:themes/<skill>.json} with its atlas
 * {@code adapt:textures/gui/theme/<skill>.png}) the first time each is asked for and again after the packs reload. A skill
 * with no theme file, or a broken one, has no theme and keeps the plain book.
 */
public final class ThemeLibrary {
    private final Map<String, Loaded> kits = new HashMap<>();
    private final Map<String, String> dataIds = new HashMap<>();
    private final Map<String, PageTheme> themes = new HashMap<>();
    /** Each skill id's parents; a base skill is its own lead and partner. */
    private final Map<String, SkillMutation> parents = new HashMap<>();

    private record Loaded(byte[] source, ThemeKit kit) {
    }

    /** The theme of base skill {@code skillId}, or null when it has none. */
    public ThemeKit kit(UiSprites sprites, String skillId) {
        if (skillId == null) {
            return null;
        }
        byte[] source = sprites.data(dataIds.computeIfAbsent(skillId, (String id) -> ThemeKit.DATA_PREFIX + id + ".json"));
        if (source == null) {
            return null;
        }
        Loaded loaded = kits.get(skillId);
        if (loaded == null || loaded.source() != source) {
            ThemeKit kit;
            try {
                kit = ThemeKit.parse(new String(source, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException broken) {
                kit = null;
            }
            loaded = new Loaded(source, kit != null && sprites.exists(kit.texture().id()) ? kit : null);
            kits.put(skillId, loaded);
        }
        return loaded.kit();
    }

    /**
     * The theme of skill {@code skillId}: its own kit, or for a mutated skill ({@code mutated-<lead>-<partner>}) the
     * lead's and the partner's. Null when the skill, or either parent, has no theme.
     */
    public PageTheme theme(UiSprites sprites, String skillId) {
        if (skillId == null) {
            return null;
        }
        SkillMutation mutation = parents.computeIfAbsent(skillId, (String id) -> {
            SkillMutation parsed = SkillMutation.parse(id);
            return parsed == null ? new SkillMutation(id, id) : parsed;
        });
        boolean mutated = !mutation.lead().equals(mutation.partner());
        ThemeKit lead = kit(sprites, mutation.lead());
        ThemeKit partner = mutated ? kit(sprites, mutation.partner()) : null;
        if (lead == null || mutated && partner == null) {
            return null;
        }
        PageTheme known = themes.get(skillId);
        if (known == null || known.lead() != lead || known.partner() != partner) {
            known = new PageTheme(lead, partner);
            themes.put(skillId, known);
        }
        return known;
    }
}
