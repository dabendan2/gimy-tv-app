package com.gimytv.horror;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;
import android.util.TypedValue;

public class GimyPlayerViewHelper {
    public static class ViewHolder {
        public FrameLayout playerContainer;
        public VideoView videoView;
        public TextView tvLoadingIndicator;
        public TextView tvPlaybackIndicator;
        public TextView tvPlayerTitle;
        public LinearLayout seekOverlayLayout;
        public TextView tvSeekCurrent;
        public TextView tvSeekTotal;
        public SeekBar seekSeekBar;
        public LinearLayout bottomSeekOverlayContainer;
        public FrameLayout previewWindowLayout;
        public TextureView previewTextureView;
        public FrameLayout previewFrame;
        public ImageView previewImageView;
    }

    public static ViewHolder buildPlayerUI(final Activity activity, final FrameLayout rootContainer, final TextureView.SurfaceTextureListener surfaceTextureListener) {
        final ViewHolder holder = new ViewHolder();

        // Full-Screen Video Player Layer
        holder.playerContainer = new FrameLayout(activity);
        holder.playerContainer.setBackgroundColor(Color.BLACK);
        holder.playerContainer.setVisibility(View.GONE);

        holder.videoView = new VideoView(activity);
        FrameLayout.LayoutParams playerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        holder.videoView.setLayoutParams(playerParams);
        holder.playerContainer.addView(holder.videoView);

        // Loading overlay
        holder.tvLoadingIndicator = new TextView(activity);
        holder.tvLoadingIndicator.setText("影片載入中，請稍候...");
        holder.tvLoadingIndicator.setTextSize(20);
        holder.tvLoadingIndicator.setTextColor(Color.WHITE);
        FrameLayout.LayoutParams loaderParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        holder.tvLoadingIndicator.setLayoutParams(loaderParams);
        holder.playerContainer.addView(holder.tvLoadingIndicator);

        // Custom playback action indicator (focus-free and intuitive TV player!)
        holder.tvPlaybackIndicator = new TextView(activity);
        holder.tvPlaybackIndicator.setTextSize(36);
        holder.tvPlaybackIndicator.setTextColor(Color.WHITE);
        holder.tvPlaybackIndicator.setGravity(Gravity.CENTER);
        holder.tvPlaybackIndicator.setPadding(40, 30, 40, 30);
        holder.tvPlaybackIndicator.setBackgroundColor(Color.parseColor("#90000000")); // 56% opacity black card
        holder.tvPlaybackIndicator.setVisibility(View.GONE);
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        holder.tvPlaybackIndicator.setLayoutParams(indicatorParams);
        holder.playerContainer.addView(holder.tvPlaybackIndicator);

        // Player title indicator (shown in the top-left corner on pause!)
        holder.tvPlayerTitle = new TextView(activity);
        holder.tvPlayerTitle.setTextSize(22);
        holder.tvPlayerTitle.setTextColor(Color.WHITE);
        holder.tvPlayerTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        holder.tvPlayerTitle.setPadding(40, 20, 40, 20);
        holder.tvPlayerTitle.setBackgroundColor(Color.parseColor("#90000000")); // 56% opacity black card
        holder.tvPlayerTitle.setVisibility(View.GONE);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        titleParams.setMargins(60, 60, 0, 0); // Margin from top-left corner
        holder.tvPlayerTitle.setLayoutParams(titleParams);
        holder.playerContainer.addView(holder.tvPlayerTitle);

        // Bottom Seek Overlay Container (placed at Gravity.BOTTOM)
        holder.bottomSeekOverlayContainer = new LinearLayout(activity);
        holder.bottomSeekOverlayContainer.setOrientation(LinearLayout.VERTICAL);
        holder.bottomSeekOverlayContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        holder.bottomSeekOverlayContainer.setVisibility(View.GONE);

        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        holder.bottomSeekOverlayContainer.setLayoutParams(containerParams);

        // Floating 1-Frame Preview Layout (above Seek Bar, moves horizontally)
        holder.previewWindowLayout = new FrameLayout(activity);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewParams.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, activity.getResources().getDisplayMetrics());
        holder.previewWindowLayout.setLayoutParams(previewParams);

