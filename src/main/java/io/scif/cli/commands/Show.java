/*
 * #%L
 * SCIFIO library for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2011 - 2017 Board of Regents of the University of
 * Wisconsin-Madison.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package io.scif.cli.commands;

import io.scif.BufferedImagePlane;
import io.scif.FormatException;
import io.scif.ImageMetadata;
import io.scif.Plane;
import io.scif.Reader;
import io.scif.Writer;
import io.scif.cli.SCIFIOToolCommand;
import io.scif.common.DataTools;
import io.scif.gui.AWTImageTools;
import io.scif.gui.GUIService;
import io.scif.io.ByteArrayHandle;
import io.scif.io.Location;
import io.scif.io.RandomAccessInputStream;
import io.scif.services.FormatService;
import io.scif.services.InitializeService;
import io.scif.services.LocationService;
import io.scif.util.AsciiImage;
import io.scif.util.FormatTools;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ProgressMonitor;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Opens a dataset for viewing. An ascii version can be printed as output for
 * convenience on headless systems, otherwise a simple AWT pane is opened, per
 * {@link ImageViewer}.
 * 
 * @author Mark Hiner
 */
@Plugin(type = SCIFIOToolCommand.class)
public class Show extends AbstractReaderCommand {

	// -- Fields --

	@Parameter
	private LocationService locationService;

	private List<BufferedImage> bImages;

	// -- Arguments --

	@Argument(metaVar = "file", index = 0, usage = "image file to display")
	private String file;

	@Argument(index = 1, multiValued = true)
	private final List<String> arguments = new ArrayList<String>();

	// -- Options --

	@Option(
		name = "-A",
		aliases = "--ascii",
		usage = "display an ascii rendering of the image. useful on headless systems")
	private boolean ascii;

	@Option(name = "-b", aliases = "--thumbs",
		usage = "read thumbnails instead of normal pixels")
	private boolean thumbs;

	@Option(name = "-n", aliases = "--normalize",
		usage = "normalize floating point images (may result in loss of precision)")
	private boolean normalize;

	@Option(
		name = "-p",
		aliases = "--preload",
		usage = "pre-load entire file into a buffer. reduces read time, increases memory use.")
	private boolean preload;

	@Option(name = "-w", aliases = "--swap",
		usage = "override the default input dimension order")
	private List<String> swap;

	@Option(name = "-u", aliases = "--shuffle",
		usage = "override the default output dimension order")
	private boolean shuffle;

	// --info

	// -- AbstractSCIFIOToolCommand API --

	@Override
	protected void run() throws CmdLineException {
		try {
			mapLocation();
		}
		catch (final IOException e) {
			throw new CmdLineException(null, e.getMessage());
		}

		final Reader reader = makeReader(file);
		showPixels(reader);
	}

	@Override
	protected String description() {
		return "command line tool for displaying a dataset.";
	}

	@Override
	protected String getName() {
		return "show";
	}

	@Override
	protected List<String> getExtraArguments() {
		return arguments;
	}

	@Override
	protected void validateParams() throws CmdLineException {
		if (file == null) {
			throw new CmdLineException(null, "Argument \"file\" is required");
		}
	}

	// -- AbstractReaderCommand API --

