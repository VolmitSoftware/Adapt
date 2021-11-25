package com.volmit.adapt.api.world;

import com.volmit.adapt.util.KMap;
import lombok.Data;

@Data
public class PlayerAdaptation {
    private String id;
    private int level;
    private KMap<String, Object> storage = new KMap<>();
}
