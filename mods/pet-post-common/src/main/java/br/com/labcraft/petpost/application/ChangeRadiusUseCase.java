package br.com.labcraft.petpost.application;

import br.com.labcraft.petpost.PostRadius;

/** Calcula a próxima etapa do raio; o adapter continua gravando o BlockState e o SavedData. */
public final class ChangeRadiusUseCase {
    public Result execute(int currentStep) {
        int nextStep = PostRadius.nextStep(currentStep);
        return new Result(nextStep, PostRadius.atStep(nextStep));
    }

    public record Result(int step, int radius) {
    }
}
