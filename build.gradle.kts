plugins {
	id("qupath-conventions")
}

qupathExtension {
	name = "qupath-extension-align"
	version = "0.5.0"
	group = "io.github.qupath"
	description = "QuPath extension for interactive image alignment"
	automaticModule = "qupath.extension.align"
}

dependencies {
	implementation(libs.bundles.qupath)
	implementation(libs.bundles.logging)
	implementation(libs.qupath.fxtras)
}
