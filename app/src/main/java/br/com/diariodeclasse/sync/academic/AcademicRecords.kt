package br.com.diariodeclasse.sync.academic

import br.com.diariodeclasse.backup.BackupCodec
import br.com.diariodeclasse.backup.BackupSnapshot
import br.com.diariodeclasse.data.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*

/** Shared record contract. Only the explicitly enabled scope is captured/sent by the v2 client. */
enum class AcademicKind(val wire: String, val primaryKey: List<String>) {
    CLASSES("classes", listOf("id")), TERMS("terms", listOf("classId", "number")),
    STUDENTS("students", listOf("id")), LESSONS("lessons", listOf("id")),
    ACTIVITIES("activities", listOf("id")), MARKS("marks", listOf("lessonId", "studentId")),
    GRADES("grades", listOf("activityId", "studentId")),
    RECOMPOSITIONS("recompositions", listOf("studentId", "term")),
    RELOCATIONS("relocations", listOf("sourceStudentId")), DRAFTS("import_drafts", listOf("id")),
    EXPORT_ROWS("export_rows", listOf("id")), ARCHIVES("class_archives", listOf("classId")),
    JOURNAL("lesson_records", listOf("id")), HISTORIES("preserved_histories", listOf("classId")),
    TIMETABLES("weekly_timetables", listOf("id"));

    fun key(payload: JsonObject) = AcademicKey(wire, JsonObject(primaryKey.associateWith { field ->
        requireNotNull(payload[field]).also { value ->
            require(value is JsonPrimitive && value != JsonNull)
        }
    }))
    companion object { fun of(wire: String) = requireNotNull(entries.singleOrNull { it.wire == wire }) { "Unknown academic kind" } }
}

@Serializable data class AcademicKey(val kind: String, val key: JsonObject)
@Serializable data class AcademicRow(val kind: String, val key: JsonObject, val payload: JsonObject) {
    val identity get() = AcademicKey(kind, key)
    fun validateIdentity() { require(AcademicKind.of(kind).key(payload) == identity) }
}
@Serializable data class AcademicChange(val kind: String, val key: JsonObject, val base: Long, val payload: JsonObject?) {
    val identity get() = AcademicKey(kind, key)
    fun validate() {
        require(base >= 0)
        val type = AcademicKind.of(kind)
        require(key.keys == type.primaryKey.toSet())
        key.forEach { (field,value) ->
            require(value is JsonPrimitive && value != JsonNull)
            if (field in setOf("number", "term")) require(!value.isString && value.intOrNull in 1..4)
            else require(value.isString && value.content.isNotBlank() && value.content.length <= 512)
        }
        payload?.let { require(type.key(it) == identity) }
    }
}
@Serializable data class AcademicBatch(val operation: String, val changes: List<AcademicChange>) {
    fun validate(maxRecords:Int=256) {
        require(java.util.UUID.fromString(operation).toString() == operation)
        require(maxRecords in 1..AcademicPaging.MAX_RECORDS)
        require(changes.size in 1..maxRecords && changes.map { it.identity }.distinct().size == changes.size)
        changes.forEach { it.validate() }
    }
}

/** Row codec reuses actual Room serializers and existing graph validation, never calculated reports. */
object AcademicRecords {
    val json = Json { encodeDefaults = true; explicitNulls = true }

    fun rows(snapshot: BackupSnapshot): List<AcademicRow> {
        BackupCodec.validate(snapshot)
        val output = mutableListOf<AcademicRow>()
        fun <T> add(kind: AcademicKind, values: List<T>, serializer: kotlinx.serialization.KSerializer<T>) {
            values.forEach { value ->
                val payload = json.encodeToJsonElement(serializer, value).jsonObject
                output += AcademicRow(kind.wire, kind.key(payload).key, payload)
            }
        }
        add(AcademicKind.CLASSES, snapshot.classes, SchoolClass.serializer())
        add(AcademicKind.TERMS, snapshot.terms, Term.serializer())
        add(AcademicKind.STUDENTS, snapshot.students, Student.serializer())
        add(AcademicKind.LESSONS, snapshot.lessons, Lesson.serializer())
        add(AcademicKind.ACTIVITIES, snapshot.activities, Activity.serializer())
        add(AcademicKind.MARKS, snapshot.marks, Attendance.serializer())
        add(AcademicKind.GRADES, snapshot.grades, Grade.serializer())
        add(AcademicKind.RECOMPOSITIONS, snapshot.recompositions, Recomposition.serializer())
        add(AcademicKind.RELOCATIONS, snapshot.relocations, Relocation.serializer())
        add(AcademicKind.DRAFTS, snapshot.drafts, ImportDraft.serializer())
        add(AcademicKind.EXPORT_ROWS, snapshot.exportRows, ExportRow.serializer())
        add(AcademicKind.ARCHIVES, snapshot.classArchives, ClassArchive.serializer())
        add(AcademicKind.JOURNAL, snapshot.lessonRecords, LessonRecord.serializer())
        add(AcademicKind.HISTORIES, snapshot.histories, PreservedHistory.serializer())
        add(AcademicKind.TIMETABLES, snapshot.timetables, WeeklyTimetable.serializer())
        return output.sortedWith(compareBy({ it.kind }, { canonical(it.key) }))
    }

