package com.bumptech.glideapp;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.zh.android.glide.progress.ProgressInterceptor;
import com.zh.android.glide.progress.ProgressListener;
import com.zh.android.glide.view.CircleProgressView;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        showBrokenGif();
        showNormalImage();
        showSmallImage();
        showResImage();
        showProgressImager();
    }

    private void showResImage() {
        ImageView resImage = findViewById(R.id.res_image);
        Glide.with(this).load(R.mipmap.bird).into(resImage);
    }

    private void showProgressImager() {
        Button loadImgBtn = findViewById(R.id.load_image_btn);
        final ImageView imageView = findViewById(R.id.progress_image);
        final CircleProgressView imageProgressView = findViewById(R.id.image_progress);
        final TextView imageProgressText = findViewById(R.id.progress_text);
        final String imgUrl = "https://www.xiwangchina.com/Uploads/Picture/2018/10/24/s5bcfdc83b2ff9.jpg";
        loadImgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GlideApp.with(imageView)
                        .asBitmap()
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                        .addListener(new RequestListener<Bitmap>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                                ProgressInterceptor.removeListener(model);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                                ProgressInterceptor.removeListener(model);
                                return false;
                            }
                        })
                        .load(imgUrl)
                        .into(new BitmapImageViewTarget(imageView) {
                            @Override
                            public void onLoadStarted(@Nullable Drawable placeholder) {
                                super.onLoadStarted(placeholder);
                                imageProgressView.setVisibility(View.VISIBLE);
                                imageProgressText.setVisibility(View.VISIBLE);
                                imageProgressView.setProgress(0f);
                                ProgressInterceptor.addListener(imgUrl, new ProgressListener() {
                                    @SuppressLint("SetTextI18n")
                                    @Override
                                    public void onProgress(final int progress) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                imageProgressView.setProgress(progress);
                                                imageProgressText.setText(progress + "%");
                                            }
                                        });
                                    }
                                });
                            }

                            @Override
                            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                super.onLoadFailed(errorDrawable);
                                imageProgressView.setVisibility(View.GONE);
                                imageProgressText.setVisibility(View.GONE);
                            }
                        });
            }
        });
    }

    private void showSmallImage() {
        final ImageView imageView = findViewById(R.id.small_image);
        String imgUrl = "https://gimg2.baidu.com/image_search/src=http%3A%2F%2Fimg1.doubanio.com%2Fview%2Fnote%2Fl%2Fpublic%2Fp82460877.jpg&refer=http%3A%2F%2Fimg1.doubanio.com&app=2002&size=f9999,10000&q=a80&n=0&g=0n&fmt=auto?sec=1651994649&t=e5ab548edb343984d05868bf34cdcb5f";
        Glide.with(this).load(imgUrl).apply(new GlideOptions().miniThumb()).into(imageView);
//        GlideApp.with(this).load(imgUrl).miniThumb().into(imageView);
    }

    private void showNormalImage() {
        final ImageView imageView = findViewById(R.id.imageview);
        String imgUrl = "https://gimg2.baidu.com/image_search/src=http%3A%2F%2Fimg1.doubanio.com%2Fview%2Fnote%2Fl%2Fpublic%2Fp82460877.jpg&refer=http%3A%2F%2Fimg1.doubanio.com&app=2002&size=f9999,10000&q=a80&n=0&g=0n&fmt=auto?sec=1651994649&t=e5ab548edb343984d05868bf34cdcb5f";
        RequestManager requestManager = Glide.with(this);
        Log.d(TAG, "showNormalImage: requestManager:" + requestManager);
        GlideApp.with(this).asMyBitmap().load(imgUrl).into(imageView);
    }

    private void showBrokenGif() {
        final ImageView gifIv = findViewById(R.id.gifiv);
//        String imgUrl = "http://pic.wenwen.soso.com/pqpic/wenwenpic/0/20171011210319-79198387_gif_405_293_4728568/0";
        String imgUrl = "http://pic.wenwen.soso.com/pqpic/wenwenpic/0/20171116143737-1895154083_gif_398_305_3740344/0";
//        String imgUrl = "https://wx2.sinaimg.cn/large/866a67c7gy1fkaw7ewstng20b208hhdx.gif";
//        String imgUrl   = "http://pic.wenwen.soso.com/pqpic/wenwenpic/0/20171116143738-1327109971_gif_405_293_3665345/0";
        Glide.with(this).asGif().load(imgUrl).into(gifIv);
    }
}
