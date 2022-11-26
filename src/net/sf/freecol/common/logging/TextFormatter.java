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

package net.sf.freecol.common.logging;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;


/**
 * Formats a log record's data into human-readable text.
 */
final class TextFormatter extends Formatter {

    /**
     * The constructor to use.
     */
    public TextFormatter() {
    }

    /**
     * Formats the given log record's data into human-readable text.
     * 
     * @param record The log record whose data needs to be formatted.
     * @return The log record's data as a string.
     */
    @Override
    public String format(LogRecord record) {
        StringBuilder result = new StringBuilder();
        result.append(record.getLevel().getName())
            .append(": ").append(record.getMessage().replaceAll("\n", "\n\t"))
            .append('\n');
        if (record.getThrown() != null) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintStream ps = new PrintStream(baos)) {
                record.getThrown().printStackTrace(ps);
            }
            result.append(baos.toString());
        }

        return result.toString();
    }
}
