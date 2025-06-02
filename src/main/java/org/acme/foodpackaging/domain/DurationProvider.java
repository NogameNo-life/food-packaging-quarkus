package org.acme.foodpackaging.domain;

import java.time.Duration;

public class DurationProvider {
    public Duration calculate(Product product, Line line, int quantity) {
        int speed;

        switch (product.getType()) {
            case PLUSH:
                speed = 164;
                break;
            case CACTUS:
                speed = 184;
                break;
            case ROD:
                speed = 198;
                break;
            case CLASSIC:
                if (line != null && "6".equals(line.getId())) {
                    speed = 240;
                    return Duration.ofMinutes((long)Math.ceil(quantity / (double)speed) + 4);
                } else {
                    speed = 200;
                }
                break;
            default:
                speed = 200;
        }
        return Duration.ofMinutes((long)Math.ceil(quantity / (double)speed) +4);
    }
}
