package se.vestige_be.pojo.enums;

import lombok.Getter;

@Getter
public enum TrustTier {
    NEW_SELLER(0),
    RISING_SELLER(1),
    PRO_SELLER(2),
    ELITE_SELLER(3);

    private final int level;

    TrustTier(int level) {
        this.level = level;
    }

}