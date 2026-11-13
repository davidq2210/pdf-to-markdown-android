# Suite PDF Offline para Android

Aplicación nativa de Android desarrollada en **Kotlin** y **Jetpack Compose (Material Design 3)** para la manipulación, conversión, optimización y seguridad de documentos PDF de forma **100% offline**.

Probada en dispositivos Xiaomi (**Redmi Note 7** con MIUI y **Redmi Note 14** con HyperOS).

## Arquitectura y Stack Tecnológico
* **Lenguaje:** Kotlin (JVM 17/21) con corrutinas (`Dispatchers.IO`).
* **Interfaz Gráfica:** Jetpack Compose + Material Design 3 (`NavigationBar` de 3 módulos).
* **Motor PDF:** `com.tom_roush:pdfbox-android:2.0.27.0`
* **Motor Office:** Apache POI (`poi` y `poi-ooxml` 5.2.3) para `.docx` y `.xlsx`.
* **OCR Local:** Google ML Kit (`text-recognition:16.0.1`) + `PdfRenderer` nativo (sin internet ni Play Services).
* **Almacenamiento:** Storage Access Framework (`GetContent`, `OpenMultipleDocuments`, `CreateDocument`).

## Funcionalidades por Módulo

### 1. Pestaña Convertir
* **PDF a Markdown (`.md`):** Extracción de texto digital con visor `Monospace` y copiado rápido.
* **Exportación Office:** Generación nativa de documentos Word (`.docx`) y hojas de cálculo Excel (`.xlsx`).
* **OCR Local Automático y Manual:** Detecta PDFs escaneados o permite activar el *Modo OCR*, renderizando con `PdfRenderer` y reconociendo texto offline con ML Kit.

### 2. Pestaña Herramientas
* **Unir PDFs (Merge):** Concatenación de múltiples archivos en un solo PDF.
* **Extraer Páginas (Split):** Extracción de rangos de páginas específicos.
* **Comprimir PDF:** Re-muestreo JPEG en 3 niveles (*Ligera* 75%, *Media* 50%, *Extrema* 30%) con reporte de ahorro.
* **Imágenes a PDF:** Empaquetado múltiple de fotos respetando orientación EXIF.
* **Rotar Páginas:** Giro de `90°`, `180°` o `270°` global o por página específica.

### 3. Pestaña Seguridad
* **Firma Manuscrita:** Lienzo táctil transparente (`ARGB_8888`) estampado en la página y esquina elegida.
* **Proteger con Contraseña:** Cifrado estándar de 128 bits (`StandardProtectionPolicy`).
* **Quitar Contraseña:** Apertura con clave y eliminación de restricciones.
* **Marca de Agua:** Estampado diagonal centrado a 45° con opacidad al 25%.

## Compilación y Despliegue vía ADB

```bash
./gradlew assembleDebug
adb push app/build/intermediates/apk/debug/app-debug.apk /data/local/tmp/app.apk
adb shell pm install -r -d -t /data/local/tmp/app.apk
adb shell am start -S -n com.example.appprueba/.MainActivity
