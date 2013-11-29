package fi.paivola.mapserver.core;

import fi.paivola.mapserver.utils.Color;
import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The base class for all models. Has some basic functions that you will surely
 * find useful.
 *
 * @author Juhani
 */
public abstract class Model {

    /**
     * Each model has a ID. It's used for identification and iteration.
     */
    public int id;
    /**
     * Icon is the representation on the map.
     */
    public String icon;
    /**
     * Color of the map icon
     */
    public Color color;
    /**
     * Type of the model (point, connection...)
     */
    public String type;
    /**
     * List of all connections (connection models).
     */
    public List<Model> connections;
    /**
     * List of events waiting.
     */
    public List<Event> events;
    /**
     * List of all settings that are exposed to the end user in the map.
     */
    public List<Setting> settings;
    /**
     * All data that are automatically saved to a DataFrame.
     */
    public Map<String, String> data;
    /**
     * Extension models that are active.
     */
    public Map<String, ExtensionModel> extensions;

    public Model(int id) {
        this.id = id;
        this.connections = new ArrayList<>();
        this.events = new ArrayList<>();
        this.settings = new ArrayList<>();
        this.data = new HashMap();
        this.extensions = new HashMap();
    }

    /**
     * Internal function that checks the events and runs extensions.
     *
     * @param last last dataframe
     * @param current current dataframe
     */
    public void onTickStart(DataFrame last, DataFrame current) {

        // lets check if there is some events waiting to get trough
        for (Event i : this.events) {
            if (i.frame == last.index) {
                this.onEvent(i);
            }
        }

        this.onTick(last, current);
        for (Map.Entry pairs : this.extensions.entrySet()) {
            ((ExtensionModel) pairs.getValue())
                    .onExtensionTickStart(this, last, current);
            // lets go trough the events ONCE again... this time for extensions
            for (Event i : this.events) {
                if (i.frame == last.index) {
                    ((ExtensionModel) pairs.getValue()).onEvent(i);
                }
            }
        }

        // lets delete the events that we used
        Iterator<Event> it = this.events.iterator();
        while (it.hasNext()) {
            if (it.next().frame == last.index) {
                it.remove();
            }
        }

        this.dumpToDataFrame(current);
    }

    /**
     * Dumps all data in data map to a dataframe
     *
     * @param frame frame to dump to
     */
    public void dumpToDataFrame(DataFrame frame) {
        for (Map.Entry pairs : this.data.entrySet()) {
            frame.saveData(this,
                    pairs.getKey().toString(), pairs.getValue().toString());
        }
    }

    /**
     * Adds an event to this model
     *
     * @param e event to add
     * @param m model from where the event originates
     */
    public void addEvent(Event e, Model m) {
        this.events.add(e);
    }

    /**
     * Adds a event to all connections.
     *
     * @param model the target of the event
     * @param frame dataframe of when it's added
     * @param e event thats added
     */
    public void addEventTo(Model model, DataFrame frame, Event e) {
        e.frame = frame.index;
        model.addEvent(e, this);
    }

    /**
     * Adds a event to all connections.
     *
     * @param frame dataframe of when it's added
     * @param e event thats added
     */
    public void addEventToAll(DataFrame frame, Event e) {
        e.frame = frame.index;
        for (Model i : this.connections) {
            i.addEvent(e, this);
        }
    }

    /**
     * Adds a extension model.
     *
     * @param name name for the extension model to go by
     * @param extension the extension model
     * @return returns true if succeeded, false otherwise
     */
    public boolean addExtension(String name, ExtensionModel extension) {
        return this.extensions.put(name, extension) == null;
    }

    /**
     * Adds a bunch of extension models from a map of strings and classes.
     *
     * @param gm game master
     * @param clss map of strings and classes
     */
    public void addExtensions(GameManager gm, Map<String, Object> clss) {
        for (Map.Entry pair : clss.entrySet()) {
            Class cls;
            cls = (Class) pair.getValue();
            //System.out.println(pair.getKey().toString());
            Constructor<Model> c;
            try {
                c = cls.getDeclaredConstructor(int.class);
                c.setAccessible(true);
                try {
                    this.addExtension(pair.getKey().toString(),
                            (ExtensionModel) c.newInstance(gm.current_id++));
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    Logger.getLogger(GameManager.class.getName())
                            .log(Level.SEVERE, null, ex);
                }
            } catch (NoSuchMethodException | SecurityException ex) {
                Logger.getLogger(GameManager.class.getName())
                        .log(Level.SEVERE, null, ex);
            }
        }
    }

    public boolean linkModel(Model m) {
        return this.connections.add(m);
    }

    public boolean delinkModel(Model m) {
        return this.connections.remove(m);
    }

    /**
     * Saves an integer. Will be saved to dataframe.
     * 
     * @param name  name of the integer
     * @param a     actual integer
     */
    public void saveInt(String name, int a) {
        this.data.put(name, "" + a);
    }

    /**
     * Saves a double. Will be saved to dataframe.
     * 
     * @param name  name of the double
     * @param a     actual double
     */
    public void saveDouble(String name, double a) {
        this.data.put(name, "" + a);
    }

    /**
     * Saves a string. Will be saved to dataframe.
     * 
     * @param name  name of the string
     * @param a     actual string
     */
    public void saveString(String name, String a) {
        this.data.put(name, a);
    }

    /**
     * Gets an integer.
     * 
     * @param name  name of the integer
     * @return      the integer or null
     */
    public int getInt(String name) {
        return parseInt(this.data.get(name));
    }

    /**
     * Gets a double.
     * 
     * @param name  name of the double
     * @return      the double or null
     */
    public double getDouble(String name) {
        return parseDouble(this.data.get(name));
    }

    /**
     * Gets a string.
     * 
     * @param name  name of the string
     * @return      the string or null
     */
    public String getString(String name) {
        return (this.data.get(name));
    }

    /**
     * Is called once the internal stuff (onTickStart) has finished, so it's
     * called once every tick.
     *
     * @param last last dataframe
     * @param current current dataframe
     */
    public abstract void onTick(DataFrame last, DataFrame current);

    /**
     * Called when there is a event to handle.
     *
     * @param e event thats handled
     */
    public abstract void onEvent(Event e);

    /**
     * Called when a particular model is added to the database of possible
     * models. Useful if you need to, say, register the current model as a
     * extension to some other model.
     *
     * @param gm game manager
     */
    public abstract void onRegisteration(GameManager gm);
    
    /**
     * Called when the module is asked for defaults, use save* here.
     */
    public abstract void onGenerateDefaults();

}
