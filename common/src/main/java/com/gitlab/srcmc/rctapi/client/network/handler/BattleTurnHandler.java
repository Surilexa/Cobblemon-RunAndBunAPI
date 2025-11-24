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
package com.gitlab.srcmc.rctapi.client.network.handler;

import java.util.stream.Stream;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.gitlab.srcmc.rctapi.client.ModClient;
import com.gitlab.srcmc.rctapi.client.network.packet.BattleTurnPacket;
import com.gitlab.srcmc.rctapi.mixins.client.BattleFaintHandlerMixin;
import com.gitlab.srcmc.rctapi.mixins.client.BattleGUIMixin;
import com.gitlab.srcmc.rctapi.mixins.client.BattleQueueRequestHandlerMixin;

import net.minecraft.client.Minecraft;

public class BattleTurnHandler implements ClientNetworkPacketHandler<BattleTurnPacket> {
    /**
     * End of turn faint softlock 'fix'.
     *
     * Triggered by pokemon fainting at the end of turn on both sides and the player
     * selecting a pokemon to switch in very quickly (tested with 'Perish Song').
     *
     * @see {@link BattleGUIMixin#injectSelectAction}
     * @see {@link BattleFaintHandlerMixin#injectHandle}
     * @see {@link BattleQueueRequestHandlerMixin#injectHandle}
     */
    @Override
    public void handle(BattleTurnPacket arg0, Minecraft arg1) {
        var battle = CobblemonClient.INSTANCE.getBattle();

        if(battle != null && Stream.of(battle.getSides()).anyMatch(s -> s.getActors().stream().anyMatch(a -> a.getType().equals(ActorType.NPC)))) {
            ModClient.BATTLE_STATE.reset();
        }
    }
}
