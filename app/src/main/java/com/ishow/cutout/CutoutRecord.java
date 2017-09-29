package com.ishow.cutout;

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
    private List<Path> pathList;
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


    public void addPath(Path path) {
        if (path == null) {
            return;
        }
        if (pathList == null) {
            pathList = new ArrayList<>();
        }
        pathList.add(path);
    }

    public void addCutoutTrack(float length) {
        if (cutoutTrackList == null) {
            cutoutTrackList = new ArrayList<>();
        }
        cutoutTrackList.add(length);
    }


    public List<Path> getPathList() {
        if (pathList == null) {
            pathList = new ArrayList<>();
        }
        return pathList;
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

    /**
     * 是否有 记录
     */
    public boolean hasRecord() {
        return !getPathList().isEmpty() || !getCutoutTrackList().isEmpty();
    }
}
