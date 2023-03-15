/**
 *  Copyright (C) 2002-2022   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.server.ai;

import java.util.List;

import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Constants.IndianDemandAction;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Monarch.MonarchAction;
import net.sf.freecol.common.model.NativeTrade;
import net.sf.freecol.common.model.NativeTrade.NativeTradeAction;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Region;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovementType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.Unit.UnitState;
import net.sf.freecol.common.networking.ServerAPI;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.model.WorkLocation;


/**
 * Wrapper class for AI message handling.
 */
public class AIMessage {

    /**
     * An AIUnit attacks in the given direction.
     *
     * @param aiUnit The {@code AIUnit} to attack with.
     * @param direction The {@code Direction} to attack in.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askAttack(AIUnit aiUnit, Direction direction) {
        return aiUnit.getAIOwner().askServer()
            .attack(aiUnit.getUnit(), direction) == ServerAPI.SUCCESS;
    }
    
    /**
     * An AIUnit attacks the given target with a ranged attack.
     *
     * @param aiUnit The {@code AIUnit} to attack with.
     * @param target The target {@code Tile} of the attack. 
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askAttackRanged(AIUnit aiUnit, Tile target) {
        return aiUnit.getAIOwner().askServer()
            .attackRanged(aiUnit.getUnit(), target) == ServerAPI.SUCCESS;
    }

    /**
     * An AIUnit builds a colony.
     *
     * @param aiUnit The {@code AIUnit} to build the colony.
     * @param name The name of the colony.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askBuildColony(AIUnit aiUnit, String name) {
        return aiUnit.getAIOwner().askServer()
            .buildColony(name, aiUnit.getUnit()) == ServerAPI.SUCCESS;
    }

    /**
     * An AIUnit cashes in.
     *
     * @param aiUnit The {@code AIUnit} cashing in.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askCashInTreasureTrain(AIUnit aiUnit) {
        return aiUnit.getAIOwner().askServer()
            .cashInTreasureTrain(aiUnit.getUnit()) == ServerAPI.SUCCESS;
    }

    /**
     * An AIUnit changes state.
     *
     * @param aiUnit The {@code AIUnit} to change the state of.
     * @param state The new {@code UnitState}.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askChangeState(AIUnit aiUnit, UnitState state) {
        return aiUnit.getAIOwner().askServer()
            .changeState(aiUnit.getUnit(), state) == ServerAPI.SUCCESS;
    }

    /**
     * An AIUnit changes its work type.
     *
     * @param aiUnit The {@code AIUnit} to change the work type of.
     * @param type The {@code GoodsType} to produce.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askChangeWorkType(AIUnit aiUnit, GoodsType type) {
        return aiUnit.getAIOwner().askServer()
            .changeWorkType(aiUnit.getUnit(), type) == ServerAPI.SUCCESS;
    }

   /**
     * An AIUnit changes its work improvement type.
     *
     * @param aiUnit The {@code AIUnit} to change the work type of.
     * @param type The {@code TileImprovementType} to produce.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askChangeWorkImprovementType(AIUnit aiUnit,
        TileImprovementType type) {
        return aiUnit.getAIOwner().askServer()
            .changeWorkImprovementType(aiUnit.getUnit(), type) == ServerAPI.SUCCESS;
    }

    /**
     * Choose a founding father for an AI player.
     *
     * @param aiPlayer The {@code AIPlayer} that is choosing.
     * @param fathers A list of {@code FoundingFather}s to choose from.
     * @param father The {@code FoundingFather} that has been chosen.
     * @return True if the message was sent.
     */
    public static boolean askChooseFoundingFather(AIPlayer aiPlayer,
                                                  List<FoundingFather> fathers,
                                                  FoundingFather father) {
        return aiPlayer.askServer()
            .chooseFoundingFather(fathers, father) == ServerAPI.SUCCESS;
    }
                                  
