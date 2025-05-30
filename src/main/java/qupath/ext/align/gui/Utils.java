package qupath.ext.align.gui;

import java.util.ResourceBundle;

/**
 * Utility methods for the whole extension.
 */
public class Utils {

    private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.align.strings");

    private Utils() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * @return the resources containing the localized strings of the extension
     */
    public static ResourceBundle getResources() {
        return resources;
    }
}
