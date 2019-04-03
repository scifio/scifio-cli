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

package io.scif.cli.convert;

import io.scif.FormatException;
import io.scif.Metadata;
import io.scif.Plane;
import io.scif.Reader;
import io.scif.Writer;
import io.scif.cli.AbstractReaderCommand;
import io.scif.cli.SCIFIOToolCommand;
import io.scif.config.SCIFIOConfig;
import io.scif.filters.ReaderFilter;
import io.scif.formats.TIFFFormat;
import io.scif.services.InitializeService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import net.imglib2.Interval;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import org.scijava.io.handle.DataHandle;
import org.scijava.io.handle.DataHandleService;
import org.scijava.io.location.Location;
import org.scijava.io.location.LocationService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * {@link SCIFIOToolCommand} plugin for converting (reading and writing)
 * datasets.
 * 
 * @author Mark Hiner
 */
@Plugin(type = SCIFIOToolCommand.class)
public class Convert extends AbstractReaderCommand {

	// -- Arguments --

	@Argument(metaVar = "input", index = 0, usage = "input dataset path")
	private String in;

	@Argument(metaVar = "output", index = 1, usage = "output dataset path")
	private String out;

	@Argument(index = 2, multiValued = true)
	private final List<String> arguments = new ArrayList<>();

	// -- Parameters --

	@Option(name = "-b", aliases = "--bigtiff",
		usage = "force BigTIFF files to be written")
	private Boolean bigTiff;

	@Option(name = "-o", aliases = "--overwrite",
		usage = "always overwrite the output file if it exists")
	private Boolean overwrite;

	@Option(name = "-n", aliases = "--nooverwrite",
		usage = "never overwrite the output file if it exists")
	private Boolean nooverwrite;

	@Option(name = "-c", aliases = "--compression", metaVar = "COMPRESSION_TYPE",
		usage = "specify which codec to use when saving images")
	private String compression;

	// -- Fields --

	@Parameter
	private InitializeService initializeService;

	@Parameter
	private LocationService locationService;

	@Parameter
	private DataHandleService dataHandleService;

	private Writer writer;

	// -- AbstractSCIFIOToolCommand API --

	@Override
	protected void run() throws CmdLineException {

		// Check overwrite status
		if (checkOverwrite()) {
			info(in);

			// configure the reader
			final ReaderFilter reader = makeReader(in);

			// Configure the writer
			writer = makeWriter(reader.getMetadata());

			try {
				info(reader.getFormat().getFormatName() + " -> " +
					writer.getFormat().getFormatName());

				read(reader);
			}
			finally {
				try {
					reader.close();
				}
				catch (final IOException e) {
					warn("Failed to close reader");
					warn(e.getMessage());
				}
				try {
					writer.close();
				}
				catch (final IOException e) {
					warn("Failed to close writer");
					warn(e.getMessage());
				}
			}
		}
	}

	@Override
	protected String description() {
		return "command line tool for converting a dataset from one type"
			+ " to another";
	}

	@Override
	protected String getName() {
		return "convert";
	}

	@Override
	protected List<String> getExtraArguments() {
		return arguments;
	}

	@Override
	protected void validateParams() throws CmdLineException {
		if (in == null) {
			throw new CmdLineException(null, "Argument \"in\" is required");
		}
		if (out == null) {
			throw new CmdLineException(null, "Argument \"out\" is required");
		}
	}

	// -- AbstractReaderCommand API --

	@Override
	protected Plane processPlane(final Reader reader, Plane plane,
		final int imageIndex, final long planeIndex, final long planeNo,
		final Interval bounds) throws CmdLineException
	{
		// The loop calling this method is reader-centric, controlled by the
		// number of planes in the Reader. If some were truncated by the writer
		// - for example if converting to a Format that doesn't support part of
		// the source dataset -- then we need to prevent this method from executing
		// too many times.
		if (planeNo < writer.getMetadata().get(imageIndex).getPlaneCount()) {
			try {
				// open the specified plane
				if (plane == null) {
					plane = reader.openPlane(imageIndex, planeIndex, bounds, getConfig());
				}
				else {
					plane = reader.openPlane(imageIndex, planeIndex, plane, bounds,
						getConfig());
				}
				// write the specified plane
				writer.savePlane(imageIndex, planeNo, plane);
			}
			catch (final FormatException e) {
				throw new CmdLineException(null, e.getMessage());
			}
			catch (final IOException e) {
				throw new CmdLineException(null, e.getMessage());
			}
		}

		return plane;
	}

	// -- SCIFIOToolCommand methods --

	@Override
	public String commandName() {
		return "convert";
	}

	// -- Helper methods --

	/**
	 * Convenience method to initialize a writer based on this command's
	 * configuration options. Wraps exceptions.
	 * 
	 * @param sourceMeta Metadata object from the Reader that will be used
	 * @return A Writer initialized using this command's configuration.
	 */
	private Writer makeWriter(final Metadata sourceMeta) throws CmdLineException {
		Writer w;
		try {
			// Initialize the writer, and don't allow files to be opened to determine
			// format compatibility (as the destination doesn't exist on disk).
			w = initializeService.initializeWriter(sourceMeta, location(out),
				new SCIFIOConfig().checkerSetOpen(false));

			// Set writer configuration
			if (w instanceof TIFFFormat.Writer && bigTiff != null) {
				((TIFFFormat.Writer<?>) w).setBigTiff(bigTiff);
			}
		}
		catch (final FormatException e) {
			throw new CmdLineException(null, e.getMessage());
		}
		catch (final IOException e) {
			throw new CmdLineException(null, e.getMessage());
		}

		return w;
	}

	/**
	 * Checks whether an output file will be overwritten and behaves as needed
	 * depending on this command's configuration. If an overwrite is necessary but
	 * the desired outcome is ambiguous, requests user input.
	 * 
	 * @return true iff it's ok to overwrite the output file
	 */
	private boolean checkOverwrite() throws CmdLineException {
		Location destination = location(out);
		boolean exists = false;
		try (final DataHandle<?> handle = dataHandleService.create(destination)) {
			exists = handle.exists();
		}
		catch (final IOException exc) {
			throw new CmdLineException(null, "Cannot query destination: " + out, exc);
		}
		if (exists) {

			// nooverwrite takes precedence.
			if (nooverwrite == null) {
				// nooverwrite wasn't specified so check the overwrite field
				if (overwrite == null) {
					// overwrite wasn't specified so get user input
					warn("Warning: destination " + out + " exists.");
					warn("Do you want to overwrite it? ([y]/n)");
					try {
						final BufferedReader r = //
							new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
						final String choice = r.readLine().trim().toLowerCase();
						overwrite = !choice.startsWith("n");
					}
					catch (final IOException e) {
						throw new CmdLineException(null, e.getMessage());
					}
				}
				nooverwrite = !overwrite;
			}

			if (nooverwrite) {
				err("Output file exists and no-overwrite flag was specified. Existing.");
				return false;
			}
		}
		return true;
	}

}