	@Override
	protected Plane processPlane(final Reader reader, Plane plane,
		final int imageIndex, final long planeIndex, final long planeNo,
		final long[] planeMin, final long[] planeMax) throws CmdLineException
	{
		try {
			// open the plane
			if (thumbs) {
				plane = reader.openThumbPlane(imageIndex, planeIndex);
			}
			else {
				if (plane == null) {
					plane =
						reader.openPlane(imageIndex, planeIndex, planeMin, planeMax,
							getConfig());
				}
				else {
					plane =
						reader.openPlane(imageIndex, planeIndex, plane, planeMin, planeMax,
							getConfig());
				}
			}
		}
		catch (final FormatException e) {
			throw new CmdLineException(null, e.getMessage());
		}
		catch (final IOException e) {
			throw new CmdLineException(null, e.getMessage());
		}

		final int pixelType = reader.getMetadata().get(imageIndex).getPixelType();
		final boolean littleEndian =
			reader.getMetadata().get(imageIndex).isLittleEndian();

		// Convert the byte array to an appropriately typed data array
		Object pix =
			DataTools.makeDataArray(plane.getBytes(), FormatTools
				.getBytesPerPixel(pixelType), FormatTools.isFloatingPoint(pixelType),
				littleEndian);

		// Convert the data array back to a simple byte array, normalizing
		// floats and doubles if needed
		byte[] bytes = null;
		if (pix instanceof short[]) {
			bytes = DataTools.shortsToBytes((short[]) pix, littleEndian);
		}
		else if (pix instanceof int[]) {
			bytes = DataTools.intsToBytes((int[]) pix, littleEndian);
		}
		else if (pix instanceof long[]) {
			bytes = DataTools.longsToBytes((long[]) pix, littleEndian);
		}
		else if (pix instanceof float[]) {
			if (normalize) {
				pix = DataTools.normalizeFloats((float[]) pix);
			}
			bytes = DataTools.floatsToBytes((float[]) pix, littleEndian);
		}
		else if (pix instanceof double[]) {
			if (normalize) {
				pix = DataTools.normalizeDoubles((double[]) pix);
			}
			bytes = DataTools.doublesToBytes((double[]) pix, littleEndian);
		}
		else if (pix instanceof byte[]) {
			bytes = (byte[]) pix;
		}

		final ImageMetadata meta = reader.getMetadata().get(imageIndex);
		try {
			// Open the potentially modified byte array as a buffered image and
			// add it to the list
			bImages.add(AWTImageTools.openImage(plane, bytes, reader, meta
				.getAxesLengthsPlanar(), imageIndex));
		}
		catch (final FormatException e) {
			throw new CmdLineException(null, e.getMessage());
		}
		catch (final IOException e) {
			throw new CmdLineException(null, e.getMessage());
		}

		if (bImages.get((int) planeNo) == null) {
			warn("\t************ Failed to read plane #" + planeNo + " ************");
		}
		if (reader.getMetadata().get(imageIndex).isIndexed() &&
			plane.getColorTable() == null)
		{
			warn("\t************ no LUT for plane #{}" + planeNo + " ************");
		}

		// check for pixel type mismatch
		final int pixType = AWTImageTools.getPixelType(bImages.get((int) planeNo));
		if (pixType != pixelType && pixType != pixelType + 1) {
			info("\tPlane #" + planeNo + ": pixel type mismatch: " +
				FormatTools.getPixelTypeString(pixType) + "/" +
				FormatTools.getPixelTypeString(pixelType));
		}
		return plane;
	}

	// -- SCIFIOToolCommand methods --

	@Override
	public String commandName() {
		return "show";
	}

	// -- Helper methods --

	/**
	 * Opens all requested image planes as buffered images, and either converts
	 * them to {@link AsciiImage}s or opens the pixels into an {@link ImageViewer}
	 * .
	 * 
	 * @param reader Reader to use for opening pixels
	 */
	private void showPixels(final Reader reader) throws CmdLineException {
		bImages = new ArrayList<BufferedImage>();

		read(reader);

		if (ascii) {
			// Print an ascii rendering of the image
			for (int i = 0; i < bImages.size(); i++) {
				final BufferedImage img = bImages.get(i);
				info("");
				info("Image #" + i + ":");
				info(new AsciiImage(img).toString());
			}
		}
		else {
			// display pixels in image viewer
			info("");
			info("Launching image viewer");
			final ImageViewer viewer = new ImageViewer(getContext(), false);
			viewer.setImages(reader, bImages
				.toArray(new BufferedImage[bImages.size()]));
			viewer.setVisible(true);
		}
	}

	/**
	 * Helper method to map the input file to a specific {@link Location} if
	 * desired. Also performs preloading if requested.
	 */
	private void mapLocation() throws IOException {
		if (getMap() != null) locationService.mapId(file, getMap());
		else if (preload) {
			final RandomAccessInputStream f =
				new RandomAccessInputStream(getContext(), file);
			final int len = (int) f.length();
			info("Caching " + len + " bytes:");
			final byte[] b = new byte[len];
			final int blockSize = 8 * 1024 * 1024; // 8 MB
			int read = 0, left = len;
			while (left > 0) {
				final int r = f.read(b, read, blockSize < left ? blockSize : left);
				read += r;
				left -= r;
				final float ratio = (float) read / len;
				final int p = (int) (100 * ratio);
				info("\tRead " + read + " bytes (" + p + "% complete)");
			}
			f.close();
			final ByteArrayHandle preloaded = new ByteArrayHandle(b);
			locationService.mapFile(file, preloaded);
		}
	}

