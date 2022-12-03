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

package net.sf.freecol.client.control;

import static net.sf.freecol.common.model.Constants.INFINITY;
import static net.sf.freecol.common.model.Constants.STEAL_LAND;
import static net.sf.freecol.common.util.CollectionUtils.allSame;
import static net.sf.freecol.common.util.CollectionUtils.alwaysTrue;
import static net.sf.freecol.common.util.CollectionUtils.find;
import static net.sf.freecol.common.util.CollectionUtils.none;
import static net.sf.freecol.common.util.CollectionUtils.removeInPlace;
import static net.sf.freecol.common.util.CollectionUtils.transform;
import static net.sf.freecol.common.util.Utils.delay;
import static net.sf.freecol.common.util.Utils.deleteFile;

import static com.ea.async.Async.await;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ChoiceItem;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.option.FreeColActionUI;
import net.sf.freecol.client.gui.panel.FreeColPanel;
import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.debug.DebugUtils;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.i18n.NameCache;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyWas;
import net.sf.freecol.common.model.Constants.ArmedUnitSettlementAction;
import net.sf.freecol.common.model.Constants.ClaimAction;
import net.sf.freecol.common.model.Constants.IndianDemandAction;
import net.sf.freecol.common.model.Constants.MissionaryAction;
import net.sf.freecol.common.model.Constants.ScoutColonyAction;
import net.sf.freecol.common.model.Constants.ScoutIndianSettlementAction;
import net.sf.freecol.common.model.Constants.TradeAction;
import net.sf.freecol.common.model.Constants.TradeBuyAction;
import net.sf.freecol.common.model.Constants.TradeSellAction;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeContext;
import net.sf.freecol.common.model.DiplomaticTrade.TradeStatus;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.Europe.MigrationType;
import net.sf.freecol.common.model.EuropeWas;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Game.LogoutReason;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.HighScore;
import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.LastSale;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.LostCityRumour;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.MarketWas;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.ModelMessage.MessageType;
import net.sf.freecol.common.model.Modifier;
import net.sf.freecol.common.model.Monarch.MonarchAction;
import net.sf.freecol.common.model.Nameable;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.NativeTrade;
import net.sf.freecol.common.model.NativeTrade.NativeTradeAction;
import net.sf.freecol.common.model.NativeTradeItem;
import net.sf.freecol.common.model.ObjectWas;
import net.sf.freecol.common.model.Ownable;
import net.sf.freecol.common.model.PathNode;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Player.NoClaimReason;
import net.sf.freecol.common.model.Region;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovementType;
import net.sf.freecol.common.model.TradeLocation;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.TradeRouteStop;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.Unit.UnitState;
import net.sf.freecol.common.model.UnitChangeType;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.model.UnitTypeChange;
import net.sf.freecol.common.model.UnitWas;
import net.sf.freecol.common.model.WorkLocation;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.common.util.Introspector;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.FreeColServer;


/**
 * The controller that will be used while the game is played.
 */
public final class InGameController extends FreeColClientHolder {

    private static final Logger logger = Logger.getLogger(InGameController.class.getName());

    /**
     * Selecting next unit depends on mode--- either from the active list,
     * from the going-to list, or flush going-to and end the turn.
     */
    private static enum MoveMode {
        NEXT_ACTIVE_UNIT,
        EXECUTE_GOTO_ORDERS,
        END_TURN;

        public MoveMode minimize(MoveMode m) {
            return (this.ordinal() > m.ordinal()) ? m : this;
        }

        public MoveMode maximize(MoveMode m) {
            return (this.ordinal() < m.ordinal()) ? m : this;
        }
    }

    private static final short UNIT_LAST_MOVE_DELAY = 300;

    /** A template to use as a magic cookie for aborted trades. */
    private static final StringTemplate abortTrade
        = StringTemplate.template("");

    /** A comparator for ordering trade route units. */
    private static final Comparator<Unit> tradeRouteUnitComparator
        = Comparator.comparing((Unit u) -> u.getTradeRoute().getName())
        .thenComparing(Function.<Unit>identity());

    /** Current mode for moving units. */
    private MoveMode moveMode = MoveMode.NEXT_ACTIVE_UNIT;

    /** A map of messages to be ignored. */
    private final java.util.Map<String, Integer> messagesToIgnore
        = Collections.synchronizedMap(new HashMap<>());

    /** The messages in the last turn report. */
    private final List<ModelMessage> turnReportMessages = new ArrayList<>();


    /**
     * The constructor to use.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public InGameController(FreeColClient freeColClient) {
        super(freeColClient);

        // FIXME: fetch value of lastSaveGameFile from a persistent
        // client value
        //   lastSaveGameFile = new File(getClientOptions().getString(null));
    }


    // Simple utilities

    /**
     * Play a sound.
     *
     * @param soundKey The sound resource key.
     */
    public void sound(String soundKey) {
        getGUI().playSound(soundKey);
    }
    
    /**
     * Require that it is this client's player's turn.
     * Put up the notYourTurn message if not.
     *
     * @return True if it is our turn.
     */
    private boolean requireOurTurn() {
        if (currentPlayerIsMyPlayer()) return true;
        if (getFreeColClient().isInGame()) {
            showInformationPanel(null, "info.notYourTurn");
        }
        return false;
    }

    /**
     * Convenience function to find an adjacent settlement.  Intended
     * to be called in contexts where we are expecting a settlement to
     * be there, such as when handling a particular move type.
     *
     * @param tile The {@code Tile} to start at.
     * @param direction The {@code Direction} to step.
     * @return A settlement on the adjacent tile if any.
     */
    private Settlement getSettlementAt(Tile tile, Direction direction) {
        return tile.getNeighbourOrNull(direction).getSettlement();
    }

    /**
     * Convenience function to find the nation controlling an adjacent
     * settlement.  Intended to be called in contexts where we are
     * expecting a settlement or unit to be there, such as when
     * handling a particular move type.
     *
     * @param tile The {@code Tile} to start at.
     * @param direction The {@code Direction} to step.
     * @return The name of the nation controlling a settlement on the
     *         adjacent tile if any.
     */
    private StringTemplate getNationAt(Tile tile, Direction direction) {
        Tile newTile = tile.getNeighbourOrNull(direction);
        Player player = null;
        if (newTile.hasSettlement()) {
            player = newTile.getSettlement().getOwner();
        } else if (newTile.getFirstUnit() != null) {
            player = newTile.getFirstUnit().getOwner();
        } else { // should not happen
            player = getGame().getUnknownEnemy();
        }
        return player.getNationLabel();
    }

    /**
     * Defer execution to the event thread.
     *
     * @param runnable The {@code Runnable} to run.
     */
    private void invokeLater(Runnable runnable) {
        getFreeColClient().getGUI().invokeNowOrLater(runnable);
    }

    /**
     * Wrapper for GUI.changeView.
     *
     * @param unit The {@code Unit} to select.
     * @param force Set true if the unit is the same, but *has*
     *     changed in some way (e.g. moves left).
     */
    private void changeView(final Unit unit, final boolean force) {
        invokeLater(() -> getGUI().changeView(unit, force));
    }

    /**
     * Wrapper for GUI.changeView.
     *
     * @param tile An optional {@code Tile} to select.
     */
    private void changeView(final Tile tile) {
        invokeLater(() -> {
                if (tile != null) {
                    getGUI().changeView(tile);
                } else {
                    getGUI().changeView();
                }
            });
    }
    
    /**
     * Wrapper for GUI.displayChat.
     *
     * @param sender The sender of the chat message.
     * @param message What to say.
     * @param color The message color.
     * @param pri If true, the message is private.
     */
    private void displayChat(String sender, String message, Color color,
                            boolean pri) {
        invokeLater(() ->
            getGUI().displayChat(sender, message, color, pri));
    }

    /**
     * Fire the changes for *Was objects.
     *
     * @param objs The *Was objects to fire changes for (nulls allowed).
     */
    private void fireChanges(ObjectWas... objs) {
        invokeLater(() -> {
                for (ObjectWas o : objs) {
                    if (o != null) o.fireChanges();
                }
            });
    }
        
    /**
     * Wrapper for GUI.showColonyPanel.
     *
     * @param colony The {@code Colony} to show.
     * @param unit An optional {@code Unit} to select within the panel.
     */
    private void showColonyPanel(final Colony colony, final Unit unit) {
        invokeLater(() -> {
                getGUI().showColonyPanel(colony, unit);
            });
    }

    /**
     * Display the colony panel for a colony, and select the unit that just
     * arrived there if it is a carrier.
     *
     * @param colony The {@code Colony} to display.
     * @param unit An optional {@code Unit} to select.
     */
    private void showColonyPanelWithCarrier(Colony colony, Unit unit) {
        showColonyPanel(colony, (unit.isCarrier()) ? unit : null);
    }

    /**
     * Wrapper for GUI.showErrorPanel.
     *
     * @param template A {@code StringTemplate} to display.
     * @param message An extra non-i18n message to display if debugging.
     */
    private void error(StringTemplate template, String message) {
        invokeLater(() -> getGUI().showErrorPanel(template, message));
    }

    /**
     * Wrapper for GUI.showEmigrationDialog
     *
     * @param player The {@code Player} who has migrating units.
     * @param foy True if this is a fountain of youth event.
     * @param n The number of migrants available.
     */
    private CompletableFuture<Void> showEmigrationDialog(final Player player, final boolean foy,
                                      final int n) {
        return getGUI().showEmigrationDialog(player, foy).thenAccept(
            (Integer value) -> { // Value is a valid slot
                emigrate(player,
                    Europe.MigrationType.convertToMigrantSlot(value),
                    n-1, foy);
            });
    }

    /**
     * Wrapper for GUI.showEventPanel.
     *
     * @param header The title.
     * @param imageKey A resource key for the image to display.
     * @param footer Optional footer text.
     */
    private void showEventPanel(String header, String imageKey,
                                String footer) {
        invokeLater(() -> getGUI().showEventPanel(header, imageKey, footer));
    }

    /**
     * Wrapper for GUI.showInformationPanel.
     *
     * @param disp An optional object to display in the panel.
     * @param messageId A message identifier.
     */
    private void showInformationPanel(FreeColObject disp, String messageId) {
        showInformationPanel(disp, StringTemplate.template(messageId));
    }

    /**
     * Wrapper for GUI.showInformationPanel.
     *
     * @param disp An optional object to display in the panel.
     * @param template A {@code StringTemplate} to display.
     */
    private void showInformationPanel(final FreeColObject disp,
                                      final StringTemplate template) {
        invokeLater(() -> getGUI().showInformationPanel(disp, template));
    }

