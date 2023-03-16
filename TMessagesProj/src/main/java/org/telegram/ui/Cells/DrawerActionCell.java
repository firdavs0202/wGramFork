/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Set;

public class DrawerActionCell extends FrameLayout {

    private AnimatedTextView textView;
    private int currentId;
    private RectF rect = new RectF();

    public DrawerActionCell(Context context) {
        super(context);

        textView = new AnimatedTextView(context, true, true, true);
        textView.setAnimationProperties(.6f, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
        textView.setTextSize(AndroidUtilities.dp(15));
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setIgnoreRTL(true);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 20, 0, 0, 0));

        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (currentId == 8) {
            Set<String> suggestions = MessagesController.getInstance(UserConfig.selectedAccount).pendingSuggestions;
            if (suggestions.contains("VALIDATE_PHONE_NUMBER") || suggestions.contains("VALIDATE_PASSWORD")) {
                int countTop = AndroidUtilities.dp(12.5f);
                int countWidth = AndroidUtilities.dp(9);
                int countLeft = getMeasuredWidth() - countWidth - AndroidUtilities.dp(25);

                int x = countLeft - AndroidUtilities.dp(5.5f);
                rect.set(x, countTop, x + countWidth + AndroidUtilities.dp(14), countTop + AndroidUtilities.dp(23));
                Theme.chat_docBackPaint.setColor(Theme.getColor(Theme.key_chats_archiveBackground));
                canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, Theme.chat_docBackPaint);

                int w = Theme.dialogs_errorDrawable.getIntrinsicWidth();
                int h = Theme.dialogs_errorDrawable.getIntrinsicHeight();
                Theme.dialogs_errorDrawable.setBounds((int) (rect.centerX() - w / 2), (int) (rect.centerY() - h / 2), (int) (rect.centerX() + w / 2), (int) (rect.centerY() + h / 2));
                Theme.dialogs_errorDrawable.draw(canvas);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
    }

    public void setTextAndIcon(int id, String text) {
        currentId = id;
        try {
            textView.setText(text, false);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void updateText(String text) {
        textView.setText(text);
    }


    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
        info.setText(textView.getText());
        info.setClassName(TextView.class.getName());
    }
}
