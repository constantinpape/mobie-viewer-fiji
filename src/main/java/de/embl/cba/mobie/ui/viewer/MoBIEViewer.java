package de.embl.cba.mobie.ui.viewer;

import com.google.gson.GsonBuilder;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.stream.JsonReader;
import de.embl.cba.bdv.utils.BdvUtils;
import de.embl.cba.mobie.dataset.Datasets;
import de.embl.cba.mobie.dataset.DatasetsParser;
import de.embl.cba.mobie.image.SourcesModel;
import de.embl.cba.mobie.bookmark.Bookmark;
import de.embl.cba.mobie.bookmark.BookmarksJsonParser;
import de.embl.cba.mobie.bookmark.BookmarksManager;
import de.embl.cba.tables.FileAndUrlUtils;
import de.embl.cba.mobie.utils.Utils;
import ij.WindowManager;
import ij.gui.NonBlockingGenericDialog;
import net.imglib2.realtransform.AffineTransform3D;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;

import static de.embl.cba.mobie.utils.FileAndUrlUtils.getName;

public class MoBIEViewer
{
	public static final String PROTOTYPE_DISPLAY_VALUE = "01234567890123456789";

	private final SourcesPanel sourcesPanel;
	private final ActionPanel actionPanel;
	private final SourcesModel sourcesModel;
	private final MoBIEOptions options;
	private String dataset;
	private final String projectBaseLocation; // without branch, pure github address
	private String projectLocation; // with branch, actual url to data
	private String imagesLocation; // selected dataset
	private String tablesLocation;

	private int frameWidth;
	private BookmarksManager bookmarksManager;
	private Datasets datasets;
	private final double[] levelingVector;
	private final JFrame jFrame;
	private String projectName;
	private AffineTransform3D defaultNormalisedViewerTransform;

	public MoBIEViewer( String projectLocation ) throws HeadlessException
	{
		this( projectLocation, MoBIEOptions.options() );
	}

	@Deprecated
	public MoBIEViewer(
			String projectLocation,
			String tablesLocation ) throws HeadlessException
	{
		this( projectLocation, MoBIEOptions.options().tableDataLocation( tablesLocation ) );
	}

	public MoBIEViewer(
			String projectBaseLocation,
			MoBIEOptions options )
	{
		this.projectBaseLocation = projectBaseLocation;
		this.options = options;

		this.projectName = getName( this.projectBaseLocation );

		configureRootLocations();
		appendSpecificDatasetLocations();

		sourcesModel = new SourcesModel( imagesLocation, options.values.getImageDataStorageModality(), tablesLocation );
		sourcesPanel = new SourcesPanel( sourcesModel, projectName );

		bookmarksManager = fetchBookmarks( projectLocation );
		levelingVector = fetchLeveling( imagesLocation );

		actionPanel = new ActionPanel( this );

		jFrame = new JFrame( "MoBIE: " + projectName + "-" + dataset );

		// open bdv and show default bookmark (this will also initialise the bdv in sourcesPanel)
		bookmarksManager.setView( "default" );
		actionPanel.setBdvAndInstallBehavioursAndPopupMenu( sourcesPanel.getBdv() );
		defaultNormalisedViewerTransform = Utils.createNormalisedViewerTransform( sourcesPanel.getBdv(), BdvUtils.getBdvWindowCenter( sourcesPanel.getBdv() ) );

		SwingUtilities.invokeLater( () ->
		{
			showFrame( jFrame );
			setLogWindowPositionAndSize( jFrame );
			sourcesPanel.setBdvWindowPositionAndSize( jFrame );
		});
	}

	public MoBIEOptions getOptions()
	{
		return options;
	}

	public AffineTransform3D getDefaultNormalisedViewerTransform()
	{
		return defaultNormalisedViewerTransform;
	}

	public double[] getLevelingVector()
	{
		return levelingVector;
	}

	public SourcesModel getSourcesModel()
	{
		return sourcesModel;
	}

	public String getProjectLocation()
	{
		return projectBaseLocation; // without branch information, which is stored in the options
	}

	public String getDataset()
	{
		return dataset;
	}

	public ArrayList< String > getDatasets()
	{
		return datasets.datasets;
	}

	public BookmarksManager getBookmarksManager()
	{
		return bookmarksManager;
	}

