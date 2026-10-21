package com.barley.musicplayer.presentation.player

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.barley.musicplayer.R
import com.barley.musicplayer.playback.Chapter
import com.barley.musicplayer.playback.PlayerUiState

@Composable
fun MiniPlayerBar(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onChapterClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDescriptionExpanded by rememberSaveable(state.currentTitle) { mutableStateOf(false) }
    val hasDescriptionText = !state.currentDescription.isNullOrBlank()
    val hasChapters = state.chapters.isNotEmpty()
    val hasExpandableContent = hasDescriptionText || hasChapters

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsyncImage(
                    model = state.currentArtworkUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.currentTitle ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.currentArtist ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (hasExpandableContent) {
                    IconButton(onClick = { isDescriptionExpanded = !isDescriptionExpanded }) {
                        val label = if (isDescriptionExpanded) {
                            stringResource(R.string.player_description_collapse)
                        } else {
                            stringResource(R.string.player_description_expand)
                        }
                        Icon(
                            imageVector = if (isDescriptionExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = label,
                        )
                    }
                }
                IconButton(onClick = onSkipPrevious, enabled = state.hasPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.player_skip_previous))
                }
                IconButton(onClick = onTogglePlayPause) {
                    val label = if (state.isPlaying) {
                        stringResource(R.string.player_pause)
                    } else {
                        stringResource(R.string.player_play)
                    }
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = label,
                    )
                }
                IconButton(onClick = onSkipNext, enabled = state.hasNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.player_skip_next))
                }
            }
            if (isDescriptionExpanded) {
                if (hasDescriptionText) {
                    Text(
                        text = state.currentDescription ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = if (hasChapters) 0.dp else 12.dp,
                            bottom = if (hasChapters) 8.dp else 12.dp,
                        ),
                    )
                }
                if (hasChapters) {
                    val currentChapterIndex = remember(state.chapters, state.currentPositionMillis) {
                        val positionSeconds = state.currentPositionMillis / 1000
                        state.chapters.lastOrNull { it.startSeconds <= positionSeconds }?.index ?: -1
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(state.chapters, key = { it.index }) { chapter ->
                            ChapterRow(
                                chapter = chapter,
                                isPlaying = chapter.index == currentChapterIndex,
                                onClick = { onChapterClick(chapter.index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(chapter: Chapter, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = formatTimestamp(chapter.startSeconds),
            style = MaterialTheme.typography.bodySmall,
            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatTimestamp(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
