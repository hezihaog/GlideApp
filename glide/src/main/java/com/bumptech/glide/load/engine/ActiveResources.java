package com.bumptech.glide.load.engine;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.engine.EngineResource.ResourceListener;
import com.bumptech.glide.util.Preconditions;
import com.bumptech.glide.util.Synthetic;
import com.bumptech.glide.util.Util;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 活动缓存，也是弱引用缓存，是Glide一级缓存
 * 使用弱引用，保证了当进行内存回收时能及时回收掉，避免一直占用内存。如果被回收掉就会转移到memory cache中
 */
final class ActiveResources {
  private static final int MSG_CLEAN_REF = 1;

  private final boolean isActiveResourceRetentionAllowed;
  private final Handler mainHandler = new Handler(Looper.getMainLooper(), new Callback() {
    @Override
    public boolean handleMessage(Message msg) {
      if (msg.what == MSG_CLEAN_REF) {
        //主线程中，把弱引用从活动缓存中移除
        cleanupActiveReference((ResourceWeakReference) msg.obj);
        return true;
      }
      return false;
    }
  });

  /**
   * 活动缓存
   */
  @VisibleForTesting
  final Map<Key, ResourceWeakReference> activeEngineResources = new HashMap<>();

  private ResourceListener listener;

  /**
   * Lazily instantiate to avoid exceptions if Glide is initialized on a background thread.
   *
   * @see <a href="https://github.com/bumptech/glide/issues/295">#295</a>
   */
  @Nullable
  private ReferenceQueue<EngineResource<?>> resourceReferenceQueue;
  @Nullable
  private Thread cleanReferenceQueueThread;
  private volatile boolean isShutdown;
  @Nullable
  private volatile DequeuedResourceCallback cb;

  ActiveResources(boolean isActiveResourceRetentionAllowed) {
    this.isActiveResourceRetentionAllowed = isActiveResourceRetentionAllowed;
  }

  void setListener(ResourceListener listener) {
    this.listener = listener;
  }

  void activate(Key key, EngineResource<?> resource) {
    ResourceWeakReference toPut =
        new ResourceWeakReference(
            key,
            resource,
            getReferenceQueue(),
            isActiveResourceRetentionAllowed);

    //如果有相同的key的资源，那么取出并重置它
    ResourceWeakReference removed = activeEngineResources.put(key, toPut);
    if (removed != null) {
      removed.reset();
    }
  }

  /**
   * 从活动缓存中移除指定Key的缓存
   */
  void deactivate(Key key) {
    //从队列中移除
    ResourceWeakReference removed = activeEngineResources.remove(key);
    //重置它
    if (removed != null) {
      removed.reset();
    }
  }

  @Nullable
  EngineResource<?> get(Key key) {
    //从活动缓存中取出
    ResourceWeakReference activeRef = activeEngineResources.get(key);
    //没有找到缓存
    if (activeRef == null) {
      return null;
    }

    EngineResource<?> active = activeRef.get();
    //找到了缓存，但是弱引用被GC，拿不到保存的资源
    if (active == null) {
      cleanupActiveReference(activeRef);
    }
    return active;
  }

  /**
   * 移除弱引用缓存，有2个调用，一个是cleanReferenceQueue()，一个是get()
   */
  @SuppressWarnings("WeakerAccess")
  @Synthetic void cleanupActiveReference(@NonNull ResourceWeakReference ref) {
    Util.assertMainThread();
    //从活动缓存中移除
    activeEngineResources.remove(ref.key);
    //cleanReferenceQueue()，是被GC的情况，会走进这个if，并return
    if (!ref.isCacheable || ref.resource == null) {
      return;
    }
    //如果是get()，会走到这里，并把资源重新放进弱引用
    EngineResource<?> newResource =
        new EngineResource<>(ref.resource, /*isCacheable=*/ true, /*isRecyclable=*/ false);
    //设置引用计数为0时的监听
    newResource.setResourceListener(ref.key, listener);
    //onResourceReleased()，会导致资源从active缓存移动到memory缓存
    listener.onResourceReleased(ref.key, newResource);
  }

  /**
   * 弱引用队列，当有弱引用被GC时，会放进队列中
   * 同时开启一个后台线程，不断从弱引用队列中取，如果能取到就是被GC了，需要把它从活动缓存中移除
   */
  private ReferenceQueue<EngineResource<?>> getReferenceQueue() {
    if (resourceReferenceQueue == null) {
      resourceReferenceQueue = new ReferenceQueue<>();
      //一个后台优先级级别的线程
      cleanReferenceQueueThread = new Thread(new Runnable() {
        @SuppressWarnings("InfiniteLoopStatement")
        @Override
        public void run() {
          //设置为后台优先级级别
          Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
          //不断while循环取弱引用队列的元素
          cleanReferenceQueue();
        }
      }, "glide-active-resources");
      cleanReferenceQueueThread.start();
    }
    return resourceReferenceQueue;
  }

  /**
   * 清理弱引用队列
   */
  @SuppressWarnings("WeakerAccess")
  @Synthetic void cleanReferenceQueue() {
    while (!isShutdown) {
      try {
        //取出弱引用队列的队头元素，并移除，用Handler发送到主线程中移除它
        ResourceWeakReference ref = (ResourceWeakReference) resourceReferenceQueue.remove();
        mainHandler.obtainMessage(MSG_CLEAN_REF, ref).sendToTarget();

        // This section for testing only.
        DequeuedResourceCallback current = cb;
        if (current != null) {
          current.onResourceDequeued();
        }
        // End for testing only.
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @VisibleForTesting
  void setDequeuedResourceCallback(DequeuedResourceCallback cb) {
    this.cb = cb;
  }

  @VisibleForTesting
  interface DequeuedResourceCallback {
    void onResourceDequeued();
  }

  @VisibleForTesting
  void shutdown() {
    isShutdown = true;
    if (cleanReferenceQueueThread == null) {
      return;
    }

    cleanReferenceQueueThread.interrupt();
    try {
      cleanReferenceQueueThread.join(TimeUnit.SECONDS.toMillis(5));
      if (cleanReferenceQueueThread.isAlive()) {
        throw new RuntimeException("Failed to join in time");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * 资源的弱引用
   */
  @VisibleForTesting
  static final class ResourceWeakReference extends WeakReference<EngineResource<?>> {
    @SuppressWarnings("WeakerAccess") @Synthetic final Key key;
    @SuppressWarnings("WeakerAccess") @Synthetic final boolean isCacheable;

    @Nullable @SuppressWarnings("WeakerAccess") @Synthetic Resource<?> resource;

    @Synthetic
    @SuppressWarnings("WeakerAccess")
    ResourceWeakReference(
        @NonNull Key key,
        @NonNull EngineResource<?> referent,
        @NonNull ReferenceQueue<? super EngineResource<?>> queue,
        boolean isActiveResourceRetentionAllowed) {
      //当弱引用被GC，会把它放进这个引用队列queue
      super(referent, queue);
      this.key = Preconditions.checkNotNull(key);
      this.resource =
          referent.isCacheable() && isActiveResourceRetentionAllowed
              ? Preconditions.checkNotNull(referent.getResource()) : null;
      isCacheable = referent.isCacheable();
    }

    /**
     * 重置引用
     */
    void reset() {
      resource = null;
      clear();
    }
  }
}