	/**
	 * A basic renderer for image data.
	 *
	 * @author Curtis Rueden
	 * @author Mark Hiner
	 */
	private class ImageViewer extends JFrame implements ActionListener,
		ChangeListener, KeyListener, MouseMotionListener, Runnable, WindowListener
	{

		// -- Constants --

		@Parameter
		private Context context;

		@Parameter
		private LogService logService;

		@Parameter
		private FormatService formatService;

		@Parameter
		private InitializeService initializeService;

		@Parameter
		private GUIService guiService;

		private static final String TITLE = "SCIFIO Viewer";

		private static final char ANIMATION_KEY = ' ';

		// -- Fields --

		/** Current format reader. */
		private Reader myReader;

		/** Current format writer. */
		private Writer myWriter;

		private final JPanel pane;

		private final ImageIcon icon;

		private final JLabel iconLabel;

		private final JPanel sliderPanel;

		private final JSlider nSlider;

		private final JLabel probeLabel;

		private final JMenuItem fileView, fileSave;

		private String filename;

		private BufferedImage[] images;

		private boolean anim = false;

		private int fps = 10;

		private boolean canCloseReader = true;

		// -- Constructor --

		/** Constructs an image viewer. */
		public ImageViewer(final Context context) {
			super(TITLE);
			context.inject(this);
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			addWindowListener(this);

			// content pane
			pane = new JPanel();
			pane.setLayout(new BorderLayout());
			setContentPane(pane);
			setSize(350, 350); // default size

			// navigation sliders
			sliderPanel = new JPanel();
			sliderPanel.setVisible(false);
			sliderPanel.setBorder(new EmptyBorder(5, 3, 5, 3));
			sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.Y_AXIS));
			pane.add(BorderLayout.SOUTH, sliderPanel);

			final JPanel nPanel = new JPanel();
			nPanel.setLayout(new BoxLayout(nPanel, BoxLayout.X_AXIS));
			sliderPanel.add(nPanel);
			sliderPanel.add(Box.createVerticalStrut(2));

			nSlider = new JSlider(1, 1);
			nSlider.setEnabled(false);
			nSlider.addChangeListener(this);
			nPanel.add(new JLabel("N"));
			nPanel.add(Box.createHorizontalStrut(3));
			nPanel.add(nSlider);

			final JPanel ztcPanel = new JPanel();
			ztcPanel.setLayout(new BoxLayout(ztcPanel, BoxLayout.X_AXIS));
			sliderPanel.add(ztcPanel);

			// image icon
			final BufferedImage dummy = AWTImageTools.makeImage(new byte[1][1], 1, 1,
				false);
			icon = new ImageIcon(dummy);
			iconLabel = new JLabel(icon, SwingConstants.LEFT);
			iconLabel.setVerticalAlignment(SwingConstants.TOP);
			pane.add(new JScrollPane(iconLabel));

			// cursor probe
			probeLabel = new JLabel(" ");
			probeLabel.setHorizontalAlignment(SwingConstants.CENTER);
			probeLabel.setBorder(new BevelBorder(BevelBorder.RAISED));
			pane.add(BorderLayout.NORTH, probeLabel);
			iconLabel.addMouseMotionListener(this);

			// menu bar
			final JMenuBar menubar = new JMenuBar();
			// FIXME: currently the menu bar is disabled to restrict the use of
			// ImageViewer to the Show command. We could attempt to get this
			// implementation working nicely, or just convert to an IJ2
			// implementation.
			// setJMenuBar(menubar);

			final JMenu file = new JMenu("File");
			file.setMnemonic('f');
			menubar.add(file);
			final JMenuItem fileOpen = new JMenuItem("Open...");
			fileOpen.setMnemonic('o');
			fileOpen.setActionCommand("open");
			fileOpen.addActionListener(this);
			file.add(fileOpen);
			fileSave = new JMenuItem("Save...");
			fileSave.setMnemonic('s');
			fileSave.setEnabled(false);
			fileSave.setActionCommand("save");
			fileSave.addActionListener(this);
			file.add(fileSave);
			fileView = new JMenuItem("View Metadata...");
			final JMenuItem fileExit = new JMenuItem("Exit");
			fileExit.setMnemonic('x');
			fileExit.setActionCommand("exit");
			fileExit.addActionListener(this);
			file.add(fileExit);

