package org.acme.foodpackaging.domain;

public enum ProductType {

    PLUSH("Плюш"),
    ROD("Стержень"),
    CLASSIC("Классика"),
    CACTUS("Кактус");

    private final String displayName;

    ProductType(String displayName) {
        this.displayName = displayName;
    }
}
