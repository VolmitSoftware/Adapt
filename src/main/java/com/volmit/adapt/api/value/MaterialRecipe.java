package com.volmit.adapt.api.value;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Builder
@Data
public class MaterialRecipe {
    private List<MaterialCount> input;
    private MaterialCount output;
}
