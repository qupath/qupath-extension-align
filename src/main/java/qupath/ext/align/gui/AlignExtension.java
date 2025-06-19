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

import org.controlsfx.control.action.Action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.align.gui.interactive.AlignCommand;
import qupath.lib.common.Version;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.extensions.QuPathExtension;

import java.util.ResourceBundle;

/**
 * Extension to interactively align images.
 */
public class AlignExtension implements QuPathExtension {

	private static final ResourceBundle resources = Utils.getResources();
	private static final Logger logger = LoggerFactory.getLogger(AlignExtension.class);
	private static final String EXTENSION_NAME = resources.getString("Extension.name");
	private static final String EXTENSION_DESCRIPTION = resources.getString("Extension.description");
	private static final Version EXTENSION_QUPATH_VERSION = Version.parse("v0.6.0");
	private boolean isInstalled = false;
	
    @Override
    public void installExtension(QuPathGUI qupath) {
		if (isInstalled) {
			logger.debug("{} is already installed. Skipping installation", getName());
			return;
		}
		isInstalled = true;

    	qupath.installActions(ActionTools.getAnnotatedActions(new ExperimentalCommands(qupath)));
    }

    @Override
    public String getName() {
        return EXTENSION_NAME;
    }

    @Override
    public String getDescription() {
        return EXTENSION_DESCRIPTION;
    }
	
	@Override
	public Version getQuPathVersion() {
		return EXTENSION_QUPATH_VERSION;
	}

	private static class ExperimentalCommands {

		@ActionMenu("Menu.Analyze>Alignment")
		public final Action imageAlignmentAction;

		private ExperimentalCommands(QuPathGUI qupath) {
			imageAlignmentAction = ActionTools.createAction(new AlignCommand(qupath));
			imageAlignmentAction.setText(resources.getString("Extension.interactiveImageAlignment"));
			imageAlignmentAction.setLongText(resources.getString("Extension.interactiveImageAlignmentDescription"));
		}
	}
}
