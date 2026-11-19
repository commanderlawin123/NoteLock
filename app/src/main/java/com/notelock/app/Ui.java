package com.notelock.app;

import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/** Small UI helpers shared by the screens. */
final class Ui {
    private Ui() {}

    /**
     * Android 15+ draws apps edge to edge (behind the status and navigation bars).
     * This pads a screen's root view so nothing sits under those bars.
     */
    static void fitSystemBars(View root) {
        if (Build.VERSION.SDK_INT < 35) return; // older Android already keeps content clear
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(),
                r = root.getPaddingRight(), b = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom);
            return insets;
        });
        root.requestApplyInsets();
    }
}
