# Scaled

![Travis build status](https://travis-ci.org/scaled/scaled.svg?branch=master)

Scaled is a modern programmer's text editor, built atop Java 8 and JavaFX 8, mostly written in
Scala, and which is designed to be extensible "all the way down" like Emacs. Like Emacs, "all the
way down" doesn't actually go all the way down, but it goes down a lot further than most other
editors.

Scaled focuses on the text editing experience first, and IDE-like features second. This does not
mean that the IDE features suck (indeed, a goal of Scaled is to push the frontiers of "intelligent"
code editing), but rather that they are not "in your face" from a user experience standpoint. We
start from the pristine calm of a colorized window of code, and tastefully grow from there.

Scaled is designed to be extensible in any JVM language. This is technically possible already, but
will be substantially improved before I claim that anyone would actually want to do this. My goal
is that a programmer using Scaled can comfortably extend the editor in their preferred JVM language
with no more cognitive dissonance than they already endure when using a third party library written
in Java. I may never reach perfection in that regard, but it will be a damned sight better than
extending the editor in elisp (nothing against lisp, use Clojure if that's your bag).

![Hello Scaled screenshot](http://scaled.github.io/images/screenshots/hello-scaled.png)

## Talk about it

A [Scaled Google Group] exists where questions, comments, suggestions and discussion of anything
else related to Scaled or programmer's editors are welcome.

## Kick the tires

Scaled is still in the early phases of active development. It is mature enough that I use it
exclusively to develop itself, but it has rough edges. Fortunately, it's relatively easy to give it
a whirl.

Scaled includes a package management system which is used to install Scaled itself as well as
extension packages. The Scaled package manager (spam) is desgined to bootstrap itself from a very
simple bootstrap jar file which you can install in conjunction with a shell script or batch file.

Note: the `java` executable in your path must be from a Java 8 JDK installation, otherwise replace
`java` in the below scripts with the absolute path to `bin/java` in a Java 8 JDK installation.

### Unix / Mac OS X / Cygwin

- Download [scaled-pacman.jar] and put it in your `~/bin` directory.

- Create a shell script `~/bin/spam` with the following contents:

```
java -jar `dirname $0`/scaled-pacman.jar "$@"
```

Note: Cygwin users can't use `dirname $0` so they'll have to replace that with the absolute path to
`scaled-pacman.jar`.

- Make the `spam` script executable: `chmod u+x ~/bin/spam`

*Note*: if you're using OpenJDK on Linux, you need to be sure that you've also installed the
OpenJFX package as Scaled uses JavaFX for its UI.

### Windows

- Download [scaled-pacman.jar] and put it into some directory that's in your shell search path.

- Create a batch file `spam.bat` in the same directory as `scaled-pacman.jar` with the following
contents:

```
set SCRIPT_DIR=%~dp0
java -jar "%SCRIPT_DIR%scaled-pacman.jar" %*
```

### Use spam to install Scaled

Now that spam is installed, you can test that it's working by running:

```
spam list
```

and you should see this output:

```
Installed:
pacman      The Scaled Package Manager. Wakka wakka wakka.
```

Assuming you do, then you're ready to install Scaled. Do that like so:

```
spam install scaled
```

This will download and build all of the core packages that make up Scaled. Scaled packages are
fetched directly from their DVCS source URLs and built locally during the installation process.
Depending on the pre-existing state of your local Maven repository, this may involve downloading a
bunch of existing jars, and it will involve compiling a bunch of code. It might take a minute or
two on a reasonably speedy development machine.

Scaled will install itself into `~/.scaled` on a non-Mac, and `~/Library/Application
Support/Scaled` on a Mac. Let's call that directory `SCALED_HOME`. You can invoke Scaled via
`spam`, but it's cumbersome, instead symlink `SCALED_HOME/Packages/scaled/bin/scaled` into your
`~/bin` directory (or wherever you like to put things so that they are on your shell path), and
then invoke Scaled via `scaled`.

Windows users can link (or copy) `SCALED_HOME\Packages\scaled\bin\scaled.bat` somewhere such that
it's on their path.

## Packages

By default, Scaled comes only with basic text editing capabilities. To properly Feel the Magic™,
you will need to install some packages. You can list the available packages via:

```
spam list --all
```

If you are a Java developer, you'll probably want to:

```
spam install java-mode
spam install maven-project
spam install xml-mode
```

If you like the Scala, be sure to:

```
spam install scala-mode
spam install sbt-project
```

Presently Scaled's integration with Maven projects is decent and its integration with SBT projects
is the barest skeleton. Eventually SBT integration will be improved, and Gradle integration is also
in the cards.

If you do happen to have a `pom.xml` with your project metadata in it, Scaled will automatically
build your code on save and allow you to navigate through the errors in the editor (via `M-]` and
`M-[`). The current compiler integration is somewhat primitive, and a tighter integration is
forthcoming.

Scaled also includes basic integration with JUnit, allowing you to run tests directly from within
the editor and see results. `C-c C-t` runs the tests in the current file if it looks like it
contains JUnit tests, and it runs all the tests for the project otherwise.

## Using Scaled

At the moment Scaled's "UI" follows Emacs where that makes sense (pretty much all of the basic
editing key bindings). Extensions like `project-mode` introduce new interactions and I'm not making
an effort to model those the myriad hodge-podge Emacs IDE-like extensions that exist, I'm just
trying to come up with sensible bindings.

At any time, you can invoke `M-x describe-mode` (or `C-h m`) to see all of the key bindings and
config vars for the the active major and minor modes. You can cross-reference that with the
[Emacs reference card] to see basic editing commands organized more usefully than alphabetic order
by key binding description.

## Development

Chances are, Scaled does not currently solve all of your development needs and make your favorite
kind of toast. If you find that the fires in your belly are stoked by the idea of an Emacs-like
extensible editor built atop the JVM, then perhaps you would like to extend Scaled such that it
does support your desired toast-making capabilities. This is becoming a less crazy prospect day by
day as the Scaled core stabilizes and the facilities for developing Scaled improve.

Because Scaled checks itself and all of its extensions out directly from source, you can simply
start hacking on the code that is checked out in `SCALED_HOME/Packages`. This is not wildly
different than how I develop Scaled. I actually have the packages checked out elsewhere and symlink
them into `SCALED_HOME/Packages`, but that's mainly so that I can arrange the myriad Scaled
subprojects into a slightly less flat directory structure.

I'll eventually add support to the Scaled Package Manager to make it easier to maintain a "working"
Scaled installation in the standard location and a "development" Scaled installation elsewhere
which you hack on, run when testing, and can break without fear of hosing your development setup.
That's even theoretically possible right now by running `spam -Dscaled.meta=somedir` (or
`scaled -Dscaled.meta=somedir` as -D args are passed through to `spam`) but I'd like to make it
even easier.

There's not much documentation on Scaled's internals yet, but if more than zero people turn up and
want to help, then I'll gladly move the writing of said documentation up my priority list. The main
ways to extend scaled are described below.

### Scaled Extensions

Scaled extensions come in three main flavors:

  * services: programmatic services which provide functionality to other services and to modes
  * plugins: services can define plugin APIs so that one package can define a service and other
    packages can extend it
  * modes: major and minor editing modes (ala Emacs), which provide editing smarts specific to a
    particular programming language or activity

An example of all of these flavors working in harmony is the `project-service` package which
provides a framework for grokking projects. Project support comes in three parts:

  * `ProjectService` is a service that a major mode can inject to gain access to "project services"
     (e.g. enumerate all files in the project, rebuild the project)
  * `ProjectFinder` is a plugin used by the project service to allow other packages to provide code
    that identifies a project based on what it sees on the file system (a `pom.xml` file, a
    `build.sbt` file, etc.) and provide code for operating on projects of that kind
  * `ProjectMode` is a minor mode which is automatically activated for any mode which is tagged
    with `project`; the project minor mode adds key bindings for things like recompiling the
    project and annotating the appropriate buffers with warnings/errors

Most packages are simpler than the project package. They just export a major mode or two
(`scala-mode` and `java-mode` for example), or just export a plugin for another service
(`maven-project` for example)

Anyone can write a Scaled extension, but all currently known Scaled extensions live in the
[Github Scaled project](https://github.com/scaled).

## License

Scaled is released under the New BSD License. The most recent version of the code is available at
https://github.com/scaled/scaled

[Emacs reference card]: http://www.gnu.org/software/emacs/refcards/pdf/refcard.pdf
[the `spam` shell script]: https://raw.githubusercontent.com/scaled/pacman/master/bin/spam
[Scaled Google Group]: https://groups.google.com/forum/#!forum/scalable-editor
[scaled-pacman.jar]: http://scaled.github.io/scaled-pacman.jar
