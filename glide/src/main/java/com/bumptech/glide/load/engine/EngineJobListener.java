package com.bumptech.glide.load.engine;

import com.bumptech.glide.load.Key;

interface EngineJobListener {
  /**
   * 通知Engine层，任务已完成
   */
  void onEngineJobComplete(EngineJob<?> engineJob, Key key, EngineResource<?> resource);

  /**
   * 通知Engine层，任务已取消
   */
  void onEngineJobCancelled(EngineJob<?> engineJob, Key key);
}
