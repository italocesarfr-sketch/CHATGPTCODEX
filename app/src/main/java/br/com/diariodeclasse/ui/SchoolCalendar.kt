package br.com.diariodeclasse.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.TextStyle
import br.com.diariodeclasse.ui.theme.NoctuaSpace

data class CalendarMarker(val text:String="",val description:String)

/** Mesmo calendário nativo nos dois modos. Só callbacks explícitos abrem uma data. */
@Composable fun SchoolCalendar(year:Int,month:Int,selected:String,changeMonth:(Int)->Unit,
    open:(String)->Unit,marker:(String)->CalendarMarker,instruction:String,legend:String,tag:String,
    footer:@Composable ()->Unit = {}) {
    val ym=YearMonth.of(year,month)
    LazyColumn(Modifier.fillMaxSize().testTag(tag),contentPadding=PaddingValues(NoctuaSpace.medium),verticalArrangement=Arrangement.spacedBy(NoctuaSpace.small)) {
        item {
            Choice("Mês do ano letivo $year",(1..12).map{"$it • ${YearMonth.of(year,it).month.getDisplayName(TextStyle.FULL,brLocale)}"},"$month • ${ym.month.getDisplayName(TextStyle.FULL,brLocale)}"){changeMonth(it.substringBefore(" •").toInt())}
            if(instruction.isNotBlank()) Text(instruction,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            BoxWithConstraints {
                val width=maxOf(NoctuaSpace.touch*7+4.dp*6,maxWidth)
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    Column(Modifier.width(width)) {
                        Row(Modifier.fillMaxWidth()){listOf("S","T","Q","Q","S","S","D").forEach{Text(it,Modifier.weight(1f).padding(vertical=8.dp),style=MaterialTheme.typography.labelMedium,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                        val offset=ym.atDay(1).dayOfWeek.value-1
                        for(week in 0 until (offset+ym.lengthOfMonth()+6)/7) {
                            Row(Modifier.fillMaxWidth().padding(bottom=4.dp),horizontalArrangement=Arrangement.spacedBy(NoctuaSpace.tiny)) {
                                for(weekday in 0..6) {
                                    val n=week*7+weekday-offset+1
                                    if(n !in 1..ym.lengthOfMonth()){Spacer(Modifier.weight(1f).height(NoctuaSpace.touch));continue}
                                    val day=ym.atDay(n).toString();val mark=marker(day)
                                    Surface(onClick={open(day)},modifier=Modifier.weight(1f).height(NoctuaSpace.touch).testTag("date-$day").semantics{
                                        contentDescription="${shortDate(day)}, ${mark.description}"; this.selected=day==selected
                                    },shape=MaterialTheme.shapes.small,color=if(day==selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor=if(day==selected)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) {
                                        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                                            Text("$n",style=MaterialTheme.typography.bodyMedium)
                                            if(mark.text.isNotEmpty())Text(mark.text,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Text(legend,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item { footer() }
    }
}
