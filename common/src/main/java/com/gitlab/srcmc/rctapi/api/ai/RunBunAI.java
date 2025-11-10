/*
 MIT License
Copyright (c) [2025] [Jacob Hooten, Mitchell Mclaughlin]

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.gitlab.srcmc.rctapi.api.ai;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.battles.interpreter.BattleContext;
import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.StatProvider;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.api.types.tera.TeraType;
import com.cobblemon.mod.common.api.types.tera.TeraTypes;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.battles.interpreter.ContextManager;
import com.cobblemon.mod.common.battles.pokemon.BattleMove;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.platform.events.ServerTickEvent;
import com.gitlab.srcmc.rctapi.ModCommon;
import com.gitlab.srcmc.rctapi.api.ai.utils.BattleEffects;
import com.gitlab.srcmc.rctapi.api.ai.utils.BattleStates;
import com.gitlab.srcmc.rctapi.api.ai.utils.PokeMathMax;
import com.gitlab.srcmc.rctapi.api.ai.utils.TypeChart;
import com.gitlab.srcmc.rctapi.api.ai.utils.RBMoveList;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import com.gitlab.srcmc.rctapi.api.trainer.Trainer;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerRegistry;
import dev.architectury.platform.Mod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.PathfinderMob;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.cobblemon.mod.common.api.Priority;
import kotlin.Unit;

public class RunBunAI implements BattleAI {
    private static boolean registered = false;
    public RunBunAI() {
        if(!registered){
            CobblemonEvents.BATTLE_STARTED_PRE.subscribe(
                    Priority.NORMAL,  // ✅ Priority required
                    event -> {
                        System.out.println("Battle is about to start! " +
                                "Players: " + event.getBattle().getPlayers());
                        RunBunAI.setHasResetDefault(false);
                        return Unit.INSTANCE;  // ✅ Kotlin Unit return
                    }
            );

            registered = true;
        }
    }
    private static final Random RANDOM = new Random();
    public static void setHasResetDefault(boolean hasResetDefault) {
        RunBunAI.hasResetDefault = hasResetDefault;
    }
    private static boolean hasResetDefault = false;
    private static int battleTurn = 1;
    private static PokemonBattle pb = null;
    private static int turnsForActivePokemon = 1;
    private static boolean hasUsedMega = false;
    private static boolean hasUsedTera = false;
    private static UUID currentPokemonUUID = null;
    private static boolean switchedLastTurn = false;
    private static Map<Integer,String> moveHistory = new HashMap<>(); //move name, turn used
    private static Map<Integer,String> moveHistoryEnemy = new HashMap<>(); //move name, turn used
    private static Map<Stat,Integer> npcStages = new HashMap<>();
    private static Map<Stat,Integer> opponentStages = new HashMap<>();
    private static Map<BattlePokemon, Boolean> isAlive = new HashMap<>();
    private static final Map<String, String> statIdMap = Map.of(
            "atk", "attack",
            "def", "defence",
            "spa", "special_attack",
            "spd", "special_defence",
            "spe", "speed",
            "eva", "evasion",
            "acc", "accuracy"
    );
    private static final Map<ElementalType, Map<ElementalType, Double>> typeChart = new HashMap<>();
    private static final List<String> priorityDamageMoves = RBMoveList.getPriorityDamageMoves();
    private static final List<String> abilityStatBooster = RBMoveList.getAbilityStatBooster();
    private static final List<String> highCriticalMoves = RBMoveList.getHighCriticalMoves();
    private static final List<String> trapMoves = RBMoveList.getTrapMoves();
    private static final List<String> speedReductionMoves = RBMoveList.getSpeedReductionMoves();
    private static final List<String> physicalAttackReductionMoves = RBMoveList.getPhysicalAttackReductionMoves();
    private static final List<String> specialAttackReductionMoves = RBMoveList.getSpecialAttackReductionMoves();
    private static final List<String> generalSetupMoves = RBMoveList.getGeneralSetupMoves();
    private static final List<String> ignoreStatDropAbilities = RBMoveList.getIgnoreStatDropAbilities();
    private static final List<String> specialFunctionMoves = RBMoveList.getSpecialFunctionMoves();
    private static final List<String> soundMoves = RBMoveList.getSoundMoves();
    private static final List<String> flinchMoves = RBMoveList.getFlinchMoves();
    private static final List<String> thawingMoves = RBMoveList.getThawingMoves();
    private static final List<String> rechargeMoves = RBMoveList.getRechargeMoves();
    private static final List<String> recoveryMoves = RBMoveList.getRecoveryMoves();
    private static final List<String> megaStones = RBMoveList.getMegaStones();
    private static final List<String> ignoreDamageMoves = RBMoveList.getIgnoreDamageMoves();
    private static final List<String> ignoreSleepAbilities = RBMoveList.getIgnoreSleepAbilities();
    private static final List<String> statusMoves = RBMoveList.getStatusMoves();


    @NotNull
    @Override
    public ShowdownActionResponse choose(@NotNull ActiveBattlePokemon activeBattlePokemon, @Nullable ShowdownMoveset moveset, boolean forceSwitch) {

        if(!hasResetDefault){
            hasResetDefault = true;
            currentPokemonUUID = null;
            battleTurn = 1;
            turnsForActivePokemon = 1;
            hasUsedMega = false;
            hasUsedTera = false;
            moveHistory = new HashMap<>(); //move name, turn used
            moveHistoryEnemy = new HashMap<>();
            pb = null;
            switchedLastTurn = false;
            isAlive = new HashMap<>();
        }

        ModCommon.LOG.info("started showdown response.");

        //AI variables
        String currentHeldItem = "";
        String currentAbility = "";
        ElementalType activePrimaryType = null;
        ElementalType activeSecondaryType = null;
        double activePokemonPercentHP = 0;
        double activePokemonCurrentHP = 0;
        ActiveBattlePokemon NPCPartner = null;


        String gimmick = null;
        BattlePokemon battlePokemon = activeBattlePokemon.getBattlePokemon();
        BattleFormat bf = new BattleFormat();
        boolean isDoubles = bf.getBattleType() == BattleTypes.INSTANCE.getDOUBLES();

        //opponent variables
        String getOpponentHeldItem = "";
        ActiveBattlePokemon opponentPartner = null;
        List<Move> oppMoves = new ArrayList<>();
        double oppMaxDamage = 0;
        String opponentAbility = "";
        double oppPercentHP = 0;

        ElementalTypes elementaltypes = ElementalTypes.INSTANCE;
        List<BattlePokemon> NPCParty = activeBattlePokemon.getActor().getPokemonList().stream().toList();

        List<BattlePokemon> aliveParty = activeBattlePokemon.getActor().getPokemonList().stream()
                .filter(BattlePokemon::canBeSentOut)
                .toList();
        Optional<ActiveBattlePokemon> opponentActiveBattlePokemon = StreamSupport.stream(
                        activeBattlePokemon.getAllActivePokemon().spliterator(), false
                )
                .filter(abp -> !abp.isAllied(activeBattlePokemon))
                .findFirst();
        List<ActiveBattlePokemon> allNPCActiveBattlePokemon = StreamSupport.stream(
                        activeBattlePokemon.getAllActivePokemon().spliterator(), false
                )
                .filter(abp -> abp.isAllied(activeBattlePokemon))
                .toList();
        List<ActiveBattlePokemon> allOpponentActiveBattlePokemon = StreamSupport.stream(
                        activeBattlePokemon.getAllActivePokemon().spliterator(), false
                )
                .filter(abp -> !abp.isAllied(activeBattlePokemon))
                .toList();
        //setting up logic for double calcs. works the same for singles anyways.
        int currentBattleSlot = allNPCActiveBattlePokemon.indexOf(activeBattlePokemon) != -1
                ? allNPCActiveBattlePokemon.indexOf(activeBattlePokemon) : 0;
        BattlePokemon opponent = allOpponentActiveBattlePokemon.isEmpty()
                ? null : allOpponentActiveBattlePokemon.get(currentBattleSlot).getBattlePokemon();
        //saving alive NPC pokemon
        for(BattlePokemon saveAliveNPCBattlePokemon : NPCParty){
            if(saveAliveNPCBattlePokemon.getHealth() <= 0){
                isAlive.put(saveAliveNPCBattlePokemon, false);
               // ModCommon.LOG.info("NPC isAlive Status" + saveAliveNPCBattlePokemon.getEffectedPokemon().getDisplayName() + " : false");
            }
            else{
                isAlive.put(saveAliveNPCBattlePokemon, true);
               // ModCommon.LOG.info("NPC isAlive Status" + saveAliveNPCBattlePokemon.getEffectedPokemon().getDisplayName() + " : true");
            }
        }
        if (battlePokemon != null) {
            if(pb == null){
                pb = opponent.getActor().getBattle();
            }
            if(currentPokemonUUID == null){
                currentPokemonUUID = battlePokemon.getUuid();
            }
            else if(currentPokemonUUID != battlePokemon.getUuid()){
                turnsForActivePokemon = 1;
                currentPokemonUUID = battlePokemon.getUuid();
            }
            activePrimaryType = battlePokemon.getEffectedPokemon().getPrimaryType();
            activeSecondaryType = battlePokemon.getEffectedPokemon().getSecondaryType();
            activePokemonPercentHP = getCurrentPercentHP(battlePokemon);
            activePokemonCurrentHP = battlePokemon.getHealth();
            currentAbility = battlePokemon.getEffectedPokemon().getAbility().getDisplayName();
            

            npcStages = getStageMap(battlePokemon);
            if (battlePokemon.getHeldItemManager().showdownId(battlePokemon) != null) {
                currentHeldItem = battlePokemon.getHeldItemManager().showdownId(battlePokemon);
                if(megaStones.contains(currentHeldItem) && turnsForActivePokemon == 1 && !hasUsedMega){
                    gimmick = ShowdownMoveset.Gimmick.MEGA_EVOLUTION.getId();
                    hasUsedMega = true;
                }
                else if(!megaStones.contains(currentHeldItem) && getCurrentPercentHP(battlePokemon) >= 50 && !hasUsedTera && battlePokemon.getEffectedPokemon().getLevel() > 35){
                    gimmick = ShowdownMoveset.Gimmick.TERASTALLIZATION.getId();
                    hasUsedTera = true;
                }
                else{
                    gimmick = null;
                }

                ModCommon.LOG.info("Held item: " + currentHeldItem);
                ModCommon.LOG.info("Pokemon Level: " + battlePokemon.getEffectedPokemon().getLevel());

            }
        }

        ModCommon.LOG.info("Current Battle Turn: "+Integer.toString(battleTurn)
                + "    Turns Since This mon has been on field: " + Integer.toString(RunBunAI.turnsForActivePokemon));

        if(opponent != null){
            opponentStages = getStageMap(opponent);
            getOpponentHeldItem = opponent.getHeldItemManager().showdownId(opponent)!=null
                    ? opponent.getHeldItemManager().showdownId(opponent):"";

            //if the opponent has missed, we have immuned or oppenent has failed an attack.
            for (Map.Entry<UUID, BattleMessage> entry : pb.getMinorBattleActions().entrySet()) {
                BattleMessage msg = entry.getValue();
                String type = msg.getId();
                BattlePokemon mon = msg.battlePokemon(0, pb);
                if ("-miss".equals(type) || "-immune".equals(type) || "-fail".equals(type)) {
                    if (mon.getUuid().equals(opponent.getUuid())) {
                        moveHistoryEnemy.put(Math.max(battleTurn - 1, 1), type);
                    }
                }
            }
            //Tracks the move history of the opponent pokemon
            for (Map.Entry<UUID, BattleMessage> entry : pb.getMajorBattleActions().entrySet()) {
                BattleMessage msg = entry.getValue();
                String type = msg.getId();
                BattlePokemon mon = msg.battlePokemon(0, pb);
                if ("move".equals(type) && mon.getUuid() == opponent.getUuid()) {
                    String moveName = msg.moveAt(1).getName() != null ? msg.moveAt(1).getName() : null;
                    moveHistoryEnemy.put(Math.max(battleTurn-1,1),moveName);
                }
                else if("move".equals(type) && mon.getUuid() == currentPokemonUUID){
                    String moveName = msg.moveAt(1).getName() != null ? msg.moveAt(1).getName() : null;
                    moveHistory.put(Math.max(turnsForActivePokemon-1,1),moveName);
                }
            }
            for (Map.Entry<Integer, String> entry : moveHistoryEnemy.entrySet()) {
                String moveName = entry.getValue();
                int turn = entry.getKey();
                ModCommon.LOG.info("Turn: " + turn + "   ::   ENEMY used move " + moveName);
            }
        }

        if(isDoubles){
            int partnerSlot = (currentBattleSlot == 0 ? 1 : 0);
            boolean hasHex = false;
            opponentPartner = allOpponentActiveBattlePokemon.get(partnerSlot);
            NPCPartner = allNPCActiveBattlePokemon.get(partnerSlot);

        }
        String NPCPartnerAbility = NPCPartner != null ? NPCPartner.getBattlePokemon().getEffectedPokemon().getAbility().getDisplayName() : "";
        String NPCPartnerHeldItem = NPCPartner != null && NPCPartner.getBattlePokemon().getHeldItemManager().showdownId(NPCPartner.getBattlePokemon()) != null
                ? NPCPartner.getBattlePokemon().getHeldItemManager().showdownId(NPCPartner.getBattlePokemon()) : "";

        boolean oppHasSpecialMove = false;
        boolean oppHasPhysicalMove = false;
        if(opponent != null){
            oppMoves = opponent.getMoveSet().getMoves();

            if(battlePokemon != null){
                for(Move m : oppMoves){
                    if(PokeMathMax.damage(opponent, battlePokemon, m, opponentStages, npcStages) > oppMaxDamage){
                        oppMaxDamage = PokeMathMax.damage(opponent, battlePokemon, m, opponentStages, npcStages);
                    }
                }
            }
            opponentAbility = opponent.getEffectedPokemon().getAbility().getDisplayName();
            oppPercentHP = getCurrentPercentHP(opponent);//this is rounded up


            String damageCategory ="";

            for(Move opponentMove : oppMoves){
                damageCategory = opponentMove.getDamageCategory().getName();
                if(damageCategory.equals(DamageCategories.INSTANCE.getPHYSICAL().getName())){
                    oppHasPhysicalMove = true;
                }
                if(damageCategory.equals(DamageCategories.INSTANCE.getSPECIAL().getName())){
                    oppHasSpecialMove = true;
                }
            }
        }


        //TODO: SWITCHING SCORING LOGIC STARTS HERE ===================================================================
        List<BattlePokemon> canSwitchTo = new ArrayList<>();
        for(Map.Entry<BattlePokemon, Boolean> entry : isAlive.entrySet()){
            if(entry.getValue() == true && entry.getKey() != battlePokemon){
                canSwitchTo.addLast(entry.getKey());
                ModCommon.LOG.info("Can Switch To " + entry.getKey().getEffectedPokemon().getDisplayName());
            }
        }
        Map<BattlePokemon, Integer> switchingScores = new HashMap<>();
        int switchScore = 0;
        boolean isSwitchMonFaster = false;
        boolean doesSwitchOHKO = false;
        boolean doesOppOHKO = false;
        BattlePokemon nextPokemon = null;

        for(BattlePokemon possibleSwitch : canSwitchTo){
            switchScore = 0;
            if(battlePokemon!=null){
                if(BattleEffects.Field.Room.trickroom(battlePokemon)){
                    isSwitchMonFaster = getInBattleSpeed(possibleSwitch) <= getInBattleSpeed(opponent);
                }
                else{
                    isSwitchMonFaster = getInBattleSpeed(possibleSwitch) >= getInBattleSpeed(opponent);
                }
            }
            doesSwitchOHKO = isOHKO(possibleSwitch.getMoveSet().getMoves(), possibleSwitch, opponent, npcStages, opponentStages);
            doesOppOHKO = isOHKO(oppMoves, opponent, possibleSwitch, opponentStages, npcStages);
            if(isSwitchMonFaster && doesSwitchOHKO){
                switchScore += 5;
            }
            //we are slower, we kill opp, opp does not kill us
            else if(!isSwitchMonFaster && !doesOppOHKO && doesSwitchOHKO){
                switchScore += 4;
            }
            // we are faster, deal more damage than we take. (dmg is the percent change not raw number)
            else if(isSwitchMonFaster && highestPercentDamageMove(possibleSwitch, opponent) > highestPercentDamageMove(opponent,possibleSwitch)){
                switchScore += 3;
            }
            else if(!isSwitchMonFaster && highestPercentDamageMove(possibleSwitch, opponent) > highestPercentDamageMove(opponent,possibleSwitch)){
                switchScore += 2;
            }
            else if(isSwitchMonFaster){
                switchScore += 1;
            }
            else if(!isSwitchMonFaster && doesOppOHKO){
                switchScore += -1;
            }
            if(possibleSwitch.getName().equals("ditto")){
                switchScore += 2;
            }
            if(isSwitchMonFaster && !doesOppOHKO){
                if(possibleSwitch.getName().equals("wynaut")
                    || possibleSwitch.getName().equals("wobbuffet")) {
                    switchScore += 2;
                }
            }
            switchingScores.put(possibleSwitch, switchScore);
            ModCommon.LOG.info(possibleSwitch.getOriginalPokemon().getDisplayName().toString() + "  " + Integer.toString(switchScore));
        }
        //filter who has the best switch score
        int maxSwitchingScore = switchingScores.values()
                .stream()
                .max(Integer::compareTo)
                .orElse(Integer.MIN_VALUE);

        List<BattlePokemon> bestSwitches = switchingScores.entrySet()
                .stream()
                .filter(entry -> entry.getValue() == maxSwitchingScore)
                .map(Map.Entry::getKey)
                .toList();


        if (!bestSwitches.isEmpty()) {
            nextPokemon = bestSwitches.getFirst();
        }
        if (forceSwitch || activeBattlePokemon.isGone()) {
            if (canSwitchTo.isEmpty()){
                return PassActionResponse.INSTANCE;
            }
            if (opponent==null) {
                if (!canSwitchTo.isEmpty()) {
                    nextPokemon = bestSwitches.getFirst();
                }
                nextPokemon.setWillBeSwitchedIn(true);
                moveHistory = new HashMap<>();
                switchedLastTurn = true;
                return new SwitchActionResponse(nextPokemon.getUuid());
            }
            if (nextPokemon == null) {
                if (!canSwitchTo.isEmpty()) {
                    nextPokemon = bestSwitches.getFirst();
                } else {
                    return PassActionResponse.INSTANCE; // no Pokémon to switch to
                }
            }
            nextPokemon.setWillBeSwitchedIn(true);
            //resetting the move history for the next mon;
            moveHistory = new HashMap<>();
            switchedLastTurn = true;
            return new SwitchActionResponse(nextPokemon.getUuid());
        }

        if (moveset == null) return PassActionResponse.INSTANCE;
        if (moveset.moves.size() == 1 && moveset.moves.get(0).getId().equals("recharge")) {
            changeTurn(battlePokemon);
            return new MoveActionResponse("recharge", null, gimmick);
        }

        //TODO: START OF MOVE MAPPING LOGIC ===========================================================================

        List<InBattleMove> inBattleMoves = moveset.moves.stream()
                .filter(InBattleMove::canBeUsed)
                .filter(inBattleMove -> {
                    List<Targetable> targetList = inBattleMove.getTarget().getTargetList().invoke(activeBattlePokemon);
                    return inBattleMove.mustBeUsed() || targetList == null || !targetList.isEmpty();
                }).toList();

        if (inBattleMoves.isEmpty()) return new MoveActionResponse("struggle", null, gimmick);
        if (opponentActiveBattlePokemon.isEmpty()) {
            changeTurn(battlePokemon);
            return new MoveActionResponse(
                    inBattleMoves.get(RANDOM.nextInt(moveset.moves.size())).id, null, gimmick
            );
        }

        if (opponent == null) {
            return new MoveActionResponse(
                    inBattleMoves.get(RANDOM.nextInt(moveset.moves.size())).id, null, gimmick
            );
        }

        Map<InBattleMove, Move> moveMap = new HashMap<>();
        IntStream.range(0, inBattleMoves.size())
                .forEach(i -> moveMap.put(
                        inBattleMoves.get(i),
                        Moves.INSTANCE.all().stream().filter(move -> move.getName().equals(inBattleMoves.get(i).getId()))
                                .findFirst().get().create()
                ));
        Map<InBattleMove, Integer> moveDamages = new HashMap<>();

        inBattleMoves.forEach(inBattleMove -> {
            int dmg = PokeMathMax.damage(
                    battlePokemon,
                    opponent,
                    moveMap.get(inBattleMove),
                    npcStages, opponentStages
            );
            if(ignoreDamageMoves.contains(inBattleMove.getId()) || trapMoves.contains(inBattleMove.getId())){
                moveDamages.put(inBattleMove, 0);
            }
            else{
                moveDamages.put(inBattleMove, dmg);
            }

            ModCommon.LOG.info(inBattleMove.getId() + "     DAMAGE = " + Integer.toString(dmg));
        });

        List<InBattleMove> killingMoves = new ArrayList<>();
        moveDamages.forEach((move, damage) -> {
            if (damage >= opponent.getHealth()){
                killingMoves.add(move);
            }
        });
        Map<InBattleMove, Integer> moveScores = new HashMap<>();
        boolean isFaster = false;

        if(battlePokemon != null){
            if(BattleEffects.Field.Room.trickroom(battlePokemon)){
                isFaster = getInBattleSpeed(battlePokemon) <= getInBattleSpeed(opponent);
            }
            else{
                isFaster = getInBattleSpeed(battlePokemon) >= getInBattleSpeed(opponent);
            }
        }
        boolean npcIsOHKO = isOHKO(oppMoves, opponent, battlePokemon, opponentStages, npcStages);
        boolean npcIs2OHKO = is2HKO(oppMoves,opponent,battlePokemon, opponentStages, npcStages);
        boolean npcIs3OHKO = is3HKO(oppMoves, opponent, battlePokemon, opponentStages, npcStages);
        boolean npcIsOHKOWithSS = wouldBeOHKOAfterShellSmash(oppMoves, opponent, battlePokemon, opponentStages, npcStages);
        boolean npcIsOHKOWithBD = isOHKOAfterBellyDrum(oppMoves, opponent, battlePokemon, opponentStages, npcStages);

        //making list of highestest dmg nonkilling moves, and adding special cases.
        List<InBattleMove> nonKillingPossibleMoves = new ArrayList<>();
        int maxDamage = 0;
        InBattleMove maxMove = null;
        if (killingMoves.isEmpty()) {
            for (int dmg : moveDamages.values()) {
                maxDamage = maxDamage >= dmg ? maxDamage : dmg;
            }
            for (Map.Entry<InBattleMove, Integer> entry : moveDamages.entrySet()) {
                String name = entry.getKey().getId().toLowerCase(Locale.ROOT).trim();
                if (trapMoves.contains(name)
                        || physicalAttackReductionMoves.contains(name)
                        || specialAttackReductionMoves.contains(name)
                        || speedReductionMoves.contains(name)
                        || specialFunctionMoves.contains(name)
                        || generalSetupMoves.contains(name)){
                    nonKillingPossibleMoves.add(entry.getKey());
                }
                else if (entry.getValue() == maxDamage) {
                    nonKillingPossibleMoves.add(entry.getKey());
                    maxMove = entry.getKey();
                }
            }
        }
        //TODO: START OF THE MOVE DAMAGE SCORING CALCULATIONS==========================================================
        for (var move : moveDamages.entrySet()) {
            InBattleMove currentMove = move.getKey();
            int moveDamage = move.getValue();
            int score = 0;

            //useless move because of abilities
            if (TypeChart.getEffectiveness(TypeChart.getMove(currentMove).getType(), opponent) == 0) {
                moveScores.put(currentMove, -20);
                continue;
            }

            //if the damage move is in the killing list.
            if (killingMoves.contains(currentMove)) {
                double roll = RANDOM.nextDouble();
                score = (roll > 0.2) ? 6 : 8;

                if (isFaster) {
                    //We are faster or speed tied and we see a kill with this move. (+12 (80%), +14 (20%))
                    score += 6;
                }
                //if we are slower than the opponent and we see a kill with priority. (+6)
                else {
                    if (priorityDamageMoves.contains(currentMove.getId())) {
                        score += 6;
                    }
                    //if we are slower and see a kill without priority
                    else {
                        score += 3;
                    }
                }
                if(currentMove.getId().equals("pursuit")){
                    score = 10;
                }
                //todo: when meloetta uses relic song she transforms to pirouette form.
                // (we want to keep track of if she has used relic song already)
            }
            //highest damaging move, speed reduction moves, ATK/SpATK reduction moves, Trap Moves (6 , 8)
            //if the move is a non-killing move.
            if (!nonKillingPossibleMoves.isEmpty()) {
                if(nonKillingPossibleMoves.contains(currentMove)){
                    String moveID = currentMove.getId();
                    double roll = RANDOM.nextDouble();

                    //This puts the final score into the map with its move key.
                    if(maxMove != null){
                        if(maxMove == currentMove){
                            roll = RANDOM.nextDouble();
                            score += roll > .2 ? 6:8;
                        }
                    }

                    if (trapMoves.contains(moveID)) {
                        score += (roll > 0.2) ? 6 : 8;
                    }
                    //if the move is a speed reducing move.
                    if (speedReductionMoves.contains(moveID)) {
                        if (moveDamages.getOrDefault(currentMove, 0) == maxDamage) {
                            roll = RANDOM.nextDouble();
                            score += (roll > 0.2) ? 6 : 8;
                        }
                        //the ai is not faster and the enemy mon can be reduced.
                        else {
                            score += !ignoreStatDropAbilities.contains(opponentAbility) && !isFaster ? 6 : 5;
                        }
                    }
                    if (physicalAttackReductionMoves.contains(moveID) || specialAttackReductionMoves.contains(moveID)) {
                        if (moveDamages.getOrDefault(currentMove,0) == maxDamage) {
                            roll = RANDOM.nextDouble();
                            score += (roll > 0.2) ? 6 : 8;
                        } else if(!ignoreStatDropAbilities.contains(opponentAbility)){
                            if(specialAttackReductionMoves.contains(moveID) && oppHasSpecialMove){
                                score += 6;
                            }
                            else if(physicalAttackReductionMoves.contains(moveID) && oppHasPhysicalMove){
                                score+=6;
                            }
                        }
                        else if(ignoreStatDropAbilities.contains(opponentAbility)){
                            score+=5;
                        }
                    }

                    boolean isOPFrozen = BattleEffects.Pokemon.Status.frz(opponent);
                    boolean isOPSleeping = BattleEffects.Pokemon.Status.slp(opponent);
                    boolean isFirstTurnOut = turnsForActivePokemon == 1;
                    String moveUsedLastTurn = moveHistory.getOrDefault(turnsForActivePokemon-1,"");
                    String moveUsed2TurnsAgo = moveHistory.getOrDefault(turnsForActivePokemon-2,"");
                    String moveUsed3TurnsAgo = moveHistory.getOrDefault(turnsForActivePokemon-3,"");
                    //if contains in special list then do special functionality bellow.
                    if (specialFunctionMoves.contains(moveID)) {
                        switch (moveID){
                            case "futuresight":
                                // If AI is faster than target and is KO’d by target
                                score += isFaster && npcIsOHKO ? 8 : 6;
                                break;
                            case "relicsong":
                                // If in Meloetta base form
                                score += battlePokemon.getName().equals("meloetta") ? 10 : 0;
                                break;
                            case "suckerpunch", "thunderclap":
                                if(moveUsedLastTurn.equals("suckerpunch") || moveUsedLastTurn.equals("thunderclap")){
                                    score += roll < .5 ? -20 : 0;
                                }
                                break;
                            case "pursuit":
                                if(oppPercentHP <= 20){
                                    score+=10;
                                }
                                else if(oppPercentHP <= 40){
                                    roll = RANDOM.nextDouble();
                                    score += roll < .5 ? 8 : 0;
                                }
                                break;
                            case "fellstinger":
                                roll = RANDOM.nextDouble();
                                int result1 = roll > .8 ? 23 : 21;
                                int result2 = roll > .8 ? 17 : 15;

                                if(battlePokemon.getStatChanges().get(Stats.ATTACK) != 6){
                                    score += isFaster ? result1 : result2;
                                }
                                break;
                            case "rollout":
                                score += 7;
                                break;
                            case "stealthrock":
                                roll = RANDOM.nextDouble();
                                if(isFirstTurnOut && getHazardCount(moveHistory, "stealthrock") ==0){
                                    score += roll > .75 ? 8 : 9;
                                }
                                else{
                                    score += roll > .75 ? 6 : 7;
                                }
                                //if first turn out\
                                if(getHazardCount(moveHistory, "stealthrock") !=0){
                                    score += -20;
                                }
                                //else
                                break;
                            case "spikes":
                                roll = RANDOM.nextDouble();
                                int spikesCount = getHazardCount(moveHistory, "spikes");
                                ModCommon.LOG.info("Spikes Count = " + spikesCount);
                                if(isFirstTurnOut){
                                    score += roll > .75 ? 8 : 9;
                                }
                                else{
                                    score += roll > .75 ? 6 : 7;
                                }
                                if(spikesCount == 3){
                                    score += -20;
                                }
                                else if(spikesCount > 0){
                                    score --;
                                }
                                break;
                            case "toxicspikes":
                                roll = RANDOM.nextDouble();
                                int toxicspikesCount = getHazardCount(moveHistory, "toxicspikes");
                                if(isFirstTurnOut){
                                    score += roll > .75 ? 8 : 9;
                                }
                                else{
                                    score += roll > .75 ? 6 : 7;
                                }
                                if(toxicspikesCount == 3){
                                    score += -20;
                                }
                                else if(toxicspikesCount > 0){
                                    score --;
                                }
                                break;
                            case "stickyweb":
                                roll = RANDOM.nextDouble();
                                if(isFirstTurnOut){
                                    score+= roll > .75 ? 9:12;
                                }
                                else{
                                    score += roll > .75 ? 6:9;
                                }
                                if(getHazardCount(moveHistory, "stickyweb") !=0){
                                    score += -20;
                                }
                                break;
                            case "kingsshield", "protect", "spikyshield", "silktrap","detect","banefulbunker","burningbulwark", "obstruct":
                                score += 6;
                            //todo: still needs to see parish song
                                if(BattleEffects.Pokemon.Volatile.cursed(battlePokemon)
                                || BattleEffects.Pokemon.Volatile.yawn(battlePokemon)
                                || BattleEffects.Pokemon.Volatile.leech(battlePokemon)
                                || BattleEffects.Pokemon.Volatile.attract(battlePokemon)
                                || BattleEffects.Pokemon.Status.brn(battlePokemon)
                                || BattleEffects.Pokemon.Status.psn(battlePokemon)
                                ||BattleEffects.Pokemon.Status.tox(battlePokemon)){
                                    if(activePokemonPercentHP <= 25){
                                        score += -20;
                                    }
                                    score += -2;
                                }
                                if((BattleEffects.Pokemon.Volatile.cursed(opponent)
                                        || BattleEffects.Pokemon.Volatile.yawn(opponent)
                                        || BattleEffects.Pokemon.Volatile.leech(opponent)
                                        || BattleEffects.Pokemon.Volatile.attract(opponent)
                                        || BattleEffects.Pokemon.Status.brn(opponent)
                                        || BattleEffects.Pokemon.Status.psn(opponent)
                                        ||BattleEffects.Pokemon.Status.tox(opponent))){
                                    score++;
                                }
                                if (isSandstormFatal(battlePokemon, activePrimaryType, activeSecondaryType, activePokemonPercentHP)) {
                                    score -= 20;
                                }
                                // If it's AI mon's first turn out and it is not a double battle:
                                if(!isDoubles && isFirstTurnOut){
                                    score--;
                                }
                                if(!moveHistory.isEmpty()){
                                    if(moveUsed2TurnsAgo == moveID && moveUsedLastTurn == moveID){
                                        score += -20;
                                    }
                                    else if(moveUsed2TurnsAgo != moveID && moveUsedLastTurn == moveID){
                                        score += roll > .5 ? -20 : 0;
                                    }
                                }
                                break;
                            case "fling":
                                if(currentHeldItem != null){
                                    break;
                                }
                                double flingEffectiveness = TypeChart.getEffectiveness(TypeChart.getMove(currentMove).getType(), opponent);
                                //need to hold a salac berry and fling is not super effective.
                                if(battlePokemon.getEffectedPokemon().heldItem().is(CobblemonItems.SALAC_BERRY)
                                        && (flingEffectiveness <=1)){
                                    score+=9;
                                }
                                //todo: flame orb toxic orb AI??
                                break;
                            case "roleplay":
                                Set<String> RPAbilities = Set.of("hugepower", "purepower", "protean", "toughclaws");
                                if(!RPAbilities.contains(currentAbility) && RPAbilities.contains(NPCPartnerAbility)){
                                    score += 9;
                                }
                                else{
                                    score += -20;
                                }
                                break;
                            case "shadowsneak", "aquajet", "iceshard":
                                if(NPCPartnerHeldItem.equals("weaknesspolicy")){
                                    if(TypeChart.getEffectiveness(currentMove, NPCPartner.getBattlePokemon()) > 1){
                                        score = 12;
                                    }
                                }
                                break;
                            case "magnitude", "earthquake"://todo doubles
                                break;
                            case "imprison":
                                int commonMoves = 0;
                                for(InBattleMove imprisonSet : moveDamages.keySet()){
                                    if(oppMoves.contains(TypeChart.getMove(imprisonSet)))
                                    {
                                        commonMoves ++;
                                    }
                                }
                                score+= commonMoves > 0 ? 9 : -20;
                                break;
                            case "batonpass":
                                if (aliveParty.isEmpty()){
                                    score += -20;
                                    break;
                                }
                                var statChanges = battlePokemon.getStatChanges();
                                var anyPositive = false;
                                for(Integer value : statChanges.values()){
                                    if(value > 0){
                                        anyPositive = true;
                                    }
                                }
                                //todo: find out when substitute is active.
                                if(!aliveParty.isEmpty() && anyPositive){
                                    score += 14;
                                }
                                break;
                            case "tailwind":
                                if(isPartySlowerThanOpponent(allNPCActiveBattlePokemon,allOpponentActiveBattlePokemon)){
                                    score+=9;
                                }
                                else{
                                    score+=5;
                                }

                                break;
                            case "trickroom":
                                if(isPartySlowerThanOpponent(allNPCActiveBattlePokemon,allOpponentActiveBattlePokemon)){
                                    score+=10;
                                }
                                else{
                                    score+=5;
                                }
                                if(BattleEffects.Field.Room.trickroom(battlePokemon)){
                                    score += -20;
                                }
                                break;
                            case "fakeout":
                                if((opponentAbility.equals("shielddust") || opponentAbility.equals("innerfocus") || getOpponentHeldItem.equals("covertcloak")) && isFirstTurnOut){
                                    score += 9;
                                }
                                break;
                            case "helpinghand":
                                //todo:AI will not use either of these moves if their partner is also using this move, or their partner is
                                //   using a Status move
                                if(currentBattleSlot == 1){
                                    //we are looking at our partners move choice
                                }
                                break;
                            case "finalgambit":
                                if(isFaster && battlePokemon.getHealth() >= opponent.getHealth()){
                                    score += 8;
                                } 
                                else if(isFaster && npcIsOHKO){
                                    score += 7;
                                }
                                else{
                                    score += 6;
                                }
                                break;
                            case "electricterrain":
                                if (!BattleEffects.Field.Terrain.electricterrain(battlePokemon)) {
                                    if(currentHeldItem != null && currentHeldItem.equals("terrainextender")){
                                        score+=9;
                                    }
                                    else{
                                        score+=8;
                                    }
                                }
                                break;
                            case "psychicterrain":
                                if (!BattleEffects.Field.Terrain.psychicterrain(battlePokemon)) {
                                    if(currentHeldItem != null && currentHeldItem.equals("terrainextender")){
                                        score+=9;
                                    }
                                    else{
                                        score+=8;
                                    }
                                }
                                break;
                            case "grassyterrain":
                                if (!BattleEffects.Field.Terrain.grassyterrain(battlePokemon)) {
                                    if(currentHeldItem != null && currentHeldItem.equals("terrainextender")){
                                        score+=9;
                                    }
                                    else{
                                        score+=8;
                                    }
                                }
                                break;
                            case "mistyterrain":
                                if (!BattleEffects.Field.Terrain.mistyterrain(battlePokemon)) {
                                    if(currentHeldItem != null && currentHeldItem.equals("terrainextender")){
                                        score+=9;
                                    }
                                    else{
                                        score+=8;
                                    }
                                }
                                break;
                            case "raindance":
                                if (!BattleEffects.Field.Weather.rain(battlePokemon) && !BattleEffects.Field.Weather.heavyrain(battlePokemon)) {
                                    if(currentHeldItem != null && currentHeldItem.equals("damprock")){
                                        score+=9;
                                    }
                                    else{
                                        score+=8;
                                    }
                                }
                                break;
                                
                            case "sunnyday":
                                if (!BattleEffects.Field.Weather.harshsunlight(battlePokemon) && !BattleEffects.Field.Weather.extremelyharshsunlight(battlePokemon)) {
                                    if(currentHeldItem != null && currentHeldItem.equals("heatrock")){
                                        score+=9;
                                    }
                                    else{
                                        score+=8;
                                    }
                                }
                                break;

                            case "hail":
                                if (!BattleEffects.Field.Weather.hail(battlePokemon)) {
                                    if(currentHeldItem != null && currentHeldItem.equals("icyrock")){
                                        score+=9;
                                    }
                                    else{
                                        score+=8;
                                    }
                                }
                                break;

                            case "chillyreception":
                                if (!BattleEffects.Field.Weather.snow(battlePokemon) || (npcIsOHKO && isFaster)) {
                                    if(currentHeldItem != null && currentHeldItem.equals("icyrock")){
                                        score+=9;
                                    }
                                    else{
                                        score+=8;
                                    }
                                }
                                break;

                            case "sandstorm":
                                if (!BattleEffects.Field.Weather.sandstorm(battlePokemon)) {
                                    if(currentHeldItem != null && currentHeldItem.equals("smoothrock")){
                                        score+=9;
                                    }
                                    else{
                                        score+=8;
                                    }
                                }
                                break;

                            case  "snowscape":
                                if (!BattleEffects.Field.Weather.snow(battlePokemon)) {
                                    if(currentHeldItem != null && currentHeldItem.equals("terrainextender")){
                                        score+=9;
                                    }
                                    else{
                                        score+=8;
                                    }
                                }
                                break;

                            case "lightscreen":
                                roll = RANDOM.nextDouble();
                                score += 6;
                                boolean lightclayLS = currentHeldItem.equals("lightclay");
                                if(lightclayLS){
                                    if(getIsMoveUp(moveID, moveHistory, 8, battleTurn)){
                                        score = -20;
                                    }
                                }
                                else{
                                    if(getIsMoveUp(moveID, moveHistory, 5, battleTurn)){
                                        score = -20;
                                    }
                                }
                                if(oppHasSpecialMove){
                                    if(lightclayLS){
                                        score += 1;
                                    }
                                    score += roll > .5 ? 1: 0;
                                }
                                break;

                            case "reflect":
                                roll = RANDOM.nextDouble();
                                score += 6;
                                boolean lightclayR = currentHeldItem.equals("lightclay");
                                if(lightclayR){
                                    if(getIsMoveUp(moveID, moveHistory, 8, battleTurn)){
                                        score = -20;
                                    }
                                }
                                else{
                                    if(getIsMoveUp(moveID, moveHistory, 5, battleTurn)){
                                        score = -20;
                                    }
                                }
                                if(oppHasPhysicalMove){
                                    if(lightclayR){
                                        score += 1;
                                    }
                                    score += roll > .5 ? 1: 0;
                                }
                                break;

                            case "substitute":
                                roll = RANDOM.nextDouble();
                                score +=6;
                                if(BattleEffects.Pokemon.Status.slp(opponent)){
                                    score += 2;
                                }
                                if(BattleEffects.Pokemon.Volatile.leech(opponent)){
                                    score += 2;
                                }
                                if (oppMoves.contains(soundMoves)) {
                                    score += -8;
                                }
                                if(activePokemonPercentHP <= 50 || opponentAbility.equals("infiltrator")){
                                    score = -20;
                                }
                                score -= roll > .5 ? 1: 0;
                                break;
                            case "explosion", "selfdestruct", "mistyexplosion":
                                roll = RANDOM.nextDouble();

                                if(!aliveParty.isEmpty() || (allOpponentActiveBattlePokemon.isEmpty() && aliveParty.isEmpty())){    
                                    if(activePokemonPercentHP < 10){
                                        score += 10;
                                    }
                                    else if(activePokemonPercentHP < 33) {
                                        score += roll > .3 ? 8 : 0;
                                    }
                                    else if(activePokemonPercentHP < 66){
                                        score += roll > .5 ? 7 : 0;
                                    }
                                    else{
                                        score += roll > .95 ? 7 : 0;
                                    }
                                    if (aliveParty.isEmpty()){
                                        score += -1;
                                    }
                                }
                                break;

                            case "memento":
                                if(!aliveParty.isEmpty()){
                                    roll = RANDOM.nextDouble();
                                    if(activePokemonPercentHP < 10){
                                        score += 16;
                                    }
                                    else if(activePokemonPercentHP < 33){
                                        score += roll > .3 ? 14 : 6;
                                    }
                                    else if(activePokemonPercentHP < 66){
                                        score += roll > .5 ? 13 : 6;
                                    }
                                    else{
                                        score += roll > .05 ? 13 : 6;
                                    }
                                }
                                break;

                            case "thunderwave", "stunspore", "glare", "nuzzle", "zapcannon":
                                if(!BattleEffects.Pokemon.Status.any(opponent)){
                                    roll = RANDOM.nextDouble();
                                    int paraRoll = roll > .5 ? -1: 0;
                                    boolean fasterIfPara = false;
                                    boolean hasFlinchMove = moveDamages.entrySet().stream()
                                            .anyMatch(entry -> flinchMoves.contains(entry.getKey().getId()) && entry.getValue() > 0);

                                    if(getInBattleSpeed(opponent)/4 < getInBattleSpeed(battlePokemon)){
                                        fasterIfPara = true;
                                    }
                                    if((!isFaster && fasterIfPara) 
                                        || hasMove(battlePokemon, "hex")
                                        || hasFlinchMove 
                                        || BattleEffects.Pokemon.Volatile.attract(opponent) 
                                        || BattleEffects.Pokemon.Volatile.confusion(opponent)){
                                        score += 8;
                                    }
                                    else{
                                        score += 7;
                                    }
                                    score += paraRoll;
                                }

                                break;

                            case "willowisp":
                                if(!BattleEffects.Pokemon.Status.any(opponent)){
                                    score += 6;
                                    roll = RANDOM.nextDouble();
                                    if(roll < .37){
                                        if(hasMove(battlePokemon,"hex")
                                        || (isDoubles && hasMove(NPCPartner.getBattlePokemon(),"hex"))){
                                            score += 1;
                                        }
                                        if(oppHasPhysicalMove){
                                            score += 1;
                                        }
                                    }
                                }
                                break;

                            case "trick", "switcheroo":
                                if(currentHeldItem != null){
                                    if(currentHeldItem.equals("toxicorb") || currentHeldItem.equals("flameorb") || currentHeldItem.equals("blacksludge")){
                                        roll = RANDOM.nextDouble();
                                        score += roll > .5 ? 6: 7;
                                    }
                                    else if (currentHeldItem.equals("ironball") || currentHeldItem.equals("laggingtail") || currentHeldItem.equals("stickybarb")) {
                                        score += 7;
                                    }else{
                                        score += 5;
                                    }
                                }
                                
                                break;

                            case "yawn", "darkvoid", "sleeppowder", "hypnosis", "lovelykiss", "spore", "grasswhistle":
                                score += 6;
                                roll = RANDOM.nextDouble();
                                boolean oppPartnerHasFlowerVeil = opponentPartner.getBattlePokemon().getEffectedPokemon().getAbility().equals("flowerveil");
                                boolean oppGrass = opponent.getEffectedPokemon().getPrimaryType() == elementaltypes.getGRASS()
                                                || opponent.getEffectedPokemon().getSecondaryType() == elementaltypes.getGRASS();

                                if(!BattleEffects.Pokemon.Status.any(opponent)) {
                                    if (roll < .25) {
                                        if ((!BattleEffects.Field.Terrain.mistyterrain(opponent) || (com.gitlab.srcmc.rctapi.api.ai.utils.BattleEffects.Field.Terrain.mistyterrain(opponent) && (opponent.getEffectedPokemon().getPrimaryType() == elementaltypes.getFLYING() || opponent.getEffectedPokemon().getSecondaryType() == elementaltypes.getFLYING())))
                                                && (!BattleEffects.Field.Terrain.electricterrain(opponent) || (BattleEffects.Field.Terrain.electricterrain(opponent) && (opponent.getEffectedPokemon().getPrimaryType() == elementaltypes.getFLYING() || opponent.getEffectedPokemon().getSecondaryType() == elementaltypes.getFLYING())))
                                                && !ignoreSleepAbilities.contains(opponentAbility)
                                                && (((move.getKey().getId().equals("hypnosis") || move.getKey().getId().equals("spore")) && !opponentAbility.equals("magicbounce")))
                                                && (((move.getKey().getId().equals("grasswhistle") || move.getKey().getId().equals("sing")) && !opponentAbility.equals("soundproof")))
                                                && (!opponentAbility.equals("leafguard") || (opponentAbility.equals("leafguard") && (!BattleEffects.Field.Weather.harshsunlight(opponent) && !BattleEffects.Field.Weather.extremelyharshsunlight(opponent))))
                                                && (!isDoubles || (isDoubles && (!oppGrass || (!oppPartnerHasFlowerVeil && oppGrass))))) {
                                            score += 1;
                                            if ((hasMove(battlePokemon, "dreameater") || hasMove(battlePokemon, "nightmare") && (!hasMove(opponent, "snore") || !hasMove(opponent, "sleeptalk")))) {
                                                score += 1;
                                            }
                                            if (isDoubles && hasMove(NPCPartner.getBattlePokemon(), "hex")) {
                                                score += 1;
                                            }
                                        }
                                    }
                                }
                                break;
                            case "poisongas", "poisonpowder", "toxic":
                                score += 6;
                                roll = RANDOM.nextDouble();
                                boolean hasDamagingMoves = false;
                                boolean hasCertainMove = false;
                                //38% of the time and we cannot kill the enemy pokemon
                                if(roll < .38 && killingMoves.isEmpty()){
                                    if(!BattleEffects.Pokemon.Status.any(opponent) && getCurrentPercentHP(opponent) > 20){
                                        for(Move oppMove : oppMoves){
                                            if(oppMove.getPower() > 0){
                                                hasDamagingMoves = true;
                                            }
                                        }
                                        for(InBattleMove ourMoves : nonKillingPossibleMoves)
                                        {
                                            if(ourMoves.getId().equals("venomdrench") || ourMoves.getId().equals("hex") || ourMoves.getId().equals("venoshock")){
                                                hasCertainMove = true;
                                            }
                                        }
                                        if(hasDamagingMoves && hasCertainMove && currentAbility.equals("merciless")){
                                            score+=2;
                                        }
                                    }
                                }
                                break;
                            case "counter":
                                //TODO
                                boolean hasOnlyPhys = oppHasPhysicalMove && !oppHasSpecialMove;
                                score += 6;
                                if(npcIsOHKO){
                                    score += -20;
                                }
                                if(hasOnlyPhys){
                                    if(currentHeldItem.equals("focussash") || currentAbility.equals("sturdy") && activePokemonPercentHP == 100){
                                        score += 2;
                                    }
                                }
                                if(!npcIsOHKO && hasOnlyPhys){
                                    score += roll > .2 ? 2:0;
                                }
                                if(isFaster){
                                    score += roll > .75 ? -1:0;
                                }
                                if(hasAnyMoveType(opponent, statusMoves)){
                                    score += roll > .75 ? -1:0;
                                }

                                break;
                            case "mirrorcoat":
                                boolean hasOnlySpecial = !oppHasPhysicalMove && oppHasSpecialMove;
                                score += 6;
                                if(npcIsOHKO){
                                    score += -20;
                                }
                                if(hasOnlySpecial){
                                    if(currentHeldItem.equals("focussash") || currentAbility.equals("sturdy") && activePokemonPercentHP == 100){
                                        score += 2;
                                    }
                                }
                                if(!npcIsOHKO && hasOnlySpecial){
                                    score += roll > .2 ? 2:0;
                                }
                                if(isFaster){
                                    score += roll > .75 ? -1:0;
                                }
                                if(hasAnyMoveType(opponent, statusMoves)){
                                    score += roll > .75 ? -1:0;
                                }
                                break;
                            case "ruination":
                                roll = RANDOM.nextDouble();
                                if ((opponent.getHealth() / 2) > maxDamage){
                                    score += roll > .4 ? 9:7;
                                }
                                break;
                            case "revivalblessing":

                                break;

                            case "shedtail":
                                if(isFaster){
                                    if(activePokemonPercentHP > .5 && maxDamage < oppMaxDamage ){
                                        score += 8;
                                    }
                                    else{
                                        score -= 20;
                                    }
                                }
                                if(!isFaster){
                                    if(activePokemonCurrentHP - oppMaxDamage > .5 && maxDamage < oppMaxDamage){
                                        score += 8;
                                    }
                                    else{
                                        score -= 20;
                                    }
                                }
                                break;
                            
                        }
                    }
                    //TODO: START OF GENERAL SETUP CODE
                    String lastTurnMove = moveHistoryEnemy.getOrDefault(battleTurn-1, "");
                    boolean isRecharging = false;
                    boolean isLoafing = false;
                    if(turnsForActivePokemon % 2 ==0 && opponentAbility.equals("truant")){
                        isLoafing = true;
                    }
                    if(rechargeMoves.contains(lastTurnMove) && (lastTurnMove != "-miss" || lastTurnMove != "-immune" || lastTurnMove != "-fail")){
                        isRecharging = true;
                    }
                    if(generalSetupMoves.contains(moveID)){
                        boolean hasThawingMove = false;
                        for(String m : thawingMoves){
                            if(oppMoves.contains(m)){
                                hasThawingMove = true;
                                break;
                            }
                        }
                        //TODO: These moves are only setup moves when contrary ability holder is using them.
                        if(currentAbility.equals("contrary") && score != 0){
                            switch (moveID){
                                case "overheat", "leafstorm":
                                    score += 6;
                                    if((isOPFrozen && !hasThawingMove) ||isOPSleeping ||  isLoafing || isRecharging){
                                        score += 3;
                                    }
                                    else if(!npcIs3OHKO) {
                                        score += 1;
                                        if(isFaster){
                                            score += 1;
                                        }
                                    }
                                    if(!isFaster && npcIs2OHKO){
                                        score += -5;
                                    }
                                    if(npcStages.getOrDefault(Stats.SPECIAL_ATTACK, 0) >= 2){
                                        score += -1;
                                    }
                                    break;
                                case "superpower":
                                    score += 6;
                                    if((isOPFrozen && !hasThawingMove) ||isOPSleeping || isLoafing || isRecharging){
                                        score += 3;
                                    }
                                    else if(!npcIs3OHKO) {
                                        score += 1;
                                        if(isFaster){
                                            score += 1;
                                        }
                                    }
                                    if(!isFaster && npcIs2OHKO){
                                        score += -5;
                                    }
                                    if(npcStages.getOrDefault(Stats.ATTACK, 0) >= 2){
                                        score--;
                                    }
                                    break;
                            }
                        }
                        if(npcIsOHKO){
                            score += -20;
                        }
                        if(opponentAbility.equals("unaware")){
                            score += -20;
                        }

                        switch(moveID){
                            //TODO: OFFENSIVE SETUP MOVES (IF WE ARE FASTER AFTER A SPEED BUFF GIVE MORE SCORE eg. DRAGON DANCE, SHIFT GEAR, QUIVER DANCE)
                            case "swordsdance", "howl", "sharpen", "meditate", "honeclaws":
                                score += 6;
                                if((isOPFrozen && !hasThawingMove) ||isOPSleeping || isLoafing || isRecharging){  //check truant or recharge
                                    score += 3;
                                }
                                if(!isFaster && npcIs2OHKO){
                                    score += -5;
                                }
                                break;
                            case "dragondance", "shiftgear", "tidyup":
                                score += 6;
                                if(moveID.equals("shiftgear")){
                                    if(fasterAndOHKOAfterBoost(battlePokemon, opponent, 2, 1, 0)){
                                        score += 5;
                                    }
                                }
                                else{
                                    if(fasterAndOHKOAfterBoost(battlePokemon, opponent, 1, 1, 0)){
                                        score += 5;
                                    }
                                }
                                if((isOPFrozen && !hasThawingMove) ||isOPSleeping || isLoafing || isRecharging){  //check truant or recharge
                                    score += 3;
                                }
                                if(!isFaster && npcIs2OHKO){
                                    score += -5;
                                }
                                if(npcStages.getOrDefault(Stats.ATTACK, 0) >= 2
                                        || npcStages.getOrDefault(Stats.SPEED, 0) >= 2){
                                    score += -3;
                                }
                                break;
                            case "acidarmor", "barrier", "cottonguard", "harden", "irondefense", "stockpile", "cosmicpower":
                                roll = RANDOM.nextDouble();
                                score += 6;
                                if(!isFaster && npcIs2OHKO){
                                    score += -5;
                                }
                                if(roll > .05){
                                    if(isOPFrozen ||isOPSleeping){
                                        score += 2;
                                    }
                                    if((moveID.equals("stockpile") || moveID.equals("cosmicpower")) && (npcStages.getOrDefault(Stats.SPECIAL_DEFENCE, 0) < 2 || npcStages.getOrDefault(Stats.DEFENCE, 0) < 2)){
                                        score += 2;
                                    }
                                }
                                break;
                            case "coil", "bulkup", "calmmind", "curse":
                                score += 6;
                                if(((oppHasPhysicalMove && !oppHasSpecialMove) || ((!oppHasPhysicalMove && !oppHasSpecialMove))) && (moveID.equals("calmmind"))){
                                    if((isOPFrozen && !hasThawingMove) ||isOPSleeping|| isLoafing || isRecharging){  //check truant or recharge
                                        score += 3;
                                    }
                                    if(!isFaster && npcIs2OHKO){
                                        score += -5;
                                    }
                                }
                                else if (!oppHasPhysicalMove && oppHasSpecialMove && (moveID.equals("calmmind"))){
                                    roll = RANDOM.nextDouble();
                                    if(!isFaster && npcIs2OHKO){
                                        score += -5;
                                    }
                                    if(roll > .05){
                                        if(isOPFrozen ||isOPSleeping){
                                            score += 2;
                                        }
                                    }
                                }
                                //Offensive setup, has at least 1 special and no physical moves
                                if(((oppHasSpecialMove && !oppHasPhysicalMove) || (!oppHasPhysicalMove && !oppHasSpecialMove)) && (moveID.equals("coil") || moveID.equals("bulkup") ||  moveID.equals("curse"))){
                                    if((isOPFrozen && !hasThawingMove) ||isOPSleeping|| isLoafing || isRecharging){  //check truant or recharge
                                        score += 3;
                                    }
                                    if(!isFaster && npcIs2OHKO){
                                        score += -5;
                                    }
                                }
                                //Deffensive setup, has at least 1 physical and no special moves
                                else if(!oppHasSpecialMove && oppHasPhysicalMove && (moveID.equals("coil") || moveID.equals("bulkup") || moveID.equals("noretreat") || moveID.equals("curse"))){
                                    roll = RANDOM.nextDouble();
                                    if(!isFaster && npcIs2OHKO){
                                        score += -5;
                                    }
                                    if(roll > .05){
                                        if(isOPFrozen ||isOPSleeping){
                                            score += 2;
                                        }
                                    }
                                }
                                break;
                            case "quiverdance", "geomancy":
                                score += 6;
                                if(moveID.equals("geomancy")){
                                    if(fasterAndOHKOAfterBoost(battlePokemon, opponent, 2, 2, 2) && "powerherb".equals(currentHeldItem)){
                                        score += 5;
                                    }
                                    else{
                                        score += -20;
                                    }
                                }
                                else{
                                    if(fasterAndOHKOAfterBoost(battlePokemon, opponent, 1, 1, 1)){
                                        score += 5;
                                    }
                                }
                                if((isOPFrozen && !hasThawingMove) ||isOPSleeping|| isLoafing || isRecharging){  //check truant or recharge
                                    score += 3;
                                }
                                if(!isFaster && npcIs2OHKO){
                                    score += -5;
                                }
                                if(npcStages.getOrDefault(Stats.SPECIAL_DEFENCE, 0) >= 2
                                        || npcStages.getOrDefault(Stats.SPECIAL_ATTACK, 0) >= 2
                                        || npcStages.getOrDefault(Stats.SPEED, 0) >= 2 ){
                                    score += -3;
                                }
                                break;

                            case "noretreat":
                                score += 6;
                                if(fasterAndOHKOAfterBoost(battlePokemon, opponent, 1, 1, 1)){
                                    score += 5;
                                }
                                if(!isFaster && npcIs2OHKO){
                                    score += -5;
                                }
                                if(npcStages.getOrDefault(Stats.SPECIAL_DEFENCE, 0) >= 2
                                        || npcStages.getOrDefault(Stats.DEFENCE, 0) >= 2
                                        || npcStages.getOrDefault(Stats.ATTACK, 0) >= 2
                                        || npcStages.getOrDefault(Stats.SPECIAL_ATTACK, 0) >= 2
                                        || npcStages.getOrDefault(Stats.SPEED, 0) >= 2 ){
                                    score += -3;
                                }
                                break;
                            case "agility", "rockpolish", "autotomize":
                                if(!isFaster){
                                    score += 7;
                                }
                                else{
                                    score += -20;
                                }
                                break;
                            case "tailglow", "nastyplot", "workup":
                                score += 6;
                                if((isOPFrozen && !hasThawingMove)
                                        ||isOPSleeping|| isLoafing || isRecharging){//check truant or recharge
                                    score += 3;
                                }
                                else if(!npcIs3OHKO) {
                                    score += 1;
                                    if(isFaster){
                                        score += 1;
                                    }
                                }
                                if(!isFaster && npcIs2OHKO){
                                    score += -5;
                                }
                                if(npcStages.getOrDefault(Stats.SPECIAL_ATTACK, 0) >= 2){
                                    score += -1;
                                }
                                break;
                            case "shellsmash":
                                if((isOPFrozen && !hasThawingMove)
                                        ||isOPSleeping|| isLoafing || isRecharging){ //check truant or recharge
                                    score += 3;
                                }
                                if(!npcIsOHKOWithSS || (!npcIsOHKO && "whiteherb".equals(currentHeldItem))){
                                    score += 2;
                                }
                                else{
                                    score += -2;
                                }
                                if(npcStages.getOrDefault(Stats.ATTACK, 0) >= 1
                                        || npcStages.getOrDefault(Stats.SPECIAL_ATTACK, 0) >= 1
                                        || npcStages.getOrDefault(Stats.ATTACK, 0) == 6
                                        || npcStages.getOrDefault(Stats.SPECIAL_ATTACK, 0) == 6){
                                    score += -20;
                                }
                                break;
                            case "bellydrum":
                                if((isOPFrozen && !hasThawingMove)
                                        ||isOPSleeping|| isLoafing || isRecharging){ //check truant or recharge
                                    score += 9;
                                }
                                else if(!npcIsOHKOWithBD){
                                    score += 8;
                                }
                                else{
                                    score += 4;
                                }
                                break;
                            case "focusenergy", "laserfocus":
                                if("scopelens".equals(currentHeldItem)
                                        || (currentAbility.equals("superluck")
                                        || currentAbility.equals("sniper"))){
                                    score+=7;
                                }
                                else{
                                    score+=6;
                                }
                                break;
                            case "coaching":
                                roll = RANDOM.nextDouble();
                                score += 6;
                                if(isDoubles && !NPCPartner.getBattlePokemon().getEffectedPokemon().getAbility().getName().equals("contrary")){
                                    if(getStageMap(NPCPartner.getBattlePokemon()).getOrDefault(Stats.ATTACK,0) <=2){
                                        score += 1 - getStageMap(NPCPartner.getBattlePokemon()).getOrDefault(Stats.ATTACK,0);
                                    }
                                    if(getStageMap(NPCPartner.getBattlePokemon()).getOrDefault(Stats.DEFENCE,0) <=2){
                                        score += 1 - getStageMap(NPCPartner.getBattlePokemon()).getOrDefault(Stats.DEFENCE,0);
                                    }
                                    score += roll > .2 ? 1: 0;
                                }
                                else {
                                    score += -20;
                                }
                                break;
                            case "meteorbeam":
                                if("powerherb".equals(currentHeldItem)){
                                    score += 9;
                                }
                                else{
                                    score += -20;
                                }
                                break;
                            case "destinybond":
                                roll = RANDOM.nextDouble();
                                if(isFaster && npcIsOHKO){
                                    score += roll > .19 ? 7: 6;
                                }
                                if(!isFaster){
                                    score += roll > .5 ? 5: 6;
                                }
                                break;
                        }
                    }
                    if(recoveryMoves.contains(move.getKey().getId())){
                        switch (move.getKey().getId()){
                            case "junglehealing", "lifedew":
                                if(shouldRecover(oppMaxDamage, move.getKey().getId(), 25, isFaster, battlePokemon,opponent)){
                                    score+=7;
                                }
                                else{
                                    score+=5;
                                }
                                if(getCurrentPercentHP(battlePokemon) == 100){
                                    score += -20;
                                }
                                else if(getCurrentPercentHP(battlePokemon) >=85){
                                    score += -6;
                                }
                                break;
                            case "recover","slackoff","healorder","softboiled","roost","strengthsap":
                                if(shouldRecover(oppMaxDamage, move.getKey().getId(), 50, isFaster, battlePokemon,opponent)){
                                    score+=7;
                                }
                                else{
                                    score+=5;
                                }
                                if(getCurrentPercentHP(battlePokemon) == 100){
                                    score += -20;
                                }
                                else if(getCurrentPercentHP(battlePokemon) >=85){
                                    score += -6;
                                }
                                break;
                            case "morningsun","synthesis", "moonlight":
                                boolean isSunActive = BattleEffects.Field.Weather.harshsunlight(opponent) || BattleEffects.Field.Weather.extremelyharshsunlight(opponent);
                                if(shouldRecover(oppMaxDamage, move.getKey().getId(), 67, isFaster, battlePokemon,opponent)
                                        && isSunActive){
                                    score+=7;
                                }
                                else if(!shouldRecover(oppMaxDamage, move.getKey().getId(), 67, isFaster, battlePokemon,opponent)
                                        || !isSunActive){
                                    if(shouldRecover(oppMaxDamage,move.getKey().getId(),50,isFaster,battlePokemon,opponent)) {
                                        score+=7;
                                    }
                                    else{
                                        score += 5;
                                    }
                                }

                                if(getCurrentPercentHP(battlePokemon) == 100){
                                    score += -20;
                                }
                                else if(getCurrentPercentHP(battlePokemon) >=85){
                                    score += -6;
                                }
                                break;
                            case "rest":
                                if(shouldRecover(oppMaxDamage, move.getKey().getId(), 100, isFaster, battlePokemon,opponent)){
                                    List<Move> NPCmoveSet = battlePokemon.getMoveSet().getMoves();
                                    boolean sleepTalkSnore = false;
                                    boolean holdingCureSleep = "chestoberry".equals(currentHeldItem) || "lumberry".equals(currentHeldItem);
                                    boolean shedSkinEarlyBird = currentAbility.equals("earlybird") || currentAbility.equals("shedskin");
                                    boolean hydrationRaining = currentAbility.equals("hydration") && (BattleEffects.Field.Weather.rain(opponent) || BattleEffects.Field.Weather.heavyrain(opponent));
                                    if(hasMoveName(battlePokemon,"sleeptalk") || hasMoveName(battlePokemon,"snore")) {
                                        sleepTalkSnore = true;
                                    }
                                    if(holdingCureSleep || sleepTalkSnore || shedSkinEarlyBird || hydrationRaining){
                                        score+=8;
                                    }
                                    else{
                                        score+=7;
                                    }
                                }
                                else{
                                    score+=5;
                                }
                                break;
                        }
                    }
                    //list of all opponents moves and their OHKO potential.
                    if (priorityDamageMoves.contains(move.getKey().getId()) && !isFaster && npcIsOHKO) {
                        score += 11;
                    }
                    //if our pokemon has a special ability give its score +1
                    if (abilityStatBooster.contains(battlePokemon.getOriginalPokemon().getAbility().getName())) {
                        score++;
                    }
                    //if a damaging move has a high crit chance and is Super Effective on the target
                    // (50% of the time the score gets increased by 1)
                    if (highCriticalMoves.contains(currentMove.getId()) && TypeChart.getEffectiveness(TypeChart.getMove(currentMove).getType(),opponent) >=2) {
                        roll = RANDOM.nextDouble();
                        score = (roll < .5) ? score + 1 : score;
                    }
                    if (currentMove.getId().equals("acidspray")) {
                        score += 6;
                    }
                    if(currentMove.getId().equals("taunt")){
                        boolean hasDefog = hasMoveName(opponent,"defog");
                        boolean hasTrickRoom = hasMoveName(opponent,"trickroom");
                        if(hasTrickRoom && !BattleEffects.Field.Room.trickroom(opponent)) {
                            score += 9;
                        }
                        //TODO: check if aura veil is active
                        else if(hasDefog && isFaster){
                            score+=9;
                        }
                        else{
                            score+=5;
                        }
                    }
                    if(currentMove.getId().equals("encore")){
                        //TODO: if last turn the opponent used a non-damaging move
                        if(isFaster){
                            score+=7;
                        }
                        else{
                            roll = RANDOM.nextDouble();
                            score += roll > .5 ? 6:5;
                        }
                    }
                }
            }
            if(currentMove.getId().equals("futuresight")){
                score+= isFaster && npcIsOHKO ? 8:6;
            }
            if(currentMove.getId().equals("pursuit") && isFaster){
                score+=3;
            }

            moveScores.put(currentMove, score);
            ModCommon.LOG.info(currentMove.getId() + "  " + Integer.toString(score));
        }
        // END OF SCORING LOGIC::START OF SWITCH AI LOGIC
        if(isSwitching(moveScores, aliveParty, battlePokemon, opponent)){
            double flip = RANDOM.nextDouble();
            boolean result = (flip > .5);
            ModCommon.LOG.info(Boolean.toString(result) + "    coin toss result");
            if(result){
                nextPokemon.setWillBeSwitchedIn(true);
                moveHistory = new HashMap<>();
                ModCommon.LOG.info("SWITCHING INTO NEXT MON");
                switchedLastTurn = true;
                return new SwitchActionResponse(nextPokemon.getUuid());
                //start switching logic
            }
        }
        int maxScore = moveScores.values()
                .stream()
                .max(Integer::compareTo)
                .orElse(Integer.MIN_VALUE);
        List<InBattleMove> bestMoves = moveScores.entrySet()
                .stream()
                .filter(entry -> entry.getValue() == maxScore)
                .map(Map.Entry::getKey)
                .toList();
        //if there are multiple best moves
        if(bestMoves.size() > 1){
            int randomInt = RANDOM.nextInt(bestMoves.size());
            var bestMove = bestMoves.get(randomInt);
            ModCommon.LOG.info("CHOOSEN BEST MOVE  " + bestMove.getId());
            List<Targetable> targets = bestMove.mustBeUsed() ? null : bestMove.getTarget().getTargetList().invoke(activeBattlePokemon);
            changeTurn(battlePokemon);
            return new MoveActionResponse(bestMove.getId(),
                    targets == null ? null : opponentActiveBattlePokemon.get().getPNX(),
                    gimmick);
        }
        List<Targetable> targets = bestMoves.get(0).mustBeUsed() ? null : bestMoves.get(0).getTarget().getTargetList().invoke(activeBattlePokemon);
        ModCommon.LOG.info("CHOOSEN BEST MOVE  " + bestMoves.get(0).getId());
        changeTurn(battlePokemon);
        return new MoveActionResponse(bestMoves.get(0).getId(),
                targets == null ? null : opponentActiveBattlePokemon.get().getPNX(),
                gimmick);
    }
    public static int getSpeedStat(ActiveBattlePokemon pkmn){
        return  (int)PokeMathMax.calcSpeedWithStatChange(pkmn.getBattlePokemon(), getStageMap(pkmn.getBattlePokemon()));
    }
    public static boolean hasMoveName(BattlePokemon pokemon, String moveName){
        for(Move move : pokemon.getMoveSet().getMoves()){
            if(move.getName().equals(moveName)){
                return true;
            }
        }
        return false;
    }
    public static boolean shouldRecover(double oppMaxDamage, String recoverMove, int recoverAmount, boolean isAIFaster, BattlePokemon AIpokemon, BattlePokemon oppPokemon){
        double maxPercentHPDamage = oppMaxDamage/(double)AIpokemon.getMaxHealth() * 100;
        double roll = RANDOM.nextDouble();
        double currentAIPercentHP = getCurrentPercentHP(AIpokemon);
        if(BattleEffects.Pokemon.Status.tox(AIpokemon)){
            return false;
        }
        if(oppMaxDamage >= recoverAmount){
            return false;
        }
        if(isAIFaster){
            if(maxPercentHPDamage >= currentAIPercentHP && maxPercentHPDamage < currentAIPercentHP + recoverAmount){
                return true;
            }
            else if(maxPercentHPDamage < currentAIPercentHP){
                if(currentAIPercentHP < 66 && currentAIPercentHP > 40){
                    return roll > .5;
                }
                else if(currentAIPercentHP < 40){
                    return true;
                }
            }
        }
        else{
            if(currentAIPercentHP < 70){
                return  roll > .25;
            }
            else if(currentAIPercentHP < 50){
                return true;
            }
        }
        return false;
    }

    public static boolean isOHKO(List<Move> moves, BattlePokemon attacker, BattlePokemon defender,Map<Stat,Integer> attackerStages, Map<Stat,Integer> defenderStages){
        int enemyDamage = 0;
        int currentHP = defender.getHealth();
        boolean result = false;
        for (Move currentMove : moves) {
            //activeBattlePokemon.getBattlePokemon().getOriginalPokemon().getPrimaryType();
            enemyDamage = PokeMathMax.damage(attacker, defender, currentMove, attackerStages, defenderStages);
            if (enemyDamage >= currentHP) {
                result = true;
                if(currentHP == defender.getMaxHealth()
                        && defender.getEffectedPokemon().getAbility().getDisplayName().equals("sturdy")){
                    result = false;
                }
                if(defender.getHeldItemManager().showdownId(defender) != null){
                    if(currentHP == defender.getMaxHealth()
                            && defender.getHeldItemManager().showdownId(defender).equals("focussash")){
                        result = false;
                    }
                }
                if(result){
                    return true;
                }
            }
        }
        return result;
    }
    public static boolean is2HKO(List<Move> moves, BattlePokemon attacker, BattlePokemon defender,Map<Stat,Integer> attackerStages, Map<Stat,Integer> defenderStages) {
        int enemyDamage = 0;
        int currentHP = defender.getHealth();
        for (Move currentMove : moves) {
            //activeBattlePokemon.getBattlePokemon().getOriginalPokemon().getPrimaryType();
            enemyDamage = PokeMathMax.damage(attacker, defender, currentMove, attackerStages, defenderStages);
            if (enemyDamage * 2 >= currentHP) {
                return true;
            }
        }
        return false;
    }
    public static boolean is3HKO(List<Move> moves, BattlePokemon attacker, BattlePokemon defender,Map<Stat,Integer> attackerStages, Map<Stat,Integer> defenderStages) {
        int enemyDamage = 0;
        int currentHP = defender.getHealth();
        for (Move currentMove : moves) {
            //activeBattlePokemon.getBattlePokemon().getOriginalPokemon().getPrimaryType();
            enemyDamage = PokeMathMax.damage(attacker, defender, currentMove, attackerStages, defenderStages);
            if (enemyDamage * 3 >= currentHP) {
                return true;
            }
        }
        return false;
    }
    public static boolean wouldBeOHKOAfterShellSmash(List<Move> moves, BattlePokemon attacker, BattlePokemon defender, Map<Stat,Integer> attackerStages, Map<Stat,Integer> defenderStages) {

        Map<Stat,Integer> simulatedDefenderStages = new HashMap<>(defenderStages);
        simulatedDefenderStages.put(Stats.DEFENCE, Math.max(-6, Math.min(6, simulatedDefenderStages.getOrDefault(Stats.DEFENCE, 0) - 2)));
        simulatedDefenderStages.put(Stats.SPECIAL_DEFENCE, Math.max(-6, Math.min(6, simulatedDefenderStages.getOrDefault(Stats.SPECIAL_DEFENCE, 0) - 2)));
        boolean result = false;
        int currentHP = defender.getHealth();
        for (Move move : moves) {
            int enemyDamage = PokeMathMax.damage(attacker, defender, move, attackerStages, simulatedDefenderStages);
            if (enemyDamage >= currentHP) {
                result = true;
                if (currentHP == defender.getMaxHealth() &&
                        defender.getEffectedPokemon().getAbility().getDisplayName().equals("sturdy")) {
                    result = false;
                }

                if (defender.getHeldItemManager().showdownId(defender) != null) {
                    if(currentHP == defender.getMaxHealth()
                                && defender.getHeldItemManager().showdownId(defender).equals("focussash")){
                            result = false;
                    }
                }
                
            }
        }
        return result;
    }
    public static boolean isOHKOAfterBellyDrum(List<Move> moves, BattlePokemon attacker, BattlePokemon defender,Map<Stat,Integer> attackerStages, Map<Stat,Integer> defenderStages){
        int enemyDamage = 0;
        int currentHP = defender.getHealth();
        boolean result = false;
        boolean hasSitrus = defender.getHeldItemManager().showdownId(defender).equals("sitrusberry");
        boolean hasPinchBerry = defender.getHeldItemManager().showdownId(defender).equals("figyberry") 
        || defender.getHeldItemManager().showdownId(defender).equals("wikiberry") 
        || defender.getHeldItemManager().showdownId(defender).equals("magoberry") 
        || defender.getHeldItemManager().showdownId(defender).equals("aguavberry")
        || defender.getHeldItemManager().showdownId(defender).equals("iapapaberry");
        int currentHPAfterBellyDrum = defender.getHealth()/2;
        if(hasSitrus){
            currentHPAfterBellyDrum += defender.getMaxHealth()/4;
        }
        if(hasPinchBerry){
            currentHPAfterBellyDrum += defender.getMaxHealth()*.33;
        }
        for (Move currentMove : moves) {
            enemyDamage = PokeMathMax.damage(attacker, defender, currentMove, attackerStages, defenderStages);
            if (enemyDamage >= currentHP || enemyDamage >= currentHPAfterBellyDrum) {
                return true;
            }
        }
        return result;
    }
    public static boolean isPartySlowerThanOpponent(List<ActiveBattlePokemon> NPC, List<ActiveBattlePokemon> OPP){
        //get the slowest mon from each team and compare
        int slowestNPC = Math.min(getSpeedStat(NPC.getFirst()) , getSpeedStat(NPC.getLast()));
        int slowestOPP = Math.min(getSpeedStat(OPP.getFirst()) , getSpeedStat(OPP.getLast()));
        if(slowestOPP > slowestNPC){
            return true;
        }
        return false;
    }
    public static boolean isPartyFasterThanOpponent(List<ActiveBattlePokemon> NPC, List<ActiveBattlePokemon> OPP){
        //get the slowest mon from each team and compare
        int fastestNPC = Math.max(getSpeedStat(NPC.getFirst()) , getSpeedStat(NPC.getLast()));
        int fastestOPP = Math.max(getSpeedStat(OPP.getFirst()) , getSpeedStat(OPP.getLast()));
        if(fastestOPP < fastestNPC){
            return true;
        }
        return false;
    }
    public static boolean isSwitching(Map<InBattleMove, Integer> moveScore, List<BattlePokemon> party,BattlePokemon self, BattlePokemon opponent){
        List<Integer> scores = new ArrayList<>();
        boolean isSecondCondition = false;
        boolean isThirdCondition = false;
        for(int val : moveScore.values()){
            scores.add(val);
        }
        boolean hasLowScore = scores.stream().allMatch(s -> s <=-5);
        if(Math.ceil(getCurrentPercentHP(self)) <= 50){
            return false;
        }
        for(BattlePokemon pokemon : party){
            //TODO: ((if mon is faster than opp, and not OHKO) || (if mon is slower and not 2OHKO)) && not below 50% hp
            if(pokemon.getEffectedPokemon().getStat(Stats.SPEED) >= opponent.getEffectedPokemon().getStat(Stats.SPEED)
                && !isOHKO(opponent.getMoveSet().getMoves(), opponent, pokemon, opponentStages, npcStages)){
                isSecondCondition = true;
            }
            if(pokemon.getEffectedPokemon().getStat(Stats.SPEED) < opponent.getEffectedPokemon().getStat(Stats.SPEED)
                    && !is2HKO(opponent.getMoveSet().getMoves(), opponent, pokemon, opponentStages, npcStages)){
                isThirdCondition = true;
            }
        }
        return isSecondCondition && isThirdCondition && hasLowScore;
    }
    public static double highestPercentDamageMove(BattlePokemon attacker, BattlePokemon defender){
        List<Move> attackerMoves = attacker.getMoveSet().getMoves();
        double highestPercent = 0;
        double currentCalc = 0;
        for(Move move : attackerMoves){
            //TODO: damage delt divided by map hp.
            currentCalc = Math.ceil((double)PokeMathMax.damage(attacker,defender,move, npcStages, opponentStages) / (double)defender.getMaxHealth());
            highestPercent = currentCalc > highestPercent ? currentCalc : highestPercent;
        }
        return highestPercent;
    }
    public static Map<Stat, Integer> getStageMap(BattlePokemon bp) {
        Map<Stat, Integer> stageMap = new HashMap<>();
        ContextManager ctx = bp.getContextManager();
        StatProvider statProvider = Cobblemon.INSTANCE.getStatProvider();

        Collection<BattleContext> boosts = ctx.get(BattleContext.Type.BOOST);
        if (boosts != null) {
            for (BattleContext c : boosts) {
                String statId = statIdMap.getOrDefault(c.getId(), c.getId());
                ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("cobblemon", statId);
                try {
                    Stat stat = statProvider.fromIdentifierOrThrow(rl);
                    stageMap.put(stat, stageMap.getOrDefault(stat, 0) + 1);
                } catch (IllegalArgumentException e) {
                    ModCommon.LOG.warn("Unknown stat for id: " + c.getId());
                }
            }
        }

        Collection<BattleContext> unboosts = ctx.get(BattleContext.Type.UNBOOST);
        if (unboosts != null) {
            for (BattleContext c : unboosts) {
                String statId = statIdMap.getOrDefault(c.getId(), c.getId());
                ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("cobblemon", statId);
                try {
                    Stat stat = statProvider.fromIdentifierOrThrow(rl);
                    stageMap.put(stat, stageMap.getOrDefault(stat, 0) - 1); // subtract for unboost
                } catch (IllegalArgumentException e) {
                    ModCommon.LOG.warn("Unknown stat for id: " + c.getId());
                }
            }
        }

        return stageMap;
    }
    private static double getInBattleSpeed(BattlePokemon pokemon){
        Map<Stat, Integer> statMap = getStageMap(pokemon);
        double multiplier = 1;
        if (statMap.getOrDefault(Stats.SPEED, 0) < 0) {
            double statChange = statMap.getOrDefault(Stats.SPEED, 0);
            multiplier = 2 / (2 - statChange);
        }
        else if (statMap.getOrDefault(Stats.SPEED, 0) > 0) {
            double statChange = statMap.getOrDefault(Stats.SPEED, 0);
            multiplier = (2 + statChange) / 2;
        } else {
            return BattleStates.getTransformationOrEffected(pokemon).getSpeed();
        }
        return BattleStates.getTransformationOrEffected(pokemon).getSpeed() * multiplier;
    }
    private static double getCurrentPercentHP(BattlePokemon pokemon){
        return ((double)pokemon.getHealth() / (double) pokemon.getMaxHealth()) * 100;
    }
    private static boolean hasMove(BattlePokemon pokemon, String moveID){
        boolean hasMove = false;
        List<Move> pokemonMoveSet = pokemon.getMoveSet().getMoves();
        for(Move pkmMove : pokemonMoveSet){
            if(pkmMove.getName().equals(moveID)){
                hasMove = true;
            }
        }
        return hasMove;
    }
    private static boolean hasAnyMoveType(BattlePokemon pokemon, List<String> list){
        boolean hasMove = false;
        List<Move> pokemonMoveSet = pokemon.getMoveSet().getMoves();
        for(Move pkmMove : pokemonMoveSet){
            if(pokemonMoveSet.contains(pkmMove.getName())){
                hasMove = true;
            }
        }
        return hasMove;
    }
    private static double getEffectiveSpeed(BattlePokemon pokemon, Map<Stat, Integer> stages) {
        double multiplier = 1;
        int stage = stages.getOrDefault(Stats.SPEED, 0);

        if (stage < 0) {
            multiplier = 2.0 / (2 - stage);
        } else if (stage > 0) {
            multiplier = (2.0 + stage) / 2.0;
        }
        return BattleStates.getTransformationOrEffected(pokemon).getSpeed() * multiplier;
    }
    private static boolean fasterAndOHKOAfterBoost(BattlePokemon attacker, BattlePokemon defender, int boostedSpeed, int boostedAtk, int boostedSpAtk){
        Map<Stat, Integer> attackerStages = new HashMap<>(getStageMap(attacker));
        Map<Stat, Integer> defenderStages = getStageMap(defender);

        attackerStages.put(Stats.SPEED, Math.max(-6, Math.min(6, attackerStages.getOrDefault(Stats.SPEED, 0) + boostedSpeed)));
        attackerStages.put(Stats.ATTACK, Math.max(-6, Math.min(6, attackerStages.getOrDefault(Stats.ATTACK, 0) + boostedAtk)));
        attackerStages.put(Stats.SPECIAL_ATTACK,  Math.max(-6, Math.min(6, attackerStages.getOrDefault(Stats.SPECIAL_ATTACK, 0) + boostedSpAtk)));

        return getEffectiveSpeed(attacker, attackerStages) >= getEffectiveSpeed(defender, defenderStages) && isOHKO(attacker.getMoveSet().getMoves(), attacker, defender, attackerStages, defenderStages);
    }
    private static void changeTurn(BattlePokemon pokemon){
        if(!switchedLastTurn){
            if(currentPokemonUUID == pokemon.getUuid()){
                turnsForActivePokemon++;
            }
            battleTurn++;
            if(!moveHistory.isEmpty()){
                for(var moves : moveHistory.entrySet()){
                    ModCommon.LOG.info("TURN " + moves.getKey() + "  NPC USED " + moves.getValue());
                }
            }
        }
        else{
            switchedLastTurn = false;
        }
    }
    public static boolean isSandstormFatal(BattlePokemon battlePokemon, ElementalType primaryType, ElementalType secondaryType, double percentHP) {
        if (!BattleEffects.Field.Weather.sandstorm(battlePokemon)) {
            return false;
        }
        Set<String> immuneTypes = Set.of("ROCK", "GROUND", "STEEL");
        boolean isImmune = immuneTypes.contains(primaryType.getDisplayName())
                || immuneTypes.contains(secondaryType.getDisplayName());
        if (isImmune) {
            return false;
        }
        return percentHP <= 8;
    }
    public static int getHazardCount(Map<Integer, String> moveHistory, String move){
        int count = 0;
        for(Map.Entry<Integer, String> entry : moveHistory.entrySet()){
            if(entry.getValue().equals(move)){
                count++;
            }
        }
        return count;
    }
    public static boolean getIsMoveUp(String moveID, Map<Integer,String> moveHistory, int turnDuration, int currentTurn){
        int lastTurnUsed = -1;
        for(Map.Entry<Integer, String> history : moveHistory.entrySet()){
            if(history.getValue().equals(moveID)){
                lastTurnUsed = history.getKey();
            }
        }
        if(lastTurnUsed == -1){
            return false;
        }
        return currentTurn - lastTurnUsed < turnDuration;
    }
}
