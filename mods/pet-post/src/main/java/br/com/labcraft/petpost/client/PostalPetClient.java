package br.com.labcraft.petpost.client;

import br.com.labcraft.petpost.PetPost;
import br.com.labcraft.petpost.PetPostBlock;
import br.com.labcraft.petpost.PetPostContractMapper;
import br.com.labcraft.petpost.PetListPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/** Abre o menu da versão 1.21 ao interagir com um Postal Pet. */
public final class PostalPetClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(PetListPayloads.RESPONSE, (response, context) -> {
            if (context.client().screen instanceof PostalPetScreen screen) screen.accept(PetPostContractMapper.toContract(response));
        });
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) return InteractionResult.PASS;
            var state = level.getBlockState(hitResult.getBlockPos());
            if (!state.is(PetPost.PET_POST)) return InteractionResult.PASS;
            PostalPetScreen screen = new PostalPetScreen(hitResult.getBlockPos(), PetPostBlock.radius(state));
            Minecraft.getInstance().setScreen(screen);
            screen.requestPetList();
            return InteractionResult.SUCCESS;
        });
    }
}
