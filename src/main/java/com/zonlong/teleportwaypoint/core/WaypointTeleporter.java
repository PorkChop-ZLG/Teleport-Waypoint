package com.zonlong.teleportwaypoint.core;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Resolves a landing position next to a waypoint (following the Waystones approach) and performs
 * a free, instant, cross-dimension teleport.
 */
public class WaypointTeleporter {

    private record Landing(BlockPos pos, float yaw) {
    }

    public static void teleport(ServerPlayer player, UUID source, UUID target) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        Optional<WaypointRecord> recordOpt = WaypointRegistryData.get(server).get(target);
        if (recordOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("chat.teleportwaypoint.target_missing"));
            return;
        }

        WaypointRecord record = recordOpt.get();
        ServerLevel targetLevel = server.getLevel(record.dimension());
        if (targetLevel == null) {
            player.sendSystemMessage(Component.translatable("chat.teleportwaypoint.invalid_dimension"));
            return;
        }

        if (!(targetLevel.getBlockEntity(record.pos()) instanceof WaypointBlockEntity)) {
            // Target waypoint no longer exists; clean it up.
            WaypointRegistryData.get(server).remove(target);
            player.sendSystemMessage(Component.translatable("chat.teleportwaypoint.target_missing"));
            return;
        }

        Landing landing = findLanding(targetLevel, record.pos());
        player.teleportTo(targetLevel,
                landing.pos().getX() + 0.5,
                landing.pos().getY() + 0.5,
                landing.pos().getZ() + 0.5,
                Set.of(),
                landing.yaw(),
                player.getXRot());

        // Teleport sound and portal particles (like Waystones).
        targetLevel.playSound(null, record.pos(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.5f, 1f);
        targetLevel.sendParticles(player, ParticleTypes.PORTAL, true,
                record.pos().getX() + 0.5, record.pos().getY() + 1.0, record.pos().getZ() + 0.5,
                128, 1.5, 1.5, 1.5, 0.1);
    }

    private static Landing findLanding(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = pos.relative(direction);
            if (hasSpace(level, candidate)) {
                // Face back toward the waypoint.
                return new Landing(candidate, direction.getOpposite().toYRot());
            }
        }
        return new Landing(pos, Direction.SOUTH.toYRot());
    }

    private static boolean hasSpace(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).isSuffocating(level, pos)
                && !level.getBlockState(pos.above()).isSuffocating(level, pos.above());
    }
}
