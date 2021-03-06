package de.embl.cba.mobie.ui.command;

import de.embl.cba.mobie.ui.viewer.MoBIEOptions;
import de.embl.cba.mobie.ui.viewer.MoBIEViewer;
import net.imagej.ImageJ;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = "Plugins>MoBIE>Open>Advanced>Open MoBIE Project Branch..." )
public class OpenMoBIEProjectBranchCommand implements Command
{
	@Parameter ( label = "Project Location" )
	public String projectLocation = "https://github.com/platybrowser/platybrowser";

	@Parameter ( label = "Project Branch" )
	public String projectBranch = "master";

	@Override
	public void run()
	{
		final MoBIEViewer moBIEViewer = new MoBIEViewer(
				projectLocation,
				MoBIEOptions.options().gitProjectBranch( projectBranch ) );
	}

	public static void main(final String... args)
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		final MoBIEViewer moBIEViewer = new MoBIEViewer(
				"https://github.com/mobie/covid-tomo-datasets",
				MoBIEOptions.options().gitProjectBranch( "norm-bookmarks" ) );
	}
}
