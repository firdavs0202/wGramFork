/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.RecyclerListViewWithOverlayDraw;

public class StickerEmojiCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, RecyclerListViewWithOverlayDraw.OverlayView {

    private ImageReceiver imageView;
    private TLRPC.Document sticker;
    private SendMessagesHelper.ImportingSticker stickerPath;
    private Object parentObject;
    private String currentEmoji;
    private TextView emojiTextView;
    private float alpha = 1;
    private boolean changingAlpha;
    private long lastUpdateTime;
    private boolean scaled;
    private float scale;
    private long time;
    private boolean recent;
    private static AccelerateInterpolator interpolator = new AccelerateInterpolator(0.5f);
    private int currentAccount = UserConfig.selectedAccount;
    private boolean fromEmojiPanel;
    private boolean isPremiumSticker;
    private float premiumAlpha = 1f;
    private boolean showPremiumLock;

    int stickerColor;

    private final static int STICKER_SIZE = 66;
    private boolean drawInParentView;

    public StickerEmojiCell(Context context, boolean isEmojiPanel) {
        super(context);

        fromEmojiPanel = isEmojiPanel;

        imageView = new ImageReceiver();
        imageView.setAspectFit(true);
        imageView.setLayerNum(1);

        emojiTextView = new TextView(context);
        emojiTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
       // addView(emojiTextView, LayoutHelper.createFrame(28, 28, Gravity.BOTTOM | Gravity.RIGHT));

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));

        setFocusable(true);
    }

    public TLRPC.Document getSticker() {
        return sticker;
    }

    public SendMessagesHelper.ImportingSticker getStickerPath() {
        return stickerPath != null && stickerPath.validated ? stickerPath : null;
    }

    public String getEmoji() {
        return currentEmoji;
    }

    public Object getParentObject() {
        return parentObject;
    }

    public boolean isRecent() {
        return recent;
    }

    public void setRecent(boolean value) {
        recent = value;
    }

    public void setSticker(TLRPC.Document document, Object parent, boolean showEmoji) {
        setSticker(document, null, parent, null, showEmoji);
    }

    public void setSticker(SendMessagesHelper.ImportingSticker path) {
        setSticker(null, path, null, path.emoji, path.emoji != null);
    }

    public MessageObject.SendAnimationData getSendAnimationData() {
        ImageReceiver imageReceiver = imageView;
        if (!imageReceiver.hasNotThumb()) {
            return null;
        }
        MessageObject.SendAnimationData data = new MessageObject.SendAnimationData();
        int[] position = new int[2];
        getLocationInWindow(position);
        data.x = imageReceiver.getCenterX() + position[0];
        data.y = imageReceiver.getCenterY() + position[1];
        data.width = imageReceiver.getImageWidth();
        data.height = imageReceiver.getImageHeight();
        return data;
    }

    public void setSticker(TLRPC.Document document, SendMessagesHelper.ImportingSticker path, Object parent, String emoji, boolean showEmoji) {
        currentEmoji = emoji;
        isPremiumSticker = MessageObject.isPremiumSticker(document);
        drawInParentView = false;
        imageView.setColorFilter(null);

        if (path != null) {
            stickerPath = path;
            if (path.validated) {
                imageView.setImage(ImageLocation.getForPath(path.path), "80_80", null, null, DocumentObject.getSvgRectThumb(Theme.key_dialogBackgroundGray, 1.0f), 0, path.animated ? "tgs" : null, 0, 1);
            } else {
                imageView.setImage(null, null, null, null, DocumentObject.getSvgRectThumb(Theme.key_dialogBackgroundGray, 1.0f), 0, path.animated ? "tgs" : null, 0, 1);
            }
            if (emoji != null) {
                emojiTextView.setText(Emoji.replaceEmoji(emoji, emojiTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(16), false));
                emojiTextView.setVisibility(VISIBLE);
            } else {
                emojiTextView.setVisibility(INVISIBLE);
            }
        } else if (document != null) {
            sticker = document;
            parentObject = parent;
            //boolean isVideoSticker = MessageObject.isVideoSticker(document);
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, fromEmojiPanel ? Theme.key_emptyListPlaceholder : Theme.key_windowBackgroundGray, fromEmojiPanel ? 0.2f : 1.0f);
            String imageFilter = fromEmojiPanel ? "66_66_pcache_compress" : "66_66";
            if (MessageObject.isTextColorEmoji(document)) {
                imageView.setColorFilter(Theme.chat_animatedEmojiTextColorFilter);
            }
            if (MessageObject.canAutoplayAnimatedSticker(document)) {
                if (fromEmojiPanel) {
                    drawInParentView = true;
                }
                if (svgThumb != null) {
                    imageView.setImage(ImageLocation.getForDocument(document), imageFilter, ImageLocation.getForDocument(thumb, document), null, null, null, svgThumb, 0, null, parentObject, 1);
                    //imageView.setImage(ImageLocation.getForDocument(document), imageFilter, svgThumb, null, parentObject, 1);
                } else if (thumb != null) {
                    imageView.setImage(ImageLocation.getForDocument(document), imageFilter, ImageLocation.getForDocument(thumb, document), null, (String) null, parentObject, 1);
                } else {
                    imageView.setImage(ImageLocation.getForDocument(document), imageFilter, null, null, parentObject, 1);
                }
            } else {
                if (svgThumb != null) {
                    if (thumb != null) {
                        imageView.setImage(ImageLocation.getForDocument(thumb, document), imageFilter,  svgThumb, "webp", parentObject, 1);
                    } else {
                        imageView.setImage(ImageLocation.getForDocument(document), imageFilter, svgThumb, "webp", parentObject, 1);
                    }
                } else if (thumb != null) {
                    imageView.setImage(ImageLocation.getForDocument(thumb, document), imageFilter, null, "webp", parentObject, 1);
                } else {
                    imageView.setImage(ImageLocation.getForDocument(document), imageFilter, null, "webp", parentObject, 1);
                }
            }

            if (emoji != null) {
                emojiTextView.setText(Emoji.replaceEmoji(emoji, emojiTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(16), false));
                emojiTextView.setVisibility(VISIBLE);
            } else if (showEmoji) {
                boolean set = false;
                for (int a = 0; a < document.attributes.size(); a++) {
                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                    if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                        if (attribute.alt != null && attribute.alt.length() > 0) {
                            emojiTextView.setText(Emoji.replaceEmoji(attribute.alt, emojiTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(16), false));
                            set = true;
                        }
                        break;
                    }
                }
                if (!set) {
                    emojiTextView.setText(Emoji.replaceEmoji(MediaDataController.getInstance(currentAccount).getEmojiForSticker(sticker.id), emojiTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(16), false));
                }
                emojiTextView.setVisibility(VISIBLE);
            } else {
                emojiTextView.setVisibility(INVISIBLE);
            }
        }
        imageView.setAlpha(alpha * premiumAlpha);
        if (drawInParentView) {
            imageView.setInvalidateAll(true);
            imageView.setParentView((View) getParent());
        } else {
            imageView.setParentView(this);
        }
    }

    public void disable() {
        changingAlpha = true;
        alpha = 0.5f;
        time = 0;
        imageView.setAlpha(alpha * premiumAlpha);
        imageView.invalidate();
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    public void setScaled(boolean value) {
        scaled = value;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    public boolean isDisabled() {
        return changingAlpha;
    }

    public boolean showingBitmap() {
        return imageView.getBitmap() != null;
    }

    public ImageReceiver getImageView() {
        return imageView;
    }

    @Override
    public void invalidate() {
        if (drawInParentView && getParent() != null) {
            ((View) getParent()).invalidate();
        }
        emojiTextView.invalidate();
        super.invalidate();
    }


    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        String descr = LocaleController.getString("AttachSticker", R.string.AttachSticker);
        if (sticker != null) {
            for (int a = 0; a < sticker.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = sticker.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    if (attribute.alt != null && attribute.alt.length() > 0) {
                        emojiTextView.setText(Emoji.replaceEmoji(attribute.alt, emojiTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(16), false));
                        descr = attribute.alt + " " + descr;
                    }
                    break;
                }
            }
        }
        info.setContentDescription(descr);
        info.setEnabled(true);
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (drawInParentView) {
            imageView.setInvalidateAll(true);
            imageView.setParentView((View) getParent());
        } else {
            imageView.setParentView(this);
        }
        imageView.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageView.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!drawInParentView) {
            drawInternal(this, canvas);
        }
        super.dispatchDraw(canvas);
    }

    @Override
    public void preDraw(View view, Canvas canvas) {
        if (drawInParentView) {
            drawInternal(view, canvas);
        }
    }

    private void drawInternal(View view, Canvas canvas) {
        if (changingAlpha || scaled && scale != 0.8f || !scaled && scale != 1.0f) {
            long newTime = System.currentTimeMillis();
            long dt = (newTime - lastUpdateTime);
            lastUpdateTime = newTime;
            if (changingAlpha) {
                time += dt;
                if (time > 1050) {
                    time = 1050;
                }
                alpha = 0.5f + interpolator.getInterpolation(time / 150.0f) * 0.5f;
                if (alpha >= 1.0f) {
                    changingAlpha = false;
                    alpha = 1.0f;
                }
                imageView.setAlpha(alpha * premiumAlpha);
            } else if (scaled && scale != 0.8f) {
                scale -= dt / 400.0f;
                if (scale < 0.8f) {
                    scale = 0.8f;
                }
            } else {
                scale += dt / 400.0f;
                if (scale > 1.0f) {
                    scale = 1.0f;
                }
            }
            view.invalidate();
        }
        int size = Math.min(AndroidUtilities.dp(STICKER_SIZE), Math.min(getMeasuredHeight(), getMeasuredWidth()));
        int cX = getMeasuredWidth() >> 1;
        int cY = getMeasuredHeight() >> 1;
        imageView.setImageCoords(cX - size / 2f, cY - size / 2f , size, size);
        imageView.setAlpha(alpha * premiumAlpha);
        if (scale != 1f) {
            canvas.save();
            canvas.scale(scale, scale, cX, cY);
            imageView.draw(canvas);
            canvas.restore();
        } else {
            imageView.draw(canvas);
        }
    }

}
