package com.gimytv.horror.ui

import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gimytv.horror.Movie

@Composable
fun MainTvScreen(
    uiState: TvUiState,
    onCategorySelected: (String) -> Unit,
    onGenreSelected: (String) -> Unit,
    onSortSelected: (String) -> Unit,
    onMovieFocused: (Movie) -> Unit,
    onMovieClicked: (Movie) -> Unit,
    onPlayClicked: () -> Unit,
    onListStateToggle: () -> Unit,
    onRecommendationClicked: (Movie) -> Unit,
    onPlayerCreated: (VideoView) -> Unit
) {
    GimyTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CharcoalDark)
        ) {
            if (uiState.isPlaying) {
                // Fullscreen Player Overlay
                GimyPlayerTvOverlay(
                    m3u8Url = uiState.m3u8Url,
                    movieTitle = uiState.selectedMovie?.title ?: "Gimy TV",
                    isPlaying = uiState.isPlaying,
                    isSeekingMode = uiState.isSeekingMode,
                    isPaused = uiState.isPaused,
                    seekPositionMs = uiState.seekPositionMs,
                    durationMs = uiState.durationMs,
                    onPlayerCreated = onPlayerCreated
                )
            } else {
                // Main Widescreen Selection Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // App Branding Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gimy 鬼魅劇場",
                            color = GimyGreen,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = "Jetpack Compose for TV",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                    }

                    // Top Filter Row
                    FilterBarTv(
                        selectedCategory = uiState.category,
                        selectedGenre = uiState.genre,
                        selectedSort = uiState.sort,
                        onCategorySelected = onCategorySelected,
                        onGenreSelected = onGenreSelected,
                        onSortSelected = onSortSelected
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Split Widescreen Layout (Left 60% Grid, Right 40% Details)
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left Section: Movie Grid (60% Weight)
                        MovieGridTv(
                            movies = uiState.movies,
                            selectedMovieId = uiState.selectedMovie?.id,
                            onMovieFocused = onMovieFocused,
                            onMovieClicked = onMovieClicked,
                            modifier = Modifier.weight(0.6f)
                        )

                        // Right Section: Details Panel (40% Weight)
                        DetailPanelTv(
                            movie = uiState.selectedMovie,
                            synopsis = uiState.synopsis,
                            actors = uiState.actors,
                            director = uiState.director,
                            region = uiState.region,
                            year = uiState.year,
                            listState = uiState.listState,
                            recommendations = uiState.recommendations,
                            onPlayClicked = onPlayClicked,
                            onListStateToggle = onListStateToggle,
                            onRecommendationClicked = onRecommendationClicked,
                            modifier = Modifier.weight(0.4f)
                        )
                    }
                }
            }
        }
    }
}
