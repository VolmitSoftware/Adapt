package art.arcane.adapt.api;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.skill.SimpleSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SkillIdentityRegressionTest {

    @Test
    @DisplayName("skills compare by identity, not by field value")
    void skillsUseIdentityEquality() {
        SimpleSkill<?> a = mock(SimpleSkill.class);
        SimpleSkill<?> b = mock(SimpleSkill.class);
        assertThat(a).isEqualTo(a);
        assertThat(a).isNotEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(System.identityHashCode(a));
    }

    @Test
    @DisplayName("adaptations compare by identity, not by field value")
    void adaptationsUseIdentityEquality() {
        SimpleAdaptation<?> a = mock(SimpleAdaptation.class);
        SimpleAdaptation<?> b = mock(SimpleAdaptation.class);
        assertThat(a).isEqualTo(a);
        assertThat(a).isNotEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(System.identityHashCode(a));
    }

    @Test
    @DisplayName("skills and adaptations are recursion-safe as map keys")
    void identityHashingIsRecursionSafe() {
        SimpleSkill<?> skill = mock(SimpleSkill.class);
        SimpleAdaptation<?> adaptation = mock(SimpleAdaptation.class);
        Map<Object, String> map = new HashMap<>();
        map.put(skill, "skill");
        map.put(adaptation, "adaptation");
        assertThat(map.get(skill)).isEqualTo("skill");
        assertThat(map.get(adaptation)).isEqualTo("adaptation");
    }
}
