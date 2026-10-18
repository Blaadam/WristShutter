import Toybox.Application;
import Toybox.Lang;
import Toybox.WatchUi;

module Settings {
    const KEY_COUNTDOWN = "countdown";
    const COUNTDOWN_OPTIONS = [0, 3, 5];

    function countdown() as Number {
        var v = Application.Storage.getValue(KEY_COUNTDOWN);
        return (v instanceof Number) ? v : 0;
    }

    function setCountdown(secs as Number) as Void {
        Application.Storage.setValue(KEY_COUNTDOWN, secs);
    }

    function buildMenu() as WatchUi.Menu2 {
        var menu = new WatchUi.Menu2({ :title => "Countdown" });
        var current = countdown();
        for (var i = 0; i < COUNTDOWN_OPTIONS.size(); i++) {
            var secs = COUNTDOWN_OPTIONS[i];
            menu.addItem(new WatchUi.MenuItem(secs.toString() + " s", secs == current ? "current" : null, secs, null));
            if (secs == current) {
                menu.setFocus(i);
            }
        }
        return menu;
    }
}

class SettingsMenuDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        if (id instanceof Number) {
            Settings.setCountdown(id);
        }
        WatchUi.popView(WatchUi.SLIDE_DOWN);
    }
}
