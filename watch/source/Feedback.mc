import Toybox.Attention;
import Toybox.Lang;

module Feedback {
    // One pulse per countdown second.
    function tick() as Void {
        vibe([new Attention.VibeProfile(40, 60)]);
    }

    // Phone acked ok.
    function ok() as Void {
        vibe([new Attention.VibeProfile(60, 150)]);
    }

    // Error or no reply: double pulse.
    function fail() as Void {
        vibe([
            new Attention.VibeProfile(80, 150),
            new Attention.VibeProfile(0, 100),
            new Attention.VibeProfile(80, 150)
        ]);
    }

    function vibe(pattern as Array<Attention.VibeProfile>) as Void {
        if (Attention has :vibrate) {
            Attention.vibrate(pattern);
        }
    }
}
