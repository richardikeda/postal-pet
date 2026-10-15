package br.com.labcraft.petpost;

import br.com.labcraft.petpost.application.BindPetUseCase;
import br.com.labcraft.petpost.application.ChangeRadiusUseCase;
import br.com.labcraft.petpost.application.RenamePostUseCase;
import br.com.labcraft.petpost.application.UnbindPetUseCase;
import br.com.labcraft.petpost.contract.persistence.PetRecord;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ponto de entrada do mod: registra o Postal Pet, suas interações, partículas
 * e os sinais sonoros usados para marcar territórios de pets.
 */
public final class PetPost implements ModInitializer {
    public static final String MOD_ID = "petpost";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Identifier PET_POST_ID = id("pet_post");
    private static final ResourceKey<Block> PET_POST_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, PET_POST_ID);
    private static final ResourceKey<Item> PET_POST_ITEM_KEY = ResourceKey.create(Registries.ITEM, PET_POST_ID);
    private static final ResourceKey<Item> SILVER_GUIDE_KEY = ResourceKey.create(Registries.ITEM, id("silver_guide"));
    private static final ResourceKey<Item> GOLD_GUIDE_KEY = ResourceKey.create(Registries.ITEM, id("gold_guide"));
    public static final PetPostBlock PET_POST = new PetPostBlock(BlockBehaviour.Properties.of()
            .setId(PET_POST_BLOCK_KEY)
            .mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion());
    public static final Item PET_POST_ITEM = new BlockItem(PET_POST, new Item.Properties().setId(PET_POST_ITEM_KEY));
    public static final Item SILVER_GUIDE = new Item(new Item.Properties().setId(SILVER_GUIDE_KEY));
    public static final Item GOLD_GUIDE = new Item(new Item.Properties().setId(GOLD_GUIDE_KEY));
    private static final SoundEvent[] WOOD_CREAKS = {
            SoundEvents.WOODEN_DOOR_OPEN,
            SoundEvents.WOODEN_TRAPDOOR_OPEN,
            SoundEvents.FENCE_GATE_OPEN
    };
    private static int lastCreakIndex = -1;
    private static final BindPetUseCase BIND_PET = new BindPetUseCase();
    private static final UnbindPetUseCase UNBIND_PET = new UnbindPetUseCase();
    private static final RenamePostUseCase RENAME_POST = new RenamePostUseCase();
    private static final ChangeRadiusUseCase CHANGE_RADIUS = new ChangeRadiusUseCase();

    @Override
    public void onInitialize() {
        PetPostConfig.load();
        Registry.register(BuiltInRegistries.BLOCK, PET_POST_ID, PET_POST);
        Registry.register(BuiltInRegistries.ITEM, PET_POST_ID, PET_POST_ITEM);
        Registry.register(BuiltInRegistries.ITEM, id("silver_guide"), SILVER_GUIDE);
        Registry.register(BuiltInRegistries.ITEM, id("gold_guide"), GOLD_GUIDE);
        CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register((tab, entries) -> {
            boolean containsChest = entries.getDisplayStacks().stream().anyMatch(stack -> stack.is(Items.CHEST));
            if (containsChest) {
                entries.insertAfter(Items.CHEST, PET_POST_ITEM);
                entries.insertAfter(PET_POST_ITEM, SILVER_GUIDE);
                entries.insertAfter(SILVER_GUIDE, GOLD_GUIDE);
            }
        });
        registerInteractions();
        registerPetBindingInteraction();
        PetListPayloads.register();
        PetActionPayloads.register();
        TerritoryPreview.register();
        PetHomeFollowGuard.register();
        registerCommands();
        LOGGER.info("Postal Pet initialized for Minecraft 26.2");
    }

    /** Registra a interação independente de cada Pet Post colocado no mundo. */
    private static void registerInteractions() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!state.is(PET_POST)) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.PASS;

            ServerLevel serverLevel = (ServerLevel) level;
            PetPostSavedData data = PetPostSavedData.get(serverLevel);
            var existing = data.at(serverLevel, pos);
            if (existing.isPresent() && !existing.get().owner().equals(player.getUUID().toString())) {
                player.sendOverlayMessage(Component.translatable("message.petpost.not_owner"));
                return InteractionResult.FAIL;
            }
            playWoodCreak(serverLevel, pos);
            if (player.isShiftKeyDown()) {
                int radius = PetPostBlock.radius(state);
                level.setBlock(pos, state.setValue(PetPostBlock.FLAG_RAISED, true), Block.UPDATE_ALL);
                data.mark(serverLevel, pos, player.getUUID(), radius);
                TerritoryPreview.start(serverLevel, pos, radius);
                player.sendOverlayMessage(Component.translatable("message.petpost.activated",
                        data.at(serverLevel, pos).orElseThrow().name(), radius));
            } else {
                int radius = PetPostBlock.radius(state);
                data.mark(serverLevel, pos, player.getUUID(), radius);
            }
            return InteractionResult.SUCCESS;
        });
    }

    /** Registra os comandos novos e mantém `/petpost` como alias legado. */
    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
            dispatcher.register(commandTree("postalpet"));
            dispatcher.register(commandTree("pp"));
            dispatcher.register(commandTree("petpost"));
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commandTree(String root) {
        return Commands.literal(root)
                .then(Commands.literal("list")
                        .executes(command -> listPosts(command.getSource().getPlayerOrException())))
                .then(Commands.literal("info")
                        .executes(command -> showNearestInfo(command.getSource().getPlayerOrException())))
                .then(Commands.literal("show")
                        .executes(command -> showNearestTerritory(command.getSource().getPlayerOrException())))
                .then(Commands.literal("radius")
                        .executes(command -> cycleNearestRadius(command.getSource().getPlayerOrException())))
                .then(Commands.literal("pets")
                        .executes(command -> listNearestPets(command.getSource().getPlayerOrException())))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(command -> renameNearestPost(
                                        command.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(command, "name")))))
                .then(Commands.literal("name")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(command -> renameNearestPost(
                                        command.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(command, "name")))));
    }

    private static int listPosts(ServerPlayer player) {
        var posts = PetPostSavedData.get(player.level()).ownedBy(player.getUUID());
        if (posts.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.petpost.list_empty"));
            return 0;
        }
        player.sendSystemMessage(Component.translatable("message.petpost.list_header", posts.size()));
        for (PetPostSavedData.PostEntry entry : posts) {
            BlockPos pos = entry.blockPos();
            player.sendSystemMessage(Component.translatable("message.petpost.list_entry",
                    entry.name(), entry.radius(), entry.dimension(), pos.getX(), pos.getY(), pos.getZ()));
        }
        return posts.size();
    }

    private static int renameNearestPost(ServerPlayer player, String requestedName) {
        PetPostSavedData data = PetPostSavedData.get(player.level());
        var nearest = data.nearestOwned(player.level(), player.blockPosition(), player.getUUID(), 8);
        if (nearest.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.petpost.rename_missing"));
            return 0;
        }
        var renamed = RENAME_POST.execute(PetPostContractMapper.toContract(nearest.get()),
                player.getUUID().toString(), requestedName);
        if (renamed.isEmpty() || !data.replaceContract(renamed.get())) {
            player.sendSystemMessage(Component.translatable("message.petpost.rename_missing"));
            return 0;
        }
        player.sendSystemMessage(Component.translatable("message.petpost.renamed", renamed.get().name()));
        return 1;
    }

    private static int showNearestInfo(ServerPlayer player) {
        var entry = nearestOwnedPost(player);
        if (entry.isEmpty()) return noNearbyPost(player);
        BlockPos pos = entry.get().blockPos();
        player.sendSystemMessage(Component.translatable("message.petpost.info",
                entry.get().name(), entry.get().radius(), entry.get().dimension(),
                pos.getX(), pos.getY(), pos.getZ()));
        return 1;
    }

    /** Lista o cadastro persistente sem alegar que entidades descarregadas estão presentes. */
    private static int listNearestPets(ServerPlayer player) {
        var entry = nearestOwnedPost(player);
        if (entry.isEmpty()) return noNearbyPost(player);
        if (entry.get().pets().isEmpty()) { player.sendSystemMessage(Component.translatable("message.petpost.pets_empty", entry.get().name())); return 0; }
        player.sendSystemMessage(Component.translatable("message.petpost.pets_header", entry.get().name(), entry.get().pets().size()));
        for (var pet : entry.get().pets()) player.sendSystemMessage(Component.translatable(
                "message.petpost.pet_entry", pet.name(), pet.type(), "descarregado"));
        return entry.get().pets().size();
    }

    private static int showNearestTerritory(ServerPlayer player) {
        var entry = nearestOwnedPost(player);
        if (entry.isEmpty()) return noNearbyPost(player);
        BlockPos pos = entry.get().blockPos();
        BlockState state = player.level().getBlockState(pos);
        if (!state.is(PET_POST)) return noNearbyPost(player);
        int radius = PetPostBlock.radius(state);
        player.level().setBlock(pos, state.setValue(PetPostBlock.FLAG_RAISED, true), Block.UPDATE_ALL);
        PetPostSavedData.get(player.level()).mark(player.level(), pos, player.getUUID(), radius);
        previewTerritory(player.level(), pos, radius);
        player.sendSystemMessage(Component.translatable(
                "message.petpost.marked", entry.get().name(), radius, pos.getX(), pos.getY(), pos.getZ()));
        return 1;
    }

    private static int cycleNearestRadius(ServerPlayer player) {
        var entry = nearestOwnedPost(player);
        if (entry.isEmpty()) return noNearbyPost(player);
        BlockPos pos = entry.get().blockPos();
        BlockState state = player.level().getBlockState(pos);
        if (!state.is(PET_POST)) return noNearbyPost(player);
        ChangeRadiusUseCase.Result change = CHANGE_RADIUS.execute(state.getValue(PetPostBlock.RADIUS_STEP));
        BlockState updated = state
                .setValue(PetPostBlock.RADIUS_STEP, change.step())
                .setValue(PetPostBlock.FLAG_RAISED, true);
        player.level().setBlock(pos, updated, Block.UPDATE_ALL);
        int radius = PetPostBlock.radius(updated);
        PetPostSavedData.get(player.level()).mark(player.level(), pos, player.getUUID(), radius);
        TerritoryPreview.start(player.level(), pos, radius);
        player.sendOverlayMessage(Component.translatable("message.petpost.radius_changed",
                entry.get().name(), radius));
        return radius;
    }

    private static java.util.Optional<PetPostSavedData.PostEntry> nearestOwnedPost(ServerPlayer player) {
        return PetPostSavedData.get(player.level()).nearestOwned(
                player.level(), player.blockPosition(), player.getUUID(), 8);
    }

    private static int noNearbyPost(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("message.petpost.rename_missing"));
        return 0;
    }

    /**
     * Fluxo físico do MVP: use uma guia no pet vanilla domesticado junto ao
     * Postal Pet; agachar com a guia remove o vínculo. A decisão e a posse são
     * verificadas somente no servidor e nunca procuram entidades por tick.
     */
    private static void registerPetBindingInteraction() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND || !isGuide(player.getItemInHand(hand))) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer serverPlayer) || !isAuthorizedVanillaPet(entity, serverPlayer)) {
                player.sendSystemMessage(Component.translatable("message.petpost.pet_not_authorized")); return InteractionResult.FAIL;
            }
            ServerLevel serverLevel = (ServerLevel) level;
            PetPostSavedData data = PetPostSavedData.get(serverLevel);
            var post = data.nearestOwned(serverLevel, entity.blockPosition(), player.getUUID(), 8);
            if (post.isEmpty()) { player.sendSystemMessage(Component.translatable("message.petpost.pet_post_missing")); return InteractionResult.FAIL; }
            var existingBinding = data.boundPost(entity.getUUID(), player.getUUID());
            if (!player.isShiftKeyDown() && existingBinding.isPresent()) {
                player.sendSystemMessage(Component.translatable("message.petpost.pet_already_bound",
                        existingBinding.get().name()));
                return InteractionResult.FAIL;
            }
            String owner = player.getUUID().toString();
            boolean changed;
            if (player.isShiftKeyDown()) {
                UnbindPetUseCase.Result result = UNBIND_PET.execute(data.ownedContracts(player.getUUID()), owner,
                        post.get().dimension(), post.get().position(), entity.getUUID());
                changed = result.changed() && data.replaceOwnedContracts(owner, result.posts());
                if (changed) PetHomeFollowGuard.forget(serverLevel, entity.getUUID());
            } else {
                PetRecord pet = new PetRecord(entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                        entity.getName().getString(), post.get().dimension(), post.get().position(), serverLevel.getGameTime(),
                        player.getItemInHand(hand).is(GOLD_GUIDE) ? "gold" : "silver");
                BindPetUseCase.Result result = BIND_PET.execute(data.ownedContracts(player.getUUID()), owner,
                        post.get().dimension(), post.get().position(), pet);
                changed = result.changed() && data.replaceOwnedContracts(owner, result.posts());
                if (changed) {
                    if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
                    if (entity instanceof TamableAnimal tamable) PetHomeFollowGuard.track(serverLevel, tamable, post.get());
                }
            }
            player.sendSystemMessage(Component.translatable(changed
                    ? (player.isShiftKeyDown() ? "message.petpost.pet_unbound" : "message.petpost.pet_bound")
                    : "message.petpost.pet_not_linked", entity.getName(), post.get().name()));
            return changed ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        });
    }

    private static boolean isGuide(net.minecraft.world.item.ItemStack stack) {
        return stack.is(SILVER_GUIDE) || stack.is(GOLD_GUIDE);
    }

    /** Aceita apenas entidades vanilla domesticáveis com o UUID do dono atual. */
    private static boolean isAuthorizedVanillaPet(Entity entity, ServerPlayer player) {
        return entity instanceof TamableAnimal pet && pet.isTame() && pet.isOwnedBy(player)
                && "minecraft".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace());
    }

    /** Desenha somente para os jogadores próximos o limite do território marcado. */
    private static void previewTerritory(ServerLevel level, BlockPos center, int radius) {
        TerritoryPreview.start(level, center, radius);
    }

    /** Toca um de três rangidos de madeira sem repetir o som imediatamente anterior. */
    private static void playWoodCreak(ServerLevel level, BlockPos pos) {
        int choice = level.getRandom().nextInt(WOOD_CREAKS.length);
        if (choice == lastCreakIndex) {
            choice = (choice + 1 + level.getRandom().nextInt(WOOD_CREAKS.length - 1)) % WOOD_CREAKS.length;
        }
        lastCreakIndex = choice;
        level.playSound(null, pos, WOOD_CREAKS[choice], SoundSource.BLOCKS,
                0.75F, 0.88F + level.getRandom().nextFloat() * 0.18F);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
