package com.bumptech.glideapp;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.RequestOptions;

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
    }

    private void showResImage() {
        ImageView resImage = findViewById(R.id.res_image);
        Glide.with(this).load(R.mipmap.bird).into(resImage);
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
