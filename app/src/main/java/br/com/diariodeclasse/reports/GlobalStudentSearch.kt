package br.com.diariodeclasse.reports

import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.RosterParser

data class StudentSearchMatch(val school:SchoolClass,val student:Student)
/** Identidades são IDs de matrícula; homônimos nunca são fundidos pelo texto. */
object GlobalStudentSearch {
    fun find(data:DiarySnapshot,query:String,showRelocated:Boolean=false):List<StudentSearchMatch> {
        val key=RosterParser.key(query)
        if(key.isBlank())return emptyList()
        return data.classes.sortedWith(compareByDescending<SchoolClass>{it.year}.thenBy{it.name}.thenBy{it.subject}.thenBy{it.id})
            .flatMap{school->data.currentRoster(school.id,showRelocated)
                .filter{RosterParser.key(it.name).contains(key)}
                .sortedWith(compareBy<Student>{it.number}.thenBy{it.name}.thenBy{it.id})
                .map{StudentSearchMatch(school,it)}}
    }
}
