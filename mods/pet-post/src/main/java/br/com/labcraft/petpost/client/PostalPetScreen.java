package br.com.labcraft.petpost.client;

import br.com.labcraft.petpost.PetPost;
import br.com.labcraft.petpost.PetPostBlock;
import br.com.labcraft.petpost.PetActionPayloads;
import br.com.labcraft.petpost.PetListPayloads;
import br.com.labcraft.petpost.contract.network.PetListResponse;
import br.com.labcraft.petpost.contract.network.PetListView;
import br.com.labcraft.petpost.ui.model.PostalPetUiState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.concurrent.atomic.AtomicInteger;

/** Three simultaneous columns; presentation uses only the authorized snapshot and tracked client entities. */
public final class PostalPetScreen extends Screen {
    private static final AtomicInteger NEXT_REQUEST = new AtomicInteger();
    private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, DARK = 0xFF555555;
    private static final int CONTENT = 0xFFD8D8D8, TEXT = 0xFF3F3F3F, MUTED = 0xFF707070;
    private static final int GRID_COLUMNS = 2, GRID_ROWS = 3, VISIBLE_PETS = GRID_COLUMNS * GRID_ROWS;
    private static final int CELL_HEIGHT = 47;
    private final BlockPos postPos;
    private final int requestNonce;
    private int radius;
    private String postName = "";
    private PostalPetUiState uiState;
    private int left, top, panelWidth, panelHeight, firstDivider, secondDivider;
    private int pointerX, pointerY;
    private EditBox nameField;
    private boolean renameEditing;
    private String pendingUnbind = "";
    private Parrot shoulderPortrait;
    private String shoulderPortraitId = "";

    public PostalPetScreen(BlockPos postPos, int radius) {
        super(Component.translatable("screen.petpost.title"));
        this.postPos = postPos.immutable();
        this.radius = radius;
        this.requestNonce = NEXT_REQUEST.incrementAndGet();
        this.uiState = PostalPetUiState.loading(postPos.asLong(), requestNonce, "");
    }

    public void requestPetList() {
        if (!ClientPlayNetworking.canSend(PetListPayloads.REQUEST)) {
            uiState = uiState.unavailable();
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) uiState = uiState.withExpectedDimension(client.level.dimension().location().toString());
        ClientPlayNetworking.send(new PetListPayloads.Request(postPos.asLong(), requestNonce));
    }

