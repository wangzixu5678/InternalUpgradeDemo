package com.wzx.test.internalupgradedemo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static String APKURL = "http://img.haidaxingyi.com/Android/app/hdxy.apk";
    private long starPos = 0;



    public static final  int PROGRESS_UNSTAR = 0;
    private static final int PROGRESS_PAUSE = 1;
    private static final int PROGRESS_DOWNLOADING = 2;
    private static final int PROGRESS_FINISH = 3;
    private static final int PROGRESS_ERROR = 4;
    private static final int PROGRESS_CANCEL = 5;

    private int downloadStatus = PROGRESS_UNSTAR ;


    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case PROGRESS_PAUSE:
                    mTvDownloadState.setText("暂停下载");
                    break;
                case PROGRESS_DOWNLOADING:
                    mTvDownloadState.setText(String.format(Locale.CHINA,"下载进度:%d",msg.arg1)+"%");
                    mPbDownloadProgress.setProgress(msg.arg1);
                    break;
                case PROGRESS_FINISH:
                    mTvDownloadState.setText("下载完成");
                    break;
                case PROGRESS_ERROR:
                    mTvDownloadState.setText("网络异常,请重新连接网络。");
                    break;
            }
        }
    };
    private DownLoadThread mDownLoadThread;

    private File mTargetFile;
    private NetWatchdog mNetWatchdog;
    private TextView mTvDownloadState;
    private ProgressBar mPbDownloadProgress;
    private long mTotalLenth;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        /**
         * 初始化UI控件
         */
        initView();
        /**
         * 初始化网络狗 监听网络状态变化
         */
        initNetWorkDog();
        /**
         * 初始化文件 并且 下载新的APK  先删除旧的APK
         */
        initFile();
        /**
         * 初始化线程
         */
        initThread();

    }


    private void initNetWorkDog() {
        mNetWatchdog = new NetWatchdog(this);
        mNetWatchdog.setNetConnectedListener(new NetWatchdog.NetConnectedListener() {
            @Override
            public void onReNetConnected(boolean isReconnect) {
                if (mDownLoadThread != null) {
                    if (downloadStatus!=PROGRESS_UNSTAR){
                        start();
                    }
                }
            }
            @Override
            public void onNetUnConnected() {
                if (mDownLoadThread != null) {
                    mHandler.sendEmptyMessage(PROGRESS_ERROR);
                    downloadStatus = PROGRESS_ERROR;
                }
            }
        });
        mNetWatchdog.startWatch();
    }


    private void initThread() {
        mDownLoadThread = new DownLoadThread();
    }

    private void initView() {
        mTvDownloadState = ((TextView) findViewById(R.id.tv_progress));
        mPbDownloadProgress  = ((ProgressBar) findViewById(R.id.pb));
        mPbDownloadProgress.setMax(100);
    }

    private void initFile() {
        File dir = new File(getExternalCacheDir() + "/download");
        if (!dir.exists()) {
            dir.mkdir();
        }
        mTargetFile = new File(dir, "海大星艺.apk");
        mTargetFile.delete();
    }


    public void btn_start(View view) {
        start();
    }

    public void btn_pause(View view) {
        pause();
    }

    public void start() {
        /**
         * 开始下载
         */
        if (downloadStatus!= PROGRESS_DOWNLOADING){
            downloadStatus = PROGRESS_DOWNLOADING;
            mDownLoadThread.start();
        }
    }

    public void pause() {
        /**
         * 暂停下载
         */
        downloadStatus = PROGRESS_PAUSE;

    }

    public void installApk(){
        Intent fileIntent = getFileIntent(mTargetFile);
        startActivity(fileIntent);
    }

    public Intent getFileIntent(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri apkUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = FileProvider.getUriForFile(this,getString(R.string.pack_name),file);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }else {
            apkUri = Uri.fromFile(file);
        }
        intent.setDataAndType(apkUri,getMIMEType(file));
        return intent;
    }

    public String getMIMEType(File file) {
        String type = null;
        String suffix = file.getName().substring(file.getName().lastIndexOf(".") + 1, file.getName().length());
        if (suffix.equals("apk")) {
            type = "application/vnd.android.package-archive";
        } else {
            type = "*/*";
        }
        return type;
    }


    public void btn_open_apk(View view) {
        /**
         * 启动Apk
         * 文件存在 并且 文件长度等于总文件长度说明已经下载完成
         */
        if (mTargetFile.exists()&&mTargetFile.length()==mTotalLenth){
            installApk();
        }else {
            Toast.makeText(this, "Apk未下载完成,请等待。", Toast.LENGTH_SHORT).show();
        }
    }

    class DownLoadThread extends Thread {
        @Override
        public void run() {
            HttpURLConnection urlConnection;
            FileOutputStream fos;
            InputStream inputStream;
            try {
                urlConnection = (HttpURLConnection) new URL(APKURL).openConnection();
                urlConnection.setConnectTimeout(5000);
                urlConnection.setReadTimeout(5000);
                urlConnection.setRequestMethod("GET");
                urlConnection.setRequestProperty("Connection", "Keep-Alive");
                urlConnection.setRequestProperty("Range", "bytes=" + starPos + "-");
                int contentLength = urlConnection.getContentLength();
                if (contentLength <= 0||downloadStatus ==PROGRESS_CANCEL) {
                    return;
                }
                mTotalLenth = starPos + contentLength;
                if (urlConnection.getResponseCode() == 206) {
                    inputStream = urlConnection.getInputStream();
                    byte[] buffer = new byte[512];
                    int len;
                    fos = new FileOutputStream(mTargetFile, true);
                    long time = System.currentTimeMillis();
                    while ((len = inputStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        fos.flush();
                        starPos += len;
                        Message message = Message.obtain();
                        message.what = PROGRESS_DOWNLOADING;
                        message.arg1 = ((int) (starPos * 100 / mTotalLenth));
                        if (starPos == mTotalLenth) {
                            downloadStatus = PROGRESS_FINISH;
                            mHandler.sendEmptyMessage(PROGRESS_FINISH);
                        } else {
                            if (System.currentTimeMillis()-time>50){
                                mHandler.sendMessage(message);
                                time =  System.currentTimeMillis();
                            }
                        }
                        if (downloadStatus == PROGRESS_PAUSE||downloadStatus==PROGRESS_ERROR) {
                            mHandler.sendEmptyMessage(downloadStatus);
                            fos.close();
                            inputStream.close();
                            return;
                        }
                    }
                    fos.close();
                    inputStream.close();
                }
            } catch (IOException e) {
                downloadStatus = PROGRESS_ERROR;
                mHandler.sendEmptyMessage(PROGRESS_ERROR);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mNetWatchdog.stopWatch();
        mDownLoadThread = null;
        downloadStatus = PROGRESS_CANCEL;
    }
}
