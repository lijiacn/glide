package com.bumptech.glide.load.engine.cache;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.VisibleForTesting;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.util.Log;
import com.bumptech.glide.util.Preconditions;
import com.bumptech.glide.util.Synthetic;

/**
 * A calculator that tries to intelligently determine cache sizes for a given device based on some
 * constants and the devices screen density, width, and height.
 * 内存计算器,会根据设备屏幕密度,宽度,高度来确定缓存的大小
 */
public final class MemorySizeCalculator {
  private static final String TAG = "MemorySizeCalculator";
  @VisibleForTesting
  static final int BYTES_PER_ARGB_8888_PIXEL = 4;
  private static final int LOW_MEMORY_BYTE_ARRAY_POOL_DIVISOR = 2;
  private final int bitmapPoolSize;
  private final int memoryCacheSize;
  private final Context context;
  private final int arrayPoolSize;

  interface ScreenDimensions {
    int getWidthPixels();
    int getHeightPixels();
  }

  // Package private to avoid PMD warning.
  MemorySizeCalculator(MemorySizeCalculator.Builder builder) {
    this.context = builder.context;
    //数组池的大小
    arrayPoolSize = isLowMemoryDevice(builder.activityManager)
            //如果是低内存设备,默认为4M/2=2M
            ? builder.arrayPoolSizeBytes / LOW_MEMORY_BYTE_ARRAY_POOL_DIVISOR
            //高内存设备默认为4M
            : builder.arrayPoolSizeBytes;

    //计算最大的可以使用内存
    int maxSize =
        getMaxSize(builder.activityManager, builder.maxSizeMultiplier, builder.lowMemoryMaxSizeMultiplier);

    int widthPixels = builder.screenDimensions.getWidthPixels();
    int heightPixels = builder.screenDimensions.getHeightPixels();
    //screenSize = 宽度*高度*4
    int screenSize = widthPixels * heightPixels * BYTES_PER_ARGB_8888_PIXEL;
    //bitmapPool大小 =屏幕尺寸*图片的大小(四舍五入),在8.0的系统上有特殊处理,小于8.0的系统默认为 4
    int targetBitmapPoolSize = Math.round(screenSize * builder.bitmapPoolScreens);
    //MemoryCache大小 = 屏幕尺寸*2 (四舍五入),默认为 2
    int targetMemoryCacheSize = Math.round(screenSize * builder.memoryCacheScreens);
    //可用大小 = 最大可用内存-arrayPoolSize的大小
    int availableSize = maxSize - arrayPoolSize;

    //计算内存大小和bitmapPool
    if (targetMemoryCacheSize + targetBitmapPoolSize <= availableSize) {
      memoryCacheSize = targetMemoryCacheSize;
      bitmapPoolSize = targetBitmapPoolSize;
    } else {
      float part = availableSize / (builder.bitmapPoolScreens + builder.memoryCacheScreens);
      memoryCacheSize = Math.round(part * builder.memoryCacheScreens);
      bitmapPoolSize = Math.round(part * builder.bitmapPoolScreens);
    }

    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(
          TAG,
          "Calculation complete"
              + ", Calculated memory cache size: "
              + toMb(memoryCacheSize)
              + ", pool size: "
              + toMb(bitmapPoolSize)
              + ", byte array size: "
              + toMb(arrayPoolSize)
              + ", memory class limited? "
              + (targetMemoryCacheSize + targetBitmapPoolSize > maxSize)
              + ", max size: "
              + toMb(maxSize)
              + ", memoryClass: "
              + builder.activityManager.getMemoryClass()
              + ", isLowMemoryDevice: "
              + isLowMemoryDevice(builder.activityManager));
    }
  }

  /**
   * Returns the recommended memory cache size for the device it is run on in bytes.
   */
  public int getMemoryCacheSize() {
    return memoryCacheSize;
  }

  /**
   * Returns the recommended bitmap pool size for the device it is run on in bytes.
   */
  public int getBitmapPoolSize() {
    return bitmapPoolSize;
  }

  /**
   * Returns the recommended array pool size for the device it is run on in bytes.
   */
  public int getArrayPoolSizeInBytes() {
    return arrayPoolSize;
  }

  private static int getMaxSize(ActivityManager activityManager, float maxSizeMultiplier,
      float lowMemoryMaxSizeMultiplier) {
    //每个进程的最大可用内存
    final int memoryClassBytes = activityManager.getMemoryClass() * 1024 * 1024;
    //判断是否是低内存设备,低版本系统(低于4.4)的手机默认是true
    final boolean isLowMemoryDevice = isLowMemoryDevice(activityManager);
    //如果是低内存设备则最多使用内存的33% 否则可以使用40%
    return Math.round(memoryClassBytes * (isLowMemoryDevice ? lowMemoryMaxSizeMultiplier
        : maxSizeMultiplier));
  }

  private String toMb(int bytes) {
    return Formatter.formatFileSize(context, bytes);
  }

  //是否是低内存设备
  @TargetApi(Build.VERSION_CODES.KITKAT)
  @Synthetic static boolean isLowMemoryDevice(ActivityManager activityManager) {
    // Explicitly check with an if statement, on some devices both parts of boolean expressions
    // can be evaluated even if we'd normally expect a short circuit.
    //noinspection SimplifiableIfStatement
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      return activityManager.isLowRamDevice();
    } else {
      return true;
    }
  }

  /**
   * Constructs an {@link MemorySizeCalculator} with reasonable defaults that can be optionally
   * overridden.
   */
  // Public API.
  @SuppressWarnings({"WeakerAccess", "unused"})
  public static final class Builder {
    @VisibleForTesting
    static final int MEMORY_CACHE_TARGET_SCREENS = 2;

    /**
     * On Android O+, we use {@link android.graphics.Bitmap.Config#HARDWARE} for all reasonably
     * sized images unless we're creating thumbnails for the first time. As a result, the Bitmap
     * pool is much less important on O than it was on previous versions.
     */
    static final int BITMAP_POOL_TARGET_SCREENS =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ? 4 : 1;

    static final float MAX_SIZE_MULTIPLIER = 0.4f;
    static final float LOW_MEMORY_MAX_SIZE_MULTIPLIER = 0.33f;
    // 4MB.
    static final int ARRAY_POOL_SIZE_BYTES = 4 * 1024 * 1024;

    @Synthetic final Context context;

    // Modifiable (non-final) for testing.
    @Synthetic ActivityManager activityManager;
    //屏幕分辨率类
    @Synthetic ScreenDimensions screenDimensions;

    @Synthetic float memoryCacheScreens = MEMORY_CACHE_TARGET_SCREENS;
    @Synthetic float bitmapPoolScreens = BITMAP_POOL_TARGET_SCREENS;
    @Synthetic float maxSizeMultiplier = MAX_SIZE_MULTIPLIER;
    @Synthetic float lowMemoryMaxSizeMultiplier = LOW_MEMORY_MAX_SIZE_MULTIPLIER;
    @Synthetic int arrayPoolSizeBytes = ARRAY_POOL_SIZE_BYTES;

    public Builder(Context context) {
      this.context = context;
      activityManager =
          (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
      screenDimensions =
          new DisplayMetricsScreenDimensions(context.getResources().getDisplayMetrics());

      // On Android O+ Bitmaps are allocated natively, ART is much more efficient at managing
      // garbage and we rely heavily on HARDWARE Bitmaps, making Bitmap re-use much less important.
      // We prefer to preserve RAM on these devices and take the small performance hit of not
      // re-using Bitmaps and textures when loading very small images or generating thumbnails.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isLowMemoryDevice(activityManager)) {
        bitmapPoolScreens = 0;
      }
    }

    /**
     * Sets the number of device screens worth of pixels the
     * {@link com.bumptech.glide.load.engine.cache.MemoryCache} should be able to hold and
     * returns this Builder.
     */
    public Builder setMemoryCacheScreens(float memoryCacheScreens) {
      Preconditions.checkArgument(memoryCacheScreens >= 0,
          "Memory cache screens must be greater than or equal to 0");
      this.memoryCacheScreens = memoryCacheScreens;
      return this;
    }

    /**
     * Sets the number of device screens worth of pixels the
     * {@link com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool} should be able to hold and
     * returns this Builder.
     */
    public Builder setBitmapPoolScreens(float bitmapPoolScreens) {
      Preconditions.checkArgument(bitmapPoolScreens >= 0,
          "Bitmap pool screens must be greater than or equal to 0");
      this.bitmapPoolScreens = bitmapPoolScreens;
      return this;
    }

    /**
     * Sets the maximum percentage of the device's memory class for standard devices that can be
     * taken up by Glide's {@link com.bumptech.glide.load.engine.cache.MemoryCache} and
     * {@link com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool} put together, and returns
     * this builder.
     */
    public Builder setMaxSizeMultiplier(float maxSizeMultiplier) {
      Preconditions.checkArgument(maxSizeMultiplier >= 0 && maxSizeMultiplier <= 1,
          "Size multiplier must be between 0 and 1");
      this.maxSizeMultiplier = maxSizeMultiplier;
      return this;
    }

    /**
     * Sets the maximum percentage of the device's memory class for low ram devices that can be
     * taken up by Glide's {@link com.bumptech.glide.load.engine.cache.MemoryCache} and
     * {@link com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool} put together, and returns
     * this builder.
     *
     * @see ActivityManager#isLowRamDevice()
     */
    public Builder setLowMemoryMaxSizeMultiplier(float lowMemoryMaxSizeMultiplier) {
      Preconditions.checkArgument(
          lowMemoryMaxSizeMultiplier >= 0 && lowMemoryMaxSizeMultiplier <= 1,
              "Low memory max size multiplier must be between 0 and 1");
      this.lowMemoryMaxSizeMultiplier = lowMemoryMaxSizeMultiplier;
      return this;
    }

    /**
     * Sets the size in bytes of the {@link
     * com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool} to use to store temporary
     * arrays while decoding data and returns this builder.
     *
     * <p>This number will be halved on low memory devices that return {@code true} from
     * {@link ActivityManager#isLowRamDevice()}.
     */
    public Builder setArrayPoolSize(int arrayPoolSizeBytes) {
      this.arrayPoolSizeBytes = arrayPoolSizeBytes;
      return this;
    }

    @VisibleForTesting
    Builder setActivityManager(ActivityManager activityManager) {
      this.activityManager = activityManager;
      return this;
    }

    @VisibleForTesting
    Builder setScreenDimensions(ScreenDimensions screenDimensions) {
      this.screenDimensions = screenDimensions;
      return this;
    }

    public MemorySizeCalculator build() {
      return new MemorySizeCalculator(this);
    }
  }

  /**
   * 屏幕分辨率管理类
   */
  private static final class DisplayMetricsScreenDimensions implements ScreenDimensions {
    private final DisplayMetrics displayMetrics;

    DisplayMetricsScreenDimensions(DisplayMetrics displayMetrics) {
      this.displayMetrics = displayMetrics;
    }

    @Override
    public int getWidthPixels() {
      return displayMetrics.widthPixels;
    }

    @Override
    public int getHeightPixels() {
      return displayMetrics.heightPixels;
    }
  }
}
