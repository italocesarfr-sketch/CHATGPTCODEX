package br.com.diariodeclasse

import br.com.diariodeclasse.ui.defaultEnrollment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EnrollmentDefaultsTest {
    @Test fun currentSchoolYearDoesNotUseRegistrationDate() {
        val year = LocalDate.now().year
        assertEquals("$year-01-01", defaultEnrollment(year))
    }
    @Test fun pastFutureAndLeapSchoolYearsStartInJanuary() {
        listOf(2025, 2026, 2027, 2028).forEach { year ->
            assertEquals("$year-01-01", defaultEnrollment(year))
        }
    }
}
