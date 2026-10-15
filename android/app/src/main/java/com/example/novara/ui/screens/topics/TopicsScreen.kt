package com.example.novara.ui.screens.topics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.novara.data.model.TopicDetail
import com.example.novara.data.model.TopicSummary
import com.example.novara.ui.theme.OchreAccent
import com.example.novara.ui.theme.OliveSecondary
import com.example.novara.ui.theme.TerracottaPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(
    learnerId: String,
    viewModel: TopicsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(learnerId) {
        viewModel.loadTopics(learnerId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TerracottaPrimary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column {
                    Text(
                        text = "Spanish Learning Hub",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "Master curated vocabulary & interactive quizzes",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TerracottaPrimary)
            }
        } else if (uiState.selectedTopicDetail != null) {
            // Topic Detail & Vocabulary View
            TopicDetailView(
                detail = uiState.selectedTopicDetail!!,
                quizResult = uiState.quizResult,
                isSubmitting = uiState.isDetailLoading,
                onBack = { viewModel.clearSelectedTopic() },
                onSubmitQuiz = { answers ->
                    viewModel.submitQuizAnswers(uiState.selectedTopicDetail!!.topicId, learnerId, answers)
                }
            )
        } else {
            // Topics List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.topics) { topic ->
                    TopicItemCard(topic = topic) {
                        viewModel.loadTopicDetail(topic.topicId)
                    }
                }
            }
        }
    }
}

@Composable
fun TopicItemCard(
    topic: TopicSummary,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (topic.completed) OliveSecondary.copy(alpha = 0.2f) else OchreAccent.copy(alpha = 0.2f),
                modifier = Modifier.padding(end = 14.dp)
            ) {
                Icon(
                    imageVector = if (topic.completed) Icons.Default.CheckCircle else Icons.Default.Book,
                    contentDescription = null,
                    tint = if (topic.completed) OliveSecondary else TerracottaPrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Module ${topic.orderIndex}: ${topic.title}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (topic.completed) "Completed • Mastered Vocabulary" else "In Progress • Tap to study & practice",
                    fontSize = 12.sp,
                    color = if (topic.completed) OliveSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TopicDetailView(
    detail: TopicDetail,
    quizResult: com.example.novara.data.model.QuizResult?,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onSubmitQuiz: (List<Int>) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("← Back to Modules", color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = detail.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerracottaPrimary
                )
            }
        }

        // Vocabulary Section
        item {
            Text(
                text = "Curated Vocabulary & Cultural Notes",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(detail.vocabulary) { vocab ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = vocab.phrase,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerracottaPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Meaning: ${vocab.meaning}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "💡 ${vocab.culturalNote}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = OliveSecondary
                    )
                }
            }
        }

        // Quiz Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Interactive Knowledge Check",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (quizResult != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = OliveSecondary.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = OliveSecondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Quiz Submitted Successfully!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = OliveSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Score: ${(quizResult.score * 100).toInt()}% (${quizResult.correctCount}/${quizResult.total} correct)",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        quizResult.paceChangeReason?.let { reason ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "🚀 Adaptive Pace Update: $reason",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TerracottaPrimary
                            )
                        }
                    }
                }
            }
        } else {
            items(detail.quiz) { question ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = question.question,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        question.quizOptionsIfAny().forEachIndexed { index, option ->
                            Button(
                                onClick = { onSubmitQuiz(listOf(index)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary.copy(alpha = 0.85f)),
                                enabled = !isSubmitting
                            ) {
                                Text(option, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun com.example.novara.data.model.QuizQuestion.quizOptionsIfAny(): List<String> = options