        int frameWidthPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 150, activity.getResources().getDisplayMetrics());
        int frameHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 84, activity.getResources().getDisplayMetrics());

        holder.previewFrame = new FrameLayout(activity);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                frameWidthPx, frameHeightPx, Gravity.LEFT | Gravity.TOP);
        holder.previewFrame.setLayoutParams(frameParams);

        // Solid dark charcoal grey background for the frame so it looks nice on a black screen
        android.graphics.drawable.GradientDrawable bgDrawable = new android.graphics.drawable.GradientDrawable();
        bgDrawable.setColor(Color.parseColor("#FF2C2C2C")); // Solid dark grey card
        bgDrawable.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, activity.getResources().getDisplayMetrics()));
        holder.previewFrame.setBackground(bgDrawable);

        // Create ImageView for preview frame
        holder.previewImageView = new ImageView(activity);
        FrameLayout.LayoutParams ivParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        holder.previewImageView.setLayoutParams(ivParams);
        holder.previewImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        holder.previewImageView.setVisibility(View.VISIBLE);
        holder.previewFrame.addView(holder.previewImageView);

        // Center Frame contains the real-time previewTextureView
        holder.previewTextureView = new TextureView(activity);
        FrameLayout.LayoutParams pVideoParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        holder.previewTextureView.setLayoutParams(pVideoParams);
        holder.previewFrame.addView(holder.previewTextureView);

        holder.previewTextureView.setSurfaceTextureListener(surfaceTextureListener);

        // Center Frame White Border Overlay
        View borderView = new View(activity);
        FrameLayout.LayoutParams borderParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        borderView.setLayoutParams(borderParams);

        android.graphics.drawable.GradientDrawable centerBorder = new android.graphics.drawable.GradientDrawable();
        centerBorder.setColor(Color.TRANSPARENT);
        centerBorder.setStroke(
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, activity.getResources().getDisplayMetrics()),
            Color.WHITE
        );
        centerBorder.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, activity.getResources().getDisplayMetrics()));
        borderView.setBackground(centerBorder);
        holder.previewFrame.addView(borderView);

        holder.previewWindowLayout.addView(holder.previewFrame);

        // Initially invisible
        holder.previewWindowLayout.setVisibility(View.GONE);
        
        holder.bottomSeekOverlayContainer.addView(holder.previewWindowLayout);

        // Custom TV seek progress timeline overlay (placed at the bottom inside container)
        holder.seekOverlayLayout = new LinearLayout(activity);
        holder.seekOverlayLayout.setOrientation(LinearLayout.HORIZONTAL);
        holder.seekOverlayLayout.setGravity(Gravity.CENTER_VERTICAL);
        holder.seekOverlayLayout.setBackgroundColor(Color.parseColor("#CC121212")); // 80% opacity dark grey
        holder.seekOverlayLayout.setPadding(50, 30, 50, 30);
        
        LinearLayout.LayoutParams seekOverlayParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        holder.seekOverlayLayout.setLayoutParams(seekOverlayParams);

        // Current Seek Time
        holder.tvSeekCurrent = new TextView(activity);
        holder.tvSeekCurrent.setText("00:00");
        holder.tvSeekCurrent.setTextColor(Color.WHITE);
        holder.tvSeekCurrent.setTextSize(14);
        holder.tvSeekCurrent.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        holder.tvSeekCurrent.setPadding(0, 0, 30, 0);
        holder.seekOverlayLayout.addView(holder.tvSeekCurrent);

        // SeekBar (Timeline)
        holder.seekSeekBar = new SeekBar(activity);
        holder.seekSeekBar.setFocusable(false); // DO NOT allow remote control focus to get stuck!
        holder.seekSeekBar.setClickable(false);
        LinearLayout.LayoutParams seekBarParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f); // Takes up all middle space
        holder.seekSeekBar.setLayoutParams(seekBarParams);
        holder.seekOverlayLayout.addView(holder.seekSeekBar);

        // Total Duration Time
        holder.tvSeekTotal = new TextView(activity);
        holder.tvSeekTotal.setText("00:00");
        holder.tvSeekTotal.setTextColor(Color.parseColor("#9AA0A6"));
        holder.tvSeekTotal.setTextSize(14);
        holder.tvSeekTotal.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        holder.tvSeekTotal.setPadding(30, 0, 0, 0);
        holder.seekOverlayLayout.addView(holder.tvSeekTotal);

        holder.bottomSeekOverlayContainer.addView(holder.seekOverlayLayout);
        holder.playerContainer.addView(holder.bottomSeekOverlayContainer);

        rootContainer.addView(holder.playerContainer);

        return holder;
    }
}
