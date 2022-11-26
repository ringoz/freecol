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
 *  MERCHANTLIMIT or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.model.mission;

import net.sf.freecol.common.util.Introspector;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.model.Game;


/**
 * The MissionManager keeps track of all missions defined.
 *
 * @see net.sf.freecol.client.gui.action.ActionManager
 */
public class MissionManager {

    private static final Logger logger = Logger.getLogger(MissionManager.class.getName());

    private static final Map<String, Class<? extends Mission>> missionMap
                                                     = new HashMap<>();

    static {
            missionMap.put(CompoundMission.TAG,
                           CompoundMission.class);
            missionMap.put(GoToMission.TAG,
                           GoToMission.class);
            missionMap.put(ImprovementMission.TAG,
                           ImprovementMission.class);
    }


    /**
     * Returns true if the given String is a known mission tag.
     *
     * @param tag a {@code String} value
     * @return a {@code boolean} value
     */
    public static boolean isMissionTag(String tag) {
        return missionMap.containsKey(tag);
    }

    /**
     * Returns a new Mission read from the input stream if possible,
     * and null if not.
     *
     * @param game a {@code Game} value
     * @param xr a {@code FreeColXMLReader} value
     * @return a {@code Mission} value
     * @exception XMLStreamException if an error occurs
     */
    public static Mission getMission(Game game,
                                     FreeColXMLReader xr) throws XMLStreamException {
        String tag = xr.getLocalName();
        Class<? extends Mission> c = missionMap.get(tag);
        if (c == null) {
            logger.warning("Unknown type of mission: '" + tag + "'.");
            xr.nextTag();
            return null;
        } else {
            try {
                return Introspector.instantiate(c, new Class[] { Game.class, FreeColXMLReader.class }, new Object[] { game, xr });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to instatiate mission with tag: "
                    + tag, e);
                return null;
            }
        }
    }
}
