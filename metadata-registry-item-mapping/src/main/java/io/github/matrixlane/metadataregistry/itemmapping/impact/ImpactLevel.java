package io.github.matrixlane.metadataregistry.itemmapping.impact;

/**
 * Level of impact in a cascade analysis.
 *
 * <p>Level 1 = direct (the item is directly connected to the changed item),
 * Level 2 = indirect (one hop away), Level 3 = cascade (two hops away).
 */
public enum ImpactLevel {
    DIRECT(1),
    INDIRECT(2),
    CASCADE(3);

    private final int depth;

    ImpactLevel(int depth) {
        this.depth = depth;
    }

    public int getDepth() {
        return depth;
    }

    public static ImpactLevel fromDepth(int depth) {
        return switch (depth) {
            case 1 -> DIRECT;
            case 2 -> INDIRECT;
            case 3 -> CASCADE;
            default -> throw new IllegalArgumentException("Depth must be 1-3, got: " + depth);
        };
    }
}
