package com.bumptech.glide.load.engine;

import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.Transformation;

import java.util.Map;

/**
 * EngineKey，缓存Key工厂
 */
class EngineKeyFactory {
    /**
     * 生产缓存Key
     *
     * @param model           load()方法传入的资源参数
     * @param signature       BaseRequestOptions的成员变量，默认会是EmptySignature.obtain() 在加载本地resource资源时会变成ApplicationVersionSignature.obtain(context)
     * @param width           ImageView的宽，如果没有指定override(int size)，那么将得到view的size
     * @param height          ImageView的高，如果没有指定override(int size)，那么将得到view的size
     * @param transformations 默认会基于ImageView的scaleType设置对应的四个Transformation； 如果指定了transform，那么就基于该值进行设置； 详见BaseRequestOptions.transform(Transformation, boolean)
     * @param resourceClass   解码后的资源，如果没有asBitmap、asGif，一般会是Object
     * @param transcodeClass  最终要转换成的数据类型，根据as方法确定，加载本地res或者网络URL，都会调用asDrawable，所以为Drawable
     * @param options         如果没有设置过transform，此处会根据ImageView的scaleType默认指定一个KV
     * @return 缓存Key
     */
    @SuppressWarnings("rawtypes")
    EngineKey buildKey(Object model, Key signature, int width, int height,
                       Map<Class<?>, Transformation<?>> transformations, Class<?> resourceClass,
                       Class<?> transcodeClass, Options options) {
        return new EngineKey(model, signature, width, height, transformations, resourceClass,
                transcodeClass, options);
    }
}