    fun snapshot(rows: List<AcademicRow>): BackupSnapshot {
        rows.forEach { it.validateIdentity() }
        require(rows.map { it.identity }.distinct().size == rows.size)
        fun <T> list(kind: AcademicKind, serializer: kotlinx.serialization.KSerializer<T>): List<T> =
            json.decodeFromJsonElement(ListSerializer(serializer), JsonArray(rows.filter { it.kind == kind.wire }.map { it.payload }))
        return BackupSnapshot(
            classes = list(AcademicKind.CLASSES, SchoolClass.serializer()), students = list(AcademicKind.STUDENTS, Student.serializer()),
            terms = list(AcademicKind.TERMS, Term.serializer()), lessons = list(AcademicKind.LESSONS, Lesson.serializer()),
            marks = list(AcademicKind.MARKS, Attendance.serializer()), activities = list(AcademicKind.ACTIVITIES, Activity.serializer()),
            grades = list(AcademicKind.GRADES, Grade.serializer()), drafts = list(AcademicKind.DRAFTS, ImportDraft.serializer()),
            recompositions = list(AcademicKind.RECOMPOSITIONS, Recomposition.serializer()), relocations = list(AcademicKind.RELOCATIONS, Relocation.serializer()),
            exportRows = list(AcademicKind.EXPORT_ROWS, ExportRow.serializer()), classArchives = list(AcademicKind.ARCHIVES, ClassArchive.serializer()),
            lessonRecords = list(AcademicKind.JOURNAL, LessonRecord.serializer()), histories = list(AcademicKind.HISTORIES, PreservedHistory.serializer()),
            timetables = list(AcademicKind.TIMETABLES, WeeklyTimetable.serializer())
        ).canonical().also { BackupCodec.validate(it) }
    }

    /** Unchanged rows produce no operation. Revision must be supplied, never guessed after restore. */
    fun delta(base: List<AcademicRow>, current: List<AcademicRow>, revisions: Map<AcademicKey, Long>): List<AcademicChange> {
        fun index(rows: List<AcademicRow>) = rows.associateBy { it.identity }.also {
            rows.forEach { row -> row.validateIdentity() }; require(it.size == rows.size)
        }
        val before = index(base); val after = index(current)
        return (before.keys + after.keys).mapNotNull { key ->
            if (before[key] == after[key]) null else {
                val revision = if (key in before) requireNotNull(revisions[key]).also { require(it > 0) }
                    else revisions[key]?.also { require(it > 0) } ?: 0 // Known tombstone is not a new id.
                AcademicChange(key.kind, key.key, revision, after[key]?.payload)
            }
        }.sortedWith(compareBy({ it.kind }, { canonical(it.key) }))
    }

    fun canonical(value: JsonElement): String = when (value) {
        is JsonObject -> JsonObject(value.toSortedMap().mapValues { json.parseToJsonElement(canonical(it.value)) }).toString()
        is JsonArray -> JsonArray(value.map { json.parseToJsonElement(canonical(it)) }).toString()
        else -> value.toString()
    }
}

data class AcademicConflict(val identity: AcademicKey, val reason: String,
    val base: JsonObject?, val local: JsonObject?, val remote: JsonObject?)
data class AcademicReview(val candidate: BackupSnapshot?, val conflicts: List<AcademicConflict>, val invalidGraph: Boolean = false)

/** Pure review, no writes. A caller must revalidate revisions/graph in its Room transaction. */
object AcademicMerge {
    fun review(base: List<AcademicRow>, local: List<AcademicRow>, changes: List<AcademicChange>,
        revisions: Map<AcademicKey, Long>, preservedConflicts: Set<AcademicKey> = emptySet()): AcademicReview {
        fun index(rows: List<AcademicRow>) = rows.associateBy { it.identity }.also {
            require(it.size == rows.size); rows.forEach { row -> row.validateIdentity() }
        }
        val original = index(base); val proposed = index(local).toMutableMap()
        require(original.keys.all { (revisions[it] ?: 0) > 0 })
        require(changes.map { it.identity }.distinct().size == changes.size)
        val conflicts = mutableListOf<AcademicConflict>()
        changes.forEach { change ->
            change.validate()
            val key = change.identity; val b = original[key]?.payload; val l = proposed[key]?.payload; val r = change.payload
            if (change.base != (revisions[key] ?: 0L)) {
                conflicts += AcademicConflict(key, "BASE_REVISION_CHANGED", b, l, r)
                return@forEach
            }
            val remoteDiffers = r != b
            val next = when {
                key in preservedConflicts -> null
                l == r || !remoteDiffers -> l
                l == b -> r
                key.kind == AcademicKind.CLASSES.wire && b != null && l != null && r != null -> {
                    val left = b.keys.filter { b[it] != l[it] }.toSet()
                    val right = b.keys.filter { b[it] != r[it] }.toSet()
                    if (left.intersect(right).isEmpty() && (left == setOf("name") || right == setOf("name")))
                        JsonObject(b + left.associateWith { l.getValue(it) } + right.associateWith { r.getValue(it) })
                    else null
                }
                else -> null
            }
            val safeDeletion = r == null && (l == b || l == null) && key !in preservedConflicts
            if (next == null && !safeDeletion) conflicts += AcademicConflict(key,
                if (key in preservedConflicts) "PRESERVED_CONFLICT" else "CONCURRENT_CHANGE", b, l, r)
            else if (next == null) proposed.remove(key)
            else proposed[key] = AcademicRow(key.kind, key.key, next)
        }
        if (conflicts.isNotEmpty()) return AcademicReview(null, conflicts)
        return try { AcademicReview(AcademicRecords.snapshot(proposed.values.toList()), emptyList()) }
            catch (_: IllegalArgumentException) { AcademicReview(null, emptyList(), invalidGraph = true) }
    }
}
