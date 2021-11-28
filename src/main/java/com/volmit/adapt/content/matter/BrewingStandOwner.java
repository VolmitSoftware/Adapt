package com.volmit.adapt.content.matter;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@AllArgsConstructor
@Data
public class BrewingStandOwner {
    private UUID owner;
}
