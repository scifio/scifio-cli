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

import io.scif.SCIFIOService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.PluginService;
import org.scijava.service.SciJavaService;

/**
 * Entry point for all SCIFIO commands. All {@link SCIFIOToolCommand}s are
 * discovered dynamically at runtime. They can be invoked by providing a set of
 * command line arguments, where the first argument is the simple, lowercase
 * class name of the desired command.
 * <p>
 * For example, {@link io.scif.cli.show.Show} can be invoked by passing
 * {@code show ...} to the {@link #run(String...)} method, or as a main method
 * argument.
 * </p>
 * 
 * @author Mark Hiner
 */
public final class SCIFIOTools {

	// -- Fields --

	private static Context ctx;

	private static Map<String, SCIFIOToolCommand> cmdMap;

	// -- Constructor --

	private SCIFIOTools() {
		// Private constructor to prevent instantiation of utility class
	}

	// -- SCIFIOTools API --

	/**
	 * Entry point for running SCIFIO commands. Launches the first command
	 * specified if it was successfully discovered.
	 * 
	 * @param args command arguments. In the form of
	 *          {@code command [flags] parameters}
	 */
	public static void run(final String... args) {
		// Populate the list of commands
		findCommands();

		if (args.length > 0) {
			final SCIFIOToolCommand cmd = cmdMap.get(args[0].toLowerCase());

			// Run the command, passing down the command arguments
			if (cmd != null) {
				cmd.runCommand(Arrays.copyOfRange(args, 1, args.length));
				ctx.dispose();
				return;
			}
		}

		// No command specified
		String msg =
			"A valid command was not supplied. Usage:\n\n"
				+ "\tscifio <command> [command args]\n\n" + "Available commands:";
		for (final String commandName : cmdMap.keySet()) {
			msg +=
				"\n\t" +
					commandName.substring(commandName.lastIndexOf('.') + 1).toLowerCase();
		}

		ctx.getService(LogService.class).error(msg);
		ctx.dispose();
	}

	// -- Helper methods --

	/**
	 * Lazily populates a map of simple names for all discovered
	 * SCIFIOToolCommands to instances of that command.
	 */
	private static void findCommands() {
		if (ctx == null) {
			ctx = new Context(SCIFIOService.class, SciJavaService.class);
		}

		if (cmdMap == null) {
			final List<SCIFIOToolCommand> commands =
				ctx.getService(PluginService.class).createInstancesOfType(
					SCIFIOToolCommand.class);
			cmdMap = new HashMap<>();

			for (final SCIFIOToolCommand cmd : commands) {
				cmdMap.put(cmd.commandName(), cmd);
			}
		}
	}

	// -- Main method --

	public static void main(final String[] args) {
		run(args);
	}
}
