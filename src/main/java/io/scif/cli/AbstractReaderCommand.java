/*
 * #%L
 * SCIFIO library for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2011 - 2018 Board of Regents of the University of
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

package io.scif.cli;

import io.scif.FormatException;
import io.scif.ImageMetadata;
import io.scif.Metadata;
import io.scif.Plane;
import io.scif.Reader;
import io.scif.config.SCIFIOConfig;
import io.scif.filters.ChannelFiller;
import io.scif.filters.FileStitcher;
import io.scif.filters.MinMaxFilter;
import io.scif.filters.PlaneSeparator;
import io.scif.filters.ReaderFilter;
import io.scif.services.InitializeService;
import io.scif.util.FormatTools;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import net.imagej.axis.CalibratedAxis;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import org.scijava.plugin.Parameter;
import org.scijava.util.ArrayUtils;

/**
 * Abstract superclass for {@link SCIFIOToolCommand} implementations that read
 * images. Contains common parameter flags, e.g. for enabling certain filters,
 * cropping, etc... methods for generating readers using these parameters, and a
 * read helper method that performs all the necessary index setup and looping
 * over desired planes.
 * 
 * @author Mark Hiner
 */
public abstract class AbstractReaderCommand extends AbstractSCIFIOToolCommand {

	// -- Fields --

	private SCIFIOConfig config;

	@Parameter
	private InitializeService initializeService;

	// -- Parameters --

	@Option(name = "-t", aliases = "--stitch",
		usage = "stitch input files with similar names")
	private boolean stitch;

	@Option(name = "-s", aliases = "--separate",
		usage = "separate non-XY planar axes")
	private boolean separate;

	// TODO need a ChannelMerger filter
//	@Option(name = "-m", aliases = "--merge",
//		usage = "combine separate channels into RGB images")
//	private boolean merge;

	@Option(name = "-e", aliases = "--expand",
		usage = "expand indexed color to RGB")
	private boolean expand;

	@Option(name = "-g", aliases = "--nogroup",
		usage = "force multi-file datasets to be read as individual files")
	private boolean nogroup;

	@Option(name = "-a", aliases = "--autoscale",
		usage = "automatically adjust brightness and contrast before converting")
	private boolean autoscale;

	@Option(name = "-M", aliases = "--map", metaVar = "FILE_NAME",
		usage = "specify file on disk to which name should be mapped")
	private String map;

	// TODO add image index support
//	@Option(name = "-i", aliases = "--image", metaVar = "START,END",
//		handler = LongArrayOptionHandler.class,
//		usage = "specify a range of image indices to convert")
//	protected long[] images = new long[2];

	@Option(
		name = "-r",
		aliases = "--range",
		handler = LongArrayOptionHandler.class,
		usage = "specify a range for non-planar indices. Values are read as [min, max] "
			+ "pairs in axis order")
	private final long[] npRange = new long[0];

	@Option(name = "-C", aliases = "--crop",
		handler = LongArrayOptionHandler.class,
		usage = "specify a range for planar axis cropping. Values are read as"
			+ " [offset, length] pairs in axis order")
	private long[] crop = new long[0];

	// -- Helper methods --

	/**
	 * @return File on disk to map to
	 */
	protected String getMap() {
		return map;
	}

	/**
	 * @param map File on disk to map to
	 */
	protected void setMap(final String map) {
		this.map = map;
	}

	/**
	 * @return Cropped axis lengths as [offset, length] pairs in axis order.
	 */
	protected long[] getCrop() {
		return crop;
	}

	/**
	 * @param crop Cropped axis lengths as [offset, length] pairs in axis order.
	 */
	protected void setCrop(final long[] crop) {
		this.crop = crop;
	}

	/**
	 * Helper method to initialize a reader given the current command parameters.
	 * Wraps exceptions.
	 * 
	 * @param path - Path to a dataset used to initialize a reader
	 * @return A ReaderFilter initialized using the input path of this command
	 */
	protected ReaderFilter makeReader(final String path) throws CmdLineException {
		ReaderFilter reader;
		try {
			reader = initializeService.initializeReader(path, getConfig());
		}
		catch (final FormatException e) {
			throw new CmdLineException(null, e.getMessage());
		}
		catch (final IOException e) {
			throw new CmdLineException(null, e.getMessage());
		}

		// Enable filters
		if (stitch) reader.enable(FileStitcher.class);
		if (separate) reader.enable(PlaneSeparator.class);
		if (expand) reader.enable(ChannelFiller.class);
		if (autoscale) reader.enable(MinMaxFilter.class);

		return reader;
	}

