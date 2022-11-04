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

package net.sf.freecol.client.gui.video;

import java.awt.Dimension;

import javax.swing.JPanel;

import net.sf.freecol.common.resources.Video;


/**
 * A component for playing video.
 */
public class VideoComponent extends JPanel {

    /**
     * Creates a component for displaying the given video.
     *
     * @param video The {@code Video} to be displayed.
     * @param mute If true, silence the video.
     * @param maximumSize An upper bound on the size of the video pane.
     */
    public VideoComponent(Video video, boolean mute, Dimension maximumSize) {
    }

    /**
     * Start playing the video.
     */
    public void play() {
        getKeyListeners()[0].keyReleased(null);
    }

    /**
     * Stop playing the video.
     */
    public void stop() {
    }
}
