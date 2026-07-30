package com.gimytv.horror.ui

import com.gimytv.horror.Movie

data class TvUiState(
    val category: String = "1",
    val genre: String = "10",
    val sort: String = "hits",
    val movies: List<Movie> = emptyList(),
    val selectedMovie: Movie? = null,
    val synopsis: String = "載入簡介中...",
    val actors: String = "未知",
    val director: String = "未知",
    val region: String = "華語",
    val year: String = "2026",
    val playPath: String = "",
    val recommendations: List<Movie> = emptyList(),
    val listState: Int = 0,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val m3u8Url: String = "",
    val isSeekingMode: Boolean = false,
    val seekPositionMs: Int = 0,
    val durationMs: Int = 0,
    val isPaused: Boolean = false
)
