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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.appprueba.ui.theme.AppPruebaTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
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
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var pestanaSeleccionada by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Transform, contentDescription = "Convertir") },
                    label = { Text("Convertir") },
                    selected = pestanaSeleccionada == 0,
                    onClick = { pestanaSeleccionada = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Build, contentDescription = "Herramientas") },
                    label = { Text("Herramientas") },
                    selected = pestanaSeleccionada == 1,
                    onClick = { pestanaSeleccionada = 1 }
                )
            }
        }
    ) { innerPadding ->
        when (pestanaSeleccionada) {
            0 -> ConversorPdfScreen(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()))
            1 -> HerramientasPdfScreen(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerramientasPdfScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Estado para "Unir PDFs (Merge)"
    var mergeUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var estaUniendo by remember { mutableStateOf(false) }

    val guardadorMerge = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && mergeUris.isNotEmpty()) {
            estaUniendo = true
            coroutineScope.launch {
                val exito = withContext(Dispatchers.IO) {
                    unirPdfs(context, mergeUris, uri)
                }
                estaUniendo = false
                if (exito) {
                    Toast.makeText(context, "PDFs unidos con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al unir los PDFs", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val selectorMultiplePdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.size >= 2) {
            mergeUris = uris
            guardadorMerge.launch("PDF_Unido.pdf")
        } else if (uris.isNotEmpty()) {
            mergeUris = emptyList()
            Toast.makeText(context, "Debes seleccionar al menos 2 archivos PDF para unir", Toast.LENGTH_SHORT).show()
        }
    }

    // Estado para "Extraer Páginas (Split)"
    var splitPdfUri by remember { mutableStateOf<Uri?>(null) }
    var splitPdfNombre by remember { mutableStateOf("") }
    var totalPaginas by remember { mutableIntStateOf(0) }
    var paginaInicio by remember { mutableStateOf("1") }
    var paginaFin by remember { mutableStateOf("1") }
    var estaExtrayendo by remember { mutableStateOf(false) }

    val guardadorSplit = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && splitPdfUri != null) {
            val inicio = paginaInicio.toIntOrNull() ?: 1
            val fin = paginaFin.toIntOrNull() ?: inicio
            estaExtrayendo = true
            coroutineScope.launch {
                val exito = withContext(Dispatchers.IO) {
                    extraerPaginasPdf(context, splitPdfUri!!, uri, inicio, fin)
                }
                estaExtrayendo = false
                if (exito) {
                    Toast.makeText(context, "Páginas extraídas con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al extraer las páginas", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val selectorSplitPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            splitPdfUri = it
            splitPdfNombre = it.lastPathSegment?.substringAfterLast("/") ?: "documento.pdf"
            coroutineScope.launch {
                val paginas = withContext(Dispatchers.IO) {
                    obtenerTotalPaginas(context, it)
                }
                totalPaginas = paginas
                paginaInicio = "1"
                paginaFin = if (paginas > 0) paginas.toString() else "1"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Herramientas PDF",
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card a) Unir PDFs (Merge)
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.CallMerge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Unir PDFs (Merge)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Combina 2 o más archivos PDF en un solo documento continuo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { selectorMultiplePdf.launch(arrayOf("application/pdf")) },
                        enabled = !estaUniendo,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Seleccionar PDFs para unir")
                    }

                    if (mergeUris.isNotEmpty()) {
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
                                text = "${mergeUris.size} archivos seleccionados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            FilledTonalButton(
                                onClick = { guardadorMerge.launch("PDF_Unido.pdf") },
                                enabled = !estaUniendo && mergeUris.size >= 2
                            ) {
                                Text("Guardar PDF Unido")
                            }
                        }
                    }

                    AnimatedVisibility(visible = estaUniendo, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Fusionando archivos PDF...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Card b) Extraer Páginas (Split)
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.CallSplit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Extraer Páginas (Split)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Selecciona un documento y extrae un rango específico de páginas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { selectorSplitPdf.launch("application/pdf") },
                        enabled = !estaExtrayendo,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (splitPdfNombre.isEmpty()) "Seleccionar PDF" else "Cambiar PDF")
                    }

                    if (splitPdfNombre.isNotEmpty()) {
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
                                text = if (totalPaginas > 0) "$splitPdfNombre ($totalPaginas págs.)" else splitPdfNombre,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = paginaInicio,
                                onValueChange = { valor ->
                                    if (valor.all { it.isDigit() }) paginaInicio = valor
                                },
                                label = { Text("Pág. Inicial") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = paginaFin,
                                onValueChange = { valor ->
                                    if (valor.all { it.isDigit() }) paginaFin = valor
                                },
                                label = { Text("Pág. Final") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Button(
                            onClick = {
                                val inicio = paginaInicio.toIntOrNull()
                                val fin = paginaFin.toIntOrNull()
                                if (inicio == null || fin == null || inicio < 1 || fin < inicio) {
                                    Toast.makeText(context, "Rango de páginas inválido", Toast.LENGTH_SHORT).show()
                                } else if (totalPaginas > 0 && (inicio > totalPaginas || fin > totalPaginas)) {
                                    Toast.makeText(context, "El rango excede el total de páginas ($totalPaginas)", Toast.LENGTH_SHORT).show()
                                } else {
                                    guardadorSplit.launch("PDF_Extraido.pdf")
                                }
                            },
                            enabled = !estaExtrayendo && paginaInicio.isNotBlank() && paginaFin.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extraer y Guardar PDF")
                        }
                    }

                    AnimatedVisibility(visible = estaExtrayendo, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Extrayendo páginas del PDF...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun unirPdfs(context: Context, uris: List<Uri>, destUri: Uri): Boolean {
    val merger = PDFMergerUtility()
    val inputStreams = mutableListOf<InputStream>()
    return try {
        for (uri in uris) {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream != null) {
                inputStreams.add(stream)
                merger.addSource(stream)
            }
        }
        context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
            merger.destinationStream = outputStream
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    } finally {
        for (stream in inputStreams) {
            try { stream.close() } catch (_: Exception) {}
        }
    }
}

private fun obtenerTotalPaginas(context: Context, uri: Uri): Int {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            PDDocument.load(stream).use { doc ->
                doc.numberOfPages
            }
        } ?: 0
    } catch (e: Exception) {
        e.printStackTrace()
        0
    }
}

private fun extraerPaginasPdf(
    context: Context,
    sourceUri: Uri,
    destUri: Uri,
    startPage: Int,
    endPage: Int
): Boolean {
    var sourceDoc: PDDocument? = null
    val splitDocs = mutableListOf<PDDocument>()
    return try {
        val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return false
        sourceDoc = PDDocument.load(inputStream)
        val total = sourceDoc.numberOfPages
        val validStart = startPage.coerceIn(1, total)
        val validEnd = endPage.coerceIn(validStart, total)

        val splitter = Splitter().apply {
            setStartPage(validStart)
            setEndPage(validEnd)
            setSplitAtPage(validEnd - validStart + 1)
            setMemoryUsageSetting(MemoryUsageSetting.setupMainMemoryOnly())
        }
        val result = splitter.split(sourceDoc)
        splitDocs.addAll(result)
        if (splitDocs.isNotEmpty()) {
            context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                splitDocs[0].save(outStream)
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    } finally {
        splitDocs.forEach {
            try { it.close() } catch (_: Exception) {}
        }
        try { sourceDoc?.close() } catch (_: Exception) {}
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
fun MainScreenPreview() {
    AppPruebaTheme {
        MainScreen()
    }
}
