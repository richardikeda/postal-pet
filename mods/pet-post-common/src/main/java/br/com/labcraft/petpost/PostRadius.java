package br.com.labcraft.petpost;

/** Minecraft-independent range rules shared by every version adapter. */
public final class PostRadius {
    private static final int[] VALUES = {8, 16, 24, 32};

    private PostRadius() {
    }

    public static int atStep(int step) {
        return VALUES[step];
    }

    public static int nextStep(int step) {
        return (step + 1) % VALUES.length;
    }

    public static int closestStep(int requested) {
        int bestStep = 0;
        int bestDistance = Math.abs(requested - VALUES[0]);
        for (int step = 1; step < VALUES.length; step++) {
            int distance = Math.abs(requested - VALUES[step]);
            if (distance < bestDistance) {
                bestStep = step;
                bestDistance = distance;
            }
        }
        return bestStep;
    }

    /** The horizontal boundary includes the edge of the circle centered on the mailbox. */
    public static boolean outsideHorizontalArea(double x, double z, int postX, int postZ, int radius) {
        double dx = x - postX - 0.5;
        double dz = z - postZ - 0.5;
        return dx * dx + dz * dz > (double) radius * radius;
    }
}
