import Toybox.Application;
import Toybox.Lang;
import Toybox.WatchUi;

module Settings {
    const KEY_COUNTDOWN = "countdown";
    const KEY_BURST = "burst";
    const COUNTDOWN_OPTIONS = [0, 3, 5];
    const BURST_OPTIONS = [1, 3, 5];
    // Samsung Camera's lens buttons on the S26 Ultra (rear camera; 100x is digital and left out).
    const ZOOM_LEVELS = ["0.6", "1", "2", "3", "5", "10"];

    function countdown() as Number {
        return stored(KEY_COUNTDOWN, 0);
    }

    // Shots per START press, 2 s apart.
    function burst() as Number {
        return stored(KEY_BURST, 1);
    }

    function stored(key as String, fallback as Number) as Number {
        var v = Application.Storage.getValue(key);
        return (v instanceof Number) ? v : fallback;
    }

    // The option after [current], wrapping round.
    function next(options as Array<Number>, current as Number) as Number {
        for (var i = 0; i < options.size(); i++) {
            if (options[i] == current) {
                return options[(i + 1) % options.size()];
            }
        }
        return options[0];
    }

    function countdownLabel() as String {
        return countdown() == 0 ? "Off" : countdown().toString() + " s";
    }

    function burstLabel() as String {
        return burst() == 1 ? "Off" : burst().toString() + " shots";
    }

    function buildMenu() as WatchUi.Menu2 {
        var menu = new WatchUi.Menu2({ :title => "Settings" });
        menu.addItem(new WatchUi.MenuItem("Countdown", countdownLabel(), :countdown, null));
        menu.addItem(new WatchUi.MenuItem("Burst", burstLabel(), :burst, null));
        menu.addItem(new WatchUi.MenuItem("Zoom", "Pick a lens", :zoom, null));
        return menu;
    }

    function buildZoomMenu() as WatchUi.Menu2 {
        var menu = new WatchUi.Menu2({ :title => "Zoom" });
        for (var i = 0; i < ZOOM_LEVELS.size(); i++) {
            menu.addItem(new WatchUi.MenuItem(ZOOM_LEVELS[i] + "x", null, ZOOM_LEVELS[i], null));
        }
        menu.setFocus(1); // 1x
        return menu;
    }
}

// Countdown and Burst step through their options in place; Zoom opens the lens list.
class SettingsMenuDelegate extends WatchUi.Menu2InputDelegate {
    private var _comms as Comms;

    function initialize(comms as Comms) {
        Menu2InputDelegate.initialize();
        _comms = comms;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        if (id == :countdown) {
            Application.Storage.setValue(Settings.KEY_COUNTDOWN, Settings.next(Settings.COUNTDOWN_OPTIONS, Settings.countdown()));
            item.setSubLabel(Settings.countdownLabel());
            WatchUi.requestUpdate();
        } else if (id == :burst) {
            Application.Storage.setValue(Settings.KEY_BURST, Settings.next(Settings.BURST_OPTIONS, Settings.burst()));
            item.setSubLabel(Settings.burstLabel());
            WatchUi.requestUpdate();
        } else if (id == :zoom) {
            WatchUi.pushView(Settings.buildZoomMenu(), new ZoomMenuDelegate(_comms), WatchUi.SLIDE_LEFT);
        }
    }
}

// Picking a level sends it and returns to the main screen to show the result.
class ZoomMenuDelegate extends WatchUi.Menu2InputDelegate {
    private var _comms as Comms;

    function initialize(comms as Comms) {
        Menu2InputDelegate.initialize();
        _comms = comms;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var level = item.getId();
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE); // zoom list
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE); // settings
        if (level instanceof String) {
            _comms.send("zoom", level);
        }
    }
}
