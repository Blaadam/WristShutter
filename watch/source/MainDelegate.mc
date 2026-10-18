import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Timer;
import Toybox.WatchUi;

// FR55 buttons: START = onSelect, UP = onPreviousPage, DOWN = onNextPage,
// BACK = onBack, long-press UP = onMenu.
class MainDelegate extends WatchUi.BehaviorDelegate {
    private var _view as MainView;
    private var _comms as Comms;
    private var _timer as Timer.Timer;
    private var _remaining as Number = 0;

    function initialize(view as MainView, comms as Comms) {
        BehaviorDelegate.initialize();
        _view = view;
        _comms = comms;
        _timer = new Timer.Timer();
    }

    // START: shoot, after the countdown if one is set. START again cancels a running countdown.
    function onSelect() as Boolean {
        if (_remaining > 0) {
            cancelCountdown();
            return true;
        }
        var secs = Settings.countdown();
        if (secs <= 0) {
            _comms.send("shoot");
        } else {
            _remaining = secs;
            _view.showCountdown(secs);
            Feedback.tick();
            _timer.start(method(:onTick), 1000, true);
        }
        return true;
    }

    function onTick() as Void {
        _remaining -= 1;
        if (_remaining <= 0) {
            _timer.stop();
            _remaining = 0;
            _comms.send("shoot");
        } else {
            _view.showCountdown(_remaining);
            Feedback.tick();
        }
    }

    // UP
    function onPreviousPage() as Boolean {
        if (_remaining == 0) {
            _comms.send("flip");
        }
        return true;
    }

    // DOWN
    function onNextPage() as Boolean {
        if (_remaining == 0) {
            _comms.send("video");
        }
        return true;
    }

    // Long-press UP
    function onMenu() as Boolean {
        WatchUi.pushView(Settings.buildMenu(), new SettingsMenuDelegate(), WatchUi.SLIDE_UP);
        return true;
    }

    // BACK cancels a countdown, otherwise exits the app (default behaviour).
    function onBack() as Boolean {
        if (_remaining > 0) {
            cancelCountdown();
            return true;
        }
        return false;
    }

    private function cancelCountdown() as Void {
        _timer.stop();
        _remaining = 0;
        _view.showText("Cancelled", Graphics.COLOR_WHITE);
    }
}
