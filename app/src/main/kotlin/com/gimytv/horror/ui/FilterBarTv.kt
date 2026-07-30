package com.gimytv.horror.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class FilterOption(val label: String, val value: String)

@Composable
fun FilterBarTv(
    selectedCategory: String,
    selectedGenre: String,
    selectedSort: String,
    onCategorySelected: (String) -> Unit,
    onGenreSelected: (String) -> Unit,
    onSortSelected: (String) -> Unit
) {
    val categories = listOf(
        FilterOption("電影", "1"),
        FilterOption("連續劇", "2"),
        FilterOption("動漫", "4"),
        FilterOption("綜藝", "29"),
        FilterOption("我的待播 📝", "my_watchlist")
    )

    val genres = listOf(
        FilterOption("恐怖", "10"),
        FilterOption("動作", "6"),
        FilterOption("喜劇", "7"),
        FilterOption("科幻", "9")
    )

    val sorts = listOf(
        FilterOption("總點擊", "hits"),
        FilterOption("週點擊", "hits_week"),
        FilterOption("月點擊", "hits_month")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        // Categories Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            items(categories) { cat ->
                FilterChipTv(
                    label = cat.label,
                    isSelected = selectedCategory == cat.value,
                    onClick = { onCategorySelected(cat.value) }
                )
            }
        }

        // Genres & Sort Row
        if (selectedCategory != "my_watchlist") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(genres) { g ->
                    FilterChipTv(
                        label = g.label,
                        isSelected = selectedGenre == g.value,
                        onClick = { onGenreSelected(g.value) }
                    )
                }
                items(sorts) { s ->
                    FilterChipTv(
                        label = "排序: ${s.label}",
                        isSelected = selectedSort == s.value,
                        onClick = { onSortSelected(s.value) }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChipTv(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        isFocused -> GimyGreen
        isSelected -> Color(0xFF2E7D32)
        else -> CharcoalCard
    }

    val textColor = when {
        isFocused -> Color.Black
        else -> TextWhite
    }

    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .background(bgColor, shape = RoundedCornerShape(16.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) TextWhite else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}
