import Toybox.Application;
import Toybox.Communications;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Timer;

// Sends { "cmd" => String, "id" => Number[, "level" => String] } to the phone and waits up to 4 s for
// the matching ack. Only one command is in flight at a time: button presses are
// ignored while one is waiting (see isBusy), so a double press can't stop a
// recording that just started.
class Comms {
    private const ACK_TIMEOUT_MS = 4000;
    // After a { "ack", "wait" => true } interim reply; the phone itself gives up after 8 s.
    private const LAUNCH_TIMEOUT_MS = 10000;
    // After this many sends in a row with no ack, suggest a watch restart. Once, the watch's
    // Connect IQ messaging got stuck: Bluetooth stayed connected and Garmin Connect saw the watch,
    // but nothing went out until the watch was restarted. Stored so it counts across app launches.
    private const STUCK_AFTER = 3;
    private const KEY_FAILURES = "failures";

    private var _view as MainView;
    private var _timer as Timer.Timer;
    private var _nextId as Number = 1;
    private var _pendingId as Number = -1;
    private var _pendingCmd as String = "";
    private var _sentAt as Number = 0;

    function initialize(view as MainView) {
        _view = view;
        _timer = new Timer.Timer();
    }

    // True while a camera command waits for its ack. The startup ping doesn't count.
    function isBusy() as Boolean {
        return _pendingId >= 0 && !_pendingCmd.equals("ping");
    }

    // [level] is the zoom factor for "zoom" (e.g. "3"), null for other commands.
    function send(cmd as String, level as String?) as Void {
        var id = _nextId;
        _nextId += 1;
        _pendingId = id;
        _pendingCmd = cmd;
        _sentAt = System.getTimer();

        _timer.stop();
        _timer.start(method(:onTimeout), ACK_TIMEOUT_MS, false);

        _view.showText(cmd.equals("ping") ? "Connecting..." : "Sent...", Graphics.COLOR_WHITE);
        var msg = { "cmd" => cmd, "id" => id } as Dictionary<Communications.TransmitKeyType, Communications.TransmitType>;
        if (level != null) {
            msg["level"] = level;
        }
        Communications.transmit(msg, null, new SendListener(self, id));
    }

    function onAck(msg as Object?) as Void {
        if (!(msg instanceof Dictionary)) {
            return;
        }
        var data = msg as Dictionary;
        var ack = data["ack"];
        if (!(ack instanceof Number) || ack != _pendingId) {
            return; // stale or unrelated
        }
        if (data["wait"] == true) {
            // The phone is opening Samsung Camera first (a cold start takes about 5 s).
            _timer.stop();
            _timer.start(method(:onTimeout), LAUNCH_TIMEOUT_MS, false);
            _view.showText("Opening camera", Graphics.COLOR_WHITE);
            return;
        }
        var cmd = _pendingCmd;
        var wasPing = clearPending();
        Application.Storage.setValue(KEY_FAILURES, 0);

        if (data["ok"] == true) {
            if (wasPing) {
                _view.showText("Phone OK", Graphics.COLOR_GREEN);
            } else {
                _view.showCheck(System.getTimer() - _sentAt);
                Feedback.ok();
            }
        } else {
            var err = data["err"];
            _view.showText(errorText(cmd, err instanceof String ? err : ""), Graphics.COLOR_RED);
            Feedback.fail();
        }
    }

    // The BLE transmit itself failed (phone out of range, Garmin Connect not running...).
    function onTransmitError(id as Number) as Void {
        if (id == _pendingId) {
            showNoReply(clearPending());
        }
    }

    function onTimeout() as Void {
        if (_pendingId >= 0) {
            showNoReply(clearPending());
        }
    }

    // Short, readable text for the phone's error codes (the screen is 208x208).
    private function errorText(cmd as String, err as String) as String {
        if (err.equals("NOT_FOUND")) {
            // Shutter and record are one button whose meaning depends on the camera mode.
            if (cmd.equals("shoot")) {
                return "Video mode?";
            } else if (cmd.equals("video")) {
                return "Photo mode?";
            }
            if (cmd.equals("zoom")) {
                return "No zoom";
            }
            return "No button";
        } else if (err.equals("NO_CAM")) {
            return "Open camera";
        } else if (err.equals("GESTURE_FAIL")) {
            return "Tap failed";
        } else if (err.equals("BUSY")) {
            return "Too fast";
        } else if (err.equals("NO_A11Y")) {
            return "Phone app off";
        }
        return err.length() > 0 ? err : "ERROR";
    }

    private function showNoReply(wasPing as Boolean) as Void {
        var stored = Application.Storage.getValue(KEY_FAILURES);
        var failures = ((stored instanceof Number) ? stored : 0) + 1;
        Application.Storage.setValue(KEY_FAILURES, failures);

        var text = wasPing ? "No phone" : "No reply";
        if (failures >= STUCK_AFTER) {
            text = "Restart watch?";
        }
        _view.showText(text, Graphics.COLOR_RED);
        if (!wasPing) {
            Feedback.fail();
        }
    }

    // Returns whether the cleared command was a ping.
    private function clearPending() as Boolean {
        _timer.stop();
        var wasPing = _pendingCmd.equals("ping");
        _pendingId = -1;
        _pendingCmd = "";
        return wasPing;
    }
}

class SendListener extends Communications.ConnectionListener {
    private var _comms as Comms;
    private var _id as Number;

    function initialize(comms as Comms, id as Number) {
        ConnectionListener.initialize();
        _comms = comms;
        _id = id;
    }

    function onComplete() as Void {
        // Delivered to Garmin Connect; the real result comes back as an ack.
    }

    function onError() as Void {
        _comms.onTransmitError(_id);
    }
}
