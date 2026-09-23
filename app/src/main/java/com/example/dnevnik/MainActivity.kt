package com.example.dnevnik

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Lesson(val id: Long, val date: String, val subject: String, val lesson: String, val homework: String, val photoUri: String?)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DiaryApp() }
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val prettyFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("ru"))

private fun todayKey() = dateFormat.format(Date())

private fun shiftDate(dateKey: String, days: Int): String {
    val date = dateFormat.parse(dateKey) ?: Date()
    val calendar = Calendar.getInstance().apply {
        time = date
        add(Calendar.DAY_OF_MONTH, days)
    }
    return dateFormat.format(calendar.time)
}

private fun prettyDate(dateKey: String): String {
    val date = dateFormat.parse(dateKey) ?: Date()
    return prettyFormat.format(date).replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryApp() {
    val context = LocalContext.current
    var lessons by remember { mutableStateOf(loadLessons(context)) }
    var showDialog by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(todayKey()) }
    val dayLessons = lessons.filter { it.date == selectedDate }.sortedBy { it.id }
    val selectedMonth = selectedDate.substring(0, 7)
    val monthLessons = lessons.filter { it.date.startsWith(selectedMonth) }

    Scaffold(topBar = { TopAppBar(title = { Text("📚 Дневник") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            var dragDistance by remember { mutableFloatStateOf(0f) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, amount -> dragDistance += amount },
                            onDragEnd = {
                                if (dragDistance > 80f) selectedDate = shiftDate(selectedDate, -1)
                                else if (dragDistance < -80f) selectedDate = shiftDate(selectedDate, 1)
                                dragDistance = 0f
                            },
                            onDragCancel = { dragDistance = 0f }
                        )
                    }
            ) {
                Text(prettyDate(selectedDate), style = MaterialTheme.typography.headlineSmall)
                Text("Записей за этот день: ${dayLessons.size}  •  За месяц: ${monthLessons.size}")
                Text("← свайп → для смены дня", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(dayLessons, key = { it.id }) { item ->
                    LessonCard(item) {
                        lessons = lessons.filterNot { it.id == item.id }
                        saveLessons(context, lessons)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { showDialog = true }, Modifier.fillMaxWidth()) { Text("➕ Добавить урок") }
        }
    }

    if (showDialog) AddLessonDialog(
        onDismiss = { showDialog = false },
        onAdd = { subject, lesson, homework, photo ->
            lessons = lessons + Lesson(System.currentTimeMillis(), selectedDate, subject, lesson, homework, photo)
            saveLessons(context, lessons)
            showDialog = false
        }
    )
}

@Composable
fun LessonCard(item: Lesson, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(item.subject, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text("Урок: ${item.lesson}")
            Spacer(Modifier.height(4.dp))
            Text("ДЗ: ${item.homework}")
            item.photoUri?.let {
                Spacer(Modifier.height(10.dp))
                AsyncImage(Uri.parse(it), "Фото задания", Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onDelete) { Text("Удалить") }
        }
    }
}

@Composable
fun AddLessonDialog(onDismiss: () -> Unit, onAdd: (String, String, String, String?) -> Unit) {
    val context = LocalContext.current
    var subject by remember { mutableStateOf("") }
    var lesson by remember { mutableStateOf("") }
    var homework by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            photoUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый урок") },
        text = {
            Column {
                OutlinedTextField(subject, { subject = it }, label = { Text("Предмет") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(lesson, { lesson = it }, label = { Text("Урок") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(homework, { homework = it }, label = { Text("ДЗ") })
                Spacer(Modifier.height(10.dp))
                Button(onClick = { picker.launch(arrayOf("image/*")) }) {
                    Text(if (photoUri == null) "📷 Добавить фото" else "📷 Фото выбрано")
                }
                photoUri?.let {
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(Uri.parse(it), null, Modifier.fillMaxWidth().height(120.dp), contentScale = ContentScale.Crop)
                }
            }
        },
        confirmButton = {
            Button(enabled = subject.isNotBlank(), onClick = { onAdd(subject, lesson, homework, photoUri) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun saveLessons(context: Context, lessons: List<Lesson>) {
    val array = JSONArray()
    lessons.forEach { array.put(JSONObject().apply {
        put("id", it.id); put("date", it.date); put("subject", it.subject); put("lesson", it.lesson); put("homework", it.homework); put("photoUri", it.photoUri ?: JSONObject.NULL)
    }) }
    context.getSharedPreferences("diary", Context.MODE_PRIVATE).edit().putString("lessons", array.toString()).apply()
}

private fun loadLessons(context: Context): List<Lesson> {
    val raw = context.getSharedPreferences("diary", Context.MODE_PRIVATE).getString("lessons", null) ?: return emptyList()
    return try {
        val array = JSONArray(raw)
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Lesson(o.getLong("id"), o.getString("date"), o.getString("subject"), o.getString("lesson"), o.getString("homework"), if (o.isNull("photoUri")) null else o.getString("photoUri"))
        }
    } catch (_: Exception) { emptyList() }
}