			final JMenu options = new JMenu("Options");
			options.setMnemonic('p');
			menubar.add(options);
			final JMenuItem optionsFPS = new JMenuItem("Frames per Second...");
			optionsFPS.setMnemonic('f');
			optionsFPS.setActionCommand("fps");
			optionsFPS.addActionListener(this);
			options.add(optionsFPS);

			final JMenu help = new JMenu("Help");
			help.setMnemonic('h');
			menubar.add(help);
			final JMenuItem helpAbout = new JMenuItem("About...");
			helpAbout.setMnemonic('a');
			helpAbout.setActionCommand("about");
			helpAbout.addActionListener(this);
			help.add(helpAbout);

			// add key listener to focusable components
			nSlider.addKeyListener(this);
		}

		/**
		 * Constructs an image viewer.
		 *
		 * @param canCloseReader whether or not the underlying reader can be closed
		 */
		public ImageViewer(final Context context, final boolean canCloseReader) {
			this(context);
			this.canCloseReader = canCloseReader;
		}

		/** Opens the given data source using the current format reader. */
		public void open(final String id) {
			wait(true);
			try {
				canCloseReader = true;
				myReader = initializeService.initializeReader(id);
				final long planeCount = myReader.getMetadata().get(0).getPlaneCount();
				final ProgressMonitor progress = new ProgressMonitor(this, "Reading " +
					id, null, 0, 1);
				progress.setProgress(1);
				final BufferedImage[] img = new BufferedImage[(int) planeCount];
				for (long planeIndex = 0; planeIndex < planeCount; planeIndex++) {
					if (progress.isCanceled()) break;
					final Plane plane = myReader.openPlane(0, planeIndex);
					img[(int) planeIndex] = AWTImageTools.openImage(plane, myReader, 0);
				}
				progress.setProgress(2);
				setImages(myReader, img);
				myReader.close(true);
			}
			catch (final FormatException exc) {
				logService.info("", exc);
				wait(false);
				return;
			}
			catch (final IOException exc) {
				logService.info("", exc);
				wait(false);
				return;
			}
			wait(false);
		}

		/**
		 * Saves the current images to the given destination using the current
		 * format writer.
		 */
		public void save(final String id) {
			if (images == null) return;
			wait(true);
			try {
				myWriter.setDest(id);
				final boolean stack = myWriter.canDoStacks();
				final ProgressMonitor progress = new ProgressMonitor(this, "Saving " +
					id, null, 0, stack ? images.length : 1);
				if (stack) {
					// save entire stack
					for (int i = 0; i < images.length; i++) {
						progress.setProgress(i);
						final boolean canceled = progress.isCanceled();
						myWriter.savePlane(0, i, getPlane(images[i]));
						if (canceled) break;
					}
					progress.setProgress(images.length);
				}
				else {
					// save current image only
					myWriter.savePlane(0, 0, getPlane(getImage()));
					progress.setProgress(1);
				}
				myWriter.close();
			}
			catch (FormatException | IOException exc) {
				logService.info("", exc);
			}
			wait(false);
		}

		/** Sets the viewer to display the given images. */
		public void setImages(final BufferedImage[] img) {
			setImages(null, img);
		}

		/**
		 * Sets the viewer to display the given images, obtaining corresponding core
		 * metadata from the specified format reader.
		 */
		public void setImages(final Reader reader, final BufferedImage[] img) {
			filename = reader == null ? null : reader.getCurrentFile();
			myReader = reader;
			images = img;

			fileView.setEnabled(true);

			fileSave.setEnabled(true);
			nSlider.removeChangeListener(this);
			nSlider.setValue(1);
			nSlider.setMaximum(images.length);
			nSlider.setEnabled(images.length > 1);
			nSlider.addChangeListener(this);
			sliderPanel.setVisible(images.length > 1);

			updateLabel(-1, -1);
			sb.setLength(0);
			if (filename != null) {
				sb.append(reader.getCurrentFile());
				sb.append(" ");
			}
			final String format = reader == null ? null : reader.getFormat()
				.getFormatName();
			if (format != null) {
				sb.append("(");
				sb.append(format);
				sb.append(")");
				sb.append(" ");
			}
			if (filename != null || format != null) sb.append("- ");
			sb.append(TITLE);
			setTitle(sb.toString());
			if (images != null) icon.setImage(images[0]);
			pack();
		}

		/** Gets the currently displayed image. */
		public BufferedImage getImage() {
			final int ndx = getPlaneIndex();
			return images == null || ndx >= images.length ? null : images[ndx];
		}

		public Plane getPlane(final BufferedImage image) {
			final BufferedImagePlane plane = new BufferedImagePlane(context);
			plane.setData(image);
			return plane;
		}

		/** Gets the index of the currently displayed image. */
		public int getPlaneIndex() {
			return nSlider.getValue() - 1;
		}

		// -- Window API methods --
		@Override
		public void setVisible(final boolean visible) {
			super.setVisible(visible);
			// kick off animation thread
			new Thread(this).start();
		}

		// -- ActionListener API methods --

		/** Handles menu commands. */
		@Override
		public void actionPerformed(final ActionEvent e) {
			final String cmd = e.getActionCommand();
			if ("open".equals(cmd)) {
				wait(true);
				final JFileChooser chooser = guiService.buildFileChooser(formatService
					.getAllFormats());
				wait(false);
				final int rval = chooser.showOpenDialog(this);
				if (rval == JFileChooser.APPROVE_OPTION) {
					final File file = chooser.getSelectedFile();
					if (file != null) open(file.getAbsolutePath(), myReader);
				}
			}
			else if ("save".equals(cmd)) {
				wait(true);
				final JFileChooser chooser = guiService.buildFileChooser(formatService
					.getOutputFormats());
				wait(false);
				final int rval = chooser.showSaveDialog(this);
				if (rval == JFileChooser.APPROVE_OPTION) {
					if (myWriter != null) {
						try {
							myWriter.close();
						}
						catch (final IOException e1) {
							logService.error(e1);
						}
					}
					final File file = chooser.getSelectedFile();
					try {
						myWriter = initializeService.initializeWriter(myReader
							.getMetadata(), file.getAbsolutePath());
					}
					catch (FormatException | IOException e1) {
						logService.error(e);
					}
					if (file != null) save(file.getAbsolutePath(), myWriter);
				}
			}
			else if ("exit".equals(cmd)) dispose();
			else if ("fps".equals(cmd)) {
				// HACK - JOptionPane prevents shutdown on dispose
				setDefaultCloseOperation(EXIT_ON_CLOSE);

				final String result = JOptionPane.showInputDialog(this,
					"Animate using space bar. How many frames per second?", "" + fps);
				try {
					fps = Integer.parseInt(result);
				}
				catch (final NumberFormatException exc) {
					logService.debug("Could not parse fps " + fps, exc);
				}
			}
			else if ("about".equals(cmd)) {
				// HACK - JOptionPane prevents shutdown on dispose
				setDefaultCloseOperation(EXIT_ON_CLOSE);

				final String msg = "<html>" + "SCIFIO core for reading and " +
					"converting file formats." + "<br>Copyright (C) 2005 - 2013" +
					" Open Microscopy Environment:" + "<ul>" +
					"<li>Board of Regents of the University of Wisconsin-Madison</li>" +
					"<li>Glencoe Software, Inc.</li>" + "<li>University of Dundee</li>" +
					"</ul>" + "<br><br>See <a href=\"" +
					"http://loci.wisc.edu/software/scifio\">" +
					"http://loci.wisc.edu/software/scifio</a>" +
					"<br>for help with using SCIFIO.";
				JOptionPane.showMessageDialog(null, msg, "SCIFIO",
					JOptionPane.INFORMATION_MESSAGE);
			}
		}

		// -- ChangeListener API methods --

		/** Handles slider events. */
		@Override
		public void stateChanged(final ChangeEvent e) {
			final boolean outOfBounds = false;
			updateLabel(-1, -1);
			final BufferedImage image = outOfBounds ? null : getImage();
			if (image == null) {
				iconLabel.setIcon(null);
				iconLabel.setText("No image plane");
			}
			else {
				icon.setImage(image);
				iconLabel.setIcon(icon);
				iconLabel.setText(null);
			}
		}

		// -- KeyListener API methods --

		/** Handles key presses. */
		@Override
		public void keyPressed(final KeyEvent e) {
			if (e.getKeyChar() == ANIMATION_KEY) anim = !anim; // toggle animation
		}

		@Override
		public void keyReleased(final KeyEvent e) {}

		@Override
		public void keyTyped(final KeyEvent e) {}

		// -- MouseMotionListener API methods --

		/** Handles cursor probes. */
		@Override
		public void mouseDragged(final MouseEvent e) {
			updateLabel(e.getX(), e.getY());
		}

		/** Handles cursor probes. */
		@Override
		public void mouseMoved(final MouseEvent e) {
			updateLabel(e.getX(), e.getY());
		}

		// -- Runnable API methods --

		/** Handles animation. */
		@Override
		public void run() {
			while (isVisible()) {
				try {
					Thread.sleep(1000 / fps);
				}
				catch (final InterruptedException exc) {
					logService.debug("", exc);
				}
			}
		}

		// -- WindowListener API methods --

		@Override
		public void windowClosing(final WindowEvent e) {}

		@Override
		public void windowActivated(final WindowEvent e) {}

		@Override
		public void windowDeactivated(final WindowEvent e) {}

		@Override
		public void windowOpened(final WindowEvent e) {}

		@Override
		public void windowIconified(final WindowEvent e) {}

		@Override
		public void windowDeiconified(final WindowEvent e) {}

		@Override
		public void windowClosed(final WindowEvent e) {
			try {
				if (myWriter != null) {
					myWriter.close();
				}
				if (canCloseReader && myReader != null) {
					myReader.close();
				}
			}
			catch (final IOException io) {}
		}

		// -- Helper methods --

		private final StringBuffer sb = new StringBuffer();

		/** Updates cursor probe label. */
		protected void updateLabel(int x, int y) {
			if (images == null) return;
			final int ndx = getPlaneIndex();
			sb.setLength(0);
			if (images.length > 1) {
				sb.append("N=");
				sb.append(ndx + 1);
				sb.append("/");
				sb.append(images.length);
			}
			final BufferedImage image = images[ndx];
			final int w = image == null ? -1 : image.getWidth();
			final int h = image == null ? -1 : image.getHeight();
			if (x >= w) x = w - 1;
			if (y >= h) y = h - 1;
			if (x >= 0 && y >= 0) {
				if (images.length > 1) sb.append("; ");
				sb.append("X=");
				sb.append(x);
				if (w > 0) {
					sb.append("/");
					sb.append(w);
				}
				sb.append("; Y=");
				sb.append(y);
				if (h > 0) {
					sb.append("/");
					sb.append(h);
				}
				if (image != null) {
					final Raster r = image.getRaster();
					final double[] pix = r.getPixel(x, y, (double[]) null);
					sb.append("; value");
					sb.append(pix.length > 1 ? "s=(" : "=");
					for (int i = 0; i < pix.length; i++) {
						if (i > 0) sb.append(", ");
						sb.append(pix[i]);
					}
					if (pix.length > 1) sb.append(")");
					sb.append("; type=");
					final int pixelType = AWTImageTools.getPixelType(image);
					sb.append(FormatTools.getPixelTypeString(pixelType));
				}
			}
			sb.append(" ");
			probeLabel.setText(sb.toString());
		}

		/** Toggles wait cursor. */
		protected void wait(final boolean wait) {
			setCursor(wait ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : null);
		}

		/**
		 * Opens from the given data source using the specified reader in a separate
		 * thread.
		 */
		protected void open(final String id, final Reader r) {
			new Thread("ImageViewer-Opener") {

				@Override
				public void run() {
					try {
						myReader.close();
					}
					catch (final IOException exc) {
						logService.info("", exc);
					}
					myReader = r;
					open(id);
				}
			}.start();
		}

		/**
		 * Saves to the given data destination using the specified writer in a
		 * separate thread.
		 */
		protected void save(final String id, final Writer w) {
			new Thread("ImageViewer-Saver") {

				@Override
				public void run() {
					try {
						myWriter.close();
					}
					catch (final IOException exc) {
						logService.info("", exc);
					}
					myWriter = w;
					save(id);
				}
			}.start();
		}
	}

}
