package com.example.fakeproductdetector.ui.scan

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fakeproductdetector.domain.model.Category
import com.example.fakeproductdetector.domain.model.ScanResult
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onNavigateToResult: (ScanResult) -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedCategory by remember { mutableStateOf(Category.OTHER) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var detectedBarcode by remember { mutableStateOf<String?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var showScanWarning by remember { mutableStateOf(false) }
    var proceedWithScan by remember { mutableStateOf<(() -> Unit)?>(null) }

    // ── Runtime camera permission ──────────────────────────────────────────
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    // ──────────────────────────────────────────────────────────────────────

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ScanUiState.Success -> {
                onNavigateToResult(state.result)
                viewModel.reset()
            }
            is ScanUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.reset()
            }
            else -> {}
        }
    }

    // ── Scan validation warning dialog ────────────────────────────────────
    if (showScanWarning) {
        AlertDialog(
            onDismissRequest = { showScanWarning = false; proceedWithScan = null },
            title = { Text("Scan May Not Be Accurate") },
            text = {
                Text(
                    "For best results, point the camera at a product with:\n" +
                    "  \u2022 Visible packaging or labels\n" +
                    "  \u2022 Brand logos or text\n" +
                    "  \u2022 A barcode or QR code\n\n" +
                    "Scanning plain objects like fabric, food without labels, or " +
                    "natural items may give inaccurate results.\n\n" +
                    "Do you want to continue anyway?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showScanWarning = false
                    proceedWithScan?.invoke()
                    proceedWithScan = null
                }) {
                    Text("Scan Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showScanWarning = false
                    proceedWithScan = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
    // ──────────────────────────────────────────────────────────────────────

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->

        // ── No permission yet — show rationale screen ──────────────────────
        if (!hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "FakeProductDetector needs camera access to scan and analyse products for authenticity.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Camera Permission")
                    }
                }
            }
            return@Scaffold
        }
        // ──────────────────────────────────────────────────────────────────

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // CameraX Preview
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture

                        val barcodeScanner = BarcodeScanning.getClient()
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(analysisExecutor) { proxy ->
                            val mediaImage = proxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    proxy.imageInfo.rotationDegrees
                                )
                                barcodeScanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        val barcode = barcodes.firstOrNull {
                                            it.valueType == Barcode.TYPE_PRODUCT ||
                                                    it.valueType == Barcode.TYPE_TEXT ||
                                                    it.rawValue != null
                                        }
                                        barcode?.rawValue?.let { detectedBarcode = it }
                                    }
                                    .addOnCompleteListener { proxy.close() }
                            } else {
                                proxy.close()
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview, capture, analysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Viewfinder overlay
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.Center)
                    .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            )

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FakeProductDetector",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onNavigateToHistory) {
                    Icon(Icons.Filled.History, contentDescription = "History", tint = Color.White)
                }
            }

            // Barcode indicator — always visible so user knows scan status
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(
                        if (detectedBarcode != null) Color(0xFF4CAF50).copy(alpha = 0.85f)
                        else Color(0xFF757575).copy(alpha = 0.85f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (detectedBarcode != null)
                        "✓ Barcode: $detectedBarcode"
                    else
                        "No barcode detected — image-only scan",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category selector
                Column {
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.name.lowercase()
                            .replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Product Category (optional)", color = Color.White) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.6f),
                            focusedTrailingIconColor = Color.White,
                            unfocusedTrailingIconColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        Category.entries.forEach { category ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        category.name.lowercase()
                                            .replaceFirstChar { it.uppercase() }
                                    )
                                },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
                Text(
                    text = "Select a category for more accurate results",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
                } // end Column

                // Capture button
                val isRateLimited = uiState is ScanUiState.RateLimited
                val isLoading = uiState is ScanUiState.Loading
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            if (isRateLimited || isLoading) Color.Gray else Color.White,
                            CircleShape
                        )
                ) {
                    IconButton(
                        onClick = {
                            if (isRateLimited || isLoading) return@IconButton
                            val capture = imageCapture ?: return@IconButton

                            val doScan = {
                                val outputFile = File(
                                    context.cacheDir,
                                    "scan_${System.currentTimeMillis()}.jpg"
                                )
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                                capture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            val uri = Uri.fromFile(outputFile).toString()
                                            viewModel.scanProduct(uri, detectedBarcode, selectedCategory)
                                        }
                                        override fun onError(exc: ImageCaptureException) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Capture failed: ${exc.message}")
                                            }
                                        }
                                    }
                                )
                            }

                            if (detectedBarcode == null && selectedCategory == Category.OTHER) {
                                proceedWithScan = doScan
                                showScanWarning = true
                            } else {
                                doScan()
                            }
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Camera,
                            contentDescription = "Capture",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // Loading overlay
            AnimatedVisibility(
                visible = uiState is ScanUiState.Loading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            text = (uiState as? ScanUiState.Loading)?.message ?: "Analyzing...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Rate limit banner with live countdown
            AnimatedVisibility(
                visible = uiState is ScanUiState.RateLimited,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val rateLimitState = uiState as? ScanUiState.RateLimited
                RateLimitBanner(
                    secondsRemaining = rateLimitState?.secondsRemaining ?: 0,
                    isQuotaExhausted = rateLimitState?.isQuotaExhausted ?: false,
                    title            = rateLimitState?.title ?: "API rate limit reached",
                    subtitle         = rateLimitState?.subtitle ?: "Ready to scan again in"
                )
            }
        }
    }
}

@Composable
private fun RateLimitBanner(
    secondsRemaining: Int,
    isQuotaExhausted: Boolean = false,
    title: String = "API rate limit reached",
    subtitle: String = "Ready to scan again in"
) {
    val totalSeconds = if (isQuotaExhausted) 300 else 60
    val bannerColor  = if (isQuotaExhausted) Color(0xFF4A148C) else Color(0xFFB71C1C)
    val progress     = secondsRemaining.toFloat() / totalSeconds.toFloat()

    Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .background(bannerColor)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Camera,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = androidx.compose.ui.Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "$subtitle  $secondsRemaining s",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            // Progress bar drains as cooldown ticks
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Color.White.copy(alpha = 0.25f),
                        androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                    )
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(
                            Color.White,
                            androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}