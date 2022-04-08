package com.zh.android.glide.progress;

/**
 * 进度监听
 */
public interface ProgressListener {
    /**
     * @param progress 进度百分比
     */
    void onProgress(int progress);
}