    public void accept(PetListResponse response) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !response.dimension().equals(client.level.dimension().location().toString())
                || !uiState.view().matches(response)) return;
        uiState = uiState.accept(response, VISIBLE_PETS);
        if (uiState.hasSnapshot() && response.accepted()) {
            postName = response.postName();
            radius = response.radius();
        }
        if (!renameEditing) rebuildWidgets();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(360, width - 16);
        panelHeight = Math.min(224, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int contentWidth = panelWidth - 16;
        firstDivider = left + 8 + contentWidth * 32 / 100;
        secondDivider = firstDivider + contentWidth * 40 / 100;
        addHeaderControls();
        addInfoControls();
        addGridControls();
    }

    private String headerName() {
        return postName.isBlank() ? Component.translatable("screen.petpost.brand").getString() : postName;
    }

    private void addHeaderControls() {
        int x = left + 29;
        if (renameEditing) {
            String draft = nameField == null ? postName : nameField.getValue();
            nameField = new EditBox(font, x, top + 6, panelWidth - 137, 16, Component.translatable("screen.petpost.rename"));
            nameField.setMaxLength(32);
            nameField.setValue(draft);
            nameField.setHint(Component.translatable("screen.petpost.rename_hint"));
            addRenderableWidget(nameField);
            addRenderableWidget(Button.builder(Component.translatable("screen.petpost.save_name"), b -> saveRename())
                    .bounds(left + panelWidth - 103, top + 6, 48, 16).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.petpost.cancel"), b -> {
                renameEditing = false; nameField = null; rebuildWidgets();
            }).bounds(left + panelWidth - 53, top + 6, 45, 16).build());
        } else {
            int titleWidth = font.width(fit(headerName(), panelWidth - 58));
            addRenderableWidget(Button.builder(Component.translatable("screen.petpost.rename_icon"), b -> {
                renameEditing = true; nameField = null; rebuildWidgets();
                setFocused(nameField); nameField.setFocused(true);
            }).bounds(x + titleWidth + 4, top + 6, 16, 16)
                    .tooltip(Tooltip.create(Component.translatable("screen.petpost.rename_tooltip"))).build());
        }
    }

    private void saveRename() {
        if (nameField == null || nameField.getValue().isBlank()) return;
        ClientPlayNetworking.send(new PetActionPayloads.Rename(postPos.asLong(), nameField.getValue().strip()));
        renameEditing = false;
        nameField = null;
        // Command and read-only query use the same ordered connection; show the server's confirmed name.
        requestPetList();
        rebuildWidgets();
    }

    private void addInfoControls() {
        int x = firstDivider + 6, w = secondDivider - firstDivider - 12;
        int half = (w - 3) / 2, y = top + 45;
        if (hasSelectedPet()) {
            PetListView selected = uiState.pets().get(uiState.selectedPet());
            addRenderableWidget(Button.builder(Component.literal("↩"), b -> {
                pendingUnbind = ""; uiState = uiState.clearSelection(); rebuildWidgets();
            }).bounds(x + w - 38, y - 2, 18, 16)
                    .tooltip(Tooltip.create(Component.translatable("screen.petpost.clear_selection_tooltip"))).build());
            addRenderableWidget(Button.builder(Component.literal(pendingUnbind.equals(selected.uuid()) ? "!" : "×"), b -> {
                if (!pendingUnbind.equals(selected.uuid())) {
                    pendingUnbind = selected.uuid(); rebuildWidgets(); return;
                }
                try {
                    ClientPlayNetworking.send(new PetActionPayloads.Unbind(postPos.asLong(),
                            java.util.UUID.fromString(selected.uuid())));
                    pendingUnbind = ""; uiState = uiState.clearSelection(); requestPetList();
                } catch (IllegalArgumentException ignored) { pendingUnbind = ""; }
                rebuildWidgets();
            }).bounds(x + w - 18, y - 2, 18, 16)
                    .tooltip(Tooltip.create(Component.translatable(pendingUnbind.equals(selected.uuid())
                            ? "screen.petpost.unbind_confirm" : "screen.petpost.unbind"))).build());
            addFutureButton("screen.petpost.action_call_short", x, y + 139, half);
            addFutureButton("screen.petpost.action_return_short", x + half + 3, y + 139, w - half - 3);
            addFutureButton("screen.petpost.action_follow_short", x, y + 155, half);
            addFutureButton("screen.petpost.action_details_short", x + half + 3, y + 155, w - half - 3);
        } else {
            addRenderableWidget(Button.builder(Component.translatable("screen.petpost.distance"), b -> {
                ClientPlayNetworking.send(new PetActionPayloads.Radius(postPos.asLong()));
                requestPetList();
            }).bounds(x, y + 45, w, 18)
                    .tooltip(Tooltip.create(Component.translatable("screen.petpost.distance_tooltip"))).build());
        }
    }

    private void addFutureButton(String key, int x, int y, int w) {
        Button button = Button.builder(Component.literal(fit(Component.translatable(key).getString(), w - 6)), b -> {})
                .bounds(x, y, w, 14).tooltip(Tooltip.create(Component.translatable("screen.petpost.action_call_tooltip"))).build();
        button.active = false;
        addRenderableWidget(button);
    }

    private void addGridControls() {
        if (uiState.pets().size() <= VISIBLE_PETS) return;
        int x = left + 13, w = firstDivider - x - 5, half = (w - 3) / 2;
        Button previous = Button.builder(Component.literal("<"), b -> scrollGrid(true)).bounds(x, top + 202, half, 14).build();
        previous.active = uiState.firstPet() > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.literal(">"), b -> scrollGrid(false)).bounds(x + half + 3, top + 202, w - half - 3, 14).build();
        next.active = uiState.firstPet() + VISIBLE_PETS < uiState.pets().size();
        addRenderableWidget(next);
    }

    private void scrollGrid(boolean towardsStart) {
        for (int i = 0; i < GRID_COLUMNS; i++) uiState = uiState.scroll(towardsStart, VISIBLE_PETS);
        rebuildWidgets();
    }

    private void sendCommand(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null) client.getConnection().sendCommand(command);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        pointerX = mouseX; pointerY = mouseY;
        graphics.fill(0, 0, width, height, 0x80000000);
        frame(graphics, left, top, panelWidth, panelHeight);
        graphics.fill(left + 8, top + 29, left + panelWidth - 8, top + panelHeight - 8, CONTENT);
        graphics.fill(firstDivider + 2, top + 46, secondDivider - 1, top + panelHeight - 9, 0xFFE6EFF7);
        graphics.fill(secondDivider + 2, top + 46, left + panelWidth - 9, top + panelHeight - 9, 0xFFE5F0EF);
        graphics.fill(left + 10, top + 30, firstDivider - 3, top + 45, 0xFF2E70BD);
        graphics.fill(firstDivider + 3, top + 30, secondDivider - 2, top + 45, 0xFF477EAE);
        graphics.fill(secondDivider + 3, top + 30, left + panelWidth - 10, top + 45, 0xFF568C91);
        graphics.fill(firstDivider, top + 29, firstDivider + 1, top + panelHeight - 8, DARK);
        graphics.fill(secondDivider, top + 29, secondDivider + 1, top + panelHeight - 8, DARK);
        graphics.renderItem(new ItemStack(PetPost.PET_POST_ITEM), left + 8, top + 6);
        if (!renameEditing) label(graphics, Component.literal(headerName()), left + 29, top + 9, panelWidth - 58, TEXT);
        columnTitle(graphics, "screen.petpost.tab.pets", (left + 8 + firstDivider) / 2, 0xFFFFFFFF);
        columnTitle(graphics, "screen.petpost.tab.info", (firstDivider + secondDivider) / 2, 0xFFFFFFFF);
        columnTitle(graphics, "screen.petpost.tab.accessories", (secondDivider + left + panelWidth - 8) / 2, 0xFFFFFFFF);
        drawPets(graphics);
        drawInfo(graphics);
        drawAccessories(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The screen draws its own unblurred background.
    }

    private void drawPets(GuiGraphics graphics) {
        int x = left + 13, w = firstDivider - x - 5, cellWidth = w / GRID_COLUMNS;
        if (!uiState.hasSnapshot() || uiState.pets().isEmpty()) {
            String key = !uiState.hasSnapshot()
                    ? (uiState.snapshotStatus() == PostalPetUiState.SnapshotStatus.UNAVAILABLE ? "screen.petpost.pets_unavailable" : "screen.petpost.pets_loading")
                    : "screen.petpost.empty_title_short";
            label(graphics, Component.translatable(key), x, top + 55, w, MUTED);
            return;
        }
        int shown = uiState.visiblePets(VISIBLE_PETS).size();
        for (int i = 0; i < shown; i++) {
            int index = uiState.firstPet() + i, cx = x + (i % GRID_COLUMNS) * cellWidth, cy = top + 48 + (i / GRID_COLUMNS) * CELL_HEIGHT;
            PetListView pet = uiState.pets().get(index);
            frame(graphics, cx, cy, cellWidth - 3, 33);
            if (index == uiState.selectedPet()) graphics.fill(cx + 2, cy + 2, cx + cellWidth - 5, cy + 31, 0xFF9FC5E8);
            portrait(graphics, pet, cx + 2, cy + 2, cellWidth - 7, 29, false);
            label(graphics, Component.literal(pet.name()), cx + 1, cy + 35, cellWidth - 4, TEXT);
        }
    }

    private void drawInfo(GuiGraphics graphics) {
        int x = firstDivider + 6, w = secondDivider - firstDivider - 12, y = top + 45;
        if (!hasSelectedPet()) {
            label(graphics, Component.translatable("screen.petpost.position", postPos.getX(), postPos.getY(), postPos.getZ()), x, y + 4, w, MUTED);
            label(graphics, Component.translatable("screen.petpost.radius", radius), x, y + 20, w, TEXT);
            return;
        }
        PetListView pet = uiState.pets().get(uiState.selectedPet());
        // Reserve the right side for back/unlink controls.
        label(graphics, Component.literal(pet.name()), x, y + 3, w - 42, TEXT);
        int portraitX = x + (w - 56) / 2;
        frame(graphics, portraitX, y + 12, 56, 60);
        portrait(graphics, pet, portraitX + 2, y + 14, 52, 56, true);
        label(graphics, Component.translatable("screen.petpost.pet_type", displayType(pet.type())), x, y + 74, w, MUTED);
        LivingEntity entity = resolvePet(pet);
        if (entity == null) {
            label(graphics, Component.translatable("screen.petpost.portrait_unavailable"), x, y + 86, w, MUTED);
        } else {
            graphics.renderItem(new ItemStack(Items.APPLE), x, y + 87);
            label(graphics, Component.translatable("screen.petpost.health",
                    Math.round(entity.getHealth() * 10.0F) / 10.0F,
                    Math.round(entity.getMaxHealth() * 10.0F) / 10.0F), x + 18, y + 91, w - 18, 0xFF9B3043);
            graphics.renderItem(new ItemStack(Items.CLOCK), x, y + 103);
            label(graphics, Component.translatable("screen.petpost.phase",
                    Component.translatable(entity.isBaby() ? "screen.petpost.baby" : "screen.petpost.adult")),
                    x + 18, y + 107, w - 18, TEXT);
            // Chronological age is not recorded; omit it instead of inventing zero days.
            graphics.renderItem(new ItemStack(Items.IRON_CHESTPLATE), x, y + 119);
            label(graphics, Component.translatable("screen.petpost.armor", entity.getArmorValue()),
                    x + 18, y + 123, w - 18, 0xFF35619A);
        }
        int half = (w - 3) / 2;
        graphics.fill(x - 3, y + 139, x - 1, y + 153, 0xFF328F4C);
        graphics.fill(x + half + 1, y + 139, x + half + 3, y + 153, 0xFF3479BD);
        graphics.fill(x - 3, y + 155, x - 1, y + 169, 0xFFC58027);
        graphics.fill(x + half + 1, y + 155, x + half + 3, y + 169, 0xFF7A4BB5);
    }

    private void drawAccessories(GuiGraphics graphics) {
        int x = secondDivider + 7, w = left + panelWidth - secondDivider - 20;
        if (!hasSelectedPet()) {
            label(graphics, Component.translatable("screen.petpost.future_actions"), x, top + 48, w, MUTED);
            return;
        }
        PetListView selected = uiState.pets().get(uiState.selectedPet());
        LivingEntity pet = resolvePet(selected);
        label(graphics, Component.translatable("screen.petpost.accessory.collar"), x, top + 50, w, TEXT);
        if (pet instanceof Wolf wolf) {
            graphics.fill(x, top + 64, x + 14, top + 78, 0xFF000000 | wolf.getCollarColor().getFireworkColor());
        } else if (pet instanceof Cat cat) {
            graphics.fill(x, top + 64, x + 14, top + 78, 0xFF000000 | cat.getCollarColor().getFireworkColor());
        } else {
            label(graphics, Component.translatable("screen.petpost.accessory_none"), x, top + 65, w, MUTED);
        }
        label(graphics, Component.translatable("screen.petpost.accessory.pendant"), x, top + 86, w, TEXT);
        if (selected.guideMaterial().equals("silver") || selected.guideMaterial().equals("gold")) {
            int metal = selected.guideMaterial().equals("gold") ? 0xFFE1AE35 : 0xFFB8C8D0;
            graphics.fill(x, top + 101, x + 13, top + 114, 0xFF473D34);
            graphics.fill(x + 2, top + 103, x + 11, top + 112, metal);
            label(graphics, Component.translatable(selected.guideMaterial().equals("gold")
                    ? "screen.petpost.material.gold" : "screen.petpost.material.silver"), x + 17, top + 103, w - 17, TEXT);
        } else {
            label(graphics, Component.translatable("screen.petpost.accessory_none"), x, top + 103, w, MUTED);
        }
        label(graphics, Component.translatable("screen.petpost.future_actions"), x, top + 126, w, MUTED);
    }

    private LivingEntity resolvePet(PetListView pet) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || pet.uuid().isBlank()) return null;
        if (pet.entityId() < 0) return shoulderParrot(pet, client);
        var entity = client.level.getEntity(pet.entityId());
        if (!(entity instanceof LivingEntity living) || !entity.isAlive()
                || !entity.getUUID().toString().equals(pet.uuid())
                || !BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(pet.type()))
            return shoulderParrot(pet, client);
        return living;
    }

    private LivingEntity shoulderParrot(PetListView pet, Minecraft client) {
        if (!pet.type().equals("minecraft:parrot") || client.player == null) return null;
        CompoundTag[] shoulders = { client.player.getShoulderEntityLeft(), client.player.getShoulderEntityRight() };
        for (CompoundTag tag : shoulders) {
            if (!tag.hasUUID("UUID") || !tag.getUUID("UUID").toString().equals(pet.uuid())
                    || !tag.getString("id").equals("minecraft:parrot")) continue;
            if (!pet.uuid().equals(shoulderPortraitId) || shoulderPortrait == null) {
                shoulderPortrait = new Parrot(EntityType.PARROT, client.level);
                shoulderPortraitId = pet.uuid();
            }
            shoulderPortrait.load(tag);
            return shoulderPortrait;
        }
        shoulderPortrait = null; shoulderPortraitId = "";
        return null;
    }

    private void portrait(GuiGraphics graphics, PetListView pet, int x, int y, int w, int h, boolean interactive) {
        LivingEntity entity = resolvePet(pet);
        if (entity == null) {
            graphics.drawCenteredString(font, Component.literal("?"), x + w / 2, y + h / 2 - 4, MUTED);
            return;
        }
        int scale = Math.max(8, (int) (Math.min(w, h) * 0.65F / Math.max(0.8F, Math.max(entity.getBbHeight(), entity.getBbWidth()))));
        float mouseX = interactive ? pointerX : x + w / 2.0F;
        float mouseY = interactive ? pointerY : y + h / 2.0F;
        float body = entity.yBodyRot, yaw = entity.getYRot(), pitch = entity.getXRot(), head = entity.yHeadRot, oldHead = entity.yHeadRotO;
        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x, y, x + w, y + h, scale, 0.0F, mouseX, mouseY, entity);
        } finally {
            entity.yBodyRot = body; entity.setYRot(yaw); entity.setXRot(pitch);
            entity.yHeadRot = head; entity.yHeadRotO = oldHead;
        }
    }

    private void frame(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, DARK);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, LIGHT);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL);
    }

    private void label(GuiGraphics graphics, Component text, int x, int y, int w, int color) {
        graphics.drawString(font, fit(text.getString(), w), x, y, color, false);
    }

    private void columnTitle(GuiGraphics graphics, String key, int center, int color) {
        String title = Component.translatable(key).getString();
        graphics.drawString(font, title, center - font.width(title) / 2, top + 33, color, false);
    }

    private String fit(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(1, maxWidth - font.width("…"))) + "…";
    }

    private boolean hasSelectedPet() {
        return uiState.hasSnapshot() && uiState.selectedPet() >= 0 && uiState.selectedPet() < uiState.pets().size();
    }

    private Component displayType(String type) {
        return switch (type) {
            case "minecraft:wolf" -> Component.translatable("screen.petpost.type.wolf");
            case "minecraft:cat" -> Component.translatable("screen.petpost.type.cat");
            case "minecraft:parrot" -> Component.translatable("screen.petpost.type.parrot");
            default -> Component.literal(type.substring(type.indexOf(':') + 1).replace('_', ' '));
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left + 13, cellWidth = (firstDivider - x - 5) / GRID_COLUMNS;
        if (button == 0 && uiState.hasSnapshot() && !renameEditing
                && mouseX >= x && mouseX < x + cellWidth * GRID_COLUMNS && mouseY >= top + 48 && mouseY < top + 48 + GRID_ROWS * CELL_HEIGHT) {
            int row = (int) (mouseY - top - 48) / CELL_HEIGHT, col = (int) (mouseX - x) / cellWidth;
            int index = uiState.firstPet() + row * GRID_COLUMNS + col;
            if (index < uiState.pets().size()) { uiState = uiState.toggleSelection(index); rebuildWidgets(); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!renameEditing && mouseX >= left + 13 && mouseX < firstDivider && mouseY >= top + 48
                && mouseY < top + 48 + GRID_ROWS * CELL_HEIGHT && verticalAmount != 0) {
            scrollGrid(verticalAmount > 0); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public boolean isPauseScreen() { return false; }
}
