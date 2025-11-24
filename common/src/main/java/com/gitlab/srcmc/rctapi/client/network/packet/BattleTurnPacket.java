/*
 * This file is part of Radical Cobblemon Trainers API.
 * Copyright (c) 2025, HDainester, All rights reserved.
 *
 * Radical Cobblemon Trainers API is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Radical Cobblemon Trainers API is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along
 * with Radical Cobblemon Trainers API. If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package com.gitlab.srcmc.rctapi.client.network.packet;

import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import kotlin.jvm.functions.Function1;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class BattleTurnPacket implements NetworkPacket<BattleTurnPacket> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("rctapi", "battle_turn");

    public static BattleTurnPacket decode(RegistryFriendlyByteBuf arg0) {
        return new BattleTurnPacket();
    }

    @Override
    public void encode(RegistryFriendlyByteBuf arg0) {
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void sendToAllPlayers() {
        throw new UnsupportedOperationException("Unimplemented method 'sendToAllPlayers'");
    }

    @Override
    public void sendToPlayer(ServerPlayer arg0) {
        CobblemonNetwork.INSTANCE.sendPacketToPlayer(arg0, this);
    }

    @Override
    public void sendToPlayers(Iterable<? extends ServerPlayer> arg0) {
        CobblemonNetwork.INSTANCE.sendPacketToPlayers(arg0, this);
    }

    @Override
    public void sendToPlayersAround(double arg0, double arg1, double arg2, double arg3, ResourceKey<Level> arg4, Function1<? super ServerPlayer, Boolean> arg5) {
        throw new UnsupportedOperationException("Unimplemented method 'sendToPlayersAround'");
    }

    @Override
    public void sendToServer() {
        throw new UnsupportedOperationException("Unimplemented method 'sendToServer'");
    }

    @Override
    public Type<BattleTurnPacket> type() {
        return new CustomPacketPayload.Type<BattleTurnPacket>(this.getId());
    }
}
