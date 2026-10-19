import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Timer;
import Toybox.WatchUi;

// FR55 buttons: START = onSelect, UP = onPreviousPage, DOWN = onNextPage,
// BACK = onBack, long-press UP = onMenu.
class MainDelegate extends WatchUi.BehaviorDelegate {
    private const BURST_INTERVAL_MS = 2000;

    private var _view as MainView;
    private var _comms as Comms;
    private var _timer as Timer.Timer;
    private var _remaining as Number = 0;
    private var _burstLeft as Number = 0;

    function initialize(view as MainView, comms as Comms) {
        BehaviorDelegate.initialize();
        _view = view;
        _comms = comms;
        _timer = new Timer.Timer();
    }

    // START: the shutter (photo, or start/stop recording in Video mode), after the countdown if one
    // is set. Stopping a recording is immediate. START again cancels a countdown or burst.
    function onSelect() as Boolean {
        if (_remaining > 0) {
            cancelCountdown();
            return true;
        }
        if (_burstLeft > 0) {
            cancelBurst();
            return true;
        }
        if (_comms.isBusy()) {
            return true;
        }
        var secs = Settings.countdown();
        if (secs <= 0 || _view.cameraMode().equals("recording")) {
            startShooting();
        } else {
            _remaining = secs;
            updateLabels();
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
            startShooting();
        } else {
            _view.showCountdown(_remaining);
            Feedback.tick();
        }
    }

    // First shot now; with burst on, the rest follow every BURST_INTERVAL_MS. Burst is for photos
    // only: in Video mode it would start and stop recordings.
    private function startShooting() as Void {
        var mode = _view.cameraMode();
        _comms.send("shoot", null);
        _burstLeft = (mode.equals("video") || mode.equals("recording")) ? 0 : Settings.burst() - 1;
        updateLabels();
        if (_burstLeft > 0) {
            _timer.start(method(:onBurstTick), BURST_INTERVAL_MS, true);
        }
    }

    function onBurstTick() as Void {
        // If the previous shot's ack is late, wait for the next tick rather than double-sending.
        if (_comms.isBusy()) {
            return;
        }
        _comms.send("shoot", null);
        _burstLeft -= 1;
        if (_burstLeft <= 0) {
            _timer.stop();
            updateLabels();
        }
    }

    // UP
    function onPreviousPage() as Boolean {
        if (canSend()) {
            _comms.send("flip", null);
        }
        return true;
    }

    // DOWN: switch the camera between Photo and Video.
    function onNextPage() as Boolean {
        if (canSend()) {
            _comms.send("mode", null);
        }
        return true;
    }

    // Long-press UP
    function onMenu() as Boolean {
        if (canSend()) {
            WatchUi.pushView(Settings.buildMenu(), new SettingsMenuDelegate(_comms), WatchUi.SLIDE_UP);
        }
        return true;
    }

    // BACK cancels a countdown or burst, otherwise exits the app (default behaviour).
    function onBack() as Boolean {
        if (_remaining > 0) {
            cancelCountdown();
            return true;
        }
        if (_burstLeft > 0) {
            cancelBurst();
            return true;
        }
        return false;
    }

    // Presses are ignored during a countdown or burst and while the last command awaits its ack.
    private function canSend() as Boolean {
        return _remaining == 0 && _burstLeft == 0 && !_comms.isBusy();
    }

    // Button labels read "Cancel" while a countdown or burst is running.
    private function updateLabels() as Void {
        _view.setCancelable(_remaining > 0 || _burstLeft > 0);
    }

    private function cancelBurst() as Void {
        _timer.stop();
        _burstLeft = 0;
        updateLabels();
        _view.showText("Cancelled", Graphics.COLOR_WHITE);
    }

    private function cancelCountdown() as Void {
        _timer.stop();
        _remaining = 0;
        updateLabels();
        _view.showText("Cancelled", Graphics.COLOR_WHITE);
    }
}
