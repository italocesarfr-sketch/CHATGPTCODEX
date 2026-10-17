package br.com.diariodeclasse.data

/** Somente uma intenção explícita de reativação pode retirar a saída. Nunca executado ao ler/migrar/restaurar. */
object EnrollmentReactivation {
    fun reopen(student: Student, data: DiarySnapshot): Student {
        require(student.status != "Remanejado" && data.relocations.none { it.sourceStudentId == student.id }) {
            "Matrícula já remanejada. Consulte o histórico; a origem não pode ser reativada."
        }
        require(data.relocations.none { it.targetStudentId == student.id }) {
            "Matrícula vinculada a remanejamento: os limites confirmados não podem ser reabertos por este formulário."
        }
        require(student.status in listOf("Ativo", "Transferido") && student.left != null) {
            "Estado inconsistente: confira a situação e a data de saída antes de reativar."
        }
        val year = requireNotNull(data.classes.firstOrNull { it.id == student.classId }) { "Turma não encontrada." }.year
        // Transferências administrativas legadas podem ter criado outra matrícula sem vínculo explícito.
        // Não inferir/fundir histórico, nem permitir sobreposição da identidade ao retirar a saída.
        require(data.students.none { other ->
            other.id != student.id && other.personId == student.personId &&
                data.classes.any { it.id == other.classId && it.year == year } &&
                other.joined <= "$year-12-31" && (other.left == null || other.left >= student.joined)
        }) { "Há outra matrícula da mesma pessoa no período reaberto. Consulte o histórico da transferência; a reativação não será feita automaticamente." }
        return student.copy(status = "Ativo", left = null)
    }
}
