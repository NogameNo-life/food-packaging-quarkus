package org.acme.foodpackaging.domain;

import java.util.Map;

public enum FillingType {
    CONDENSED_MILK("Вареная сгущенка"),
    CHOCOLATE("Шоколад"),
    STRAWBERRY("Клубника"),
    RASPBERRY("Малина"),
    MANGO("Манго"),
    CARAMEL_PEANUT("Карамель-Арахис"),
    HAZELNUT("Фундук"),
    NONE("Без начинки");

    private static final Map<String, FillingType> ID_TO_FILLING = Map.of(
            "4810268050671", CONDENSED_MILK,
            "4810268050640", CHOCOLATE,
            "4810268050138", CHOCOLATE,
            "4810268050657", STRAWBERRY,
            "4810268050121", STRAWBERRY,
            "4810268054969", HAZELNUT,
            "4810268050664", MANGO,
            "4810268056826", CARAMEL_PEANUT,
            "4810268053153", RASPBERRY,
            "4810268050282", NONE

    );
    private final String displayName;

    FillingType(String displayName) {
        this.displayName = displayName;
    }

    public static FillingType fromProduct(String productId) {
        return ID_TO_FILLING.getOrDefault(productId, NONE);
    }
}

