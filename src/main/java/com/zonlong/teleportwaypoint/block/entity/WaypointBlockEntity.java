package com.zonlong.teleportwaypoint.block.entity;

import java.util.UUID;

import com.zonlong.teleportwaypoint.block.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.zonlong.teleportwaypoint.menu.ModMenus;
import com.zonlong.teleportwaypoint.menu.RenamePocketWaypointMenu;
import com.zonlong.teleportwaypoint.menu.RenameWaypointMenu;
import com.zonlong.teleportwaypoint.menu.WaypointListMenu;

public class WaypointBlockEntity extends BlockEntity {
    private static final String TAG_UID = "uid";
    private static final String TAG_ID = "id";
    private static final String TAG_NAME = "name";
    private static final String TAG_OWNER = "owner";

    private static final String ID_PATTERN = "[a-z0-9_]+";

    private UUID uid;
    private String id = "empty";
    private String name = "";
    private UUID owner;

    public WaypointBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.WAYPOINT.get(), pos, blockState);
    }

    public boolean isPocketWaypoint() {
        return getBlockState().is(ModBlocks.POCKET_WAYPOINT);
    }

    /**
     * Returns the unique id of this waypoint, lazily generating it on the server if absent.
     */
    public UUID getUid() {
        if (uid == null && level != null && !level.isClientSide()) {
            uid = UUID.randomUUID();
            setChanged();
        }
        return uid;
    }

    /**
     * Returns the uid without triggering lazy generation; may be null.
     */
    public UUID getExistingUid() {
        return uid;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = (id == null || id.isEmpty()) ? "empty" : id;
        setChanged();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name == null || name.isEmpty()) ? "Pocket Waypoint" : name;
        setChanged();
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public Component getDisplayName() {
        if (isPocketWaypoint()) {
            return name.isEmpty() ? Component.translatable("teleportwaypoint.pocket_waypoint.empty") : Component.literal(name);
        }
        return id.isEmpty() ? Component.translatable("teleportwaypoint.waypoint.empty") : Component.translatable("teleportwaypoint.waypoint." + id);
    }

    public static boolean isValidId(String id) {
        return id != null && id.matches(ID_PATTERN);
    }

    public boolean canRename(Player player) {
        if (player.isCreative()) {
            return true;
        }
        return isPocketWaypoint() && owner != null && owner.equals(player.getUUID());
    }

    public void openRenameScreen(net.minecraft.server.level.ServerPlayer player) {
        boolean canEdit = canRename(player);
        String name = isPocketWaypoint() ? this.name : this.id;
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return isPocketWaypoint()
                        ? Component.translatable("gui.teleportwaypoint.rename_pocket_waypoint")
                        : Component.translatable("gui.teleportwaypoint.rename_waypoint");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                if (isPocketWaypoint()) {
                    return new RenamePocketWaypointMenu(ModMenus.RENAME_POCKET_WAYPOINT.get(), containerId, getBlockPos(), canEdit, name);
                }
                return new RenameWaypointMenu(ModMenus.RENAME_WAYPOINT.get(), containerId, getBlockPos(), canEdit, name);
            }
        }, buf -> {
            buf.writeBlockPos(getBlockPos());
            buf.writeBoolean(canEdit);
            buf.writeUtf(name);
        });
    }

    public void openListScreen(net.minecraft.server.level.ServerPlayer player) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("gui.teleportwaypoint.waypoint_list");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                return new WaypointListMenu(ModMenus.WAYPOINT_LIST.get(), containerId, getBlockPos());
            }
        }, buf -> buf.writeBlockPos(getBlockPos()));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (uid != null) {
            tag.put(TAG_UID, NbtUtils.createUUID(uid));
        }
        if (!id.isEmpty()) {
            tag.putString(TAG_ID, id);
        }
        if (!name.isEmpty()) {
            tag.putString(TAG_NAME, name);
        }
        if (owner != null) {
            tag.put(TAG_OWNER, NbtUtils.createUUID(owner));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_UID, Tag.TAG_INT_ARRAY)) {
            uid = NbtUtils.loadUUID(tag.get(TAG_UID));
        }
        if (tag.contains(TAG_ID)) {
            id = tag.getString(TAG_ID);
            if (id.isEmpty()) {
                id = "empty";
            }
        }
        name = tag.getString(TAG_NAME);
        if (name.isEmpty()) {
            name = "Pocket Waypoint";
        }
        if (tag.contains(TAG_OWNER, Tag.TAG_INT_ARRAY)) {
            owner = NbtUtils.loadUUID(tag.get(TAG_OWNER));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
