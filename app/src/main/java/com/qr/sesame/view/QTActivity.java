package com.qr.sesame.view;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.qr.sesame.R;
import com.qr.sesame.api.QRService;
import com.qr.sesame.entiy.SuccessData;
import com.qr.sesame.entiy.UserInfo;
import com.qr.sesame.util.IPSharedPrefsUtil;
import com.qr.sesame.util.ToastUtil;
import com.qr.sesame.util.UserInfoSharedPrefsUtil;
import com.xys.libzxing.zxing.activity.CaptureActivity;
import com.zhy.m.permission.MPermissions;
import com.zhy.m.permission.PermissionDenied;
import com.zhy.m.permission.PermissionGrant;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by wangqi on 2017/4/17.
 */

public class QTActivity extends BaseActivity implements View.OnClickListener {

    @BindView(R.id.scan)
    Button scan;
    @BindView(R.id.result)
    TextView tvResult;
    Button btn_change;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.qt_activity);
        ButterKnife.bind(this);
        scan = (Button) findViewById(R.id.scan);
        tvResult = (TextView) findViewById(R.id.result);
        scan.setOnClickListener(this);
        btn_change = (Button) findViewById(R.id.btn_change);
        btn_change.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.scan:
                customScan();
                break;
            case R.id.btn_change:
                startActivity(new Intent(QTActivity.this, MainActivity.class));
                break;
        }
    }

    public static final int PERMISSION_CAMERA = 0;

    public void customScan() {
        MPermissions.requestPermissions(this, PERMISSION_CAMERA, Manifest.permission.CAMERA);
    }

    @PermissionGrant(PERMISSION_CAMERA)
    public void requestCameraGranted() {
        //启动扫描二维码界面
        startActivityForResult(new Intent(this, CaptureActivity.class), 0);
    }

    @PermissionDenied(PERMISSION_CAMERA)
    public void requestCameraDenied() {
        ToastUtil.shortToast(this, "请前往设置授予照相机权限");
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(new Intent(this, CaptureActivity.class), 0);
            } else {
                ToastUtil.shortToast(this, "请前往设置授予照相机权限");
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //扫描二维码成功
        if (resultCode == RESULT_OK) {
            Bundle bundle = data.getExtras();
            if (bundle != null) {
                String result = bundle.getString("result");
                tvResult.setText(result);
                Gson gson = new Gson();
                try {
                    UserInfo cache=UserInfoSharedPrefsUtil.getUserInfoCache(this);
                    List<UserInfo> userInfos = jsonToArrayList(result, UserInfo.class);
                    for (int i = 0; i < userInfos.size(); i++) {
                        UserInfo userInfo = userInfos.get(i);
                        //二维码内容和本地保存的用户信息一致则开门
                        if (userInfo.getName().equals(cache.getName())
                                && userInfo.getPassword().equals(cache.getPassword())
                                && userInfo.getIdcard().equals(cache.getIdcard())) {
                            openDoor();
                            return;
                        }
                    }
                    ToastUtil.shortToast(this, "未通过");
                } catch (JsonSyntaxException e) {
                    e.printStackTrace();
                    ToastUtil.shortToast(this, "未通过");
                }
            }
        }

    }

    public static <T> ArrayList<T> jsonToArrayList(String json, Class<T> clazz) {
        Type type = new TypeToken<ArrayList<JsonObject>>() {
        }.getType();
        ArrayList<JsonObject> jsonObjects = new Gson().fromJson(json, type);

        ArrayList<T> arrayList = new ArrayList<>();
        for (JsonObject jsonObject : jsonObjects) {
            arrayList.add(new Gson().fromJson(jsonObject, clazz));
        }
        return arrayList;
    }

    private OkHttpClient getOkHttpClient() {
        //日志显示级别
        HttpLoggingInterceptor.Level level = HttpLoggingInterceptor.Level.BODY;
        //新建log拦截器
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(String message) {
                Log.v("wq", message.toString());
            }
        });
        loggingInterceptor.setLevel(level);
        //定制OkHttp
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient
                .Builder();
        //OkHttp进行添加拦截器loggingInterceptor
        httpClientBuilder.addInterceptor(loggingInterceptor);
        return httpClientBuilder.build();
    }

    //扫描成功后发送请求给服务端
    private void openDoor() {
        String baseUrl = "http://" + IPSharedPrefsUtil.getIPCache(this) + ":8080/qrcls/";
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(getOkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build();

        QRService qrService = retrofit.create(QRService.class);
        qrService.scan("scanQRCode", "true")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<SuccessData>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        ToastUtil.shortToast(QTActivity.this, "未通过");
                    }

                    @Override
                    public void onNext(SuccessData successData) {
                        if (successData.getStatus() == 1) {
                            ToastUtil.shortToast(QTActivity.this, successData.getMsg());

                        } else
                            ToastUtil.shortToast(QTActivity.this, successData.getMsg());
                    }
                });
    }

}
