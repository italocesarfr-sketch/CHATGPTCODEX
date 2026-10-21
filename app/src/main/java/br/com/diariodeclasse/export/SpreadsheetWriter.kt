package br.com.diariodeclasse.export

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** OOXML SpreadsheetML mínimo, sem fórmulas, macros, dependências ou planilhas protegidas. */
object SpreadsheetWriter {
    fun plain(cell:ExportCell):String=cell.number?.let { if(cell.style==NumberStyle.DATE) java.time.LocalDate.ofEpochDay(it.toLong()-25569).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) else ExportProjection.decimal(it) } ?: cell.text.orEmpty()
    fun delimited(rows:List<List<ExportCell>>,delimiter:Char):String=rows.joinToString("\r\n",postfix=if(rows.isEmpty())""else"\r\n"){row->
        if(row.size==1&&plain(row.single()).isEmpty())"\"\""else row.joinToString(delimiter.toString()){cell->val v=plain(cell);if(v.any{it==delimiter||it=='"'||it=='\r'||it=='\n'})"\"${v.replace("\"","\"\"")}\""else v}
    }
    fun csv(table:ExportTable,output:OutputStream) {output.write(byteArrayOf(0xEF.toByte(),0xBB.toByte(),0xBF.toByte()));output.write(delimited(table.rows(),';').toByteArray(Charsets.UTF_8))}
    fun clipboard(table:ExportTable,onlyGrades:Boolean=false,allGrades:Boolean=false)=delimited(table.rows(onlyGrades,allGrades),'\t')
    private fun xml(s:String):String=buildString {
        s.codePoints().forEach{cp->when(cp){13->append("&#13;");38->append("&amp;");60->append("&lt;");62->append("&gt;");34->append("&quot;");39->append("&apos;");
            else->{require(cp==9||cp==10||cp==13||cp in 32..0xD7FF||cp in 0xE000..0xFFFD||cp in 0x10000..0x10FFFF){"Texto contém um caractere incompatível com XML/Excel."};appendCodePoint(cp)}}}
    }
    private fun column(index:Int):String {var n=index+1;var s="";while(n>0){n--;s=('A'.code+n%26).toChar()+s;n/=26};return s}
    fun xlsx(table:ExportTable,output:OutputStream) {
        val rows=table.rows();val width=table.columns.size;val plans=table.layout==SheetLayout.LESSON_PLANS
        if(plans) require(table.records.isNotEmpty()) { "Nenhum plano de aula neste período." }
        require(width in 1..16384&&rows.size<=1048576){"A planilha excede os limites do Excel."}
        require(rows.all{r->r.size==width&&r.all{(it.text?.length?:0)<=32767}}){"Uma célula excede o limite de texto do Excel."}
        // O wrapper mantém o stream do chamador aberto: falhas de flush/close continuam observáveis no fluxo SAF.
        val zip=ZipOutputStream(object:FilterOutputStream(output){override fun close(){flush()}})
        zip.use {
        fun part(name:String,body:String) {zip.putNextEntry(ZipEntry(name));zip.write(body.toByteArray(Charsets.UTF_8));zip.closeEntry()}
        val ns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        part("[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
        part("_rels/.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
        part("xl/workbook.xml","""<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="$ns" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Noctua" sheetId="1" r:id="rId1"/></sheets></workbook>""")
        part("xl/_rels/workbook.xml.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
        part("xl/styles.xml","""<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="$ns"><numFmts count="2"><numFmt numFmtId="165" formatCode="dd/mm/yyyy"/><numFmt numFmtId="164" formatCode="0.00&amp;quot;%&amp;quot;"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><sz val="11"/><name val="Calibri"/><b/><color rgb="FFFFFFFF"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF16324F"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="6"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""".replace("&amp;quot;","&quot;"))
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"));val w=OutputStreamWriter(zip,Charsets.UTF_8)
        val views=if(plans) "<sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\"><pane ySplit=\"2\" topLeftCell=\"A3\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>" else "<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews>"
        val columns=if(plans) "<cols><col min=\"1\" max=\"1\" width=\"14\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"40\" customWidth=\"1\"/><col min=\"3\" max=\"3\" width=\"90\" customWidth=\"1\"/></cols>" else ""
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"$ns\"><dimension ref=\"A1:${column(width-1)}${rows.size.coerceAtLeast(1)}\"/>$views<sheetFormatPr defaultRowHeight=\"15\"/>$columns<sheetData>")
        rows.forEachIndexed{r,row->
            val height=if(plans) {
                val lines=if(r==0) 2 else row.mapIndexed { c,cell -> (cell.text ?: plain(cell)).split('\n').sumOf { line -> ((line.length + (if(c==1)39 else 89))/(if(c==1)40 else 90)).coerceAtLeast(1) } }.maxOrNull() ?: 1
                " ht=\"${(lines*15+10).coerceIn(30,409)}\" customHeight=\"1\""
            } else ""
            w.write("<row r=\"${r+1}\"$height>");row.forEachIndexed{c,cell->
            val at="${column(c)}${r+1}";when {
                cell.number!=null -> {val style=when(cell.style){NumberStyle.INTEGER->0;NumberStyle.DECIMAL->1;NumberStyle.PERCENT->2;NumberStyle.DATE->3};w.write("<c r=\"$at\" s=\"$style\"><v>${java.math.BigDecimal.valueOf(cell.number).stripTrailingZeros().toPlainString()}</v></c>")}
                cell.text!=null -> w.write("<c r=\"$at\" t=\"inlineStr\"${if(plans) " s=\"${if(r==1)5 else 4}\"" else ""}><is><t xml:space=\"preserve\">${xml(cell.text)}</t></is></c>")
                else -> w.write("<c r=\"$at\"/>")
            }
        };w.write("</row>")};w.write("</sheetData>${if(plans) "<mergeCells count=\"1\"><mergeCell ref=\"A1:C1\"/></mergeCells>" else ""}</worksheet>");w.flush();zip.closeEntry();zip.finish();zip.flush()
        }
    }
}
