package com.silentlexx.ffmpeggui_os.activities;

import android.app.Activity;
import android.app.ActionBar;
import android.os.Build;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Keeps interactive activity content clear of system bars and the activity action bar. */
final class EdgeToEdgeContentInsets {

    private EdgeToEdgeContentInsets() {
    }

    static void install(Activity activity) {
        // Edge-to-edge is enforced for targetSdk 35 apps on Android 15 and newer.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }

        View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }

        View actionBar = activity.findViewById(androidx.appcompat.R.id.action_bar_container);
        View finalActionBar = actionBar;
        ActionBar platformActionBar = actionBar == null ? activity.getActionBar() : null;
        View decor = activity.getWindow().getDecorView();
        int baseLeft = content.getPaddingLeft();
        int baseTop = content.getPaddingTop();
        int baseRight = content.getPaddingRight();
        int baseBottom = content.getPaddingBottom();
        Insets[] latestInsets = new Insets[1];

        Runnable updatePadding = () -> {
            Insets bars = latestInsets[0];
            if (bars == null || content.getWidth() == 0 || content.getHeight() == 0
                    || decor.getWidth() == 0 || decor.getHeight() == 0) {
                return;
            }

            int[] contentLocation = new int[2];
            content.getLocationInWindow(contentLocation);

            int protectedTop = bars.top;
            if (finalActionBar != null && finalActionBar.getVisibility() == View.VISIBLE
                    && finalActionBar.getHeight() > 0) {
                int[] actionBarLocation = new int[2];
                finalActionBar.getLocationInWindow(actionBarLocation);
                protectedTop = Math.max(protectedTop,
                        actionBarLocation[1] + finalActionBar.getHeight());
            } else if (platformActionBar != null && platformActionBar.isShowing()
                    && platformActionBar.getHeight() > 0) {
                protectedTop = Math.max(protectedTop,
                        bars.top + platformActionBar.getHeight());
            }

            int leftInset = Math.max(0, bars.left - contentLocation[0]);
            int topInset = Math.max(0, protectedTop - contentLocation[1]);
            int rightInset = Math.max(0,
                    contentLocation[0] + content.getWidth()
                            - (decor.getWidth() - bars.right));
            int bottomInset = Math.max(0,
                    contentLocation[1] + content.getHeight()
                            - (decor.getHeight() - bars.bottom));

            int left = baseLeft + leftInset;
            int top = baseTop + topInset;
            int right = baseRight + rightInset;
            int bottom = baseBottom + bottomInset;
            if (content.getPaddingLeft() != left || content.getPaddingTop() != top
                    || content.getPaddingRight() != right
                    || content.getPaddingBottom() != bottom) {
                content.setPadding(left, top, right, bottom);
            }
        };

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            latestInsets[0] = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.post(updatePadding);
            return windowInsets;
        });
        content.addOnLayoutChangeListener((view, left, top, right, bottom,
                                           oldLeft, oldTop, oldRight, oldBottom) ->
                view.post(updatePadding));
        ViewCompat.requestApplyInsets(content);
    }
}
