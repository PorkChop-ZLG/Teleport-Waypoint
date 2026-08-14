package com.zonlong.teleportwaypoint.network;

import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenRenameScreenPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenRenameScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "open_rename_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRenameScreenPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenRenameScreenPayload::pos,
                    OpenRenameScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
