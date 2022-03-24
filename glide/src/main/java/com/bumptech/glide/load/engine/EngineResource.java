package com.bumptech.glide.load.engine;

import android.os.Looper;
import android.support.annotation.NonNull;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.util.Preconditions;

/**
 * A wrapper resource that allows reference counting a wrapped {@link
 * com.bumptech.glide.load.engine.Resource} interface.
 *
 * @param <Z> The type of data returned by the wrapped {@link Resource}.
 *
 * 使用的是引用计数算法，重点关注 acquire() 和 release()，acquire()会让引用计数 + 1，而release()会让引用计数 - 1
 * 当计数为0时，通知监听器要进行回收
 */
class EngineResource<Z> implements Resource<Z> {
  private final boolean isCacheable;
  private final boolean isRecyclable;
  private ResourceListener listener;
  private Key key;
  /**
   * 引用计数
   */
  private int acquired;
  private boolean isRecycled;
  private final Resource<Z> resource;

  interface ResourceListener {
    void onResourceReleased(Key key, EngineResource<?> resource);
  }

  EngineResource(Resource<Z> toWrap, boolean isCacheable, boolean isRecyclable) {
    resource = Preconditions.checkNotNull(toWrap);
    this.isCacheable = isCacheable;
    this.isRecyclable = isRecyclable;
  }

  void setResourceListener(Key key, ResourceListener listener) {
    this.key = key;
    this.listener = listener;
  }

  Resource<Z> getResource() {
    return resource;
  }

  boolean isCacheable() {
    return isCacheable;
  }

  @NonNull
  @Override
  public Class<Z> getResourceClass() {
    return resource.getResourceClass();
  }

  @NonNull
  @Override
  public Z get() {
    return resource.get();
  }

  @Override
  public int getSize() {
    return resource.getSize();
  }

  @Override
  public void recycle() {
    if (acquired > 0) {
      throw new IllegalStateException("Cannot recycle a resource while it is still acquired");
    }
    if (isRecycled) {
      throw new IllegalStateException("Cannot recycle a resource that has already been recycled");
    }
    isRecycled = true;
    if (isRecyclable) {
      resource.recycle();
    }
  }

  /**
   * Increments the number of consumers using the wrapped resource. Must be called on the main
   * thread.
   *
   * <p> This must be called with a number corresponding to the number of new consumers each time
   * new consumers begin using the wrapped resource. It is always safer to call acquire more often
   * than necessary. Generally external users should never call this method, the framework will take
   * care of this for you. </p>
   *
   * 引用计数 + 1
   */
  void acquire() {
    if (isRecycled) {
      throw new IllegalStateException("Cannot acquire a recycled resource");
    }
    if (!Looper.getMainLooper().equals(Looper.myLooper())) {
      throw new IllegalThreadStateException("Must call acquire on the main thread");
    }
    ++acquired;
  }

  /**
   * Decrements the number of consumers using the wrapped resource. Must be called on the main
   * thread.
   *
   * <p>This must only be called when a consumer that called the {@link #acquire()} method is now
   * done with the resource. Generally external users should never call this method, the framework
   * will take care of this for you.
   *
   * 引用计数 - 1
   */
  void release() {
    if (acquired <= 0) {
      throw new IllegalStateException("Cannot release a recycled or not yet acquired resource");
    }
    if (!Looper.getMainLooper().equals(Looper.myLooper())) {
      throw new IllegalThreadStateException("Must call release on the main thread");
    }
    //当引用计数为0了，则回调监听器，就是要被回收了
    if (--acquired == 0) {
      listener.onResourceReleased(key, this);
    }
  }

  @Override
  public String toString() {
    return "EngineResource{"
        + "isCacheable=" + isCacheable
        + ", listener=" + listener
        + ", key=" + key
        + ", acquired=" + acquired
        + ", isRecycled=" + isRecycled
        + ", resource=" + resource
        + '}';
  }
}