    /**
     * Claims a tile for a colony.
     *
     * @param tile The {@code Tile} to claim.
     * @param aic The {@code AIColony} that is claiming.
     * @param price The price to pay.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askClaimLand(Tile tile, AIColony aic, int price) {
        return aic.getAIOwner().askServer()
            .claimTile(tile, aic.getColony(), price) == ServerAPI.SUCCESS;
    }

    /**
     * Claims a tile.
     *
     * @param tile The {@code Tile} to claim.
     * @param aiUnit The {@code AIUnit} that is claiming.
     * @param price The price to pay.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askClaimLand(Tile tile, AIUnit aiUnit, int price) {
        return aiUnit.getAIOwner().askServer()
            .claimTile(tile, aiUnit.getUnit(), price) == ServerAPI.SUCCESS;
    }

    /**
     * Clears the speciality of a unit.
     *
     * @param aiUnit The {@code AIUnit} to clear.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askClearSpeciality(AIUnit aiUnit) {
        return aiUnit.getAIOwner().askServer()
            .clearSpeciality(aiUnit.getUnit()) == ServerAPI.SUCCESS;
    }
 
    /**
     * Do some diplomacy.
     *
     * @param aiPlayer The {@code AIPlayer} being diplomatic.
     * @param our Our object ({@code Unit} or {@code Colony})
     *     conducting the diplomacy.
     * @param other The other object ({@code Unit} or {@code Colony})
     *     to negotiate with.
     * @param dt The {@code DiplomaticTrade} agreement to propose.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askDiplomacy(AIPlayer aiPlayer, FreeColGameObject our,
                                       FreeColGameObject other,
                                       DiplomaticTrade dt) {
        return aiPlayer.askServer()
            .diplomacy(our, other, dt) == ServerAPI.SUCCESS;
    }

    /**
     * An AIUnit disbands.
     *
     * @param aiUnit The {@code AIUnit} to disband.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askDisband(AIUnit aiUnit) {
        return aiUnit.getAIOwner().askServer()
            .disbandUnit(aiUnit.getUnit()) == ServerAPI.SUCCESS;
    }

    /**
     * An AIUnit disembarks.
     *
     * @param aiUnit The {@code AIUnit} disembarking.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askDisembark(AIUnit aiUnit) {
        return aiUnit.getAIOwner().askServer()
            .disembark(aiUnit.getUnit()) == ServerAPI.SUCCESS;
    }

    /**
     * An AIUnit embarks.
     *
     * @param aiUnit The {@code AIUnit} carrier.
     * @param unit The {@code Unit} that is embarking.
     * @param direction The {@code Direction} to embark in (may be null).
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askEmbark(AIUnit aiUnit, Unit unit,
                                    Direction direction) {
        return aiUnit.getAIOwner().askServer()
            .embark(unit, aiUnit.getUnit(), direction) == ServerAPI.SUCCESS;
    }

    /**
     * A unit in Europe emigrates.
     *
     * @param aiPlayer The {@code AIPlayer} requiring emigration.
     * @param slot The slot to emigrate from.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askEmigrate(AIPlayer aiPlayer, int slot) {
        return aiPlayer.askServer()
            .emigrate(slot) == ServerAPI.SUCCESS;
    }

    /**
     * Ends the player turn.
     *
     * @param aiPlayer The {@code AIPlayer} ending the turn.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askEndTurn(AIPlayer aiPlayer) {
        return aiPlayer.askServer()
            .endTurn() == ServerAPI.SUCCESS;
    }

    /**
     * Change the role of a unit.
     *
     * @param aiUnit The {@code AIUnit} to equip.
     * @param role The {@code Role} to equip for.
     * @param roleCount The role count.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askEquipForRole(AIUnit aiUnit, Role role,
                                          int roleCount) {
        return aiUnit.getAIOwner().askServer()
            .equipUnitForRole(aiUnit.getUnit(), role, roleCount) == ServerAPI.SUCCESS;
    }

    /**
     * Establishes a mission in the given direction.
     *
     * @param aiUnit The {@code AIUnit} establishing the mission.
     * @param direction The {@code Direction} to move the unit.
     * @param denounce Is this a denunciation?
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askEstablishMission(AIUnit aiUnit,
                                              Direction direction,
                                              boolean denounce) {
        return aiUnit.getAIOwner().askServer()
            .missionary(aiUnit.getUnit(), direction, denounce) == ServerAPI.SUCCESS;
    }

    /**
     * Handle a first contact.
     *
     * @param aiPlayer The {@code AIPlayer} being contacted.
     * @param contactor The contacting {@code Player}.
     * @param contactee The contacted {@code Player}.
     * @param tile The contact {@code Tile}.
     * @param ack The treaty acceptance state.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askFirstContact(AIPlayer aiPlayer, Player contactor,
                                          Player contactee, Tile tile,
                                          boolean ack) {
        return aiPlayer.askServer()
            .firstContact(contactor, contactee, tile, ack) == ServerAPI.SUCCESS;
    }

    /**
     * Makes demands to a colony.  One and only one of goods or gold is valid.
     *
     * @param aiPlayer The {@code AIPlayer} that is demanding or responding.
     * @param unit The {@code Unit} that is demanding.
     * @param colony The {@code Colony} to demand of.
     * @param type The {@code GoodsType} to demand.
     * @param amount The amount of goods to demand.
     * @param result Null if this is the initial demand, true/false if this
     *     is a response.
     * @return True if the message was sent, a non-error reply returned, and
     *     the demand was accepted.
     */
    public static boolean askIndianDemand(AIPlayer aiPlayer, Unit unit,
                                          Colony colony, GoodsType type,
                                          int amount,
                                          IndianDemandAction result) {
        return aiPlayer.askServer()
            .indianDemand(unit, colony, type, amount, result) == ServerAPI.SUCCESS;
    }

