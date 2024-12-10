plugins {
	id("qupath-conventions")
}

qupathExtension {
	name = "qupath-extension-align"
	version = "0.5.0-SNAPSHOT"
	group = "io.github.qupath"
	description = "QuPath extension for interactive image alignment"
	automaticModule = "qupath.extension.align"
}

dependencies {

	implementation(libs.bundles.qupath)
	implementation(libs.bundles.logging)

	// For testing
	testImplementation(libs.junit)

}