	private double[] fetchLeveling( String dataLocation )
	{
		final String levelingFile = FileAndUrlUtils.combinePath( dataLocation, "misc/leveling.json" );
		try
		{
			InputStream is = FileAndUrlUtils.getInputStream( levelingFile );
			final JsonReader reader = new JsonReader( new InputStreamReader( is, "UTF-8" ) );
			final GsonBuilder gsonBuilder = new GsonBuilder();
			LinkedTreeMap linkedTreeMap = gsonBuilder.create().fromJson( reader, Object.class );
			ArrayList< Double >  normalVector = ( ArrayList< Double > ) linkedTreeMap.get( "NormalVector" );
			final double[] doubles = normalVector.stream().mapToDouble( Double::doubleValue ).toArray();
			return doubles;
		}
		catch ( Exception e )
		{
			return null; // new double[]{0.70,0.56,0.43};
		}
	}

	public void appendSpecificDatasetLocations()
	{
		this.datasets = new DatasetsParser().fetchProjectDatasets( projectLocation );
		this.dataset = options.values.getDataset();

		if ( dataset == null )
		{
			dataset = datasets.defaultDataset;
		}

		projectLocation = FileAndUrlUtils.combinePath( projectLocation, dataset );
		imagesLocation = FileAndUrlUtils.combinePath( imagesLocation, dataset );
		tablesLocation = FileAndUrlUtils.combinePath( tablesLocation, dataset );

		Utils.log( "Fetching project from: " + projectLocation );
		Utils.log( "Fetching images from: " + imagesLocation );
		Utils.log( "Image data storage modality: " + options.values.getImageDataStorageModality() );
		Utils.log( "Fetching tables from: " + tablesLocation );
	}

	public void configureRootLocations( )
	{
		this.projectLocation = projectBaseLocation;
		this.imagesLocation = options.values.getImageDataLocation() != null ? options.values.getImageDataLocation() : projectBaseLocation;
		this.tablesLocation = options.values.getTableDataLocation() != null ? options.values.getTableDataLocation() : projectBaseLocation;

		projectLocation = FileAndUrlUtils.removeTrailingSlash( projectLocation );
		imagesLocation = FileAndUrlUtils.removeTrailingSlash( imagesLocation );
		tablesLocation = FileAndUrlUtils.removeTrailingSlash( tablesLocation );

		projectLocation = adaptUrl( projectLocation, options.values.getProjectBranch() ) + "/data";
		imagesLocation = adaptUrl( imagesLocation, options.values.getProjectBranch() ) + "/data";
		tablesLocation = adaptUrl( tablesLocation, options.values.getTableDataBranch() ) + "/data";
	}

	public String adaptUrl( String url, String projectBranch )
	{
		if ( url.contains( "github.com" ) )
		{
			url = url.replace( "github.com", "raw.githubusercontent.com" );
			url += "/" + projectBranch;
		}
		return url;
	}

	public BookmarksManager fetchBookmarks( String location )
	{
		BookmarksJsonParser bookmarkParser = new BookmarksJsonParser(location);
		Map< String, Bookmark > nameToBookmark = bookmarkParser.getDefaultBookmarks();

		return new BookmarksManager( sourcesPanel, nameToBookmark, bookmarkParser);
	}

	public void setLogWindowPositionAndSize( JFrame jFrame )
	{
		final Frame log = WindowManager.getFrame( "Log" );
		if (log != null) {
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			final int logWindowHeight = screenSize.height - ( jFrame.getLocationOnScreen().y + jFrame.getHeight() + 20 );
			log.setSize( jFrame.getWidth(), logWindowHeight  );
			log.setLocation( jFrame.getLocationOnScreen().x, jFrame.getLocationOnScreen().y + jFrame.getHeight() );
		}
	}

	public void showFrame( JFrame frame )
	{
		JSplitPane splitPane = new JSplitPane();
		splitPane.setOrientation( JSplitPane.VERTICAL_SPLIT );
		final int numModalities = actionPanel.getSortedModalities().size();
		final int actionPanelHeight = ( numModalities + 7 ) * 40;
		splitPane.setDividerLocation( actionPanelHeight );
		splitPane.setTopComponent( actionPanel );
		splitPane.setBottomComponent( sourcesPanel );
		splitPane.setAutoscrolls( true );
		frameWidth = 600;
		frame.setPreferredSize( new Dimension( frameWidth, actionPanelHeight + 200 ) );
		frame.getContentPane().setLayout( new GridLayout() );
		frame.getContentPane().add( splitPane );

		frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
		frame.pack();
		frame.setVisible( true );
	}

	public SourcesPanel getSourcesPanel()
	{
		return sourcesPanel;
	}

	public ActionPanel getActionPanel()
	{
		return actionPanel;
	}

	public void close()
	{
		sourcesPanel.removeAllSourcesFromPanelAndViewers();
		sourcesPanel.getBdv().close();
		jFrame.dispose();
	}
}
