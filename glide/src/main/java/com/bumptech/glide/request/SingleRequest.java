package com.bumptech.glide.request;

import android.content.Context;
import android.content.res.Resources.Theme;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pools;
import android.util.Log;
import com.bumptech.glide.GlideContext;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.Engine;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.drawable.DrawableDecoderCompat;
import com.bumptech.glide.request.target.SizeReadyCallback;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.request.transition.TransitionFactory;
import com.bumptech.glide.util.LogTime;
import com.bumptech.glide.util.Synthetic;
import com.bumptech.glide.util.Util;
import com.bumptech.glide.util.pool.FactoryPools;
import com.bumptech.glide.util.pool.StateVerifier;
import java.util.List;

/**
 * A {@link Request} that loads a {@link com.bumptech.glide.load.engine.Resource} into a given
 * {@link Target}.
 *
 * @param <R> The type of the resource that will be transcoded from the loaded resource.
 */
public final class SingleRequest<R> implements Request,
    SizeReadyCallback,
    ResourceCallback,
    FactoryPools.Poolable {
  /** Tag for logging internal events, not generally suitable for public use. */
  private static final String TAG = "Request";
  /** Tag for logging externally useful events (request completion, timing etc). */
  private static final String GLIDE_TAG = "Glide";
  private static final Pools.Pool<SingleRequest<?>> POOL = FactoryPools.simple(150,
      new FactoryPools.Factory<SingleRequest<?>>() {
        @Override
        public SingleRequest<?> create() {
          return new SingleRequest<Object>();
        }
      });
  private boolean isCallingCallbacks;

  private static final boolean IS_VERBOSE_LOGGABLE =
      Log.isLoggable(TAG, Log.VERBOSE);

  private enum Status {
    /**
     * Created but not yet running.
     */
    PENDING,
    /**
     * In the process of fetching media.
     */
    RUNNING,
    /**
     * Waiting for a callback given to the Target to be called to determine target dimensions.
     */
    WAITING_FOR_SIZE,
    /**
     * Finished loading media successfully.
     */
    COMPLETE,
    /**
     * Failed to load media, may be restarted.
     */
    FAILED,
    /**
     * Cleared by the user with a placeholder set, may be restarted.
     */
    CLEARED,
  }

  @Nullable
  private final String tag = IS_VERBOSE_LOGGABLE ? String.valueOf(super.hashCode()) : null;
  private final StateVerifier stateVerifier = StateVerifier.newInstance();

  @Nullable
  private RequestListener<R> targetListener;
  private RequestCoordinator requestCoordinator;
  private Context context;
  private GlideContext glideContext;
  @Nullable
  private Object model;
  private Class<R> transcodeClass;
  private RequestOptions requestOptions;
  private int overrideWidth;
  private int overrideHeight;
  private Priority priority;
  private Target<R> target;
  @Nullable private List<RequestListener<R>> requestListeners;
  private Engine engine;
  private TransitionFactory<? super R> animationFactory;
  private Resource<R> resource;
  private Engine.LoadStatus loadStatus;
  private long startTime;
  private Status status;
  private Drawable errorDrawable;
  private Drawable placeholderDrawable;
  private Drawable fallbackDrawable;
  private int width;
  private int height;

  public static <R> SingleRequest<R> obtain(
      Context context,
      GlideContext glideContext,
      Object model,
      Class<R> transcodeClass,
      RequestOptions requestOptions,
      int overrideWidth,
      int overrideHeight,
      Priority priority,
      Target<R> target,
      RequestListener<R> targetListener,
      @Nullable List<RequestListener<R>> requestListeners,
      RequestCoordinator requestCoordinator,
      Engine engine,
      TransitionFactory<? super R> animationFactory) {
    @SuppressWarnings("unchecked") SingleRequest<R> request =
        (SingleRequest<R>) POOL.acquire();
    if (request == null) {
      request = new SingleRequest<>();
    }
    request.init(
        context,
        glideContext,
        model,
        transcodeClass,
        requestOptions,
        overrideWidth,
        overrideHeight,
        priority,
        target,
        targetListener,
        requestListeners,
        requestCoordinator,
        engine,
        animationFactory);
    return request;
  }

  @SuppressWarnings("WeakerAccess")
  @Synthetic
  SingleRequest() {
    // just create, instances are reused with recycle/init
  }

  private void init(
      Context context,
      GlideContext glideContext,
      Object model,
      Class<R> transcodeClass,
      RequestOptions requestOptions,
      int overrideWidth,
      int overrideHeight,
      Priority priority,
      Target<R> target,
      RequestListener<R> targetListener,
      @Nullable List<RequestListener<R>> requestListeners,
      RequestCoordinator requestCoordinator,
      Engine engine,
      TransitionFactory<? super R> animationFactory) {
    this.context = context;
    this.glideContext = glideContext;
    this.model = model;
    this.transcodeClass = transcodeClass;
    this.requestOptions = requestOptions;
    this.overrideWidth = overrideWidth;
    this.overrideHeight = overrideHeight;
    this.priority = priority;
    this.target = target;
    this.targetListener = targetListener;
    this.requestListeners = requestListeners;
    this.requestCoordinator = requestCoordinator;
    this.engine = engine;
    this.animationFactory = animationFactory;
    status = Status.PENDING;
  }

  @NonNull
  @Override
  public StateVerifier getVerifier() {
    return stateVerifier;
  }

  @Override
  public void recycle() {
    assertNotCallingCallbacks();
    context = null;
    glideContext = null;
    model = null;
    transcodeClass = null;
    requestOptions = null;
    overrideWidth = -1;
    overrideHeight = -1;
    target = null;
    requestListeners = null;
    targetListener = null;
    requestCoordinator = null;
    animationFactory = null;
    loadStatus = null;
    errorDrawable = null;
    placeholderDrawable = null;
    fallbackDrawable = null;
    width = -1;
    height = -1;
    POOL.release(this);
  }

  @Override
  public void begin() {
    assertNotCallingCallbacks();
    stateVerifier.throwIfRecycled();
    startTime = LogTime.getLogTime();
    //如果model，就是传入的url等资源，为空，则回调加载失败
    if (model == null) {
      if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
        width = overrideWidth;
        height = overrideHeight;
      }
      // Only log at more verbose log levels if the user has set a fallback drawable, because
      // fallback Drawables indicate the user expects null models occasionally.
      int logLevel = getFallbackDrawable() == null ? Log.WARN : Log.DEBUG;
      onLoadFailed(new GlideException("Received null model"), logLevel);
      return;
    }

    //已经是运行中，不允许再用这个对象发起请求
    if (status == Status.RUNNING) {
      throw new IllegalArgumentException("Cannot restart a running request");
    }

    // If we're restarted after we're complete (usually via something like a notifyDataSetChanged
    // that starts an identical request into the same Target or View), we can simply use the
    // resource and size we retrieved the last time around and skip obtaining a new size, starting a
    // new load etc. This does mean that users who want to restart a load because they expect that
    // the view size has changed will need to explicitly clear the View or Target before starting
    // the new load.
    if (status == Status.COMPLETE) {
      //如果我们请求完后，想重新开始加载，那么会直接返回已经加载好的资源
      onResourceReady(resource, DataSource.MEMORY_CACHE);
      return;
    }

    // Restarts for requests that are neither complete nor running can be treated as new requests
    // and can run again from the beginning.

    //Glide对根据ImageView的宽高来进行缓存，所以需要获取ImageView的宽高，overrideWidth和overrideHeight默认为-1
    status = Status.WAITING_FOR_SIZE;
    //第一次请求，是还没有获取宽高的，所以会走else逻辑去获取宽高
    if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
      onSizeReady(overrideWidth, overrideHeight);
    } else {
      //获取ImageView的宽高，target是前面buildImageViewTarget的时候，生成的DrawableImageViewTarget
      //getSize()方法在它的父类ViewTarget中，获取成功会回调当前SingleRequest的onSizeReady()方法
      target.getSize(this);
    }

    //回调加载中，显示预占位图
    if ((status == Status.RUNNING || status == Status.WAITING_FOR_SIZE)
        && canNotifyStatusChanged()) {
      target.onLoadStarted(getPlaceholderDrawable());
    }
    if (IS_VERBOSE_LOGGABLE) {
      logV("finished run method in " + LogTime.getElapsedMillis(startTime));
    }
  }

  /**
   * Cancels the current load but does not release any resources held by the request and continues
   * to display the loaded resource if the load completed before the call to cancel.
   *
   * <p> Cancelled requests can be restarted with a subsequent call to {@link #begin()}. </p>
   *
   * @see #clear()
   */
  private void cancel() {
    assertNotCallingCallbacks();
    stateVerifier.throwIfRecycled();
    target.removeCallback(this);
    if (loadStatus != null) {
      loadStatus.cancel();
      loadStatus = null;
    }
  }

  // Avoids difficult to understand errors like #2413.
  private void assertNotCallingCallbacks() {
    if (isCallingCallbacks) {
      throw new IllegalStateException("You can't start or clear loads in RequestListener or"
          + " Target callbacks. If you're trying to start a fallback request when a load fails, use"
          + " RequestBuilder#error(RequestBuilder). Otherwise consider posting your into() or"
          + " clear() calls to the main thread using a Handler instead.");
    }
  }

  /**
   * Cancels the current load if it is in progress, clears any resources held onto by the request
   * and replaces the loaded resource if the load completed with the placeholder.
   *
   * <p> Cleared requests can be restarted with a subsequent call to {@link #begin()} </p>
   *
   * @see #cancel()
   */
  @Override
  public void clear() {
    Util.assertMainThread();
    assertNotCallingCallbacks();
    stateVerifier.throwIfRecycled();
    if (status == Status.CLEARED) {
      return;
    }
    cancel();
    // Resource must be released before canNotifyStatusChanged is called.
    if (resource != null) {
      releaseResource(resource);
    }
    if (canNotifyCleared()) {
      target.onLoadCleared(getPlaceholderDrawable());
    }

    status = Status.CLEARED;
  }

  private void releaseResource(Resource<?> resource) {
    engine.release(resource);
    this.resource = null;
  }

  @Override
  public boolean isRunning() {
    return status == Status.RUNNING || status == Status.WAITING_FOR_SIZE;
  }

  @Override
  public boolean isComplete() {
    return status == Status.COMPLETE;
  }

  @Override
  public boolean isResourceSet() {
    return isComplete();
  }

  @Override
  public boolean isCleared() {
    return status == Status.CLEARED;
  }

  @Override
  public boolean isFailed() {
    return status == Status.FAILED;
  }

  private Drawable getErrorDrawable() {
    if (errorDrawable == null) {
      errorDrawable = requestOptions.getErrorPlaceholder();
      if (errorDrawable == null && requestOptions.getErrorId() > 0) {
        errorDrawable = loadDrawable(requestOptions.getErrorId());
      }
    }
    return errorDrawable;
  }

  private Drawable getPlaceholderDrawable() {
     if (placeholderDrawable == null) {
      placeholderDrawable = requestOptions.getPlaceholderDrawable();
      if (placeholderDrawable == null && requestOptions.getPlaceholderId() > 0) {
        placeholderDrawable = loadDrawable(requestOptions.getPlaceholderId());
      }
    }
    return placeholderDrawable;
  }

  private Drawable getFallbackDrawable() {
    if (fallbackDrawable == null) {
      fallbackDrawable = requestOptions.getFallbackDrawable();
      if (fallbackDrawable == null && requestOptions.getFallbackId() > 0) {
        fallbackDrawable = loadDrawable(requestOptions.getFallbackId());
      }
    }
    return fallbackDrawable;
  }

  private Drawable loadDrawable(@DrawableRes int resourceId) {
    Theme theme = requestOptions.getTheme() != null
        ? requestOptions.getTheme() : context.getTheme();
    return DrawableDecoderCompat.getDrawable(glideContext, resourceId, theme);
  }

  private void setErrorPlaceholder() {
    if (!canNotifyStatusChanged()) {
      return;
    }

    Drawable error = null;
    if (model == null) {
      error = getFallbackDrawable();
    }
    // Either the model isn't null, or there was no fallback drawable set.
    if (error == null) {
      error = getErrorDrawable();
    }
    // The model isn't null, no fallback drawable was set or no error drawable was set.
    if (error == null) {
      error = getPlaceholderDrawable();
    }
    target.onLoadFailed(error);
  }

  /**
   * A callback method that should never be invoked directly.
   * 获取ImageView的宽高成功
   */
  @Override
  public void onSizeReady(int width, int height) {
    stateVerifier.throwIfRecycled();
    if (IS_VERBOSE_LOGGABLE) {
      logV("Got onSizeReady in " + LogTime.getElapsedMillis(startTime));
    }
    if (status != Status.WAITING_FOR_SIZE) {
      return;
    }
    status = Status.RUNNING;

    float sizeMultiplier = requestOptions.getSizeMultiplier();
    this.width = maybeApplySizeMultiplier(width, sizeMultiplier);
    this.height = maybeApplySizeMultiplier(height, sizeMultiplier);

    if (IS_VERBOSE_LOGGABLE) {
      logV("finished setup for calling load in " + LogTime.getElapsedMillis(startTime));
    }

    //通知引擎，加载图片
    loadStatus = engine.load(
        glideContext,
        model,
        requestOptions.getSignature(),
        this.width,
        this.height,
        requestOptions.getResourceClass(),
        transcodeClass,
        priority,
        requestOptions.getDiskCacheStrategy(),
        requestOptions.getTransformations(),
        requestOptions.isTransformationRequired(),
        requestOptions.isScaleOnlyOrNoTransform(),
        requestOptions.getOptions(),
        requestOptions.isMemoryCacheable(),
        requestOptions.getUseUnlimitedSourceGeneratorsPool(),
        requestOptions.getUseAnimationPool(),
        requestOptions.getOnlyRetrieveFromCache(),
        this);

    // This is a hack that's only useful for testing right now where loads complete synchronously
    // even though under any executor running on any thread but the main thread, the load would
    // have completed asynchronously.
    if (status != Status.RUNNING) {
      loadStatus = null;
    }
    if (IS_VERBOSE_LOGGABLE) {
      logV("finished onSizeReady in " + LogTime.getElapsedMillis(startTime));
    }
  }

  private static int maybeApplySizeMultiplier(int size, float sizeMultiplier) {
    return size == Target.SIZE_ORIGINAL ? size : Math.round(sizeMultiplier * size);
  }

  private boolean canSetResource() {
    return requestCoordinator == null || requestCoordinator.canSetImage(this);
  }

  private boolean canNotifyCleared() {
    return requestCoordinator == null || requestCoordinator.canNotifyCleared(this);
  }

  private boolean canNotifyStatusChanged() {
    return requestCoordinator == null || requestCoordinator.canNotifyStatusChanged(this);
  }

  private boolean isFirstReadyResource() {
    return requestCoordinator == null || !requestCoordinator.isAnyResourceSet();
  }

  private void notifyLoadSuccess() {
    if (requestCoordinator != null) {
      requestCoordinator.onRequestSuccess(this);
    }
  }

  private void notifyLoadFailed() {
    if (requestCoordinator != null) {
      requestCoordinator.onRequestFailed(this);
    }
  }

  /**
   * A callback method that should never be invoked directly.
   */
  @SuppressWarnings("unchecked")
  @Override
  public void onResourceReady(Resource<?> resource, DataSource dataSource) {
    stateVerifier.throwIfRecycled();
    loadStatus = null;
    if (resource == null) {
      GlideException exception = new GlideException("Expected to receive a Resource<R> with an "
          + "object of " + transcodeClass + " inside, but instead got null.");
      onLoadFailed(exception);
      return;
    }

    Object received = resource.get();
    if (received == null || !transcodeClass.isAssignableFrom(received.getClass())) {
      releaseResource(resource);
      GlideException exception = new GlideException("Expected to receive an object of "
          + transcodeClass + " but instead" + " got "
          + (received != null ? received.getClass() : "") + "{" + received + "} inside" + " "
          + "Resource{" + resource + "}."
          + (received != null ? "" : " " + "To indicate failure return a null Resource "
          + "object, rather than a Resource object containing null data."));
      onLoadFailed(exception);
      return;
    }

    if (!canSetResource()) {
      releaseResource(resource);
      // We can't put the status to complete before asking canSetResource().
      status = Status.COMPLETE;
      return;
    }

    //继续通知资源准备好了
    onResourceReady((Resource<R>) resource, (R) received, dataSource);
  }

  /**
   * Internal {@link #onResourceReady(Resource, DataSource)} where arguments are known to be safe.
   *
   * @param resource original {@link Resource}, never <code>null</code>
   * @param result   object returned by {@link Resource#get()}, checked for type and never
   *                 <code>null</code>
   */
  private void onResourceReady(Resource<R> resource, R result, DataSource dataSource) {
    // We must call isFirstReadyResource before setting status.
    boolean isFirstResource = isFirstReadyResource();
    status = Status.COMPLETE;
    this.resource = resource;

    if (glideContext.getLogLevel() <= Log.DEBUG) {
      Log.d(GLIDE_TAG, "Finished loading " + result.getClass().getSimpleName() + " from "
          + dataSource + " for " + model + " with size [" + width + "x" + height + "] in "
          + LogTime.getElapsedMillis(startTime) + " ms");
    }

    isCallingCallbacks = true;
    try {
      boolean anyListenerHandledUpdatingTarget = false;
      if (requestListeners != null) {
        for (RequestListener<R> listener : requestListeners) {
          anyListenerHandledUpdatingTarget |=
              listener.onResourceReady(result, model, target, dataSource, isFirstResource);
        }
      }
      anyListenerHandledUpdatingTarget |=
          targetListener != null
              && targetListener.onResourceReady(result, model, target, dataSource, isFirstResource);

      if (!anyListenerHandledUpdatingTarget) {
        Transition<? super R> animation =
            animationFactory.build(dataSource, isFirstResource);
        //通知ImageViewTarget，资源加载完成
        target.onResourceReady(result, animation);
      }
    } finally {
      isCallingCallbacks = false;
    }

    notifyLoadSuccess();
  }

  /**
   * A callback method that should never be invoked directly.
   */
  @Override
  public void onLoadFailed(GlideException e) {
    onLoadFailed(e, Log.WARN);
  }

  private void onLoadFailed(GlideException e, int maxLogLevel) {
    stateVerifier.throwIfRecycled();
    int logLevel = glideContext.getLogLevel();
    if (logLevel <= maxLogLevel) {
      Log.w(GLIDE_TAG, "Load failed for " + model + " with size [" + width + "x" + height + "]", e);
      if (logLevel <= Log.INFO) {
        e.logRootCauses(GLIDE_TAG);
      }
    }

    loadStatus = null;
    status = Status.FAILED;

    isCallingCallbacks = true;
    try {
      //TODO: what if this is a thumbnail request?
      boolean anyListenerHandledUpdatingTarget = false;
      if (requestListeners != null) {
        for (RequestListener<R> listener : requestListeners) {
          anyListenerHandledUpdatingTarget |=
              listener.onLoadFailed(e, model, target, isFirstReadyResource());
        }
      }
      anyListenerHandledUpdatingTarget |=
          targetListener != null
              && targetListener.onLoadFailed(e, model, target, isFirstReadyResource());

      if (!anyListenerHandledUpdatingTarget) {
        setErrorPlaceholder();
      }
    } finally {
      isCallingCallbacks = false;
    }

    notifyLoadFailed();
  }

  @Override
  public boolean isEquivalentTo(Request o) {
    if (o instanceof SingleRequest) {
      SingleRequest<?> that = (SingleRequest<?>) o;
      return overrideWidth == that.overrideWidth
          && overrideHeight == that.overrideHeight
          && Util.bothModelsNullEquivalentOrEquals(model, that.model)
          && transcodeClass.equals(that.transcodeClass)
          && requestOptions.equals(that.requestOptions)
          && priority == that.priority
          // We do not want to require that RequestListeners implement equals/hashcode, so we don't
          // compare them using equals(). We can however, at least assert that the same amount of
          // request listeners are present in both requests
          && listenerCountEquals(this, that);
    }
    return false;
  }

  private static boolean listenerCountEquals(SingleRequest<?> first, SingleRequest<?> second) {
    int firstListenerCount = first.requestListeners == null ? 0 : first.requestListeners.size();
    int secondListenerCount = second.requestListeners == null ? 0 : second.requestListeners.size();
    return firstListenerCount == secondListenerCount;
  }

  private void logV(String message) {
    Log.v(TAG, message + " this: " + tag);
  }
}
