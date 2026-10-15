package br.com.labcraft.petpost.mixin;

import br.com.labcraft.petpost.PetHomeFollowGuard;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps vanilla roaming goals available when a linked pet's owner leaves home. */
@Mixin(FollowOwnerGoal.class)
abstract class FollowOwnerGoalMixin {
    @Shadow @Final private TamableAnimal tamable;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void petpost$startOnlyInsideHome(CallbackInfoReturnable<Boolean> result) {
        if (PetHomeFollowGuard.suppressFollowing(tamable)) result.setReturnValue(false);
    }

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void petpost$continueOnlyInsideHome(CallbackInfoReturnable<Boolean> result) {
        if (PetHomeFollowGuard.suppressFollowing(tamable)) result.setReturnValue(false);
    }
}
