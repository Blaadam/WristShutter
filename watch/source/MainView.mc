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

    function showCheck() as Void {
        _mode = MODE_CHECK;
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
        } else {
            dc.setColor(_color, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, cy, Graphics.FONT_MEDIUM, _text, center);
        }
    }
}
