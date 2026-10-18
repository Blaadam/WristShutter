import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.WatchUi;

class WristShutterApp extends Application.AppBase {
    private var _comms as Comms?;

    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() {
        var view = new MainView();
        var comms = new Comms(view);
        _comms = comms;
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
        comms.send("ping");
        return [view, new MainDelegate(view, comms)];
    }

    // Acks from the phone: { "ack" => id, "ok" => Boolean, "err" => String }
    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        var comms = _comms;
        if (comms != null) {
            comms.onAck(msg.data);
        }
    }
}
