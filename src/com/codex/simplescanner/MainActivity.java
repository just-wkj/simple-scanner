package com.codex.simplescanner;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Patterns;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int CAMERA_REQUEST = 7;

    private TextureView textureView;
    private TextView statusText;
    private LinearLayout resultPanel;
    private TextView resultText;
    private TextView formatText;
    private Button openButton;
    private Button copyButton;
    private Button rescanButton;

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private HandlerThread decodeThread;
    private Handler decodeHandler;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageReader imageReader;
    private Size previewSize;
    private String cameraId;

    private final MultiFormatReader reader = new MultiFormatReader();
    private volatile boolean scanning = true;
    private volatile boolean decodeBusy = false;
    private long lastDecodeTime = 0L;
    private String currentResult = "";

    private final TextureView.SurfaceTextureListener surfaceListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCameraIfReady();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createCameraSession();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
            setStatus("相机已断开");
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
            setStatus("无法打开相机");
        }
    };

    private final ImageReader.OnImageAvailableListener imageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image == null) {
                return;
            }

            long now = System.currentTimeMillis();
            if (!scanning || decodeBusy || now - lastDecodeTime < 240L) {
                image.close();
                return;
            }
            lastDecodeTime = now;

            final int width = image.getWidth();
            final int height = image.getHeight();
            final byte[] luminance = copyLuminance(image);
            image.close();
            if (luminance == null) {
                return;
            }

            decodeBusy = true;
            decodeHandler.post(new Runnable() {
                @Override
                public void run() {
                    final Result result = decode(luminance, width, height);
                    decodeBusy = false;
                    if (result != null && scanning) {
                        scanning = false;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showResult(result);
                            }
                        });
                    }
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setupReader();
        setupUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startThreads();
        if (textureView.isAvailable()) {
            openCameraIfReady();
        } else {
            textureView.setSurfaceTextureListener(surfaceListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopThreads();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCameraIfReady();
            } else {
                setStatus("需要相机权限才能扫码");
            }
        }
    }

    private void setupReader() {
        List<BarcodeFormat> formats = Arrays.asList(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.AZTEC,
                BarcodeFormat.PDF_417,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.CODABAR,
                BarcodeFormat.ITF
        );
        Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);
    }

    private void setupUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        textureView = new TextureView(this);
        root.addView(textureView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        root.addView(new ScanOverlayView(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        statusText = new TextView(this);
        statusText.setText("将二维码或条形码放入取景框");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(16), dp(18), dp(16), dp(10));
        statusText.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.gravity = Gravity.TOP;
        root.addView(statusText, statusParams);

        resultPanel = new LinearLayout(this);
        resultPanel.setOrientation(LinearLayout.VERTICAL);
        resultPanel.setPadding(dp(16), dp(14), dp(16), dp(16));
        resultPanel.setBackgroundColor(0xF7FFFFFF);
        resultPanel.setVisibility(View.GONE);

        TextView title = new TextView(this);
        title.setText("扫描结果");
        title.setTextColor(0xFF111111);
        title.setTextSize(17);
        title.setGravity(Gravity.START);
        title.setPadding(0, 0, 0, dp(8));
        resultPanel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        resultText = new TextView(this);
        resultText.setTextColor(0xFF222222);
        resultText.setTextSize(15);
        resultText.setTextIsSelectable(true);
        resultText.setPadding(dp(10), dp(10), dp(10), dp(10));
        resultText.setBackgroundColor(0xFFF1F3F4);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(resultText, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        resultPanel.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(120)
        ));

        formatText = new TextView(this);
        formatText.setTextColor(0xFF666666);
        formatText.setTextSize(12);
        formatText.setGravity(Gravity.START);
        formatText.setPadding(dp(2), dp(8), dp(2), 0);
        resultPanel.addView(formatText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, dp(2));

        copyButton = new Button(this);
        copyButton.setText("复制");
        styleActionButton(copyButton);
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyResult();
            }
        });

        openButton = new Button(this);
        openButton.setText("浏览器打开");
        styleActionButton(openButton);
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openResult();
            }
        });

        rescanButton = new Button(this);
        rescanButton.setText("继续扫描");
        styleActionButton(rescanButton);
        rescanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartScan();
            }
        });

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, dp(54), 1f);
        buttonParams.setMargins(dp(4), 0, dp(4), 0);
        actions.addView(copyButton, buttonParams);
        actions.addView(openButton, new LinearLayout.LayoutParams(buttonParams));
        actions.addView(rescanButton, new LinearLayout.LayoutParams(buttonParams));
        resultPanel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panelParams.gravity = Gravity.BOTTOM;
        root.addView(resultPanel, panelParams);

        setContentView(root);
    }

    private void startThreads() {
        cameraThread = new HandlerThread("camera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());

        decodeThread = new HandlerThread("decode");
        decodeThread.start();
        decodeHandler = new Handler(decodeThread.getLooper());
    }

    private void stopThreads() {
        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
            cameraHandler = null;
        }
        if (decodeThread != null) {
            decodeThread.quitSafely();
            decodeThread = null;
            decodeHandler = null;
        }
    }

    private void openCameraIfReady() {
        if (cameraDevice != null || cameraHandler == null || !textureView.isAvailable()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
            return;
        }

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = chooseBackCamera(manager);
            if (cameraId == null) {
                setStatus("没有找到后置相机");
                return;
            }
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                setStatus("相机不支持预览");
                return;
            }
            previewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class));
            manager.openCamera(cameraId, stateCallback, cameraHandler);
        } catch (CameraAccessException e) {
            setStatus("无法访问相机");
        } catch (SecurityException e) {
            setStatus("需要相机权限才能扫码");
        }
    }

    private String chooseBackCamera(CameraManager manager) throws CameraAccessException {
        String[] ids = manager.getCameraIdList();
        for (String id : ids) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        return ids.length > 0 ? ids[0] : null;
    }

    private Size choosePreviewSize(Size[] sizes) {
        List<Size> candidates = new ArrayList<Size>();
        for (Size size : sizes) {
            int area = size.getWidth() * size.getHeight();
            if (area <= 1280 * 720 && area >= 640 * 360) {
                candidates.add(size);
            }
        }
        if (candidates.isEmpty()) {
            candidates.addAll(Arrays.asList(sizes));
        }
        Collections.sort(candidates, new Comparator<Size>() {
            @Override
            public int compare(Size a, Size b) {
                return b.getWidth() * b.getHeight() - a.getWidth() * a.getHeight();
            }
        });
        return candidates.get(0);
    }

    private void createCameraSession() {
        if (cameraDevice == null || previewSize == null || textureView.getSurfaceTexture() == null) {
            return;
        }
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(texture);

            imageReader = ImageReader.newInstance(
                    previewSize.getWidth(),
                    previewSize.getHeight(),
                    ImageFormat.YUV_420_888,
                    2
            );
            imageReader.setOnImageAvailableListener(imageListener, cameraHandler);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.addTarget(imageReader.getSurface());
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

            List<Surface> surfaces = Arrays.asList(previewSurface, imageReader.getSurface());
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) {
                        return;
                    }
                    captureSession = session;
                    try {
                        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler);
                        setStatus("将二维码或条形码放入取景框");
                    } catch (CameraAccessException e) {
                        setStatus("相机预览启动失败");
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    setStatus("相机配置失败");
                }
            }, cameraHandler);
        } catch (CameraAccessException e) {
            setStatus("相机启动失败");
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            try {
                captureSession.close();
            } catch (Exception ignored) {
            }
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private byte[] copyLuminance(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        byte[] data = new byte[width * height];

        if (pixelStride != 1) {
            return null;
        }

        if (rowStride == width) {
            buffer.get(data);
            return data;
        }

        byte[] row = new byte[rowStride];
        for (int y = 0; y < height; y++) {
            int length = Math.min(rowStride, buffer.remaining());
            buffer.get(row, 0, length);
            System.arraycopy(row, 0, data, y * width, width);
        }
        return data;
    }

    private Result decode(byte[] data, int width, int height) {
        Result result = tryDecode(data, width, height);
        if (result != null) {
            return result;
        }

        byte[] clockwise = rotateClockwise(data, width, height);
        result = tryDecode(clockwise, height, width);
        if (result != null) {
            return result;
        }

        byte[] counterClockwise = rotateCounterClockwise(data, width, height);
        return tryDecode(counterClockwise, height, width);
    }

    private Result tryDecode(byte[] data, int width, int height) {
        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                data,
                width,
                height,
                0,
                0,
                width,
                height,
                false
        );
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        try {
            return reader.decodeWithState(bitmap);
        } catch (NotFoundException e) {
            return null;
        } finally {
            reader.reset();
        }
    }

    private byte[] rotateClockwise(byte[] input, int width, int height) {
        byte[] output = new byte[input.length];
        int index = 0;
        for (int x = 0; x < width; x++) {
            for (int y = height - 1; y >= 0; y--) {
                output[index++] = input[y * width + x];
            }
        }
        return output;
    }

    private byte[] rotateCounterClockwise(byte[] input, int width, int height) {
        byte[] output = new byte[input.length];
        int index = 0;
        for (int x = width - 1; x >= 0; x--) {
            for (int y = 0; y < height; y++) {
                output[index++] = input[y * width + x];
            }
        }
        return output;
    }

    private void showResult(Result result) {
        currentResult = result.getText() == null ? "" : result.getText();
        resultText.setText(currentResult);
        formatText.setText("格式: " + result.getBarcodeFormat().toString());
        openButton.setVisibility(normalizeUrl(currentResult) == null ? View.GONE : View.VISIBLE);
        resultPanel.setVisibility(View.VISIBLE);
        setStatus("已识别，确认后可继续扫描");
    }

    private void restartScan() {
        currentResult = "";
        resultPanel.setVisibility(View.GONE);
        scanning = true;
        setStatus("将二维码或条形码放入取景框");
    }

    private void copyResult() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("扫码结果", currentResult));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    private void openResult() {
        String url = normalizeUrl(currentResult);
        if (url == null) {
            Toast.makeText(this, "不是可打开的网址", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "没有可用浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private String normalizeUrl(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.length() == 0 || text.indexOf(' ') >= 0 || text.indexOf('\n') >= 0) {
            return null;
        }
        String lower = text.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return text;
        }
        if (Patterns.WEB_URL.matcher(text).matches()) {
            return "https://" + text;
        }
        return null;
    }

    private void setStatus(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText(message);
            }
        });
    }

    private void styleActionButton(Button button) {
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(true);
        button.setPadding(dp(2), 0, dp(2), dp(2));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class ScanOverlayView extends View {
        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ScanOverlayView(Context context) {
            super(context);
            dimPaint.setColor(0x77000000);
            framePaint.setColor(0x55FFFFFF);
            framePaint.setStyle(Paint.Style.STROKE);
            framePaint.setStrokeWidth(dp(1));
            cornerPaint.setColor(0xFF24D18B);
            cornerPaint.setStrokeWidth(dp(4));
            cornerPaint.setStyle(Paint.Style.STROKE);
            linePaint.setColor(0xAA24D18B);
            linePaint.setStrokeWidth(dp(2));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            int box = Math.min((int) (w * 0.78f), dp(310));
            int left = (w - box) / 2;
            int top = (h - box) / 2 - dp(40);
            int right = left + box;
            int bottom = top + box;

            canvas.drawRect(0, 0, w, top, dimPaint);
            canvas.drawRect(0, top, left, bottom, dimPaint);
            canvas.drawRect(right, top, w, bottom, dimPaint);
            canvas.drawRect(0, bottom, w, h, dimPaint);

            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawRoundRect(rect, dp(8), dp(8), framePaint);

            int c = dp(28);
            canvas.drawLine(left, top, left + c, top, cornerPaint);
            canvas.drawLine(left, top, left, top + c, cornerPaint);
            canvas.drawLine(right, top, right - c, top, cornerPaint);
            canvas.drawLine(right, top, right, top + c, cornerPaint);
            canvas.drawLine(left, bottom, left + c, bottom, cornerPaint);
            canvas.drawLine(left, bottom, left, bottom - c, cornerPaint);
            canvas.drawLine(right, bottom, right - c, bottom, cornerPaint);
            canvas.drawLine(right, bottom, right, bottom - c, cornerPaint);

            canvas.drawLine(left + dp(18), top + box / 2, right - dp(18), top + box / 2, linePaint);
        }
    }
}
