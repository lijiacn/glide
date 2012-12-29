/*
 * Copyright (c) 2012. Bump Technologies Inc. All Rights Reserved.
 */

package com.bumptech.photos.view.loader;

import com.bumptech.photos.LoadedCallback;
import com.bumptech.photos.PhotoManager;
import com.bumptech.photos.view.assetpath.AssetPathConverter;

/**
 * Created with IntelliJ IDEA.
 * User: sam
 * Date: 12/25/12
 * Time: 10:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class CenterCrop<T> extends PhotoManagerLoader<T> {

    public CenterCrop(PhotoManager photoManager, AssetPathConverter<T> assetToPath) {
        super(photoManager, assetToPath);
    }

    @Override
    protected Object doFetchImage(String path, T model, int width, int height, LoadedCallback cb) {
        return photoManager.centerCrop(path, width, height, cb);
    }
}
