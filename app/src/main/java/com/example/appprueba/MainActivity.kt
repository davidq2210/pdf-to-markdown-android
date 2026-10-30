package com.example.appprueba

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.appprueba.ui.theme.AppPruebaTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            AppPruebaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ConversorPdfScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversorPdfScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var textoMarkdown by remember { mutableStateOf("") }
    var estaProcesando by remember { mutableStateOf(false) }
    var nombreArchivo by remember { mutableStateOf("") }

    val selectorPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            nombreArchivo = it.lastPathSegment?.substringAfterLast("/") ?: "documento.pdf"
            estaProcesando = true
            textoMarkdown = ""

            coroutineScope.launch {
                val resultado = withContext(Dispatchers.IO) {
                    procesarPdfAMarkdown(context, it)
                }
                textoMarkdown = resultado
                estaProcesando = false
            }
        }
    }

    val guardadorMd = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                escribirTextoEnUri(context, it, textoMarkdown)
            }
        }
    }

    val guardadorDocx = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                escribirStreamEnUri(context, it) { stream -> generarDocx(textoMarkdown, stream) }
            }
        }
    }

    val guardadorXlsx = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                escribirStreamEnUri(context, it) { stream -> generarXlsx(textoMarkdown, stream) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "PDF to Markdown",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { selectorPdf.launch("application/pdf") },
                        enabled = !estaProcesando,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (nombreArchivo.isEmpty()) "Seleccionar PDF" else "Cambiar PDF")
                    }

                    if (nombreArchivo.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = nombreArchivo,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = estaProcesando, enter = fadeIn(), exit = fadeOut()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 24.dp)
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Extrayendo y formateando documento...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (!estaProcesando && textoMarkdown.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SuggestionChip(
                        onClick = {
                            val base = nombreArchivo.substringBeforeLast(".")
                            guardadorMd.launch("$base.md")
                        },
                        label = { Text("MD") },
                        icon = { Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    SuggestionChip(
                        onClick = {
                            val base = nombreArchivo.substringBeforeLast(".")
                            guardadorDocx.launch("$base.docx")
                        },
                        label = { Text("DOCX") },
                        icon = { Icon(Icons.Filled.Article, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    SuggestionChip(
                        onClick = {
                            val base = nombreArchivo.substringBeforeLast(".")
                            guardadorXlsx.launch("$base.xlsx")
                        },
                        label = { Text("XLSX") },
                        icon = { Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Markdown", textoMarkdown))
                        Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar texto")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = textoMarkdown,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
                            )
                        )
                    }
                }
            } else if (!estaProcesando && nombreArchivo.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Selecciona un archivo PDF para comenzar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

private fun procesarPdfAMarkdown(context: Context, uri: Uri): String {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        if (inputStream == null) return "Error: No se pudo abrir el archivo."
        val document = PDDocument.load(inputStream)
        val stripper = PDFTextStripper()
        val textoPlano = stripper.getText(document)
        document.close()
        inputStream.close()
        convertirTextoAMarkdown(textoPlano)
    } catch (e: Exception) {
        "Error al procesar el PDF: ${e.localizedMessage}"
    }
}

private fun convertirTextoAMarkdown(texto: String): String {
    val lineas = texto.lines()
    val sb = StringBuilder()
    for (linea in lineas) {
        val limpia = linea.trim()
        when {
            limpia.isEmpty() -> sb.append("\n\n")
            limpia.length in 4..45 && limpia == limpia.uppercase() && !limpia.contains(".") -> {
                sb.append("## ").append(limpia).append("\n\n")
            }
            else -> sb.append(limpia).append(" ")
        }
    }
    return sb.toString().trim()
}

private fun escribirTextoEnUri(context: Context, uri: Uri, contenido: String) {
    try {
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(contenido.toByteArray(Charsets.UTF_8))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun escribirStreamEnUri(context: Context, uri: Uri, accionEscritura: (OutputStream) -> Unit) {
    try {
        context.contentResolver.openOutputStream(uri)?.use {
            accionEscritura(it)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun generarDocx(markdown: String, outputStream: OutputStream) {
    val doc = XWPFDocument()
    for (linea in markdown.lines()) {
        val p = doc.createParagraph()
        val run = p.createRun()
        if (linea.startsWith("## ")) {
            run.isBold = true
            run.fontSize = 14
            run.setText(linea.removePrefix("## "))
        } else {
            run.fontSize = 11
            run.setText(linea)
        }
    }
    doc.write(outputStream)
    doc.close()
}

private fun generarXlsx(markdown: String, outputStream: OutputStream) {
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet("Datos")
    var rowIndex = 0

    for (linea in markdown.lines()) {
        val limpia = linea.trim()
        if (limpia.isEmpty() || limpia.startsWith("|---")) continue
        val row = sheet.createRow(rowIndex++)
        if (limpia.startsWith("|") && limpia.endsWith("|")) {
            val celdas = limpia.split("|").filter { it.isNotBlank() }
            celdas.forEachIndexed { colIndex, celda ->
                row.createCell(colIndex).setCellValue(celda.trim())
            }
        } else {
            row.createCell(0).setCellValue(limpia)
        }
    }
    workbook.write(outputStream)
    workbook.close()
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ConversorPdfScreenPreview() {
    AppPruebaTheme {
        ConversorPdfScreen()
    }
}
