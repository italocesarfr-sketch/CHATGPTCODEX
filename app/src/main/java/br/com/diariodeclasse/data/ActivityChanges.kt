package br.com.diariodeclasse.data

enum class GradeChangeMode { KEEP, PROPORTIONAL }
data class ActivitySnapshot(val activity: Activity, val grades: List<Grade>) {
    val launched: Int get() = grades.count { it.value != null }
}
data class ActivityEditPreview(val before: ActivitySnapshot, val proposed: Activity) {
    val maximumChanged: Boolean get() = before.activity.maximum != proposed.maximum
    val needsChoice: Boolean get() = maximumChanged && before.launched > 0
    val canKeep: Boolean get() = before.grades.all { it.value == null || it.value <= proposed.maximum }
}
