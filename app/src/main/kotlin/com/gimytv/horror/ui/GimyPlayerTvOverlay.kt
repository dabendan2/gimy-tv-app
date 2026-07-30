package com.gimytv.horror.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.gimytv.horror.TimeUtils

@Composable
fun GimyPlayerTvOverlay(
    m3u8Url: String,
    movieTitle: String,
    isPlaying: Boolean,
    isSeekingMode: Boolean,
    isPaused: Boolean,
    seekPositionMs: Int,
    durationMs: Int,
    onPlayerCreated: (VideoView) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Native VideoView embedded in Compose
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    onPlayerCreated(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top-Left Pause Title Indicator ("《片名》 - 暫停")
        // State Rule enforced via TimeUtils.shouldShowPauseTitle
        val showPauseTitle = TimeUtils.shouldShowPauseTitle(isPlaying, isSeekingMode, isPaused)
        if (showPauseTitle) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 32.dp, start = 32.dp)
                    .background(Color(0x90000000), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "《$movieTitle》 - 暫停",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom Timeline & 7-Frame Visual Scrubbing Overlay
        if (isSeekingMode || isPaused) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 24.dp, start = 32.dp, end = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 7-Frame Preview Strip
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(7) { index ->
                        val isCenter = index == 3
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(62.dp)
                                .background(CharcoalCard, shape = RoundedCornerShape(6.dp))
                                .border(
                                    width = if (isCenter) 3.dp else 1.dp,
                                    color = if (isCenter) Color.White else Color.Gray,
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isCenter) "🎯" else "🎞️",
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                // SeekBar Timeline Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x90000000), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = TimeUtils.formatTime(seekPositionMs),
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Slider(
                        value = if (durationMs > 0) seekPositionMs.toFloat() / durationMs else 0f,
                        onValueChange = {},
                        enabled = false,
                        colors = SliderDefaults.colors(
                            disabledThumbColor = GimyGreen,
                            disabledActiveTrackColor = GimyGreen,
                            disabledInactiveTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )

                    Text(
                        text = TimeUtils.formatTime(durationMs),
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
