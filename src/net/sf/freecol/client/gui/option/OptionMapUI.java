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

package net.sf.freecol.client.gui.option;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import net.sf.freecol.common.option.BooleanOption;
import net.sf.freecol.common.option.FileOption;
import net.sf.freecol.common.option.IntegerOption;
import net.sf.freecol.common.option.LanguageOption;
import net.sf.freecol.common.option.Option;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.option.OptionMap;
import net.sf.freecol.common.option.PercentageOption;
import net.sf.freecol.common.option.RangeOption;
import net.sf.freecol.common.option.SelectOption;

/**
 * This class provides visualization for an {@link OptionMap}. In order to
 * enable values to be both seen and changed.
 */
public final class OptionMapUI extends JPanel implements OptionUpdater {
    private static final Logger logger = Logger.getLogger(OptionMapUI.class.getName());




    private final OptionUpdater[] optionUpdaters;
    
    private final HashMap<String, JComponent> optionUIs;

    private final JTabbedPane tb;


    /**
     * Creates a new <code>OptionMapUI</code> for the given
     * <code>OptionMap</code>. This is the same as using
     * {@link #OptionMapUI(OptionMap, boolean)} with
     * <code>editable == true</code>.
     * 
     * @param option The <code>OptionMap</code> to make a user interface for.
     */
    public OptionMapUI(OptionMap option) {
        this(option, true);
    }

    /**
     * Creates a new <code>OptionMapUI</code> for the given
     * <code>OptionMap</code>.
     * 
     * @param option The <code>OptionMap</code> to make a user interface for.
     */
    public OptionMapUI(OptionMap option, boolean editable) {
        super(new BorderLayout());

        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.setOpaque(false);
        
        optionUIs = new HashMap<String, JComponent>();

        tb = new JTabbedPane(JTabbedPane.TOP);
        tb.setOpaque(false);

        ArrayList<JComponent> ou = new ArrayList<JComponent>();
        Iterator<Option> it = option.iterator();
        while (it.hasNext()) {
            Option o = it.next();

            if (o instanceof OptionGroup) {
                JComponent c = new OptionGroupUI((OptionGroup) o, editable, 1, optionUIs);
                c.setOpaque(true);
                ou.add(c);
                JScrollPane scroll = new JScrollPane(c, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                scroll.getVerticalScrollBar().setUnitIncrement(16);
                scroll.setBorder(BorderFactory.createEmptyBorder());
                c.setBorder(BorderFactory.createEmptyBorder(OptionGroupUI.H_GAP - 5, OptionGroupUI.H_GAP, 0,
                        OptionGroupUI.H_GAP));
                c = scroll;
                tb.addTab(o.getName(), null, c, o.getShortDescription());
            } else if (o instanceof BooleanOption) {
                JComponent c = new BooleanOptionUI((BooleanOption) o, editable);
                northPanel.add(c);
                ou.add(c);
                if (!o.getId().equals(Option.NO_ID)) {
                    optionUIs.put(o.getId(), c);
                }
            } else if (o instanceof FileOption) {
                final FileOptionUI iou = new FileOptionUI((FileOption) o, editable);
                northPanel.add(iou);
                ou.add(iou);
                if (!o.getId().equals(Option.NO_ID)) {
                    optionUIs.put(o.getId(), iou);
                }
            } else if (o instanceof PercentageOption) {
                JComponent c = new PercentageOptionUI((PercentageOption) o, editable);
                northPanel.add(c);
                ou.add(c);
                if (!o.getId().equals(Option.NO_ID)) {
                    optionUIs.put(o.getId(), c);
                }
            } else if (o instanceof IntegerOption) {
                JComponent c = new IntegerOptionUI((IntegerOption) o, editable);
                northPanel.add(c);
                ou.add(c);
                if (!o.getId().equals(Option.NO_ID)) {
                    optionUIs.put(o.getId(), c);
                }
            } else if (o instanceof SelectOption) {
                JComponent c = new SelectOptionUI((SelectOption) o, editable);
                northPanel.add(c);
                ou.add(c);
                if (!o.getId().equals(Option.NO_ID)) {
                    optionUIs.put(o.getId(), c);
                }
            } else if (o instanceof RangeOption) {
                JComponent c = new RangeOptionUI((RangeOption) o, editable);
                northPanel.add(c);
                ou.add(c);
                if (!o.getId().equals(Option.NO_ID)) {
                    optionUIs.put(o.getId(), c);
                }
            } else if (o instanceof LanguageOption) {
                System.out.println("found languageOption");
                JComponent c = new LanguageOptionUI((LanguageOption) o, editable);
                northPanel.add(c);
                ou.add(c);
                if (!o.getId().equals(Option.NO_ID)) {
                    optionUIs.put(o.getId(), c);
                }
            } else {
                logger.warning("Unknown option.");
            }
        }
        optionUpdaters = ou.toArray(new OptionUpdater[0]);

        add(northPanel, BorderLayout.NORTH);
        if (tb.getTabCount() > 0) {
            add(tb, BorderLayout.CENTER);
        }

        setOpaque(false);
    }

    /**
     * Unregister <code>PropertyChangeListener</code>s.
     */
    public void unregister() {
        for (int i = 0; i < optionUpdaters.length; i++) {
            optionUpdaters[i].unregister();
        }
    }

    /**
     * Updates the value of the {@link Option} this object keeps.
     */
    public void updateOption() {
        for (int i = 0; i < optionUpdaters.length; i++) {
            optionUpdaters[i].updateOption();
        }
    }
    
    public JComponent getOptionUI(String key) {
        return optionUIs.get(key);
    }

    /**
     * Reset with the value from the option.
     */
    public void reset() {
        for (int i = 0; i < optionUpdaters.length; i++) {
            optionUpdaters[i].reset();
        }
    }
}
