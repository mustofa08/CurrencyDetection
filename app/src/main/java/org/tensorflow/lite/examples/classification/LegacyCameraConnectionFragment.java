package org.tensorflow.lite.examples.classification;

import android.app.Fragment;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import java.io.IOException;
import java.util.List;
import org.tensorflow.lite.examples.classification.customview.AutoFitTextureView;
import org.tensorflow.lite.examples.classification.env.ImageUtils;
import org.tensorflow.lite.examples.classification.env.Logger;

public class LegacyCameraConnectionFragment extends Fragment {
  private static final Logger LOGGER = new Logger();
  /** Conversion from screen rotation to JPEG orientation. */
  private static final int[] ORIENTATIONS = {90, 0, 270, 180};

  private Camera camera;
  private Camera.PreviewCallback imageListener;
  private Size desiredSize;
  private int layout;
  private AutoFitTextureView textureView;
  private final TextureView.SurfaceTextureListener surfaceTextureListener =
          new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(
                    final SurfaceTexture texture, final int width, final int height) {

              int index = getCameraId();
              camera = Camera.open(index);

              try {
                Camera.Parameters parameters = camera.getParameters();
                List<String> focusModes = parameters.getSupportedFocusModes();
                if (focusModes != null
                        && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                  parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                }
                List<Camera.Size> cameraSizes = parameters.getSupportedPreviewSizes();
                Size[] sizes = new Size[cameraSizes.size()];
                int i = 0;
                for (Camera.Size size : cameraSizes) {
                  sizes[i++] = new Size(size.width, size.height);
                }
                Size previewSize =
                        CameraConnectionFragment.chooseOptimalSize(
                                sizes, desiredSize.getWidth(), desiredSize.getHeight());
                parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
                camera.setDisplayOrientation(ORIENTATIONS[getActivity().getWindowManager().getDefaultDisplay().getRotation()]);
                camera.setParameters(parameters);
                camera.setPreviewTexture(texture);
              } catch (IOException exception) {
                LOGGER.e("Cannot access the camera.", exception);
                camera.release();
              }

              camera.setPreviewCallbackWithBuffer(imageListener);
              Camera.Size s = camera.getParameters().getPreviewSize();
              camera.addCallbackBuffer(new byte[ImageUtils.getYUVByteSize(s.height, s.width)]);

              textureView.setAspectRatio(s.height, s.width);

              camera.startPreview();
            }

            @Override
            public void onSurfaceTextureSizeChanged(
                    final SurfaceTexture texture, final int width, final int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
              stopCamera();
              return true;
            }

            @Override
            public void onSurfaceTextureUpdated(final SurfaceTexture texture) {}
          };

  private HandlerThread backgroundThread;

  public LegacyCameraConnectionFragment(
          final Camera.PreviewCallback imageListener, final int layout, final Size desiredSize) {
    this.imageListener = imageListener;
    this.layout = layout;
    this.desiredSize = desiredSize;
  }

  @Override
  public View onCreateView(
          final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
    return inflater.inflate(layout, container, false);
  }

  @Override
  public void onViewCreated(final View view, final Bundle savedInstanceState) {
    textureView = view.findViewById(R.id.texture);
  }

  @Override
  public void onActivityCreated(final Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
  }

  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();

    if (textureView.isAvailable()) {
      if (camera != null) {
        camera.startPreview();
      }
    } else {
      textureView.setSurfaceTextureListener(surfaceTextureListener);
    }
  }

  @Override
  public void onPause() {
    stopCamera();
    stopBackgroundThread();
    super.onPause();
  }

  private void startBackgroundThread() {
    backgroundThread = new HandlerThread("CameraBackground");
    backgroundThread.start();
  }

  private void stopBackgroundThread() {
    if (backgroundThread != null) {
      backgroundThread.quitSafely();
      try {
        backgroundThread.join();
        backgroundThread = null;
      } catch (final InterruptedException e) {
        LOGGER.e("Exception occurred when stopping background thread.", e);
      }
    }
  }

  private void stopCamera() {
    if (camera != null) {
      camera.stopPreview();
      camera.setPreviewCallback(null);
      camera.release();
      camera = null;
    }
  }

  private int getCameraId() {
    Camera.CameraInfo ci = new Camera.CameraInfo();
    for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
      Camera.getCameraInfo(i, ci);
      if (ci.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i;
    }
    return -1; // No camera found
  }
}
