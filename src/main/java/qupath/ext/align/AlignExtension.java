/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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

package qupath.ext.align;

import org.controlsfx.control.action.Action;

import qupath.ext.align.gui.InteractiveImageAlignmentCommand;
import qupath.lib.common.Version;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * Extension to make more experimental commands present in the GUI.
 */
public class AlignExtension implements QuPathExtension, GitHubProject {

	public static class ExperimentalCommands {

		@ActionMenu("Menu.Analyze>Alignment")
		public final Action actionInteractiveAlignment;

		private ExperimentalCommands(QuPathGUI qupath) {
			var interactiveAlignment = new InteractiveImageAlignmentCommand(qupath);
			actionInteractiveAlignment = qupath.createProjectAction(project -> interactiveAlignment.run());
			actionInteractiveAlignment.setText("Interactive image alignment");
			actionInteractiveAlignment.setLongText("Experimental command to interactively align images using an Affine transform. \n" +
					"This is currently not terribly useful in itself, but may be helpful as part of more complex scripting workflows");
		}
		
	}
	
	
    @Override
    public void installExtension(QuPathGUI qupath) {

    	qupath.installActions(ActionTools.getAnnotatedActions(new ExperimentalCommands(qupath)));
    }

    @Override
    public String getName() {
        return "Align extension";
    }

    @Override
    public String getDescription() {
        return "Adds the 'Interactive image alignment' command";
    }

	@Override
	public GitHubRepo getRepository() {
		return GitHubRepo.create(getName(), "qupath", "qupath-extension-align");
	}
	
	@Override
	public Version getQuPathVersion() {
		return Version.parse("0.5.0");
	}
	
}
