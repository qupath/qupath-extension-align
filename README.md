# QuPath Align extension

Welcome to the image alignment extension for [QuPath](http://qupath.github.io)!

This was previously the *Interactive image alignment* command in QuPath v0.2.
Not much loved by its creator, this command has proven useful to numerous people 
on the [Scientific Community Image Forum](http://image.sc).

It has been separated out to its own repository so that it can be developed 
independently from the main QuPath project.
And perhaps so someone will fork it and make a better version.

## Building

You can build the extension with

```bash
gradlew clean build
```

The output .jar will be under `build/libs`.

## Installing

You'll need to add the extension to QuPath's extensions folder.

The easiest way to do that is to drag the .jar file onto QuPath's main window, 
and then allow QuPath to copy it to the right place.

You might then need to restart QuPath (but not your computer).
