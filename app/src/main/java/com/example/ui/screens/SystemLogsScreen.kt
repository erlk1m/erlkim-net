package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.LogSeverity
import com.example.data.model.LogSeverityFilter
import com.example.data.model.RouterLogEntry
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalErlkimColors
import com.example.ui.viewmodel.ErlkimUiState

const val ROUTE_SYSTEM_LOGS = "system_logs"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemLogsScreen(
    uiState: ErlkimUiState,
    onNavigateBack: () -> Unit,
    onRefreshLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val pullRefreshState = rememberPullToRefreshState()

    var selectedFilter by remember { mutableStateOf(LogSeverityFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val infoCount = remember(uiState.logs) { uiState.logs.count { it.severity == LogSeverity.INFO } }
    val warnCount = remember(uiState.logs) { uiState.logs.count { it.severity == LogSeverity.WARNING } }
    val errCount = remember(uiState.logs) { uiState.logs.count { it.severity == LogSeverity.ERROR } }

    val filteredLogs = remember(uiState.logs, selectedFilter, searchQuery) {
        val q = searchQuery.trim()
        uiState.logs.filter { log ->
            val matchesSeverity = when (selectedFilter) {
                LogSeverityFilter.ALL -> true
                LogSeverityFilter.INFO -> log.severity == LogSeverity.INFO
                LogSeverityFilter.WARNING -> log.severity == LogSeverity.WARNING
                LogSeverityFilter.ERROR -> log.severity == LogSeverity.ERROR
            }
            val matchesQuery = q.isBlank() ||
                log.title.contains(q, ignoreCase = true) ||
                log.detail.contains(q, ignoreCase = true) ||
                log.topics.contains(q, ignoreCase = true) ||
                log.time.contains(q, ignoreCase = true)
            matchesSeverity && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background)
            .testTag("system_logs_screen"),
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.title_system_logs),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = cs.surfaceContainerHigh,
                    ) {
                        Text(
                            text = "${filteredLogs.size}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = cs.onSurface,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("system_logs_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_navigate_back),
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onRefreshLogs,
                    modifier = Modifier.testTag("system_logs_refresh_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = cs.background,
                titleContentColor = cs.onBackground,
                navigationIconContentColor = cs.onBackground,
                actionIconContentColor = cs.onBackground,
            ),
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefreshLogs,
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("system_logs_pull_refresh"),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = cs.surfaceContainerHighest,
                    color = cs.primary,
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("system_logs_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "logs_search_bar") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = cs.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.cd_search),
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.logs_search_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                                    cursorBrush = SolidColor(cs.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("system_logs_search_input"),
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_clear_search),
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "logs_severity_filters") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LogSeverityFilter.entries.forEach { filter ->
                            val selected = selectedFilter == filter
                            val count = when (filter) {
                                LogSeverityFilter.ALL -> uiState.logs.size
                                LogSeverityFilter.INFO -> infoCount
                                LogSeverityFilter.WARNING -> warnCount
                                LogSeverityFilter.ERROR -> errCount
                            }
                            val accent = when (filter) {
                                LogSeverityFilter.ALL -> cs.primary
                                LogSeverityFilter.INFO -> cs.tertiary
                                LogSeverityFilter.WARNING -> ext.demoAmber
                                LogSeverityFilter.ERROR -> ext.offlineRed
                            }
                            val selectedBg = when (filter) {
                                LogSeverityFilter.ALL -> cs.primaryContainer
                                LogSeverityFilter.INFO -> cs.tertiaryContainer
                                LogSeverityFilter.WARNING -> ext.demoContainer
                                LogSeverityFilter.ERROR -> ext.offlineContainer
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("log_filter_${filter.name.lowercase()}")
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedFilter = filter },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) selectedBg else cs.background,
                                border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.65f)) else BorderStroke(1.dp, cs.outline),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (filter != LogSeverityFilter.ALL) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(accent),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = "${filter.label} ($count)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                        ),
                                        color = if (selected) accent else cs.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                if (filteredLogs.isEmpty()) {
                    item(key = "logs_empty_state") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
                            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ReceiptLong,
                                    contentDescription = null,
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(38.dp),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.logs_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = cs.onSurface,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.logs_empty_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(filteredLogs, key = { it.id }) { log ->
                        SystemLogCard(log = log)
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemLogCard(
    log: RouterLogEntry,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current

    val (badgeBg, badgeFg, icon) = when (log.severity) {
        LogSeverity.INFO -> Triple(cs.tertiaryContainer, cs.tertiary, Icons.Rounded.Info)
        LogSeverity.WARNING -> Triple(ext.demoContainer, ext.demoAmber, Icons.Rounded.WarningAmber)
        LogSeverity.ERROR -> Triple(ext.offlineContainer, ext.offlineRed, Icons.Rounded.ErrorOutline)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("log_card_${log.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        border = BorderStroke(
            width = 1.dp,
            color = if (log.severity == LogSeverity.ERROR) {
                ext.offlineRed.copy(alpha = 0.35f)
            } else {
                cs.outlineVariant.copy(alpha = 0.55f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBg,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = log.severity.label,
                                tint = badgeFg,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = log.severity.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                ),
                                color = badgeFg,
                            )
                        }
                    }

                    Text(
                        text = log.topics,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = log.time,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    color = cs.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = log.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = log.detail,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}
