import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

enum {
    MODE_TEXT,
    MODE_COUNTDOWN,
    MODE_CHECK
}

// The only view. System fonts and vector drawing only, to keep memory low.
class MainView extends WatchUi.View {
    private var _mode as Number = MODE_TEXT;
    private var _text as String = "Connecting...";
    private var _color as Graphics.ColorType = Graphics.COLOR_WHITE;
    private var _digit as Number = 0;
    private var _roundTripMs as Number = 0;
    // During a countdown or burst, START and BACK cancel, and UP/DOWN are ignored.
    private var _cancelable as Boolean = false;

    function initialize() {
        View.initialize();
    }

    function showText(text as String, color as Graphics.ColorType) as Void {
        _mode = MODE_TEXT;
        _text = text;
        _color = color;
        WatchUi.requestUpdate();
    }

    function showCountdown(secs as Number) as Void {
        _mode = MODE_COUNTDOWN;
        _digit = secs;
        WatchUi.requestUpdate();
    }

    // [roundTripMs]: watch press to phone ack, shown under the check mark.
    function showCheck(roundTripMs as Number) as Void {
        _mode = MODE_CHECK;
        _roundTripMs = roundTripMs;
        WatchUi.requestUpdate();
    }

    function setCancelable(cancelable as Boolean) as Void {
        _cancelable = cancelable;
        WatchUi.requestUpdate();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        var cx = dc.getWidth() / 2;
        var cy = dc.getHeight() / 2;
        var center = Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER;

        if (_mode == MODE_COUNTDOWN) {
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, cy, Graphics.FONT_NUMBER_THAI_HOT, _digit.toString(), center);
        } else if (_mode == MODE_CHECK) {
            // Drawn rather than using a glyph, since the system fonts may not include a check mark.
            dc.setColor(Graphics.COLOR_GREEN, Graphics.COLOR_TRANSPARENT);
            dc.setPenWidth(12);
            dc.drawLine(cx - 42, cy, cx - 12, cy + 30);
            dc.drawLine(cx - 12, cy + 30, cx + 44, cy - 32);
            dc.setPenWidth(1);
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            var secs = (_roundTripMs / 100).toFloat() / 10;
            dc.drawText(cx, cy + 42, Graphics.FONT_TINY, secs.format("%.1f") + " s", Graphics.TEXT_JUSTIFY_CENTER);
        } else {
            // Slightly above centre so it clears the Flip label at 9 o'clock.
            dc.setColor(_color, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, cy - 24, Graphics.FONT_MEDIUM, _text, center);
        }
        drawButtonLabels(dc, cx, cy);
    }

    // Small labels beside each button. Positions follow the FR55's buttons in the SDK's
    // simulator.json: START at 2 o'clock, BACK at 4, DOWN at 8, UP at 9.
    private function drawButtonLabels(dc as Graphics.Dc, cx as Number, cy as Number) as Void {
        var font = Graphics.FONT_XTINY;
        var right = Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER;
        var left = Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER;
        var w = dc.getWidth();
        var edge = w * 12 / 208; // inset from the round edge, scaled for other screen sizes
        var dx = w * 86 / 208;   // horizontal offset of the 2/4/8 o'clock labels
        var dy = w * 52 / 208;   // vertical offset of the 2/4/8 o'clock labels

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        if (_cancelable) {
            dc.drawText(cx + dx, cy - dy, font, "Cancel", right);
            dc.drawText(cx + dx, cy + dy, font, "Cancel", right);
            return;
        }
        dc.drawText(cx + dx, cy - dy, font, "Shoot", right);
        dc.drawText(cx + dx, cy + dy, font, "Exit", right);
        dc.drawText(cx - dx, cy + dy, font, "Video", left);
        dc.drawText(edge, cy, font, "Flip", left);
        dc.drawText(cx, w - edge - w * 14 / 208, font, "Hold UP: menu", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}
