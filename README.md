TLCockpit - GUI frontend to tlmgr
=================================

TLCockpit aims at being a GUI for [tlmgr](https://www.tug.org/texlive/tlmgr.html),
the [TeX Live](https://www.tug.org/texlive/) Manager, with a modern look and feel.


Supported functionality
-----------------------

While functionality is by now restricted to listing of packages, getting information
on packages, and updating/installing/removing packages, further features will be added
over time.

For advanced users there is an input field allowing to enter arbitrary commands of the
*tlmgr shell*, see [tlmgr manual](https://www.tug.org/texlive/doc/tlmgr.html#shell) for details.

Installing/Running
------------------

Since TLCockpit is included in TeX Live, it will be probably already installed
on your system. In case it isn't, a simple call to `tlmgr install tlcockpit`
should suffice.

We are providing new jar files on a daily bases in the github page, but update
the copy in TeX Live only at certain intervals. If you want to try out the latest
and greatest, get [tlcockpit.jar](jar/tlcockpit.jar) and put it into
`TEXLIVEROOT/temxf-dist/scripts/tlcockpit` replacing the current `tlcockpit.jar`.

After this you can run it by simply typing `tlcockpit`, or via a direct `java`
call as in `java -jar PATH/TO/tlcockpit.jar`.

Requirements
------------
You need at least `tlmgr` from after 2017-09-01, in TeX Live language at least
version 45190 or `texlive.infra`.

A recent Java installation that includes JavaFX is required. If you see
errors like
```
Error: A JNI error has occurred, please check your installation and try again
Exception in thread "main" java.lang.NoClassDefFoundError: javafx/event/EventTarget
	at java.lang.ClassLoader.defineClass1(Native Method)
	at java.lang.ClassLoader.defineClass(ClassLoader.java:763)
...
```
that means that the Java installation does not provides JavaFX.

On Windows the latest JRE from Oracle include JavaFX. On Linux one might
need to install some extra packages (Debian/Ubuntu: openjfx).

Development
-----------

Development is done in Scala with the ScalaFX widget set. The project is
mainly a challenge for myself to learn Scala. Building is done with *sbt*,
opening the project in IntelliJ works fine.

Deplyment is done via [sbt assembly](https://github.com/sbt/sbt-assembly)
which packs all the necessary libraries into one jar file.

Contributions are welcome, improvements are easily done as I just started
writing Scala and my style is horrible ;-)

Screenshots
-----------

(permanently outdated)

Main window

![Main window](screenshots/mainwindow.png)

Package details

![Package details](screenshots/pkg-details.png)


License
-------

GPL3+


Author
------

Norbert Preining

