package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class PreferenceFixtures {
    private PreferenceFixtures() {
    }

    public static JsonObject execute(Player player, String[] args) {
        AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
        if (!runtime.isRuntimeReady()) throw new IllegalStateException("Player runtime is not ready");
        if (args[2].equals("catalog")) return catalog();
        if (args[2].equals("skill")) {
            Skill<?> skill = Objects.requireNonNull(Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(args[3]));
            if (args.length > 4 && args[4].equals("open")) skill.openGui(player);
            JsonObject result = inventory(player);
            result.addProperty("enabled", PlayerPreferences.resolve(skill, runtime.getData(), CommonPreferences.SKILL_ENABLED).name());
            return result;
        }
        Adaptation<?> adaptation = adaptation(args[3]);
        if (args[2].equals("audit")) return audit(adaptation, runtime);
        if (args[2].equals("learn")) runtime.getData().getSkillLine(adaptation.getSkill().getName()).setAdaptation(adaptation, adaptation.getMaxLevel());
        if (args[2].equals("open")) adaptation.openGui(player);
        if (args[2].equals("set")) {
            PlayerPreference<?> preference = adaptation.getPlayerPreferences().stream()
                .filter(candidate -> candidate.id().equals(args[4])).findFirst().orElseThrow();
            if (!set(adaptation, runtime, preference, args[5])) throw new IllegalStateException("Preference write rejected");
        }
        JsonObject result = inventory(player);
        result.addProperty("learned", adaptation.getLevel(runtime));
        result.addProperty("active", adaptation.getActiveLevel(player));
        JsonArray settings = new JsonArray();
        for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", preference.id());
            entry.addProperty("label", AdaptLanguage.text(preference.label()));
            entry.addProperty("value", resolve(adaptation, runtime, preference));
            entry.addProperty("visible", adaptation.isPlayerPreferenceVisible(runtime, preference));
            settings.add(entry);
        }
        result.add("settings", settings);
        return result;
    }

    private static JsonObject catalog() {
        JsonObject result = new JsonObject();
        JsonArray adaptations = new JsonArray();
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", adaptation.getName());
                entry.addProperty("skill", skill.getName());
                entry.addProperty("controls", adaptation.getPlayerPreferences().size());
                JsonArray settings = new JsonArray();
                for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
                    JsonObject setting = new JsonObject();
                    setting.addProperty("id", preference.id());
                    setting.addProperty("label", AdaptLanguage.text(preference.label()));
                    setting.addProperty("default", PlayerPreferences.policy(adaptation, preference).defaultValue);
                    JsonArray choices = new JsonArray();
                    for (PlayerPreference.Choice<?> choice : preference.choices()) {
                        JsonObject value = new JsonObject();
                        value.addProperty("value", choice.value().name());
                        value.addProperty("label", AdaptLanguage.text(choice.label()));
                        value.addProperty("level", choice.minimumLevel());
                        choices.add(value);
                    }
                    setting.add("choices", choices);
                    settings.add(setting);
                }
                entry.add("settings", settings);
                adaptations.add(entry);
            }
        }
        result.add("adaptations", adaptations);
        Path export = Adapt.instance.getDataFolder().toPath().resolve("qa-preference-catalog.json");
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
        Player player = runtime.getPlayer();
        int previous = adaptation.getLevel(runtime);
        int level = adaptation.getMaxLevel();
        Skill<?> skill = adaptation.getSkill();
        runtime.getData().getSkillLine(skill.getName()).setAdaptation(adaptation, level);
        JsonObject result = new JsonObject();
        int choices = 0;
        try {
            PlayerPreference<?> enabled = adaptation.getPlayerPreferences().stream()
                .filter(preference -> preference.id().equals("enabled")).findFirst().orElseThrow();
            if (!set(adaptation, runtime, enabled, "OFF") || adaptation.getActiveLevel(player) != 0
                || adaptation.getLevel(runtime) != level
                || Adapt.instance.getAdaptServer().getOnlineAdaptationLevel(player.getUniqueId(), skill.getName(), adaptation.getName()) != 0)
                throw new IllegalStateException("Personal off did not preserve learned level and block activation");
            if (!set(adaptation, runtime, enabled, "ON")
                || !PlayerPreferences.setSkillEnabled(skill, runtime, CommonPreferences.Toggle.OFF)
                || adaptation.getActiveLevel(player) != 0
                || Adapt.instance.getAdaptServer().getOnlineAdaptationLevel(player.getUniqueId(), skill.getName(), adaptation.getName()) != 0)
                throw new IllegalStateException("Skill off did not win over adaptation on");
            PlayerPreferences.resetSkill(skill, runtime);
            for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
                choices += auditChoices(adaptation, runtime, preference);
            }
            result.addProperty("id", adaptation.getName());
            result.addProperty("controls", adaptation.getPlayerPreferences().size());
            result.addProperty("choices", choices);
            result.addProperty("passed", true);
            return result;
        } finally {
            PlayerPreferences.reset(adaptation, runtime);
            PlayerPreferences.resetSkill(skill, runtime);
            runtime.getData().getSkillLine(skill.getName()).setAdaptation(adaptation, previous);
        }
    }

    private static <E extends Enum<E>> int auditChoices(Adaptation<?> adaptation, AdaptPlayer runtime, PlayerPreference<E> preference) {
        PreferencePolicy policy = PlayerPreferences.policy(adaptation, preference);
        String defaultValue = policy.defaultValue;
        List<String> allowed = List.copyOf(policy.allowedValues);
        int count = 0;
        for (PlayerPreference.Choice<E> choice : PlayerPreferences.allowedChoices(adaptation, preference, adaptation.getLevel(runtime))) {
            if (!PlayerPreferences.set(adaptation, runtime, preference, choice.value())
                || PlayerPreferences.resolve(adaptation, runtime.getData(), adaptation.getLevel(runtime), preference) != choice.value()) {
                throw new IllegalStateException("Could not select " + adaptation.getName() + "." + preference.id() + "=" + choice.value());
            }
            count++;
        }
        if (!defaultValue.equals(policy.defaultValue) || !allowed.equals(policy.allowedValues)) {
            throw new IllegalStateException("Player write changed server policy");
        }
        return count;
    }

    private static <E extends Enum<E>> boolean set(Adaptation<?> adaptation, AdaptPlayer runtime, PlayerPreference<E> preference, String value) {
        return PlayerPreferences.set(adaptation, runtime, preference, preference.parse(value));
    }

    private static <E extends Enum<E>> String resolve(Adaptation<?> adaptation, AdaptPlayer runtime, PlayerPreference<E> preference) {
        return PlayerPreferences.resolve(adaptation, runtime.getData(), adaptation.getLevel(runtime), preference).name();
    }

    private static Adaptation<?> adaptation(String id) {
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation.getName().equals(id)) return adaptation;
            }
        }
        throw new IllegalArgumentException("Unknown adaptation " + id);
    }

    private static JsonObject inventory(Player player) {
        JsonObject result = new JsonObject();
        JsonArray slots = new JsonArray();
        Inventory inventory = player.getOpenInventory().getTopInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !item.hasItemMeta()) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("slot", slot);
            entry.addProperty("material", item.getType().name());
            entry.addProperty("name", item.getItemMeta().getDisplayName());
            slots.add(entry);
        }
        result.add("slots", slots);
        return result;
    }
}
