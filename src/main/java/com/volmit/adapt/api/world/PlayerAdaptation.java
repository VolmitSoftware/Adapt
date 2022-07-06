package com.volmit.adapt.api.world;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class PlayerAdaptation {
    private String id;
    private int level;
    private Map<String, Object> storage = new HashMap<>();
}
