package com.example.novara.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.novara.ui.theme.TerracottaPrimary

@Composable
fun ProfileSwitcherDialog(
    activeLearnerId: String?,
    savedLearnerIds: List<String>,
    onSelectProfile: (String) -> Unit,
    onCreateNewProfile: () -> Unit,
    onRemoveProfile: (String) -> Unit,
    onSignOut: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = TerracottaPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Manage Learner Profiles",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Saved Local Profiles:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    items(savedLearnerIds) { learnerId ->
                        val isActive = learnerId == activeLearnerId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    if (!isActive) {
                                        onSelectProfile(learnerId)
                                    }
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = learnerId,
                                    fontSize = 15.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    IconButton(
                                        onClick = { onRemoveProfile(learnerId) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Profile",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Create New Profile Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCreateNewProfile() },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "+ Create New Profile",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sign Out Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSignOut() },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sign Out Active Profile",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}
