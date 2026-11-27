package art.arcane.adapt.gameplay;

import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.PlayerRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class PreferenceFixtures {
    private PreferenceFixtures() {
    }

    public static JsonObject execute(PlayerRef player, String[] args) {
        AdaptPlayer runtime = Objects.requireNonNull(Fixtures.adaptPlayer(player));
        if (!runtime.isRuntimeReady()) throw new IllegalStateException("Player runtime is not ready");
        if (args[2].equals("catalog")) return catalog();
        if (args[2].equals("lock-audit")) return auditLock(runtime);
        if (args[2].equals("skill")) {
            Skill<?> skill = Objects.requireNonNull(Fixtures.skill(args[3]));
            if (args.length > 4 && args[4].equals("open")) SkillHost.of(skill).openGui(player);
            JsonObject result = inventory(player);
            if (args.length > 4 && args[4].equals("set")) {
                result.addProperty("accepted", PlayerPreferences.setSkillEnabled(skill, runtime, CommonPreferences.Toggle.valueOf(args[5])));
            }
            if (args.length > 4 && args[4].equals("reset")) result.addProperty("accepted", PlayerPreferences.resetSkill(skill, runtime));
            result.addProperty("enabled", PlayerPreferences.resolve(skill, runtime.getData(), CommonPreferences.SKILL_ENABLED).name());
            result.addProperty("stored", runtime.getData().getPreferences().getSkill(SkillHost.lineKey(skill), CommonPreferences.SKILL_ENABLED.id()));
            result.addProperty("playerSettingsEnabled", AdaptConfig.get().isPlayerSettingsEnabled());
            return result;
        }
        Adaptation<?> adaptation = Fixtures.adaptation(args[3]);
        if (args[2].equals("audit")) return audit(adaptation, runtime);
        if (args[2].equals("learn")) runtime.getData().getSkillLine(SkillHost.lineKey(adaptation.getSkill())).setAdaptation(adaptation, adaptation.getMaxLevel());
        if (args[2].equals("open")) AdaptationHost.of(adaptation).openGui(player);
        Boolean accepted = null;
        if (args[2].equals("set") || args[2].equals("try-set")) {
            PlayerPreference<?> preference = adaptation.getPlayerPreferences().stream()
                .filter(candidate -> candidate.id().equals(args[4])).findFirst().orElseThrow();
            accepted = set(adaptation, runtime, preference, args[5]);
            if (args[2].equals("set") && !accepted) throw new IllegalStateException("Preference write rejected");
        }
        if (args[2].equals("reset")) accepted = PlayerPreferences.reset(adaptation, runtime);
        JsonObject result = inventory(player);
        if (accepted != null) result.addProperty("accepted", accepted);
        result.addProperty("playerSettingsEnabled", AdaptConfig.get().isPlayerSettingsEnabled());
        result.addProperty("learned", adaptation.getLevel(PlayerApi.profile(runtime)));
        result.addProperty("active", adaptation.getActiveLevel(player));
        JsonArray settings = new JsonArray();
        for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", preference.id());
            entry.addProperty("label", PlayerPreferences.shown(
                    PlayerPreferences.label(PlayerPreferences.ownerKey(adaptation), preference)));
            entry.addProperty("value", resolve(adaptation, runtime, preference));
            entry.addProperty("stored", runtime.getData().getPreferences().get(adaptation.getId().toString(), preference.id()));
            entry.addProperty("visible", AdaptConfig.get().isPlayerSettingsEnabled() && adaptation.isPlayerPreferenceVisible(PlayerApi.profile(runtime), preference));
            settings.add(entry);
        }
        result.add("settings", settings);
        return result;
    }

    private static JsonObject auditLock(AdaptPlayer runtime) {
        if (AdaptConfig.get().isPlayerSettingsEnabled()) throw new IllegalStateException("Player settings are not locked");
        int adaptations = 0;
        int controls = 0;
        int skills = 0;
        for (Skill<?> skill : Fixtures.core().getAdaptServer().getSkillRegistry().getAllSkills()) {
            String storedSkill = runtime.getData().getPreferences().getSkill(SkillHost.lineKey(skill), CommonPreferences.SKILL_ENABLED.id());
            if (!PlayerPreferences.resolve(skill, runtime.getData(), CommonPreferences.SKILL_ENABLED).name()
                .equals(PlayerPreferences.policy(skill, CommonPreferences.SKILL_ENABLED).defaultValue)
                || PlayerPreferences.setSkillEnabled(skill, runtime, CommonPreferences.Toggle.OFF)
                || PlayerPreferences.resetSkill(skill, runtime)
                || !Objects.equals(storedSkill, runtime.getData().getPreferences().getSkill(SkillHost.lineKey(skill), CommonPreferences.SKILL_ENABLED.id()))) {
                throw new IllegalStateException("Locked skill preference changed for " + SkillHost.lineKey(skill));
            }
            skills++;
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                int previous = adaptation.getLevel(PlayerApi.profile(runtime));
                runtime.getData().getSkillLine(SkillHost.lineKey(skill)).setAdaptation(adaptation, adaptation.getMaxLevel());
                try {
                    for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
                        auditLockedPreference(adaptation, runtime, preference);
                        controls++;
                    }
                    if (PlayerPreferences.reset(adaptation, runtime) || adaptation.getLevel(PlayerApi.profile(runtime)) != adaptation.getMaxLevel()) {
                        throw new IllegalStateException("Locked adaptation reset changed " + adaptation.getId().toString());
                    }
                    adaptations++;
                } finally {
                    runtime.getData().getSkillLine(SkillHost.lineKey(skill)).setAdaptation(adaptation, previous);
                }
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("adaptations", adaptations);
        result.addProperty("controls", controls);
        result.addProperty("skills", skills);
        result.addProperty("passed", true);
        return result;
    }

    private static <E extends Enum<E>> void auditLockedPreference(Adaptation<?> adaptation, AdaptPlayer runtime, PlayerPreference<E> preference) {
        String stored = runtime.getData().getPreferences().get(adaptation.getId().toString(), preference.id());
        if (!resolve(adaptation, runtime, preference).equals(PlayerPreferences.policy(adaptation, preference).defaultValue)) {
            throw new IllegalStateException("Locked adaptation did not use server default for " + adaptation.getId().toString() + "." + preference.id());
        }
        for (PlayerPreference.Choice<E> choice : preference.choices()) {
            if (PlayerPreferences.set(adaptation, runtime, preference, choice.value())
                || !Objects.equals(stored, runtime.getData().getPreferences().get(adaptation.getId().toString(), preference.id()))) {
                throw new IllegalStateException("Locked adaptation write changed " + adaptation.getId().toString() + "." + preference.id());
            }
        }
    }

    private static JsonObject catalog() {
        JsonObject result = new JsonObject();
        JsonArray adaptations = new JsonArray();
        for (Skill<?> skill : Fixtures.core().getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", adaptation.getId().toString());
                entry.addProperty("skill", SkillHost.lineKey(skill));
                entry.addProperty("controls", adaptation.getPlayerPreferences().size());
                JsonArray settings = new JsonArray();
                for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
                    JsonObject setting = new JsonObject();
                    setting.addProperty("id", preference.id());
                    String owner = PlayerPreferences.ownerKey(adaptation);
                    setting.addProperty("label", PlayerPreferences.shown(PlayerPreferences.label(owner, preference)));
                    setting.addProperty("default", PlayerPreferences.policy(adaptation, preference).defaultValue);
                    setting.add("choices", choices(owner, preference));
                    settings.add(setting);
                }
                entry.add("settings", settings);
                adaptations.add(entry);
            }
        }
        result.add("adaptations", adaptations);
        Path export = Fixtures.host().dataDirectory().resolve("qa-preference-catalog.json");
        try {
            Files.writeString(export, result.toString());
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not export preference catalog", failure);
        }
        for (int index = 0; index < adaptations.size(); index++) {
            adaptations.get(index).getAsJsonObject().remove("settings");
        }
        result.addProperty("export", export.toString());
        return result;
    }

    private static JsonObject audit(Adaptation<?> adaptation, AdaptPlayer runtime) {
        PlayerRef player = runtime.getPlayer();
        int previous = adaptation.getLevel(PlayerApi.profile(runtime));
        int level = adaptation.getMaxLevel();
        Skill<?> skill = adaptation.getSkill();
        runtime.getData().getSkillLine(SkillHost.lineKey(skill)).setAdaptation(adaptation, level);
        JsonObject result = new JsonObject();
        int choices = 0;
        try {
            PlayerPreference<?> enabled = adaptation.getPlayerPreferences().stream()
                .filter(preference -> preference.id().equals("enabled")).findFirst().orElseThrow();
            if (!set(adaptation, runtime, enabled, "OFF") || adaptation.getActiveLevel(player) != 0
                || adaptation.getLevel(PlayerApi.profile(runtime)) != level
                || Fixtures.core().getAdaptServer().getOnlineAdaptationLevel(player.uuid(), SkillHost.lineKey(skill), adaptation.getId().toString()) != 0)
                throw new IllegalStateException("Personal off did not preserve learned level and block activation");
            if (!set(adaptation, runtime, enabled, "ON")
                || !PlayerPreferences.setSkillEnabled(skill, runtime, CommonPreferences.Toggle.OFF)
                || adaptation.getActiveLevel(player) != 0
                || Fixtures.core().getAdaptServer().getOnlineAdaptationLevel(player.uuid(), SkillHost.lineKey(skill), adaptation.getId().toString()) != 0)
                throw new IllegalStateException("Skill off did not win over adaptation on");
            PlayerPreferences.resetSkill(skill, runtime);
            for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
                choices += auditChoices(adaptation, runtime, preference);
            }
            result.addProperty("id", adaptation.getId().toString());
            result.addProperty("controls", adaptation.getPlayerPreferences().size());
            result.addProperty("choices", choices);
            result.addProperty("passed", true);
            return result;
        } finally {
            PlayerPreferences.reset(adaptation, runtime);
            PlayerPreferences.resetSkill(skill, runtime);
            runtime.getData().getSkillLine(SkillHost.lineKey(skill)).setAdaptation(adaptation, previous);
        }
    }

    private static <E extends Enum<E>> int auditChoices(Adaptation<?> adaptation, AdaptPlayer runtime, PlayerPreference<E> preference) {
        PreferencePolicy policy = PlayerPreferences.policy(adaptation, preference);
        String defaultValue = policy.defaultValue;
        List<String> allowed = List.copyOf(policy.allowedValues);
        int count = 0;
        for (PlayerPreference.Choice<E> choice : PlayerPreferences.allowedChoices(adaptation, preference, adaptation.getLevel(PlayerApi.profile(runtime)))) {
            if (!PlayerPreferences.set(adaptation, runtime, preference, choice.value())
                || PlayerPreferences.resolve(adaptation, runtime.getData(), adaptation.getLevel(PlayerApi.profile(runtime)), preference) != choice.value()) {
                throw new IllegalStateException("Could not select " + adaptation.getId().toString() + "." + preference.id() + "=" + choice.value());
            }
            count++;
        }
        if (!defaultValue.equals(policy.defaultValue) || !allowed.equals(policy.allowedValues)) {
            throw new IllegalStateException("Player write changed server policy");
        }
        return count;
    }

    private static <E extends Enum<E>> boolean set(Adaptation<?> adaptation, AdaptPlayer runtime, PlayerPreference<E> preference, String value) {
        return PlayerPreferences.set(adaptation, runtime, preference, preference.parse(value).orElse(null));
    }

    private static <E extends Enum<E>> String resolve(Adaptation<?> adaptation, AdaptPlayer runtime, PlayerPreference<E> preference) {
        return PlayerPreferences.resolve(adaptation, runtime.getData(), adaptation.getLevel(PlayerApi.profile(runtime)), preference).name();
    }

    private static JsonObject inventory(PlayerRef player) {
        JsonObject result = new JsonObject();
        result.add("slots", FixtureItems.menuSlots(player));
        return result;
    }

    private static <E extends Enum<E>> JsonArray choices(String owner, PlayerPreference<E> preference) {
        JsonArray choices = new JsonArray();
        for (PlayerPreference.Choice<E> choice : preference.choices()) {
            JsonObject value = new JsonObject();
            value.addProperty("value", choice.value().name());
            value.addProperty("label", PlayerPreferences.shown(PlayerPreferences.label(owner, preference, choice.value())));
            value.addProperty("level", choice.minimumLevel());
            choices.add(value);
        }
        return choices;
    }
}
