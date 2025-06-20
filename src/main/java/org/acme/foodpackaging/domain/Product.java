package org.acme.foodpackaging.domain;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;

import java.time.Duration;
import java.util.Map;

public class Product {

    @PlanningId
    private String id;
    private String name;
    private ProductType type;
    private GlazeType glaze;
    private FillingType filling;
    private boolean allergen;
    /** The map key is previous product on assembly line. */
    private Map<Product, Duration> cleaningDurations;
    private Map<Product, Integer> cleaningPenalties;

    public Product() {
    }

    public Product(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public Product(String id, String name, ProductType type, boolean allergen) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.filling = FillingType.fromProduct(id);
        this.glaze = GlazeType.fromProduct(id, type);
        this.allergen = allergen;
    }

    public ProductType getType() { return type; }

    public GlazeType getGlaze(){ return glaze; }

    public boolean is_allergen() { return allergen; }

    @Override
    public String toString() {
        return name;
    }

    public Duration getCleanupDuration(Product previousProduct) {
        Duration cleanupDuration = cleaningDurations.get(previousProduct);
        if (cleanupDuration == null) {
            throw new IllegalArgumentException("Cleanup duration previousProduct (" + previousProduct
                    + ") to toProduct (" + this + ") is missing.");
        }
        return cleanupDuration;
    }

    public Integer getCleanupPenalties(Product previousProduct) {
        Integer cleanupPenalty = cleaningPenalties.get(previousProduct);
        if (cleanupPenalty == null) {
            throw new IllegalArgumentException("Cleanup penalty previousProduct (" + previousProduct
                    + ") to toProduct (" + this + ") is missing.");
        }
        return cleanupPenalty;
    }

    // ************************************************************************
    // Getters and setters
    // ************************************************************************

    public FillingType getFilling(){ return filling; }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<Product, Duration> getCleaningDurations() {
        return cleaningDurations;
    }

    public void setCleaningDurations(Map<Product, Duration> cleaningDurations) {
        this.cleaningDurations = cleaningDurations;
    }

    public Map<Product, Integer> getCleaningPenalties() {
        return cleaningPenalties;
    }

    public void setCleaningPenalties(Map<Product, Integer> cleaningPenalties) {
        this.cleaningPenalties = cleaningPenalties;
    }
}
