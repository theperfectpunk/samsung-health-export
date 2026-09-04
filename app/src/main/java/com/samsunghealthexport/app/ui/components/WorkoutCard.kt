package com.samsunghealthexport.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsunghealthexport.app.model.WorkoutSession
import com.samsunghealthexport.app.ui.theme.BrightGreen
import com.samsunghealthexport.app.ui.theme.DarkCard
import com.samsunghealthexport.app.ui.theme.HeartRed
import com.samsunghealthexport.app.ui.theme.OrangeFlame
import com.samsunghealthexport.app.ui.theme.SamsungBlue
import com.samsunghealthexport.app.util.Formatters

@Composable
fun WorkoutCard(
    session: WorkoutSession,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit,
    onQuickExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = session.summary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkCard)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            // Header Row: Sport icon, Title, Date, and Select Checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .background(SamsungBlue.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = SamsungBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = Formatters.formatDateTime(session.startTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = SamsungBlue,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metrics Grid: Distance, Duration, Avg Pace, Avg HR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Distance
                MetricColumn(
                    label = "DISTANCE",
                    value = Formatters.formatDistance(s.totalDistanceMeters),
                    color = BrightGreen
                )

                // Duration
                MetricColumn(
                    label = "DURATION",
                    value = s.formattedDuration,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Avg Pace
                MetricColumn(
                    label = "AVG PACE",
                    value = s.formattedAvgPace,
                    color = OrangeFlame
                )

                // Avg HR
                MetricColumn(
                    label = "AVG HR",
                    value = s.avgHeartRateBpm?.let { "${it.toInt()} bpm" } ?: "--",
                    color = HeartRed
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer Row: Badges (GPS, Live points count) + Quick Export button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (session.hasGpsRoute) {
                        TagBadge(
                            icon = Icons.Default.LocationOn,
                            text = "GPS Route",
                            color = BrightGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (session.liveDataPoints.isNotEmpty()) {
                        TagBadge(
                            icon = Icons.Default.Speed,
                            text = "${session.liveDataPoints.size} live pts",
                            color = SamsungBlue
                        )
                    }
                }

                IconButton(
                    onClick = onQuickExport,
                    modifier = Modifier
                        .size(34.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export CSV",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.6.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun TagBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
