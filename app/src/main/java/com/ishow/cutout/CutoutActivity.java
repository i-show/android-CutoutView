package com.ishow.cutout;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.ishow.common.app.activity.BaseActivity;
import com.ishow.common.utils.image.loader.ImageLoader;
import com.ishow.common.utils.image.select.OnSelectPhotoListener;
import com.ishow.common.utils.image.select.SelectPhotoUtils;
import com.ishow.common.widget.loading.LoadingDialog;

import java.util.List;

import static com.ishow.cutout.R.id.cutout;

public class CutoutActivity extends BaseActivity implements OnSelectPhotoListener, View.OnClickListener {
    private SelectPhotoUtils mSelectPhotoUtils;
    private CutoutView mCutoutView;
    private LoadingDialog mLoadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageLoader.init(this);
        mCutoutView = (CutoutView) findViewById(cutout);
        mCutoutView.setMode(CutoutView.Mode.CUT_OUT);
        mCutoutView.setOnCutoutListener(new CutoutView.OnCutoutListener() {
            @Override
            public void canBack(int count) {

            }

            @Override
            public void showLoading() {
                mLoadingDialog = LoadingDialog.show(CutoutActivity.this, mLoadingDialog);
            }

            @Override
            public void dismissLoading() {
                LoadingDialog.dismiss(mLoadingDialog);
            }
        });
        mCutoutView.setPhoto("/storage/emulated/0/Android/data/com.ishow.demo/cache/5EC29A97-A8E4-4DAA-AFFC-B654F0B1436C.jpg");

        mSelectPhotoUtils = new SelectPhotoUtils(this, SelectPhotoUtils.SelectMode.SINGLE);
        mSelectPhotoUtils.setOnSelectPhotoListener(this);


        View button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSelectPhotoUtils.select();
            }
        });

        View cut = findViewById(R.id.cut);
        cut.setOnClickListener(this);
        View back = findViewById(R.id.back);
        back.setOnClickListener(this);
        View eraser = findViewById(R.id.eraser);
        eraser.setOnClickListener(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mSelectPhotoUtils.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onSelectedPhoto(List<String> multiPath, String singlePath) {
        mCutoutView.setPhoto(singlePath);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cut:
                mCutoutView.setMode(CutoutView.Mode.CUT_OUT);
                break;
            case R.id.back:
                mCutoutView.back();
                break;
            case R.id.eraser:
                mCutoutView.setMode(CutoutView.Mode.ERASER);
                break;
        }
    }
}
