package com.leon.weather.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leon.weather.R
import com.leon.weather.model.Location
import com.leon.weather.model.PopularLocations
import com.leon.weather.ui.components.AttributionFooter
import com.leon.weather.ui.components.LoadingContent
import com.leon.weather.ui.components.MessageContent
import com.leon.weather.ui.components.errorMessage

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onRemoveHistory: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onSelect: (Location) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val select: (Location) -> Unit = { keyboard?.hide(); focus.clearFocus(); onSelect(it) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().testTag("search_screen"),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.WbSunny, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                    Text(stringResource(R.string.brand), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.search_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
            item {
                OutlinedTextField(
                    value = state.query, onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().testTag("city_search"),
                    label = { Text(stringResource(R.string.search_label)) },
                    placeholder = { Text(stringResource(R.string.search_placeholder), style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.clear_search))
                        }
                    },
                    singleLine = true, shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onRetry() }),
                )
            }
            when (val status = state.status) {
                SearchStatus.Idle -> {
                    if (state.history.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.history_title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = onClearHistory, modifier = Modifier.testTag("clear_history")) {
                                    Text(stringResource(R.string.clear_history))
                                }
                            }
                        }
                        item { HistoryList(state.history, select, onRemoveHistory) }
                    }
                    if (state.historyUpdateFailed) item {
                        Text(stringResource(R.string.history_update_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.popular_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(PopularLocations.all.chunked(2), key = { it.first().id }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { city -> PopularCityTile(city, select, Modifier.weight(1f)) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                SearchStatus.TooShort -> item { MessageContent(stringResource(R.string.too_short_title), stringResource(R.string.too_short_message)) }
                SearchStatus.Loading -> item { LoadingContent(stringResource(R.string.searching)) }
                SearchStatus.Empty -> item { MessageContent(stringResource(R.string.empty_title), stringResource(R.string.empty_message)) }
                is SearchStatus.Error -> item { MessageContent(stringResource(R.string.search_error_title), errorMessage(status.error), onRetry = onRetry) }
                is SearchStatus.Results -> {
                    item { Text(stringResource(R.string.results_title), modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(status.locations, key = Location::id) { LocationRow(it, select) }
                }
            }
            item { AttributionFooter(Modifier.padding(top = 8.dp), linkEnabled = false) }
        }
    }
}

@Composable
private fun HistoryList(locations: List<Location>, onSelect: (Location) -> Unit, onRemove: (Long) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.testTag("search_history")) {
        locations.forEachIndexed { index, location ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(location) }.testTag("history_${location.id}")
                    .heightIn(min = 48.dp).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                Text(location.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(location.subtitle, modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onRemove(location.id) }, modifier = Modifier.testTag("remove_history_${location.id}")) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.remove_history_city, location.name), tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                }
            }
            if (index < locations.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun PopularCityTile(location: Location, onSelect: (Location) -> Unit, modifier: Modifier) {
    Card(onClick = { onSelect(location) }, modifier = modifier.testTag("location_${location.id}"), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(location.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(location.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LocationRow(location: Location, onSelect: (Location) -> Unit) {
    Card(onClick = { onSelect(location) }, modifier = Modifier.fillMaxWidth().testTag("location_${location.id}"),
        shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(location.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(location.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
    }
}
