/**
 *  Copyright (C) 2002-2007  The FreeCol Team
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

package net.sf.freecol.server.control;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.CombatModel;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.EquipmentType;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GameOptions;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.IndianNationType;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.LostCityRumour;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.ModelController;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Modifier;
import net.sf.freecol.common.model.Monarch;
import net.sf.freecol.common.model.Monarch.MonarchAction;
import net.sf.freecol.common.model.Nameable;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.Ownable;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.PlayerExploredTile;
import net.sf.freecol.common.model.Region;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TradeItem;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.model.CombatModel.CombatResult;
import net.sf.freecol.common.model.DiplomaticTrade.TradeStatus;
import net.sf.freecol.common.model.FoundingFather.FoundingFatherType;
import net.sf.freecol.common.model.LostCityRumour.RumourType;
import net.sf.freecol.common.model.Map.Direction;
import net.sf.freecol.common.model.Map.Position;
import net.sf.freecol.common.model.Monarch.MonarchAction;
import net.sf.freecol.common.model.Player.PlayerType;
import net.sf.freecol.common.model.Player.Stance;
import net.sf.freecol.common.model.TradeRoute.Stop;
import net.sf.freecol.common.model.Unit.UnitState;
import net.sf.freecol.common.model.UnitTypeChange.ChangeType;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.DiplomacyMessage;
import net.sf.freecol.common.networking.Message;
import net.sf.freecol.common.util.RandomChoice;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.control.ChangeSet;
import net.sf.freecol.server.control.ChangeSet.See;
import net.sf.freecol.server.model.ServerPlayer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;


/**
 * The main server controller.
 */
public final class InGameController extends Controller {

    private static Logger logger = Logger.getLogger(InGameController.class.getName());

    public static final int SCORE_INDEPENDENCE_DECLARED = 100;

    private final Random random;

    public static final int SCORE_INDEPENDENCE_GRANTED = 1000;

    public int debugOnlyAITurns = 0;

    private java.util.Map<String,java.util.Map<String, java.util.Map<String,Object>>> transactionSessions;
    
    public static enum MigrationType {
        NORMAL,     // Unit decided to migrate
        RECRUIT,    // Player is paying
        FOUNTAIN    // As a result of a Fountain of Youth discovery
    }


    /**
     * The constructor to use.
     * 
     * @param freeColServer The main server object.
     */
    public InGameController(FreeColServer freeColServer) {
        super(freeColServer);

        random = freeColServer.getServerRandom();
        transactionSessions = new HashMap<String,java.util.Map<String, java.util.Map<String,Object>>>();
    }


    /**
     * Get a list of all server players, optionally excluding supplied ones.
     *
     * @param serverPlayers The <code>ServerPlayer</code>s to exclude.
     * @return A list of all connected server players, with exclusions.
     */
    public List<ServerPlayer> getOtherPlayers(ServerPlayer... serverPlayers) {
        List<ServerPlayer> result = new ArrayList<ServerPlayer>();
        outer: for (Player otherPlayer : getGame().getPlayers()) {
            ServerPlayer enemyPlayer = (ServerPlayer) otherPlayer;
            if (!enemyPlayer.isConnected()) continue;
            for (ServerPlayer exclude : serverPlayers) {
                if (enemyPlayer == exclude) continue outer;
            }
            result.add(enemyPlayer);
        }
        return result;
    }


    /**
     * Send a set of changes to all players.
     *
     * @param cs The <code>ChangeSet</code> to send.
     */
    private void sendToAll(ChangeSet cs) {
        sendToList(getOtherPlayers(), cs);
    }

    /**
     * Send an element to all players.
     *
     * @param element The <code>Element</code> to send.
     */
    public void sendToAll(Element element) {
        sendToList(getOtherPlayers(), element);
    }

    /**
     * TODO: deprecated, kill as soon as last users in igih are gone.
     * Send an update to all players except one.
     *
     * @param serverPlayer A <code>ServerPlayer</code> to exclude.
     * @param objects The <code>FreeColGameObject</code>s to update.
     */
    public void sendToOthers(ServerPlayer serverPlayer,
                             FreeColGameObject... objects) {
        ChangeSet cs = new ChangeSet();
        for (FreeColGameObject fcgo : objects) cs.add(See.perhaps(), fcgo);
        sendToOthers(serverPlayer, cs);
    }

    /**
     * Send an update to all players except one.
     *
     * @param serverPlayer A <code>ServerPlayer</code> to exclude.
     * @param cs The <code>ChangeSet</code> encapsulating the update.
     */
    private void sendToOthers(ServerPlayer serverPlayer, ChangeSet cs) {
        sendToList(getOtherPlayers(serverPlayer), cs);
    }

    /**
     * Send an element to all players except one.
     *
     * @param serverPlayer A <code>ServerPlayer</code> to exclude.
     * @param element An <code>Element</code> to send.
     */
    public void sendToOthers(ServerPlayer serverPlayer, Element element) {
        sendToList(getOtherPlayers(serverPlayer), element);
    }

    /**
     * Send an update to a list of players.
     *
     * @param serverPlayers The <code>ServerPlayer</code>s to send to.
     * @param cs The <code>ChangeSet</code> encapsulating the update.
     */
    private void sendToList(List<ServerPlayer> serverPlayers, ChangeSet cs) {
        for (ServerPlayer s : serverPlayers) sendElement(s, cs);
    }

    /**
     * Send an element to a list of players.
     *
     * @param serverPlayers The <code>ServerPlayer</code>s to send to.
     * @param element An <code>Element</code> to send.
     */
    public void sendToList(List<ServerPlayer> serverPlayers, Element element) {
        if (element != null) {
            for (ServerPlayer s : serverPlayers) {
                sendElement(s, element);
            }
        }
    }

    /**
     * Send an element to a specific player.
     *
     * @param serverPlayer The <code>ServerPlayer</code> to update.
     * @param cs A <code>ChangeSet</code> to build an <code>Element</code> with.
     */
    private void sendElement(ServerPlayer serverPlayer, ChangeSet cs) {
        sendElement(serverPlayer, cs.build(serverPlayer));
    }

    /**
     * Send an element to a specific player.
     *
     * @param serverPlayer The <code>ServerPlayer</code> to update.
     * @param element An <code>Element</code> containing the update.
     */
    public void sendElement(ServerPlayer serverPlayer, Element element) {
        if (element != null) {
            try {
                serverPlayer.getConnection().sendAndWait(element);
            } catch (Exception e) {
                logger.warning(e.getMessage());
            }
        }
    }

    /**
     * Ask for a reply from a specific player.
     *
     * @param serverPlayer The <code>ServerPlayer</code> to ask.
     * @param element An <code>Element</code> containing a query.
     */
    public Element askElement(ServerPlayer serverPlayer, Element element) {
        if (element != null) {
            try {
                return serverPlayer.getConnection().ask(element);
            } catch (IOException e) {
                logger.warning(e.getMessage());
            }
        }
        return null;
    }

    /**
     * Change stance and collect changes that need updating.
     *
     * @param player The originating <code>Player</code>.
     * @param stance The new <code>Stance</code>.
     * @param otherPlayer The <code>Player</code> wrt which the stance changes.
     * @param cs A <code>ChangeSet</code> containing the changes.
     * @return True if there was a change in stance at all.
     */
    private boolean changeStance(Player player, Stance stance,
                                 Player otherPlayer, ChangeSet cs) {
        Stance oldStance = player.getStance(otherPlayer);
        if (oldStance == stance) return false;

        // Collect the expected tension modifiers ahead of the stance change.
        int pmodifier, omodifier;
        try {
            pmodifier = oldStance.getTensionModifier(stance);
            omodifier = otherPlayer.getStance(player).getTensionModifier(stance);
        } catch (IllegalStateException e) { // Catch illegal transitions
            logger.warning(e.getMessage());
            return false;
        }
        player.setStance(otherPlayer, stance);
        otherPlayer.setStance(player, stance);

        // Everyone might see the stance change if it meets the stance
        // visibility criteria.
        cs.addStance(See.perhaps(), player, stance, otherPlayer);

        // Stance changing players might see settlement alarm changes.
        if (pmodifier != 0) {
            cs.add(See.only((ServerPlayer) otherPlayer),
                   player.modifyTension(otherPlayer, pmodifier));
        }
        if (omodifier != 0) {
            cs.add(See.only((ServerPlayer) player),
                   otherPlayer.modifyTension(player, omodifier));
        }

        return true;
    }

    /**
     * Change stance and inform all but the originating player.
     *
     * @param player The originating <code>Player</code>.
     * @param stance The new <code>Stance</code>.
     * @param otherPlayer The <code>Player</code> wrt which the stance changes.
     * @return A <code>ChangeSet</code> encapsulating the resulting changes.
     */
    public ChangeSet sendChangeStance(Player player, Stance stance,
                                      Player otherPlayer) {
        ChangeSet cs = new ChangeSet();
        if (changeStance(player, stance, otherPlayer, cs)) {
            sendToOthers((ServerPlayer) player, cs);
        }
        return cs;
    }


    /**
     * Ends the turn of the given player.
     * 
     * @param player The player to end the turn of.
     */
    public void endTurn(ServerPlayer player) {
        /* BEGIN FIX
         * 
         * TODO: Remove this temporary fix for bug:
         *       [ 1709196 ] Waiting for next turn (inifinite wait)
         *       
         *       This fix can be removed when FIFO ordering of
         *       of network messages is working correctly.
         *       (scheduled to be fixed as part of release 0.8.0)
         */
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {}
        // END FIX
        
        FreeColServer freeColServer = getFreeColServer();
        ServerPlayer oldPlayer = (ServerPlayer) getGame().getCurrentPlayer();
        
        if (oldPlayer != player) {
            throw new IllegalArgumentException("It is not "
                + player.getName() + "'s turn, it is "
                + ((oldPlayer == null) ? "noone" : oldPlayer.getName()) + "'s!");
        }
        
        player.clearModelMessages();
        freeColServer.getModelController().clearTaskRegister();

        Player winner = checkForWinner();
        if (winner != null && (!freeColServer.isSingleplayer() || !winner.isAI())) {
            Element gameEndedElement = Message.createNewRootElement("gameEnded");
            gameEndedElement.setAttribute("winner", winner.getId());
            sendToAll(gameEndedElement);
            
            // TODO: Remove when the server can properly revert to a pre-game state:
            if (FreeCol.getFreeColClient() == null) {
                new Timer(true).schedule(new TimerTask() {
                    @Override
                    public void run() {
                        System.exit(0);
                    }
                }, 20000);
            }
            return;
        }
        
        ServerPlayer newPlayer = (ServerPlayer) nextPlayer();
        
        if (newPlayer != null 
            && !newPlayer.isAI()
            && (!newPlayer.isConnected() || debugOnlyAITurns > 0)) {
            endTurn(newPlayer);
            return;
        }
    }

    /**
     * Remove a standard yearly amount of storable goods, and
     * a random extra amount of a random type.
     * Send the market and change messages to the player.
     * This method is public so it can be use in the Market test code.
     *
     * @param serverPlayer The <code>ServerPlayer</code> whose market
     *            is to be updated.
     */
    public void yearlyGoodsRemoval(ServerPlayer serverPlayer) {
        ChangeSet cs = new ChangeSet();
        List<GoodsType> goodsTypes = getGame().getSpecification().getGoodsTypeList();
        Market market = serverPlayer.getMarket();

        // Pick a random type of goods to remove an extra amount of.
        GoodsType removeType;
        do {
            int randomGoods = random.nextInt(goodsTypes.size());
            removeType = goodsTypes.get(randomGoods);
        } while (!removeType.isStorable());

        // Remove standard amount, and the extra amount.
        for (GoodsType type : goodsTypes) {
            if (type.isStorable() && market.hasBeenTraded(type)) {
                int amount = getGame().getTurn().getNumber() / 10;
                if (type == removeType && amount > 0) {
                    amount += random.nextInt(2 * amount + 1);
                }
                if (amount > 0) {
                    market.addGoodsToMarket(type, -amount);
                }
            }
            if (market.hasPriceChanged(type)) {
                cs.addMessage(See.only(serverPlayer),
                              market.makePriceChangeMessage(type));
                market.flushPriceChange(type);
            }
        }

        // Update the client
        cs.add(See.only(serverPlayer), market);
        sendElement(serverPlayer, cs);
    }

    /**
     * Kill a player, remove any leftovers, inform all players.
     *
     * @param serverPlayer The <code>ServerPlayer</code> to kill.
     */
    private void killPlayer(ServerPlayer serverPlayer) {
        ChangeSet cs = new ChangeSet();

        // Mark the player as dead.
        serverPlayer.setDead(true);
        cs.addDead(serverPlayer);

        // Notify everyone.
        cs.addMessage(See.all(),
            new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                             ((serverPlayer.isEuropean())
                              ? "model.diplomacy.dead.european"
                              : "model.diplomacy.dead.native"),
                             serverPlayer)
                .addStringTemplate("%nation%", serverPlayer.getNationName()));

        // Clean up missions
        if (serverPlayer.isEuropean()) {
            for (ServerPlayer other : getOtherPlayers(serverPlayer)) {
                if (!other.isIndian()) continue;
                for (IndianSettlement s : other.getIndianSettlements()) {
                    Unit unit = s.getMissionary();
                    if (unit != null && unit.getOwner() == serverPlayer) {
                        s.setMissionary(null);
                        cs.addDispose(serverPlayer, s.getTile(), unit);
                    }
                }
            }
        }

        // Remove settlements.  Update formerly owned tiles.
        for (Settlement settlement : serverPlayer.getSettlements()) {
            for (Tile tile : settlement.getOwnedTiles()) {
                if (tile != settlement.getTile()) {
                    cs.add(See.only(serverPlayer), tile);
                }
            }
            cs.addDispose(serverPlayer, settlement.getTile(), settlement);
        }

        // Remove units
        for (Unit unit : serverPlayer.getUnits()) {
            cs.addDispose(serverPlayer, unit.getLocation(), unit);
        }