    /**
     * Wrapper for GUI.getMissionaryChoice
     *
     * @param unit The {@code Unit} that will enter the settlement.
     * @param is The {@code IndianSettlement} to check.
     * @param direction The {@code Direction} to move the unit.
     */
    private CompletableFuture<Void> getMissionaryChoice(final Unit unit,
        final IndianSettlement is, final Direction direction) {
        final Player player = unit.getOwner();
        final boolean canEstablish = !is.hasMissionary();
        final boolean canDenounce = !canEstablish
            && !is.hasMissionary(player);

        // Offer the choices.
        MissionaryAction act = await(getGUI().getMissionaryChoice(unit, is,
            canEstablish, canDenounce));
        if (act == null) return CompletableFuture.completedFuture(null);
        switch (act) {
        case MISSIONARY_ESTABLISH_MISSION: case MISSIONARY_DENOUNCE_HERESY:
            askServer().missionary(unit, direction,
                    act == MissionaryAction.MISSIONARY_DENOUNCE_HERESY)
            .thenAccept((result) -> { if (result 
                    && is.hasMissionary(player)) {
                    sound("sound.event.missionEstablished");
                    player.invalidateCanSeeTiles();
                }
            });
            break;
        case MISSIONARY_INCITE_INDIANS:
            Player enemy = await(getGUI().getChoice(unit.getTile(),
                StringTemplate.key("missionarySettlement.inciteQuestion"),
                unit, "missionarySettlement.cancel",
                transform(getGame().getLiveEuropeanPlayers(player), alwaysTrue(),
                    p -> new ChoiceItem<>(Messages.message(p.getCountryLabel()), p))));
            if (enemy != null) askServer().incite(unit, is, enemy, -1);
            break;
        default:
            logger.warning("showUseMissionaryDialog fail");
            break;
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Wrapper for GUI.showNamingDialog.
     *
     * @param template A message template.
     * @param defaultName The default name for the object.
     * @param unit The {@code Unit} that is naming.
     * @param handler A callback to handle the user response.
     */
    private CompletableFuture<String> showNamingDialog(final StringTemplate template,
        final String defaultName, final Unit unit) {
        return getGUI().showNamingDialog(template, defaultName, unit);
    }

    /**
     * Wrapper for GUI.showNegotiationDialog and followup.
     *
     * @param unit The {@code Unit} that is negotiating.
     * @param settlement The {@code Settlement} that is negotiating.
     * @param agreement The proposed agreement.
     * @param comment An optional {@code StringTemplate} containing a
     *     commentary message.
     * @param direction An optional {@code Direction} to move the unit
     *     to the settlement on success.
     */
    private CompletableFuture<Void> showNegotiationDialog(final Unit unit,
        final Settlement settlement, final DiplomaticTrade agreement,
        final StringTemplate comment, final Direction direction) {
        final Player player = unit.getOwner();
        return getGUI().showNegotiationDialog(unit, settlement, agreement, comment).thenAccept((DiplomaticTrade dt) -> {
            if (direction != null && dt != null
                && dt.getStatus() != TradeStatus.REJECT_TRADE) {
                moveDiplomacy(unit, direction, dt);
            }
        });
    }

    /**
     * Wrapper for GUI.showStatusPanel, but clear on null.
     *
     * @param message The text message to display on the status panel.
     */
    private void showStatusPanel(String message) {
        if (message == null) {
            invokeLater(() -> getGUI().closeStatusPanel());
        } else {
            invokeLater(() -> getGUI().showStatusPanel(message));
        }
    }

    /**
     * Makes a new unit active if any, or focus on a tile (useful if the
     * current unit just died).
     *
     * Displays any new {@code ModelMessage}s with
     * {@link #nextModelMessage}.
     *
     * @param tile The {@code Tile} to select if no new unit can
     *     be made active.
     * @return True if the active unit changes.
     */
    private CompletableFuture<Boolean> updateActiveUnit(Tile tile) {
        // Make sure the active unit is done.
        final Player player = getMyPlayer();

        // Are we no longer in normal next unit mode?
        if (moveMode != MoveMode.NEXT_ACTIVE_UNIT) {
            // Clear the panel first
            // if (getGUI().isPanelShowing()) return false;
            // Flush any orders
            // if (!doExecuteGotoOrders()) return false;
            
            /*
             * Resetting moveMode to NEXT_ACTIVE_UNIT AND
             * returning false instead of calling
             * doExecuteGotoOrders -- since calling that method
             * will make it run in parallel at end-turn (giving
             * all sorts of errors).
             * 
             * Please test with the savegame in BR#3277 before
             * committing a change to this behaviour.
             */
           
            if (getGUI().getActiveUnit() != null) {
                moveMode = MoveMode.NEXT_ACTIVE_UNIT;
            }
            
            return CompletableFuture.completedFuture(false);
        }

        // Successfully found a unit to display
        if (player.hasNextActiveUnit()) {
            changeView(player.getNextActiveUnit(), false);
            return CompletableFuture.completedFuture(true);
        }

        // No active units left.  Do the goto orders.
        if (!await(doExecuteGotoOrders())) return CompletableFuture.completedFuture(true);

        // Disable active unit display, using fallback tile if supplied
        changeView(tile);

        // Check for automatic end of turn
        final ClientOptions options = getClientOptions();
        if (options.getBoolean(ClientOptions.AUTO_END_TURN)) {
            await(doEndTurn(options.getBoolean(ClientOptions.SHOW_END_TURN_DIALOG)));
        }
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Update the GUI and the active unit, with a fallback tile.
     *
     * @param tile An optional fallback {@code Tile}.
     * @param updateUnit An override setting which if true forces a new
     *     active unit to be selected (useful for the Wait command).
     */
    private void updateGUI(final Tile tile, boolean updateUnit) {
        displayModelMessages(false, false);
        
        /* This seems dubious, so I am just displaying the message and
         * continuing even when there are additional model messages.
         * 
         * There are multiple cases where we must update the GUI to
         * avoid errors -- for example when the unit has been removed.
         * 
        if (displayModelMessages(false, false)) {
            ; // If messages are displayed they probably refer to the
              // current unit, so do not update it.
        } else {
        */

        final GUI gui = getGUI();
        // Update the unit if asked to, or none present, or the
        // current one is out of moves (but not in Europe or newly
        // bought/trained units get immediately deselected), or has
        // been captured.
        Unit active = gui.getActiveUnit();
        final boolean update = updateUnit || active == null
                || (!active.couldMove() && !active.isInEurope())
                || !getMyPlayer().owns(active);
        // Tile is displayed if no new active unit is found,
        // which is useful when the last unit might have died
        invokeLater(() -> {
                if (update) updateActiveUnit(tile);
                gui.updateMapControls();
                gui.updateMenuBar();
            });
    }


    // Server access routines called from multiple places.

    /**
     * Ask the server to assign a trade route.
     *
     * @param unit The {@code Unit} to assign to.
     * @param tradeRoute The {@code TradeRoute} to assign.
     * @return True if the assignment succeeds.
     */
    private CompletableFuture<Boolean> askAssignTradeRoute(Unit unit, TradeRoute tradeRoute) {
        if (tradeRoute == unit.getTradeRoute()) return CompletableFuture.completedFuture(true);

        if (tradeRoute != null && unit.getTradeRoute() != null) {
            if (!await(getGUI().confirmClearTradeRoute(unit))) return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.completedFuture(await(askServer().assignTradeRoute(unit, tradeRoute))
            && unit.getTradeRoute() == tradeRoute);
    }

    /**
     * Claim a tile.
     *
     * @param player The {@code Player} that is claiming.
     * @param tile The {@code Tile} to claim.
     * @param claimant The {@code Unit} or {@code Colony} claiming.
     * @param price The price required.
     * @return True if the claim succeeded.
     */
    private CompletableFuture<Boolean> askClaimTile(Player player, Tile tile,
                                 FreeColGameObject claimant, int price) {
        final Player owner = tile.getOwner();
        if (price < 0) { // not for sale
            return CompletableFuture.completedFuture(false);
        } else if (price > 0) { // for sale
            ClaimAction act
                = await(getGUI().getClaimChoice(tile, player, price, owner));
            if (act == null) return CompletableFuture.completedFuture(false); // Cancelled
            switch (act) {
            case CLAIM_ACCEPT: // accepted price
                break;
            case CLAIM_STEAL:
                price = STEAL_LAND;
                break;
            default:
                logger.warning("Claim dialog fail: " + act);
                return CompletableFuture.completedFuture(false);
            }
        } // else price == 0 and we can just proceed to claim

        // Ask the server
        return CompletableFuture.completedFuture(await(askServer().claimTile(tile, claimant, price))
            && player.owns(tile));
    }

    /**
     * Clears the goto orders of the given unit by setting its destination
     * to null.
     *
     * @param unit The {@code Unit} to clear the destination for.
     * @return True if the unit now has no destination or trade route.
     */
    private CompletableFuture<Boolean> askClearGotoOrders(Unit unit) {
        if (!await(askAssignTradeRoute(unit, null))
            || !await(askSetDestination(unit, null))) return CompletableFuture.completedFuture(false);

        getGUI().clearGotoPath();
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Embark onto a carrier.
     *
     * @param unit The {@code Unit} to embark.
     * @param carrier The carrier {@code Unit} to board.
     * @return True if boarding succeeded.
     */
    private CompletableFuture<Boolean> askEmbark(Unit unit, Unit carrier) {
        ColonyWas colonyWas = (unit.getColony() != null)
            ? new ColonyWas(unit.getColony()) : null;
        EuropeWas europeWas = (unit.isInEurope())
            ? new EuropeWas(unit.getOwner().getEurope()) : null;
        UnitWas unitWas = new UnitWas(unit);
        if (await(askServer().embark(unit, carrier, null))
            && unit.getLocation() == carrier) {
            sound("sound.event.loadCargo");
            fireChanges(unitWas, colonyWas, europeWas);
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }
    
    /**
     * A unit in Europe emigrates.
     *
     * This is unusual for an ask* routine in that it uses a *Was
     * structure, but it is needed to extract the unit.
     *
     * @param europe The {@code Europe} where the unit appears.
     * @param slot The slot to choose, [0..RECRUIT_COUNT].
     * @return The new {@code Unit} or null on failure.
     */
    private CompletableFuture<Unit> askEmigrate(Europe europe, int slot) {
        if (europe == null
            || !MigrationType.validMigrantSlot(slot)) return null;

        EuropeWas europeWas = new EuropeWas(europe);
        Unit newUnit = null;
        if (await(askServer().emigrate(slot))
            && (newUnit = europeWas.getNewUnit()) != null) {
            fireChanges(europeWas);
        }
        return CompletableFuture.completedFuture(newUnit);
    }

    /**
     * Select all the units to emigrate from Europe.  If they are all
     * the same they can be picked automatically, but otherwise use
     * the emigration dialog.  Only to be called if the player is
     * allowed to select the unit type (i.e. FoY or has Brewster).
     *
     * The server contains the count of available FoY-units, and
     * maintains the immigration/immigrationRequired amounts, so this
     * routine will fail harmlessly if it asks for too much.
     *
     * @param player The {@code Player} that owns the unit.
     * @param n The number of units known to be eligible to emigrate.
     * @param fountainOfYouth True if this migration if due to a FoY.
     */
    private CompletableFuture<Void> emigration(Player player, int n, boolean fountainOfYouth) {
        final Europe europe = player.getEurope();
        if (europe == null) return CompletableFuture.completedFuture(null);

        for (; n > 0 || player.checkEmigrate() ; n--) {
            if (!allSame(europe.getExpandedRecruitables(false))) {
                showEmigrationDialog(player, fountainOfYouth, n);
                return CompletableFuture.completedFuture(null);
            }
            Unit u = await(askEmigrate(europe, Europe.MigrationType.getDefaultSlot()));
            if (u == null) break; // Give up on failure, try again next turn
            player.addModelMessage(player.getEmigrationMessage(u));
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Load some goods onto a carrier.
     *
     * @param loc The {@code Location} to load from.
     * @param type The {@code GoodsType} to load.
     * @param amount The amount of goods to load.
     * @param carrier The {@code Unit} to load onto.
     * @return True if the load succeeded.
     */
    private CompletableFuture<Boolean> askLoadGoods(Location loc, GoodsType type, int amount,
                                 Unit carrier) {
        TradeLocation trl = carrier.getTradeLocation();
        if (trl == null) return CompletableFuture.completedFuture(false);

        // Size check, if there are spare holds they can be filled, but...
        int loadable = carrier.getLoadableAmount(type);
        if (amount > loadable) amount = loadable;

        final Player player = carrier.getOwner();

        if (carrier.isInEurope()) {
            // Are the goods boycotted?
            if (!player.canTrade(type)) return CompletableFuture.completedFuture(false);

            // Check that the purchase is funded.
            if (!player.checkGold(player.getMarket().getBidPrice(type, amount))) {
                showInformationPanel(null, "info.notEnoughGold");
                return CompletableFuture.completedFuture(false);
            }
        }

        // Try to purchase.
        int oldAmount = carrier.getGoodsContainer().getGoodsCount(type);
        if (await(askServer().loadGoods(loc, type, amount, carrier))
            && carrier.getGoodsContainer().getGoodsCount(type) != oldAmount) {
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Set a destination for a unit.
     *
     * @param unit The {@code Unit} to direct.
     * @param destination The destination {@code Location}.
     * @return True if the destination was set.
     */
    private CompletableFuture<Boolean> askSetDestination(Unit unit, Location destination) {
        if (unit.getDestination() == destination) return CompletableFuture.completedFuture(true);

        return CompletableFuture.completedFuture(await(askServer().setDestination(unit, destination))
            && unit.getDestination() == destination);
    }

    /**
     * Unload some goods from a carrier.
     *
     * @param type The {@code GoodsType} to unload.
     * @param amount The amount of goods to unload.
     * @param carrier The {@code Unit} carrying the goods.
     * @return True if the unload succeeded.
     */
    private CompletableFuture<Boolean> askUnloadGoods(GoodsType type, int amount, Unit carrier) {
        // Do not check for trade location, unloading can include dumping
        // which can happen anywhere
        final Player player = getMyPlayer();

        int oldAmount = carrier.getGoodsContainer().getGoodsCount(type);
        if (await(askServer().unloadGoods(type, amount, carrier))
            && carrier.getGoodsContainer().getGoodsCount(type) != oldAmount) {
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }


    // Utilities connected with saving the game

    /**
     * Get the trunk of the save game string.
     *
     * @param game The {@code Game} to query.
     * @return The trunk of the file name to use for saved games.
     */
    private String getSaveGameString(Game game) {
        final Player player = getMyPlayer();
        final String gid = Integer.toHexString(game.getUUID().hashCode());
        final Turn turn = game.getTurn();
        return (/* player.getName() + "_" */ gid
            + "_" + Messages.message(player.getNationLabel())
            + "_" + turn.getSaveGameSuffix()
            + "." + FreeCol.FREECOL_SAVE_EXTENSION)
            .replaceAll(" ", "_");
    }

    /**
     * Creates at least one autosave game file of the currently played
     * game in the autosave directory.  Does nothing if there is no
     * game running.
     */
    private void autoSaveGame () {
        final Game game = getGame();
        if (game == null) return;

        // unconditional save per round (fixed file "last-turn")
        final ClientOptions options = getClientOptions();
        final String prefix = options.getText(ClientOptions.AUTO_SAVE_PREFIX);
        final String lastTurnName = prefix + "-"
            + options.getText(ClientOptions.LAST_TURN_NAME)
            + "." + FreeCol.FREECOL_SAVE_EXTENSION;
        final String beforeLastTurnName = prefix + "-"
            + options.getText(ClientOptions.BEFORE_LAST_TURN_NAME)
            + "." + FreeCol.FREECOL_SAVE_EXTENSION;
       
        File lastTurnFile = FreeColDirectories.getAutosaveFile(lastTurnName);
        File beforeLastTurnFile
            = FreeColDirectories.getAutosaveFile(beforeLastTurnName);
        // if "last-turn" file exists, shift it to "before-last-turn" file
        if (lastTurnFile != null) {
            if (lastTurnFile.exists()) {
                if (beforeLastTurnFile.exists()) deleteFile(beforeLastTurnFile);
                try {
                    if (!lastTurnFile.renameTo(beforeLastTurnFile)) {
                        logger.warning("Could not rename: "
                            + lastTurnFile.getPath());
                    }
                } catch (NullPointerException|SecurityException ex) {
                    logger.log(Level.WARNING, "Could not rename: "
                        + lastTurnFile.getPath(), ex);
                }
            }
            saveGame(lastTurnFile);
        }

        // conditional save after user-set period
        int saveGamePeriod = options.getInteger(ClientOptions.AUTOSAVE_PERIOD);
        int turnNumber = game.getTurn().getNumber();
        if (saveGamePeriod >= 1 && turnNumber % saveGamePeriod == 0) {
            String fileName = prefix + "-" + getSaveGameString(game);
            saveGame(FreeColDirectories.getAutosaveFile(fileName));
        }
    }

    /**
     * Saves the game to the given file.
     *
     * @param file The {@code File}.
     * @return True if the game was saved.
     */
    private boolean saveGame(final File file) {
        if (file == null) return false;
        final FreeColServer server = getFreeColServer();
        boolean result = false;
        if (server != null) {
            showStatusPanel(Messages.message("status.savingGame"));
            try {
                server.saveGame(file, getClientOptions(), getGUI().getActiveUnit());
                result = true;
            } catch (IOException ioe) {
                error(FreeCol.badFile("error.couldNotSave", file), null);
                logger.log(Level.WARNING, "Save fail", ioe);
            } finally {
                showStatusPanel(null);
            }
        }
        return result;
    }


    // Utilities for message handling.

    /**
     * Start ignoring a kind of message.
     *
     * @param key The key for a message to ignore.
     * @param turn The current {@code Turn}.
     */
    private void startIgnoringMessage(String key, Turn turn) {
        messagesToIgnore.put(key, turn.getNumber());
        logger.finer("Ignore message start: " + key);
    }

    /**
     * Stop ignoring a kind of message.
     *
     * @param key The key for a message to stop ignoring.
     */
    private void stopIgnoringMessage(String key) {
        messagesToIgnore.remove(key);
        logger.finer("Ignore message stop: " + key);
    }

    /**
     * Reap all ignored message keys that are older than the given turn.
     *
     * @param turn The {@code Turn} value to test against.
     */
    private void reapIgnoredMessages(Turn turn) {
        removeInPlace(messagesToIgnore, e -> e.getValue() < turn.getNumber());
    }

    /**
     * See if messages with a given key were ignored last turn.  If so,
     * continue to ignore them.
     *
     * @param key The key to check.
     * @param turn The current {@code Turn}.
     * @return True if the message should continue to be ignored.
     */
    private boolean continueIgnoreMessage(String key, Turn turn) {
        Integer value = -1;
        boolean ret = key != null
            && (value = messagesToIgnore.get(key)) != null
            && value + 1 == turn.getNumber();
        if (ret) messagesToIgnore.put(key, value + 1);
        return ret;
    }

    /**
     * Displays the messages in the current turn report.
     */
    public void displayTurnReportMessages() {
        getGUI().showReportTurnPanel(turnReportMessages);
    }

    /**
     * Displays pending {@code ModelMessage}s.
     *
     * @param allMessages Display all messages or just the undisplayed ones.
     * @param endOfTurn Use a turn report panel if necessary.
     * @return True if any messages were displayed.
     */
    private boolean displayModelMessages(final boolean allMessages,
                                         final boolean endOfTurn) {
        final ClientOptions co = getClientOptions();
        final Player player = getMyPlayer();
        final Turn thisTurn = getGame().getTurn();
        final List<ModelMessage> messages = new ArrayList<>();
        List<ModelMessage> todo = (allMessages) ? player.getModelMessages()
            : player.getNewModelMessages();

        for (ModelMessage m : todo) {
            final String key = m.getOptionName();
            try {
                if ((key == null || co.getBoolean(key))
                    && !continueIgnoreMessage(m.getIgnoredMessageKey(), thisTurn)) {
                    messages.add(m);
                }
            } catch (RuntimeException rte) {
                logger.warning("Bogus ModelMessage with key<" + key
                    + ">: " + m);
            }
            m.setDisplayed(true);
        }

        reapIgnoredMessages(thisTurn);

        if (!messages.isEmpty()) {
            Runnable uiTask;
            if (endOfTurn) {
                turnReportMessages.addAll(messages);
                uiTask = () -> { displayTurnReportMessages(); };
            } else {
                uiTask = () -> { getGUI().showModelMessages(messages); };
            }
            getGUI().invokeNowOrWait(uiTask);
        }
        return !messages.isEmpty();
    }

    /**
     * Displays the next {@code ModelMessage}.
     *
     * Called from CC.reconnect, CargoPanel,
     * ColonyPanel.closeColonyPanel, EuropePanel.exitAction,
     * EuropePanel.MarketPanel
     *
     * @return True if any messages were displayed.
     */
    public boolean nextModelMessage() {
        return displayModelMessages(false, false);
    }


    // Utilities to handle the transitions between the active-unit,
    // execute-orders and end-turn states.

    /**
     * Do the goto orders operation.
     *
     * @return True if all goto orders have been performed and no units
     *     reached their destination and are free to move again.
     */
    private CompletableFuture<Boolean> doExecuteGotoOrders() {
        final Player player = getMyPlayer();
        Unit active = getGUI().getActiveUnit();
        boolean ret = true;

        // Ensure the goto mode sticks.
        moveMode = moveMode.maximize(MoveMode.EXECUTE_GOTO_ORDERS);

        // Deal with the trade route units first.
        List<ModelMessage> messages = new ArrayList<>();
        final Predicate<Unit> tradePred = u ->
            u.isReadyToTrade() && player.owns(u);
        for (Unit unit : transform(player.getUnits(), tradePred,
                                   Function.<Unit>identity(),
                                   tradeRouteUnitComparator)) {
            changeView(unit, false);
            if (!await(moveToDestination(unit, messages))) {
                ret = false;
                break;
            }
        }
        if (!messages.isEmpty()) {
            turnReportMessages.addAll(messages);
            for (ModelMessage m : messages) player.addModelMessage(m);
            nextModelMessage();
            ret = false;
        }
        if (!ret) return CompletableFuture.completedFuture(false);

        // Wait for user to close outstanding panels.
        if (getGUI().isPanelShowing()) return CompletableFuture.completedFuture(false);

        // The active unit might also be a going-to unit.  Make sure it
        // gets processed first.  setNextGoingToUnit will fail harmlessly
        // if it is not a going-to unit so this is safe.
        if (active != null) player.setNextGoingToUnit(active);

        // Process all units.
        while (player.hasNextGoingToUnit()) {
            Unit unit = player.getNextGoingToUnit();
            changeView(unit, false);
            // Move the unit as much as possible
            if (!await(moveToDestination(unit, null))) {
                ret = false;
                break;
            }
            // This was the active unit, but we are confident it can not
            // do anything else useful, so do not reselect it below
            if (active == unit) active = null;
        }
        nextModelMessage(); // Might have LCR messages to display
        if (ret) { // If no unit issues, restore previously active unit 
            changeView(active, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * End the turn.
     *
     * @param showDialog Show the end turn dialog?
     */
    private CompletableFuture<Void> doEndTurn(boolean showDialog) {
        final Player player = getMyPlayer();
        // Clear any panels first
        if (getGUI().isPanelShowing()) return CompletableFuture.completedFuture(null);
        
        if (showDialog) {
            List<Unit> units = transform(player.getUnits(), Unit::couldMove);
            if (!units.isEmpty()) {
                // Modal dialog takes over
                getGUI().showEndTurnDialog(units).thenAccept(
                    (Boolean value) -> {
                        if (value != null && value) {
                            endTurn(false);
                        }
                    });
                return CompletableFuture.completedFuture(null);
            }
        }

        // Ensure end-turn mode sticks.
        moveMode = moveMode.maximize(MoveMode.END_TURN);

        changeView(null);
        final List<Unit> units = transform(player.getUnits(), Unit::couldMove);
        for (final Unit unit : units) {
            if (unit.getState() != Unit.UnitState.SKIPPED) {
                await(changeState(unit, Unit.UnitState.SKIPPED));
            }
        }
        
        // Make sure all goto orders are complete before ending turn, and
        // that nothing (like a LCR exploration) has cancelled the end turn.
        if (!await(doExecuteGotoOrders())
            || moveMode.ordinal() < MoveMode.END_TURN.ordinal()) return CompletableFuture.completedFuture(null);

        // Check for desync as last thing!
        if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.DESYNC)
            && DebugUtils.checkDesyncAction(getFreeColClient())) {
            logger.warning("Reconnecting on desync");
            await(getFreeColClient().getConnectController()
                .requestLogout(LogoutReason.RECONNECT));
            return CompletableFuture.completedFuture(null);
        }
        
        // Clean up lingering menus.
        getGUI().closeMenus();

        // Restart the selection cycle.
        moveMode = MoveMode.NEXT_ACTIVE_UNIT;

        // Clear outdated turn report messages.
        turnReportMessages.clear();

        // Inform the server of end of turn.
        await(askServer().endTurn());
        return CompletableFuture.completedFuture(null);
    }


    // Movement support.

    /**
     * Moves the given unit towards its destination/s if possible.
     *
     * @param unit The {@code Unit} to move.
     * @param messages An optional list in which to retain any
     *     trade route {@code ModelMessage}s generated.
     * @return True if all is well with the unit, false if the unit
     *     should be selected and examined by the user.
     */
    private CompletableFuture<Boolean> moveToDestination(Unit unit, List<ModelMessage> messages) {
        final Player player = getMyPlayer();
        Location destination = unit.getDestination();
        PathNode path;
        boolean ret;
        if (!requireOurTurn()
            || unit.isAtSea()
            || unit.getMovesLeft() <= 0
            || unit.getState() == UnitState.SKIPPED) {
            ret = true; // invalid, should not be here
        } else if (unit.getTradeRoute() != null) {
            ret = await(followTradeRoute(unit, messages));
        } else if (destination == null) {
            ret = true; // also invalid, but trade route check needed first
        } else if (!await(changeState(unit, UnitState.ACTIVE))) {
            ret = true; // another error case
        } else if ((path = unit.findPath(destination)) == null) {
            // No path to destination. Give the player a chance to do
            // something about it, but default to skipping this unit as
            // the path blockage is most likely just transient
            StringTemplate src = unit.getLocation()
                .getLocationLabelFor(player);
            StringTemplate dst = destination.getLocationLabelFor(player);
            StringTemplate template = StringTemplate
                .template("info.moveToDestinationFailed")
                .addStringTemplate("%unit%",
                    unit.getLabel(Unit.UnitLabelType.NATIONAL))
                .addStringTemplate("%location%", src)
                .addStringTemplate("%destination%", dst);
            showInformationPanel(unit, template);
            await(changeState(unit, UnitState.SKIPPED));
            ret = false;
        } else if (!await(movePath(unit, path))) {
            ret = false; // ask the player to resolve the movePath problem
        } else if (unit.isAtLocation(destination)) {
            final Colony colony = (unit.hasTile()) ? unit.getTile().getColony()
                : null;
            // Clear ordinary destinations if arrived.
            if (!await(askClearGotoOrders(unit))) {
                ret = false; // Should not happen.  Desync?  Ask the user.
            } else if (colony != null) {
                // Always ask to be selected if arriving at a colony
                // unless the unit cashed in (and thus gone), and bring
                // up the colony panel so something can be done with the
                // unit
                if (await(checkCashInTreasureTrain(unit))) {
                    ret = true;
                } else {
                    showColonyPanelWithCarrier(colony, unit);
                    ret = false;
                }
            } else {
                // If the unit has moves left, select it
                ret = unit.getMovesLeft() == 0;
            }
        } else { // Still in transit, do not select         
            ret = true;
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Follow a path.
     *
     * @param unit The {@code Unit} to move.
     * @param path The path to follow.
     * @return True if automatic movement of the unit can proceed.
     */
    private CompletableFuture<Boolean> movePath(Unit unit, PathNode path) {
        for (; path != null; path = path.next) {
            if (unit.isAtLocation(path.getLocation())) continue;

            if (path.getLocation() instanceof Europe) {
                if (unit.hasTile()
                    && unit.getTile().isDirectlyHighSeasConnected()) {
                    return moveTowardEurope(unit, (Europe)path.getLocation());
                }
                logger.warning("Can not move to Europe from "
                    + unit.getLocation()
                    + " on path: " + path.fullPathToString());
                return CompletableFuture.completedFuture(false);

            } else if (path.getLocation() instanceof Tile) {
                if (path.getDirection() == null) {
                    if (unit.isInEurope()) {
                        return moveAwayFromEurope(unit, unit.getGame().getMap());
                    }
                    logger.warning("Null direction on path: "
                        + path.fullPathToString());
                    return CompletableFuture.completedFuture(false);
                }
                if (!await(moveDirection(unit, path.getDirection(), false))) {
                    // Lack of moves is an expected non-failure condition
                    return CompletableFuture.completedFuture(unit.getMoveType(path.getDirection())
                        == Unit.MoveType.MOVE_NO_MOVES);
                }

            } else if (path.getLocation() instanceof Unit) {
                return moveEmbark(unit, path.getDirection());

            } else {
                logger.warning("Bad path: " + path.fullPathToString());
                return CompletableFuture.completedFuture(false);
            }
        }
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Move a unit in a given direction.
     *
     * Public for the test suite.
     *
     * @param unit The {@code Unit} to move.
     * @param direction The {@code Direction} to move in.
     * @param interactive Interactive mode: play sounds and emit errors.
     * @return True if automatic movement of the unit can proceed.
     */
    public CompletableFuture<Boolean> moveDirection(Unit unit, Direction direction,
                                 boolean interactive) {
        // Is the unit on the brink of reaching the destination with
        // this move?
        final Location destination = unit.getDestination();
        final Tile oldTile = unit.getTile();
        boolean destinationImminent = destination != null
            && oldTile != null
            && Map.isSameLocation(oldTile.getNeighbourOrNull(direction),
                                  destination);

        // Consider all the move types.
        final Unit.MoveType mt = unit.getMoveType(direction);
        CompletableFuture<Boolean> result = CompletableFuture.completedFuture(mt.isLegal());
        switch (mt) {
        case MOVE_HIGH_SEAS:
            // If the destination is Europe (and valid) move there,
            // if the destination is null, ask what to do,
            // otherwise just move on the map.
            result = (destination instanceof Europe
                      && getMyPlayer().getEurope() != null)
                ? moveTowardEurope(unit, (Europe)destination)
                : (destination == null)
                ? moveHighSeas(unit, direction)
                : moveTile(unit, direction);
            break;
        case MOVE:
            result = moveTile(unit, direction);
            break;
        case EXPLORE_LOST_CITY_RUMOUR:
            result = moveExplore(unit, direction);
            break;
        case ATTACK_UNIT:
            result = moveAttack(unit, direction);
            break;
        case ATTACK_SETTLEMENT:
            result = moveAttackSettlement(unit, direction);
            break;
        case EMBARK:
            result = moveEmbark(unit, direction);
            break;
        case ENTER_INDIAN_SETTLEMENT_WITH_FREE_COLONIST:
            result = moveLearnSkill(unit, direction);
            break;
        case ENTER_INDIAN_SETTLEMENT_WITH_SCOUT:
            result = moveScoutIndianSettlement(unit, direction);
            break;
        case ENTER_INDIAN_SETTLEMENT_WITH_MISSIONARY:
            result = moveUseMissionary(unit, direction);
            break;
        case ENTER_FOREIGN_COLONY_WITH_SCOUT:
            result = moveScoutColony(unit, direction);
            break;
        case ENTER_SETTLEMENT_WITH_CARRIER_AND_GOODS:
            result = moveTrade(unit, direction);
            break;

        // Illegal moves
        case MOVE_NO_ACCESS_BEACHED:
            if (interactive || destinationImminent) {
                sound("sound.event.illegalMove");
                StringTemplate nation = getNationAt(unit.getTile(), direction);
                showInformationPanel(unit, StringTemplate
                    .template("move.noAccessBeached")
                    .addStringTemplate("%nation%", nation));
            }
            break;
        case MOVE_NO_ACCESS_CONTACT:
            if (interactive || destinationImminent) {
                sound("sound.event.illegalMove");
                StringTemplate nation = getNationAt(unit.getTile(), direction);
                showInformationPanel(unit, StringTemplate
                    .template("move.noAccessContact")
                    .addStringTemplate("%nation%", nation));
            }
            break;
        case MOVE_NO_ACCESS_GOODS:
            if (interactive || destinationImminent) {
                sound("sound.event.illegalMove");
                StringTemplate nation = getNationAt(unit.getTile(), direction);
                showInformationPanel(unit, StringTemplate
                    .template("move.noAccessGoods")
                    .addStringTemplate("%nation%", nation)
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL)));
            }
            break;
        case MOVE_NO_ACCESS_LAND:
            if (!await(moveDisembark(unit, direction))) {
                if (interactive) {
                    sound("sound.event.illegalMove");
                }
            }
            break;
        case MOVE_NO_ACCESS_MISSION_BAN:
            if (interactive || destinationImminent) {
                sound("sound.event.illegalMove");
                StringTemplate nation = getNationAt(unit.getTile(), direction);
                showInformationPanel(unit, StringTemplate
                    .template("move.noAccessMissionBan")
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL))
                    .addStringTemplate("%nation%", nation));
            }
            break;
        case MOVE_NO_ACCESS_SETTLEMENT:
            if (interactive || destinationImminent) {
                sound("sound.event.illegalMove");
                StringTemplate nation = getNationAt(unit.getTile(), direction);
                showInformationPanel(unit, StringTemplate
                    .template("move.noAccessSettlement")
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL))
                    .addStringTemplate("%nation%", nation));
            }
            break;
        case MOVE_NO_ACCESS_SKILL:
            if (interactive || destinationImminent) {
                sound("sound.event.illegalMove");
                showInformationPanel(unit, StringTemplate
                    .template("move.noAccessSkill")
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL)));
            }
            break;
        case MOVE_NO_ACCESS_TRADE:
            if (interactive || destinationImminent) {
                sound("sound.event.illegalMove");
                StringTemplate nation = getNationAt(unit.getTile(), direction);
                showInformationPanel(unit, StringTemplate
                    .template("move.noAccessTrade")
                    .addStringTemplate("%nation%", nation));
            }
            break;
        case MOVE_NO_ACCESS_WAR:
            if (interactive || destinationImminent) {
                sound("sound.event.illegalMove");
                StringTemplate nation = getNationAt(unit.getTile(), direction);
                showInformationPanel(unit, StringTemplate
                    .template("move.noAccessWar")
                    .addStringTemplate("%nation%", nation));
            }
            break;
        case MOVE_NO_ACCESS_WATER:
            if (interactive || destinationImminent) {
                sound("sound.event.illegalMove");
                showInformationPanel(unit, StringTemplate
                    .template("move.noAccessWater")
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL)));
            }
            break;
        case MOVE_NO_ATTACK_MARINE:
            if (interactive || destinationImminent) {
                sound("sound.event.illegalMove");
                showInformationPanel(unit, StringTemplate
                    .template("move.noAttackWater")
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL)));
            }
            break;
        case MOVE_NO_MOVES:
            // The unit may have some moves left, but not enough
            // to move to the next node.  The move is illegal
            // this turn, but might not be next turn, so do not cancel the
            // destination but set the state to skipped instead.
            destinationImminent = false;
            await(changeState(unit, UnitState.SKIPPED));
            break;
        case MOVE_NO_TILE:
            if (interactive || destinationImminent) {
                sound("sound.event.illegalMove");
                showInformationPanel(unit, StringTemplate
                    .template("move.noTile")
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL)));
            }
            break;
        default:
            if (interactive || destinationImminent) {
                sound("sound.event.illegalMove");
            }
            result = CompletableFuture.completedFuture(false);
            break;
        }
        if (destinationImminent && !unit.isDisposed()) {
            // The unit either reached the destination or failed at
            // the last step for some reason.  In either case, clear
            // the goto orders because they have failed.
            if (!await(askClearGotoOrders(unit))) result = CompletableFuture.completedFuture(false);
        }
        // Force redisplay of unit information
        if (unit == getGUI().getActiveUnit()) {
            /*
             * The unit might have been disposed as a result of the move
             * when we get here. For example after vanishing when exploring
             * a lost city rumour.
             */
            changeView(unit, true);
        }

        return result;
    }

    /**
     * Move a unit from off map to an on map location.
     *
     * @param unit The {@code Unit} to be moved.
     * @param destination The {@code Location} to be moved to.
     * @return True if automatic movement of the unit can proceed (never).
     */
    private CompletableFuture<Boolean> moveAwayFromEurope(Unit unit, Location destination) {
        // Autoload emigrants.
        List<Unit> ul;
        if (getClientOptions().getBoolean(ClientOptions.AUTOLOAD_EMIGRANTS)
            && unit.isInEurope()
            && !(ul = transform(unit.getOwner().getEurope().getUnits(),
                                Unit.sentryPred)).isEmpty()) {
            // Can still proceed even if moves consumed
            await(moveAutoload(unit, ul));
        }

        EuropeWas europeWas = (!unit.isInEurope()) ? null
            : new EuropeWas(unit.getOwner().getEurope());
        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askServer().moveTo(unit, destination));
        if (ret) {
            fireChanges(unitWas, europeWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Move a unit from on map towards Europe.
     *
     * @param unit The {@code Unit} to be moved.
     * @param europe The {@code Europe} to be moved to.
     * @return True if automatic movement of the unit can proceed (never).
     */
    private CompletableFuture<Boolean> moveTowardEurope(Unit unit, Europe europe) {
        UnitWas unitWas = new UnitWas(unit);
        if (await(askServer().moveTo(unit, europe))) {
            fireChanges(unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Confirm attack or demand a tribute from a native settlement, following
     * an attacking move.
     *
     * @param unit The {@code Unit} to perform the attack.
     * @param direction The direction in which to attack.
     * @return True if automatic movement of the unit can proceed (never).
     */
    private CompletableFuture<Boolean> moveAttack(Unit unit, Direction direction) {
        final Tile tile = unit.getTile();
        final Tile target = tile.getNeighbourOrNull(direction);
        final Unit u = target.getFirstUnit();
        if (u == null || unit.getOwner().owns(u)) return CompletableFuture.completedFuture(false);

        if (await(askClearGotoOrders(unit))
            && await(getGUI().confirmHostileAction(unit, target))
            && await(getGUI().confirmPreCombat(unit, target))) {
            await(askServer().attack(unit, direction));
            // Immediately display resulting message, allowing
            // next updateGUI to select another unit.
            nextModelMessage();
        }
        // Always return false, as the unit has either attacked and lost
        // its remaining moves, or the move can not proceed because it is
        // blocked.
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Confirm attack or demand a tribute from a settlement, following
     * an attacking move.
     *
     * @param unit The {@code Unit} to perform the attack.
     * @param direction The direction in which to attack.
     * @return True if automatic movement of the unit can proceed (never).
     */
    private CompletableFuture<Boolean> moveAttackSettlement(Unit unit, Direction direction) {
        final Tile tile = unit.getTile();
        final Tile target = tile.getNeighbourOrNull(direction);
        final Settlement settlement = target.getSettlement();
        if (settlement == null
            || unit.getOwner().owns(settlement)) return CompletableFuture.completedFuture(false);

        ArmedUnitSettlementAction act
            = await(getGUI().getArmedUnitSettlementChoice(settlement));
        if (act == null) return CompletableFuture.completedFuture(false); // Cancelled
        switch (act) {
        case SETTLEMENT_ATTACK:
            if (await(getGUI().confirmHostileAction(unit, target))
                && await(getGUI().confirmPreCombat(unit, target))) {
                await(askServer().attack(unit, direction));
                Colony col = target.getColony();
                if (col != null && unit.getOwner().owns(col)) {
                    showColonyPanel(col, unit);
                }
                // Immediately display resulting message, allowing
                // next updateGUI to select another unit.
                nextModelMessage();
            }
            break;

        case SETTLEMENT_TRIBUTE:
            int amount = (settlement instanceof Colony)
                ? await(getGUI().confirmEuropeanTribute(unit, (Colony)settlement,
                    await(nationSummary(settlement.getOwner()))))
                : (settlement instanceof IndianSettlement)
                ? await(getGUI().confirmNativeTribute(unit, (IndianSettlement)settlement))
                : -1;
            if (amount > 0) return moveTribute(unit, amount, direction);
            break;
            
        default:
            logger.warning("showArmedUnitSettlementDialog fail: " + act);
            break;
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Primitive to handle autoloading of a list of units onto a carrier.
     *
     * @param carrier The carrier {@code Unit} to load onto.
     * @param embark A list of {@code Unit}s to load.
     * @return True if automatic movement of the carrier can proceed.
     */
    private CompletableFuture<Boolean> moveAutoload(Unit carrier, List<Unit> embark) {
        boolean update = false;
        for (Unit u : embark) {
            if (!carrier.couldCarry(u)) continue;
            update |= await(askEmbark(u, carrier));
            if (u.getLocation() != carrier) {
                await(changeState(u, UnitState.SKIPPED));
            }
        }
        if (update) updateGUI(null, false);
        // Boarding might have consumed the carrier moves.
        return CompletableFuture.completedFuture(carrier.couldMove());
    }

    /**
     * Initiates diplomacy with a foreign power.
     *
     * @param unit The {@code Unit} negotiating.
     * @param direction The direction of a settlement to negotiate with.
     * @param dt The base {@code DiplomaticTrade} agreement to
     *     begin the negotiation with.
     * @return True if automatic movement of the unit can proceed (never).
     */
    private CompletableFuture<Boolean> moveDiplomacy(Unit unit, Direction direction,
                                  DiplomaticTrade dt) {
        Settlement settlement = getSettlementAt(unit.getTile(), direction);
        if (settlement instanceof Colony) {
            final Player player = unit.getOwner();
            final Colony colony = (Colony)settlement;
            final Player other = colony.getOwner();
            // Can not negotiate with the REF!
            if (other == player.getREFPlayer()) return CompletableFuture.completedFuture(false);
            await(askServer().diplomacy(unit, colony, dt));
            // invalidate nation summaries in case colonies changed hands
            player.clearNationSummary(player);
            player.clearNationSummary(other);
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Check the carrier for passengers to disembark, possibly
     * snatching a useful result from the jaws of a
     * MOVE_NO_ACCESS_LAND failure.
     *
     * @param unit The carrier containing the unit to disembark.
     * @param direction The direction in which to disembark the unit.
     * @return True if the disembark "succeeds" (which deliberately includes
     *     declined disembarks).
     */
    private CompletableFuture<Boolean> moveDisembark(Unit unit, final Direction direction) {
        final Tile tile = unit.getTile().getNeighbourOrNull(direction);
        if (tile.getFirstUnit() != null
            && tile.getFirstUnit().getOwner() != unit.getOwner()) {
            return CompletableFuture.completedFuture(false); // Can not disembark onto other nation units.
        }

        // Disembark selected units able to move.
        final List<Unit> disembarkable
            = transform(unit.getUnits(),
                        u -> u.getMoveType(tile).isProgress());
        if (disembarkable.isEmpty()) return CompletableFuture.completedFuture(false); // Fail, did not find one
        for (Unit u : disembarkable) await(changeState(u, UnitState.ACTIVE));
        if (disembarkable.size() == 1) {
            if (await(getGUI().confirm(tile, StringTemplate.key("disembark.text"),
                                 disembarkable.get(0), "ok", "cancel"))) {
                await(moveDirection(disembarkable.get(0), direction, false));
            }
        } else {
            List<ChoiceItem<Unit>> choices
                = transform(disembarkable, alwaysTrue(), u ->
                    new ChoiceItem<Unit>(u.getDescription(Unit.UnitLabelType.NATIONAL), u));
            choices.add(new ChoiceItem<>(Messages.message("all"), unit));

            // Use moveDirection() to disembark units as while the
            // destination tile is known to be clear of other player
            // units or settlements, it may have a rumour or need
            // other special handling.
            Unit u = await(getGUI().getChoice(unit.getTile(),
                                        StringTemplate.key("disembark.text"),
                                        unit, "none", choices));
            if (u == null) {
                // Cancelled, done.
            } else if (u == unit) {
                // Disembark all.
                for (Unit dUnit : disembarkable) {
                    await(moveDirection(dUnit, direction, false));
                }
            } else {
                await(moveDirection(u, direction, false));
            }
        }
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Embarks the specified unit onto a carrier in a specified direction
     * following a move of MoveType.EMBARK.
     *
     * @param unit The {@code Unit} that wishes to embark.
     * @param direction The direction in which to embark.
     * @return True if automatic movement of the unit can proceed (never).
     */
    private CompletableFuture<Boolean> moveEmbark(Unit unit, Direction direction) {
        if (unit.getColony() != null
            && !await(getGUI().confirmLeaveColony(unit))) return CompletableFuture.completedFuture(false);

        final Tile sourceTile = unit.getTile();
        final Tile destinationTile = sourceTile.getNeighbourOrNull(direction);
        Unit carrier = null;
        List<ChoiceItem<Unit>> choices
            = transform(destinationTile.getUnits(),
                        u -> u.canAdd(unit),
                        u -> new ChoiceItem<>(u.getDescription(Unit.UnitLabelType.NATIONAL), u));
        if (choices.isEmpty()) {
            throw new RuntimeException("Unit " + unit.getId()
                + " found no carrier to embark upon.");
        } else if (choices.size() == 1) {
            carrier = choices.get(0).getObject();
        } else {
            carrier = await(getGUI().getChoice(unit.getTile(),
                                         StringTemplate.key("embark.text"),
                                         unit, "none", choices));
            if (carrier == null) return CompletableFuture.completedFuture(false); // User cancelled
        }

        // Proceed to embark, skip if it did not work.
        if (await(askClearGotoOrders(unit))
            && await(askServer().embark(unit, carrier, direction))
            && unit.getLocation() == carrier) {
            unit.getOwner().invalidateCanSeeTiles();
        } else {
            await(changeState(unit, UnitState.SKIPPED));
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Confirm exploration of a lost city rumour, following a move of
     * MoveType.EXPLORE_LOST_CITY_RUMOUR.
     *
     * @param unit The {@code Unit} that is exploring.
     * @param direction The direction of a rumour.
     * @return True if automatic movement of the unit can proceed (never).
     */
    private CompletableFuture<Boolean> moveExplore(Unit unit, Direction direction) {
        // Confirm the move.
        final Tile now = unit.getTile();
        final Tile tile = now.getNeighbourOrNull(direction);
        if (!await(getGUI().confirm(now,
                StringTemplate.key("exploreLostCityRumour.text"), unit,
                "exploreLostCityRumour.yes", "exploreLostCityRumour.no"))) {
            if (unit.getDestination() != null) askClearGotoOrders(unit);
            return CompletableFuture.completedFuture(false);
        }

        // Handle the mounds decision.
        if (tile.getLostCityRumour().getType() == LostCityRumour.RumourType.MOUNDS
            && !await(getGUI().confirm(now,
                StringTemplate.key("exploreMoundsRumour.text"), unit,
                "exploreLostCityRumour.yes", "exploreLostCityRumour.no"))) {
            await(askServer().declineMounds(unit, direction)); // LCR goes away
            return CompletableFuture.completedFuture(false);
        }

        // Always stop automatic movement as exploration always does something.
        await(moveTile(unit, direction));
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Moves a unit onto the "high seas" in a specified direction following
     * a move of MoveType.MOVE_HIGH_SEAS.
     * This may result in a move to Europe, no move, or an ordinary move.
     *
     * @param unit The {@code Unit} to be moved.
     * @param direction The direction in which to move.
     * @return True if automatic movement of the unit can proceed.
     */
    private CompletableFuture<Boolean> moveHighSeas(Unit unit, Direction direction) {
        // Confirm moving to Europe if told to move to a null tile
        // (FIXME: can this still happen?), or if crossing the boundary
        // between coastal and high sea.  Otherwise just move.
        final Tile oldTile = unit.getTile();
        final Tile newTile = oldTile.getNeighbourOrNull(direction);
        if (newTile == null
            || (!oldTile.isDirectlyHighSeasConnected()
                && newTile.isDirectlyHighSeasConnected())) {
            TradeRouteStop stop;
            if (unit.getTradeRoute() != null
                && (stop = unit.getStop()) != null
                && TradeRoute.isStopValid(unit, stop)
                && stop.getLocation() instanceof Europe) {
                return moveTowardEurope(unit, (Europe)stop.getLocation());
            } else if (unit.getDestination() instanceof Europe) {
                return moveTowardEurope(unit, (Europe)unit.getDestination());
            } else if (await(getGUI().confirm(oldTile, StringTemplate
                    .template("highseas.text")
                    .addAmount("%number%", unit.getSailTurns()),
                    unit, "highseas.yes", "highseas.no"))) {
                return moveTowardEurope(unit, unit.getOwner().getEurope());
            }
        }
        return moveTile(unit, direction);
    }

    /**
     * Move a free colonist to a native settlement to learn a skill following
     * a move of MoveType.ENTER_INDIAN_SETTLEMENT_WITH_FREE_COLONIST.
     * The colonist does not physically get into the village, it will
     * just stay where it is and gain the skill.
     *
     * @param unit The {@code Unit} to learn the skill.
     * @param direction The direction in which the Indian settlement lies.
     * @return True if automatic movement of the unit can proceed (never).
     */
    private CompletableFuture<Boolean> moveLearnSkill(Unit unit, Direction direction) {
        // Refresh knowledge of settlement skill.  It may have been
        // learned by another player.
        if (await(askClearGotoOrders(unit))
            && await(askServer().askSkill(unit, direction))) {
            IndianSettlement is
                = (IndianSettlement)getSettlementAt(unit.getTile(), direction);
            UnitType skill = is.getLearnableSkill();
            if (skill == null) {
                showInformationPanel(is, "info.noMoreSkill");
            } else if (unit.getUnitChange(UnitChangeType.NATIVES) == null) {
                showInformationPanel(is, StringTemplate
                    .template("info.cantLearnSkill")
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL))
                    .addNamed("%skill%", skill));
            } else if (await(getGUI().confirm(unit.getTile(), StringTemplate
                    .template("learnSkill.text")
                    .addNamed("%skill%", skill),
                    unit, "learnSkill.yes", "learnSkill.no"))) {
                if (await(askServer().learnSkill(unit, direction))) {
                    if (unit.isDisposed()) {
                        showInformationPanel(is, "learnSkill.die");
                    } else if (unit.getType() != skill) {
                        showInformationPanel(is, "learnSkill.leave");
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Move a unit in a specified direction on the map, following a
     * move of MoveType.MOVE.
     *
     * @param unit The {@code Unit} to be moved.
     * @param direction The direction in which to move the Unit.
     * @return True if automatic movement of the unit can proceed.
     */
    private CompletableFuture<Boolean> moveTile(Unit unit, Direction direction) {
        final ClientOptions options = getClientOptions();
        List<Unit> ul;
        if (unit.canCarryUnits() && unit.hasSpaceLeft()
            && options.getBoolean(ClientOptions.AUTOLOAD_SENTRIES)
            && unit.isInColony()
            && !(ul = unit.getTile().getUnitList()).isEmpty()) {
            // Autoload sentries if selected
            if (!await(moveAutoload(unit,
                              transform(ul, Unit.sentryPred)))) return CompletableFuture.completedFuture(false);
        }

        // Break up the goto to allow region naming to occur, BR#2707
        final Tile newTile = unit.getTile().getNeighbourOrNull(direction);
        boolean discover = newTile != null
            && newTile.getDiscoverableRegion() != null;

        // Ask the server
        if (!await(askServer().move(unit, direction))) {
            // Can fail due to desynchronization.  Skip this unit so
            // we do not end up retrying indefinitely.
            await(changeState(unit, UnitState.SKIPPED));
            return CompletableFuture.completedFuture(false);
        }

        unit.getOwner().invalidateCanSeeTiles();
        // Perform a short pause on an active unit's last move if the
        // option is enabled.
        if (unit.getMovesLeft() <= 0
            && options.getBoolean(ClientOptions.UNIT_LAST_MOVE_DELAY)) {
            delay(UNIT_LAST_MOVE_DELAY, "Last move delay interrupted.");
        }

        // Update the active unit and GUI.
        boolean ret = !unit.isDisposed() && !await(checkCashInTreasureTrain(unit));
        if (ret) {
            final Tile tile = unit.getTile();
            if (unit.isInColony()
                && unit.isCarrier()
                && unit.getTradeRoute() == null
                && Map.isSameLocation(tile, unit.getDestination())) {
                // Bring up colony panel if non-trade-route carrier
                // unit just arrived at a destination colony.
                // Automatic movement should stop.
                showColonyPanelWithCarrier(tile.getColony(), unit);
                ret = false;
            } else {
                ; // Automatic movement can continue after successful move.
            }
        }
        return CompletableFuture.completedFuture(ret && !discover);
    }

    /**
     * Move to a foreign colony and either attack, negotiate with the
     * foreign power or spy on them.  Follows a move of
     * MoveType.ENTER_FOREIGN_COLONY_WITH_SCOUT.
     *
     * FIXME: Unify trade and negotiation.
     *
     * @param unit The unit that will spy, negotiate or attack.
     * @param direction The direction in which the foreign colony lies.
     * @return True if automatic movement of the unit can proceed (never).
     */
    private CompletableFuture<Boolean> moveScoutColony(Unit unit, Direction direction) {
        final Game game = getGame();
        Colony colony = (Colony) getSettlementAt(unit.getTile(), direction);
        boolean canNeg = colony.getOwner() != unit.getOwner().getREFPlayer();

        if (!await(askClearGotoOrders(unit))) return CompletableFuture.completedFuture(false);

        ScoutColonyAction act
            = await(getGUI().getScoutForeignColonyChoice(colony, unit, canNeg));
        if (act == null) return CompletableFuture.completedFuture(true); // Cancelled
        switch (act) {
        case SCOUT_COLONY_ATTACK:
            return moveAttackSettlement(unit, direction);
        case SCOUT_COLONY_NEGOTIATE:
            Player player = unit.getOwner();
            DiplomaticTrade agreement
                = new DiplomaticTrade(game, TradeContext.DIPLOMATIC,
                                      player, colony.getOwner(), null, 0);
            showNegotiationDialog(unit, colony, agreement,
                agreement.getSendMessage(player, colony), direction);
            return CompletableFuture.completedFuture(false);
        case SCOUT_COLONY_SPY:
            return moveSpy(unit, direction);
        default:
            logger.warning("showScoutForeignColonyDialog fail: " + act);
            break;
        }
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Move a scout into an Indian settlement to speak with the chief,
     * or demand a tribute following a move of
     * MoveType.ENTER_INDIAN_SETTLEMENT_WITH_SCOUT.
     * The scout does not physically get into the village, it will
     * just stay where it is.
     *
     * @param unit The {@code Unit} that is scouting.
     * @param direction The direction in which the Indian settlement lies.
     * @return True if automatic movement of the unit can proceed (never).
     */
    private CompletableFuture<Boolean> moveScoutIndianSettlement(Unit unit, Direction direction) {
        if (!await(askClearGotoOrders(unit))
            || !await(askServer().scoutSettlement(unit, direction))) return CompletableFuture.completedFuture(false);

        // Offer the choices.
        final Tile unitTile = unit.getTile();
        final Tile tile = unitTile.getNeighbourOrNull(direction);
        final Player player = unit.getOwner();
        final IndianSettlement is = tile.getIndianSettlement();
        final int count = player.getNationSummary(is.getOwner())
            .getNumberOfSettlements();
        ScoutIndianSettlementAction act
            = await(getGUI().getScoutIndianSettlementChoice(is, (count <= 0)
                ? Messages.message("many") : Integer.toString(count)));
        if (act == null) return CompletableFuture.completedFuture(false); // Cancelled
        switch (act) {
        case SCOUT_SETTLEMENT_ATTACK:
            if (await(getGUI().confirmPreCombat(unit, tile))) {
                await(askServer().attack(unit, direction));
            }
            break;
        case SCOUT_SETTLEMENT_SPEAK:
            // Prevent turn ending to allow speaking results to complete
            moveMode = moveMode.minimize(MoveMode.EXECUTE_GOTO_ORDERS);
            await(askServer().scoutSpeakToChief(unit, is));
            break;
        case SCOUT_SETTLEMENT_TRIBUTE:
            return moveTribute(unit, 1, direction);
        default:
            logger.warning("showScoutIndianSettlementDialog fail: " + act);
            break;
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Spy on a foreign colony.
     *
     * @param unit The {@code Unit} that is spying.
     * @param direction The {@code Direction} of a colony to spy on.
     * @return True if automatic movement of the unit can proceed (never).
     */
    private CompletableFuture<Boolean> moveSpy(Unit unit, Direction direction) {
        Settlement settlement = getSettlementAt(unit.getTile(), direction);
        if (settlement instanceof Colony && !unit.getOwner().owns(settlement)) {
            await(askServer().spy(unit, settlement));
        } else {
            logger.warning("Unit " + unit + " can not spy on " + settlement);
        }                
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Arrive at a settlement with a laden carrier following a move of
     * MoveType.ENTER_SETTLEMENT_WITH_CARRIER_AND_GOODS.
     *
     * @param unit The carrier.
     * @param direction The direction to the settlement.
     * @return True if automatic movement of the unit can proceed (never).
     */
    private CompletableFuture<Boolean> moveTrade(Unit unit, Direction direction) {
        if (!await(askClearGotoOrders(unit))) return CompletableFuture.completedFuture(false);

        Settlement settlement = getSettlementAt(unit.getTile(), direction);
        if (settlement instanceof Colony) {
            final Game game = getGame();
            final Player player = unit.getOwner();
            DiplomaticTrade agreement
                = new DiplomaticTrade(game, TradeContext.TRADE,
                    player, settlement.getOwner(), null, 0);
            showNegotiationDialog(unit, settlement, agreement,
                agreement.getSendMessage(player, settlement), direction);
        } else if (settlement instanceof IndianSettlement) {
            await(askServer().newNativeTradeSession(unit, (IndianSettlement)settlement));
            changeView(unit, false); // Will be deselected on losing moves
        } else {
            throw new RuntimeException("Bogus settlement: "
                + settlement.getId());
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Demand a tribute.
     *
     * @param unit The {@code Unit} to perform the attack.
     * @param amount An amount of tribute to demand.
     * @param direction The direction in which to attack.
     * @return True if automatic movement of the unit can proceed (never).
     */
    private CompletableFuture<Boolean> moveTribute(Unit unit, int amount, Direction direction) {
        final Game game = getGame();
        Player player = unit.getOwner();
        Tile tile = unit.getTile();
        Tile target = tile.getNeighbourOrNull(direction);
        Settlement settlement = target.getSettlement();
        Player other = settlement.getOwner();

        // Indians are easy and can use the basic tribute mechanism.
        if (settlement.getOwner().isIndian()) {
            await(askServer().demandTribute(unit, direction));
            return CompletableFuture.completedFuture(false);
        }
        
        // Europeans might be human players, so we convert to a diplomacy
        // dialog.
        DiplomaticTrade agreement = DiplomaticTrade
            .makePeaceTreaty(TradeContext.TRIBUTE, player, other);
        agreement.add(new GoldTradeItem(game, other, player, amount));
        return moveDiplomacy(unit, direction, agreement);
    }

    /**
     * Move a missionary into a native settlement, following a move of
     * MoveType.ENTER_INDIAN_SETTLEMENT_WITH_MISSIONARY.
     *
     * @param unit The {@code Unit} that will enter the settlement.
     * @param direction The direction in which the Indian settlement lies.
     * @return True if automatic movement of the unit can proceed (never).
     */
    private CompletableFuture<Boolean> moveUseMissionary(Unit unit, Direction direction) {
        if (!await(askClearGotoOrders(unit))) return CompletableFuture.completedFuture(false);

        final IndianSettlement is
            = (IndianSettlement)getSettlementAt(unit.getTile(), direction);
        getMissionaryChoice(unit, is, direction);
        return CompletableFuture.completedFuture(false);
    }


    // Trade route support.

    /**
     * Follows a trade route, doing load/unload actions, moving the unit,
     * and updating the stop and destination.
     *
     * @param unit The {@code Unit} on the route.
     * @param messages An optional list in which to retain any
     *     {@code ModelMessage}s generated.
     * @return True if automatic movement can proceed.
     */
    private CompletableFuture<Boolean> followTradeRoute(Unit unit, List<ModelMessage> messages) {
        final Player player = unit.getOwner();
        final TradeRoute tr = unit.getTradeRoute();
        final boolean detailed = getClientOptions()
            .getBoolean(ClientOptions.SHOW_GOODS_MOVEMENT);
        final boolean checkProduction = getClientOptions()
            .getBoolean(ClientOptions.STOCK_ACCOUNTS_FOR_PRODUCTION);
        final List<TradeRouteStop> stops = unit.getCurrentStops();
        boolean result = true;

        // If required, accumulate a summary of all the activity of
        // this unit on its trade route.
        LogBuilder lb = new LogBuilder((detailed && !tr.isSilent()) ? 256
            : -1);
        lb.mark();

        // Validate the whole route.
        boolean valid = true;
        for (TradeRouteStop trs : stops) {
            if (!TradeRoute.isStopValid(unit, trs)) {
                lb.add(" ", Messages.message(trs.invalidStopLabel(player)));
                valid = false;
            }
        }
        if (!valid) {
            await(clearOrders(unit));
            stops.clear();
            result = false;
        }

        // Try to find work to do on the current list of stops.
        while (!stops.isEmpty()) {
            TradeRouteStop stop = stops.remove(0);

            if (!unit.atStop(stop)) {
                // Not at stop, give up if no moves left or the path was
                // exhausted on a previous round.
                if (unit.getMovesLeft() <= 0
                    || unit.getState() == UnitState.SKIPPED) {
                    lb.add(" ", Messages.message(stop
                            .getLabelFor("tradeRoute.toStop", player)));
                    break;
                }

                // Find a path to the stop, skip if none.
                Location destination = stop.getLocation();
                PathNode path = unit.findPath(destination);
                if (path == null) {
                    lb.add("\n", Messages.message(stop
                            .getLabelFor("tradeRoute.pathStop", player)));
                    await(changeState(unit, UnitState.SKIPPED));
                    break;
                }
                
                // Try to follow the path.  If the unit does not reach
                // the stop it is finished for now.
                await(movePath(unit, path));
                if (!unit.atStop(stop)) {
                    await(changeState(unit, UnitState.SKIPPED));
                    break;
                }
            }

            // At the stop, do the work available.
            lb.mark();
            await(unloadUnitAtStop(unit, lb)); // Anything to unload?
            await(loadUnitAtStop(unit, lb)); // Anything to load?
            lb.grew("\n", Messages.message(stop.getLabelFor("tradeRoute.atStop",
                                                            player)));

            // If the un/load consumed the moves, break now before
            // updating the stop.  This allows next turn to retry
            // un/loading, but this time it will not consume the moves.
            if (unit.getMovesLeft() <= 0) break;

            // Find the next stop with work to do.
            TradeRouteStop next = null;
            List<TradeRouteStop> moreStops = unit.getCurrentStops();
            if (unit.atStop(moreStops.get(0))) moreStops.remove(0);
            for (TradeRouteStop trs : moreStops) {
                if (trs.hasWork(unit, (!checkProduction) ? 0
                                : unit.getTurnsToReach(trs.getLocation()))) {
                    next = trs;
                    break;
                }
            }
            if (next == null) {
                // No work was found anywhere on the trade route,
                // so we should skip this unit.
                lb.add(" ", Messages.message("tradeRoute.wait"));
                await(changeState(unit, UnitState.SKIPPED));
                unit.setMovesLeft(0);
                break;
            }
            // Add a message for any skipped stops.
            List<TradeRouteStop> skipped
                = tr.getStopSublist(stops.get(0), next);
            if (!skipped.isEmpty()) {
                StringTemplate t = StringTemplate.label("")
                    .add("tradeRoute.skipped");
                String sep = " ";
                for (TradeRouteStop trs : skipped) {
                    t.addName(sep)
                        .addStringTemplate(trs.getLocation()
                            .getLocationLabelFor(player));
                    sep = ", ";
                }
                t.addName(".");
                lb.add(" ", Messages.message(t));
            }
            // Bring the next stop to the head of the stops list if it
            // is present.
            while (!stops.isEmpty() && stops.get(0) != next) {
                stops.remove(0);
            }
            // Set the new stop, skip on error.
            if (!await(askServer().setCurrentStop(unit, tr.getIndex(next)))) {
                await(changeState(unit, UnitState.SKIPPED));
                break;
            }
        }

        if (lb.grew()) {
            ModelMessage m = new ModelMessage(MessageType.GOODS_MOVEMENT,
                                              "tradeRoute.prefix", unit)
                .addName("%route%", tr.getName())
                .addStringTemplate("%unit%",
                    unit.getLabel(Unit.UnitLabelType.NATIONAL))
                .addName("%data%", lb.toString());
            if (messages != null) {
                messages.add(m);
            } else {
                player.addModelMessage(m);
                turnReportMessages.add(m);
            }
        }
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Work out what goods to load onto a unit at a stop, and load them.
     *
     * @param unit The {@code Unit} to load.
     * @param lb A {@code LogBuilder} to update.
     * @return True if goods were loaded.
     */
    private CompletableFuture<Boolean> loadUnitAtStop(Unit unit, LogBuilder lb) {
        final boolean enhancedTradeRoutes = getSpecification()
            .getBoolean(GameOptions.ENHANCED_TRADE_ROUTES);
        final TradeRoute tradeRoute = unit.getTradeRoute();
        final TradeLocation trl = unit.getTradeLocation();
        if (trl == null) return CompletableFuture.completedFuture(false);

        final TradeRouteStop stop = unit.getStop();
        boolean ret = false;

        // A collapsed list of goods to load at this stop.
        List<AbstractGoods> toLoad = stop.getCompactCargo();
        // Templates to accumulate messages in.
        StringTemplate unexpected = StringTemplate.label(", ");
        StringTemplate noLoad = StringTemplate.label(", ");
        StringTemplate left = StringTemplate.label(", ");
        StringTemplate loaded = StringTemplate.label(", ");
        StringTemplate nonePresent = StringTemplate.label(", ");
        
        // Check the goods already on board.  If it is not expected to
        // be loaded at this stop then complain (unload must have
        // failed somewhere).  If it is expected to load, reduce the
        // loading amount by what is already on board.
        for (Goods g : unit.getCompactGoodsList()) {
            AbstractGoods ag = find(toLoad, AbstractGoods.matches(g.getType()));
            if (ag == null) { // Excess goods on board, failed unload?
                unexpected.addStringTemplate(g.getLabel());
            } else {
                int goodsAmount = g.getAmount();
                if (ag.getAmount() <= goodsAmount) { // At capacity
                    noLoad.addStringTemplate(StringTemplate
                        .template("tradeRoute.loadStop.noLoad.carrier")
                            .addNamed("%goodsType%", ag.getType()));
                    toLoad.remove(ag);
                } else {
                    ag.setAmount(ag.getAmount() - goodsAmount);
                }
            }
        }

        // Adjust toLoad with the actual amount to load.
        // Drop goods that are:
        // - missing
        // - do not have an export surplus
        // - (optionally) are not needed by the destination
        // and add messages for them.
        //
        // Similarly, for each goods type, add an entry to the limit
        // map, with value:
        // - the unit, when it lacks capacity for all the goods present
        // - the stop when there is a non-zero export limit
        // - (optionally) the destination stop when there is a non-zero
        //   import limit
        // - otherwise null
        java.util.Map<GoodsType, Location> limit = new HashMap<>();
        Iterator<AbstractGoods> iterator = toLoad.iterator();
        while (iterator.hasNext()) {
            AbstractGoods ag = iterator.next();
            final GoodsType type = ag.getType();
            int present = stop.getAvailableGoodsCount(type);
            int exportAmount = stop.getExportAmount(type, 0);
            int importAmount = INFINITY;
            TradeRouteStop unload = null;
            if (enhancedTradeRoutes) {
                final List<TradeRouteStop> stops = unit.getCurrentStops();
                stops.remove(0);
                Location start = unit.getLocation();
                int turns = 0;
                for (TradeRouteStop trs : stops) {
                    turns += unit.getTurnsToReach(start, trs.getLocation());
                    int amountIn = trs.getImportAmount(type, turns),
                        amountOut = trs.getExportAmount(type, turns);
                    if (none(trs.getCompactCargo(), AbstractGoods.matches(type))
                        || amountIn > amountOut) {
                        importAmount = amountIn;
                        unload = trs;
                        break;
                    }
                    start = trs.getLocation();
                }
            }
            if (enhancedTradeRoutes && unload == null) {
                noLoad.addStringTemplate(StringTemplate
                    .template("tradeRoute.loadStop.noLoad.noUnload")
                        .addNamed("%goodsType%", type));
                ag.setAmount(0);
            } else if (present <= 0) { // None present
                nonePresent.addNamed(type);
                ag.setAmount(0);
            } else if (exportAmount <= 0) { // Export blocked
                noLoad.addStringTemplate(StringTemplate
                    .template("tradeRoute.loadStop.noLoad.export")
                        .addNamed("%goodsType%", type)
                        .addAmount("%more%", present));
                ag.setAmount(0);
            } else if (importAmount <= 0) { // Import blocked
                noLoad.addStringTemplate(StringTemplate
                    .template("tradeRoute.loadStop.noLoad.import")
                        .addNamed("%goodsType%", type)
                        .addAmount("%more%", present)
                        .addStringTemplate("%location%", unload.getLocation()
                            .getLocationLabelFor(unit.getOwner())));
                ag.setAmount(0);
            } else if (exportAmount <= importAmount
                && exportAmount < ag.getAmount()) { // Export limited
                ag.setAmount(exportAmount);
                limit.put(type, stop.getLocation());
            } else if (importAmount <= exportAmount
                && importAmount < ag.getAmount()) { // Import limited
                int already = unit.getGoodsCount(type);
                if (already >= importAmount) {
                    if (already > importAmount) {
                        askUnloadGoods(type, already - importAmount, unit);
                    }
                    noLoad.addStringTemplate(StringTemplate
                        .template("tradeRoute.loadStop.noLoad.already")
                            .addNamed("%goodsType%", type));
                    ag.setAmount(0);
                } else {
                    ag.setAmount(importAmount - already);
                }
                limit.put(type, unload.getLocation());
            } else if (present > ag.getAmount()) { // Carrier limited (last!)
                limit.put(type, unit);
            } else { // Expecting to load everything present
                limit.put(type, null);
            }

            // Do not load this goods type
            if (ag.getAmount() <= 0) iterator.remove();

            logger.log(Level.FINEST, "Load " + tradeRoute.getName()
                + " with " + unit.getId() + " at " + stop.getLocation()
                + " of " + type.getSuffix() + " from " + present
                + " exporting " + exportAmount + " importing " + importAmount
                + " to " + ((unload == null) ? "?"
                    : unload.getLocation().toString())
                + " limited by " + limit.get(type)
                + " -> " + ag.getAmount());
        }

        if (enhancedTradeRoutes) { // Prioritize by goods amount
            toLoad.sort(AbstractGoods.descendingAmountComparator);
        }
        
        // Load the goods.
        boolean done = false;
        for (AbstractGoods ag : toLoad) {
            final GoodsType type = ag.getType();
            final int amount = ag.getAmount();
            if (!done) {
                if (unit.getLoadableAmount(type) < amount) {
                    done = true;
                } else if (stop.getLocation() instanceof Europe) {
                    done = !await(buyGoods(type, amount, unit));
                } else {
                    done = !await(askLoadGoods(stop.getLocation(), type, amount, unit));
                }
            }
            if (done) {
                left.addNamed(ag);
                continue;
            }
            int present = stop.getAvailableGoodsCount(type);
            Location why = limit.get(type);
            if (present == 0) {
                loaded.addStringTemplate(ag.getLabel());
            } else if (why == null) {
                loaded.addStringTemplate(StringTemplate
                    .template("tradeRoute.loadStop.load.fail")
                        .addStringTemplate("%goods%", ag.getLabel())
                        .addAmount("%more%", present));
            } else if (why == unit) {
                loaded.addStringTemplate(StringTemplate
                    .template("tradeRoute.loadStop.load.carrier")
                        .addStringTemplate("%goods%", ag.getLabel())
                        .addAmount("%more%", present));
            } else if (Map.isSameLocation(why, stop.getLocation())) {
                loaded.addStringTemplate(StringTemplate
                    .template("tradeRoute.loadStop.load.export")
                    .addStringTemplate("%goods%", ag.getLabel())
                    .addAmount("%more%", present));
            } else {
                loaded.addStringTemplate(StringTemplate
                    .template("tradeRoute.loadStop.load.import")
                    .addStringTemplate("%goods%", ag.getLabel())
                    .addAmount("%more%", present)
                    .addStringTemplate("%location%",
                        why.getLocationLabelFor(unit.getOwner())));
            }
            ret = true;
        }
        if (!loaded.isEmpty()) {
            lb.add("\n  ", Messages.message(StringTemplate
                    .template("tradeRoute.loadStop.load")
                        .addStringTemplate("%goodsList%", loaded)));
        }
        if (!unexpected.isEmpty()) {
            lb.add("\n  ", Messages.message(StringTemplate
                    .template("tradeRoute.loadStop.unexpected")
                        .addStringTemplate("%goodsList%", unexpected)));
        }
        if (!left.isEmpty()) {
            noLoad.addStringTemplate(StringTemplate
                .template("tradeRoute.loadStop.noLoad.left")
                    .addStringTemplate("%goodsList%", left));
        }
        if (!nonePresent.isEmpty()) {
            noLoad.addStringTemplate(StringTemplate
                .template("tradeRoute.loadStop.noLoad.goods")
                    .addStringTemplate("%goodsList%", nonePresent));
        }
        if (!noLoad.isEmpty()) {
            lb.add("\n  ", Messages.message(StringTemplate
                    .template("tradeRoute.loadStop.noLoad")
                        .addStringTemplate("%goodsList%", noLoad)));
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Work out what goods to unload from a unit at a stop, and unload them.
     *
     * @param unit The {@code Unit} to unload.
     * @param lb A {@code LogBuilder} to update.
     * @return True if something was unloaded.
     */
    private CompletableFuture<Boolean> unloadUnitAtStop(Unit unit, LogBuilder lb) {
        final TradeLocation trl = unit.getTradeLocation();
        if (trl == null) return CompletableFuture.completedFuture(false);

        final TradeRouteStop stop = unit.getStop();
        final List<GoodsType> goodsTypesToLoad = stop.getCargo();
        final StringTemplate unloaded = StringTemplate.label(", ");
        final StringTemplate noUnload = StringTemplate.label(", ");
        boolean ret = false;

        // Unload everything that is on the carrier but not listed to
        // be loaded at this stop.
        for (Goods goods : unit.getCompactGoodsList()) {
            GoodsType type = goods.getType();
            if (goodsTypesToLoad.contains(type)) continue; // Keep this cargo.
            int present = goods.getAmount();
            if (present <= 0) {
                logger.warning("Unexpected empty goods unload " + goods);
                continue;
            }
            int toUnload = present;
            int atStop = trl.getImportAmount(type, 0);
            int amount = toUnload;
            if (amount > atStop) {
                StringTemplate locName = ((Location)trl).getLocationLabel();
                int option = getClientOptions()
                    .getInteger(ClientOptions.UNLOAD_OVERFLOW_RESPONSE);
                switch (option) {
                case ClientOptions.UNLOAD_OVERFLOW_RESPONSE_ASK:
                    StringTemplate template = StringTemplate
                        .template("traderoute.warehouseCapacity")
                        .addStringTemplate("%unit%",
                            unit.getLabel(Unit.UnitLabelType.NATIONAL))
                        .addStringTemplate("%colony%", locName)
                        .addAmount("%amount%", toUnload - atStop)
                        .addNamed("%goods%", goods);
                    if (!await(getGUI().confirm(unit.getTile(), template,
                                          unit, "yes", "no"))) {
                        if (atStop == 0) continue;
                        amount = atStop;
                    }
                    break;
                case ClientOptions.UNLOAD_OVERFLOW_RESPONSE_NEVER:
                    amount = atStop;
                    break;
                case ClientOptions.UNLOAD_OVERFLOW_RESPONSE_ALWAYS:
                    break;
                default:
                    logger.warning("Illegal UNLOAD_OVERFLOW_RESPONSE: "
                        + Integer.toString(option));
                    break;
                }
            }
            if (amount == 0) {
                noUnload.addStringTemplate(goods.getLabel());
                continue;
            }
            // Try to unload.
            ret = await(askUnloadGoods(type, amount, unit));
            int retained = unit.getGoodsCount(type);
            if (!ret || present == retained) {
                noUnload.addStringTemplate(StringTemplate
                    .template("tradeRoute.unloadStop.noUnload.fail")
                    .addStringTemplate("%goods%", goods.getLabel()));
                ret = false;
                break;
            }
            if (present - retained != amount) {
                unloaded.addStringTemplate(StringTemplate
                    .template("tradeRoute.unloadStop.unload.fail")
                    .addNamed("%goodsType%", type)
                    .addAmount("%amount%", amount)
                    .addAmount("%more%", retained));
            } else if (amount > atStop) {
                if (retained > 0) {
                    unloaded.addStringTemplate(StringTemplate
                        .template("tradeRoute.unloadStop.unload.keep")
                        .addNamed("%goodsType%", type)
                        .addAmount("%amount%", atStop)
                        .addAmount("%more%", retained));
                } else {
                    unloaded.addStringTemplate(StringTemplate
                        .template("tradeRoute.unloadStop.unload.overflow")
                        .addNamed("%goodsType%", type)
                        .addAmount("%amount%", atStop)
                        .addAmount("%more%", amount - atStop));
                }
            } else {
                unloaded.addStringTemplate(goods.getLabel());
            }
        }
        if (!unloaded.isEmpty()) {
            lb.add("\n  ", Messages.message(StringTemplate
                    .template("tradeRoute.unloadStop.unload")
                        .addStringTemplate("%goodsList%", unloaded)));
        }
        if (!noUnload.isEmpty()) {
            lb.add("\n  ", Messages.message(StringTemplate
                    .template("tradeRoute.unloadStop.noUnload")
                        .addStringTemplate("%goodsList%", noUnload)));
        }

        return CompletableFuture.completedFuture(ret);
    }


    // Routines from here on are mostly user commands.  That is they
    // are called directly as a result of keyboard, menu, mouse or
    // panel/dialog actions.  Some though are called indirectly after
    // a call to the server routes information back through the input
    // handler.  They should all be annotated as such to confirm where
    // they can come from.
    //
    // User command all return a success/failure indication, except if
    // the game is stopped.  Message.clientHandler-initiated routines
    // do not need to.
    //
    // Successfully executed commands should update the GUI.

    /**
     * Abandon a colony with no units.
     *
     * Called from ColonyPanel.closeColonyPanel
     *
     * @param colony The {@code Colony} to be abandoned.
     * @return True if the colony was abandoned.
     */
    public CompletableFuture<Boolean> abandonColony(Colony colony) {
        final Player player = getMyPlayer();
        if (colony == null || !player.owns(colony) || colony.getUnitCount() > 0
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        // Proceed to abandon
        final Tile tile = colony.getTile();
        boolean ret = await(askServer().abandonColony(colony))
            && !tile.hasSettlement();
        if (ret) {
            player.invalidateCanSeeTiles();
            updateGUI(tile, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Handle an addPlayer message.
     *
     * @param players The {@code Player}s to add.
     */
    public void addPlayerHandler(List<Player> players) {
        getGame().addPlayers(players);
    }

    /**
     * Animate an attack.
     *
     * @param attacker The attacking {@code Unit}.
     * @param defender The defending {@code Unit}.
     * @param attackerTile The {@code Tile} the attack originates from.
     * @param defenderTile The {@code Tile} the defence takes place on.
     * @param success True if the attack succeeds.
     */
    public void animateAttackHandler(Unit attacker, Unit defender,
                                     Tile attackerTile, Tile defenderTile,
                                     boolean success) {
        getGUI().animateUnitAttack(attacker, defender,
                                   attackerTile, defenderTile, success);
    }

    /**
     * Animate a move.
     *
     * @param unit The {@code Unit} that moves.
     * @param oldTile The {@code Tile} the move begins at.
     * @param newTile The {@code Tile} the move ends at.
     */
    public void animateMoveHandler(Unit unit, Tile oldTile, Tile newTile) {
        getGUI().animateUnitMove(unit, oldTile, newTile);
    }

    /**
     * Assigns a student to a teacher.
     *
     * Called from UnitLabel
     *
     * @param student The student {@code Unit}.
     * @param teacher The teacher {@code Unit}.
     * @return True if the student was assigned.
     */
    public CompletableFuture<Boolean> assignTeacher(Unit student, Unit teacher) {
        final Player player = getMyPlayer();
        if (student == null
            || !player.owns(student)
            || student.getColony() == null
            || !student.isInColony()
            || teacher == null
            || !player.owns(teacher)
            || !student.canBeStudent(teacher)
            || teacher.getColony() == null
            || student.getColony() != teacher.getColony()
            || !teacher.getColony().canTrain(teacher)
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        UnitWas unitWas = new UnitWas(student);
        boolean ret = await(askServer().assignTeacher(student, teacher))
            && student.getTeacher() == teacher;
        if (ret) {
            fireChanges(unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Assigns a trade route to a unit.
     *
     * Called from EuropePanel.DestinationPanel, TradeRoutePanel(),
     * TradeRoutePanel.newRoute
     *
     * @param unit The {@code Unit} to assign a trade route to.
     * @param tradeRoute The {@code TradeRoute} to assign.
     * @return True if the route was successfully assigned.
     */
    public CompletableFuture<Boolean> assignTradeRoute(Unit unit, TradeRoute tradeRoute) {
        if (unit == null) return CompletableFuture.completedFuture(false);

        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askAssignTradeRoute(unit, tradeRoute));
        if (ret) {
            fireChanges(unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Boards a specified unit onto a carrier.
     * The carrier must be at the same location as the boarding unit.
     *
     * Called from CargoPanel, TilePopup.
     *
     * @param unit The {@code Unit} which is to board the carrier.
     * @param carrier The location of this Unit.
     * @return True if the unit boards the carrier.
     */
    public CompletableFuture<Boolean> boardShip(Unit unit, Unit carrier) {
        if (unit == null || unit.isCarrier()
            || carrier == null || !carrier.canCarryUnits()
            || !unit.isAtLocation(carrier.getLocation())
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        boolean ret = await(askEmbark(unit, carrier));
        if (ret) {
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Use the active unit to build a colony.
     *
     * Called from BuildColonyAction.
     *
     * @param unit The {@code Unit} to build the colony.
     * @return True if a colony was built.
     */
    public CompletableFuture<Boolean> buildColony(Unit unit) {
        if (!requireOurTurn() || unit == null) return CompletableFuture.completedFuture(false);

        // Check unit, which must be on the map and able to build.
        final Tile tile = unit.getTile();
        if (tile == null) return CompletableFuture.completedFuture(false);
        if (!unit.canBuildColony()) {
            showInformationPanel(unit, StringTemplate
                .template("buildColony.badUnit")
                .addName("%unit%", unit.getName()));
            return CompletableFuture.completedFuture(false);
        }

        // Join existing colony if present
        if (await(joinColony(unit)) || tile.getColony() != null) return CompletableFuture.completedFuture(false);

        // Check for other impediments.
        final Player player = getMyPlayer();
        NoClaimReason reason = player.canClaimToFoundSettlementReason(tile);
        switch (reason) {
        case NONE:
        case NATIVES: // Tile can still be claimed
            break;
        default:
            showInformationPanel(null, reason.getDescriptionKey());
            return CompletableFuture.completedFuture(false);
        }

        // Show the warnings if applicable.
        if (getClientOptions().getBoolean(ClientOptions.SHOW_COLONY_WARNINGS)) {
            StringTemplate warnings = tile.getBuildColonyWarnings(unit);
            if (!warnings.isEmpty()
                && !await(getGUI().confirm(tile, warnings, unit,
                                     "buildColony.yes", "buildColony.no"))) {
                return CompletableFuture.completedFuture(false);
            }
        }

        // Get and check the name.
        String name = await(getGUI().getNewColonyName(player, tile));
        if (name == null) return CompletableFuture.completedFuture(false);

        // Claim tile from other owners before founding a settlement.
        // Only native owners that we can steal, buy from, or use a
        // bonus center tile exception should be possible by this point.
        UnitWas unitWas = new UnitWas(unit);
        boolean ret = player.owns(tile);
        if (!ret) {
            ret = await(askClaimTile(player, tile, unit, player.getLandPrice(tile)));
            if (!ret) NameCache.putSettlementName(player, name);
        }            
        if (ret) {
            ret = await(askServer().buildColony(name, unit))
                && tile.hasSettlement();
            if (ret) {
                sound("sound.event.buildingComplete");
                player.invalidateCanSeeTiles();
                fireChanges(unitWas);
                // Check units present for treasure cash-in as they are now
                // at a colony.
                for (Unit u : tile.getUnitList()) checkCashInTreasureTrain(u);
                updateGUI(null, false);
                showColonyPanelWithCarrier((Colony)tile.getSettlement(), unit);
            }
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Buy goods in Europe.
     * The amount of goods is adjusted to the space in the carrier.
     *
     * Called from CargoPanel, TilePopup, loadCargo()
     *
     * @param type The type of goods to buy.
     * @param amount The amount of goods to buy.
     * @param carrier The {@code Unit} acting as carrier.
     * @return True if the purchase succeeds.
     */
    public CompletableFuture<Boolean> buyGoods(GoodsType type, int amount, Unit carrier) {
        final Player player = getMyPlayer();
        if (type == null || amount <= 0
            || carrier == null || !carrier.isInEurope()
            || !player.owns(carrier) || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        final Europe europe = player.getEurope();
        EuropeWas europeWas = new EuropeWas(europe);
        MarketWas marketWas = new MarketWas(player);
        UnitWas unitWas = new UnitWas(carrier);

        boolean ret = await(askLoadGoods(europe, type, amount, carrier));
        if (ret) {
            marketWas.add(new AbstractGoods(type, -amount));
            sound("sound.event.loadCargo");
            fireChanges(unitWas, europeWas, marketWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Sends a public chat message.
     *
     * Called from ChatPanel
     *
     * @param chat The text of the message.
     * @return True if the message was sent.
     */
    public CompletableFuture<Boolean> chat(String chat) {
        if (chat == null) return CompletableFuture.completedFuture(false);

        final Player player = getMyPlayer();
        final boolean sent = await(askServer().chat(player, chat));
        displayChat(player.getName(), chat, player.getNationColor(), false);
        return CompletableFuture.completedFuture(sent);
    }

    /**
     * Chat with another player.
     *
     * @param sender The sender of the chat message.
     * @param message What to say.
     * @param color The message color.
     * @param pri If true, the message is private.
     */
    public void chatHandler(String sender, String message, Color color,
                            boolean pri) {
        displayChat(sender, message, color, pri);
    }
    
    /**
     * Changes the state of this {@code Unit}.
     *
     * Called from FortifyAction, SentryAction, TilePopup, UnitLabel
     *
     * @param unit The {@code Unit}
     * @param state The state of the unit.
     * @return True if the state was changed.
     */
    public CompletableFuture<Boolean> changeState(Unit unit, UnitState state) {
        if (!requireOurTurn() || unit == null) return CompletableFuture.completedFuture(false);
        if (unit.getState() == state) return CompletableFuture.completedFuture(true);
        if (!unit.checkSetState(state)) return CompletableFuture.completedFuture(false);

        // Check if this is a hostile fortification, and give the player
        // a chance to confirm.
        final Player player = getMyPlayer();
        if (state == UnitState.FORTIFYING && unit.isOffensiveUnit()
            && !unit.isOwnerHidden()) {
            Tile tile = unit.getTile();
            if (tile != null && tile.getOwningSettlement() != null) {
                Player enemy = tile.getOwningSettlement().getOwner();
                if (player != enemy
                    && player.getStance(enemy) != Stance.ALLIANCE
                    && !await(getGUI().confirmHostileAction(unit, tile)))
                    return CompletableFuture.completedFuture(false); // Aborted
            }
        }

        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askServer().changeState(unit, state))
            && unit.getState() == state;
        if (ret) {
            fireChanges(unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }
    
    /**
     * Changes the work type of this {@code Unit}.
     *
     * Called from ImprovementAction.
     *
     * @param unit The {@code Unit}
     * @param improvementType a {@code TileImprovementType} value
     * @return True if the improvement was changed.
     */
    public CompletableFuture<Boolean> changeWorkImprovementType(Unit unit,
        TileImprovementType improvementType) {
        if (unit == null || !unit.hasTile()
            || !unit.checkSetState(UnitState.IMPROVING)
            || improvementType == null || improvementType.isNatural()
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        // May need to claim the tile first
        final Player player = getMyPlayer();
        final Tile tile = unit.getTile();
        UnitWas unitWas = new UnitWas(unit);
        boolean ret = player.owns(tile)
            || await(askClaimTile(player, tile, unit, player.getLandPrice(tile)));
        if (ret) {
            updateGUI(null, false);

            ret = await(askServer()
                .changeWorkImprovementType(unit, improvementType))
                && unit.getWorkImprovement() != null
                && unit.getWorkImprovement().getType() == improvementType;
            if (ret) {
                fireChanges(unitWas);
                updateGUI(null, false);
            }
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Changes the work type of this {@code Unit}.
     *
     * Called from ColonyPanel.tryWork, UnitLabel
     *
     * @param unit The {@code Unit}
     * @param workType The new {@code GoodsType} to produce.
     * @return True if the work type was changed.
     */
    public CompletableFuture<Boolean> changeWorkType(Unit unit, GoodsType workType) {
        if (!requireOurTurn() || unit == null) return CompletableFuture.completedFuture(false);

        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askServer().changeWorkType(unit, workType))
            && unit.getWorkType() == workType;
        if (ret) {
            fireChanges(unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Check if a unit is a treasure train, and if it should be cashed in.
     * Transfers the gold carried by this unit to the {@link Player owner}.
     *
     * Called from TilePopup
     *
     * @param unit The {@code Unit} to be checked.
     * @return True if the unit was cashed in (and disposed).
     */
    public CompletableFuture<Boolean> checkCashInTreasureTrain(Unit unit) {
        if (unit == null || !unit.canCarryTreasure()
            || !unit.canCashInTreasureTrain()
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        final Tile tile = unit.getTile();
        final Europe europe = unit.getOwner().getEurope();
        if (europe == null || unit.isInEurope()) {
            ;// No need to check for transport.
        } else {
            int fee = unit.getTransportFee();
            StringTemplate template;
            if (fee == 0) {
                template = StringTemplate.template("cashInTreasureTrain.free");
            } else {
                int percent = getSpecification()
                    .getInteger(GameOptions.TREASURE_TRANSPORT_FEE);
                template = StringTemplate.template("cashInTreasureTrain.pay")
                    .addAmount("%fee%", percent);
            }
            if (!await(getGUI().confirm(unit.getTile(), template, unit,
                                  "accept", "reject"))) return CompletableFuture.completedFuture(false);
        }

        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askServer().cashInTreasureTrain(unit))
            && unit.isDisposed();
        if (ret) {
            sound("sound.event.cashInTreasureTrain");
            fireChanges(unitWas);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Choose a founding father from an offered list.
     *
     * @param ffs A list of {@code FoundingFather}s to choose from.
     * @param ff The chosen {@code FoundingFather} (may be null).
     * @return True if a father was chosen.
     */
    private CompletableFuture<Boolean> chooseFoundingFather(List<FoundingFather> ffs,
                                         FoundingFather ff) {
        if (ffs == null) return CompletableFuture.completedFuture(false);

        final Player player = getMyPlayer();
        player.setCurrentFather(ff);
        return askServer().chooseFoundingFather(ffs, ff);
    }

    /**
     * Choose a founding father from an offered list.
     *
     * @param ffs A list of {@code FoundingFather}s to choose from.
     */
    public void chooseFoundingFatherHandler(List<FoundingFather> ffs) {
        if (ffs == null || ffs.isEmpty()) return;
       
        getGUI().showChooseFoundingFatherDialog(ffs).thenAccept(
            (FoundingFather ff) -> chooseFoundingFather(ffs, ff));
    }

    /**
     * Claim a tile.
     *
     * Called from ColonyPanel.ASingleTilePanel, UnitLabel and work()
     *
     * @param tile The {@code Tile} to claim.
     * @param claimant The {@code Unit} or {@code Colony} claiming.
     * @return True if the claim succeeded.
     */
    public CompletableFuture<Boolean> claimTile(Tile tile, FreeColGameObject claimant) {
        if (tile == null
            || claimant == null
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        final Player player = getMyPlayer();
        final int price = ((claimant instanceof Settlement)
                ? player.canClaimForSettlement(tile)
                : player.canClaimForImprovement(tile))
            ? 0
            : player.getLandPrice(tile);
        UnitWas unitWas = (claimant instanceof Unit)
            ? new UnitWas((Unit)claimant) : null;
        boolean ret = await(askClaimTile(player, tile, claimant, price));
        if (ret) {
            fireChanges(unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Clears the goto orders of the given unit by setting its destination
     * to null.
     *
     * Called from CanvasMouseListener
     *
     * @param unit The {@code Unit} to clear the destination for.
     * @return True if the unit has no destination.
     */
    public CompletableFuture<Boolean> clearGotoOrders(Unit unit) {
        if (!requireOurTurn() || unit == null) return CompletableFuture.completedFuture(false);

        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askClearGotoOrders(unit));
        if (ret) {
            fireChanges(unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Clears the orders of the given unit.
     * Make the unit active and set a null destination and trade route.
     *
     * Called from ClearOrdersAction, TilePopup, TradeRoutePanel, UnitLabel
     *
     * @param unit The {@code Unit} to clear the orders of
     * @return boolean <b>true</b> if the orders were cleared
     */
    public CompletableFuture<Boolean> clearOrders(Unit unit) {
        if (!requireOurTurn() || unit == null) return CompletableFuture.completedFuture(false);

        if (unit.getState() == UnitState.IMPROVING
            && !await(getGUI().confirm(unit.getTile(), StringTemplate
                .template("clearOrders.text")
                .addAmount("%turns%", unit.getWorkTurnsLeft()),
                unit, "ok", "cancel"))) {
            return CompletableFuture.completedFuture(false);
        }

        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askClearGotoOrders(unit))
            && await(changeState(unit, UnitState.ACTIVE));
        if (ret) {
            fireChanges(unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Clear the speciality of a Unit, making it a Free Colonist.
     *
     * Called from UnitLabel
     *
     * @param unit The {@code Unit} to clear the speciality of.
     * @return True if the speciality was cleared.
     */
    public CompletableFuture<Boolean> clearSpeciality(Unit unit) {
        if (!requireOurTurn() || unit == null) return CompletableFuture.completedFuture(false);

        UnitTypeChange uc = unit.getUnitChange(UnitChangeType.CLEAR_SKILL);
        UnitType newType = (uc == null) ? null : uc.to;
        if (newType == null) {
            showInformationPanel(unit, StringTemplate
                .template("clearSpeciality.impossible")
                .addStringTemplate("%unit%",
                    unit.getLabel(Unit.UnitLabelType.NATIONAL)));
            return CompletableFuture.completedFuture(false);
        }

        final Tile tile = (getGUI().isPanelShowing()) ? null : unit.getTile();
        if (!await(getGUI().confirm(tile, StringTemplate
                .template("clearSpeciality.areYouSure")
                .addStringTemplate("%oldUnit%",
                    unit.getLabel(Unit.UnitLabelType.NATIONAL))
                .addNamed("%unit%", newType),
                unit, "ok", "cancel"))) {
            return CompletableFuture.completedFuture(false);
        }

        // Try to clear.
        // Note that this routine is only called out of UnitLabel,
        // where the unit icon is always updated anyway.
        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askServer().clearSpeciality(unit))
            && unit.getType() == newType;
        if (ret) {
            fireChanges(unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Special handling when we close a colony panel.
     *
     * @param colony The {@code Colony} to close.
     * @param abandon True if abandoning, otherwise just update.
     */
    public void closeColony(Colony colony, boolean abandon) {
        if (abandon) {
            abandonColony(colony);
        } else {
            updateGUI(colony.getTile(), false);
        }
    }

    /**
     * Close any open GUI menus.
     *
     * @param panel The identifier for the panel to close.
     */
    public void closeHandler(String panel) {
        getGUI().closePanel(panel);
    }

    /**
     * Declares independence for the home country.
     *
     * Called from DeclareIndependenceAction
     *
     * @return True if independence was declared.
     */
    public boolean declareIndependence() {
        if (!requireOurTurn()) return false;

        final Player player = getMyPlayer();
        if (player.getNewLandName() == null) {
            return false; // Can only happen in debug mode.
        }

        // Check for adequate support.
        StringTemplate declare = player.checkDeclareIndependence();
        if (declare != null) {
            showInformationPanel(null, declare);
            return false;
        }

        // Confirm intention, and collect nation+country names.
        List<String> names = await(getGUI().showConfirmDeclarationDialog());
        if (names == null
            || names.get(0) == null || names.get(0).isEmpty()
            || names.get(1) == null || names.get(1).isEmpty()) {
            // Empty name => user cancelled.
            return false;
        }

        getGUI().showDeclarationPanel(() -> {
            askServer().declareIndependence(names.get(0), names.get(1));
            updateGUI(null, false);
        });
        return true;
    }

    /**
     * Delete a trade route.
     *
     * Called from TradeRoutePanel button.
     *
     * @param tradeRoute The {@code TradeRoute} to delete.
     * @return True if the route was successfully deleted.
     */
    public CompletableFuture<Boolean> deleteTradeRoute(TradeRoute tradeRoute) {
        final Player player = getMyPlayer();
        final String name = tradeRoute.getName();
        boolean ret = await(askServer().deleteTradeRoute(tradeRoute));
        return CompletableFuture.completedFuture(ret && player.getTradeRouteByName(name, null) == null);
    }

    /**
     * Handle a diplomatic offer.
     *
     * @param our Our {@code FreeColGameObject} that is negotiating.
     * @param other The other {@code FreeColGameObject}.
     * @param agreement The {@code DiplomaticTrade} agreement.
     */
    public void diplomacyHandler(final FreeColGameObject our,
                                 final FreeColGameObject other,
                                 final DiplomaticTrade agreement) {
        final Player player = getMyPlayer();
        final Player otherPlayer = agreement.getOtherPlayer(player);
        StringTemplate t, nation = otherPlayer.getNationLabel();

        switch (agreement.getStatus()) {
        case ACCEPT_TRADE:
            boolean visibilityChange = false;
            for (Colony c : agreement.getColoniesGivenBy(player)) {
                player.removeSettlement(c);//-vis(player)
                visibilityChange = true;
            }
            for (Unit u : agreement.getUnitsGivenBy(player)) {
                player.removeUnit(u);//-vis(player)
                visibilityChange = true;
            }
            if (visibilityChange) player.invalidateCanSeeTiles();//+vis(player)
            ModelMessage mm
                = new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                                   "diplomacy.offerAccepted", otherPlayer)
                    .addStringTemplate("%nation%", nation);
            player.addModelMessage(mm);
            updateGUI(null, false);
            break;
        case REJECT_TRADE:
            t = StringTemplate.template("diplomacy.offerRejected")
                .addStringTemplate("%nation%", nation);
            showInformationPanel(null, t);
            break;
        case PROPOSE_TRADE:
            invokeLater(() -> {
                    StringTemplate template = agreement.getReceiveMessage(otherPlayer);
                    getGUI().showNegotiationDialog(our, other, agreement, template).thenAccept((DiplomaticTrade ourAgreement) -> {
                        if (ourAgreement == null) {
                            ourAgreement = agreement;
                            ourAgreement.setStatus(TradeStatus.REJECT_TRADE);
                        }
                        askServer().diplomacy(our, other, ourAgreement);
                    });
                });
            break;
        default:
            logger.warning("Bogus trade status: " + agreement.getStatus());
            break;
        }
    }

    /**
     * Disbands the active unit.
     *
     * Called from DisbandUnitAction.
     *
     * @param unit The {@code Unit} to disband.
     * @return True if the unit was disbanded.
     */
    public CompletableFuture<Boolean> disbandUnit(Unit unit) {
        if (!requireOurTurn() || unit == null) return CompletableFuture.completedFuture(false);

        if (unit.getColony() != null
            && !await(getGUI().confirmLeaveColony(unit))) return CompletableFuture.completedFuture(false);
        final Tile tile = (getGUI().isPanelShowing()) ? null : unit.getTile();
        if (!await(getGUI().confirm(tile, StringTemplate.key("disbandUnit.text"),
                              unit, "disbandUnit.yes", "cancel")))
            return CompletableFuture.completedFuture(false);

        // Try to disband
        boolean ret = await(askServer().disbandUnit(unit)) && unit.isDisposed();
        if (ret) {
            updateGUI(tile, false);
        }
        // Special case if no units left, might end up undead
        if (getMyPlayer().getUnitCount() == 0 && await(setDead())) {
            updateGUI(null, true);
        }

        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Emigrate a unit from Europe.
     *
     * @param player The {@code Player} that owns the unit.
     * @param slot The slot to emigrate from, [0..RECRUIT_COUNT].
     * @param n The number of remaining units known to be eligible to migrate.
     * @param foY True if this migration is due to a fountain of youth event.
     */
    private CompletableFuture<Void> emigrate(Player player, int slot, int n, boolean foY) {
        if (player == null || !player.isColonial()
            || !MigrationType.validMigrantSlot(slot)) return CompletableFuture.completedFuture(null);

        if (await(askEmigrate(player.getEurope(), slot)) != null) {
            await(emigration(player, n, foY));
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * End the turn command.
     *
     * Called from EndTurnAction, GUI.showEndTurnDialog
     *
     * @param showDialog If false, suppress showing the end turn dialog.
     */
    public CompletableFuture<Void> endTurn(boolean showDialog) {
        if (!requireOurTurn()) {
            return CompletableFuture.completedFuture(null);
        }

        await(doEndTurn(showDialog && getClientOptions().getBoolean(ClientOptions.SHOW_END_TURN_DIALOG)));
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Change the role-equipment a unit has.
     *
     * Called from DefaultTransferHandler, QuickActionMenu
     *
     * @param unit The {@code Unit}.
     * @param role The {@code Role} to assume.
     * @param roleCount The role count.
     * @return True if the role is taken.
     */
    public CompletableFuture<Boolean> equipUnitForRole(Unit unit, Role role, int roleCount) {
        if (unit == null
            || role == null || 0 > roleCount || roleCount > role.getMaximumCount()
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);
        if (role == unit.getRole()
            && roleCount == unit.getRoleCount()) return CompletableFuture.completedFuture(true);

        final Player player = getMyPlayer();
        final Colony colony = unit.getColony();
        ColonyWas colonyWas = null;
        EuropeWas europeWas = null;
        MarketWas marketWas = null;
        int price = -1;

        List<AbstractGoods> req = unit.getGoodsDifference(role, roleCount);
        if (unit.isInEurope()) {
            europeWas = new EuropeWas(player.getEurope());
            marketWas = new MarketWas(player);
            for (AbstractGoods ag : req) {
                GoodsType goodsType = ag.getType();
                if (!player.canTrade(goodsType) && !await(payArrears(goodsType))) {
                    return CompletableFuture.completedFuture(false); // payment failed
                }
            }
            try {
                price = player.getEurope().priceGoods(req);
                if (!player.checkGold(price)) return CompletableFuture.completedFuture(false);
            } catch (FreeColException fce) {
                return CompletableFuture.completedFuture(false);
            }
            // Reverse the sense of the transaction.  It is currently
            // relative to the unit (positive amount => unit gains
            // goods/equipment) but if the server interaction
            // succeeds, we want to log the transaction with respect
            // to the market (using the MarketWas and req) where we
            // want a positive amount => unit gave up (sold)
            // goods/equipment.
            for (AbstractGoods ag : req) ag.setAmount(-ag.getAmount());
            marketWas.addAll(req);
        } else if (colony != null) {
            colonyWas = new ColonyWas(colony);
            for (AbstractGoods ag : req) {
                if (colony.getGoodsCount(ag.getType()) < ag.getAmount()) {
                    StringTemplate template = StringTemplate
                        .template("equipUnit.impossible")
                        .addName("%colony%", colony.getName())
                        .addNamed("%equipment%", ag.getType())
                        .addStringTemplate("%unit%",
                            unit.getLabel(Unit.UnitLabelType.NATIONAL));
                    showInformationPanel(unit, template);
                    return CompletableFuture.completedFuture(false);
                }
            }
        } else {
            return CompletableFuture.completedFuture(false);
        }

        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askServer().equipUnitForRole(unit, role, roleCount))
            && unit.getRole() == role;
        if (ret) {
            fireChanges(colonyWas, europeWas, marketWas, unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Handle an error.
     *
     * @param template A {@code StringTemplate} to display.
     * @param message An extra non-i18n message to display if debugging.
     */
    public void errorHandler(StringTemplate template, String message) {
        error(template, message);
    }

    /**
     * Execute goto orders command.
     *
     * Called from ExecuteGotoOrdersAction.
     *
     * @return True if all goto orders have been performed and no units
     *     reached their destination and are free to move again.
     */
    public CompletableFuture<Boolean> executeGotoOrders() {
        if (!requireOurTurn()
            || getGUI().isPanelShowing()) return CompletableFuture.completedFuture(false);

        return doExecuteGotoOrders();
    }

    /**
     * Handle feature changes.
     *
     * @param parent The parent {@code FreeColGameObject} to add to.
     * @param children The child {@code FreeColObject}s that change.
     * @param add If true, add the child, otherwise remove it.
     */
    public void featureChangeHandler(FreeColGameObject parent,
                                     List<FreeColObject> children, boolean add) {
        for (FreeColObject fco : children) {
            if (fco instanceof Ability) {
                if (add) {
                    parent.addAbility((Ability)fco);
                } else {
                    parent.removeAbility((Ability)fco);
                }
            } else if (fco instanceof Modifier) {
                if (add) {
                    parent.addModifier((Modifier)fco);
                } else {
                    parent.removeModifier((Modifier)fco);
                }
            } else if (fco instanceof HistoryEvent) {
                if (parent instanceof Player && add) {
                    Player player = (Player)parent;
                    player.addHistory((HistoryEvent)fco);
                } else {
                    logger.warning("Feature change NYI: "
                        + parent + "/" + add + "/" + fco);
                }
            } else if (fco instanceof LastSale) {
                if (parent instanceof Player && add) {
                    Player player = (Player)parent;
                    player.addLastSale((LastSale)fco);
                } else {
                    logger.warning("Feature change NYI: "
                        + parent + "/" + add + "/" + fco);
                }
            } else if (fco instanceof ModelMessage) {
                if (parent instanceof Player && add) {
                    Player player = (Player)parent;
                    player.addModelMessage((ModelMessage)fco);
                } else {
                    logger.warning("Feature change NYI: "
                        + parent + "/" + add + "/" + fco);
                }
            } else {        
                logger.warning("featureChange unrecognized: " + fco);
            }
        }
    }

    /**
     * A player makes first contact with a native player.
     *
     * @param player The {@code Player} making contact.
     * @param other The native {@code Player} being contacted.
     * @param tile An optional {@code Tile} to offer the player if
     *     they have made a first landing.
     * @param result Whether the initial treaty was accepted.
     * @return True if first contact occurs.
     */
    private CompletableFuture<Boolean> firstContact(Player player, Player other, Tile tile,
                                 boolean result) {
        if (player == null || player == other || tile == null) return CompletableFuture.completedFuture(false);

        boolean ret = await(askServer().firstContact(player, other, tile, result));
        if (ret) {
            updateGUI(tile, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * A player makes first contact with a native player.
     *
     * @param player The {@code Player} making contact.
     * @param other The native {@code Player} being contacted.
     * @param tile An optional {@code Tile} to offer the player if
     *     they have made a first landing.
     * @param n The number of settlements claimed by the native player.
     */
    public void firstContactHandler(Player player, Player other, Tile tile,
                                    int n) {
        getGUI().showFirstContactDialog(player, other, tile, n).thenAccept(
            (Boolean b) -> firstContact(player, other, tile, b));
    }

    /**
     * Handle a fountain of youth event.
     *
     * @param n The number of migrants available for selection.
     */
    public void fountainOfYouthHandler(int n) {
        showEmigrationDialog(getMyPlayer(), true, n);
    }

    /**
     * The player has won, show the high scores and victory dialog.
     *
     * @param score If "true", a new high score was reached.
     */
    public void gameEndedHandler(String score) {
        highScore("true".equalsIgnoreCase(score)).thenAccept((ret) ->
            getGUI().showVictoryDialog().thenAccept((Boolean result) -> victory(result)));
    }

    /**
     * Go to a tile.
     *
     * Called from CanvasMouseListener, TilePopup
     *
     * @param unit The {@code Unit} to move.
     * @param path The {@code Path} to move along.
     * @return True if the destination change was successful.
     */
    public CompletableFuture<Boolean> goToTile(Unit unit, PathNode path) {
        if (unit == null || !getMyPlayer().owns(unit)
            || path == null
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        if (!await(getGUI().confirmClearTradeRoute(unit))) return CompletableFuture.completedFuture(false);

        // FIXME: should follow path directly rather than delegating
        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askSetDestination(unit, path.getLastNode().getLocation()));
        if (ret) {
            await(moveToDestination(unit, null));
            fireChanges(unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Display the high scores.
     *
     * Called from ReportHighScoresAction
     *
     * @param high A {@code Boolean} whose values indicates whether
     *     a new high score has been achieved, or no information if null.
     * @return True if the server interaction succeeded.
     */
    public CompletableFuture<Boolean> highScore(Boolean high) {
        return askServer().getHighScores((high == null) ? null
            : ((high) ? "highscores.yes" : "highscores.no"));
    }

    /**
     * Display the high scores.
     *
     * @param key An optional message key.
     * @param scores The list of {@code HighScore} records to display.
     */
    public void highScoresHandler(String key, List<HighScore> scores) {
        invokeLater(() ->
            getGUI().showHighScoresPanel(key, scores));
    }

    /**
     * Ignore this ModelMessage from now on until it is not generated
     * in a turn.
     *
     * Called from ReportTurnPanel
     *
     * @param message a {@code ModelMessage} value
     * @param flag whether to ignore the ModelMessage or not
     * @return True, ignore message status changes can not fail.
     */
    public boolean ignoreMessage(ModelMessage message, boolean flag) {
        String key;
        if (message == null
            || (key = message.getIgnoredMessageKey()) == null) return false;
        if (flag) {
            final Turn turn = getGame().getTurn();
            if (!continueIgnoreMessage(key, turn)) {
                startIgnoringMessage(key, turn);
            }
        } else {
            stopIgnoringMessage(key);
        }
        return true;
    }

    /**
     * Handle an incite response.
     *
     * @param unit The {@code Unit} that is inciting.
     * @param is The {@code IndianSettlement} being incited.
     * @param enemy The {@code Player} incited against.
     * @param gold The gold required by the natives to become hostile.
     */
    public void inciteHandler(Unit unit, IndianSettlement is, Player enemy,
                              int gold) {
        final Player player = getMyPlayer();
        
        if (gold < 0) {
            ; // protocol fail
        } else if (!player.checkGold(gold)) {
            showInformationPanel(is, StringTemplate
                .template("missionarySettlement.inciteGoldFail")
                .add("%player%", enemy.getName())
                .addAmount("%amount%", gold));
        } else {
            if (await(getGUI().confirm(unit.getTile(), StringTemplate
                    .template("missionarySettlement.inciteConfirm")
                    .addStringTemplate("%enemy%", enemy.getNationLabel())
                    .addAmount("%amount%", gold),
                    unit, "yes", "no"))) {
                askServer().incite(unit, is, enemy, gold);
            }
        }
    }

    /**
     * Handle a native demand at a colony.
     *
     * @param unit The native {@code Unit} making the demand.
     * @param colony The {@code Colony} demanded of.
     * @param type The {@code GoodsType} demanded (null means gold).
     * @param amount The amount of goods/gold demanded.
     */
    public void indianDemandHandler(Unit unit, Colony colony,
                                    GoodsType type, int amount) {
        final Player player = getMyPlayer();

        boolean accepted;
        int opt = getClientOptions().getInteger(ClientOptions.INDIAN_DEMAND_RESPONSE);
        switch (opt) {
        case ClientOptions.INDIAN_DEMAND_RESPONSE_ASK:
            getGUI().showNativeDemandDialog(unit, colony, type, amount).thenAccept(
                (Boolean accept) ->
                    askServer().indianDemand(unit, colony, type, amount,
                        ((accept) ? IndianDemandAction.INDIAN_DEMAND_ACCEPT
                            : IndianDemandAction.INDIAN_DEMAND_REJECT)));
            return;
        case ClientOptions.INDIAN_DEMAND_RESPONSE_ACCEPT:
            accepted = true;
            break;
        case ClientOptions.INDIAN_DEMAND_RESPONSE_REJECT:
            accepted = false;
            break;
        default:
            throw new RuntimeException("Impossible option value: " + opt);
        }

        final String nation = Messages.message(unit.getOwner().getNationLabel());
        ModelMessage m = (type == null)
            ? new ModelMessage(ModelMessage.MessageType.DEMANDS,
                               "indianDemand.gold.text", colony, unit)
                .addName("%nation%", nation)
                .addName("%colony%", colony.getName())
                .addAmount("%amount%", amount)
            : (type.isFoodType())
            ? new ModelMessage(ModelMessage.MessageType.DEMANDS,
                               "indianDemand.food.text", colony, unit)
                .addName("%nation%", nation)
                .addName("%colony%", colony.getName())
                .addAmount("%amount%", amount)
            : new ModelMessage(ModelMessage.MessageType.DEMANDS,
                               "indianDemand.other.text", colony, unit)
                .addName("%nation%", nation)
                .addName("%colony%", colony.getName())
                .addAmount("%amount%", amount)
                .addNamed("%goods%", type);
        player.addModelMessage(m);
        invokeLater(() -> nextModelMessage());
    }

    /**
     * Join the colony at a unit's current location.
     *
     * @param unit The {@code Unit} to use.
     * @return True if the unit joined a colony.
     */
    private CompletableFuture<Boolean> joinColony(Unit unit) {
        final Tile tile = unit.getTile();
        final Colony colony = (tile == null) ? null : tile.getColony();
        boolean ret = colony != null && await(askServer().joinColony(unit, colony))
            && unit.getState() == UnitState.IN_COLONY;
        if (ret) {
            updateGUI(null, false);
            showColonyPanel(colony, unit);
        }
        return CompletableFuture.completedFuture(ret);
    }
    
    /**
     * Leave a ship.  The ship must be in harbour.
     *
     * Called from CargoPanel, ColonyPanel, EuropePanel.unloadAction,
     * UnitLabel
     *
     * @param unit The {@code Unit} which is to leave the ship.
     * @return True if the unit left the ship.
     */
    public CompletableFuture<Boolean> leaveShip(Unit unit) {
        Unit carrier;
        if (unit == null || (carrier = unit.getCarrier()) == null
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        // Proceed to disembark
        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askServer().disembark(unit))
            && unit.getLocation() != carrier;
        if (ret) {
            checkCashInTreasureTrain(unit);
            fireChanges(unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Loads a cargo onto a carrier.
     *
     * Called from CargoPanel, ColonyPanel, LoadAction, TilePopup.
     *
     * @param goods The {@code Goods} which are going aboard the carrier.
     * @param carrier The {@code Unit} acting as carrier.
     * @return True if the goods were loaded.
     */
    public CompletableFuture<Boolean> loadCargo(Goods goods, Unit carrier) {
        if (goods == null || goods.getAmount() <= 0
            || goods.getLocation() == null
            || carrier == null || !carrier.isCarrier()
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        if (goods.getLocation() instanceof Europe) {
            return buyGoods(goods.getType(), goods.getAmount(), carrier);
        }
        UnitWas carrierWas = new UnitWas(carrier);
        UnitWas sourceWas = null;
        ColonyWas colonyWas = null;
        if (goods.getLocation() instanceof Unit) {
            Unit source = (Unit)goods.getLocation();
            sourceWas = new UnitWas(source);
        } else {
            Colony colony = carrier.getColony();
            if (colony == null) return CompletableFuture.completedFuture(false);
            colonyWas = new ColonyWas(colony);
        }

        boolean ret = await(askLoadGoods(goods.getLocation(), goods.getType(),
                                   goods.getAmount(), carrier));
        if (ret) {
            sound("sound.event.loadCargo");
            fireChanges(colonyWas, sourceWas, carrierWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Opens a dialog where the user should specify the filename and
     * loads the game.
     *
     * Called from OpenAction.
     *
     * Returns no status as this game is stopped.
     */
    public CompletableFuture<Void> loadGame() {
        File file = await(getGUI()
            .showLoadSaveFileDialog(FreeColDirectories.getSaveDirectory(),
                                    FreeCol.FREECOL_SAVE_EXTENSION));
        if (file == null) return CompletableFuture.completedFuture(null);

        final FreeColClient fcc = getFreeColClient();
        if (fcc.isInGame() && !await(getGUI().confirmStopGame())) {
            // User aborted.
            return CompletableFuture.completedFuture(null);
        }

        if (fcc.isLoggedIn()) {
            await(fcc.getConnectController().requestLogout(LogoutReason.LOGIN));
        }
        fcc.stopServer();
        getGUI().prepareShowingMainMenu();
        getGUI().repaint();

        FreeColDirectories.setSavegameFile(file.getPath());
        return getConnectController().startSavedGame(file).thenAccept((ret) -> {
            if (!ret)
                getGUI().showMainPanel(null);
        });
    }

    /**
     * Log out the current player.
     *
     * @param player The {@code Player} that is logging out.
     * @param reason The reason for logging out.
     */
    public void logoutHandler(Player player, LogoutReason reason) {
        if (player == null) return;

        final Game game = getGame();
        if (game.getCurrentPlayer() == player) {
            game.setCurrentPlayer(game.getNextPlayer());
        }
        if (getMyPlayer() == player) {
            getFreeColClient().getConnectController().logout(reason);
        }
    }
       
    /**
     * Loot some cargo.
     *
     * Called from GUI.showCaptureGoodsDialog
     *
     * @param unit The {@code Unit} that is looting.
     * @param goods A list of {@code Goods} to choose from.
     * @param defenderId The identifier of the defender unit (may have sunk).
     * @return True if looting occurs.
     */
    private CompletableFuture<Boolean> lootCargo(Unit unit, List<Goods> goods, String defenderId) {
        if (unit == null || goods == null || goods.isEmpty()
            || defenderId == null) return CompletableFuture.completedFuture(false);

        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askServer().loot(unit, defenderId, goods));
        if (ret) {
            fireChanges(unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Loot some cargo.
     *
     * @param unit The {@code Unit} that is looting.
     * @param goods A list of {@code Goods} to choose from.
     * @param loserId The identifier of the defender unit (may have sunk).
     */
    public void lootCargoHandler(Unit unit, List<Goods> goods, String loserId) {
        getGUI().showCaptureGoodsDialog(unit, goods).thenAccept(
            (List<Goods> gl) -> lootCargo(unit, gl, loserId));
    }

    /**
     * Accept or reject a monarch action.
     *
     * Called from GUI.showMonarchDialog
     *
     * @param action The {@code MonarchAction} performed.
     * @param accept If true, accept the action.
     * @return True if the monarch was answered.
     */
    public CompletableFuture<Boolean> monarchAction(MonarchAction action, boolean accept) {
        if (action == null) return CompletableFuture.completedFuture(false);

        boolean ret = false;
        switch (action) {
        case RAISE_TAX_ACT: case RAISE_TAX_WAR:
        case MONARCH_MERCENARIES: case HESSIAN_MERCENARIES:
            ret = await(askServer().answerMonarch(action, accept));
            break;
        default:
            break;
        }
        if (ret) {
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Do a monarch interaction.
     *
     * @param action The {@code MonarchAction} to perform.
     * @param template A {@code StringTemplate} describing the action.
     * @param monarchKey A key for the monarch involved.
     */
    public void monarchActionHandler(MonarchAction action,
                                     StringTemplate template,
                                     String monarchKey) {
        getGUI().showMonarchDialog(action, template, monarchKey).thenAccept(
            (Boolean b) -> monarchAction(action, b));
    }

    /**
     * Moves the specified unit somewhere that requires crossing the
     * high seas.
     *
     * Called from EuropePanel.DestinationPanel, TilePopup
     *
     * @param unit The {@code Unit} to be moved.
     * @param destination The {@code Location} to be moved to.
     * @return True if automatic movement of the unit can proceed.
     */
    public CompletableFuture<Boolean> moveTo(Unit unit, Location destination) {
        if (unit == null || destination == null
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        // Consider the distinct types of destinations.
        if (destination instanceof Europe) {
            if (unit.isInEurope()) {
                sound("sound.event.illegalMove");
                return CompletableFuture.completedFuture(false);
            }
            return moveTowardEurope(unit, (Europe)destination);
        } else if (destination instanceof Map) {
            if (unit.hasTile()
                // Will we have multiple maps one day?
                && unit.getTile().getMap() == destination) {
                sound("sound.event.illegalMove");
                return CompletableFuture.completedFuture(false);
            }
            return moveAwayFromEurope(unit, destination);
        } else if (destination instanceof Settlement) {
            if (unit.hasTile()) {
                sound("sound.event.illegalMove");
                return CompletableFuture.completedFuture(false);
            }
            return moveAwayFromEurope(unit, destination);
        } else {
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Moves the active unit in a specified direction. This may result in an
     * attack, move... action.
     *
     * Called from MoveAction, CornerMapControls
     *
     * @param unit The {@code Unit} to move.
     * @param direction The {@code Direction} in which to move
     *     the active unit.
     */
    public CompletableFuture<Void> moveUnit(Unit unit, Direction direction) {
        if (unit == null || !unit.hasTile()
            || direction == null
            || !requireOurTurn()) return CompletableFuture.completedFuture(null);

        if (!await(askClearGotoOrders(unit))) return CompletableFuture.completedFuture(null);

        final Tile oldTile = unit.getTile();
        UnitWas unitWas = new UnitWas(unit);
        ColonyWas colonyWas = (unit.getColony() == null) ? null
            : new ColonyWas(unit.getColony());
        await(changeState(unit, UnitState.ACTIVE));
        await(moveDirection(unit, direction, true));

        fireChanges(unitWas, colonyWas);
        updateGUI(null, false);

        if (unit.getTile() != oldTile && !unit.couldMove()
            && !unit.isDisposed() && unit.hasTile()) {
            // Show colony panel if unit moved and is now out of moves
            Colony colony = unit.getTile().getColony();
            if (colony != null) showColonyPanel(colony, unit);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * The player names a new region.
     *
     * Called from newRegionName, GUI.showNameNewRegionDialog
     *
     * @param tile The {@code Tile} within the region.
     * @param unit The {@code Unit} that has discovered the region.
     * @param region The {@code Region} to name.
     * @param name The name to offer.
     * @return True if the new region was named.
     */
    public CompletableFuture<Boolean> nameNewRegion(final Tile tile, final Unit unit,
                                 final Region region, final String name) {
        if (tile == null || unit == null || region == null) return CompletableFuture.completedFuture(false);

        return askServer().newRegionName(region, tile, unit, name);
    }

    /**
     * Get the nation summary for a player.
     *
     * Called from DiplomaticTradePanel, ReportForeignAffairsPanel,
     * ReportIndianPanel
     *
     * @param player The {@code Player} to summarize.
     * @return A summary of that nation, or null on error.
     */
    public CompletableFuture<NationSummary> nationSummary(Player player) {
        if (player == null) return CompletableFuture.completedFuture(null);

        final Player myPlayer = getMyPlayer();
        NationSummary ns = myPlayer.getNationSummary(player);
        if (ns != null) return CompletableFuture.completedFuture(ns);
        // Refresh from server
        if (await(askServer().nationSummary(myPlayer, player))) {
            return CompletableFuture.completedFuture(myPlayer.getNationSummary(player));
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handle a nation summary update.
     *
     * @param other The {@code Player} to update.
     * @param ns The {@code NationSummary} for the other player.
     */
    public void nationSummaryHandler(Player other, NationSummary ns) {
        final Player player = getMyPlayer();
        player.putNationSummary(other, ns);
        logger.info("Updated nation summary of " + other.getSuffix()
            + " for " + player.getSuffix() + " with " + ns);
    }

    /**
     * Cache a native trade update.
     *
     * @param action The {@code NativeTradeAction} to handle.
     * @param nt The {@code NativeTrade} underway.
     */
    public void nativeTradeHandler(NativeTradeAction action, NativeTrade nt) {
        if (nt == null) return;
        
        final GUI gui = getGUI();
        final IndianSettlement is = nt.getIndianSettlement();
        final Unit unit = nt.getUnit();
        final Player player = getMyPlayer();
        if (!player.owns(unit)) {
            logger.warning("We do not own the trading unit: " + unit);
            return;
        }

        final NativeTradeItem nti;
        final TradeAction act;
        final StringTemplate prompt;
        switch (action) { // Only consider actions returned by server
        case ACK_OPEN:
            nti = null;
            act = null;
            prompt = null;
            break;
        case ACK_BUY:
            nti = null;
            act = null;
            prompt = StringTemplate
                .template("trade.bought")
                .addNamed("%goodsType%", nt.getItem().getGoods().getType())
                .addStringTemplate("%nation%", is.getOwner().getNationLabel())
                .addStringTemplate("%settlement%", is.getLocationLabelFor(player));
            break;
        case ACK_SELL:
            nti = null;
            act = null;
            prompt = StringTemplate
                .template("trade.sold")
                .addNamed("%goodsType%", nt.getItem().getGoods().getType())
                .addStringTemplate("%nation%", is.getOwner().getNationLabel())
                .addStringTemplate("%settlement%", is.getLocationLabelFor(player));
            break;
        case ACK_GIFT:
            nti = null;
            act = null;
            prompt = StringTemplate
                .template("trade.gave")
                .addNamed("%goodsType%", nt.getItem().getGoods().getType())
                .addStringTemplate("%nation%", is.getOwner().getNationLabel())
                .addStringTemplate("%settlement%", is.getLocationLabelFor(player));
            break;
        case ACK_BUY_HAGGLE:
            act = TradeAction.BUY;
            nti = nt.getItem();
            prompt = null;
            break;
        case ACK_SELL_HAGGLE:
            act = TradeAction.SELL;
            nti = nt.getItem();
            prompt = null;
            break;
        case NAK_GOODS: // Polite refusal, does not end the session
            nti = null;
            act = null;
            prompt = StringTemplate
                .template("trade.noTradeGoods")
                .addNamed("%goodsType%", nt.getItem().getGoods().getType());
            break;
        case NAK_HAGGLE:
            showInformationPanel(null, StringTemplate
                .template("trade.noTradeHaggle"));
            return;
        case NAK_HOSTILE:
            showInformationPanel(null, StringTemplate
                    .template("trade.noTradeHostile"));
            return;
        case NAK_NOSALE:
            showInformationPanel(null, StringTemplate
                .template("trade.nothingToSell"));
            return;
        case NAK_INVALID: // Should not happen, log and fail quietly.
        default:
            logger.warning("Bogus native trade: " + nt);
            return;
        }

        invokeLater(() -> {
                Unit current = gui.getActiveUnit();
                gui.changeView(unit, false);
                UnitWas uw = new UnitWas(unit);
                nativeTrade(nt, act, nti, prompt);
                uw.fireChanges();
                gui.changeView(current, false);
            });                
    }

    /**
     * Execute the native trade.
     *
     * @param nt The {@code NativeTrade} underway.
     * @param act The {@code TradeAction} to perform.
     * @param nti The {@code NativeTradeItem} being haggled over, if any.
     * @param prompt An action-specific base prompt, if any.
     */
    private CompletableFuture<Boolean> nativeTrade(NativeTrade nt, TradeAction act,
                             NativeTradeItem nti, StringTemplate prompt) {
        final IndianSettlement is = nt.getIndianSettlement();
        final Unit unit = nt.getUnit();
        final StringTemplate base = StringTemplate
            .template("trade.welcome")
            .addStringTemplate("%nation%", is.getOwner().getNationLabel())
            .addStringTemplate("%settlement%", is.getLocationLabelFor(unit.getOwner()));
        
        // Col1 only displays at most 3 types of goods for sale.
        // Maintain this for now but consider lifting in a future
        // "enhanced trade" mode.
        nt.limitSettlementToUnit(3);
        
        final Function<NativeTradeItem, ChoiceItem<NativeTradeItem>>
            goodsMapper = i -> {
            String label = Messages.message(i.getGoods().getLabel(true));
            return new ChoiceItem<>(label, i);
        };
        while (!nt.getDone()) {
            if (act == null) {
                if (prompt == null) prompt = base;
                act = await(getGUI().getIndianSettlementTradeChoice(is, prompt,
                    nt.canBuy(), nt.canSell(), nt.canGift()));
                if (act == null) break;
                prompt = base; // Revert to base after first time through
            }
            switch (act) {
            case BUY:
                act = null;
                if (nti == null) {
                    nti = await(getGUI().getChoice(unit.getTile(),
                        StringTemplate.key("buyProposition.text"),
                        is, "nothing",
                        transform(nt.getSettlementToUnit(),
                                  NativeTradeItem::priceIsValid, goodsMapper)));
                    if (nti == null) break;
                    nt.setItem(nti);
                }
                TradeBuyAction tba = await(getGUI().getBuyChoice(unit, is,
                    nti.getGoods(), nti.getPrice(),
                    unit.getOwner().checkGold(nti.getPrice())));
                if (tba == TradeBuyAction.BUY) {
                    return askServer().nativeTrade(NativeTradeAction.BUY, nt);
                } else if (tba == TradeBuyAction.HAGGLE) {
                    nti.setPrice(NativeTradeItem.PRICE_UNSET);
                    return askServer().nativeTrade(NativeTradeAction.BUY, nt);
                }                    
                break;
            case SELL:
                act = null;
                if (nti == null) {
                    nti = await(getGUI().getChoice(unit.getTile(),
                        StringTemplate.key("sellProposition.text"),
                        is, "nothing",
                        transform(nt.getUnitToSettlement(),
                                  NativeTradeItem::priceIsValid, goodsMapper)));
                    if (nti == null) break;
                    nt.setItem(nti);
                }
                TradeSellAction tsa = await(getGUI().getSellChoice(unit, is,
                    nti.getGoods(), nti.getPrice()));
                if (tsa == TradeSellAction.SELL) {
                    return askServer().nativeTrade(NativeTradeAction.SELL, nt);
                } else if (tsa == TradeSellAction.HAGGLE) {
                    nti.setPrice(NativeTradeItem.PRICE_UNSET);
                    return askServer().nativeTrade(NativeTradeAction.SELL, nt);
                }
                break;
            case GIFT:
                act = null;
                nti = await(getGUI().getChoice(unit.getTile(),
                    StringTemplate.key("gift.text"),
                    is, "cancel",
                    transform(nt.getUnitToSettlement(), alwaysTrue(),
                              goodsMapper)));
                if (nti != null) {
                    nt.setItem(nti);
                    return askServer().nativeTrade(NativeTradeAction.GIFT, nt);
                }
                break;
            default:
                logger.warning("showIndianSettlementTradeDialog fail: "
                    + act);
                nt.setDone();
                break;
            }
            nti = null;
        }
        return askServer().nativeTrade(NativeTradeAction.CLOSE, nt);
    }

    /**
     * A player names the New World.
     *
     * Called from GUI.showNameNewLandDialog
     *
     * @param unit The {@code Unit} that landed.
     * @param name The name to use.
     * @return True if the new land was named.
     */
    private CompletableFuture<Boolean> newLandName(Unit unit, String name) {
        if (unit == null || name == null) return CompletableFuture.completedFuture(false);

        // Respond to the server.
        if (!await(askServer().newLandName(unit, name))) return CompletableFuture.completedFuture(false);

        // The name is set, bring up the first landing panel.
        final Player player = unit.getOwner();
        StringTemplate t = StringTemplate.template("event.firstLanding")
            .addName("%name%", name);

        invokeLater(() -> {
            final FreeColPanel firstLandinPanel = getGUI().showEventPanel(Messages.message(t), "image.flavor.event.firstLanding", null);
            firstLandinPanel.addClosingCallback(() -> {
                // Add tutorial message.
                final String key = "B"; /*@net.ringoz.GwtIncompatible FreeColActionUI
                    .getHumanKeyStrokeText(getFreeColClient()
                        .getActionManager().getFreeColAction("buildColonyAction")
                        .getAccelerator());*/
                player.addModelMessage(new ModelMessage(ModelMessage.MessageType.TUTORIAL, "buildColony.tutorial", player)
                    .addName("%colonyKey%", key)
                    .add("%colonyMenuItem%", "buildColonyAction.name")
                    .add("%ordersMenuItem%", "menuBar.orders"));
                nextModelMessage();
            });
        });

        return CompletableFuture.completedFuture(true);
    }

    /**
     * Ask the player to name the new land.
     *
     * @param unit The {@code Unit} that has landed.
     * @param defaultName The default name to use.
     */
    public void newLandNameHandler(Unit unit, String defaultName) {
        showNamingDialog(StringTemplate.key("newLand.text"), defaultName, unit).thenAccept(
            (String name) -> newLandName(unit,
                (name == null || name.isEmpty()) ? defaultName : name));
    }

    /**
     * The player names a new region.
     *
     * @param tile The {@code Tile} within the region.
     * @param unit The {@code Unit} that has discovered the region.
     * @param region The {@code Region} to name.
     * @param name The name to offer.
     * @return True if the new region was named.
     */
    private CompletableFuture<Boolean> newRegionName(final Region region, final Tile tile,
                                  final Unit unit, final String name) {
        if (tile == null || unit == null || region == null) return CompletableFuture.completedFuture(false);

        return askServer().newRegionName(region, tile, unit, name);
    }

    /**
     * Handle new region naming.
     *
     * @param region The {@code Region} to name.
     * @param tile The {@code Tile} the unit landed at.
     * @param unit The {@code Unit} that has landed.
     * @param name The default name to use.
     */
    public void newRegionNameHandler(Region region, Tile tile, Unit unit,
                                     String name) {
        invokeLater(() -> {
                if (region.hasName()) {
                    if (region.isPacific()) {
                        showEventPanel(Messages.message("event.discoverPacific"),
                            "image.flavor.event.discoverPacific", null);
                    }
                    newRegionName(region, tile, unit, name);
                } else {
                    if (getClientOptions().getBoolean(ClientOptions.SHOW_REGION_NAMING)) {
                        showNamingDialog(StringTemplate
                            .template("nameRegion.text")
                            .addStringTemplate("%type%", region.getLabel()),
                            name, unit).thenAccept(
                            (String n) -> newRegionName(region, tile, unit,
                                (n == null || n.isEmpty()) ? name : n));
                    } else {
                        newRegionName(region, tile, unit, name);
                    }

                }
            });
    }

    /**
     * Gets a new trade route for a player.
     *
     * Called from TradeRoutePanel.newRoute.  Relies on new trade routes
     * being added at the end of the trade route list.
     *
     * @param player The {@code Player} to get a new trade route for.
     * @return A new {@code TradeRoute}.
     */
    public CompletableFuture<TradeRoute> newTradeRoute(Player player) {
        if (player == null) return CompletableFuture.completedFuture(null);

        final int n = player.getTradeRouteCount();
        return CompletableFuture.completedFuture((await(askServer().newTradeRoute())
            && player.getTradeRouteCount() == n + 1)
            ? player.getNewestTradeRoute()
            : null);
    }

    /**
     * Handle a new trade route.
     *
     * @param tr The {@code TradeRoute} to add.
     */
    public void newTradeRouteHandler(TradeRoute tr) {
        final Player player = getMyPlayer();

        player.addTradeRoute(tr);
    }

    /**
     * Switch to a new turn.
     *
     * @param turn The turn number.
     * @return True if the new turn occurs.
     */
    private boolean newTurn(int turn) {
        final Game game = getGame();
        final Player player = getMyPlayer();

        if (turn < 0) {
            logger.warning("Bad turn in newTurn: " + turn);
            return false;
        }
        Turn newTurn = new Turn(turn);
        game.setTurn(newTurn);
        logger.info("New turn: " + newTurn + "/" + turn);

        if (getClientOptions().getBoolean(ClientOptions.AUDIO_ALERTS)) {
            sound("sound.event.alertSound");
        }

        final Turn currTurn = game.getTurn();
        if (currTurn.isFirstSeasonTurn()) {
            player.addModelMessage(new ModelMessage(MessageType.WARNING,
                                                    "twoTurnsPerYear", player)
                .addStringTemplate("%year%", currTurn.getLabel())
                .addAmount("%amount%", Turn.getSeasonNumber()));
        }
        player.clearNationCache();
        return true;
    }

    /**
     * Handle the new turn.
     *
     * @param turn The new turn number.
     */
    public void newTurnHandler(int turn) {
        invokeLater(() -> newTurn(turn));
    }
    
    /**
     * Makes a new unit active.
     *
     * Called from PGC.startGame, ColonyPanel.closeColonyPanel
     *
     * @return True unless it was not our turn.
     */
    public boolean nextActiveUnit() {
        if (!requireOurTurn()) return false;

        updateGUI(null, false);
        return true;
    }

    /**
     * Handle partial updates.
     *
     * @param fcgo The {@code FreeColGameObject} to update.
     * @param fields A map of fields to update.
     */
    public void partialHandler(FreeColGameObject fcgo,
        java.util.Map<String, String> fields) {
        for (Entry<String, String> e : fields.entrySet()) {
            try {
                Introspector intro = new Introspector(fcgo.getClass(), e.getKey());
                intro.setter(fcgo, e.getValue()); // Possible -vis(player)
            } catch (Introspector.IntrospectorException ie) {
                logger.log(Level.WARNING, "Partial update setter fail: "
                    + fcgo.getId() + "/" + e.getKey() + "=" + e.getValue(), ie);
            }
        }

        final Player player = getMyPlayer();
        if ((fcgo instanceof Player && (fcgo == player))
            || ((fcgo instanceof Ownable) && player.owns((Ownable)fcgo))) {
            player.invalidateCanSeeTiles();//+vis(player)
        }
    }

    /**
     * Pays the tax arrears on this type of goods.
     *
     * Called from CargoPanel, EuropePanel.MarketPanel,
     * EuropePanel.unloadAction, QuickActionMenu
     *
     * @param type The type of goods for which to pay arrears.
     * @return True if the arrears were paid.
     */
    public CompletableFuture<Boolean> payArrears(GoodsType type) {
        if (!requireOurTurn() || type == null) return CompletableFuture.completedFuture(false);

        final Player player = getMyPlayer();
        int arrears = player.getArrears(type);
        if (arrears <= 0) return CompletableFuture.completedFuture(false);
        if (!player.checkGold(arrears)) {
            showInformationPanel(null, StringTemplate
                .template("payArrears.noGold")
                .addAmount("%amount%", arrears));
            return CompletableFuture.completedFuture(false);
        }

        StringTemplate t = StringTemplate.template("payArrears.text")
            .addAmount("%amount%", arrears);
        if (!await(getGUI().confirm(null, t, type, "ok", "cancel"))) return CompletableFuture.completedFuture(false);

        boolean ret = await(askServer().payArrears(type)) && player.canTrade(type);
        if (ret) {
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Buys the remaining hammers and tools for the {@link Building} currently
     * being built in the given {@code Colony}.
     *
     * Called from BuildQueuePanel
     *
     * @param colony The {@code Colony} where the building should be
     *     bought.
     * @return True if the building was bought.
     */
    public CompletableFuture<Boolean> payForBuilding(Colony colony) {
        if (!requireOurTurn() || colony == null) return CompletableFuture.completedFuture(false);

        if (!getSpecification().getBoolean(GameOptions.PAY_FOR_BUILDING)) {
            showInformationPanel(null, "payForBuilding.disabled");
            return CompletableFuture.completedFuture(false);
        }

        if (!colony.canPayToFinishBuilding()) {
            showInformationPanel(null, "info.notEnoughGold");
            return CompletableFuture.completedFuture(false);
        }

        final int price = colony.getPriceForBuilding();
        StringTemplate t = StringTemplate.template("payForBuilding.text")
            .addAmount("%amount%", price);
        if (!await(getGUI().confirm(null, t, colony, "yes", "no"))) return CompletableFuture.completedFuture(false);

        ColonyWas colonyWas = new ColonyWas(colony);
        boolean ret = await(askServer().payForBuilding(colony))
            && colony.getPriceForBuilding() == 0;
        if (ret) {
            fireChanges(colonyWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Puts the specified unit outside the colony.
     *
     * Called from ColonyPanel.OutsideColonyPanel, UnitLabel
     *
     * @param unit The {@code Unit}
     * @return True if the unit was successfully put outside the colony.
     */
    public CompletableFuture<Boolean> putOutsideColony(Unit unit) {
        Colony colony;
        if (unit == null
            || (colony = unit.getColony()) == null
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        if (!await(getGUI().confirmLeaveColony(unit))) return CompletableFuture.completedFuture(false);

        ColonyWas colonyWas = new ColonyWas(colony);
        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askServer().putOutsideColony(unit))
            && unit.getLocation() == colony.getTile();
        if (ret) {
            fireChanges(colonyWas, unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Query whether the user wants to reconnect?
     *
     * Called from ReconnectAction.
     *
     * Returns no status, this game is going away.
     */
    public void reconnect() {
        final FreeColClient fcc = getFreeColClient();
        getGUI().confirm("reconnect.text",
                         "reconnect.no", "reconnect.yes").thenAccept((ret) -> {
            if (ret) {
                logger.finest("Reconnect quit.");
                fcc.getConnectController().requestLogout(LogoutReason.QUIT);
            } else {
                logger.finest("Reconnect accepted.");
                fcc.getConnectController().requestLogout(LogoutReason.RECONNECT);
            }
        });
    }

    /**
     * Handle a reconnect message.
     */
    public void reconnectHandler() {
        invokeLater(() -> reconnect());
    }

    /**
     * Recruit a unit from a specified index in Europe.
     *
     * Called from RecruitPanel
     *
     * @param index The index in Europe to recruit from, [0..RECRUIT_COUNT).
     * @return True if a unit was recruited.
     */
    public CompletableFuture<Boolean> recruitUnitInEurope(int index) {
        if (!requireOurTurn()
            || !MigrationType.validMigrantIndex(index)) return CompletableFuture.completedFuture(false);

        final Player player = getMyPlayer();
        if (!player.isColonial()) return CompletableFuture.completedFuture(false);

        if (!player.checkGold(player.getEuropeanRecruitPrice())) {
            showInformationPanel(null, "info.notEnoughGold");
            return CompletableFuture.completedFuture(false);
        }

        Unit newUnit = await(askEmigrate(player.getEurope(),
                                   MigrationType.migrantIndexToSlot(index)));
        if (newUnit != null) {
            changeView(newUnit, false);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(newUnit != null);
    }

    /**
     * Remove game objects.
     *
     * @param objects A list of {@code FreeColGameObject}s to remove.
     * @param divert An object to divert to when the original disappears.
     */
    public void removeHandler(List<FreeColGameObject> objects,
                              FreeColGameObject divert) {
        final Player player = getMyPlayer();
        boolean visibilityChange = false, updateUnit = false;
        for (FreeColGameObject fcgo : objects) {
            if (divert != null) player.divertModelMessages(fcgo, divert);
        
            if (fcgo instanceof Settlement) {
                Settlement settlement = (Settlement)fcgo;
                if (settlement.getOwner() != null) {
                    settlement.getOwner().removeSettlement(settlement);
                }
                visibilityChange = true;//-vis(player)
                
            } else if (fcgo instanceof Unit) {
                // Deselect the object if it is the current active unit.
                Unit u = (Unit)fcgo;
                updateUnit |= u == getGUI().getActiveUnit();

                // Temporary hack until we have real containers.
                if (u.getOwner() != null) u.getOwner().removeUnit(u);
                visibilityChange = true;//-vis(player)
            }

            // Do just the low level dispose that removes
            // reference to this object in the client.  The other
            // updates should have done the rest.
            fcgo.disposeResources();
        }
        if (visibilityChange) player.invalidateCanSeeTiles();//+vis(player)
        if (updateUnit) updateGUI(null, true);
    }
        
    /**
     * Renames a {@code Nameable}.
     *
     * Apparently this can be done while it is not your turn.
     *
     * Called from RenameAction, TilePopup.
     *
     * @param object The object to rename.
     * @return True if the object was renamed.
     */
    public CompletableFuture<Boolean> rename(Nameable object) {
        final Player player = getMyPlayer();
        if (!(object instanceof Ownable)
            || !player.owns((Ownable)object)) return CompletableFuture.completedFuture(false);

        String name = null;
        if (object instanceof Colony) {
            Colony colony = (Colony) object;
            name = await(getGUI().getInput(colony.getTile(),
                StringTemplate.key("renameColony.text"),
                colony.getName(), "rename", "cancel"));
            if (name == null) { // User cancelled
                return CompletableFuture.completedFuture(false);
            } else if (name.isEmpty()) { // Zero length invalid
                showInformationPanel(null, "info.enterSomeText");
                return CompletableFuture.completedFuture(false);
            } else if (colony.getName().equals(name)) { // No change
                return CompletableFuture.completedFuture(false);
            } else if (player.getSettlementByName(name) != null) {
                // Colony name must be unique.
                showInformationPanel((Colony)object, StringTemplate
                    .template("nameColony.notUnique")
                    .addName("%name%", name));
                return CompletableFuture.completedFuture(false);
            }
        } else if (object instanceof Unit) {
            Unit unit = (Unit) object;
            name = await(getGUI().getInput(unit.getTile(),
                StringTemplate.key("renameUnit.text"),
                unit.getName(), "rename", "cancel"));
            if (name == null) return CompletableFuture.completedFuture(false); // User cancelled
        } else {
            logger.warning("Tried to rename an unsupported Nameable: "
                + object);
            return CompletableFuture.completedFuture(false);
        }

        if (await(askServer().rename((FreeColGameObject)object, name))) {
            updateGUI(null, false);
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Save and quit the game.
     *
     * Called from and SaveAndQuitAction.
     *
     * @return False if the game was not saved, otherwise the game quits.
     */
    public CompletableFuture<Boolean> saveAndQuit() {
        if (!await(saveGame())) return CompletableFuture.completedFuture(false);
        getFreeColClient().quit();
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Opens a dialog where the user should specify the filename and
     * saves the game.
     *
     * Called from SaveAction and SaveAndQuitAction.
     *
     * @return True if the game was saved.
     */
    public CompletableFuture<Boolean> saveGame() {
        if (!getFreeColClient().canSaveCurrentGame()) return CompletableFuture.completedFuture(false);

        final Game game = getGame();
        if (game == null) return CompletableFuture.completedFuture(false); // Keyboard handling can race init
        String fileName = getSaveGameString(game);
        File file = await(getGUI().showSaveDialog(FreeColDirectories.getSaveDirectory(),
                                            fileName));
        if (file == null) return CompletableFuture.completedFuture(false);
        if (!getClientOptions().getBoolean(ClientOptions.CONFIRM_SAVE_OVERWRITE)
            || !file.exists()
            || await(getGUI().confirm("saveConfirmationDialog.areYouSure.text",
                                "ok", "cancel"))) {
            FreeColDirectories.setSavegameFile(file.getPath());
            return CompletableFuture.completedFuture(saveGame(file));
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Display the results of speaking to a chief.
     *
     * @param unit The {@code Unit} that was speaking.
     * @param is The {@code IndianSettlement} spoken to.
     * @param result The result.
     */
    public void scoutSpeakToChiefHandler(Unit unit, IndianSettlement is,
                                         String result) {
        switch (result) {
        case "":
            break;
        case "die":
            showInformationPanel(is, "scoutSettlement.speakDie");
            break;
        case "expert":
            showInformationPanel(is, StringTemplate
                .template("scoutSettlement.expertScout")
                .addNamed("%unit%", unit.getType()));
            break;
        case "tales":
            showInformationPanel(is, "scoutSettlement.speakTales");
            break;
        case "nothing":
            showInformationPanel(is, StringTemplate
                .template("scoutSettlement.speakNothing")
                .addStringTemplate("%nation%", unit.getOwner().getNationLabel()));
            break;
        default: // result == amount of gold
            showInformationPanel(is, StringTemplate
                .template("scoutSettlement.speakBeads")
                .add("%amount%", result));
            break;
        }
    }
    
    /**
     * Selects a destination for this unit. Europe and the player's
     * colonies are valid destinations.
     *
     * Called from GotoAction.
     *
     * @param unit The unit for which to select a destination.
     * @return True if the destination change succeeds.
     */
    public CompletableFuture<Boolean> selectDestination(Unit unit) {
        if (!requireOurTurn() || unit == null) return CompletableFuture.completedFuture(false);

        if (!await(getGUI().confirmClearTradeRoute(unit))) return CompletableFuture.completedFuture(false);
        Location destination = await(getGUI().showSelectDestinationDialog(unit));
        if (destination == null) return CompletableFuture.completedFuture(false);

        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askSetDestination(unit, destination));
        if (ret) {
            if (destination instanceof Europe) {
                if (unit.hasTile()
                    && unit.getTile().isDirectlyHighSeasConnected()) {
                    await(moveTowardEurope(unit, (Europe)destination));
                } else {
                    await(moveToDestination(unit, null));
                }
            } else {
                if (unit.isInEurope()) {
                    await(moveAwayFromEurope(unit, destination));
                } else {
                    await(moveToDestination(unit, null));
                }
            }
            fireChanges(unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Sells goods in Europe.
     *
     * Called from EuropePanel.MarketPanel, EuropePanel.unloadAction,
     * unload(), unloadCargo()
     *
     * @param goods The goods to be sold.
     * @return True if the sale succeeds.
     */
    public CompletableFuture<Boolean> sellGoods(Goods goods) {
        if (goods == null || !(goods.getLocation() instanceof Unit)
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        final Player player = getMyPlayer();
        Unit carrier = (Unit)goods.getLocation();

        EuropeWas europeWas = new EuropeWas(player.getEurope());
        MarketWas marketWas = new MarketWas(player);
        
        UnitWas unitWas = new UnitWas(carrier);
        boolean ret = await(askUnloadGoods(goods.getType(), goods.getAmount(), carrier));
        if (ret) {
            marketWas.add(new AbstractGoods(goods.getType(), goods.getAmount()));
            sound("sound.event.sellCargo");
            fireChanges(europeWas, marketWas, unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Handle setting the AI state of a player.
     *
     * @param player The {@code Player} to set.
     * @param ai The new AI state.
     */
    public void setAIHandler(Player player, boolean ai) {
        player.setAI(ai);
    }

    /**
     * Changes the current construction project of a {@code Colony}.
     *
     * Called from BuildQueuePanel
     *
     * @param colony The {@code Colony}
     * @param buildQueue List of {@code BuildableType}
     * @return True if the build queue was changed.
     */
    public CompletableFuture<Boolean> setBuildQueue(Colony colony,
                                 List<BuildableType> buildQueue) {
        if (colony == null
            || buildQueue == null
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        ColonyWas colonyWas = new ColonyWas(colony);
        boolean ret = await(askServer().setBuildQueue(colony, buildQueue));
        if (ret) {
            fireChanges(colonyWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Set a player to be the new current player.
     *
     * @param player The {@code Player} to be the new current player.
     * @return True if the current player changes.
     */
    private CompletableFuture<Boolean> setCurrentPlayer(Player player) {
        if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.MENUS)
            && currentPlayerIsMyPlayer()) {
            getGUI().closeMenus();
        }

        final Game game = getGame();
        game.setCurrentPlayer(player);

        if (getMyPlayer().equals(player)) {
            moveMode = MoveMode.NEXT_ACTIVE_UNIT;
            
            FreeColDebugger.finishDebugRun(getFreeColClient(), false);
            if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.DESYNC)
                && DebugUtils.checkDesyncAction(getFreeColClient())) {
                FreeCol.fatal(logger, "Exiting on desynchronization");
            }

            // Save the game (if it isn't newly loaded)
            if (getFreeColServer() != null
                && game.getTurn().getNumber() > 0) autoSaveGame();

            // Get turn report out quickly before more message display occurs.
            player.removeDisplayedModelMessages();
            displayModelMessages(true, true);

            player.invalidateCanSeeTiles();

            // Check for emigration.
            Europe europe = player.getEurope();
            if (player.hasAbility(Ability.SELECT_RECRUIT)) {
                await(emigration(player, 0, false));
            } else {
                while (player.checkEmigrate()) {
                    await(askEmigrate(europe,
                        Europe.MigrationType.getUnspecificSlot()));
                }
            }
            
            // Wake up human!
            if (!getFreeColClient().getSinglePlayer()) {
                sound("sound.anthem." + player.getNationId());
            }

            player.resetIterators();
            updateGUI(player.getFallbackTile(), false);
        }
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Handle a current player setting.
     *
     * @param currentPlayer The new current {@code Player}.
     */
    public CompletableFuture<Boolean> setCurrentPlayerHandler(Player currentPlayer) {
        return setCurrentPlayer(currentPlayer); // Seems safe to call directly
    }

    /**
     * This player has died.
     *
     * @return True if the player has risen as the undead.
     */
    private CompletableFuture<Boolean> setDead() {
        final FreeColClient fcc = getFreeColClient();
        final Player player = getMyPlayer();
        LogoutReason reason = null;
        if (fcc.getSinglePlayer()) {
            if (player.getPlayerType() == Player.PlayerType.RETIRED) {
                ; // Do nothing, retire routine will quit
            } else {
                if (player.getPlayerType() != Player.PlayerType.UNDEAD
                    && await(getGUI().confirm("defeatedSinglePlayer.text",
                                        "defeatedSinglePlayer.yes", "quit"))) {
                    await(askServer().enterRevengeMode());
                    return CompletableFuture.completedFuture(true);
                }
                reason = LogoutReason.DEFEATED;
            }
        } else {
            if (!await(getGUI().confirm("defeated.text", "defeated.yes", "quit"))) {
                reason = LogoutReason.DEFEATED;
            }
        }
        FreeColDebugger.finishDebugRun(fcc, true);
        if (reason != null) {
            fcc.getConnectController().requestLogout(reason);
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Set a player to be dead.
     *
     * @param dead The dead {@code Player}.
     */
    public void setDeadHandler(Player dead) {
        final Player player = getMyPlayer();
        if (player == dead) {
            invokeLater(() -> setDead());
        } else {
            player.setStance(dead, null);
        }
    }

    /**
     * Informs this controller that a game has been newly loaded.
     *
     * Called from ConnectController.startSavedGame
     *
     * No status returned to connect controller.
     */
    public void setGameConnected () {
        final Player player = getMyPlayer();
        if (player != null) {
            player.refilterModelMessages(getClientOptions());
        }
    }

    /**
     * Sets the export settings of the custom house.
     *
     * Called from WarehouseDialog
     *
     * @param colony The colony with the custom house.
     * @param goodsType The goods for which to set the settings.
     * @return True if the levels were set.
     */
    public CompletableFuture<Boolean> setGoodsLevels(Colony colony, GoodsType goodsType) {
        if (colony == null || goodsType == null) return CompletableFuture.completedFuture(false);

        return askServer().setGoodsLevels(colony,
                                          colony.getExportData(goodsType));
    }

    /**
     * Sets the debug mode to include the extra menu commands.
     *
     * Called from DebugAction
     *
     * @return True, always succeeds.
     */
    public boolean setInDebugMode() {
        FreeColDebugger.enableDebugMode(FreeColDebugger.DebugMode.MENUS);
        getFreeColClient().getConnectController()
            .requestLogout(LogoutReason.RECONNECT);
        return true;
    }

    /**
     * Notify the player that the stance between two players has changed.
     *
     * @param stance The changed {@code Stance}.
     * @param first The first {@code Player}.
     * @param second The second {@code Player}.
     * @return True if the stance change succeeds.
     */
    public boolean setStanceHandler(Stance stance, Player first, Player second) {
        if (stance == null || first == null || second == null) return false;

        final Player player = getMyPlayer();
        Stance old = first.getStance(second);
        try {
            first.setStance(second, stance);
        } catch (IllegalStateException e) {
            logger.log(Level.WARNING, "Illegal stance transition", e);
            return false;
        }
        player.clearNationSummary(second);
        if (player == first && old == Stance.UNCONTACTED) {
            invokeLater(() ->
                sound("sound.event.meet." + second.getNationId()));
        }
        return true;
    }

    /**
     * Spy on a settlement.
     *
     * @param tile A special copy of the {@code Tile} with the settlement.
     */
    public void spySettlementHandler(Tile tile) {
        final Colony colony = tile.getColony();
        if (colony != null) showColonyPanel(colony, null);
    }
    
    /**
     * Trains a unit of a specified type in Europe.
     *
     * Called from NewUnitPanel.
     *
     * @param unitType The type of unit to be trained.
     * @return True if a new unit was trained.
     */
    public CompletableFuture<Boolean> trainUnitInEurope(UnitType unitType) {
        if (!requireOurTurn() || unitType == null) return CompletableFuture.completedFuture(false);

        final Player player = getMyPlayer();
        final Europe europe = player.getEurope();
        if (!player.checkGold(europe.getUnitPrice(unitType))) {
            showInformationPanel(null, "info.notEnoughGold");
            return CompletableFuture.completedFuture(false);
        }

        EuropeWas europeWas = new EuropeWas(europe);
        Unit newUnit = null;
        boolean ret = await(askServer().trainUnitInEurope(unitType))
            && (newUnit = europeWas.getNewUnit()) != null;
        if (ret) {
            fireChanges(europeWas);
            changeView(newUnit, false);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Unload, including dumping cargo.
     *
     * Called from UnloadAction, UnitLabel
     *
     * @param unit The {@code Unit} that is dumping.
     * @return True if the unit unloaded.
     */
    public CompletableFuture<Boolean> unload(Unit unit) {
        if (unit == null || !unit.isCarrier()
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        boolean ret = true;
        Colony colony = unit.getColony();
        if (colony != null) { // In colony, unload units and goods.
            for (Unit u : unit.getUnitList()) {
                if (!await(leaveShip(u))) ret = false;
            }
            for (Goods goods : unit.getGoodsList()) {
                if (!await(unloadCargo(goods, false))) ret = false;
            }
        } else if (unit.isInEurope()) { // In Europe, unload non-boycotted goods
            final Player player = getMyPlayer();
            for (Goods goods : unit.getCompactGoodsList()) {
                if (player.canTrade(goods.getType())) {
                    if (!await(sellGoods(goods))) ret = false;
                }
            }
            if (unit.hasGoodsCargo()) { // Goods left here must be dumped.
                getGUI().showDumpCargoDialog(unit).thenAccept(
                    (List<Goods> goodsList) -> {
                        for (Goods g : goodsList) unloadCargo(g, true);
                    });
                return CompletableFuture.completedFuture(false);
            }
        } else { // Dump goods, units dislike jumping overboard
            for (Goods goods : unit.getGoodsList()) {
                if (!await(unloadCargo(goods, false))) ret = false;
            }
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Unload cargo.  If the unit carrying the cargo is not in a
     * harbour, or if the given boolean is true, the goods will be
     * dumped.
     *
     * Called from CargoPanel, ColonyPanel, EuropePanel.MarketPanel,
     * GUI.showDumpCargoDialog, QuickActionMenu, unload()
     *
     * @param goods The {@code Goods} to unload.
     * @param dump If true, dump the goods.
     * @return True if the unload succeeds.
     */
    public CompletableFuture<Boolean> unloadCargo(Goods goods, boolean dump) {
        if (goods == null || goods.getAmount() <= 0
            || !(goods.getLocation() instanceof Unit)
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        // Find the carrier
        final Unit carrier = (Unit)goods.getLocation();

        // Use Europe-specific routine if needed
        if (carrier.isInEurope()) return sellGoods(goods);

        // Check for a colony
        final Colony colony = carrier.getColony();

        // Unload
        ColonyWas colonyWas = (colony == null) ? null : new ColonyWas(colony);
        UnitWas unitWas = new UnitWas(carrier);
        boolean ret = await(askUnloadGoods(goods.getType(), goods.getAmount(), carrier));
        if (ret) {
            if (!dump) sound("sound.event.unloadCargo");
            fireChanges(colonyWas, unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }

    /**
     * Handle updates.
     *
     * @param objects The {@code FreeColObject}s to update.
     */
    public void updateHandler(List<FreeColObject> objects) {
        final Game game = getGame();
        final Player player = getMyPlayer();
        for (FreeColObject fco : objects) {
            FreeColGameObject fcgo = game.getFreeColGameObject(fco.getId());
            if (fcgo == null) {
                logger.warning("Update of missing FCGO: " + fco.getId());
                continue;
            }
            if (!fcgo.copyIn(fco)) { // Possibly -vis(player)
                logger.warning("Update copy-in failed: " + fco.getId());
                continue;
            } else if (fco instanceof Tile) {
                invokeLater(() -> getGUI().refreshTile((Tile)fco));
            }
        }
        player.invalidateCanSeeTiles(); //+vis(player)
    }

    /**
     * Updates a trade route.
     *
     * Called from TradeRoutePanel(), TradeRoutePanel.newRoute
     *
     * @param route The trade route to update.
     * @return True if the trade route was updated.
     */
    public CompletableFuture<Boolean> updateTradeRoute(TradeRoute route) {
        if (route == null) return CompletableFuture.completedFuture(false);

        return askServer().updateTradeRoute(route);
    }

    /**
     * The player has won!
     *
     * @param quit If true, leave this game and start a new one.
     * @return True.
     */
    private CompletableFuture<Boolean> victory(Boolean quit) {
        if (quit) {
            invokeLater(() ->
                getFreeColClient().getConnectController().newGame());
        } else {
            await(askServer().continuePlaying());
        }
        return CompletableFuture.completedFuture(true);
    }
        
    /**
     * Tell a unit to wait.
     *
     * Called from WaitAction.
     *
     * @return True, this can not fail.
     */
    public boolean waitUnit() {
        if (!requireOurTurn()) return false;

        updateGUI(null, true);
        return true;
    }

    /**
     * Moves a {@code Unit} to a {@code WorkLocation}.
     *
     * Called from ColonyPanel.tryWork, UnitLabel
     *
     * @param unit The {@code Unit}.
     * @param workLocation The new {@code WorkLocation}.
     * @return True if the unit is now working at the new work location.
     */
    public CompletableFuture<Boolean> work(Unit unit, WorkLocation workLocation) {
        if (unit == null
            || workLocation == null
            || !requireOurTurn()) return CompletableFuture.completedFuture(false);

        StringTemplate template;
        if (unit.getStudent() != null
            && !await(getGUI().confirmAbandonEducation(unit, false))) return CompletableFuture.completedFuture(false);

        Colony colony = workLocation.getColony();
        Tile tile = workLocation.getWorkTile();
        if (tile != null) {
            if (tile.hasLostCityRumour()) {
                showInformationPanel(null, "tileHasRumour");
                return CompletableFuture.completedFuture(false);
            }
            if (!unit.getOwner().owns(tile)) {
                if (!await(claimTile(tile, colony))) return CompletableFuture.completedFuture(false);
            }
        }

        // Try to change the work location.
        ColonyWas colonyWas = new ColonyWas(colony);
        UnitWas unitWas = new UnitWas(unit);
        boolean ret = await(askServer().work(unit, workLocation))
            && unit.getLocation() == workLocation;
        if (ret) {
            fireChanges(colonyWas, unitWas);
            updateGUI(null, false);
        }
        return CompletableFuture.completedFuture(ret);
    }
}
