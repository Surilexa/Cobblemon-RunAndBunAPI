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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.gitlab.srcmc.rctapi.client.network.packet.BattleTurnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleCaptureAction;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.dispatch.BattleDispatch;
import com.cobblemon.mod.common.battles.dispatch.DispatchResult;
import com.cobblemon.mod.common.net.messages.client.battle.BattleSwapPokemonPacket;
import com.gitlab.srcmc.rctapi.api.battle.BattleState;
import com.gitlab.srcmc.rctapi.client.network.packet.BattleDispatchesCompletePacket;
import com.google.common.collect.Streams;

import kotlin.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.minecraft.server.level.ServerPlayer;

@Mixin(PokemonBattle.class)
public abstract class PokemonBattleMixin {
    @Shadow(remap = false)
    abstract BattleFormat getFormat();

    @Shadow(remap = false)
    abstract Iterable<BattleActor> getActors();

    @Shadow(remap = false)
    abstract List<BattleCaptureAction> getCaptureActions();

    @Shadow(remap = false)
    abstract Pair<BattleActor, ActiveBattlePokemon> getActorAndActiveSlotFromPNX(String pnx);

    @Shadow(remap = false)
    abstract boolean checkForfeit();

    @Shadow(remap = false)
    abstract boolean getStarted();

    @Shadow(remap = false)
    abstract ConcurrentLinkedDeque<BattleDispatch> getDispatches();

    @Shadow(remap = false)
    abstract List<Function0<Unit>> getAfterDispatches();

    @Shadow(remap = false)
    abstract DispatchResult getDispatchResult();

    @Shadow(remap = false)
    abstract List<ServerPlayer> getPlayers();

    private boolean $dispatchesComplete;


    @Inject(method = "turn", at = @At("HEAD"), remap = false)
    private void injectTurn(int n, CallbackInfo ci) {
        CobblemonNetwork.INSTANCE.sendPacketToPlayers(this.getPlayers(), new BattleTurnPacket());
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void injectTick(CallbackInfo ci) {
        if(this.getStarted()) {
            if(this.getDispatches().isEmpty() && this.getAfterDispatches().isEmpty() && this.getDispatchResult().canProceed()) {
                if(!this.$dispatchesComplete) {
                    if(BattleState.findFirst((PokemonBattle)(Object)this) != null) {
                        CobblemonNetwork.INSTANCE.sendPacketToPlayers(this.getPlayers(), new BattleDispatchesCompletePacket());
                    }

                    this.$dispatchesComplete = true;
                }
            } else {
                this.$dispatchesComplete = false;
            }
        }
    }

    /**
     * Ignores defeated actors and only sets the request to null
     * for actors that actually have responses.
     */
    @Inject(method = "checkForInputDispatch", at = @At("HEAD"), remap = false, cancellable = true)
    private void injectCheckForInputDispatch(CallbackInfo ci) {
        if(BattleState.findFirst((PokemonBattle)(Object)this) != null) {
            if(this.checkForfeit()) {
                ci.cancel();
                return;
            }

            var actors = Streams.stream(this.getActors()).filter(a -> a.getPokemonList().stream().anyMatch(p -> p.getHealth() > 0)).toList();        
            var readyToInput = actors.stream().anyMatch(a -> !a.getMustChoose() && !a.getResponses().isEmpty()) && actors.stream().noneMatch(a -> a.getMustChoose());

            if(readyToInput && this.getCaptureActions().isEmpty()) {
                actors.stream()
                    .filter(a -> !a.getResponses().isEmpty())
                    .forEach(a -> {
                        a.writeShowdownResponse();
                        a.getResponses().clear();
                        a.setRequest(null);
                    });
            }

            ci.cancel();
        }
    }

    /**
     * Swaps pokemon in pokemonList of actors whenever they swap positions on
     * field (e.g. by 'Ally Switch'). Fixes issues with follow up switch responses.
     * 
     * Note that this is not synchronized with clients. It does not appear to be
     * an issue other than in triple battles. Although 'Ally Switch' appears to
     * cause issues in triples anyway (so I'll leave it at that for now).
     */
    @Inject(method = "sendUpdate", at = @At("HEAD"), remap = false)
    private void injectSendUpdate(NetworkPacket<?> packet, CallbackInfo ci) {
        if(BattleState.findFirst((PokemonBattle)(Object)this) != null) {
            if(packet instanceof BattleSwapPokemonPacket swPacket) {
                if(this.getFormat().component2().getPokemonPerSide() < 3) {
                    var pnxB = swPacket.getPnx(); // positions have already been swapped (so this actually refers to the target)
                    var actorAndSlotB = this.getActorAndActiveSlotFromPNX(pnxB);
                    
                    var actor = actorAndSlotB.component1();
                    var pkmnB = actorAndSlotB.component2();
                    var pkmnA = (ActiveBattlePokemon)pkmnB.getAdjacentAllies().stream().findFirst().get();
                    var actorPkmn = actor.getPokemonList();
                    Collections.swap(actorPkmn, actorPkmn.indexOf(pkmnA.getBattlePokemon()), actorPkmn.indexOf(pkmnB.getBattlePokemon()));
                    // ModCommon.LOG.info(String.format("==> SWAP FIX: pnx: %s, %s <=> %s", pnxB, pkmnA.getBattlePokemon().getName().getString(), pkmnB.getBattlePokemon().getName().getString()));
                } else {
                    // Ally switch in triple battles kinda messes things up.
                    // Applying this 'fix' here does not help the cause.
                    // ModCommon.LOG.info("==> SWAP FIX SKIPPED (too many pokemon per side)");
                }
            }
        }
    }
}