        // Everyone sees players leave.
        sendToAll(cs);
    }


    /**
     * Sets a new current player and notifies the clients.
     * @return The new current player.
     */
    private Player nextPlayer() {
        if (!isHumanPlayersLeft()) {
            getGame().setCurrentPlayer(null);
            return null;
        }
        
        if (getGame().isNextPlayerInNewTurn()) {
            getGame().newTurn();
            if (getGame().getTurn().getAge() > 1
                && !getGame().getSpanishSuccession()) {
                checkSpanishSuccession();
            }
            if (debugOnlyAITurns > 0) {
                debugOnlyAITurns--;
            }
            Element newTurnElement = Message.createNewRootElement("newTurn");
            sendToAll(newTurnElement);
        }
        
        ServerPlayer newPlayer = (ServerPlayer) getGame().getNextPlayer();
        getGame().setCurrentPlayer(newPlayer);
        if (newPlayer == null) {
            getGame().setCurrentPlayer(null);
            return null;
        }
        
        synchronized (newPlayer) {
            if (newPlayer.checkForDeath()) {
                killPlayer(newPlayer);
                logger.info(newPlayer.getNation() + " is dead.");
                return nextPlayer();
            }
        }
        
        if (newPlayer.isEuropean()) {
            yearlyGoodsRemoval(newPlayer);

            if (newPlayer.getCurrentFather() == null
                && newPlayer.getSettlements().size() > 0) {
                chooseFoundingFather(newPlayer);
            }

            if (newPlayer.getMonarch() != null && newPlayer.isConnected()) {
                List<RandomChoice<MonarchAction>> choices
                    = newPlayer.getMonarch().getActionChoices();
                final ServerPlayer player = newPlayer;
                final MonarchAction action
                    = (choices == null) ? MonarchAction.NO_ACTION
                    : RandomChoice.getWeightedRandom(random, choices);
                Thread t = new Thread("monarchAction") {
                        public void run() {
                            try {
                                monarchAction(player, action);
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "Monarch action failed!", e);
                            }
                        }
                    };
                t.start();
            }
            bombardEnemyShips(newPlayer);
        }
        else if (newPlayer.isIndian()) {
            
            for (IndianSettlement indianSettlement: newPlayer.getIndianSettlements()) {
                if (indianSettlement.checkForNewMissionaryConvert()) {
                    // an Indian brave gets converted by missionary
                    Unit missionary = indianSettlement.getMissionary();
                    ServerPlayer european = (ServerPlayer) missionary.getOwner();
                    // search for a nearby colony
                    Tile settlementTile = indianSettlement.getTile();
                    Tile targetTile = null;
                    Iterator<Position> ffi = getGame().getMap().getFloodFillIterator(settlementTile.getPosition());
                    while (ffi.hasNext()) {
                        Tile t = getGame().getMap().getTile(ffi.next());
                        if (settlementTile.getDistanceTo(t) > IndianSettlement.MAX_CONVERT_DISTANCE) {
                            break;
                        }
                        if (t.getSettlement() != null && t.getSettlement().getOwner() == european) {
                            targetTile = t;
                            break;
                        }
                    }
        
                    if (targetTile != null) {
                        
                        List<UnitType> converts = getGame().getSpecification().getUnitTypesWithAbility("model.ability.convert");
                        if (converts.size() > 0) {
                            // perform the conversion from brave to convert in the server
                            Unit brave = indianSettlement.getUnitIterator().next();
                            String nationId = brave.getOwner().getNationID();
                            brave.dispose();
                            ModelController modelController = getGame().getModelController();
                            int random = modelController.getRandom(indianSettlement.getId() + "getNewConvertType", converts.size());
                            UnitType unitType = converts.get(random);
                            Unit unit = modelController.createUnit(indianSettlement.getId() + "newTurn100missionary", targetTile,
                                                                   european, unitType);
                            // and send update information to the client
                            try {
                                Element updateElement = Message.createNewRootElement("newConvert");
                                updateElement.setAttribute("nation", nationId);
                                updateElement.setAttribute("colonyTile", targetTile.getId());
                                updateElement.appendChild(unit.toXMLElement(european,updateElement.getOwnerDocument()));
                                european.getConnection().send(updateElement);
                                logger.info("New convert created for " + european.getName() + " with ID=" + unit.getId());
                            } catch (IOException e) {
                                logger.warning("Could not send message to: " + european.getName());
                            }
                        }
                    }
                }
            }
        }
        
        Element setCurrentPlayerElement = Message.createNewRootElement("setCurrentPlayer");
        setCurrentPlayerElement.setAttribute("player", newPlayer.getId());
        sendToAll(setCurrentPlayerElement);
        
        return newPlayer;
    }

    private void checkSpanishSuccession() {
        boolean rebelMajority = false;
        Player weakestAIPlayer = null;
        Player strongestAIPlayer = null;
        java.util.Map<Player, Element> documentMap = new HashMap<Player, Element>();
        for (Player player : getGame().getPlayers()) {
            documentMap.put(player, Message.createNewRootElement("spanishSuccession"));
            if (player.isEuropean()) {
                if (player.isAI() && !player.isREF()) {
                    if (weakestAIPlayer == null
                        || weakestAIPlayer.getScore() > player.getScore()) {
                        weakestAIPlayer = player;
                    }
                    if (strongestAIPlayer == null
                        || strongestAIPlayer.getScore() < player.getScore()) {
                        strongestAIPlayer = player;
                    }
                } else if (player.getSoL() > 50) {
                    rebelMajority = true;
                }
            }
        }

        if (rebelMajority
            && weakestAIPlayer != null
            && strongestAIPlayer != null
            && weakestAIPlayer != strongestAIPlayer) {
            documentMap.remove(weakestAIPlayer);
            for (Element element : documentMap.values()) {
                element.setAttribute("loser", weakestAIPlayer.getId());
                element.setAttribute("winner", strongestAIPlayer.getId());
            }
            for (Colony colony : weakestAIPlayer.getColonies()) {
                colony.changeOwner(strongestAIPlayer);
                for (Entry<Player, Element> entry : documentMap.entrySet()) {
                    if (entry.getKey().canSee(colony.getTile())) {
                        entry.getValue().appendChild(colony.toXMLElement(entry.getKey(),
                                                                         entry.getValue().getOwnerDocument()));
                    }
                }
            }
            for (Unit unit : weakestAIPlayer.getUnits()) {
                unit.setOwner(strongestAIPlayer);
                for (Entry<Player, Element> entry : documentMap.entrySet()) {
                    if (entry.getKey().canSee(unit.getTile())) {
                        entry.getValue().appendChild(unit.toXMLElement(entry.getKey(),
                                                                       entry.getValue().getOwnerDocument()));
                    }
                }
            }
            for (Entry<Player, Element> entry : documentMap.entrySet()) {
                try {
                    ((ServerPlayer) entry.getKey()).getConnection().send(entry.getValue());
                } catch (IOException e) {
                    logger.warning("Could not send message to: " + entry.getKey().getName());
                }
            }
            weakestAIPlayer.setDead(true);
            getGame().setSpanishSuccession(true);
        }
    }
    
    private boolean isHumanPlayersLeft() {
        for (Player player : getFreeColServer().getGame().getPlayers()) {
            if (!player.isDead() && !player.isAI() && ((ServerPlayer) player).isConnected()) {
                return true;
            }
        }
        return false;
    }

    private void chooseFoundingFather(ServerPlayer player) {
        final ServerPlayer nextPlayer = player;
        Thread t = new Thread(FreeCol.SERVER_THREAD+"FoundingFather-thread") {
                public void run() {
                    List<FoundingFather> randomFoundingFathers = getRandomFoundingFathers(nextPlayer);
                    boolean atLeastOneChoice = false;
                    Element chooseFoundingFatherElement = Message.createNewRootElement("chooseFoundingFather");
                    for (FoundingFather father : randomFoundingFathers) {
                        chooseFoundingFatherElement.setAttribute(father.getType().toString(),
                                                                 father.getId());
                        atLeastOneChoice = true;
                    }
                    if (!atLeastOneChoice) {
                        nextPlayer.setCurrentFather(null);
                    } else {
                        Connection conn = nextPlayer.getConnection();
                        if (conn != null) {
                            try {
                                Element reply = conn.ask(chooseFoundingFatherElement);
                                FoundingFather father = getGame().getSpecification().
                                    getFoundingFather(reply.getAttribute("foundingFather"));
                                if (!randomFoundingFathers.contains(father)) {
                                    throw new IllegalArgumentException();
                                }
                                nextPlayer.setCurrentFather(father);
                            } catch (IOException e) {
                                logger.warning("Could not send message to: " + nextPlayer.getName());
                            }
                        }
                    }
                }
            };
        t.start();
    }

    /**
     * Build a list of random FoundingFathers, one per type.
     * Do not include any the player has or are not available.
     * 
     * @param player The <code>Player</code> that should pick a founding
     *            father from this list.
     * @return A list of FoundingFathers.
     */
    private List<FoundingFather> getRandomFoundingFathers(Player player) {
        // Build weighted random choice for each father type
        Specification spec = getGame().getSpecification();
        int age = getGame().getTurn().getAge();
        EnumMap<FoundingFatherType, List<RandomChoice<FoundingFather>>> choices
            = new EnumMap<FoundingFatherType,
            List<RandomChoice<FoundingFather>>>(FoundingFatherType.class);
        for (FoundingFather father : spec.getFoundingFathers()) {
            if (!player.hasFather(father) && father.isAvailableTo(player)) {
                FoundingFatherType type = father.getType();
                List<RandomChoice<FoundingFather>> rc = choices.get(type);
                if (rc == null) {
                    rc = new ArrayList<RandomChoice<FoundingFather>>();
                }
                int weight = father.getWeight(age);
                rc.add(new RandomChoice<FoundingFather>(father, weight));
                choices.put(father.getType(), rc);
            }
        }

        // Select one from each father type
        List<FoundingFather> randomFathers = new ArrayList<FoundingFather>();
        String logMessage = "Random fathers";
        for (FoundingFatherType type : FoundingFatherType.values()) {
            List<RandomChoice<FoundingFather>> rc = choices.get(type);
            if (rc != null) {
                FoundingFather father = RandomChoice.getWeightedRandom(random, rc);
                randomFathers.add(father);
                logMessage += ":" + father.getNameKey();
            }
        }
        logger.info(logMessage);
        return randomFathers;
    }

    /**
     * Checks if anybody has won the game and returns that player.
     * 
     * @return The <code>Player</code> who have won the game or <i>null</i>
     *         if the game is not finished.
     */
    public Player checkForWinner() {
        List<Player> players = getGame().getPlayers();
        GameOptions go = getGame().getGameOptions();
        if (go.getBoolean(GameOptions.VICTORY_DEFEAT_REF)) {
            for (Player player : players) {
                if (!player.isAI() && player.getPlayerType() == PlayerType.INDEPENDENT) {
                    return player;
                }
            }
        }
        if (go.getBoolean(GameOptions.VICTORY_DEFEAT_EUROPEANS)) {
            Player winner = null;
            for (Player player : players) {
                if (!player.isDead() && player.isEuropean() && !player.isREF()) {
                    if (winner != null) {
                        // There is more than one european player alive:
                        winner = null;
                        break;
                    } else {
                        winner = player;
                    }
                }
            }
            if (winner != null) {
                return winner;
            }
        }
        if (go.getBoolean(GameOptions.VICTORY_DEFEAT_HUMANS)) {
            Player winner = null;
            for (Player player : players) {
                if (!player.isDead() && !player.isAI()) {
                    if (winner != null) {
                        // There is more than one human player alive:
                        winner = null;
                        break;
                    } else {
                        winner = player;
                    }
                }
            }
            if (winner != null) {
                return winner;
            }
        }
        return null;
    }

    /**
     * Performs the monarchs actions.
     * 
     * @param serverPlayer The <code>ServerPlayer</code> whose monarch
     *            is acting.
     * @param action The monarch action.
     */
    private void monarchAction(ServerPlayer serverPlayer, MonarchAction action) {
        Specification spec = getGame().getSpecification();
        Monarch monarch = serverPlayer.getMonarch();
        Connection conn = serverPlayer.getConnection();
        int turn = getGame().getTurn().getNumber();
        Element monarchActionElement = Message.createNewRootElement("monarchAction");
        monarchActionElement.setAttribute("action", String.valueOf(action));

        switch (action) {
        case RAISE_TAX:
            int oldTax = serverPlayer.getTax();
            int newTax = monarch.raiseTax(random);
            Goods goods = serverPlayer.getMostValuableGoods();
            if (goods == null) return;
            monarchActionElement.setAttribute("amount", String.valueOf(newTax));
            // TODO: don't use localized name
            monarchActionElement.setAttribute("goods", Messages.message(goods.getNameKey()));
            monarchActionElement.setAttribute("force", String.valueOf(false));
            try {
                serverPlayer.setTax(newTax); // to avoid cheating
                Element reply = conn.ask(monarchActionElement);
                boolean accepted = Boolean.valueOf(reply.getAttribute("accepted")).booleanValue();
                if (!accepted) {
                    Colony colony = (Colony) goods.getLocation();
                    if (colony.getGoodsCount(goods.getType()) >= goods.getAmount()) {
                        serverPlayer.setTax(oldTax); // player hasn't accepted, restoring tax
                        Element removeGoodsElement = Message.createNewRootElement("removeGoods");
                        colony.removeGoods(goods);
                        serverPlayer.setArrears(goods);
                        colony.getFeatureContainer().addModifier(Modifier
                                                                 .createTeaPartyModifier(getGame().getTurn()));
                        removeGoodsElement.appendChild(goods.toXMLElement(serverPlayer, removeGoodsElement
                                                                          .getOwnerDocument()));
                        conn.send(removeGoodsElement);
                    } else {
                        // player has cheated and removed goods from colony, don't restore tax
                        monarchActionElement.setAttribute("force", String.valueOf(true));
                        conn.send(monarchActionElement);
                    }
                }
            } catch (IOException e) {
                logger.warning("Could not send message to: " + serverPlayer.getName());
            }
            break;
        case LOWER_TAX:
            int lowerTax = monarch.lowerTax(random);
            monarchActionElement.setAttribute("amount", String.valueOf(lowerTax));
            try {
                serverPlayer.setTax(lowerTax); // to avoid cheating
                conn.send(monarchActionElement);
            } catch (IOException e) {
                logger.warning("Could not send message to: " + serverPlayer.getName());
            }
            break;
        case ADD_TO_REF:
            List<AbstractUnit> unitsToAdd = monarch.addToREF(random);
            monarch.addToREF(unitsToAdd);
            Element additionElement = monarchActionElement.getOwnerDocument().createElement("addition");
            for (AbstractUnit unit : unitsToAdd) {
                additionElement.appendChild(unit.toXMLElement(serverPlayer,additionElement.getOwnerDocument()));
            }
            monarchActionElement.appendChild(additionElement);
            try {
                conn.send(monarchActionElement);
            } catch (IOException e) {
                logger.warning("Could not send message to: " + serverPlayer.getName());
            }
            break;
        case DECLARE_WAR:
            Player enemy = monarch.declareWar(random);
            if (enemy == null) { // this should not happen
                logger.warning("Declared war on nobody.");
                return;
            }
            monarchActionElement.setAttribute("enemy", enemy.getId());
            ChangeSet cs = sendChangeStance(serverPlayer, Stance.WAR, enemy);
            sendElement(serverPlayer, cs);
            // TODO glom onto monarch element.
            break;
            /** TODO: restore
                case Monarch.SUPPORT_LAND:
                int[] additions = monarch.supportLand();
                createUnits(additions, monarchActionElement, serverPlayer);
                try {
                conn.send(monarchActionElement);
                } catch (IOException e) {
                logger.warning("Could not send message to: " + serverPlayer.getName());
                }
                break;
                case Monarch.SUPPORT_SEA:
                // TODO: make this generic
                UnitType unitType = getGame().getSpecification().getUnitType("model.unit.frigate");
                newUnit = new Unit(getGame(), serverPlayer.getEurope(), serverPlayer, unitType, UnitState.ACTIVE);
                //serverPlayer.getEurope().add(newUnit);
                monarchActionElement.appendChild(newUnit.toXMLElement(serverPlayer, monarchActionElement
                .getOwnerDocument()));
                try {
                conn.send(monarchActionElement);
                } catch (IOException e) {
                logger.warning("Could not send message to: " + serverPlayer.getName());
                }
                break;
            */
        case OFFER_MERCENARIES:
            Element mercenaryElement = monarchActionElement.getOwnerDocument().createElement("mercenaries");
            List<AbstractUnit> units = monarch.getMercenaries(random);
            int price = monarch.getPrice(units, true);
            monarchActionElement.setAttribute("price", String.valueOf(price));
            for (AbstractUnit unit : units) {
                mercenaryElement.appendChild(unit.toXMLElement(monarchActionElement.getOwnerDocument()));
            }
            monarchActionElement.appendChild(mercenaryElement);
            try {
                Element reply = conn.ask(monarchActionElement);
                boolean accepted = Boolean.valueOf(reply.getAttribute("accepted")).booleanValue();
                if (accepted) {
                    Element updateElement = Message.createNewRootElement("monarchAction");
                    updateElement.setAttribute("action", String.valueOf(MonarchAction.ADD_UNITS));
                    serverPlayer.modifyGold(-price);
                    createUnits(units, updateElement, serverPlayer);
                    conn.send(updateElement);
                }
            } catch (IOException e) {
                logger.warning("Could not send message to: " + serverPlayer.getName());
            }
            break;
        case NO_ACTION:
            // nothing to do here. :-)
            break;
        }
    }

    /**
     * Create the Royal Expeditionary Force player corresponding to
     * a given player that is about to rebel.
     *
     * @param serverPlayer The <code>ServerPlayer</code> about to rebel.
     * @return The REF player.
     */
    public ServerPlayer createREFPlayer(ServerPlayer serverPlayer) {
        Nation refNation = serverPlayer.getNation().getRefNation();
        ServerPlayer refPlayer = getFreeColServer().addAIPlayer(refNation);
        refPlayer.setEntryLocation(serverPlayer.getEntryLocation());
        Player.makeContact(serverPlayer, refPlayer); // Will change, setup only
        createREFUnits(serverPlayer, refPlayer);
        return refPlayer;
    }
    
    public List<Unit> createREFUnits(ServerPlayer player, ServerPlayer refPlayer){
        EquipmentType muskets = getGame().getSpecification().getEquipmentType("model.equipment.muskets");
        EquipmentType horses = getGame().getSpecification().getEquipmentType("model.equipment.horses");
        
        List<Unit> unitsList = new ArrayList<Unit>();
        List<Unit> navalUnits = new ArrayList<Unit>();
        List<Unit> landUnits = new ArrayList<Unit>();
        
        // Create naval units
        for (AbstractUnit unit : player.getMonarch().getNavalUnits()) {
            for (int index = 0; index < unit.getNumber(); index++) {
                Unit newUnit = new Unit(getGame(), refPlayer.getEurope(), refPlayer,
                                        unit.getUnitType(getGame().getSpecification()),
                                        UnitState.TO_AMERICA);
                navalUnits.add(newUnit);
            }
        }
        unitsList.addAll(navalUnits);
        
        // Create land units
        for (AbstractUnit unit : player.getMonarch().getLandUnits()) {
            EquipmentType[] equipment = EquipmentType.NO_EQUIPMENT;
            switch(unit.getRole()) {
            case SOLDIER:
                equipment = new EquipmentType[] { muskets };
                break;
            case DRAGOON:
                equipment = new EquipmentType[] { horses, muskets };
                break;
            default:
            }
            for (int index = 0; index < unit.getNumber(); index++) {
                landUnits.add(new Unit(getGame(), refPlayer.getEurope(), refPlayer,
                                       unit.getUnitType(getGame().getSpecification()),
                                       UnitState.ACTIVE, equipment));
            }
        }
        unitsList.addAll(landUnits);
            
        // Board land units
        Iterator<Unit> carriers = navalUnits.iterator();
        for(Unit unit : landUnits){
            //cycle through the naval units to find a carrier for this unit
            
            // check if there is space for this unit
            boolean noSpaceForUnit=true;
            for(Unit carrier : navalUnits){
                if (unit.getSpaceTaken() <= carrier.getSpaceLeft()) {
                    noSpaceForUnit=false;
                    break;
                }
            }
            // There is no space for this unit, stays in Europe
            if(noSpaceForUnit){
                continue;
            }
            // Find carrier
            Unit carrier = null;
            while (carrier == null){
                // got to the end of the list, restart
                if (!carriers.hasNext()) {
                    carriers = navalUnits.iterator();
                }
                carrier = carriers.next();
                // this carrier cant carry this unit
                if (unit.getSpaceTaken() > carrier.getSpaceLeft()) {
                    carrier = null;
                }
            }
            // set unit aboard carrier
            unit.setLocation(carrier);
            //XXX: why only the units that can be aboard are sent to the player?
            //unitsList.add(unit);
        }
        return unitsList;
    }

    private void createUnits(List<AbstractUnit> units, Element element, ServerPlayer nextPlayer) {
        String musketsTypeStr = null;
        String horsesTypeStr = null;
        if(nextPlayer.isIndian()){
            musketsTypeStr = "model.equipment.indian.muskets";
            horsesTypeStr = "model.equipment.indian.horses";
        } else {
            musketsTypeStr = "model.equipment.muskets";
            horsesTypeStr = "model.equipment.horses";
        }

        final EquipmentType muskets = getGame().getSpecification().getEquipmentType(musketsTypeStr);
        final EquipmentType horses = getGame().getSpecification().getEquipmentType(horsesTypeStr);

        EquipmentType[] soldier = new EquipmentType[] { muskets };
        EquipmentType[] dragoon = new EquipmentType[] { horses, muskets };
        for (AbstractUnit unit : units) {
            EquipmentType[] equipment = EquipmentType.NO_EQUIPMENT;
            for (int count = 0; count < unit.getNumber(); count++) {
                switch(unit.getRole()) {
                case SOLDIER:
                    equipment = soldier;
                    break;
                case DRAGOON:
                    equipment = dragoon;
                    break;
                default:
                }
                Unit newUnit = new Unit(getGame(), nextPlayer.getEurope(), nextPlayer,
                                        unit.getUnitType(getGame().getSpecification()), UnitState.ACTIVE, equipment);
                //nextPlayer.getEurope().add(newUnit);
                if (element != null) {
                    element.appendChild(newUnit.toXMLElement(nextPlayer, element.getOwnerDocument()));
                }
            }
        }
    }

    private void bombardEnemyShips(ServerPlayer currentPlayer) {
        logger.finest("Entering method bombardEnemyShips.");
        Map map = getFreeColServer().getGame().getMap();
        CombatModel combatModel = getFreeColServer().getGame().getCombatModel();
        for (Settlement settlement : currentPlayer.getSettlements()) {
            Colony colony = (Colony) settlement;
            
            if (!colony.canBombardEnemyShip()){
            	continue;
            }

            logger.fine("Colony " + colony.getName() + " can bombard enemy ships.");
            Position colonyPosition = colony.getTile().getPosition();
            for (Direction direction : Direction.values()) {
            	Tile tile = map.getTile(colonyPosition.getAdjacent(direction));

            	// ignore land tiles and borders
            	if(tile == null || tile.isLand()){
            		continue;
            	}

            	// Go through the units in the tile
            	// a new list must be created, since the original may be changed while iterating
            	List<Unit> unitList = new ArrayList<Unit>(tile.getUnitList());
            	for(Unit unit : unitList){
                    logger.fine(colony.getName() + " found unit : " + unit.toString());
            		// we need to save the tile of the unit
            		//before the location of the unit can change
            		Tile unitTile = unit.getTile();
            		
            		Player player = unit.getOwner();

            		// ignore own units
            		if(player == currentPlayer){
            			continue;
            		}

            		// ignore friendly units
            		if (!currentPlayer.atWarWith(player)
                    && !unit.hasAbility("model.ability.piracy")) {
                    logger.warning(colony.getName()
                                   + " found unit to not bombard: "
                                   + unit.toString());
                    continue;
            		}

            		logger.warning(colony.getName() + " found enemy unit to bombard: " +
                                       unit.toString());
            		// generate bombardment result
            		CombatModel.CombatResult result = combatModel.generateAttackResult(colony, unit);

            		// ship was damaged, get repair location
            		Location repairLocation = null;
            		if(result.type == CombatModel.CombatResultType.WIN){
            			repairLocation = player.getRepairLocation(unit);
            		}

            		// update server data
            		getGame().getCombatModel().bombard(colony, unit, result, repairLocation);

            		// Inform the players (other then the player
            		// attacking) about the attack:
            		int plunderGold = -1;
            		Iterator<Player> enemyPlayerIterator = getFreeColServer().getGame().getPlayerIterator();
            		while (enemyPlayerIterator.hasNext()) {
            			ServerPlayer enemyPlayer = (ServerPlayer) enemyPlayerIterator.next();

            			if (enemyPlayer.getConnection() == null) {
            				continue;
            			}

            			// unit tile not visible to player, move to next player
            			if(!enemyPlayer.canSee(unitTile)){
            				continue;
            			}

            			Element opponentAttackElement = Message.createNewRootElement("opponentAttack");                                 
            			opponentAttackElement.setAttribute("direction", direction.toString());
            			opponentAttackElement.setAttribute("result", result.type.toString());
            			opponentAttackElement.setAttribute("plunderGold", Integer.toString(plunderGold));
            			opponentAttackElement.setAttribute("colony", colony.getId());
            			opponentAttackElement.setAttribute("defender", unit.getId());
            			opponentAttackElement.setAttribute("damage", String.valueOf(result.damage));

            			// Add repair location to defending player
            			if(enemyPlayer == player && repairLocation != null){
            				opponentAttackElement.setAttribute("repairIn", repairLocation.getId());
            			}

            			// Every player who witness the confrontation needs to know about the attacker
            			if (!enemyPlayer.canSee(colony.getTile())) {
            				opponentAttackElement.setAttribute("update", "tile");
            				enemyPlayer.setExplored(colony.getTile());
            				opponentAttackElement.appendChild(colony.getTile().toXMLElement(
            						enemyPlayer, opponentAttackElement.getOwnerDocument()));
            			}

            			// Send response
            			try {
            				enemyPlayer.getConnection().send(opponentAttackElement);
            			} catch (IOException e) {
            				logger.warning("Could not send message to: " + enemyPlayer.getName()
            						+ " with connection " + enemyPlayer.getConnection());
            			}
            		}
                }
            }
        }
    }
    
    /**
     * Cash in a treasure train.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is cashing in.
     * @param unit The treasure train <code>Unit</code> to cash in.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element cashInTreasureTrain(ServerPlayer serverPlayer, Unit unit) {
        ChangeSet cs = new ChangeSet();

        // Work out the cash in amount.
        int fullAmount = unit.getTreasureAmount();
        int cashInAmount = (fullAmount - unit.getTransportFee())
            * (100 - serverPlayer.getTax()) / 100;
        serverPlayer.modifyGold(cashInAmount);
        cs.addPartial(See.only(serverPlayer), serverPlayer, "gold", "score");

        // Generate a suitable message.
        String messageId = (serverPlayer.getPlayerType() == PlayerType.REBEL
                            || serverPlayer.getPlayerType() == PlayerType.INDEPENDENT)
            ? "model.unit.cashInTreasureTrain.independent"
            : "model.unit.cashInTreasureTrain.colonial";
        cs.addMessage(See.only(serverPlayer),
            new ModelMessage(messageId, serverPlayer, unit)
                .addAmount("%amount%", fullAmount)
                .addAmount("%cashInAmount%", cashInAmount));

        // Dispose of the unit.
        cs.addDispose(serverPlayer, unit.getLocation(), unit);

        // Others can not see cash-ins which happen in colonies or Europe.
        return cs.build(serverPlayer);
    }


    /**
     * Declare independence.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is naming.
     * @param nationName The new name for the independent nation.
     * @param countryName The new name for its residents.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element declareIndependence(ServerPlayer serverPlayer,
                                       String nationName, String countryName) {
        ChangeSet cs = new ChangeSet();

        // Cross the Rubicon
        StringTemplate oldNation = serverPlayer.getNationName();
        serverPlayer.setIndependentNationName(nationName);
        serverPlayer.setNewLandName(countryName);
        serverPlayer.setPlayerType(PlayerType.REBEL);
        serverPlayer.getFeatureContainer().addAbility(new Ability("model.ability.independenceDeclared"));
        serverPlayer.modifyScore(SCORE_INDEPENDENCE_DECLARED);

        // Do not add history event to cs as we are going to update the
        // entire player.  Likewise clear model messages.
        Turn turn = getGame().getTurn();
        serverPlayer.addHistory(new HistoryEvent(turn,
                HistoryEvent.EventType.DECLARE_INDEPENDENCE));
        serverPlayer.clearModelMessages();

        // Dispose of units in Europe.
        Europe europe = serverPlayer.getEurope();
        StringTemplate seized = StringTemplate.label(", ");
        for (Unit u : europe.getUnitList()) {
            seized.addStringTemplate(u.getLabel());
        }
        if (!seized.getReplacements().isEmpty()) {
            cs.addMessage(See.only(serverPlayer),
                new ModelMessage(ModelMessage.MessageType.UNIT_LOST,
                                 "model.player.independence.unitsSeized",
                                 serverPlayer)
                    .addStringTemplate("%units%", seized));
        }

        // Generalized continental army muster
        java.util.Map<UnitType, UnitType> upgrades
            = new HashMap<UnitType, UnitType>();
        Specification spec = getGame().getSpecification();
        for (UnitType unitType : spec.getUnitTypeList()) {
            UnitType upgrade = unitType.getUnitTypeChange(ChangeType.INDEPENDENCE,
                                                          serverPlayer);
            if (upgrade != null) {
                upgrades.put(unitType, upgrade);
            }
        }
        for (Colony colony : serverPlayer.getColonies()) {
            int sol = colony.getSoL();
            if (sol > 50) {
                java.util.Map<UnitType, List<Unit>> unitMap = new HashMap<UnitType, List<Unit>>();
                List<Unit> allUnits = new ArrayList<Unit>(colony.getTile().getUnitList());
                allUnits.addAll(colony.getUnitList());
                for (Unit unit : allUnits) {
                    if (upgrades.containsKey(unit.getType())) {
                        List<Unit> unitList = unitMap.get(unit.getType());
                        if (unitList == null) {
                            unitList = new ArrayList<Unit>();
                            unitMap.put(unit.getType(), unitList);
                        }
                        unitList.add(unit);
                    }
                }
                for (Entry<UnitType, List<Unit>> entry : unitMap.entrySet()) {
                    int limit = (entry.getValue().size() + 2) * (sol - 50) / 100;
                    if (limit > 0) {
                        for (int index = 0; index < limit; index++) {
                            Unit unit = entry.getValue().get(index);
                            if (unit == null) break;
                            unit.setType(upgrades.get(entry.getKey()));
                            cs.add(See.only(serverPlayer), unit);
                        }
                        cs.addMessage(See.only(serverPlayer),
                            new ModelMessage(ModelMessage.MessageType.UNIT_IMPROVED,
                                             "model.player.continentalArmyMuster",
                                             serverPlayer, colony)
                                .addName("%colony%", colony.getName())
                                .addAmount("%number%", limit)
                                .add("%oldUnit%", entry.getKey().getNameKey())
                                .add("%unit%", upgrades.get(entry.getKey()).getNameKey()));
                    }
                }
            }
        }

        // Create the REF.
        ServerPlayer refPlayer = createREFPlayer(serverPlayer);

        // Now the REF is ready, we can dispose of the European connection.
        cs.addDispose(serverPlayer, null, serverPlayer.getEurope());
        serverPlayer.setEurope(null);
        serverPlayer.setMonarch(null);

        // Pity to have to update such a heavy object as the player,
        // but we do this, at most, once per player.  Other players
        // only need a partial player update and the stance change.
        // Put the stance change after the name change so that the
        // other players see the new nation name declaring war.  The
        // REF is hardwired to declare war on rebels so there is no
        // need to adjust its stance or tension.
        cs.addPartial(See.all().except(serverPlayer), serverPlayer,
                      "playerType", "independentNationName", "newLandName");
        cs.addMessage(See.all().except(serverPlayer),
            new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                             "declareIndependence.announce",
                             serverPlayer)
                .addStringTemplate("%oldNation%", oldNation)
                .addStringTemplate("%newNation%", serverPlayer.getNationName())
                .add("%ruler%", serverPlayer.getRulerNameKey()));
        cs.add(See.only(serverPlayer), serverPlayer);
        serverPlayer.setStance(refPlayer, Stance.WAR);
        cs.addStance(See.perhaps(), serverPlayer, Stance.WAR, refPlayer);

        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Give independence.  Note that the REF player is granting, but
     * most of the changes happen to the newly independent player.
     * hence the special handling.
     *
     * @param serverPlayer The REF <code>ServerPlayer</code> that is granting.
     * @param independent The newly independent <code>ServerPlayer</code>.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element giveIndependence(ServerPlayer serverPlayer,
                                    ServerPlayer independent) {
        ChangeSet cs = new ChangeSet();

        // The rebels have won.
        if (!changeStance(serverPlayer, Stance.PEACE, independent, cs)) {
            return Message.clientError("Unable to make peace!?!");
        }
        independent.setPlayerType(PlayerType.INDEPENDENT);
        Turn turn = getGame().getTurn();
        independent.modifyScore(SCORE_INDEPENDENCE_GRANTED - turn.getNumber());
        independent.setTax(0);
        independent.reinitialiseMarket();
        independent.addHistory(new HistoryEvent(turn,
                HistoryEvent.EventType.INDEPENDENCE));

        // Who surrenders?
        List<Unit> surrenderUnits = new ArrayList<Unit>();
        for (Unit u : serverPlayer.getUnits()) {
            if (!u.isNaval()) surrenderUnits.add(u);
        }
        if (surrenderUnits.size() > 0) {
            StringTemplate surrender = StringTemplate.label(", ");
            for (Unit u : surrenderUnits) {
                if (u.getType().hasAbility("model.ability.refUnit")) {
                    // Make sure the independent player does not end
                    // up owning any Kings Regulars!
                    UnitType downgrade = u.getType().getUnitTypeChange(ChangeType.CAPTURE,
                                                                       independent);
                    if (downgrade != null) u.setType(downgrade);
                }
                u.setOwner(independent);
                surrender.addStringTemplate(u.getLabel());
                // Make sure the former owner is notified!
                cs.add(See.perhaps().always(serverPlayer), u);
            }
            cs.addMessage(See.only(independent),
                new ModelMessage("model.player.independence.unitsAcquired",
                                 independent)
                    .addStringTemplate("%units%", surrender));
        }

        // Update others with player type.  Again, a pity to have to do
        // a whole player update.
        cs.addPartial(See.all().except(independent), independent, "playerType");
        cs.addMessage(See.all().except(independent),
            new ModelMessage("model.player.independence", serverPlayer));
        cs.add(See.only(independent), independent);

        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Rename an object.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is naming.
     * @param object The <code>Nameable</code> to rename.
     * @param newName The new name.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element renameObject(ServerPlayer serverPlayer, Nameable object,
                                String newName) {
        ChangeSet cs = new ChangeSet();

        object.setName(newName);
        FreeColGameObject fcgo = (FreeColGameObject) object;
        cs.addPartial(See.all(), fcgo, "name");

        // Others may be able to see the name change.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Get a transaction session.  Either the current one if it exists,
     * or create a fresh one.
     *
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @return A session describing the transaction.
     */
    public java.util.Map<String,Object> getTransactionSession(Unit unit, Settlement settlement) {
        java.util.Map<String, java.util.Map<String,Object>> unitTransactions = null;
        // Check for existing session, return it if present.
        if (transactionSessions.containsKey(unit.getId())) {
            unitTransactions = transactionSessions.get(unit.getId());
            if (unitTransactions.containsKey(settlement.getId())) {
                return unitTransactions.get(settlement.getId());
            }
        }

        // Session does not exist, create, store, and return it.
        java.util.Map<String,Object> session = new HashMap<String,Object>();
        // Default values
        session.put("actionTaken", false);
        session.put("unitMoves", unit.getMovesLeft());
        session.put("canGift", true);
        if (settlement.getOwner().atWarWith(unit.getOwner())) {
            session.put("canSell", false);
            session.put("canBuy", false);
        } else {
            session.put("canBuy", true);
            // The unit took nothing to sell, so nothing should be in
            // this session.
            session.put("canSell", unit.getSpaceTaken() != 0);
        }
        session.put("agreement", null);

        // Only keep track of human player sessions.
        if (unit.getOwner().isAI()) {
            return session;
        }
        
        // Save session for tracking
        // Unit has no open transactions?
        if (unitTransactions == null) {
            unitTransactions = new HashMap<String,java.util.Map<String, Object>>();
            transactionSessions.put(unit.getId(), unitTransactions);
        }
        unitTransactions.put(settlement.getId(), session);
        return session;
    }

    /**
     * Close a transaction session.
     *
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     */
    public void closeTransactionSession(Unit unit, Settlement settlement) {
        // Only keep track of human player sessions.
        if (unit.getOwner().isAI()) {
            return;
        }

        if (!transactionSessions.containsKey(unit.getId())) {
            throw new IllegalStateException("Trying to close a non-existing session (unit)");
        }

        java.util.Map<String, java.util.Map<String,Object>> unitTransactions
            = transactionSessions.get(unit.getId());
        if (!unitTransactions.containsKey(settlement.getId())) {
            throw new IllegalStateException("Trying to close a non-existing session (settlement)");
        }

        unitTransactions.remove(settlement.getId());
        if (unitTransactions.isEmpty()) {
            transactionSessions.remove(unit.getId());
        }
    }
    
    /**
     * Query whether a transaction session exists.
     *
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @return True if a session is already open.
     */
    public boolean isTransactionSessionOpen(Unit unit, Settlement settlement) {
        // AI does not need to send a message to open a session
        if (unit.getOwner().isAI()) return true;

        return transactionSessions.containsKey(unit.getId())
            && settlement != null
            && transactionSessions.get(unit.getId()).containsKey(settlement.getId());
    }

    /**
     * Get the client view of a transaction session, either existing or
     * newly opened.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is trading.
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element getTransaction(ServerPlayer serverPlayer, Unit unit,
                                  Settlement settlement) {
        ChangeSet cs = new ChangeSet();
        java.util.Map<String,Object> session;

        if (isTransactionSessionOpen(unit, settlement)) {
            session = getTransactionSession(unit, settlement);
        } else {
            if (unit.getMovesLeft() <= 0) {
                return Message.clientError("Unit " + unit.getId()
                                           + " has no moves left.");
            }
            session = getTransactionSession(unit, settlement);
            // Sets unit moves to zero to avoid cheating.  If no
            // action is taken, the moves will be restored when
            // closing the session
            unit.setMovesLeft(0);
            cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
        }

        // Add just the attributes the client needs.
        cs.addAttribute(See.only(serverPlayer), "canBuy",
                        ((Boolean) session.get("canBuy")).toString());
        cs.addAttribute(See.only(serverPlayer), "canSell",
                        ((Boolean) session.get("canSell")).toString());
        cs.addAttribute(See.only(serverPlayer), "canGift",
                        ((Boolean) session.get("canGift")).toString());

        // Others can not see transactions.
        return cs.build(serverPlayer);
    }

    /**
     * Close a transaction.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is trading.
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element closeTransaction(ServerPlayer serverPlayer, Unit unit,
                                    Settlement settlement) {
        if (!isTransactionSessionOpen(unit, settlement)) {
            return Message.clientError("No such transaction session.");
        }

        ChangeSet cs = new ChangeSet();
        java.util.Map<String,Object> session
            = getTransactionSession(unit, settlement);

        // Restore unit movement if no action taken.
        Boolean actionTaken = (Boolean) session.get("actionTaken");
        if (!actionTaken) {
            Integer unitMoves = (Integer) session.get("unitMoves");
            unit.setMovesLeft(unitMoves);
            cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
        }
        closeTransactionSession(unit, settlement);

        // Others can not see end of transaction.
        return cs.build(serverPlayer);
    }


    /**
     * Get the goods for sale in a settlement.
     *
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @return An list of <code>Goods</code> for sale at the settlement.
     */
    public List<Goods> getGoodsForSale(Unit unit, Settlement settlement)
        throws IllegalStateException {
        List<Goods> sellGoods = null;

        if (settlement instanceof IndianSettlement) {
            IndianSettlement indianSettlement = (IndianSettlement) settlement;
            sellGoods = indianSettlement.getSellGoods();
            if (!sellGoods.isEmpty()) {
                AIPlayer aiPlayer = (AIPlayer) getFreeColServer().getAIMain()
                    .getAIObject(indianSettlement.getOwner());
                for (Goods goods : sellGoods) {
                    aiPlayer.registerSellGoods(goods);
                }
            }
        } else { // Colony might be supported one day?
            throw new IllegalStateException("Bogus settlement");
        }
        return sellGoods;
    }


    /**
     * Price some goods for sale from a settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is buying.
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @param goods The <code>Goods</code> to buy.
     * @param price The buyers proposed price for the goods.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element buyProposition(ServerPlayer serverPlayer,
                                  Unit unit, Settlement settlement,
                                  Goods goods, int price) {
        if (!isTransactionSessionOpen(unit, settlement)) {
            return Message.clientError("Proposing to buy without opening a transaction session?!");
        }
        java.util.Map<String,Object> session
            = getTransactionSession(unit, settlement);
        if (!(Boolean) session.get("canBuy")) {
            return Message.clientError("Proposing to buy in a session where buying is not allowed.");
        }

        ChangeSet cs = new ChangeSet();

        // AI considers the proposition, return with a gold value
        AIPlayer ai = (AIPlayer) getFreeColServer().getAIMain()
            .getAIObject(settlement.getOwner());
        int gold = ai.buyProposition(unit, settlement, goods, price);

        // Others can not see proposals.
        cs.addAttribute(See.only(serverPlayer), "gold", Integer.toString(gold));
        return cs.build(serverPlayer);
    }

    /**
     * Price some goods for sale to a settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is buying.
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @param goods The <code>Goods</code> to sell.
     * @param price The sellers proposed price for the goods.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element sellProposition(ServerPlayer serverPlayer,
                                   Unit unit, Settlement settlement,
                                   Goods goods, int price) {
        if (!isTransactionSessionOpen(unit, settlement)) {
            return Message.clientError("Proposing to sell without opening a transaction session");
        }
        java.util.Map<String,Object> session
            = getTransactionSession(unit, settlement);
        if (!(Boolean) session.get("canSell")) {
            return Message.clientError("Proposing to sell in a session where selling is not allowed.");
        }

        ChangeSet cs = new ChangeSet();

        // AI considers the proposition, return with a gold value
        AIPlayer ai = (AIPlayer) getFreeColServer().getAIMain()
            .getAIObject(settlement.getOwner());
        int gold = ai.sellProposition(unit, settlement, goods, price);

        // Others can not see proposals.
        cs.addAttribute(See.only(serverPlayer), "gold", Integer.toString(gold));

        return cs.build(serverPlayer);
    }


    /**
     * Propagate an European market change to the other European markets.
     *
     * @param type The type of goods that was traded.
     * @param amount The amount of goods that was traded.
     * @param serverPlayer The player that performed the trade.
     */
    private void propagateToEuropeanMarkets(GoodsType type, int amount,
                                            ServerPlayer serverPlayer) {
        // Propagate 5-30% of the original change.
        final int lowerBound = 5; // TODO: make into game option?
        final int upperBound = 30;// TODO: make into game option?
        amount *= random.nextInt(upperBound - lowerBound + 1) + lowerBound;
        amount /= 100;
        if (amount == 0) return;

        // Do not need to update the clients here, these changes happen
        // while it is not their turn, and they will get a fresh copy
        // of the altered market in the update sent in nextPlayer above.
        Market market;
        for (ServerPlayer other : getOtherPlayers(serverPlayer)) {
            if (other.isEuropean() && (market = other.getMarket()) != null) {
                market.addGoodsToMarket(type, amount);
            }
        }
    }

    /**
     * Buy goods.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is buying.
     * @param unit The <code>Unit</code> to carry the goods.
     * @param type The <code>GoodsType</code> to buy.
     * @param amount The amount of goods to buy.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element buyGoods(ServerPlayer serverPlayer, Unit unit,
                            GoodsType type, int amount) {
        ChangeSet cs = new ChangeSet();
        Market market = serverPlayer.getMarket();

        // FIXME: market.buy() should be here in the controller, but
        // there are two cases remaining that are hard to move still.
        //
        // 1. There is a shortcut buying of equipment in Europe in
        // Unit.equipWith().
        // 2. Also for the goods required for a building in
        // Colony.payForBuilding().  This breaks the pattern implemented
        // here as there is no unit involved.
        market.buy(type, amount, serverPlayer);
        unit.getGoodsContainer().addGoods(type, amount);
        cs.add(See.only(serverPlayer), unit);
        cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
        if (market.hasPriceChanged(type)) {
            // This type of goods has changed price, so we will update
            // the market and send a message as well.
            cs.addMessage(See.only(serverPlayer),
                          market.makePriceChangeMessage(type));
            market.flushPriceChange(type);
            cs.add(See.only(serverPlayer), market);
        }
        propagateToEuropeanMarkets(type, amount, serverPlayer);

        // Action occurs in Europe, nothing is visible to other players.
        return cs.build(serverPlayer);
    }

    /**
     * Sell goods.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is selling.
     * @param unit The <code>Unit</code> carrying the goods.
     * @param type The <code>GoodsType</code> to sell.
     * @param amount The amount of goods to sell.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element sellGoods(ServerPlayer serverPlayer, Unit unit,
                             GoodsType type, int amount) {
        ChangeSet cs = new ChangeSet();
        Market market = serverPlayer.getMarket();

        // FIXME: market.sell() should be in the controller, but the
        // following cases will have to wait.
        //
        // 1. Unit.dumpEquipment() gets called from a few places.
        // 2. Colony.exportGoods() is in the newTurn mess.
        // Its also still in MarketTest, which needs to be moved to
        // ServerPlayerTest where it also is already.
        //
        // Try to sell.
        market.sell(type, amount, serverPlayer);
        unit.getGoodsContainer().addGoods(type, -amount);
        cs.add(See.only(serverPlayer), unit);
        cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
        if (market.hasPriceChanged(type)) {
            // This type of goods has changed price, so update the
            // market and send a message as well.
            cs.addMessage(See.only(serverPlayer),
                          market.makePriceChangeMessage(type));
            market.flushPriceChange(type);
            cs.add(See.only(serverPlayer), market);
        }
        propagateToEuropeanMarkets(type, amount, serverPlayer);

        // Action occurs in Europe, nothing is visible to other players.
        return cs.build(serverPlayer);
    }


    /**
     * A unit migrates from Europe.
     *
     * @param serverPlayer The <code>ServerPlayer</code> whose unit it will be.
     * @param slot The slot within <code>Europe</code> to select the unit from.
     * @param type The type of migration occurring.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element emigrate(ServerPlayer serverPlayer, int slot,
                            MigrationType type) {
        ChangeSet cs = new ChangeSet();

        // Valid slots are in [1,3], recruitable indices are in [0,2].
        // An invalid slot is normal when the player has no control over
        // recruit type.
        boolean selected = 1 <= slot && slot <= Europe.RECRUIT_COUNT;
        int index = (selected) ? slot-1 : random.nextInt(Europe.RECRUIT_COUNT);

        // Create the recruit, move it to the docks.
        Europe europe = serverPlayer.getEurope();
        UnitType recruitType = europe.getRecruitable(index);
        Game game = getGame();
        Unit unit = new Unit(game, europe, serverPlayer, recruitType,
                             UnitState.ACTIVE,
                             recruitType.getDefaultEquipment());
        unit.setLocation(europe);

        // Handle migration type specific changes.
        switch (type) {
        case FOUNTAIN:
            serverPlayer.setRemainingEmigrants(serverPlayer.getRemainingEmigrants() - 1);
            break;
        case RECRUIT:
            serverPlayer.modifyGold(-europe.getRecruitPrice());
            europe.increaseRecruitmentDifficulty();
            // Fall through
        case NORMAL:
            serverPlayer.updateImmigrationRequired();
            serverPlayer.reduceImmigration();
            cs.addPartial(See.only(serverPlayer), serverPlayer,
                          "immigration", "immigrationRequired");
            break;
        default:
            throw new IllegalArgumentException("Bogus migration type");
        }

        // Replace the recruit we used.
        europe.setRecruitable(index, serverPlayer.generateRecruitable());
        cs.add(See.only(serverPlayer), europe);

        // Return an informative message only if this was an ordinary
        // migration where we did not select the unit type.
        // Other cases were selected.
        if (!selected) {
            cs.addMessage(See.only(serverPlayer),
                new ModelMessage(ModelMessage.MessageType.UNIT_ADDED,
                                 "model.europe.emigrate",
                                 serverPlayer, unit)
                    .add("%europe%", europe.getNameKey())
                    .addStringTemplate("%unit%", unit.getLabel()));
        }

        // Do not update others, emigration is private.
        return cs.build(serverPlayer);
    }


    /**
     * If a unit moves, check if an opposing naval unit slows it down.
     * Note that the unit moves are reduced here.
     *
     * @param unit The <code>Unit</code> that is moving.
     * @param newTile The <code>Tile</code> the unit is moving to.
     * @return Either an enemy unit that causes a slowdown, or null if none.
     */
    private Unit getSlowedBy(Unit unit, Tile newTile) {
        Player player = unit.getOwner();
        Game game = unit.getGame();
        CombatModel combatModel = game.getCombatModel();
        boolean pirate = unit.hasAbility("model.ability.piracy");
        Unit attacker = null;
        float attackPower = 0, totalAttackPower = 0;

        if (!unit.isNaval() || unit.getMovesLeft() <= 0) return null;
        for (Tile tile : game.getMap().getSurroundingTiles(newTile, 1)) {
            // Ships in settlements do not slow enemy ships, but:
            // TODO should a fortress slow a ship?
            Player enemy;
            if (tile.isLand()
                || tile.getColony() != null
                || tile.getFirstUnit() == null
                || (enemy = tile.getFirstUnit().getOwner()) == player) continue;
            for (Unit enemyUnit : tile.getUnitList()) {
                if ((pirate || enemyUnit.hasAbility("model.ability.piracy")
                     || (enemyUnit.isOffensiveUnit() && player.atWarWith(enemy)))
                    && enemyUnit.isNaval()
                    && combatModel.getOffencePower(enemyUnit, unit) > attackPower) {
                    attackPower = combatModel.getOffencePower(enemyUnit, unit);
                    totalAttackPower += attackPower;
                    attacker = enemyUnit;
                }
            }
        }
        if (attacker != null) {
            float defencePower = combatModel.getDefencePower(attacker, unit);
            float totalProbability = totalAttackPower + defencePower;
            if (random.nextInt(Math.round(totalProbability) + 1)
                < totalAttackPower) {
                int diff = Math.max(0, Math.round(totalAttackPower - defencePower));
                int moves = Math.min(9, 3 + diff / 3);
                unit.setMovesLeft(unit.getMovesLeft() - moves);
                logger.info(unit.getId()
                            + " slowed by " + attacker.getId()
                            + " by " + Integer.toString(moves) + " moves.");
            } else {
                attacker = null;
            }
        }
        return attacker;
    }

    /**
     * Returns a type of Lost City Rumour. The type of rumour depends on the
     * exploring unit, as well as player settings.
     *
     * @param lostCity The <code>LostCityRumour</code> to investigate.
     * @param unit The <code>Unit</code> exploring the lost city rumour.
     * @param difficulty The difficulty level.
     * @return The type of rumour.
     * TODO: Move all the magic numbers in here to the specification.
     *       Also change the logic so that the special events appear a
     *       fixed number of times throughout the game, according to
     *       the specification.  Names for the cities of gold is also
     *       on the wishlist.
     */
    private RumourType getLostCityRumourType(LostCityRumour lostCity,
                                             Unit unit, int difficulty) {
        Tile tile = unit.getTile();
        Player player = unit.getOwner();
        RumourType rumour = lostCity.getType();
        if (rumour != null) {
            // Filter out failing cases that could only occur if the
            // type was explicitly set in debug mode.
            switch (rumour) {
            case BURIAL_GROUND:
                if (tile.getOwner() == null || !tile.getOwner().isIndian()) {
                    rumour = RumourType.NOTHING;
                }
                break;
            case LEARN:
                if (unit.getType().getUnitTypesLearntInLostCity().isEmpty()) {
                    rumour = RumourType.NOTHING;
                }
                break;
            default:
                break;
            }
            return rumour;
        }

        // The following arrays contain percentage values for
        // "good" and "bad" events when scouting with a non-expert
        // at the various difficulty levels [0..4] exact values
        // but generally "bad" should increase, "good" decrease
        final int BAD_EVENT_PERCENTAGE[]  = { 11, 17, 23, 30, 37 };
        final int GOOD_EVENT_PERCENTAGE[] = { 75, 62, 48, 33, 17 };
        // remaining to 100, event NOTHING:   14, 21, 29, 37, 46

        // The following arrays contain the modifiers applied when
        // expert scout is at work exact values; modifiers may
        // look slightly "better" on harder levels since we're
        // starting from a "worse" percentage.
        final int BAD_EVENT_MOD[]  = { -6, -7, -7, -8, -9 };
        final int GOOD_EVENT_MOD[] = { 14, 15, 16, 18, 20 };

        // The scouting outcome is based on three factors: level,
        // expert scout or not, DeSoto or not.  Based on this, we
        // are going to calculate probabilites for neutral, bad
        // and good events.
        boolean isExpertScout = unit.hasAbility("model.ability.expertScout")
            && unit.hasAbility("model.ability.scoutIndianSettlement");
        boolean hasDeSoto = player.hasAbility("model.ability.rumoursAlwaysPositive");
        int percentNeutral;
        int percentBad;
        int percentGood;
        if (hasDeSoto) {
            percentBad  = 0;
            percentGood = 100;
            percentNeutral = 0;
        } else {
            // First, get "basic" percentages
            percentBad  = BAD_EVENT_PERCENTAGE[difficulty];
            percentGood = GOOD_EVENT_PERCENTAGE[difficulty];

            // Second, apply ExpertScout bonus if necessary
            if (isExpertScout) {
                percentBad  += BAD_EVENT_MOD[difficulty];
                percentGood += GOOD_EVENT_MOD[difficulty];
            }

            // Third, get a value for the "neutral" percentage,
            // unless the other values exceed 100 already
            if (percentBad + percentGood < 100) {
                percentNeutral = 100 - percentBad - percentGood;
            } else {
                percentNeutral = 0;
            }
        }

        // Now, the individual events; each section should add up to 100
        // The NEUTRAL
        int eventNothing = 100;

        // The BAD
        int eventVanish = 100;
        int eventBurialGround = 0;
        // If the tile not is European-owned, allow burial grounds rumour.
        if (tile.getOwner() != null && tile.getOwner().isIndian()) {
            eventVanish = 75;
            eventBurialGround = 25;
        }

        // The GOOD
        int eventLearn    = 30;
        int eventTrinkets = 30;
        int eventColonist = 20;
        // or, if the unit can't learn
        if (unit.getType().getUnitTypesLearntInLostCity().isEmpty()) {
            eventLearn    =  0;
            eventTrinkets = 50;
            eventColonist = 30;
        }

        // The SPECIAL
        // Right now, these are considered "good" events that happen randomly.
        int eventRuins    = 9;
        int eventCibola   = 6;
        int eventFountain = 5;

        // Finally, apply the Good/Bad/Neutral modifiers from
        // above, so that we end up with a ton of values, some of
        // them zero, the sum of which should be 10000.
        eventNothing      *= percentNeutral;
        eventVanish       *= percentBad;
        eventBurialGround *= percentBad;
        eventLearn        *= percentGood;
        eventTrinkets     *= percentGood;
        eventColonist     *= percentGood;
        eventRuins        *= percentGood;
        eventCibola       *= percentGood;
        eventFountain     *= percentGood;

        // Add all possible events to a RandomChoice List
        List<RandomChoice<RumourType>> choices = new ArrayList<RandomChoice<RumourType>>();
        if (eventNothing > 0) {
            choices.add(new RandomChoice<RumourType>(RumourType.NOTHING, eventNothing));
        }
        if (eventVanish > 0) {
            choices.add(new RandomChoice<RumourType>(RumourType.EXPEDITION_VANISHES, eventVanish));
        }
        if (eventBurialGround > 0) {
            choices.add(new RandomChoice<RumourType>(RumourType.BURIAL_GROUND, eventBurialGround));
        }
        if (eventLearn > 0) {
            choices.add(new RandomChoice<RumourType>(RumourType.LEARN, eventLearn));
        }
        if (eventTrinkets > 0) {
            choices.add(new RandomChoice<RumourType>(RumourType.TRIBAL_CHIEF, eventTrinkets));
        }
        if (eventColonist > 0) {
            choices.add(new RandomChoice<RumourType>(RumourType.COLONIST, eventColonist));
        }
        if (eventRuins > 0) {
            choices.add(new RandomChoice<RumourType>(RumourType.RUINS, eventRuins));
        }
        if (eventCibola > 0) {
            choices.add(new RandomChoice<RumourType>(RumourType.CIBOLA, eventCibola));
        }
        if (eventFountain > 0) {
            choices.add(new RandomChoice<RumourType>(RumourType.FOUNTAIN_OF_YOUTH, eventFountain));
        }
        return RandomChoice.getWeightedRandom(random, choices);
    }

    /**
     * Explore a lost city.
     *
     * @param unit The <code>Unit</code> that is exploring.
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param cs A <code>ChangeSet</code> to add changes to.
     */
    private void exploreLostCityRumour(ServerPlayer serverPlayer, Unit unit,
                                       ChangeSet cs) {
        Tile tile = unit.getTile();
        LostCityRumour lostCity = tile.getLostCityRumour();
        if (lostCity == null) return;

        Specification specification = getGame().getSpecification();
        int difficulty = specification.getRangeOption("model.option.difficulty").getValue();
        int dx = 10 - difficulty;
        Game game = unit.getGame();
        UnitType unitType;
        Unit newUnit = null;
        List<UnitType> treasureUnitTypes = null;

        switch (getLostCityRumourType(lostCity, unit, difficulty)) {
        case BURIAL_GROUND:
            Player indianPlayer = tile.getOwner();
            cs.add(See.only(serverPlayer),
                   indianPlayer.modifyTension(serverPlayer, Tension.Level.HATEFUL.getLimit()));
            cs.add(See.only(serverPlayer), indianPlayer);
            cs.addMessage(See.only(serverPlayer),
                new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                                 "lostCityRumour.BurialGround",
                                 serverPlayer, unit)
                    .addStringTemplate("%nation%", indianPlayer.getNationName()));
            break;
        case EXPEDITION_VANISHES:
            cs.addDispose(serverPlayer, tile, unit);
            cs.addMessage(See.only(serverPlayer),
                new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                                 "lostCityRumour.ExpeditionVanishes",
                                 serverPlayer));
            break;
        case NOTHING:
            cs.addMessage(See.only(serverPlayer),
                new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                                 "lostCityRumour.Nothing",
                                 serverPlayer, unit));
            break;
        case LEARN:
            List<UnitType> learntUnitTypes = unit.getType().getUnitTypesLearntInLostCity();
            StringTemplate oldName = unit.getLabel();
            unit.setType(learntUnitTypes.get(random.nextInt(learntUnitTypes.size())));
            cs.addMessage(See.only(serverPlayer),
                new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                                 "lostCityRumour.Learn",
                                 serverPlayer, unit)
                    .addStringTemplate("%unit%", oldName)
                    .add("%type%", unit.getType().getNameKey()));
            break;
        case TRIBAL_CHIEF:
            int chiefAmount = random.nextInt(dx * 10) + dx * 5;
            serverPlayer.modifyGold(chiefAmount);
            cs.addPartial(See.only(serverPlayer), serverPlayer,
                          "gold", "score");
            cs.addMessage(See.only(serverPlayer),
                new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                                 "lostCityRumour.TribalChief",
                                 serverPlayer, unit)
                    .addAmount("%money%", chiefAmount));
            break;
        case COLONIST:
            List<UnitType> newUnitTypes = specification.getUnitTypesWithAbility("model.ability.foundInLostCity");
            newUnit = new Unit(game, tile, serverPlayer,
                               newUnitTypes.get(random.nextInt(newUnitTypes.size())),
                               UnitState.ACTIVE);
            cs.addMessage(See.only(serverPlayer),
                new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                                 "lostCityRumour.Colonist",
                                 serverPlayer, newUnit));
            break;
        case CIBOLA:
            String cityName = game.getCityOfCibola();
            if (cityName != null) {
                int treasureAmount = random.nextInt(dx * 600) + dx * 300;
                if (treasureUnitTypes == null) {
                    treasureUnitTypes = specification.getUnitTypesWithAbility("model.ability.carryTreasure");
                }
                unitType = treasureUnitTypes.get(random.nextInt(treasureUnitTypes.size()));
                newUnit = new Unit(game, tile, serverPlayer, unitType, UnitState.ACTIVE);
                newUnit.setTreasureAmount(treasureAmount);
                cs.addMessage(See.only(serverPlayer),
                    new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                                     "lostCityRumour.Cibola",
                                     serverPlayer, newUnit)
                        .add("%city%", cityName)
                        .addAmount("%money%", treasureAmount));
                cs.addHistory(serverPlayer,
                    new HistoryEvent(game.getTurn(),
                                     HistoryEvent.EventType.CITY_OF_GOLD)
                        .add("%city%", cityName)
                        .addAmount("%treasure%", treasureAmount));
                break;
            }
            // Fall through, found all the cities of gold.
        case RUINS:
            int ruinsAmount = random.nextInt(dx * 2) * 300 + 50;
            if (ruinsAmount < 500) { // TODO remove magic number
                serverPlayer.modifyGold(ruinsAmount);
                cs.addPartial(See.only(serverPlayer), serverPlayer,
                              "gold", "score");
            } else {
                if (treasureUnitTypes == null) {
                    treasureUnitTypes = specification.getUnitTypesWithAbility("model.ability.carryTreasure");
                }
                unitType = treasureUnitTypes.get(random.nextInt(treasureUnitTypes.size()));
                newUnit = new Unit(game, tile, serverPlayer, unitType, UnitState.ACTIVE);
                newUnit.setTreasureAmount(ruinsAmount);
            }
            cs.addMessage(See.only(serverPlayer),
                 new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                                  "lostCityRumour.Ruins",
                                  serverPlayer, ((newUnit != null) ? newUnit : unit))
                     .addAmount("%money%", ruinsAmount));
            break;
        case FOUNTAIN_OF_YOUTH:
            Europe europe = serverPlayer.getEurope();
            if (europe == null) {
                cs.addMessage(See.only(serverPlayer),
                     new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                                      "lostCityRumour.FountainOfYouthWithoutEurope",
                                      serverPlayer, unit));
            } else {
                if (serverPlayer.hasAbility("model.ability.selectRecruit")
                    && !serverPlayer.isAI()) { // TODO: let the AI select
                    // Remember, and ask player to select
                    serverPlayer.setRemainingEmigrants(dx);
                    cs.addAttribute(See.only(serverPlayer),
                                    "fountainOfYouth", Integer.toString(dx));
                } else {
                    List<RandomChoice<UnitType>> recruitables
                        = serverPlayer.generateRecruitablesList();
                    for (int k = 0; k < dx; k++) {
                        new Unit(game, europe, serverPlayer,
                                 RandomChoice.getWeightedRandom(random, recruitables),
                                 UnitState.ACTIVE);
                    }
                    cs.add(See.only(serverPlayer), europe);
                }
                cs.addMessage(See.only(serverPlayer),
                     new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                                      "lostCityRumour.FountainOfYouth",
                                      serverPlayer, unit));
            }
            break;
        case NO_SUCH_RUMOUR:
        default:
            throw new IllegalStateException("No such rumour.");
        }
        tile.removeLostCityRumour();
    }

    /**
     * Check for a special contact panel for a nation.  If not found,
     * check for a more general one if allowed.
     *
     * @param player A European <code>Player</code> making contact.
     * @param other The <code>Player</code> nation to being contacted.
     * @return An <code>EventPanel</code> key, or null if none appropriate.
     */
    private String getContactKey(Player player, Player other) {
        String key = "EventPanel.MEETING_"
            + Messages.message(other.getNationName()).toUpperCase();
        if (!Messages.containsKey(key)) {
            if (other.isEuropean()) {
                key = (player.hasContactedEuropeans()) ? null
                    : "EventPanel.MEETING_EUROPEANS";
            } else {
                key = (player.hasContactedIndians()) ? null
                    : "EventPanel.MEETING_NATIVES";
            }
        }
        return key;
    }

    /**
     * Move a unit.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is moving.
     * @param unit The <code>Unit</code> to move.
     * @param newTile The <code>Tile</code> to move to.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element move(ServerPlayer serverPlayer, Unit unit, Tile newTile) {
        ChangeSet cs = new ChangeSet();
        Game game = getGame();
        Turn turn = game.getTurn();

        // Plan to update tiles that could not be seen before but will
        // now be within the line-of-sight.
        List<FreeColGameObject> newTiles = new ArrayList<FreeColGameObject>();
        int los = unit.getLineOfSight();
        for (Tile tile : game.getMap().getSurroundingTiles(newTile, los)) {
            if (!serverPlayer.canSee(tile)) newTiles.add(tile);
        }

        // Update unit state.
        Location oldLocation = unit.getLocation();
        unit.setState(UnitState.ACTIVE);
        unit.setStateToAllChildren(UnitState.SENTRY);
        if (oldLocation instanceof Unit) {
            unit.setMovesLeft(0); // Disembark always consumes all moves.
        } else {
            unit.setMovesLeft(unit.getMovesLeft() - unit.getMoveCost(newTile));
        }


        // Do the move and explore a rumour if needed.
        unit.setLocation(newTile);
        if (newTile.hasLostCityRumour() && serverPlayer.isEuropean()) {
            exploreLostCityRumour(serverPlayer, unit, cs);
        }

        // Always update old location and new tile except if it has
        // already been handled by fatal rumour.
        // Always add the animation, but dead units make no discoveries.
        cs.add(See.perhaps(), (FreeColGameObject) oldLocation);
        cs.addMove(See.perhaps(), unit, oldLocation, newTile);
        if (!unit.isDisposed()) {
            cs.add(See.perhaps(), newTile);
            cs.add(See.only(serverPlayer), newTiles);
        }

        if (!unit.isDisposed()) {
            if (newTile.isLand()) {
                // Check for first landing
                if (serverPlayer.isEuropean()
                    && !serverPlayer.isNewLandNamed()) {
                    String newLand = Messages.getNewLandName(serverPlayer);
                    if (serverPlayer.isAI()) {
                        // TODO: Not convinced shortcutting the AI like
                        // this is a good idea, this really should be in
                        // the AI code.
                        serverPlayer.setNewLandName(newLand);
                    } else { // Ask player to name the land.
                        cs.addAttribute(See.only(serverPlayer),
                                        "nameNewLand", newLand);
                    }
                }

                // Check for new contacts.
                ServerPlayer welcomer = null;
                Map map = getGame().getMap();
                for (Tile t : map.getSurroundingTiles(newTile, 1, 1)) {
                    if (t == null || !t.isLand()) {
                        continue; // Invalid tile for contact
                    }

                    ServerPlayer other = null;
                    Settlement settlement = t.getSettlement();
                    if (settlement != null) {
                        other = (ServerPlayer) t.getSettlement().getOwner();
                    } else if (t.getFirstUnit() != null) {
                        other = (ServerPlayer) t.getFirstUnit().getOwner();
                    }
                    if (other == null || other == serverPlayer) {
                        continue; // No contact
                    }

                    // Activate sentries
                    for (Unit u : t.getUnitList()) {
                        if (u.getState() == UnitState.SENTRY) {
                            u.setState(UnitState.ACTIVE);
                            cs.add(See.only(serverPlayer), u);
                        }
                    }

                    // Ignore previously contacted nations.
                    if (serverPlayer.hasContacted(other)) continue;

                    // Must be a first contact!
                    if (serverPlayer.isIndian()) {
                        // Ignore native-to-native contacts.
                        if (!other.isIndian()) {
                            String key = getContactKey(other, serverPlayer);
                            if (key != null) {
                                cs.addMessage(See.only(other),
                                    new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                                                     key, other, serverPlayer));
                            }
                            cs.addHistory(other,
                                new HistoryEvent(turn,
                                    HistoryEvent.EventType.MEET_NATION)
                                    .addStringTemplate("%nation%", serverPlayer.getNationName()));
                        }
                    } else { // (serverPlayer.isEuropean)
                        // Initialize alarm for native settlements.
                        // TODO: check if this is still necessary.
                        if (other.isIndian() && settlement != null) {
                            IndianSettlement is = (IndianSettlement) settlement;
                            if (is.getAlarm(serverPlayer) == null) {
                                is.setAlarm(serverPlayer,
                                            other.getTension(serverPlayer));
                                cs.add(See.only(serverPlayer), is);
                            }
                        }

                        // Add first contact messages.
                        String key = getContactKey(serverPlayer, other);
                        if (key != null) {
                            cs.addMessage(See.only(serverPlayer),
                                new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                                                 key, serverPlayer, other));
                        }

                        // History event for European players.
                        cs.addHistory(serverPlayer,
                            new HistoryEvent(turn,
                                HistoryEvent.EventType.MEET_NATION)
                                .addStringTemplate("%nation%", other.getNationName()));
                        // Extra special meeting on first landing!
                        if (other.isIndian()
                            && !serverPlayer.isNewLandNamed()
                            && (welcomer == null || newTile.getOwner() == other)) {
                            welcomer = other;
                        }
                    }

                    // Now make the contact properly.
                    Player.makeContact(serverPlayer, other);
                    cs.addStance(See.perhaps(), serverPlayer,
                                 Stance.PEACE, other);
                }
                if (welcomer != null) {
                    cs.addAttribute(See.only(serverPlayer), "welcome",
                                    welcomer.getId());
                    cs.addAttribute(See.only(serverPlayer), "camps",
                        Integer.toString(welcomer.getNumberOfSettlements()));
               }
           }

            // Check for slowing units.
            Unit slowedBy = getSlowedBy(unit, newTile);
            if (slowedBy != null) {
                cs.addAttribute(See.only(serverPlayer), "slowedBy",
                                slowedBy.getId());
            }

            // Check for region discovery
            Region region = newTile.getDiscoverableRegion();
            if (serverPlayer.isEuropean() && region != null) {
                HistoryEvent h = null;
                if (region.isPacific()) {
                    cs.addAttribute(See.only(serverPlayer),
                                    "discoverPacific", "true");
                    cs.addRegion(serverPlayer, region, "model.region.pacific");
                } else {
                    String regionName = Messages.getDefaultRegionName(serverPlayer,
                                                                      region.getType());
                    if (serverPlayer.isAI()) {
                        // TODO: here is another dubious AI shortcut.
                        cs.addRegion(serverPlayer, region, regionName);
                    } else { // Ask player to name the region.
                        cs.addAttribute(See.only(serverPlayer),
                                        "discoverRegion", regionName);
                        cs.addAttribute(See.only(serverPlayer),
                                        "regionType",
                                        Messages.message(region.getLabel()));
                    }
                }
                if (h != null) cs.addHistory(serverPlayer, h);
            }
        }

        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Set land name.
     *
     * @param serverPlayer The <code>ServerPlayer</code> who landed.
     * @param name The new land name.
     * @param welcomer An optional <code>ServerPlayer</code> that has offered
     *            a treaty.
     * @param accept True if the treaty has been accepted.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element setNewLandName(ServerPlayer serverPlayer, String name,
                                  ServerPlayer welcomer, boolean accept) {
        ChangeSet cs = new ChangeSet();

        // Special case of a welcome from an adjacent native unit,
        // offering the land the landing unit is on if a peace treaty
        // is accepted.  Slight awkwardness that we have to find the
        // unit that landed, which relies on this code being triggered
        // from the first landing and thus there is only one land unit
        // in the new world (which is not the case in a debug game).
        serverPlayer.setNewLandName(name);
        if (welcomer != null) {
            if (accept) { // Claim land
                for (Unit u : serverPlayer.getUnits()) {
                    if (u.isNaval()) continue;
                    Tile tile = u.getTile();
                    if (tile == null) continue;
                    if (tile.isLand() && tile.getOwner() == welcomer) {
                        tile.setOwner(serverPlayer);
                        cs.add(See.only(serverPlayer), tile);
                        break;
                    }
                }
                welcomer = null;
            } else {
                // Consider not accepting the treaty to be a minor
                // insult.  WWC1D?
                cs.add(See.only(serverPlayer),
                       welcomer.modifyTension(serverPlayer, Tension.TENSION_ADD_MINOR));
            }
        }

        // Update the name and note the history.
        cs.addPartial(See.only(serverPlayer), serverPlayer, "newLandName");
        Turn turn = serverPlayer.getGame().getTurn();
        HistoryEvent h = new HistoryEvent(turn,
                    HistoryEvent.EventType.DISCOVER_NEW_WORLD)
            .addName("%name%", name);
        cs.addHistory(serverPlayer,
            new HistoryEvent(turn, HistoryEvent.EventType.DISCOVER_NEW_WORLD)
                .addName("%name%", name));

        // Only the tile change is not private.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Set region name.
     *
     * @param serverPlayer The <code>ServerPlayer</code> discovering.
     * @param unit The <code>Unit</code> discovering the region.
     * @param region The <code>Region</code> to discover.
     * @param name The new region name.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element setNewRegionName(ServerPlayer serverPlayer, Unit unit,
                                    Region region, String name) {
        ChangeSet cs = new ChangeSet();
        cs.addRegion(serverPlayer, region, name);

        // Others do find out about region name changes.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Move a unit to Europe.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param unit The <code>Unit</code> to move to Europe.
     */
    public Element moveToEurope(ServerPlayer serverPlayer, Unit unit) {
        Europe europe = serverPlayer.getEurope();
        if (unit.getLocation() == europe) {
            // Unit already in Europe, nothing to see for the others.
            unit.setState(UnitState.TO_EUROPE);
            return new ChangeSet().add(See.only(serverPlayer), unit, europe)
                .build(serverPlayer);
        }

        ChangeSet cs = new ChangeSet();

        // Set entry location before setState (satisfy its check), then
        // set location.
        Tile tile = unit.getTile();
        unit.setEntryLocation(tile);
        unit.setState(UnitState.TO_EUROPE);
        unit.setLocation(europe);
        cs.addDisappear(serverPlayer, tile, unit);
        cs.add(See.only(serverPlayer), tile, europe);

        // Others see a disappearance, player sees tile and europe update
        // as europe now contains the unit.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Embark a unit onto a carrier.
     * Checking that the locations are appropriate is not done here.
     *
     * @param serverPlayer The <code>ServerPlayer</code> embarking.
     * @param unit The <code>Unit</code> that is embarking.
     * @param carrier The <code>Unit</code> to embark onto.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element embarkUnit(ServerPlayer serverPlayer, Unit unit,
                              Unit carrier) {
        if (unit.isNaval()) {
            return Message.clientError("Naval unit " + unit.getId()
                                       + " can not embark.");
        }
        if (carrier.getSpaceLeft() < unit.getSpaceTaken()) {
            return Message.clientError("No space available for unit "
                                       + unit.getId() + " to embark.");
        }

        ChangeSet cs = new ChangeSet();

        Location oldLocation = unit.getLocation();
        unit.setLocation(carrier);
        unit.setMovesLeft(0);
        unit.setState(UnitState.SENTRY);
        cs.add(See.only(serverPlayer), (FreeColGameObject) oldLocation);
        if (carrier.getLocation() != oldLocation) {
            cs.add(See.only(serverPlayer), carrier);
            cs.addMove(See.only(serverPlayer), unit, oldLocation,
                       carrier.getTile());
            cs.addDisappear(serverPlayer, carrier.getTile(), unit);
        }

        // Others might see the unit disappear, or the carrier capacity.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Disembark unit from a carrier.
     *
     * @param serverPlayer The <code>ServerPlayer</code> whose unit is
     *                     embarking.
     * @param unit The <code>Unit</code> that is disembarking.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element disembarkUnit(ServerPlayer serverPlayer, Unit unit) {
        if (unit.isNaval()) {
            return Message.clientError("Naval unit " + unit.getId()
                                       + " can not disembark.");
        }
        if (!(unit.getLocation() instanceof Unit)) {
            return Message.clientError("Unit " + unit.getId()
                                       + " is not embarked.");
        }

        ChangeSet cs = new ChangeSet();

        Unit carrier = (Unit) unit.getLocation();
        Location newLocation = carrier.getLocation();
        unit.setLocation(newLocation);
        unit.setMovesLeft(0); // In Col1 disembark consumes whole move.
        unit.setState(UnitState.ACTIVE);
        cs.add(See.only(serverPlayer), (FreeColGameObject) newLocation);

        // Others can (potentially) see the location.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Ask about learning a skill at an IndianSettlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is learning.
     * @param unit The <code>Unit</code> that is learning.
     * @param settlement The <code>Settlement</code> to learn from.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element askLearnSkill(ServerPlayer serverPlayer, Unit unit,
                                 IndianSettlement settlement) {
        ChangeSet cs = new ChangeSet();

        Tile tile = settlement.getTile();
        PlayerExploredTile pet = tile.getPlayerExploredTile(serverPlayer);
        pet.setVisited();
        pet.setSkill(settlement.getLearnableSkill());
        cs.add(See.only(serverPlayer), tile);
        unit.setMovesLeft(0);
        cs.addPartial(See.only(serverPlayer), unit, "movesLeft");

        // Do not update others, nothing to see yet.
        return cs.build(serverPlayer);
    }

    /**
     * Learn a skill at an IndianSettlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is learning.
     * @param unit The <code>Unit</code> that is learning.
     * @param settlement The <code>Settlement</code> to learn from.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element learnFromIndianSettlement(ServerPlayer serverPlayer,
                                             Unit unit,
                                             IndianSettlement settlement) {
        // Sanity checks.
        UnitType skill = settlement.getLearnableSkill();
        if (skill == null) {
            return Message.clientError("No skill to learn at "
                                       + settlement.getName());
        }
        if (!unit.getType().canBeUpgraded(skill, ChangeType.NATIVES)) {
            return Message.clientError("Unit " + unit.toString()
                                       + " can not learn skill " + skill
                                       + " at " + settlement.getName());
        }

        ChangeSet cs = new ChangeSet();

        // Try to learn
        unit.setMovesLeft(0);
        Tension tension = settlement.getAlarm(serverPlayer);
        switch (tension.getLevel()) {
        case HATEFUL: // Killed
            cs.addDispose(serverPlayer, unit.getLocation(), unit);
            break;
        case ANGRY: // Learn nothing, not even a pet update
            cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
            break;
        default:
            // Teach the unit, and expend the skill if necessary.
            // Do a full information update as the unit is in the settlement.
            unit.setType(skill);
            if (!settlement.isCapital()) settlement.setLearnableSkill(null);
            Tile tile = settlement.getTile();
            tile.updateIndianSettlementInformation(serverPlayer);
            cs.add(See.only(serverPlayer), unit, tile);
            break;
        }

        // Others always see the unit, it may have died or been taught.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Demand a tribute from a native settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> demanding the tribute.
     * @param unit The <code>Unit</code> that is demanding the tribute.
     * @param settlement The <code>IndianSettlement</code> demanded of.
     * @return An <code>Element</code> encapsulating this action.
     * TODO: Move TURNS_PER_TRIBUTE magic number to the spec.
     */
    public Element demandTribute(ServerPlayer serverPlayer, Unit unit,
                                 IndianSettlement settlement) {
        ChangeSet cs = new ChangeSet();
        final int TURNS_PER_TRIBUTE = 5;

        Player indianPlayer = settlement.getOwner();
        int gold = 0;
        int year = getGame().getTurn().getNumber();
        if (settlement.getLastTribute() + TURNS_PER_TRIBUTE < year
            && indianPlayer.getGold() > 0) {
            switch (indianPlayer.getTension(serverPlayer).getLevel()) {
            case HAPPY:
            case CONTENT:
                gold = Math.min(indianPlayer.getGold() / 10, 100);
                break;
            case DISPLEASED:
                gold = Math.min(indianPlayer.getGold() / 20, 100);
                break;
            case ANGRY:
            case HATEFUL:
            default:
                break; // do nothing
            }
        }

        // Increase tension whether we paid or not.  Apply tension
        // directly to the settlement and let propagation work.
        settlement.modifyAlarm(serverPlayer, Tension.TENSION_ADD_NORMAL);
        settlement.setLastTribute(year);
        ModelMessage m;
        if (gold > 0) {
            indianPlayer.modifyGold(-gold);
            serverPlayer.modifyGold(gold);
            cs.addPartial(See.only(serverPlayer), serverPlayer, "gold", "score");
            m = new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                                 "scoutSettlement.tributeAgree",
                                 unit, settlement)
                .addAmount("%amount%", gold);
        } else {
            m = new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                                 "scoutSettlement.tributeDisagree",
                                 unit, settlement);
        }
        cs.addMessage(See.only(serverPlayer), m);
        unit.setMovesLeft(0);
        cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
        // Do not update others, this is all private.
        return cs.build(serverPlayer);
    }

    /**
     * Scout a native settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is scouting.
     * @param unit The scout <code>Unit</code>.
     * @param settlement The <code>IndianSettlement</code> to scout.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element scoutIndianSettlement(ServerPlayer serverPlayer,
                                         Unit unit,
                                         IndianSettlement settlement) {
        ChangeSet cs = new ChangeSet();
        String result;

        // Hateful natives kill the scout right away.
        Player player = unit.getOwner();
        Tension tension = settlement.getAlarm(player);
        if (tension != null && tension.getLevel() == Tension.Level.HATEFUL) {
            cs.addDispose(serverPlayer, unit.getLocation(), unit);
            result = "die";
        } else {
            // Otherwise player gets to visit, and learn about the settlement.
            int gold = 0;
            Tile tile = settlement.getTile();
            int radius = unit.getLineOfSight();
            UnitType skill = settlement.getLearnableSkill();
            if (settlement.hasBeenVisited()) {
                // Pre-visited settlements are a noop.
                result = "nothing";
            } else if (skill != null
                       && skill.hasAbility("model.ability.expertScout")
                       && unit.getType().canBeUpgraded(skill, ChangeType.NATIVES)) {
                // If the scout can be taught to be an expert it will be.
                // TODO: in the old code the settlement retains the
                // teaching ability.  Is this Col1 compliant?
                unit.setType(settlement.getLearnableSkill());
                // settlement.setLearnableSkill(null);
                cs.add(See.only(serverPlayer), unit);
                result = "expert";
            } else if (random.nextInt(3) == 0) {
                // Otherwise 1/3 of cases are tales...
                radius = Math.max(radius, IndianSettlement.TALES_RADIUS);
                result = "tales";
            } else {
                // ...and the rest are beads.
                gold = (random.nextInt(400) * settlement.getBonusMultiplier())
                    + 50;
                if (unit.hasAbility("model.ability.expertScout")) {
                    gold = (gold * 11) / 10;
                }
                serverPlayer.modifyGold(gold);
                settlement.getOwner().modifyGold(-gold);
                cs.addPartial(See.only(serverPlayer), serverPlayer,
                              "gold", "score");
                result = "beads";
            }

            // Update settlement tile with new information, and any
            // newly visible tiles, possibly with enhanced radius.
            settlement.setVisited(player);
            tile.updateIndianSettlementInformation(player);
            cs.add(See.only(serverPlayer), tile);
            Map map = getFreeColServer().getGame().getMap();
            for (Tile t : map.getSurroundingTiles(tile, radius)) {
                if (!serverPlayer.canSee(t) && (t.isLand() || t.isCoast())) {
                    player.setExplored(t);
                    cs.add(See.only(serverPlayer), t);
                }
            }

            // If the unit did not get promoted, update it for moves.
            unit.setMovesLeft(0);
            if (!"expert".equals(result)) {
                cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
            }
        }
        // Always add result.
        cs.addAttribute(See.only(serverPlayer), "result", result);

        // Other players may be able to see unit disappearing, or
        // learning.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Denounce an existing mission.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is denouncing.
     * @param unit The <code>Unit</code> denouncing.
     * @param settlement The <code>IndianSettlement</code> containing the
     *                   mission to denounce.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element denounceMission(ServerPlayer serverPlayer, Unit unit,
                                   IndianSettlement settlement) {
        // Determine result
        Unit missionary = settlement.getMissionary();
        ServerPlayer enemy = (ServerPlayer) missionary.getOwner();
        double denounce = random.nextDouble() * enemy.getImmigration()
            / (serverPlayer.getImmigration() + 1);
        if (missionary.hasAbility("model.ability.expertMissionary")) {
            denounce += 0.2;
        }
        if (unit.hasAbility("model.ability.expertMissionary")) {
            denounce -= 0.2;
        }

        if (denounce < 0.5) { // Success, remove old mission and establish ours
            return establishMission(serverPlayer, unit, settlement);
        }

        ChangeSet cs = new ChangeSet();

        // Denounce failed
        cs.addMessage(See.only(serverPlayer),
            new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                             "indianSettlement.mission.noDenounce",
                             serverPlayer, unit)
                .addStringTemplate("%nation%", settlement.getOwner().getNationName()));
        cs.addDispose(serverPlayer, unit.getLocation(), unit);

        // Others can see missionary disappear
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Establish a new mission.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is establishing.
     * @param unit The missionary <code>Unit</code>.
     * @param settlement The <code>IndianSettlement</code> to establish at.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element establishMission(ServerPlayer serverPlayer, Unit unit,
                                    IndianSettlement settlement) {
        ChangeSet cs = new ChangeSet();

        Unit missionary = settlement.getMissionary();
        if (missionary != null) {
            ServerPlayer enemy = (ServerPlayer) missionary.getOwner();
            settlement.setMissionary(null);

            // Inform the enemy of loss of mission
            cs.addDispose(enemy, settlement.getTile(), missionary);
            cs.addMessage(See.only(enemy),
                new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                                 "indianSettlement.mission.denounced",
                                 settlement)
                    .addStringTemplate("%settlement%", settlement.getLocationName()));
        }

        // Result depends on tension wrt this settlement.
        // Establish if at least not angry.
        Tension tension = settlement.getAlarm(serverPlayer);
        if (tension == null) { // TODO: fix this
            tension = new Tension(0);
            settlement.setAlarm(serverPlayer, tension);
        }
        switch (tension.getLevel()) {
        case HATEFUL: case ANGRY:
            cs.addDispose(serverPlayer, unit.getLocation(), unit);
            break;
        case HAPPY: case CONTENT: case DISPLEASED:
            settlement.setMissionary(unit); //TODO: tension change?
            if (missionary == null) {
                cs.add(See.only(serverPlayer), settlement);
            }
        }
        String messageId = "indianSettlement.mission."
            + settlement.getAlarm(serverPlayer).toString().toLowerCase();
        cs.addMessage(See.only(serverPlayer),
            new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                             messageId, serverPlayer, unit)
                .addStringTemplate("%nation%", settlement.getOwner().getNationName()));

        // Others can see missionary disappear and settlement acquire
        // mission.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Incite a settlement against an enemy.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is inciting.
     * @param unit The missionary <code>Unit</code> inciting.
     * @param settlement The <code>IndianSettlement</code> to incite.
     * @param enemy The <code>Player</code> to be incited against.
     * @param gold The amount of gold in the bribe.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element incite(ServerPlayer serverPlayer, Unit unit,
                          IndianSettlement settlement,
                          Player enemy, int gold) {
        ChangeSet cs = new ChangeSet();

        // How much gold will be needed?
        ServerPlayer enemyPlayer = (ServerPlayer) enemy;
        Player nativePlayer = settlement.getOwner();
        Tension payingTension = nativePlayer.getTension(serverPlayer);
        Tension targetTension = nativePlayer.getTension(enemyPlayer);
        int payingValue = (payingTension == null) ? 0 : payingTension.getValue();
        int targetValue = (targetTension == null) ? 0 : targetTension.getValue();
        int goldToPay = (payingTension != null && targetTension != null
                         && payingValue > targetValue) ? 10000 : 5000;
        goldToPay += 20 * (payingValue - targetValue);
        goldToPay = Math.max(goldToPay, 650);

        // Try to incite?
        if (gold < 0) { // Initial enquiry.
            cs.addAttribute(See.only(serverPlayer),
                            "gold", Integer.toString(goldToPay));
        } else if (gold < goldToPay || serverPlayer.getGold() < gold) {
            cs.addMessage(See.only(serverPlayer),
                new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                                 "indianSettlement.inciteGoldFail",
                                 serverPlayer, settlement)
                    .addStringTemplate("%player%", enemyPlayer.getNationName())
                    .addAmount("%amount%", goldToPay));
            cs.addAttribute(See.only(serverPlayer), "gold", "0");
            unit.setMovesLeft(0);
            cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
        } else {
            // Success.  Raise the tension for the native player with respect
            // to the european player.  Let resulting stance changes happen
            // naturally in the AI player turn/s.
            cs.add(See.only(enemyPlayer),
                   nativePlayer.modifyTension(enemyPlayer,
                                              Tension.WAR_MODIFIER));
            cs.add(See.only(serverPlayer),
                   enemyPlayer.modifyTension(serverPlayer,
                                             Tension.TENSION_ADD_WAR_INCITER));
            cs.addAttribute(See.only(serverPlayer),
                            "gold", Integer.toString(gold));
            serverPlayer.modifyGold(-gold);
            nativePlayer.modifyGold(gold);
            cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
            unit.setMovesLeft(0);
            cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
        }

        // Others might include enemy.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Set a unit destination.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param unit The <code>Unit</code> to set the destination for.
     * @param destination The <code>Location</code> to set as destination.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element setDestination(ServerPlayer serverPlayer, Unit unit,
                                  Location destination) {
        if (unit.getTradeRoute() != null) unit.setTradeRoute(null);
        unit.setDestination(destination);

        // Others can not see a destination change.
        return new ChangeSet().add(See.only(serverPlayer), unit)
            .build(serverPlayer);
    }


    /**
     * Is there work for a unit to do at a stop?
     *
     * @param unit The <code>Unit</code> to check.
     * @param stop The <code>Stop</code> to test.
     * @return True if the unit should load or unload cargo at the stop.
     */
    private boolean hasWorkAtStop(Unit unit, Stop stop) {
        ArrayList<GoodsType> stopGoods = stop.getCargo();
        int cargoSize = stopGoods.size();
        for (Goods goods : unit.getGoodsList()) {
            GoodsType type = goods.getType();
            if (stopGoods.contains(type)) {
                if (unit.getLoadableAmount(type) > 0) {
                    // There is space on the unit to load some more
                    // of this goods type, so return true if there is
                    // some available at the stop.
                    Location loc = stop.getLocation();
                    if (loc instanceof Colony) {
                        if (((Colony) loc).getExportAmount(type) > 0) {
                            return true;
                        }
                    } else if (loc instanceof Europe) {
                        return true;
                    }
                } else {
                    cargoSize--; // No room for more of this type.
                }
            } else {
                return true; // This type should be unloaded here.
            }
        }

        // Return true if there is space left, and something to load.
        return unit.getSpaceLeft() > 0 && cargoSize > 0;
    }

    /**
     * Set current stop of a unit to the next valid stop if any.
     *
     * @param serverPlayer The <code>ServerPlayer</code> the unit belongs to.
     * @param unit The <code>Unit</code> to update.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element updateCurrentStop(ServerPlayer serverPlayer, Unit unit) {
        // Check if there is a valid current stop?
        int current = unit.validateCurrentStop();
        if (current < 0) return null; // No valid stop.

        ArrayList<Stop> stops = unit.getTradeRoute().getStops();
        int next = current;
        for (;;) {
            if (++next >= stops.size()) next = 0;
            if (next == current) break;
            if (hasWorkAtStop(unit, stops.get(next))) break;
        }

        // Next is the updated stop.
        // Could do just a partial update of currentStop if we did not
        // also need to set the unit destination.
        unit.setCurrentStop(next);
        unit.setDestination(stops.get(next).getLocation());

        // Others can not see a stop change.
        return new ChangeSet().add(See.only(serverPlayer), unit)
            .build(serverPlayer);
    }

    /**
     * Move goods from current location to another.
     *
     * @param goods The <code>Goods</code> to move.
     * @param loc The new <code>Location</code>.
     */
    public void moveGoods(Goods goods, Location loc)
        throws IllegalStateException {
        Location oldLoc = goods.getLocation();
        if (oldLoc == null) {
            throw new IllegalStateException("Goods in null location.");
        } else if (loc == null) {
            ; // Dumping is allowed
        } else if (loc instanceof Unit) {
            if (((Unit) loc).isInEurope()) {
                if (!(oldLoc instanceof Unit && ((Unit) oldLoc).isInEurope())) {
                    throw new IllegalStateException("Goods and carrier not both in Europe.");
                }
            } else if (loc.getTile() == null) {
                throw new IllegalStateException("Carrier not on the map.");
            } else if (oldLoc instanceof IndianSettlement) {
                // Can not be co-located when buying from natives.
            } else if (loc.getTile() != oldLoc.getTile()) {
                throw new IllegalStateException("Goods and carrier not co-located.");
            }
        } else if (loc instanceof IndianSettlement) {
            // Can not be co-located when selling to natives.
        } else if (loc instanceof Colony) {
            if (oldLoc instanceof Unit
                && ((Unit) oldLoc).getOwner() != ((Colony) loc).getOwner()) {
                // Gift delivery
            } else if (loc.getTile() != oldLoc.getTile()) {
                throw new IllegalStateException("Goods and carrier not both in Colony.");
            }
        } else if (loc.getGoodsContainer() == null) {
            throw new IllegalStateException("New location with null GoodsContainer.");
        }

        oldLoc.remove(goods);
        goods.setLocation(null);

        if (loc != null) {
            loc.add(goods);
            goods.setLocation(loc);
        }
    }

    /**
     * Buy from a settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is buying.
     * @param unit The <code>Unit</code> that will carry the goods.
     * @param settlement The <code>IndianSettlement</code> to buy from.
     * @param goods The <code>Goods</code> to buy.
     * @param amount How much gold to pay.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element buyFromSettlement(ServerPlayer serverPlayer, Unit unit,
                                     IndianSettlement settlement,
                                     Goods goods, int amount) {
        if (!isTransactionSessionOpen(unit, settlement)) {
            return Message.clientError("Trying to buy without opening a transaction session");
        }
        java.util.Map<String,Object> session
            = getTransactionSession(unit, settlement);
        if (!(Boolean) session.get("canBuy")) {
            return Message.clientError("Trying to buy in a session where buying is not allowed.");
        }
        if (unit.getSpaceLeft() <= 0) {
            return Message.clientError("Unit is full, unable to buy.");
        }
        // Check that this is the agreement that was made
        AIPlayer ai = (AIPlayer) getFreeColServer().getAIMain().getAIObject(settlement.getOwner());
        int returnGold = ai.buyProposition(unit, settlement, goods, amount);
        if (returnGold != amount) {
            return Message.clientError("This was not the price we agreed upon! Cheater?");
        }
        // Check this is funded.
        if (serverPlayer.getGold() < amount) {
            return Message.clientError("Insufficient gold to buy.");
        }

        ChangeSet cs = new ChangeSet();

        // Valid, make the trade.
        moveGoods(goods, unit);
        cs.add(See.perhaps(), unit);

        Player settlementPlayer = settlement.getOwner();
        settlement.updateWantedGoods();
        settlement.getTile().updateIndianSettlementInformation(serverPlayer);
        settlement.modifyAlarm(serverPlayer, -amount / 50);
        settlementPlayer.modifyGold(amount);
        serverPlayer.modifyGold(-amount);
        cs.add(See.only(serverPlayer), settlement);
        cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
        session.put("actionTaken", true);
        session.put("canBuy", false);

        // Others can see the unit capacity.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Sell to a settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is selling.
     * @param unit The <code>Unit</code> carrying the goods.
     * @param settlement The <code>IndianSettlement</code> to sell to.
     * @param goods The <code>Goods</code> to sell.
     * @param amount How much gold to expect.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element sellToSettlement(ServerPlayer serverPlayer, Unit unit,
                                    IndianSettlement settlement,
                                    Goods goods, int amount) {
        if (!isTransactionSessionOpen(unit, settlement)) {
            return Message.clientError("Trying to sell without opening a transaction session");
        }
        java.util.Map<String,Object> session
            = getTransactionSession(unit, settlement);
        if (!(Boolean) session.get("canSell")) {
            return Message.clientError("Trying to sell in a session where selling is not allowed.");
        }

        // Check that the gold is the agreed amount
        AIPlayer ai = (AIPlayer) getFreeColServer().getAIMain().getAIObject(settlement.getOwner());
        int returnGold = ai.sellProposition(unit, settlement, goods, amount);
        if (returnGold != amount) {
            return Message.clientError("This was not the price we agreed upon! Cheater?");
        }

        ChangeSet cs = new ChangeSet();

        // Valid, make the trade.
        moveGoods(goods, settlement);
        cs.add(See.perhaps(), unit);

        Player settlementPlayer = settlement.getOwner();
        settlementPlayer.modifyGold(-amount);
        settlement.modifyAlarm(serverPlayer, -settlement.getPrice(goods) / 500);
        serverPlayer.modifyGold(amount);
        settlement.updateWantedGoods();
        settlement.getTile().updateIndianSettlementInformation(serverPlayer);
        cs.add(See.only(serverPlayer), settlement);
        cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
        session.put("actionTaken", true);
        session.put("canSell", false);

        // Others can see the unit capacity.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Deliver gift to settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is delivering.
     * @param unit The <code>Unit</code> that is delivering.
     * @param goods The <code>Goods</code> to deliver.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element deliverGiftToSettlement(ServerPlayer serverPlayer,
                                           Unit unit, Settlement settlement,
                                           Goods goods) {
        if (!isTransactionSessionOpen(unit, settlement)) {
            return Message.clientError("Trying to deliverGift without opening a transaction session");
        }

        ChangeSet cs = new ChangeSet();
        java.util.Map<String,Object> session
            = getTransactionSession(unit, settlement);

        Tile tile = settlement.getTile();
        moveGoods(goods, settlement);
        cs.add(See.perhaps(), unit);
        if (settlement instanceof IndianSettlement) {
            IndianSettlement indianSettlement = (IndianSettlement) settlement;
            indianSettlement.modifyAlarm(serverPlayer, -indianSettlement.getPrice(goods) / 50);
            indianSettlement.updateWantedGoods();
            tile.updateIndianSettlementInformation(serverPlayer);
            cs.add(See.only(serverPlayer), settlement);
        }
        session.put("actionTaken", true);
        session.put("canGift", false);

        // Inform the receiver of the gift.
        ServerPlayer receiver = (ServerPlayer) settlement.getOwner();
        if (!receiver.isAI() && receiver.isConnected()
            && settlement instanceof Colony) {
            cs.add(See.only(receiver), unit);
            cs.add(See.only(receiver), settlement);
            cs.addMessage(See.only(receiver),
                new ModelMessage(ModelMessage.MessageType.GIFT_GOODS,
                                 "model.unit.gift", settlement, goods.getType())
                    .addStringTemplate("%player%", serverPlayer.getNationName())
                    .add("%type%", goods.getNameKey())
                    .addAmount("%amount%", goods.getAmount())
                    .addName("%colony%", settlement.getName()));
        }
        logger.info("Gift delivered by unit: " + unit.getId()
                    + " to settlement: " + settlement.getName());

        // Others can see unit capacity, receiver gets it own items.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Load cargo.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is loading.
     * @param unit The <code>Unit</code> to load.
     * @param goods The <code>Goods</code> to load.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element loadCargo(ServerPlayer serverPlayer, Unit unit,
                             Goods goods) {
        ChangeSet cs = new ChangeSet();

        goods.adjustAmount();
        moveGoods(goods, unit);
        if (unit.getInitialMovesLeft() != unit.getMovesLeft()) {
            unit.setMovesLeft(0);
        }

        // Only have to update the carrier location, as that *must*
        // include the original location of the goods.  Others can see
        // capacity change.
        cs.add(See.only(serverPlayer),
               (FreeColGameObject) unit.getLocation());
        cs.add(See.perhaps().except(serverPlayer), unit);

        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Unload cargo.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is unloading.
     * @param unit The <code>Unit</code> to unload.
     * @param goods The <code>Goods</code> to unload.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element unloadCargo(ServerPlayer serverPlayer, Unit unit,
                               Goods goods) {
        ChangeSet cs = new ChangeSet();

        FreeColGameObject update;
        Location loc;
        if (unit.isInEurope()) { // Must be a dump of boycotted goods
            loc = null;
        } else if (unit.getTile() == null) {
            return Message.clientError("Unit not on the map.");
        } else if (unit.getTile().getSettlement() instanceof Colony) {
            loc = unit.getTile().getSettlement();
        } else { // Dump of goods onto a tile
            loc = null;
        }
        goods.adjustAmount();
        moveGoods(goods, loc);
        if (unit.getInitialMovesLeft() != unit.getMovesLeft()) {
            unit.setMovesLeft(0);
        }

        if (loc instanceof Settlement) {
            cs.add(See.only(serverPlayer), (FreeColGameObject) loc);
        }
        // Always update unit, to show goods are gone.
        // Others might see a capacity change.
        cs.add(See.perhaps(), unit);

        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Clear the specialty of a unit.
     *
     * @param serverPlayer The owner of the unit.
     * @param unit The <code>Unit</code> to clear the speciality of.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element clearSpeciality(ServerPlayer serverPlayer, Unit unit) {
        UnitType newType = unit.getType()
            .getUnitTypeChange(ChangeType.CLEAR_SKILL, serverPlayer);
        if (newType == null) {
            return Message.clientError("Can not clear unit speciality: "
                                       + unit.getId());
        }
        // There can be some restrictions that may prevent the
        // clearing of the speciality.  For example, teachers cannot
        // not be cleared of their speciality.
        Location oldLocation = unit.getLocation();
        if (oldLocation instanceof Building
            && !((Building) oldLocation).canAdd(newType)) {
            return Message.clientError("Cannot clear speciality, building does not allow new unit type");
        }

        // Valid, change type.
        unit.setType(newType);

        // Update just the unit, others can not see it.  TODO: check!
        return new ChangeSet().add(See.only(serverPlayer), unit)
            .build(serverPlayer);
    }


    /**
     * Disband a unit.
     * 
     * @param serverPlayer The owner of the unit.
     * @param unit The <code>Unit</code> to disband.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element disbandUnit(ServerPlayer serverPlayer, Unit unit) {
        ChangeSet cs = new ChangeSet();

        // Dispose of the unit.
        cs.addDispose(serverPlayer, unit.getLocation(), unit);

        // Others can see the unit removal and the space it leaves.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Generates a skill that could be taught from a settlement on the
     * given Tile.
     *
     * @param map The <code>Map</code>.
     * @param tile The <code>Tile</code> where the settlement will be located.
     * @param nationType The <code>IndianNationType</code> teaching.
     * @return A skill that can be taught to Europeans.
     */
    private UnitType generateSkillForLocation(Map map, Tile tile,
                                              IndianNationType nationType) {
        List<RandomChoice<UnitType>> skills = nationType.getSkills();
        java.util.Map<GoodsType, Integer> scale
            = new HashMap<GoodsType, Integer>();

        for (RandomChoice<UnitType> skill : skills) {
            scale.put(skill.getObject().getExpertProduction(), 1);
        }

        Iterator<Position> iter = map.getAdjacentIterator(tile.getPosition());
        while (iter.hasNext()) {
            Map.Position p = iter.next();
            Tile t = map.getTile(p);
            for (GoodsType goodsType : scale.keySet()) {
                scale.put(goodsType, scale.get(goodsType).intValue()
                          + t.potential(goodsType, null));
            }
        }

        List<RandomChoice<UnitType>> scaledSkills
            = new ArrayList<RandomChoice<UnitType>>();
        for (RandomChoice<UnitType> skill : skills) {
            UnitType unitType = skill.getObject();
            int scaleValue = scale.get(unitType.getExpertProduction()).intValue();
            scaledSkills.add(new RandomChoice<UnitType>(unitType, skill.getProbability() * scaleValue));
        }

        UnitType skill = RandomChoice.getWeightedRandom(random, scaledSkills);
        if (skill == null) {
            // Seasoned Scout
            Specification spec = getGame().getSpecification();
            List<UnitType> unitList
                = spec.getUnitTypesWithAbility("model.ability.expertScout");
            return unitList.get(random.nextInt(unitList.size()));
        }
        return skill;
    }

    /**
     * Build a settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is building.
     * @param unit The <code>Unit</code> that is building.
     * @param name The new settlement name.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element buildSettlement(ServerPlayer serverPlayer, Unit unit,
                                   String name) {
        ChangeSet cs = new ChangeSet();
        Game game = serverPlayer.getGame();

        // Build settlement
        Tile tile = unit.getTile();
        Settlement settlement;
        if (Player.ASSIGN_SETTLEMENT_NAME.equals(name)) {
            name = serverPlayer.getSettlementName();
            if (Player.ASSIGN_SETTLEMENT_NAME.equals(name)) {
                // Load settlement names on demand.
                serverPlayer.installSettlementNames(Messages
                        .getSettlementNames(serverPlayer), random);
                name = serverPlayer.getSettlementName();
            }
        }
        if (serverPlayer.isEuropean()) {
            settlement = new Colony(game, serverPlayer, name, tile);
        } else {
            IndianNationType nationType
                = (IndianNationType) serverPlayer.getNationType();
            UnitType skill = generateSkillForLocation(game.getMap(), tile,
                                                      nationType);
            settlement = new IndianSettlement(game, serverPlayer, tile,
                                              name, false, skill,
                                              new HashSet<Player>(), null);
            // TODO: its lame that the settlement starts with no contacts
        }
        settlement.placeSettlement();

        // Join.
        unit.setState(UnitState.IN_COLONY);
        unit.setLocation(settlement);
        unit.setMovesLeft(0);

        // Update with settlement tile, and newly owned tiles.
        List<FreeColGameObject> tiles = new ArrayList<FreeColGameObject>();
        tiles.addAll(settlement.getOwnedTiles());
        cs.add(See.only(serverPlayer), tiles);

        cs.addHistory(serverPlayer,
            new HistoryEvent(game.getTurn(),
                             HistoryEvent.EventType.FOUND_COLONY)
                .addName("%colony%", settlement.getName()));

        // Also send any tiles that can now be seen because the colony
        // can perhaps see further than the founding unit.
        if (settlement.getLineOfSight() > unit.getLineOfSight()) {
            tiles.clear();
            Map map = game.getMap();
            for (Tile t : map.getSurroundingTiles(tile, unit.getLineOfSight()+1,
                                                  settlement.getLineOfSight())) {
                if (!tiles.contains(t)) tiles.add(t);
            }
            cs.add(See.only(serverPlayer), tiles);
        }

        // Others can see tile changes.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Join a colony.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param unit The <code>Unit</code> that is joining.
     * @param colony The <code>Colony</code> to join.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element joinColony(ServerPlayer serverPlayer, Unit unit,
                              Colony colony) {
        ChangeSet cs = new ChangeSet();
        List<Tile> ownedTiles = colony.getOwnedTiles();
        Tile tile = colony.getTile();

        // Join.
        unit.setState(UnitState.IN_COLONY);
        unit.setLocation(colony);
        unit.setMovesLeft(0);

        // Update with colony tile, and tiles now owned.
        cs.add(See.only(serverPlayer), tile);
        Map map = serverPlayer.getGame().getMap();
        for (Tile t : map.getSurroundingTiles(tile, colony.getRadius())) {
            if (t.getOwningSettlement() == colony && !ownedTiles.contains(t)) {
                cs.add(See.only(serverPlayer), t);
            }
        }

        // Potentially lots to see.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Abandon a settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is abandoning.
     * @param settlement The <code>Settlement</code> to abandon.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element abandonSettlement(ServerPlayer serverPlayer,
                                     Settlement settlement) {
        ChangeSet cs = new ChangeSet();

        // Collect the tiles the settlement owns before disposing.
        for (Tile t : settlement.getOwnedTiles()) {
            if (t != settlement.getTile()) cs.add(See.only(serverPlayer), t);
        }

        // Create history event before disposing.
        if (settlement instanceof Colony) {
            cs.addHistory(serverPlayer,
                new HistoryEvent(getGame().getTurn(),
                                 HistoryEvent.EventType.ABANDON_COLONY)
                    .addName("%colony%", settlement.getName()));
        }

        // Now do the dispose.
        cs.addDispose(serverPlayer, settlement.getTile(), settlement);

        // TODO: Player.settlements is still being fixed on the client side.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Claim land.
     *
     * @param serverPlayer The <code>ServerPlayer</code> claiming.
     * @param tile The <code>Tile</code> to claim.
     * @param settlement The <code>Settlement</code> to claim for.
     * @param price The price to pay for the land, which must agree
     *              with the owner valuation, unless negative which
     *              denotes stealing.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element claimLand(ServerPlayer serverPlayer, Tile tile,
                             Settlement settlement, int price) {
        ChangeSet cs = new ChangeSet();

        Player owner = tile.getOwner();
        Settlement ownerSettlement = tile.getOwningSettlement();
        tile.setOwningSettlement(settlement);
        tile.setOwner(serverPlayer);
        tile.updatePlayerExploredTiles();

        // Update the tile for all, and privately any now-angrier
        // owners, or the player gold if a price was paid.
        cs.add(See.only(serverPlayer), tile);
        if (price > 0) {
            serverPlayer.modifyGold(-price);
            owner.modifyGold(price);
            cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
        } else if (price < 0 && owner.isIndian()) {
            cs.add(See.only(serverPlayer),
                   owner.modifyTension(serverPlayer, Tension.TENSION_ADD_LAND_TAKEN,
                                                     (IndianSettlement) ownerSettlement));
        }

        // Others can see the tile.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Accept a diplomatic trade.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is trading.
     * @param other The other <code>ServerPlayer</code> that is trading.
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> to trade with.
     * @param agreement The <code>DiplomaticTrade</code> to consider.
     * @return An <code>Element</code> encapsulating the changes.
     */
    private Element acceptTrade(ServerPlayer serverPlayer, ServerPlayer other,
                                Unit unit, Settlement settlement,
                                DiplomaticTrade agreement) {
        closeTransactionSession(unit, settlement);

        ChangeSet cs = new ChangeSet();

        cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
        for (TradeItem tradeItem : agreement.getTradeItems()) {
            // Check trade carefully before committing.
            if (!tradeItem.isValid()) {
                logger.warning("Trade with invalid tradeItem: "
                               + tradeItem.toString());
                continue;
            }
            ServerPlayer source = (ServerPlayer) tradeItem.getSource();
            if (source != serverPlayer && source != other) {
                logger.warning("Trade with invalid source: "
                               + ((source == null) ? "null" : source.getId()));
                continue;
            }
            ServerPlayer dest = (ServerPlayer) tradeItem.getDestination();
            if (dest != serverPlayer && dest != other) {
                logger.warning("Trade with invalid destination: "
                               + ((dest == null) ? "null" : dest.getId()));
                continue;
            }

            // Collect changes for updating.  Not very OO but
            // TradeItem should not know about server internals.
            // Take care to show items that change hands to the *old*
            // owner too.
            Stance stance = tradeItem.getStance();
            if (stance != null
                && !changeStance(serverPlayer, stance, other, cs)) {
                logger.warning("Stance trade failure");
            }
            Colony colony = tradeItem.getColony();
            if (colony != null) {
                ServerPlayer former = (ServerPlayer) colony.getOwner();
                colony.changeOwner(tradeItem.getDestination());
                List<FreeColGameObject> tiles = new ArrayList<FreeColGameObject>();
                for (Tile t : colony.getOwnedTiles()) tiles.add(t);
                cs.add(See.only(former), tiles);
            }
            int gold = tradeItem.getGold();
            if (gold > 0) {
                tradeItem.getSource().modifyGold(-gold);
                tradeItem.getDestination().modifyGold(gold);
                cs.addPartial(See.only(serverPlayer), serverPlayer,
                                  "gold", "score");
                cs.addPartial(See.only(other), other, "gold", "score");
            }
            Goods goods = tradeItem.getGoods();
            if (goods != null) {
                moveGoods(goods, settlement);
                cs.add(See.only(serverPlayer), unit);
                cs.add(See.only(other), settlement);
            }
            Unit newUnit = tradeItem.getUnit();
            if (newUnit != null) {
                ServerPlayer former = (ServerPlayer) newUnit.getOwner();
                unit.setOwner(tradeItem.getDestination());
                cs.add(See.only(former), newUnit);
            }
        }

        // Original player also sees conclusion of diplomacy.
        sendToOthers(serverPlayer, cs);
        Element element = cs.build(serverPlayer);
        element.appendChild(new DiplomacyMessage(unit, settlement, agreement)
                            .toXMLElement());
        return element;
    }

    /**
     * Reject a diplomatic trade.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is trading.
     * @param other The other <code>ServerPlayer</code> that is trading.
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> to trade with.
     * @param agreement The <code>DiplomaticTrade</code> to consider.
     * @return An <code>Element</code> encapsulating the changes.
     */
    private Element rejectTrade(ServerPlayer serverPlayer, ServerPlayer other,
                                Unit unit, Settlement settlement,
                                DiplomaticTrade agreement) {
        ChangeSet cs = new ChangeSet();

        closeTransactionSession(unit, settlement);
        cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
        Element element = cs.build(serverPlayer);
        element.appendChild(new DiplomacyMessage(unit, settlement, agreement)
                            .toXMLElement());
        return element;
    }

    /**
     * Diplomatic trades.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is trading.
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> to trade with.
     * @param agreement The <code>DiplomaticTrade</code> to consider.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element diplomaticTrade(ServerPlayer serverPlayer, Unit unit,
                                   Settlement settlement,
                                   DiplomaticTrade agreement) {
        DiplomacyMessage diplomacy;
        java.util.Map<String,Object> session;
        DiplomaticTrade current;
        ServerPlayer other = (ServerPlayer) settlement.getOwner();
        unit.setMovesLeft(0);

        switch (agreement.getStatus()) {
        case ACCEPT_TRADE:
            if (!isTransactionSessionOpen(unit, settlement)) {
                return Message.clientError("Accepting without open session.");
            }
            session = getTransactionSession(unit, settlement);
            // Act on what was proposed, not what is in the accept
            // message to frustrate tricksy client changing the conditions.
            current = (DiplomaticTrade) session.get("agreement");
            current.setStatus(TradeStatus.ACCEPT_TRADE);

            diplomacy = new DiplomacyMessage(unit, settlement, current);
            sendElement(other, diplomacy.toXMLElement());
            return acceptTrade(serverPlayer, other, unit, settlement, current);

        case REJECT_TRADE:
            if (!isTransactionSessionOpen(unit, settlement)) {
                return Message.clientError("Rejecting without open session.");
            }
            session = getTransactionSession(unit, settlement);
            current = (DiplomaticTrade) session.get("agreement");
            current.setStatus(TradeStatus.REJECT_TRADE);

            diplomacy = new DiplomacyMessage(unit, settlement, current);
            sendElement(other, diplomacy.toXMLElement());
            return rejectTrade(serverPlayer, other, unit, settlement, current);

        case PROPOSE_TRADE:
            session = getTransactionSession(unit, settlement);
            current = agreement;
            session.put("agreement", agreement);

            // If the unit is on a carrier we need to update the
            // client with it first as the diplomacy message refers to it.
            // Ask the other player about this proposal.
            diplomacy = new DiplomacyMessage(unit, settlement, agreement);
            Element proposal = diplomacy.toXMLElement();
            if (!unit.isVisibleTo(other)) {
                proposal.appendChild(new ChangeSet().add(See.only(other), unit)
                                     .build(other));
            }
            Element response = askElement(other, proposal);

            // What did they think?
            diplomacy = (response == null) ? null
                : new DiplomacyMessage(getGame(), response);
            agreement = (diplomacy == null) ? null : diplomacy.getAgreement();
            TradeStatus status = (agreement == null) ? TradeStatus.REJECT_TRADE
                : agreement.getStatus();
            switch (status) {
            case ACCEPT_TRADE:
                // Act on the proposed agreement, not what was passed back
                // as accepted.
                current.setStatus(TradeStatus.ACCEPT_TRADE);
                return acceptTrade(serverPlayer, other, unit, settlement,
                                   current);

            case PROPOSE_TRADE:
                // Save the counter-proposal, sanity test, then pass back.
                if ((ServerPlayer) agreement.getSender() == serverPlayer
                    && (ServerPlayer) agreement.getRecipient() == other) {
                    session.put("agreement", agreement);
                    return diplomacy.toXMLElement();
                }
                logger.warning("Trade counter-proposal was incompatible.");
                // Fall through

            case REJECT_TRADE:
            default:
                // Reject the current trade.
                current.setStatus(TradeStatus.REJECT_TRADE);
                return rejectTrade(serverPlayer, other, unit, settlement,
                                   current);
            }

        default:
            return Message.clientError("Bogus trade");
        }
    }

}