    /**
     * An AI unit loads some cargo.
     *
     * @param loc The {@code Location} where the goods are.
     * @param type The {@code GoodsType} to load.
     * @param amount The amount of goods to load.
     * @param aiUnit The {@code AIUnit} that is loading.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askLoadGoods(Location loc, GoodsType type,
                                       int amount, AIUnit aiUnit) {
        return aiUnit.getAIOwner().askServer()
            .loadGoods(loc, type, amount, aiUnit.getUnit()) == ServerAPI.SUCCESS;
    }

    /**
     * An AI unit loots some cargo.
     *
     * @param aiUnit The {@code AIUnit} that is looting.
     * @param defenderId The object identifier of the defending unit.
     * @param goods A list of {@code Goods} to loot.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askLoot(AIUnit aiUnit, String defenderId,
                                  List<Goods> goods) {
        return aiUnit.getAIOwner().askServer()
            .loot(aiUnit.getUnit(), defenderId, goods) == ServerAPI.SUCCESS;
    }

    /**
     * Handle answering the monarch.
     *
     * @param aiPlayer The {@code AIPlayer} that is responding.
     * @param action The {@code MonarchAction} responded to.
     * @param accept Whether the action is accepted or not.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askMonarchAction(AIPlayer aiPlayer,
                                           MonarchAction action,
                                           boolean accept) {
        return aiPlayer.askServer()
            .answerMonarch(action, accept) == ServerAPI.SUCCESS;
    }

    /**
     * Moves an AIUnit in the given direction.
     *
     * @param aiUnit The {@code AIUnit} to move.
     * @param direction The {@code Direction} to move the unit.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askMove(AIUnit aiUnit, Direction direction) {
        return aiUnit.getAIOwner().askServer()
            .move(aiUnit.getUnit(), direction) == ServerAPI.SUCCESS;
    }

    /**
     * Moves an AIUnit across the high seas.
     *
     * @param aiUnit The {@code AIUnit} to move.
     * @param destination The {@code Location} to move to.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askMoveTo(AIUnit aiUnit, Location destination) {
        return aiUnit.getAIOwner().askServer()
            .moveTo(aiUnit.getUnit(), destination) == ServerAPI.SUCCESS;
    }

    /**
     * Gets a nation summary for a player.
     *
     * @param owner The {@code AIPlayer} making the inquiry.
     * @param player The {@code Player} to summarize.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askNationSummary(AIPlayer owner, Player player) {
        return owner.askServer()
            .nationSummary(owner.getPlayer(), player) == ServerAPI.SUCCESS;
    }

    /**
     * A native AIUnit delivers a gift to a colony.
     *
     * @param aiUnit The {@code AIUnit} delivering the gift.
     * @param colony The {@code Colony} to give to.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askNativeGift(AIUnit aiUnit, Colony colony) {
        return aiUnit.getAIOwner().askServer()
            .nativeGift(aiUnit.getUnit(), colony) == ServerAPI.SUCCESS;
    }

    /**
     * Respond to a native trade offer.
     *
     * @param aiPlayer The {@code AIPlayer} that is trading.
     * @param action The {@code NativeTradeAction} to take.
     * @param nt The proposed {@code NativeTrade}.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askNativeTrade(AIPlayer aiPlayer,
        NativeTradeAction action, NativeTrade nt) {
        return aiPlayer.askServer()
            .nativeTrade(action, nt) == ServerAPI.SUCCESS;
    }

    /**
     * Response to discovering the new world.
     *
     * @param aiPlayer The discovering {@code AIPlayer}.
     * @param unit The {@code Unit} that made the discovery.
     * @param name The new land name.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askNewLandName(AIPlayer aiPlayer, Unit unit,
                                         String name) {
        return aiPlayer.askServer()
            .newLandName(unit, name) == ServerAPI.SUCCESS;
    }

    /**
     * Response to discovering a new region.
     *
     * @param aiPlayer The discovering {@code AIPlayer}.
     * @param region The {@code Region} that was discovered.
     * @param tile The {@code Tile} of discovery.
     * @param unit The {@code Unit} that was discovering.
     * @param name The new region name.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askNewRegionName(AIPlayer aiPlayer, Region region,
                                           Tile tile, Unit unit, String name) {
        return aiPlayer.askServer()
            .newRegionName(region, tile, unit, name) == ServerAPI.SUCCESS;
    }

    /**
     * An AIUnit is put outside a colony.
     *
     * @param aiUnit The {@code AIUnit} to put out.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askPutOutsideColony(AIUnit aiUnit) {
        return aiUnit.getAIOwner().askServer()
            .putOutsideColony(aiUnit.getUnit()) == ServerAPI.SUCCESS;
    }

    /**
     * Rearrange an AI colony.
     *
     * @param aiColony The {@code AIColony} to rearrange.
     * @param workers A list of worker {@code Unit}s that may change.
     * @param scratch A copy of the underlying {@code Colony} with the
     *     workers arranged as required.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askRearrangeColony(AIColony aiColony,
                                             List<Unit> workers,
                                             Colony scratch) {
        return aiColony.getAIOwner().askServer()
            .rearrangeColony(aiColony.getColony(), workers, scratch) == ServerAPI.SUCCESS;
    }

    /**
     * An AI unit speaks to the chief of a native settlement.
     *
     * @param aiUnit The {@code AIUnit} that is scouting.
     * @param is The {@code IndianSettlement} to scout.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askScoutSpeakToChief(AIUnit aiUnit,
                                               IndianSettlement is) {
        return aiUnit.getAIOwner().askServer()
            .scoutSpeakToChief(aiUnit.getUnit(), is) == ServerAPI.SUCCESS;
    }

    /**
     * Set the build queue in a colony.
     *
     * @param aiColony The {@code AIColony} that is building.
     * @param queue The list of {@code BuildableType}s to build.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askSetBuildQueue(AIColony aiColony,
                                           List<BuildableType> queue) {
        return aiColony.getAIOwner().askServer()
            .setBuildQueue(aiColony.getColony(), queue) == ServerAPI.SUCCESS;
    }

    /**
     * Train unit in Europe.
     *
     * @param aiPlayer The {@code AIPlayer} requiring training.
     * @param type The {@code UnitType} to train.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askTrainUnitInEurope(AIPlayer aiPlayer,
                                               UnitType type) {
        return aiPlayer.askServer()
            .trainUnitInEurope(type) == ServerAPI.SUCCESS;
    }


    /**
     * An AI unit unloads some cargo.
     *
     * @param type The {@code GoodsType} to unload.
     * @param amount The amount of goods to unload.
     * @param aiUnit The {@code AIUnit} that is unloading.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askUnloadGoods(GoodsType type, int amount,
                                         AIUnit aiUnit) {
        return aiUnit.getAIOwner().askServer()
            .unloadGoods(type, amount, aiUnit.getUnit()) == ServerAPI.SUCCESS;
    }

    /**
     * Set a unit to work in a work location.
     *
     * @param aiUnit The {@code AIUnit} to work.
     * @param workLocation The {@code WorkLocation} to work in.
     * @return True if the message was sent, and a non-error reply returned.
     */
    public static boolean askWork(AIUnit aiUnit, WorkLocation workLocation) {
        return aiUnit.getAIOwner().askServer()
            .work(aiUnit.getUnit(), workLocation) == ServerAPI.SUCCESS;
    }
}
