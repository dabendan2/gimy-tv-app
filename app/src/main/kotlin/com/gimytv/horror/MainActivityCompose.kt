package com.gimytv.horror

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.gimytv.horror.ui.MainTvScreen
import com.gimytv.horror.ui.TvUiState

class MainActivityCompose : ComponentActivity() {

    private var uiState by mutableStateOf(TvUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainTvScreen(
                uiState = uiState,
                onCategorySelected = { cat ->
                    uiState = uiState.copy(category = cat)
                    loadMovies()
                },
                onGenreSelected = { genre ->
                    uiState = uiState.copy(genre = genre)
                    loadMovies()
                },
                onSortSelected = { sort ->
                    uiState = uiState.copy(sort = sort)
                    loadMovies()
                },
                onMovieFocused = { movie ->
                    uiState = uiState.copy(selectedMovie = movie)
                    loadMovieDetails(movie)
                },
                onMovieClicked = { movie ->
                    uiState = uiState.copy(selectedMovie = movie)
                    startPlayback(movie)
                },
                onPlayClicked = {
                    uiState.selectedMovie?.let { startPlayback(it) }
                },
                onListStateToggle = {
                    toggleListState()
                },
                onRecommendationClicked = { recMovie ->
                    uiState = uiState.copy(selectedMovie = recMovie)
                    loadMovieDetails(recMovie)
                },
                onPlayerCreated = { videoView ->
                    // Bind native VideoView to player controller
                }
            )
        }

        loadMovies()
    }

    private fun loadMovies() {
        Thread {
            try {
                val url = if (uiState.category == "my_watchlist") {
                    "my_watchlist"
                } else {
                    "https://gimyplus.com/show/${uiState.genre}--${uiState.sort}---------${uiState.year}.html"
                }
                
                val movies = if (url == "my_watchlist") {
                    emptyList<Movie>()
                } else {
                    val html = GimyParser.fetchHtml(url)
                    GimyParser.parseMoviesFromHtml(html)
                }

                runOnUiThread {
                    uiState = uiState.copy(movies = movies)
                    if (movies.isNotEmpty() && uiState.selectedMovie == null) {
                        uiState = uiState.copy(selectedMovie = movies[0])
                        loadMovieDetails(movies[0])
                    }
                }
            } catch (e: Exception) {
                Log.e("GimyHorror_Compose", "Error loading movies", e)
            }
        }.start()
    }

    private fun loadMovieDetails(movie: Movie) {
        Thread {
            try {
                val url = "https://gimyplus.com/vod/${movie.id}.html"
                val html = GimyParser.fetchHtml(url)
                val details = GimyParser.parseMovieDetails(url, html)

                runOnUiThread {
                    uiState = uiState.copy(
                        synopsis = details[0] ?: "無簡介",
                        playPath = details[1] ?: "",
                        region = details[2] ?: "華語",
                        year = details[3] ?: "2026",
                        actors = details[4] ?: "未知",
                        director = details[5] ?: "未知"
                    )
                }
            } catch (e: Exception) {
                Log.e("GimyHorror_Compose", "Error loading details", e)
            }
        }.start()
    }

    private fun toggleListState() {
        val nextState = (uiState.listState + 1) % 4
        uiState = uiState.copy(listState = nextState)
    }

    private fun startPlayback(movie: Movie) {
        Thread {
            try {
                val playUrl = "https://gimyplus.com${uiState.playPath}"
                val epHtml = GimyParser.fetchHtml(playUrl)
                val m3u8Url = GimyParser.parseM3U8Url(epHtml)

                runOnUiThread {
                    uiState = uiState.copy(
                        isPlaying = true,
                        m3u8Url = m3u8Url ?: ""
                    )
                }
            } catch (e: Exception) {
                Log.e("GimyHorror_Compose", "Error starting playback", e)
            }
        }.start()
    }
}
