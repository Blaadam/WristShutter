import Toybox.Communications;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Timer;

// Sends { "cmd" => String, "id" => Number } to the phone and waits up to 4 s for
// the matching ack. Only one command is in flight at a time; a new one replaces it.
class Comms {
    private const ACK_TIMEOUT_MS = 4000;

    private var _view as MainView;
    private var _timer as Timer.Timer;
    private var _nextId as Number = 1;
    private var _pendingId as Number = -1;
    private var _pendingCmd as String = "";

    function initialize(view as MainView) {
        _view = view;
        _timer = new Timer.Timer();
    }

    function send(cmd as String) as Void {
        var id = _nextId;
        _nextId += 1;
        _pendingId = id;
        _pendingCmd = cmd;

        _timer.stop();
        _timer.start(method(:onTimeout), ACK_TIMEOUT_MS, false);

        _view.showText(cmd.equals("ping") ? "Connecting..." : "Sent...", Graphics.COLOR_WHITE);
        Communications.transmit({ "cmd" => cmd, "id" => id }, null, new SendListener(self, id));
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
        var wasPing = clearPending();

        if (data["ok"] == true) {
            if (wasPing) {
                _view.showText("Phone OK", Graphics.COLOR_GREEN);
            } else {
                _view.showCheck();
                Feedback.ok();
            }
        } else {
            var err = data["err"];
            _view.showText((err instanceof String && err.length() > 0) ? err : "ERROR", Graphics.COLOR_RED);
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

    private function showNoReply(wasPing as Boolean) as Void {
        _view.showText(wasPing ? "No phone" : "No reply", Graphics.COLOR_RED);
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
