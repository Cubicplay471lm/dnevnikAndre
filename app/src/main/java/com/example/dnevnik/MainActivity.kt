package com.example.dnevnik

import android.content.Context
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
private fun shiftDate(key: String, days: Int): String {
    val c = Calendar.getInstance().apply { time = dateFormat.parse(key) ?: Date(); add(Calendar.DAY_OF_MONTH, days) }
    return dateFormat.format(c.time)
}
private fun prettyDate(key: String): String = (prettyFormat.format(dateFormat.parse(key) ?: Date())).replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryApp() {
    val context = LocalContext.current
    var lessons by remember { mutableStateOf(loadLessons(context)) }
    var selectedDate by remember { mutableStateOf(todayKey()) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Lesson?>(null) }
    val dayLessons = lessons.filter { it.date == selectedDate }.sortedBy { it.id }
    var dragDistance by remember { mutableFloatStateOf(0f) }

    Scaffold(topBar = { TopAppBar(title = { Text("📚 Дневник") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
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
            Text("Записей за день: ${dayLessons.size}")
            Text("← предыдущий день    следующий день →", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(dayLessons, key = { it.id }) { item ->
                    LessonCard(item,
                        onEdit = { editing = item },
                        onDelete = {
                            lessons = lessons.filterNot { it.id == item.id }
                            saveLessons(context, lessons)
                        }
                    )
                }
            }
            Button(onClick = { showAdd = true }, Modifier.fillMaxWidth()) { Text("➕ Добавить урок") }
        }
    }

    if (showAdd) LessonDialog(null, { showAdd = false }) { subject, lesson, homework, photo ->
        lessons = lessons + Lesson(System.currentTimeMillis(), selectedDate, subject, lesson, homework, photo)
        saveLessons(context, lessons); showAdd = false
    }
    editing?.let { old ->
        LessonDialog(old, { editing = null }) { subject, lesson, homework, photo ->
            lessons = lessons.map { if (it.id == old.id) old.copy(subject = subject, lesson = lesson, homework = homework, photoUri = photo) else it }
            saveLessons(context, lessons); editing = null
        }
    }
}

@Composable
fun LessonCard(item: Lesson, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(item.subject, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp)); Text("Урок: ${item.lesson}")
            Spacer(Modifier.height(4.dp)); Text("ДЗ: ${item.homework}")
            item.photoUri?.let { uri ->
                Spacer(Modifier.height(10.dp))
                AsyncImage(Uri.parse(uri), "Фото задания", Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit) { Text("✏️ Изменить") }
                OutlinedButton(onClick = onDelete) { Text("Удалить") }
            }
        }
    }
}

@Composable
fun LessonDialog(existing: Lesson?, onDismiss: () -> Unit, onSave: (String, String, String, String?) -> Unit) {
    var subject by remember(existing?.id) { mutableStateOf(existing?.subject ?: "") }
    var lesson by remember(existing?.id) { mutableStateOf(existing?.lesson ?: "") }
    var homework by remember(existing?.id) { mutableStateOf(existing?.homework ?: "") }
    var photoUri by remember(existing?.id) { mutableStateOf(existing?.photoUri) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { photoUri = it.toString() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новый урок" else "Изменить урок") },
        text = {
            Column {
                OutlinedTextField(subject, { subject = it }, label = { Text("Предмет") }, singleLine = true)
                Spacer(Modifier.height(8.dp)); OutlinedTextField(lesson, { lesson = it }, label = { Text("Урок") })
                Spacer(Modifier.height(8.dp)); OutlinedTextField(homework, { homework = it }, label = { Text("ДЗ") })
                Spacer(Modifier.height(10.dp))
                Button(onClick = { picker.launch(arrayOf("image/*")) }) { Text(if (photoUri == null) "📷 Добавить фото" else "📷 Заменить фото") }
            }
        },
        confirmButton = { Button(enabled = subject.isNotBlank(), onClick = { onSave(subject, lesson, homework, photoUri) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun saveLessons(context: Context, lessons: List<Lesson>) {
    val array = JSONArray()
    lessons.forEach { array.put(JSONObject().apply { put("id", it.id); put("date", it.date); put("subject", it.subject); put("lesson", it.lesson); put("homework", it.homework); put("photoUri", it.photoUri ?: JSONObject.NULL) }) }
    context.getSharedPreferences("diary", Context.MODE_PRIVATE).edit().putString("lessons", array.toString()).apply()
}
private fun loadLessons(context: Context): List<Lesson> {
    val raw = context.getSharedPreferences("diary", Context.MODE_PRIVATE).getString("lessons", null) ?: return emptyList()
    return try {
        val a = JSONArray(raw)
        List(a.length()) { i -> val o = a.getJSONObject(i); Lesson(o.getLong("id"), o.getString("date"), o.getString("subject"), o.getString("lesson"), o.getString("homework"), if (o.isNull("photoUri")) null else o.getString("photoUri")) }
    } catch (_: Exception) { emptyList() }
}
