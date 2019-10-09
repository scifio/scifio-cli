[![](https://travis-ci.org/scifio/scifio-cli.svg?branch=master)](https://travis-ci.org/scifio/scifio-cli)

SCIFIO Command Line Tools
=========================

An extensible application for using SCIFIO components from the command line.

Installation
------------

The easiest way to run the SCIFIO command line tools is via the
[jgo](https://github.com/scijava/jgo) launcher.

Add the following to your `.jgorc`:
```ini
[shortcuts]
scifio = io.scif:scifio-cli

[repositories]
scijava.public = https://maven.scijava.org/content/groups/public
```

And then run:
```
jgo scifio
```
The first time you run it, it will bootstrap the libraries into your Maven
local repository cache (typically in `~/.m2/repository`) and symlinked them
into the `jgo` cache (typically at `~/.jgo/io.scif/scifio-cli`).
Subsequent invocations will be much faster.

Building from source
--------------------

If you would like to build the SCIFIO command-line tools from scratch, simply run:

  ```mvn clean install```

from the top-level SCIFIO directory. You should see three projects completed: "SCIFIO projects", "SCIFIO Core" and "SCIFIO Tools." After installation, the tools will be installed to ```tools/target/appassembler/```. Here you will find the directory structure detailed in the [installation](#installation) section.

Note that there is one caveat in using these tools on *nix operating systems: by default, they will not be created witih execute permissions (per [this issue](http://jira.codehaus.org/browse/MAPPASM-54). So, instead of running ```scifio ...``` commands as in the [usage](#usage) section, you will have two options:

* run ```bash scifio ...``` instead

or

* Add execution permissions to the scifio script, using ```chmod a+x tools/target/appassembler/bin/scifio```

Note also that the zipped scripts (which will also be created as part of the install, in ```tools/target```) will unzip with execute permission already set.

Usage
-----

The SCIFIO command-line tools were designed to be syntactically similar to [git](http://git-scm.com/docs/gittutorial). So if you already know git, this should feel familiar.

Assuming you have the scripts on your ```PATH``` (per [installation](#installation) instructions) executing a command will always take the form of:

  ```scifio <command> [options] <parameters>```

NB: in all these examples, use ```scifio.bat``` if running in a Windows environment.

The ```<command>``` option is the simple lowercase name of the command you want to run. For example, if you wanted to view (using the ```Show.java``` command) a picture of a [kraken](http://en.wikipedia.org/wiki/Kraken), you would use:

  ```scifio show kraken.tiff```

If you want to see a list of all available commands, just run the script with no arguments.

Commands may have a set of flags or options available to modify their behavior. All command options are designed to work like [unix flags or switches](http://www.cs.bu.edu/teaching/unix/reference/vocab.html#flag), and typically will have a short (```-x```) and explicit (```--exterminate-kraken```) version. You can combine as many of these options as you want. For example, to print an ascii version of the top left 128x128 square of your kraken picture, you could use:

  ```scifio show -A --crop 0,128,0,128 kraken.tiff```

If you ever need to see the list of options a command has, and parameters a command requires, each command has a help flag:

  ```scifio show -h``` or ```scifio show --help```

You can also run the ```help``` meta-command:

  ```scifio help show```

Don't worry about making mistakes with the command invocation - commands will always print their usage on failure.

Getting help
------------

If you run into any problems or have questions about the commands,
or adding new commands, please use the
[scifio tag on the Image.sc Forum](https://forum.image.sc/tags/scifio).

Thank you for using the SCIFIO command-line tools!
