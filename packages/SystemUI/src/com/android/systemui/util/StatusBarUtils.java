/*
 * Copyright (C) 2023-2024 The risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.util;

import android.content.Context;
import android.content.res.Resources;
import android.provider.Settings;
import android.util.TypedValue;

public class StatusBarUtils {

    public static final String LEFT_PADDING = "statusbar_left_padding";
    public static final String RIGHT_PADDING = "statusbar_right_padding";
    public static final String TOP_PADDING = "statusbar_top_padding";

    private int mLeftPad;
    private int mRightPad;
    private int mTopPad;

    private Context mContext;
    private Resources mRes;

    private static StatusBarUtils sInstance;

    public StatusBarUtils(Context context) {
        mContext = context;
        mRes = mContext.getResources();
        loadPaddingFromSettings();
    }

    public int getLeftPadding() {
        return mLeftPad;
    }

    public int getRightPadding() {
        return mRightPad;
    }

    public int getTopPadding() {
        return mTopPad;
    }
    
    private int convertToDip(int padding) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                padding,
                mRes.getDisplayMetrics()));
    }

    public int getDefaultLeftPadding() {
        return mRes.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_padding_start);
    }

    public int getDefaultRightPadding() {
        return mRes.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_padding_end);
    }

    public int getDefaultTopPadding() {
        return mRes.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_padding_top);
    }

    private void loadPaddingFromSettings() {
        mLeftPad = convertToDip(Settings.System.getInt(mContext.getContentResolver(), LEFT_PADDING, getDefaultLeftPadding()));
        mRightPad = convertToDip(Settings.System.getInt(mContext.getContentResolver(), RIGHT_PADDING, getDefaultRightPadding()));
        mTopPad = convertToDip(Settings.System.getInt(mContext.getContentResolver(), TOP_PADDING, getDefaultTopPadding()));
    }
}
