package qupath.ext.align.gui;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TextFormatter;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * UI utility methods for the whole extension.
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

    /**
     * Loads the FXML file located at the provided URL and set its controller.
     *
     * @param controller the controller of the FXML file to load
     * @param url the path of the FXML file to load
     * @throws IOException if an error occurs while loading the FXML file
     * @throws NullPointerException if one of the provided parameter is null
     */
    public static void loadFXML(Object controller, URL url) throws IOException {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(url), resources);
        loader.setRoot(Objects.requireNonNull(controller));
        loader.setController(controller);
        loader.load();
    }

    /**
     * Create an {@link ObservableMap} whose keys match the provided list and whose values are determined by calling
     * the provided function.
     * <p>
     * The returned map will listen to changes on the provided list and update its content accordingly. This means:
     * <ul>
     *     <li>The provided list should be updated from the same thread, as no synchronization is performed by the map.</li>
     *     <li>
     *         The provided list will contain a strong reference to this map (through the listener), so this map won't be
     *         garbage collected until the provided list is.
     *     </li>
     * </ul>
     *
     * @param list keys of the returned map will match elements of this list
     * @param keyToValue a function to determine a value from a key. It will be called each time a new pair must be added
     *                   to the returned map
     * @return an {@link ObservableMap} whose keys match the provided list and whose values are determined by calling the
     * provided function
     * @param <T> the type of the keys of the returned map
     * @param <V> the type of the values of the returned map
     * @throws NullPointerException if one of the provided parameter is null
     */
    public static <T, V> ObservableMap<T, V> createKeyMappedObservableMap(ObservableList<T> list, Function<T, V> keyToValue) {
        ObservableMap<T, V> map = FXCollections.observableHashMap();

        for (T item : list) {
            map.put(item, keyToValue.apply(item));
        }

        list.addListener((ListChangeListener<T>) change -> {
            while (change.next()) {
                if (change.wasRemoved()) {
                    for (T removedItem : change.getRemoved()) {
                        map.remove(removedItem);
                    }
                }
                if (change.wasAdded()) {
                    for (T addedItem : change.getAddedSubList()) {
                        map.put(addedItem, keyToValue.apply(addedItem));
                    }
                }
            }
            change.reset();
        });

        return map;
    }

    /**
     * @return a {@link TextFormatter} that only accepts positive decimal numbers
     */
    public static TextFormatter<String> createFloatFormatter() {
        return new TextFormatter<>(change -> Pattern.matches("^\\d*\\.?\\d*$", change.getControlNewText()) ? change : null);
    }
}
