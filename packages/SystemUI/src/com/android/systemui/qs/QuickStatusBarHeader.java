/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.policy.SystemBarUtils;
import com.android.settingslib.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.battery.BatteryMeterView;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.statusbar.phone.StatusBarContentInsetsProvider;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.phone.StatusBarIconController.TintedIconManager;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.NetworkTraffic;
import com.android.systemui.statusbar.policy.VariableDateView;
import com.android.systemui.util.LargeScreenUtils;
import com.android.systemui.tuner.TunerService;

import java.util.List;

/**
 * View that contains the top-most bits of the QS panel (primarily the status bar with date, time,
 * battery, carrier info and privacy icons) and also contains the {@link QuickQSPanel}.
 */
public class QuickStatusBarHeader extends FrameLayout implements TunerService.Tunable,
        View.OnClickListener, View.OnLongClickListener {    

    private static final String SHOW_QS_CLOCK =
            "system:" + Settings.System.SHOW_QS_CLOCK;
    private static final String SHOW_QS_DATE =
            "system:" + Settings.System.SHOW_QS_DATE;
    public static final String STATUS_BAR_BATTERY_STYLE =
            "system:" + Settings.System.STATUS_BAR_BATTERY_STYLE;
    public static final String QS_BATTERY_STYLE =
            "system:" + Settings.System.QS_BATTERY_STYLE;
    public static final String QS_BATTERY_LOCATION =
            "system:" + Settings.System.QS_BATTERY_LOCATION;
    private static final String QS_SHOW_BATTERY_PERCENT =
            "system:" + Settings.System.QS_SHOW_BATTERY_PERCENT;
    private static final String QS_SHOW_BATTERY_ESTIMATE =
            "system:" + Settings.System.QS_SHOW_BATTERY_ESTIMATE;
    private static final String QS_WEATHER_POSITION =
            "system:" + Settings.System.QS_WEATHER_POSITION;
    private static final String NETWORK_TRAFFIC_LOCATION =
            "system:" + Settings.System.NETWORK_TRAFFIC_LOCATION;

    private boolean mExpanded;
    private boolean mQsDisabled;

    @Nullable
    private TouchAnimator mAlphaAnimator;
    @Nullable
    private TouchAnimator mTranslationAnimator;
    @Nullable
    private TouchAnimator mIconsAlphaAnimator;
    private TouchAnimator mIconsAlphaAnimatorFixed;

    protected QuickQSPanel mHeaderQsPanel;
    private View mDatePrivacyView;
    private View mDateView;
    // DateView next to clock. Visible on QQS
    private VariableDateView mClockDateView;
    private View mStatusIconsView;
    private View mContainer;

    private View mQsWeatherView;
    private View mQsWeatherHeaderView; 

    private View mQSCarriers;
    private ViewGroup mClockContainer;
    private Clock mClockView;
    private Space mDatePrivacySeparator;
    private View mClockIconsSeparator;
    private boolean mShowClockIconsSeparator;
    private View mRightLayout;
    private View mDateContainer;
    private View mPrivacyContainer;

    private BatteryMeterView mBatteryRemainingIcon;
    private BatteryMeterView mBatteryIcon;
    private StatusIconContainer mIconContainer;
    private View mPrivacyChip;
    
    private int mQQSWeather;

    @Nullable
    private TintedIconManager mTintedIconManager;
    @Nullable
    private QSExpansionPathInterpolator mQSExpansionPathInterpolator;
    private StatusBarContentInsetsProvider mInsetsProvider;

    private int mRoundedCornerPadding = 0;
    private int mStatusBarPaddingTop;
    private int mWaterfallTopInset;
    private int mCutOutPaddingLeft;
    private int mCutOutPaddingRight;
    private float mKeyguardExpansionFraction;
    private int mTextColorPrimary = Color.TRANSPARENT;
    private int mTopViewMeasureHeight;

    @NonNull
    private List<String> mRssiIgnoredSlots = List.of();
    private boolean mIsSingleCarrier;

    private boolean mUseCombinedQSHeader;
    private boolean mShowDate;

    private int mStatusBarBatteryStyle, mQSBatteryStyle, mQSBatteryLocation;
    private final ActivityStarter mActivityStarter;
    private final Vibrator mVibrator;

    private NetworkTraffic mNetworkTraffic;
    private boolean mShowNetworkTraffic;

    public QuickStatusBarHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        mActivityStarter = Dependency.get(ActivityStarter.class);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /**
     * How much the view containing the clock and QQS will translate down when QS is fully expanded.
     *
     * This matches the measured height of the view containing the date and privacy icons.
     */
    public int getOffsetTranslation() {
        return mTopViewMeasureHeight;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mHeaderQsPanel = findViewById(R.id.quick_qs_panel);
        mDatePrivacyView = findViewById(R.id.quick_status_bar_date_privacy);
        mStatusIconsView = findViewById(R.id.quick_qs_status_icons);
        mQSCarriers = findViewById(R.id.carrier_group);
        mContainer = findViewById(R.id.qs_container);
        mIconContainer = findViewById(R.id.statusIcons);
        mPrivacyChip = findViewById(R.id.privacy_chip);
        mDateView = findViewById(R.id.date);
        mDateView.setOnClickListener(this);
        mDateView.setOnLongClickListener(this);
        mClockDateView = findViewById(R.id.date_clock);
        mClockDateView.setOnClickListener(this);
        mClockDateView.setOnLongClickListener(this);
        mQsWeatherView = findViewById(R.id.qs_weather_view);
        mQsWeatherView.setOnLongClickListener(this);
        mQsWeatherHeaderView = findViewById(R.id.weather_view_header);
        mQsWeatherHeaderView.setOnLongClickListener(this);
        mClockIconsSeparator = findViewById(R.id.separator);
        mRightLayout = findViewById(R.id.rightLayout);
        mDateContainer = findViewById(R.id.date_container);
        mPrivacyContainer = findViewById(R.id.privacy_container);

        mClockContainer = findViewById(R.id.clock_container);
        mClockView = findViewById(R.id.clock);
        mClockView.setQsHeader();
        mClockView.setOnClickListener(this);
        mClockView.setOnLongClickListener(this);
        mDatePrivacySeparator = findViewById(R.id.space);
        // Tint for the battery icons are handled in setupHost()
        mBatteryRemainingIcon = findViewById(R.id.batteryRemainingIcon);
        mBatteryRemainingIcon.mQS = true;
        mBatteryRemainingIcon.setOnClickListener(this);
        mBatteryRemainingIcon.setOnLongClickListener(this);

        mBatteryIcon = findViewById(R.id.batteryIcon);

        mNetworkTraffic = findViewById(R.id.network_traffic);

        Configuration config = mContext.getResources().getConfiguration();
        setDatePrivacyContainersWidth(config.orientation == Configuration.ORIENTATION_LANDSCAPE);

        mIconsAlphaAnimatorFixed = new TouchAnimator.Builder()
                .addFloat(mIconContainer, "alpha", 0, 1)
                .addFloat(mBatteryRemainingIcon, "alpha", 0, 1)
                .build();

        updateResources();

        Dependency.get(TunerService.class).addTunable(this,
                SHOW_QS_CLOCK,
                SHOW_QS_DATE,
                STATUS_BAR_BATTERY_STYLE,
                QS_BATTERY_STYLE,
                QS_BATTERY_LOCATION,
                QS_SHOW_BATTERY_PERCENT,
                QS_SHOW_BATTERY_ESTIMATE,
                QS_WEATHER_POSITION,
                NETWORK_TRAFFIC_LOCATION);
    }

    void onAttach(TintedIconManager iconManager,
            QSExpansionPathInterpolator qsExpansionPathInterpolator,
            List<String> rssiIgnoredSlots,
            StatusBarContentInsetsProvider insetsProvider,
            boolean useCombinedQSHeader) {
        mUseCombinedQSHeader = useCombinedQSHeader;
        mTintedIconManager = iconManager;
        mRssiIgnoredSlots = rssiIgnoredSlots;
        mInsetsProvider = insetsProvider;
        int fillColor = Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.textColorPrimary);

        // Set the correct tint for the status icons so they contrast
        iconManager.setTint(fillColor);

        mQSExpansionPathInterpolator = qsExpansionPathInterpolator;
        updateAnimators();
    }

    void setIsSingleCarrier(boolean isSingleCarrier) {
        mIsSingleCarrier = isSingleCarrier;
        if (mIsSingleCarrier) {
            mIconContainer.removeIgnoredSlots(mRssiIgnoredSlots);
        }
        updateAlphaAnimator();
    }

    public QuickQSPanel getHeaderQsPanel() {
        return mHeaderQsPanel;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mDatePrivacyView.getMeasuredHeight() != mTopViewMeasureHeight) {
            mTopViewMeasureHeight = mDatePrivacyView.getMeasuredHeight();
            updateAnimators();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
        setDatePrivacyContainersWidth(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateResources();
    }

    @Override
    public void onClick(View v) {
        // Clock view is still there when the panel is not expanded
        // Making sure we get the date action when the user clicks on it
        // but actually is seeing the date
        if (v == mClockView) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    AlarmClock.ACTION_SHOW_ALARMS), 0);
        } else if (v == mDateView || v == mClockDateView) {
            Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendPath("time");
            builder.appendPath(Long.toString(System.currentTimeMillis()));
            Intent todayIntent = new Intent(Intent.ACTION_VIEW, builder.build());
            mActivityStarter.postStartActivityDismissingKeyguard(todayIntent, 0);
        } else if (v == mBatteryRemainingIcon) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    Intent.ACTION_POWER_USAGE_SUMMARY), 0);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (v == mClockView || v == mDateView || v == mClockDateView) {
            Intent nIntent = new Intent(Intent.ACTION_MAIN);
            nIntent.setClassName("com.android.settings",
                    "com.android.settings.Settings$DateTimeSettingsActivity");
            mActivityStarter.startActivity(nIntent, true /* dismissShade */);
            mVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            return true;
        } else if (v == mBatteryRemainingIcon) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    Intent.ACTION_POWER_USAGE_SUMMARY), 0);
            return true;
        } else if (v == mQsWeatherHeaderView || v == mQsWeatherView) {
            Intent wIntent = new Intent(Intent.ACTION_MAIN);
            wIntent.setClassName("org.omnirom.omnijaws",
                    "org.omnirom.omnijaws.SettingsActivity");
            mActivityStarter.startActivity(wIntent, true /* dismissShade */);
            mVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            return true;
        }
        return false;
    }

    private void setDatePrivacyContainersWidth(boolean landscape) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mDateContainer.getLayoutParams();
        lp.width = landscape ? WRAP_CONTENT : 0;
        lp.weight = landscape ? 0f : 1f;
        mDateContainer.setLayoutParams(lp);

        lp = (LinearLayout.LayoutParams) mPrivacyContainer.getLayoutParams();
        lp.width = landscape ? WRAP_CONTENT : 0;
        lp.weight = landscape ? 0f : 1f;
        mPrivacyContainer.setLayoutParams(lp);
    }

    void updateResources() {
        Resources resources = mContext.getResources();
        boolean largeScreenHeaderActive =
                LargeScreenUtils.shouldUseLargeScreenShadeHeader(resources);

        boolean gone = largeScreenHeaderActive || mUseCombinedQSHeader || mQsDisabled;
        mStatusIconsView.setVisibility(gone ? View.GONE : View.VISIBLE);
        mDatePrivacyView.setVisibility(gone ? View.GONE : View.VISIBLE);

        mRoundedCornerPadding = resources.getDimensionPixelSize(
                R.dimen.rounded_corner_content_padding);

        int statusBarHeight = SystemBarUtils.getStatusBarHeight(mContext);

        mStatusBarPaddingTop = resources.getDimensionPixelSize(
                R.dimen.status_bar_padding_top);

        mDatePrivacyView.getLayoutParams().height = statusBarHeight;
        mDatePrivacyView.setLayoutParams(mDatePrivacyView.getLayoutParams());

        mStatusIconsView.getLayoutParams().height = statusBarHeight;
        mStatusIconsView.setLayoutParams(mStatusIconsView.getLayoutParams());

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (mQsDisabled) {
            lp.height = mStatusIconsView.getLayoutParams().height - mWaterfallTopInset;
        } else {
            lp.height = WRAP_CONTENT;
        }
        setLayoutParams(lp);

        int textColor = Utils.getColorAttrDefaultColor(mContext, android.R.attr.textColorPrimary);
        if (textColor != mTextColorPrimary) {
            int isCircleBattery = mBatteryIcon.getBatteryStyle();
            int textColorSecondary = Utils.getColorAttrDefaultColor(mContext,
                    (isCircleBattery == 1 || isCircleBattery == 2 || isCircleBattery == 3)
                    ? android.R.attr.textColorHint : android.R.attr.textColorSecondary);
            mTextColorPrimary = textColor;
            mClockView.setTextColor(textColor);
            if (mTintedIconManager != null) {
                mTintedIconManager.setTint(textColor);
            }
            mBatteryRemainingIcon.updateColors(mTextColorPrimary, textColorSecondary,
                    mTextColorPrimary);
            mBatteryIcon.updateColors(mTextColorPrimary, textColorSecondary,
                    mTextColorPrimary);
            mNetworkTraffic.setTint(textColor);
        }

        MarginLayoutParams qqsLP = (MarginLayoutParams) mHeaderQsPanel.getLayoutParams();
        qqsLP.topMargin = largeScreenHeaderActive || !mUseCombinedQSHeader ? mContext.getResources()
                .getDimensionPixelSize(R.dimen.qqs_layout_margin_top) : SystemBarUtils.getQuickQsOffsetHeight(mContext);
        mHeaderQsPanel.setLayoutParams(qqsLP);

        updateHeadersPadding();
        updateAnimators();

        updateClockDatePadding();
    }

    private void updateClockDatePadding() {
        int startPadding = mContext.getResources()
                .getDimensionPixelSize(R.dimen.status_bar_left_clock_starting_padding);
        int endPadding = mContext.getResources()
                .getDimensionPixelSize(R.dimen.status_bar_left_clock_end_padding);
        mClockView.setPaddingRelative(
                startPadding,
                mClockView.getPaddingTop(),
                endPadding,
                mClockView.getPaddingBottom()
        );

        MarginLayoutParams lp = (MarginLayoutParams) mClockDateView.getLayoutParams();
        lp.setMarginStart(endPadding);
        mClockDateView.setLayoutParams(lp);
    }

    private void updateAnimators() {
        if (mUseCombinedQSHeader) {
            mTranslationAnimator = null;
            return;
        }
        updateAlphaAnimator();
        int offset = mTopViewMeasureHeight;

        mTranslationAnimator = new TouchAnimator.Builder()
                .addFloat(mContainer, "translationY", 0, offset)
                .setInterpolator(mQSExpansionPathInterpolator != null
                        ? mQSExpansionPathInterpolator.getYInterpolator()
                        : null)
                .build();
    }

    private void updateAlphaAnimator() {
        if (mUseCombinedQSHeader) {
            mAlphaAnimator = null;
            return;
        }
        TouchAnimator.Builder builder = new TouchAnimator.Builder()
                // These views appear on expanding down
                .addFloat(mDateView, "alpha", 0, 0, 1)
                .addFloat(mClockDateView, "alpha", 1, 0, 0)
                .addFloat(mQsWeatherHeaderView, "alpha", 0, 0, 1)
                .addFloat(mQsWeatherView, "alpha", 1, 0, 0)
                .addFloat(mQSCarriers, "alpha", 0, 1)
                .setListener(new TouchAnimator.ListenerAdapter() {
                    @Override
                    public void onAnimationAtEnd() {
                        super.onAnimationAtEnd();
                        if (!mIsSingleCarrier) {
                            mIconContainer.addIgnoredSlots(mRssiIgnoredSlots);
                        }
                        // Make it gone so there's enough room for carrier names
                        mClockDateView.setVisibility(View.GONE);
                    }

                    @Override
                    public void onAnimationStarted() {
                        if (mShowDate) {
                            mClockDateView.setVisibility(View.VISIBLE);
                            mClockDateView.setFreezeSwitching(true);
                        }
                        setSeparatorVisibility(false);
                        if (!mIsSingleCarrier) {
                            mIconContainer.addIgnoredSlots(mRssiIgnoredSlots);
                        }
                    }

                    @Override
                    public void onAnimationAtStart() {
                        super.onAnimationAtStart();
                        if (mShowDate) {
                            mClockDateView.setFreezeSwitching(false);
                            mClockDateView.setVisibility(View.VISIBLE);
                        }
                        setSeparatorVisibility(mShowClockIconsSeparator);
                        // In QQS we never ignore RSSI.
                        mIconContainer.removeIgnoredSlots(mRssiIgnoredSlots);
                    }
                });
        mAlphaAnimator = builder.build();
    }

    void setChipVisibility(boolean visibility) {
        boolean showBattery = mQSBatteryLocation == 1
                && (mBatteryIcon.getBatteryStyle() != 5
                || mBatteryIcon.getBatteryEstimate() != 0);
        if (showBattery) {
            mBatteryIcon.setVisibility(visibility ? View.GONE : View.VISIBLE);
        }
        mNetworkTraffic.setChipVisibility(visibility);
        if (visibility || showBattery || mShowNetworkTraffic) {
            // Animates the icons and battery indicator from alpha 0 to 1, when the chip is visible
            mIconsAlphaAnimator = mIconsAlphaAnimatorFixed;
            mIconsAlphaAnimator.setPosition(mKeyguardExpansionFraction);
        } else {
            mIconsAlphaAnimator = null;
            mIconContainer.setAlpha(1);
            mBatteryRemainingIcon.setAlpha(1);
        }

    }

    /** */
    public void setExpanded(boolean expanded, QuickQSPanelController quickQSPanelController) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        quickQSPanelController.setExpanded(expanded);
        updateEverything();
    }

    /**
     * Animates the inner contents based on the given expansion details.
     *
     * @param forceExpanded whether we should show the state expanded forcibly
     * @param expansionFraction how much the QS panel is expanded/pulled out (up to 1f)
     * @param panelTranslationY how much the panel has physically moved down vertically (required
     *                          for keyguard animations only)
     */
    public void setExpansion(boolean forceExpanded, float expansionFraction,
                             float panelTranslationY) {
        final float keyguardExpansionFraction = forceExpanded ? 1f : expansionFraction;

        if (mAlphaAnimator != null) {
            mAlphaAnimator.setPosition(keyguardExpansionFraction);
        }
        if (mTranslationAnimator != null) {
            mTranslationAnimator.setPosition(keyguardExpansionFraction);
        }
        if (mIconsAlphaAnimator != null) {
            mIconsAlphaAnimator.setPosition(keyguardExpansionFraction);
        }
        // If forceExpanded (we are opening QS from lockscreen), the animators have been set to
        // position = 1f.
        if (forceExpanded) {
            setTranslationY(panelTranslationY);
        } else {
            setTranslationY(0);
        }

        mKeyguardExpansionFraction = keyguardExpansionFraction;
    }

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        mHeaderQsPanel.setDisabledByPolicy(disabled);
        mStatusIconsView.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        updateResources();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        // Handle padding of the views
        DisplayCutout cutout = insets.getDisplayCutout();

        Pair<Integer, Integer> sbInsets = mInsetsProvider
                .getStatusBarContentInsetsForCurrentRotation();
        boolean hasCornerCutout = mInsetsProvider.currentRotationHasCornerCutout();

        mDatePrivacyView.setPadding(sbInsets.first, 0, sbInsets.second, 0);
        mStatusIconsView.setPadding(sbInsets.first, 0, sbInsets.second, 0);
        LinearLayout.LayoutParams datePrivacySeparatorLayoutParams =
                (LinearLayout.LayoutParams) mDatePrivacySeparator.getLayoutParams();
        LinearLayout.LayoutParams mClockIconsSeparatorLayoutParams =
                (LinearLayout.LayoutParams) mClockIconsSeparator.getLayoutParams();
        if (cutout != null) {
            Rect topCutout = cutout.getBoundingRectTop();
            if (topCutout.isEmpty() || hasCornerCutout) {
                datePrivacySeparatorLayoutParams.width = 0;
                mDatePrivacySeparator.setVisibility(View.GONE);
                mClockIconsSeparatorLayoutParams.width = 0;
                setSeparatorVisibility(false);
                mShowClockIconsSeparator = false;
            } else {
                datePrivacySeparatorLayoutParams.width = topCutout.width();
                mDatePrivacySeparator.setVisibility(View.VISIBLE);
                mClockIconsSeparatorLayoutParams.width = topCutout.width();
                mShowClockIconsSeparator = true;
                setSeparatorVisibility(mKeyguardExpansionFraction == 0f);
            }
        }
        mDatePrivacySeparator.setLayoutParams(datePrivacySeparatorLayoutParams);
        mClockIconsSeparator.setLayoutParams(mClockIconsSeparatorLayoutParams);
        mCutOutPaddingLeft = sbInsets.first;
        mCutOutPaddingRight = sbInsets.second;
        mWaterfallTopInset = cutout == null ? 0 : cutout.getWaterfallInsets().top;

        updateHeadersPadding();
        return super.onApplyWindowInsets(insets);
    }

    /**
     * Sets the visibility of the separator between clock and icons.
     *
     * This separator is "visible" when there is a center cutout, to block that space. In that
     * case, the clock and the layout on the right (containing the icons and the battery meter) are
     * set to weight 1 to take the available space.
     * @param visible whether the separator between clock and icons should be visible.
     */
    private void setSeparatorVisibility(boolean visible) {
        int newVisibility = visible ? View.VISIBLE : View.GONE;
        if (mClockIconsSeparator.getVisibility() == newVisibility) return;

        mClockIconsSeparator.setVisibility(visible ? View.VISIBLE : View.GONE);
        mQSCarriers.setVisibility(visible ? View.GONE : View.VISIBLE);

        LinearLayout.LayoutParams lp =
                (LinearLayout.LayoutParams) mClockContainer.getLayoutParams();
        lp.width = visible ? 0 : WRAP_CONTENT;
        lp.weight = visible ? 1f : 0f;
        mClockContainer.setLayoutParams(lp);

        lp = (LinearLayout.LayoutParams) mRightLayout.getLayoutParams();
        lp.width = visible ? 0 : WRAP_CONTENT;
        lp.weight = visible ? 1f : 0f;
        mRightLayout.setLayoutParams(lp);
    }

    private void updateHeadersPadding() {
        setContentMargins(mDatePrivacyView, 0, 0);
        setContentMargins(mStatusIconsView, 0, 0);
        int paddingLeft = 0;
        int paddingRight = 0;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        int leftMargin = lp.leftMargin;
        int rightMargin = lp.rightMargin;

        // The clock might collide with cutouts, let's shift it out of the way.
        // We only do that if the inset is bigger than our own padding, since it's nicer to
        // align with
        if (mCutOutPaddingLeft > 0) {
            // if there's a cutout, let's use at least the rounded corner inset
            int cutoutPadding = Math.max(mCutOutPaddingLeft, mRoundedCornerPadding);
            paddingLeft = Math.max(cutoutPadding - leftMargin, 0);
        }
        if (mCutOutPaddingRight > 0) {
            // if there's a cutout, let's use at least the rounded corner inset
            int cutoutPadding = Math.max(mCutOutPaddingRight, mRoundedCornerPadding);
            paddingRight = Math.max(cutoutPadding - rightMargin, 0);
        }

        mDatePrivacyView.setPadding(paddingLeft,
                mStatusBarPaddingTop,
                paddingRight,
                0);
        mStatusIconsView.setPadding(paddingLeft,
                mStatusBarPaddingTop,
                paddingRight,
                0);
    }

    public void updateEverything() {
        post(() -> setClickable(!mExpanded));
    }

    private void setContentMargins(View view, int marginStart, int marginEnd) {
        MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
        lp.setMarginStart(marginStart);
        lp.setMarginEnd(marginEnd);
        view.setLayoutParams(lp);
    }

    /**
     * Scroll the headers away.
     *
     * @param scrollY the scroll of the QSPanel container
     */
    public void setExpandedScrollAmount(int scrollY) {
        mStatusIconsView.setScrollY(scrollY);
        mDatePrivacyView.setScrollY(scrollY);
    }

    private void updateBatteryStyle() {
        int style;
        if (mQSBatteryStyle == -1) {
            style = mStatusBarBatteryStyle;
        } else {
            style = mQSBatteryStyle;
        }
        mBatteryRemainingIcon.setBatteryStyle(style);
        mBatteryIcon.setBatteryStyle(style);
        setChipVisibility(mPrivacyChip.getVisibility() == View.VISIBLE);
    }

    public BatteryMeterView getBatteryMeterView() {
        if (mQSBatteryLocation == 0) {
            return mBatteryRemainingIcon;
        }
        return mBatteryIcon;
    }

    private void updateQSWeatherPosition() {
        if (mQQSWeather == 0) {
            mQsWeatherHeaderView.setVisibility(View.GONE);
            mQsWeatherView.setVisibility(View.VISIBLE);
        } else if (mQQSWeather == 1) {
            mQsWeatherHeaderView.setVisibility(View.VISIBLE);
            mQsWeatherView.setVisibility(View.GONE);
        } else {
            mQsWeatherHeaderView.setVisibility(View.VISIBLE);
            mQsWeatherView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case SHOW_QS_CLOCK:
                boolean showClock =
                        TunerService.parseIntegerSwitch(newValue, true);
                mClockView.setClockVisibleByUser(showClock);
                break;
            case SHOW_QS_DATE:
                mShowDate =
                        TunerService.parseIntegerSwitch(newValue, true);
                mDateContainer.setVisibility(mShowDate ? View.VISIBLE : View.GONE);
                mClockDateView.setVisibility(mShowDate ? View.VISIBLE : View.GONE);
                break;
            case QS_BATTERY_STYLE:
                mQSBatteryStyle =
                        TunerService.parseInteger(newValue, -1);
                updateBatteryStyle();
                break;
            case STATUS_BAR_BATTERY_STYLE:
                mStatusBarBatteryStyle =
                        TunerService.parseInteger(newValue, 0);
                updateBatteryStyle();
                break;
            case QS_BATTERY_LOCATION:
                mQSBatteryLocation =
                        TunerService.parseInteger(newValue, 0);
                if (mQSBatteryLocation == 0) {
                    mBatteryIcon.setVisibility(View.GONE);
                    mBatteryRemainingIcon.setVisibility(View.VISIBLE);
                } else {
                    mBatteryRemainingIcon.setVisibility(View.GONE);
                    mBatteryIcon.setVisibility(View.VISIBLE);
                }
                setChipVisibility(mPrivacyChip.getVisibility() == View.VISIBLE);
                break;
            case QS_SHOW_BATTERY_PERCENT:
                mBatteryRemainingIcon.setBatteryPercent(
                        TunerService.parseInteger(newValue, 2));
                mBatteryIcon.setBatteryPercent(
                        TunerService.parseInteger(newValue, 2));
                break;
            case QS_SHOW_BATTERY_ESTIMATE:
                mBatteryRemainingIcon.setBatteryEstimate(
                        TunerService.parseInteger(newValue, 0));
                mBatteryIcon.setBatteryEstimate(
                        TunerService.parseInteger(newValue, 0));
                setChipVisibility(mPrivacyChip.getVisibility() == View.VISIBLE);
                break;
            case QS_WEATHER_POSITION:
                mQQSWeather =
                       TunerService.parseInteger(newValue, 2);
                updateQSWeatherPosition();
                break;
            case NETWORK_TRAFFIC_LOCATION:
                mShowNetworkTraffic =
                        TunerService.parseInteger(newValue, 0) == 2;
                setChipVisibility(mPrivacyChip.getVisibility() == View.VISIBLE);
                break;
            default:
                break;
        }
    }
}
