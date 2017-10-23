package com.ishow.cutout;

import android.graphics.Matrix;
import android.graphics.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by yuhaiyang on 2017/9/28.
 * 历史记录
 */

@SuppressWarnings("ALL")
public class CutoutRecord {
    /**
     * 图片路径
     */
    private String imagePath;
    /**
     * 路径列表
     */
    private List<Path> eraserPathList;
    private List<Matrix> eraserMatrixList;
    /**
     * 抠图的轨迹
     */
    private List<Float> cutoutTrackList;

    public CutoutRecord() {
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }


    public void addEraserMatrix(Matrix matrix) {
        if (matrix == null) {
            return;
        }
        if (eraserMatrixList == null) {
            eraserMatrixList = new ArrayList<>();
        }
        eraserMatrixList.add(matrix);
    }

    public void addEraserPath(Path path) {
        if (path == null) {
            return;
        }
        if (eraserPathList == null) {
            eraserPathList = new ArrayList<>();
        }
        eraserPathList.add(path);
    }

    public void addCutoutTrack(float length) {
        if (cutoutTrackList == null) {
            cutoutTrackList = new ArrayList<>();
        }
        cutoutTrackList.add(length);
    }


    public List<Path> getEraserPathList() {
        if (eraserPathList == null) {
            eraserPathList = new ArrayList<>();
        }
        return eraserPathList;
    }

    public List<Matrix> getEraserMatrixList() {
        if (eraserMatrixList == null) {
            eraserMatrixList = new ArrayList<>();
        }
        return eraserMatrixList;
    }

    public List<Float> getCutoutTrackList() {
        if (cutoutTrackList == null) {
            cutoutTrackList = new ArrayList<>();
        }
        return cutoutTrackList;
    }

    public void clearPointList() {
        if (cutoutTrackList == null) {
            return;
        }
        cutoutTrackList.clear();
    }

    public void clearEraserInfo() {
        clearEraserMatrixList();
        clearEraserPathList();
    }

    public void clearEraserMatrixList() {
        getEraserMatrixList().clear();
    }

    public void clearEraserPathList() {
        getEraserPathList().clear();
    }

    /**
     * 是否有 记录
     */
    public boolean hasRecord() {
        return !getEraserPathList().isEmpty() || !getCutoutTrackList().isEmpty();
    }
}
