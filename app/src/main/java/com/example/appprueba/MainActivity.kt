package com.example.appprueba

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.appprueba.ui.theme.AppPruebaTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix as PdfMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class NivelCompresion(val etiqueta: String, val calidad: Float, val maxDimension: Int) {
    LIGERA("Ligera", 0.75f, 1600),
    MEDIA("Media", 0.50f, 1200),
    EXTREMA("Extrema", 0.30f, 900)
}

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
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Security, contentDescription = "Seguridad") },
                    label = { Text("Seguridad") },
                    selected = pestanaSeleccionada == 2,
                    onClick = { pestanaSeleccionada = 2 }
                )
            }
        }
    ) { innerPadding ->
        when (pestanaSeleccionada) {
            0 -> ConversorPdfScreen(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()))
            1 -> HerramientasPdfScreen(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()))
            2 -> SeguridadPdfScreen(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()))
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
    var uriSeleccionada by remember { mutableStateOf<Uri?>(null) }
    var modoOcrActivo by rememberSaveable { mutableStateOf(false) }
    var mensajeProgreso by remember { mutableStateOf("Extrayendo y formateando documento...") }

    fun procesarDocumento(uri: Uri, forzarOcr: Boolean) {
        coroutineScope.launch {
            estaProcesando = true
            textoMarkdown = ""
            mensajeProgreso = "Extrayendo y formateando documento..."

            val resultado = withContext(Dispatchers.IO) {
                var usarOcr = forzarOcr
                var textoExtraido = ""

                if (!usarOcr) {
                    textoExtraido = procesarPdfAMarkdown(context, uri)
                    val charsNoVacios = textoExtraido.count { !it.isWhitespace() }
                    if (charsNoVacios < 25 || textoExtraido.startsWith("Error al procesar el PDF:")) {
                        usarOcr = true
                        withContext(Dispatchers.Main) {
                            modoOcrActivo = true
                        }
                    }
                }

                if (usarOcr) {
                    ejecutarOcrLocal(context, uri) { estado ->
                        withContext(Dispatchers.Main) {
                            mensajeProgreso = estado
                        }
                    }
                } else {
                    textoExtraido
                }
            }
            textoMarkdown = resultado
            estaProcesando = false
        }
    }

    val selectorPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            uriSeleccionada = it
            nombreArchivo = it.lastPathSegment?.substringAfterLast("/") ?: "documento.pdf"
            procesarDocumento(it, modoOcrActivo)
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

                    Spacer(modifier = Modifier.height(10.dp))

                    FilterChip(
                        selected = modoOcrActivo,
                        onClick = {
                            val nuevoModo = !modoOcrActivo
                            modoOcrActivo = nuevoModo
                            uriSeleccionada?.let { uri ->
                                if (!estaProcesando) {
                                    procesarDocumento(uri, nuevoModo)
                                }
                            }
                        },
                        label = { Text("Modo OCR (PDF escaneado)") },
                        leadingIcon = {
                            Icon(
                                if (modoOcrActivo) Icons.Filled.DocumentScanner else Icons.Outlined.DocumentScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

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
                        text = mensajeProgreso,
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
                        icon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, modifier = Modifier.size(16.dp)) }
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

    // Estado para "Comprimir PDF"
    var comprimirPdfUri by remember { mutableStateOf<Uri?>(null) }
    var comprimirPdfNombre by remember { mutableStateOf("") }
    var nivelCompresion by remember { mutableStateOf(NivelCompresion.MEDIA) }
    var estaComprimiendo by remember { mutableStateOf(false) }
    var mensajeResultadoCompresion by remember { mutableStateOf<String?>(null) }

    val guardadorComprimir = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && comprimirPdfUri != null) {
            estaComprimiendo = true
            mensajeResultadoCompresion = null
            coroutineScope.launch {
                val resultado = withContext(Dispatchers.IO) {
                    comprimirPdf(
                        context = context,
                        sourceUri = comprimirPdfUri!!,
                        destUri = destUri,
                        calidad = nivelCompresion.calidad,
                        maxDimension = nivelCompresion.maxDimension
                    )
                }
                estaComprimiendo = false
                if (resultado != null) {
                    val msg = if (resultado.tamanoOriginal > 0 && resultado.tamanoFinal in 1 until resultado.tamanoOriginal) {
                        "Reducido de ${formatearTamanoBytes(resultado.tamanoOriginal)} a ${formatearTamanoBytes(resultado.tamanoFinal)} (${String.format(Locale.getDefault(), "%.1f", resultado.porcentaje)}% menor)"
                    } else if (resultado.tamanoFinal > 0) {
                        "PDF guardado (${formatearTamanoBytes(resultado.tamanoFinal)}). Las imágenes ya estaban optimizadas."
                    } else {
                        "PDF comprimido guardado con éxito"
                    }
                    mensajeResultadoCompresion = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Error al comprimir el PDF", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val selectorComprimirPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            comprimirPdfUri = it
            comprimirPdfNombre = it.lastPathSegment?.substringAfterLast("/") ?: "documento.pdf"
            mensajeResultadoCompresion = null
        }
    }

    // Estado para "Imágenes a PDF"
    var imagenesUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var estaCreandoPdfImagenes by remember { mutableStateOf(false) }

    val guardadorPdfImagenes = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && imagenesUris.isNotEmpty()) {
            estaCreandoPdfImagenes = true
            coroutineScope.launch {
                val exito = withContext(Dispatchers.IO) {
                    crearPdfDesdeImagenes(context, imagenesUris, destUri)
                }
                estaCreandoPdfImagenes = false
                if (exito) {
                    Toast.makeText(context, "PDF creado desde imágenes con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al crear PDF desde imágenes", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val selectorImagenes = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            imagenesUris = uris
        }
    }

    // Estado para "Rotar Páginas PDF"
    var rotarPdfUri by remember { mutableStateOf<Uri?>(null) }
    var rotarPdfNombre by remember { mutableStateOf("") }
    var rotarTotalPaginas by remember { mutableIntStateOf(0) }
    var rotarAngulo by remember { mutableIntStateOf(90) }
    var rotarPaginaInput by remember { mutableStateOf("") }
    var estaRotando by remember { mutableStateOf(false) }

    val guardadorRotar = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && rotarPdfUri != null) {
            val pagNum = rotarPaginaInput.toIntOrNull()
            estaRotando = true
            coroutineScope.launch {
                val exito = withContext(Dispatchers.IO) {
                    rotarPaginasPdf(
                        context = context,
                        sourceUri = rotarPdfUri!!,
                        destUri = destUri,
                        angulo = rotarAngulo,
                        paginaEspecifica = pagNum
                    )
                }
                estaRotando = false
                if (exito) {
                    Toast.makeText(context, "PDF rotado con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al rotar el PDF", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val selectorRotarPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            rotarPdfUri = it
            rotarPdfNombre = it.lastPathSegment?.substringAfterLast("/") ?: "documento.pdf"
            coroutineScope.launch {
                val paginas = withContext(Dispatchers.IO) {
                    obtenerTotalPaginas(context, it)
                }
                rotarTotalPaginas = paginas
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
                            Icons.AutoMirrored.Filled.CallMerge,
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
                            Icons.AutoMirrored.Filled.CallSplit,
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

            // Card c) Comprimir PDF
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
                            Icons.Filled.Compress,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Comprimir PDF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Reduce el tamaño del archivo comprimiendo y redimensionando sus imágenes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { selectorComprimirPdf.launch("application/pdf") },
                        enabled = !estaComprimiendo,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (comprimirPdfNombre.isEmpty()) "Seleccionar PDF" else "Cambiar PDF")
                    }

                    if (comprimirPdfNombre.isNotEmpty()) {
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
                                text = comprimirPdfNombre,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "Nivel de compresión:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NivelCompresion.entries.forEach { nivel ->
                                FilterChip(
                                    selected = nivelCompresion == nivel,
                                    onClick = { nivelCompresion = nivel },
                                    label = { Text(nivel.etiqueta, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }

                        Button(
                            onClick = { guardadorComprimir.launch("PDF_Comprimido.pdf") },
                            enabled = !estaComprimiendo,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Comprimir y Guardar PDF")
                        }
                    }

                    mensajeResultadoCompresion?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = estaComprimiendo, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Comprimiendo documento PDF...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Card d) Imágenes a PDF
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
                            Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Imágenes a PDF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Convierte una colección de fotos o imágenes en un único archivo PDF.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { selectorImagenes.launch(arrayOf("image/*")) },
                        enabled = !estaCreandoPdfImagenes,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (imagenesUris.isEmpty()) "Seleccionar imágenes" else "Cambiar selección")
                    }

                    if (imagenesUris.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Outlined.Collections,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val textoContador = if (imagenesUris.size == 1) {
                                "1 imagen lista para convertir"
                            } else {
                                "${imagenesUris.size} imágenes listas para convertir"
                            }
                            Text(
                                text = textoContador,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = { imagenesUris = emptyList() },
                                enabled = !estaCreandoPdfImagenes
                            ) {
                                Text("Limpiar", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Button(
                            onClick = { guardadorPdfImagenes.launch("Imagenes_Unidas.pdf") },
                            enabled = !estaCreandoPdfImagenes,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Crear PDF desde Imágenes")
                        }
                    }

                    AnimatedVisibility(visible = estaCreandoPdfImagenes, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Creando documento PDF desde imágenes...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Card e) Rotar Páginas PDF
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
                            Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Rotar Páginas PDF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Gira la orientación de las páginas de tu documento en 90°, 180° o 270°.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { selectorRotarPdf.launch("application/pdf") },
                        enabled = !estaRotando,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (rotarPdfNombre.isEmpty()) "Seleccionar PDF" else "Cambiar PDF")
                    }

                    if (rotarPdfNombre.isNotEmpty()) {
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
                                text = if (rotarTotalPaginas > 0) "$rotarPdfNombre ($rotarTotalPaginas págs.)" else rotarPdfNombre,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "Ángulo de rotación:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val opcionesAngulos = listOf(
                            "90° Derecha" to 90,
                            "180° Invertir" to 180,
                            "270° Izquierda" to 270
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            opcionesAngulos.forEach { (label, angulo) ->
                                FilterChip(
                                    selected = rotarAngulo == angulo,
                                    onClick = { rotarAngulo = angulo },
                                    label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = rotarPaginaInput,
                            onValueChange = { valor ->
                                if (valor.all { it.isDigit() }) rotarPaginaInput = valor
                            },
                            label = { Text("Pág. a rotar (opcional, vacío = todas)") },
                            placeholder = { Text("Ej. 1") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                val pag = rotarPaginaInput.toIntOrNull()
                                if (rotarPaginaInput.isNotBlank() && (pag == null || pag < 1 || (rotarTotalPaginas > 0 && pag > rotarTotalPaginas))) {
                                    Toast.makeText(context, "Número de página inválido (1 a $rotarTotalPaginas)", Toast.LENGTH_SHORT).show()
                                } else {
                                    guardadorRotar.launch("PDF_Rotado.pdf")
                                }
                            },
                            enabled = !estaRotando && rotarPdfUri != null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Rotar y Guardar PDF")
                        }
                    }

                    AnimatedVisibility(visible = estaRotando, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Rotando páginas del PDF...",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeguridadPdfScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Estado Tarjeta A: Firmar Documento PDF
    var firmarPdfUri by remember { mutableStateOf<Uri?>(null) }
    var firmarPdfNombre by remember { mutableStateOf("") }
    var firmarTotalPaginas by remember { mutableIntStateOf(0) }
    var firmarPagina by remember { mutableStateOf("1") }
    var posicionFirma by remember { mutableStateOf("Inferior Derecha") }
    val posiciones = listOf("Inferior Izquierda", "Inferior Centro", "Inferior Derecha")

    var paths by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var bitmapFirmaGuardar by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var estaFirmando by remember { mutableStateOf(false) }

    val selectorFirmarPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            firmarPdfUri = it
            firmarPdfNombre = it.lastPathSegment?.substringAfterLast("/") ?: "documento.pdf"
            coroutineScope.launch {
                val paginas = withContext(Dispatchers.IO) {
                    obtenerTotalPaginas(context, it)
                }
                firmarTotalPaginas = paginas
                firmarPagina = "1"
            }
        }
    }

    val guardadorFirma = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && firmarPdfUri != null && bitmapFirmaGuardar != null) {
            val numPag = firmarPagina.toIntOrNull() ?: 1
            estaFirmando = true
            coroutineScope.launch {
                val exito = withContext(Dispatchers.IO) {
                    firmarPdf(
                        context = context,
                        sourceUri = firmarPdfUri!!,
                        destUri = destUri,
                        pageNumber = numPag,
                        bitmap = bitmapFirmaGuardar!!,
                        posicion = posicionFirma
                    )
                }
                estaFirmando = false
                if (exito) {
                    Toast.makeText(context, "PDF firmado con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al firmar el PDF", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Estado Tarjeta B: Proteger PDF con Contraseña
    var protegerPdfUri by remember { mutableStateOf<Uri?>(null) }
    var protegerPdfNombre by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var contrasenaVisible by remember { mutableStateOf(false) }
    var estaProtegiendo by remember { mutableStateOf(false) }

    val selectorProtegerPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            protegerPdfUri = it
            protegerPdfNombre = it.lastPathSegment?.substringAfterLast("/") ?: "documento.pdf"
        }
    }

    val guardadorProteger = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && protegerPdfUri != null && contrasena.isNotEmpty()) {
            estaProtegiendo = true
            coroutineScope.launch {
                val exito = withContext(Dispatchers.IO) {
                    protegerPdf(context, protegerPdfUri!!, destUri, contrasena)
                }
                estaProtegiendo = false
                if (exito) {
                    Toast.makeText(context, "PDF protegido con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al proteger el PDF", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Estado Tarjeta C: Marca de Agua (Watermark)
    var watermarkPdfUri by remember { mutableStateOf<Uri?>(null) }
    var watermarkPdfNombre by remember { mutableStateOf("") }
    var watermarkTexto by remember { mutableStateOf("CONFIDENCIAL") }
    var estaAplicandoWatermark by remember { mutableStateOf(false) }

    val guardadorWatermark = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && watermarkPdfUri != null) {
            estaAplicandoWatermark = true
            coroutineScope.launch {
                val exito = withContext(Dispatchers.IO) {
                    estamparMarcaAguaPdf(context, watermarkPdfUri!!, destUri, watermarkTexto)
                }
                estaAplicandoWatermark = false
                if (exito) {
                    Toast.makeText(context, "Marca de agua aplicada con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al aplicar la marca de agua", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val selectorWatermarkPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            watermarkPdfUri = it
            watermarkPdfNombre = it.lastPathSegment?.substringAfterLast("/") ?: "documento.pdf"
        }
    }

    // Estado Tarjeta D: Quitar Contraseña (Desproteger)
    var desprotegerPdfUri by remember { mutableStateOf<Uri?>(null) }
    var desprotegerPdfNombre by remember { mutableStateOf("") }
    var desprotegerClave by remember { mutableStateOf("") }
    var desprotegerClaveVisible by remember { mutableStateOf(false) }
    var estaDesprotegiendo by remember { mutableStateOf(false) }

    val guardadorDesproteger = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && desprotegerPdfUri != null) {
            estaDesprotegiendo = true
            coroutineScope.launch {
                val resultado = withContext(Dispatchers.IO) {
                    quitarContrasenaPdf(context, desprotegerPdfUri!!, destUri, desprotegerClave)
                }
                estaDesprotegiendo = false
                when (resultado) {
                    is DesprotegerResultado.Exito -> {
                        Toast.makeText(context, "Contraseña eliminada con éxito", Toast.LENGTH_SHORT).show()
                    }
                    is DesprotegerResultado.ContrasenaIncorrecta -> {
                        Toast.makeText(context, "Contraseña incorrecta. No se pudo desproteger el documento.", Toast.LENGTH_LONG).show()
                    }
                    is DesprotegerResultado.Error -> {
                        Toast.makeText(context, "Error: ${resultado.mensaje}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val selectorDesprotegerPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            desprotegerPdfUri = it
            desprotegerPdfNombre = it.lastPathSegment?.substringAfterLast("/") ?: "documento.pdf"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Seguridad PDF",
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
            // Tarjeta A: Firmar Documento PDF
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
                            Icons.Filled.Draw,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Firmar Documento PDF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Dibuja tu firma y estámpala de forma transparente sobre cualquier página del PDF.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { selectorFirmarPdf.launch("application/pdf") },
                        enabled = !estaFirmando,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (firmarPdfNombre.isEmpty()) "Seleccionar PDF a Firmar" else "Cambiar PDF")
                    }

                    if (firmarPdfNombre.isNotEmpty()) {
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
                                text = if (firmarTotalPaginas > 0) "$firmarPdfNombre ($firmarTotalPaginas págs.)" else firmarPdfNombre,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = firmarPagina,
                            onValueChange = { valor ->
                                if (valor.all { it.isDigit() }) firmarPagina = valor
                            },
                            label = { Text("Página para estampar la firma") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Text(
                            text = "Posición de la firma:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            posiciones.forEach { pos ->
                                FilterChip(
                                    selected = posicionFirma == pos,
                                    onClick = { posicionFirma = pos },
                                    label = { Text(pos, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                    }

                    Text(
                        text = "Firma manuscrita:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Canvas para firma manuscrita
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentPath = listOf(offset)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentPath = currentPath + change.position
                                        },
                                        onDragEnd = {
                                            if (currentPath.isNotEmpty()) {
                                                paths = paths + listOf(currentPath)
                                                currentPath = emptyList()
                                            }
                                        },
                                        onDragCancel = {
                                            currentPath = emptyList()
                                        }
                                    )
                                }
                        ) {
                            // Trazos previos
                            for (stroke in paths) {
                                if (stroke.size > 1) {
                                    val p = androidx.compose.ui.graphics.Path()
                                    p.moveTo(stroke[0].x, stroke[0].y)
                                    for (i in 1 until stroke.size) {
                                        p.lineTo(stroke[i].x, stroke[i].y)
                                    }
                                    drawPath(
                                        path = p,
                                        color = Color.Black,
                                        style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                } else if (stroke.size == 1) {
                                    drawCircle(color = Color.Black, radius = 2.5f, center = stroke[0])
                                }
                            }
                            // Trazo actual
                            if (currentPath.size > 1) {
                                val p = androidx.compose.ui.graphics.Path()
                                p.moveTo(currentPath[0].x, currentPath[0].y)
                                for (i in 1 until currentPath.size) {
                                    p.lineTo(currentPath[i].x, currentPath[i].y)
                                }
                                drawPath(
                                    path = p,
                                    color = Color.Black,
                                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            } else if (currentPath.size == 1) {
                                drawCircle(color = Color.Black, radius = 2.5f, center = currentPath[0])
                            }
                        }

                        if (paths.isEmpty() && currentPath.isEmpty()) {
                            Text(
                                text = "Dibuja aquí tu firma con el dedo",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                paths = emptyList()
                                currentPath = emptyList()
                            },
                            enabled = paths.isNotEmpty() || currentPath.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpiar firma")
                        }
                    }

                    Button(
                        onClick = {
                            val pag = firmarPagina.toIntOrNull()
                            if (firmarPdfUri == null) {
                                Toast.makeText(context, "Selecciona un archivo PDF primero", Toast.LENGTH_SHORT).show()
                            } else if (paths.isEmpty()) {
                                Toast.makeText(context, "Dibuja una firma antes de continuar", Toast.LENGTH_SHORT).show()
                            } else if (pag == null || pag < 1 || (firmarTotalPaginas > 0 && pag > firmarTotalPaginas)) {
                                Toast.makeText(context, "Número de página inválido", Toast.LENGTH_SHORT).show()
                            } else {
                                val bitmap = crearBitmapFirma(paths)
                                if (bitmap != null) {
                                    bitmapFirmaGuardar = bitmap
                                    guardadorFirma.launch("PDF_Firmado.pdf")
                                } else {
                                    Toast.makeText(context, "No se pudo generar la firma", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !estaFirmando && firmarPdfUri != null && paths.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Draw, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Firmar y Guardar PDF")
                    }

                    AnimatedVisibility(visible = estaFirmando, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Estampando firma en el PDF...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Tarjeta B: Proteger PDF con Contraseña
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
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Proteger PDF con Contraseña",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Cifra tu documento PDF con protección estándar AES de 128 bits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { selectorProtegerPdf.launch("application/pdf") },
                        enabled = !estaProtegiendo,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (protegerPdfNombre.isEmpty()) "Seleccionar PDF a Proteger" else "Cambiar PDF")
                    }

                    if (protegerPdfNombre.isNotEmpty()) {
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
                                text = protegerPdfNombre,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = contrasena,
                            onValueChange = { contrasena = it },
                            label = { Text("Contraseña de protección") },
                            singleLine = true,
                            visualTransformation = if (contrasenaVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val icono = if (contrasenaVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                IconButton(onClick = { contrasenaVisible = !contrasenaVisible }) {
                                    Icon(
                                        icono,
                                        contentDescription = if (contrasenaVisible) "Ocultar clave" else "Mostrar clave"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                if (contrasena.isBlank()) {
                                    Toast.makeText(context, "Ingresa una contraseña para cifrar", Toast.LENGTH_SHORT).show()
                                } else {
                                    guardadorProteger.launch("PDF_Protegido.pdf")
                                }
                            },
                            enabled = !estaProtegiendo && contrasena.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cifrar y Guardar PDF")
                        }
                    }

                    AnimatedVisibility(visible = estaProtegiendo, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Cifrando documento PDF...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Tarjeta C: Marca de Agua (Watermark)
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
                            Icons.AutoMirrored.Filled.BrandingWatermark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Marca de Agua (Watermark)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Estampa un texto diagonal translúcido en todas las páginas del documento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { selectorWatermarkPdf.launch("application/pdf") },
                        enabled = !estaAplicandoWatermark,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (watermarkPdfNombre.isEmpty()) "Seleccionar PDF" else "Cambiar PDF")
                    }

                    if (watermarkPdfNombre.isNotEmpty()) {
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
                                text = watermarkPdfNombre,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = watermarkTexto,
                            onValueChange = { watermarkTexto = it },
                            label = { Text("Texto de la marca de agua") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                if (watermarkTexto.isBlank()) {
                                    Toast.makeText(context, "Ingresa un texto para la marca de agua", Toast.LENGTH_SHORT).show()
                                } else {
                                    guardadorWatermark.launch("PDF_MarcaAgua.pdf")
                                }
                            },
                            enabled = !estaAplicandoWatermark && watermarkTexto.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Estampar y Guardar PDF")
                        }
                    }

                    AnimatedVisibility(visible = estaAplicandoWatermark, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Estampando marca de agua en las páginas...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Tarjeta D: Quitar Contraseña (Desproteger)
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
                            Icons.Filled.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Quitar Contraseña (Desproteger)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Elimina la clave de cifrado de un documento PDF protegido para guardarlo libremente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { selectorDesprotegerPdf.launch("application/pdf") },
                        enabled = !estaDesprotegiendo,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (desprotegerPdfNombre.isEmpty()) "Seleccionar PDF Protegido" else "Cambiar PDF")
                    }

                    if (desprotegerPdfNombre.isNotEmpty()) {
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
                                text = desprotegerPdfNombre,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = desprotegerClave,
                            onValueChange = { desprotegerClave = it },
                            label = { Text("Contraseña actual del documento") },
                            singleLine = true,
                            visualTransformation = if (desprotegerClaveVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val icono = if (desprotegerClaveVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                IconButton(onClick = { desprotegerClaveVisible = !desprotegerClaveVisible }) {
                                    Icon(
                                        icono,
                                        contentDescription = if (desprotegerClaveVisible) "Ocultar clave" else "Mostrar clave"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                if (desprotegerClave.isBlank()) {
                                    Toast.makeText(context, "Ingresa la contraseña actual del documento", Toast.LENGTH_SHORT).show()
                                } else {
                                    guardadorDesproteger.launch("PDF_SinClave.pdf")
                                }
                            },
                            enabled = !estaDesprotegiendo && desprotegerClave.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.LockOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Desproteger y Guardar PDF")
                        }
                    }

                    AnimatedVisibility(visible = estaDesprotegiendo, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Descifrando documento PDF...",
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

private fun crearBitmapFirma(paths: List<List<Offset>>): android.graphics.Bitmap? {
    if (paths.isEmpty()) return null
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE

    for (stroke in paths) {
        for (pt in stroke) {
            if (pt.x < minX) minX = pt.x
            if (pt.x > maxX) maxX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.y > maxY) maxY = pt.y
        }
    }

    if (minX >= maxX || minY >= maxY) return null

    val padding = 16f
    val startX = (minX - padding).coerceAtLeast(0f)
    val startY = (minY - padding).coerceAtLeast(0f)
    val width = ((maxX - minX) + 2 * padding).toInt().coerceAtLeast(1)
    val height = ((maxY - minY) + 2 * padding).toInt().coerceAtLeast(1)

    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 6f
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }

    for (stroke in paths) {
        if (stroke.size > 1) {
            val path = android.graphics.Path()
            path.moveTo(stroke[0].x - startX, stroke[0].y - startY)
            for (i in 1 until stroke.size) {
                path.lineTo(stroke[i].x - startX, stroke[i].y - startY)
            }
            canvas.drawPath(path, paint)
        } else if (stroke.size == 1) {
            canvas.drawPoint(stroke[0].x - startX, stroke[0].y - startY, paint)
        }
    }
    return bitmap
}

private fun firmarPdf(
    context: Context,
    sourceUri: Uri,
    destUri: Uri,
    pageNumber: Int,
    bitmap: android.graphics.Bitmap,
    posicion: String
): Boolean {
    return try {
        val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return false
        val document = PDDocument.load(inputStream, MemoryUsageSetting.setupMainMemoryOnly())
        val total = document.numberOfPages
        val pageIdx = (pageNumber - 1).coerceIn(0, total - 1)
        val page = document.getPage(pageIdx)

        val pdImage = LosslessFactory.createFromImage(document, bitmap)

        val mediaBox = page.cropBox ?: page.mediaBox
        val pageWidth = mediaBox.width
        val margin = 36f

        val sigAspect = bitmap.height.toFloat() / bitmap.width.toFloat()
        var sigWidth = 150f
        var sigHeight = sigWidth * sigAspect
        if (sigHeight > 80f) {
            sigHeight = 80f
            sigWidth = sigHeight / sigAspect
        }

        val x = when (posicion) {
            "Inferior Izquierda" -> mediaBox.lowerLeftX + margin
            "Inferior Centro" -> mediaBox.lowerLeftX + (pageWidth - sigWidth) / 2f
            else -> mediaBox.upperRightX - margin - sigWidth
        }
        val y = mediaBox.lowerLeftY + margin

        val contentStream = PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true
        )
        contentStream.drawImage(pdImage, x, y, sigWidth, sigHeight)
        contentStream.close()

        context.contentResolver.openOutputStream(destUri)?.use { out ->
            document.save(out)
        }
        document.close()
        inputStream.close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun protegerPdf(
    context: Context,
    sourceUri: Uri,
    destUri: Uri,
    clave: String
): Boolean {
    return try {
        val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return false
        val document = PDDocument.load(inputStream, MemoryUsageSetting.setupMainMemoryOnly())
        val accessPermission = AccessPermission()
        val policy = StandardProtectionPolicy(clave, clave, accessPermission)
        policy.encryptionKeyLength = 128
        document.protect(policy)
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            document.save(out)
        }
        document.close()
        inputStream.close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
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
            PDDocument.load(stream, MemoryUsageSetting.setupMainMemoryOnly()).use { doc ->
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
        sourceDoc = PDDocument.load(inputStream, MemoryUsageSetting.setupMainMemoryOnly())
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

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exception ->
            if (cont.isActive) cont.resumeWithException(exception)
        }
        addOnCanceledListener {
            cont.cancel()
        }
    }

private suspend fun ejecutarOcrLocal(
    context: Context,
    uri: Uri,
    onProgreso: suspend (String) -> Unit
): String {
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val sb = StringBuilder()

    return try {
        pfd = context.contentResolver.openFileDescriptor(uri, "r")
        if (pfd == null) return "Error: No se pudo abrir el archivo PDF."
        renderer = PdfRenderer(pfd)
        val totalPaginas = renderer.pageCount
        if (totalPaginas == 0) return "El documento PDF no contiene páginas."

        for (i in 0 until totalPaginas) {
            onProgreso("Ejecutando OCR local: página ${i + 1} de $totalPaginas...")
            val page = renderer.openPage(i)
            try {
                val maxDim = maxOf(page.width, page.height)
                val factor = if (maxDim > 0) (2000f / maxDim).coerceAtMost(2.0f) else 1.0f
                val targetW = (page.width * factor).toInt().coerceAtLeast(1)
                val targetH = (page.height * factor).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val image = InputImage.fromBitmap(bitmap, 0)
                val visionText = recognizer.process(image).awaitTask()
                bitmap.recycle()

                val text = visionText.text.trim()
                if (text.isNotEmpty()) {
                    sb.append("## Página ${i + 1}\n\n").append(text).append("\n\n")
                }
            } finally {
                try {
                    page.close()
                } catch (_: Exception) {}
            }
        }

        val res = sb.toString().trim()
        if (res.isEmpty()) "No se detectó texto en el PDF escaneado." else res
    } catch (e: Exception) {
        "Error durante el reconocimiento OCR: ${e.localizedMessage ?: e.message}"
    } finally {
        try { recognizer.close() } catch (_: Exception) {}
        try { renderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
    }
}

private fun procesarPdfAMarkdown(context: Context, uri: Uri): String {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        if (inputStream == null) return "Error: No se pudo abrir el archivo."
        val document = PDDocument.load(inputStream, MemoryUsageSetting.setupMainMemoryOnly())
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

private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
    var count: Long = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
    }

    override fun flush() = out.flush()
    override fun close() = out.close()
}

private data class ResultadoCompresion(
    val tamanoOriginal: Long,
    val tamanoFinal: Long,
    val porcentaje: Double
)

private fun comprimirPdf(
    context: Context,
    sourceUri: Uri,
    destUri: Uri,
    calidad: Float,
    maxDimension: Int
): ResultadoCompresion? {
    val origSize = obtenerTamanoUri(context, sourceUri)
    val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
    return try {
        val document = PDDocument.load(inputStream, MemoryUsageSetting.setupMainMemoryOnly())
        val processedObjects = mutableSetOf<COSBase>()

        for (page in document.pages) {
            val resources = page.resources ?: continue
            val names = resources.xObjectNames?.toList() ?: emptyList()
            for (name in names) {
                if (resources.isImageXObject(name)) {
                    val xObject = resources.getXObject(name) as? PDImageXObject ?: continue
                    if (processedObjects.contains(xObject.cosObject)) {
                        continue
                    }
                    val originalBitmap = try {
                        xObject.image
                    } catch (e: Exception) {
                        null
                    } ?: continue

                    val origWidth = originalBitmap.width
                    val origHeight = originalBitmap.height
                    val scaledBitmap = if (origWidth > maxDimension || origHeight > maxDimension) {
                        val ratio = origWidth.toFloat() / origHeight.toFloat()
                        val targetWidth: Int
                        val targetHeight: Int
                        if (origWidth >= origHeight) {
                            targetWidth = maxDimension
                            targetHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
                        } else {
                            targetHeight = maxDimension
                            targetWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
                        }
                        val scaled = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
                        if (scaled != originalBitmap) {
                            originalBitmap.recycle()
                        }
                        scaled
                    } else {
                        originalBitmap
                    }

                    try {
                        val compressedImage = JPEGFactory.createFromImage(document, scaledBitmap, calidad)
                        processedObjects.add(compressedImage.cosObject)
                        resources.put(name, compressedImage)
                    } finally {
                        scaledBitmap.recycle()
                    }
                }
            }
        }

        var bytesEscritos = 0L
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            val countingOut = CountingOutputStream(out)
            document.save(countingOut)
            countingOut.flush()
            bytesEscritos = countingOut.count
        }
        document.close()
        inputStream.close()

        val finalSize = if (bytesEscritos > 0L) bytesEscritos else obtenerTamanoUri(context, destUri)
        val pct = if (origSize > 0L && finalSize in 1 until origSize) {
            ((origSize - finalSize).toDouble() / origSize.toDouble()) * 100.0
        } else {
            0.0
        }

        ResultadoCompresion(
            tamanoOriginal = origSize,
            tamanoFinal = finalSize,
            porcentaje = pct
        )
    } catch (e: Exception) {
        e.printStackTrace()
        try { inputStream.close() } catch (_: Exception) {}
        null
    }
}

private fun crearPdfDesdeImagenes(
    context: Context,
    uris: List<Uri>,
    destUri: Uri
): Boolean {
    if (uris.isEmpty()) return false
    return try {
        val document = PDDocument()
        for (uri in uris) {
            val bitmap = decodificarBitmapOptimizada(context, uri, maxDimension = 2000) ?: continue
            try {
                val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                document.addPage(page)
                val pdImage = JPEGFactory.createFromImage(document, bitmap, 0.85f)
                PDPageContentStream(document, page).use { contentStream ->
                    contentStream.drawImage(pdImage, 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                }
            } finally {
                bitmap.recycle()
            }
        }

        if (document.numberOfPages == 0) {
            document.close()
            return false
        }

        context.contentResolver.openOutputStream(destUri)?.use { out ->
            document.save(out)
        }
        document.close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun decodificarBitmapOptimizada(
    context: Context,
    uri: Uri,
    maxDimension: Int = 2000
): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        val w = options.outWidth
        val h = options.outHeight
        if (w <= 0 || h <= 0) return null

        var inSampleSize = 1
        if (w > maxDimension || h > maxDimension) {
            val halfW = w / 2
            val halfH = h / 2
            while ((halfW / inSampleSize) >= maxDimension || (halfH / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated != decoded) {
                decoded.recycle()
            }
            rotated
        } else {
            decoded
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun obtenerTamanoUri(context: Context, uri: Uri): Long {
    try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            if (pfd.statSize > 0) return pfd.statSize
        }
    } catch (_: Exception) {}
    try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeIdx != -1 && cursor.moveToFirst()) {
                val s = cursor.getLong(sizeIdx)
                if (s > 0) return s
            }
        }
    } catch (_: Exception) {}
    return -1L
}

private fun formatearTamanoBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val unidades = arrayOf("B", "KB", "MB", "GB")
    var digitos = bytes.toDouble()
    var idx = 0
    while (digitos >= 1024 && idx < unidades.size - 1) {
        digitos /= 1024
        idx++
    }
    return String.format(Locale.getDefault(), "%.1f %s", digitos, unidades[idx])
}

private fun rotarPaginasPdf(
    context: Context,
    sourceUri: Uri,
    destUri: Uri,
    angulo: Int,
    paginaEspecifica: Int?
): Boolean {
    return try {
        val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return false
        val document = PDDocument.load(inputStream, MemoryUsageSetting.setupMainMemoryOnly())
        val total = document.numberOfPages
        if (paginaEspecifica != null) {
            val pageIdx = (paginaEspecifica - 1).coerceIn(0, total - 1)
            val page = document.getPage(pageIdx)
            page.rotation = (page.rotation + angulo) % 360
        } else {
            for (page in document.pages) {
                page.rotation = (page.rotation + angulo) % 360
            }
        }
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            document.save(out)
        }
        document.close()
        inputStream.close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun limpiarTextoParaPdf(texto: String): String {
    val normalizado = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFC)
    val sb = StringBuilder()
    for (ch in normalizado) {
        if (ch.code in 32..255) {
            sb.append(ch)
        } else {
            sb.append('?')
        }
    }
    val res = sb.toString().trim()
    return if (res.isNotEmpty()) res else "CONFIDENCIAL"
}

private fun estamparMarcaAguaPdf(
    context: Context,
    sourceUri: Uri,
    destUri: Uri,
    marcaAguaTexto: String
): Boolean {
    val textoLimpio = limpiarTextoParaPdf(if (marcaAguaTexto.isBlank()) "CONFIDENCIAL" else marcaAguaTexto)
    return try {
        val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return false
        val document = PDDocument.load(inputStream, MemoryUsageSetting.setupMainMemoryOnly())
        val font = PDType1Font.HELVETICA_BOLD

        val graphicsState = PDExtendedGraphicsState().apply {
            setNonStrokingAlphaConstant(0.25f)
        }

        for (page in document.pages) {
            val mediaBox = page.cropBox ?: page.mediaBox
            val pageWidth = mediaBox.width
            val pageHeight = mediaBox.height

            val fontSize = (pageWidth / 10f).coerceIn(24f, 54f)
            val stringWidth = try {
                (font.getStringWidth(textoLimpio) / 1000f) * fontSize
            } catch (_: Exception) {
                fontSize * textoLimpio.length * 0.5f
            }

            val centerX = mediaBox.lowerLeftX + (pageWidth / 2f)
            val centerY = mediaBox.lowerLeftY + (pageHeight / 2f)

            val rad = Math.toRadians(45.0)
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()
            val halfW = stringWidth / 2f
            val halfH = fontSize / 3f

            val tx = centerX - (halfW * cos - halfH * sin)
            val ty = centerY - (halfW * sin + halfH * cos)

            val matrix = PdfMatrix.getRotateInstance(rad, tx, ty)

            PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
            ).use { contentStream ->
                contentStream.setGraphicsStateParameters(graphicsState)
                contentStream.setNonStrokingColor(0.85f, 0.2f, 0.2f)
                contentStream.beginText()
                contentStream.setFont(font, fontSize)
                contentStream.setTextMatrix(matrix)
                contentStream.showText(textoLimpio)
                contentStream.endText()
            }
        }

        context.contentResolver.openOutputStream(destUri)?.use { out ->
            document.save(out)
        }
        document.close()
        inputStream.close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private sealed class DesprotegerResultado {
    object Exito : DesprotegerResultado()
    object ContrasenaIncorrecta : DesprotegerResultado()
    data class Error(val mensaje: String) : DesprotegerResultado()
}

private fun quitarContrasenaPdf(
    context: Context,
    sourceUri: Uri,
    destUri: Uri,
    clave: String
): DesprotegerResultado {
    var inputStream: InputStream? = null
    var document: PDDocument? = null
    return try {
        inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: return DesprotegerResultado.Error("No se pudo abrir el archivo")
        document = PDDocument.load(inputStream, clave, MemoryUsageSetting.setupMainMemoryOnly())
        document.setAllSecurityToBeRemoved(true)
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            document.save(out)
        }
        DesprotegerResultado.Exito
    } catch (e: InvalidPasswordException) {
        DesprotegerResultado.ContrasenaIncorrecta
    } catch (e: Exception) {
        e.printStackTrace()
        DesprotegerResultado.Error(e.localizedMessage ?: "Error al procesar el PDF")
    } finally {
        try { document?.close() } catch (_: Exception) {}
        try { inputStream?.close() } catch (_: Exception) {}
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    AppPruebaTheme {
        MainScreen()
    }
}
