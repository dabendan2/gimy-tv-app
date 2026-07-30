package com.gimytv.horror.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gimytv.horror.Movie

@Composable
fun DetailPanelTv(
    movie: Movie?,
    synopsis: String,
    actors: String,
    director: String,
    region: String,
    year: String,
    listState: Int,
    recommendations: List<Movie>,
    onPlayClicked: () -> Unit,
    onListStateToggle: () -> Unit,
    onRecommendationClicked: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(CharcoalCard, shape = RoundedCornerShape(12.dp))
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        if (movie == null) {
            Text(
                text = "請選擇影片查看詳情",
                color = TextGray,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            // Movie Title
            Text(
                text = movie.title ?: "未知標題",
                color = TextWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata row (Region / Year / Director)
            Text(
                text = "地區：$region  |  年代：$year  |  導演：$director",
                color = TextGray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Actors row
            Text(
                text = "主演：$actors",
                color = TextGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play Button
                Button(
                    onClick = onPlayClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = GimyGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("播放正片 ▶", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                // Cyclic List State Emoji Button (+ / 📝 / ❤️ / 💩)
                val emojiLabel = when (listState) {
                    1 -> "待播 📝"
                    2 -> "喜歡 ❤️"
                    3 -> "不喜歡 💩"
                    else -> "收藏 +"
                }

                Button(
                    onClick = onListStateToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3C4043)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(emojiLabel, color = TextWhite, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Synopsis Plot Section
            Text(
                text = "劇情簡介",
                color = TextWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = synopsis,
                color = TextWhite,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Recommendations Carousel
            if (recommendations.isNotEmpty()) {
                Text(
                    text = "更多推薦",
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recommendations) { recMovie ->
                        RecommendationCardTv(
                            movie = recMovie,
                            onClick = { onRecommendationClicked(recMovie) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendationCardTv(
    movie: Movie,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(100.dp)
            .height(140.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .background(CharcoalDark, shape = RoundedCornerShape(6.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) GimyGreen else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(6.dp)
    ) {
        Text(
            text = movie.title ?: "",
            color = TextWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