	/**
	 * Convenience method to split a single list of values to a set of
	 * offset/length values. Default values for offsets are 0 and lengths are set
	 * to the corresponding axis length
	 * 
	 * @param values List of values, in alternating offset,length format
	 * @param offsets Array of minimum values. Populated by reference
	 * @param lengths Array of maximum values. Populated by reference
	 * @param meta Metadata to look up axis lengths
	 * @param axes List of axes determining the order of the offset/length arrays
	 */
	protected void makeRange(final long[] values, final long[] offsets,
		final long[] lengths, final ImageMetadata meta,
		final List<CalibratedAxis> axes)
	{
		// The values array is one long list of values for both offsets and lengths,
		// so it should be twice the length of a single array.
		for (int i = 0; i < offsets.length * 2; i++) {
			// Even indices are offsets
			if (i % 2 == 0) {
				// Take the provided value, otherwise leave it unchanged (assumes 0 for
				// a fresh array)
				if (i < values.length) {
					offsets[i / 2] = values[i];
				}
			}
			// Odd indices are lengths
			else {
				// Take the provided value
				if (i < values.length) {
					lengths[i / 2] = values[i];
				}
				// Otherwise, look up the axis length
				else {
					lengths[i / 2] = meta.getAxisLength(axes.get(i / 2));
				}
			}
		}
	}

	/**
	 * Intermediate read method. Iterates over the dataset, delegating the actual
	 * open pixel operations to the
	 * {@link #processPlane(Reader, Plane, int, long, long, Interval)}
	 * method.
	 * 
	 * @param reader Reader to use for opening a dataset
	 */
	protected void read(final Reader reader) throws CmdLineException {
		final Metadata m = reader.getMetadata();

		final ImageMetadata iMeta = m.get(0);
		// Get the planar offsets/lengths (account for cropping)
		final long[] planeOffsets = new long[iMeta.getAxesPlanar().size()];
		final long[] planeLengths = new long[planeOffsets.length];
		makeRange(getCrop(), planeOffsets, planeLengths, iMeta, iMeta
			.getAxesPlanar());

		// Get the non-planar offsets/lengths (e.g. restricting plane indices)
		final long[] npOffsets = new long[iMeta.getAxesNonPlanar().size()];
		final long[] npLengths = new long[npOffsets.length];
		makeRange(npRange, npOffsets, npLengths, iMeta, iMeta.getAxesNonPlanar());
		final long[] position = Arrays.copyOf(npOffsets, npOffsets.length);

		// Count the total number of planes
		final long[] planeCounts = new long[npOffsets.length];
		for (int i = 0; i < planeCounts.length; i++) {
			planeCounts[i] = npLengths[i] - npOffsets[i];
		}
		final long planeCount =
			planeCounts.length > 0 ? ArrayUtils.safeMultiply64(planeCounts) : 1;

		long planeIndex = FormatTools.positionToRaster(0, m, position);
		Plane plane = null;
		long planeNo = 0;

		// As long as we have planes to proces, open the next plane's pixels
		// according to the needs of the current plugin
		do {
			final long[] planeMax = new long[planeLengths.length];
			for (int d = 0; d < planeMax.length; d++) {
				planeMax[d] = planeOffsets[d] + planeLengths[d] - 1;
			}
			final Interval bounds = new FinalInterval(planeOffsets, planeMax);
			plane = processPlane(reader, plane, 0, planeIndex, planeNo++, bounds);

			// Print statistics
			info("Processed: " + planeNo + "/" + planeCount + " planes.");
		}
		// Verifies the next plane index is valid and updates the plane position
		while ((planeIndex =
			FormatTools.nextPlaneIndex(0, m, position, npOffsets, npLengths)) != -1);
	}

	/**
	 * Helper method to open the specified region of the specified plane and
	 * perform any command-specific operations.
	 */
	protected abstract Plane processPlane(Reader reader, Plane plane,
		int imageIndex, long planeIndex, long planeNo, Interval bounds)
		throws CmdLineException;

	/**
	 * @return A {@link SCIFIOConfig} based on the parameters passed to this
	 *         command.
	 */
	protected SCIFIOConfig getConfig() {
		if (config == null) config =
			new SCIFIOConfig().groupableSetGroupFiles(!nogroup).writerSetSequential(
				true);
		return config;
	}

}
