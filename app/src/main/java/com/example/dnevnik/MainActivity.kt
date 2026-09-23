package com.example.dnevnik

import android.app.DatePickerDialog
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

data class Lesson(
    val id: Long,
    val date: String,
    val subject: String,
    val lesson: String,
    val homework: String,
    val photoUri: String?,
    val completed: Boolean = false,
    val dueDate: String? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { DiaryApp() } }
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
    var tomorrowMode by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Lesson?>(null) }
    var dragDistance by remember { mutableFloatStateOf(0f) }

    val viewDate = if (tomorrowMode) shiftDate(todayKey(), 1) else selectedDate
    val dayLessons = lessons.filter { it.date == viewDate }.sortedBy { it.id }

    Scaffold(topBar = {
        TopAppBar(title = { Text("📚 Дневник") }, actions = {
            TextButton(onClick = { tomorrowMode = false }) { Text("По датам") }
            TextButton(onClick = { tomorrowMode = true }) { Text("На завтра") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).pointerInput(tomorrowMode) {
            if (!tomorrowMode) detectHorizontalDragGestures(
                onHorizontalDrag = { _, amount -> dragDistance += amount },
                onDragEnd = {
                    if (dragDistance > 80f) selectedDate = shiftDate(selectedDate, -1)
                    else if (dragDistance < -80f) selectedDate = shiftDate(selectedDate, 1)
                    dragDistance = 0f
                }, onDragCancel = { dragDistance = 0f })
        }) {
            Text(if (tomorrowMode) "На завтра • ${prettyDate(viewDate)}" else prettyDate(selectedDate), style = MaterialTheme.typography.headlineSmall)
            if (!tomorrowMode) Text("← предыдущий день    следующий день →", style = MaterialTheme.typography.bodySmall)
            Text("Записей: ${dayLessons.size}")
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(dayLessons, key = { it.id }) { item ->
                    LessonCard(item,
                        onToggleDone = {
                            lessons = lessons.map { if (it.id == item.id) it.copy(completed = !it.completed) else it }
                            saveLessons(context, lessons)
                        },
                        onEdit = { editing = item },
                        onDelete = {
                            lessons = lessons.filterNot { it.id == item.id }; saveLessons(context, lessons)
                        })
                }
            }
            Button(onClick = { showAdd = true }, Modifier.fillMaxWidth()) { Text("➕ Добавить урок") }
        }
    }

    if (showAdd) LessonDialog(null, selectedDate, { showAdd = false }) { value ->
        lessons = lessons + value; saveLessons(context, lessons); showAdd = false
    }
    editing?.let { old -> LessonDialog(old, old.date, { editing = null }) { value ->
        lessons = lessons.map { if (it.id == old.id) value.copy(id = old.id) else it }
        saveLessons(context, lessons); editing = null
    } }
}

@Composable
fun LessonCard(item: Lesson, onToggleDone: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.subject, style = MaterialTheme.typography.titleLarge)
                Row { Checkbox(checked = item.completed, onCheckedChange = { onToggleDone() }); Text(if (item.completed) "Сделано" else "Не сделано", Modifier.padding(top = 12.dp)) }
            }
            Text("Урок: ${item.lesson}")
            Spacer(Modifier.height(4.dp)); Text("ДЗ: ${item.homework}")
            item.photoUri?.let { uri -> Spacer(Modifier.height(10.dp)); AsyncImage(Uri.parse(uri), "Фото задания", Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onEdit) { Text("✏️ Изменить") }; OutlinedButton(onClick = onDelete) { Text("Удалить") } }
        }
    }
}

@Composable
fun LessonDialog(existing: Lesson?, initialDate: String, onDismiss: () -> Unit, onSave: (Lesson) -> Unit) {
    val context = LocalContext.current
    var date by remember(existing?.id) { mutableStateOf(initialDate) }
    var subject by remember(existing?.id) { mutableStateOf(existing?.subject ?: "") }
    var lesson by remember(existing?.id) { mutableStateOf(existing?.lesson ?: "") }
    var homework by remember(existing?.id) { mutableStateOf(existing?.homework ?: "") }
    var photoUri by remember(existing?.id) { mutableStateOf(existing?.photoUri) }
    var completed by remember(existing?.id) { mutableStateOf(existing?.completed ?: false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            photoUri = it.toString()
        }
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "Новый урок" else "Изменить урок") }, text = {
        Column {
            Button(onClick = {
                val c = Calendar.getInstance().apply { time = dateFormat.parse(date) ?: Date() }
                DatePickerDialog(context, { _, y, m, d -> c.set(y, m, d); date = dateFormat.format(c.time) }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }) { Text("📅 Дата: ${prettyDate(date)}") }
            Spacer(Modifier.height(8.dp)); OutlinedTextField(subject, { subject = it }, label = { Text("Предмет") }, singleLine = true)
            Spacer(Modifier.height(8.dp)); OutlinedTextField(lesson, { lesson = it }, label = { Text("Урок") })
            Spacer(Modifier.height(8.dp)); OutlinedTextField(homework, { homework = it }, label = { Text("ДЗ") })
            Spacer(Modifier.height(10.dp)); Button(onClick = { picker.launch(arrayOf("image/*")) }) { Text(if (photoUri == null) "📷 Добавить фото" else "📷 Заменить фото") }
        }
    }, confirmButton = { Button(enabled = subject.isNotBlank(), onClick = { onSave(Lesson(existing?.id ?: System.currentTimeMillis(), date, subject, lesson, homework, photoUri, completed, date)) }) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

private fun saveLessons(context: Context, lessons: List<Lesson>) {
    val a = JSONArray(); lessons.forEach { a.put(JSONObject().apply { put("id", it.id); put("date", it.date); put("subject", it.subject); put("lesson", it.lesson); put("homework", it.homework); put("photoUri", it.photoUri ?: JSONObject.NULL); put("completed", it.completed); put("dueDate", it.dueDate ?: JSONObject.NULL) }) }
    context.getSharedPreferences("diary", Context.MODE_PRIVATE).edit().putString("lessons", a.toString()).apply()
}
private fun loadLessons(context: Context): List<Lesson> {
    val raw = context.getSharedPreferences("diary", Context.MODE_PRIVATE).getString("lessons", null) ?: return emptyList()
    return try { val a = JSONArray(raw); List(a.length()) { i -> val o = a.getJSONObject(i); Lesson(o.getLong("id"), o.getString("date"), o.getString("subject"), o.getString("lesson"), o.getString("homework"), if (o.isNull("photoUri")) null else o.getString("photoUri"), o.optBoolean("completed", false), if (o.isNull("dueDate")) null else o.getString("dueDate")) } } catch (_: Exception) { emptyList() }
}
