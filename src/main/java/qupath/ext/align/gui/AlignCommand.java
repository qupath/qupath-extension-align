/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.ext.align.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;

import java.io.IOException;
import java.util.Objects;

/**
 * A command to start an {@link AlignWindow}.
 * 
 * @author Pete Bankhead
 */
class AlignCommand implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(AlignCommand.class);
	private final QuPathGUI qupath;
	private AlignWindow alignWindow;
	
	/**
	 * Create the command.
	 *
	 * @param qupath the QuPath GUI that should own this command
	 * @throws NullPointerException if the provided parameter is null
	 */
	public AlignCommand(QuPathGUI qupath) {
		this.qupath = Objects.requireNonNull(qupath);
	}

	@Override
	public void run() {
		if (alignWindow == null) {
            try {
				logger.debug("Image alignment window of {} does not exit. Creating it", qupath);
                alignWindow = new AlignWindow(qupath);
            } catch (IOException e) {
				logger.error("Error while creating image overlay alignment window", e);
				return;
            }
        }
		alignWindow.show();
		alignWindow.requestFocus();
	}
}