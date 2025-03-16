package com.example.lector_codigos_qr;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView resultTextView;
    private MaterialButton uploadButton;
    private BarcodeScanner barcodeScanner;

    // Lanzador para seleccionar imágenes
    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        loadImageAndDetectBarcodes(imageUri);
                    }
                }
            }
    );

    // Lanzador para solicitar permisos
    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openImagePicker();
                } else {
                    Toast.makeText(MainActivity.this, "Permiso denegado", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Aplicar insets para edge-to-edge al root view (id "main")
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Inicializar vistas
        imageView = findViewById(R.id.imageView);
        resultTextView = findViewById(R.id.resultTextView);
        uploadButton = findViewById(R.id.uploadButton);

        // Configurar opciones del escáner para detectar todos los formatos
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionAndPickImage();
            }
        });
    }

    private void checkPermissionAndPickImage() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        } else if (shouldShowRequestPermissionRationale(permission)) {
            Toast.makeText(this, "Se necesita permiso para acceder a las imágenes", Toast.LENGTH_LONG).show();
            requestPermissionLauncher.launch(permission);
        } else {
            requestPermissionLauncher.launch(permission);
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private void loadImageAndDetectBarcodes(Uri imageUri) {
        try {
            // Mostrar la imagen seleccionada
            imageView.setImageURI(imageUri);
            // Crear InputImage para ML Kit
            InputImage inputImage = InputImage.fromFilePath(this, imageUri);
            // Informar al usuario que se está analizando la imagen
            resultTextView.setText("Analizando imagen...");

            barcodeScanner.process(inputImage)
                    .addOnSuccessListener(new OnSuccessListener<List<Barcode>>() {
                        @Override
                        public void onSuccess(List<Barcode> barcodes) {
                            boolean urlFound = false;
                            for (Barcode barcode : barcodes) {
                                if (barcode.getValueType() == Barcode.TYPE_URL) {
                                    String urlString = barcode.getRawValue();
                                    if (urlString != null && !urlString.isEmpty()) {
                                        urlFound = true;
                                        try {
                                            Uri uri = Uri.parse(urlString);
                                            // Abrir el link automáticamente
                                            startActivity(new Intent(Intent.ACTION_VIEW, uri));
                                            // Opcional: terminar la actividad al abrir el enlace
                                            finish();
                                        } catch (Exception e) {
                                            Toast.makeText(MainActivity.this, "URL inválida: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                        break;
                                    }
                                }
                            }
                            if (!urlFound) {
                                StringBuilder stringBuilder = new StringBuilder();
                                stringBuilder.append("No se detectó un URL en la imagen.\n\n");
                                for (int i = 0; i < barcodes.size(); i++) {
                                    Barcode barcode = barcodes.get(i);
                                    int valueType = barcode.getValueType();
                                    String typeStr;
                                    switch (valueType) {
                                        case Barcode.TYPE_CONTACT_INFO:
                                            typeStr = "Contacto";
                                            break;
                                        case Barcode.TYPE_EMAIL:
                                            typeStr = "Email";
                                            break;
                                        case Barcode.TYPE_PHONE:
                                            typeStr = "Teléfono";
                                            break;
                                        case Barcode.TYPE_SMS:
                                            typeStr = "SMS";
                                            break;
                                        case Barcode.TYPE_TEXT:
                                            typeStr = "Texto";
                                            break;
                                        case Barcode.TYPE_WIFI:
                                            typeStr = "WiFi";
                                            break;
                                        case Barcode.TYPE_GEO:
                                            typeStr = "Ubicación";
                                            break;
                                        case Barcode.TYPE_CALENDAR_EVENT:
                                            typeStr = "Evento";
                                            break;
                                        case Barcode.TYPE_DRIVER_LICENSE:
                                            typeStr = "Licencia";
                                            break;
                                        case Barcode.TYPE_ISBN:
                                            typeStr = "ISBN";
                                            break;
                                        case Barcode.TYPE_PRODUCT:
                                            typeStr = "Producto";
                                            break;
                                        default:
                                            typeStr = "Desconocido";
                                            break;
                                    }
                                    String formatStr;
                                    switch (barcode.getFormat()) {
                                        case Barcode.FORMAT_QR_CODE:
                                            formatStr = "QR Code";
                                            break;
                                        case Barcode.FORMAT_CODE_128:
                                            formatStr = "Code 128";
                                            break;
                                        case Barcode.FORMAT_CODE_39:
                                            formatStr = "Code 39";
                                            break;
                                        case Barcode.FORMAT_EAN_13:
                                            formatStr = "EAN-13";
                                            break;
                                        case Barcode.FORMAT_EAN_8:
                                            formatStr = "EAN-8";
                                            break;
                                        case Barcode.FORMAT_UPC_A:
                                            formatStr = "UPC-A";
                                            break;
                                        case Barcode.FORMAT_UPC_E:
                                            formatStr = "UPC-E";
                                            break;
                                        case Barcode.FORMAT_PDF417:
                                            formatStr = "PDF417";
                                            break;
                                        case Barcode.FORMAT_AZTEC:
                                            formatStr = "Aztec";
                                            break;
                                        case Barcode.FORMAT_DATA_MATRIX:
                                            formatStr = "Data Matrix";
                                            break;
                                        case Barcode.FORMAT_ITF:
                                            formatStr = "ITF";
                                            break;
                                        case Barcode.FORMAT_CODABAR:
                                            formatStr = "Codabar";
                                            break;
                                        default:
                                            formatStr = "Desconocido";
                                            break;
                                    }
                                    stringBuilder.append("Código ").append(i + 1).append(":\n");
                                    stringBuilder.append("Formato: ").append(formatStr).append("\n");
                                    stringBuilder.append("Tipo: ").append(typeStr).append("\n");
                                    stringBuilder.append("Valor: ").append(barcode.getRawValue()).append("\n\n");
                                }
                                resultTextView.setText(stringBuilder.toString());
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            resultTextView.setText("Error al procesar la imagen: " + e.getMessage());
                        }
                    });

        } catch (IOException e) {
            Toast.makeText(this, "Error al cargar la imagen", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        barcodeScanner.close();
    }
}
