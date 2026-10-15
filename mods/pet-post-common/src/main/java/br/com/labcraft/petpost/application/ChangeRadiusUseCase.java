package br.com.labcraft.petpost.application;

import br.com.labcraft.petpost.PostRadius;

/** Computes the next range step; the adapter remains responsible for BlockState and SavedData writes. */
public final class ChangeRadiusUseCase {
    public Result execute(int currentStep) {
        int nextStep = PostRadius.nextStep(currentStep);
        return new Result(nextStep, PostRadius.atStep(nextStep));
    }

    public record Result(int step, int radius) {
    }
}
