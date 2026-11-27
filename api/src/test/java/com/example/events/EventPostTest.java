package com.example.events;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.server.ResourcesReloadedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventPostTest {
  @Test
  void anAddonPostsItsOwnEvents() {
    OreFound event = new OreFound();

    assertThat(AdaptApi.events().post(event)).isSameAs(event);
  }

  @Test
  void adaptsOwnEventsCannotBePostedByAnAddon() {
    assertThatThrownBy(() -> AdaptApi.events().post(new ResourcesReloadedEvent(ResourcesReloadedEvent.Cause.UNKNOWN)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(ResourcesReloadedEvent.class.getName());
  }

  static final class OreFound extends AdaptEvent {
  }
}
