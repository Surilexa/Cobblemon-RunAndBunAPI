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
package com.gitlab.srcmc.rctapi.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.net.PacketRegisterInfo;
import com.gitlab.srcmc.rctapi.client.network.handler.BattleDispatchesCompleteHandler;
import com.gitlab.srcmc.rctapi.client.network.packet.BattleDispatchesCompletePacket;
import com.gitlab.srcmc.rctapi.client.network.packet.BattleTurnPacket;
import com.gitlab.srcmc.rctapi.client.network.handler.BattleTurnHandler;

@Mixin(CobblemonNetwork.class)
public abstract class CobblemonNetworMixin {
    // borrowed their platform implementations
    @Inject(method = "generateS2CPacketInfoList", at = @At("TAIL"), remap = false)
    private void injectGenerateS2CPacketInfoList(CallbackInfoReturnable<List<PacketRegisterInfo<?>>> cir) {
        cir.getReturnValue().add(new PacketRegisterInfo<>(BattleDispatchesCompletePacket.ID, BattleDispatchesCompletePacket::decode, new BattleDispatchesCompleteHandler(), null));
        cir.getReturnValue().add(new PacketRegisterInfo<>(BattleTurnPacket.ID, buf -> BattleTurnPacket.decode(buf), new BattleTurnHandler(), null));
    }
}
