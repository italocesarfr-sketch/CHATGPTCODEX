package br.com.diariodeclasse.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Contexto visual, separado do banco escolar. IDs nunca são posições na lista. */
@Serializable
data class AppSelection(val year: Int, val classId: String? = null)

interface SelectionStore {
    fun read(): AppSelection?
    fun write(value: AppSelection?)
}

class LocalSelectionStore(context: Context, name: String = "diary-context") : SelectionStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state = _state.asStateFlow()
    override fun read(): AppSelection? {
        val year = prefs.getInt("year", 0)
        return if (year in 2000..2100) AppSelection(year, prefs.getString("class-id", null)) else null
    }
    @Synchronized override fun write(value: AppSelection?) {
        require(value == null || value.year in 2000..2100)
        val edit = prefs.edit().clear()
        if (value != null) edit.putInt("year", value.year).putString("class-id", value.classId)
        check(edit.commit()) { "Não foi possível memorizar a seleção." }
        _state.value = value
    }
}

/** Turmas valem por todo o ano; a data de criação nunca participa da seleção. */
fun resolveSelection(saved: AppSelection?, classes: List<SchoolClass>, currentYear: Int = LocalDate.now().year): AppSelection {
    val year = saved?.year ?: classes.firstOrNull { it.year == currentYear }?.year ?: classes.maxOfOrNull { it.year } ?: currentYear
    val roster = classes.filter { it.year == year }
    return AppSelection(year, roster.firstOrNull { it.id == saved?.classId }?.id ?: roster.firstOrNull()?.id)
}
