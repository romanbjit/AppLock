package com.lzx.lock.services;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.lzx.lock.LockApplication;
import com.lzx.lock.activities.lock.GestureUnlockActivity;
import com.lzx.lock.base.AppConstants;
import com.lzx.lock.db.CommLockInfoManager;
import com.lzx.lock.receiver.LockRestarterBroadcastReceiver;
import com.lzx.lock.utils.SpUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xian on 2017/2/17.
 */

public class LockService extends IntentService {
    public static final String UNLOCK_ACTION = "UNLOCK_ACTION";
    public static final String LOCK_SERVICE_LASTTIME = "LOCK_SERVICE_LASTTIME";
    public static final String LOCK_SERVICE_LASTAPP = "LOCK_SERVICE_LASTAPP";
    private static final String TAG = "LockService";
    public static boolean isActionLock = false;
    public boolean threadIsTerminate = false;
    @Nullable
    public String savePkgName;


    UsageStatsManager sUsageStatsManager;
    private boolean isLockTypeAccessibility;
    private long lastUnlockTimeSeconds = 0;
    private String lastUnlockPackageName = "";
    private boolean lockState;
    private ServiceReceiver mServiceReceiver;
    private CommLockInfoManager mLockInfoManager;
    @Nullable
    private ActivityManager activityManager;

  /*  @Override
    public int onStartCommand( Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }*/

    public LockService() {
        super("LockService");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        lockState = SpUtil.getInstance().getBoolean(AppConstants.LOCK_STATE);
        mLockInfoManager = new CommLockInfoManager(this);
        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

        mServiceReceiver = new ServiceReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(UNLOCK_ACTION);
        registerReceiver(mServiceReceiver, filter);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            sUsageStatsManager = (UsageStatsManager) this.getSystemService(Context.USAGE_STATS_SERVICE);
        }

        threadIsTerminate = true;

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        checkData();
    }

    private void checkData() {
        while (threadIsTerminate) {
            String packageName = getLauncherTopApp(LockService.this, activityManager);
            if (lockState && !TextUtils.isEmpty(packageName) && !inWhiteList(packageName)) {
                boolean isLockOffScreenTime = SpUtil.getInstance().getBoolean(AppConstants.LOCK_AUTO_SCREEN_TIME, false);
                boolean isLockOffScreen = SpUtil.getInstance().getBoolean(AppConstants.LOCK_AUTO_SCREEN, false);
                savePkgName = SpUtil.getInstance().getString(AppConstants.LOCK_LAST_LOAD_PKG_NAME, "");

                if (isLockOffScreenTime && !isLockOffScreen) {
                    long time = SpUtil.getInstance().getLong(AppConstants.LOCK_CURR_MILLISENCONS, 0);
                    long leaverTime = SpUtil.getInstance().getLong(AppConstants.LOCK_APART_MILLISENCONS, 0);
                    if (!TextUtils.isEmpty(savePkgName) && !TextUtils.isEmpty(packageName) && !savePkgName.equals(packageName)) {
                        if (getHomes().contains(packageName) || packageName.contains("launcher")) {
                            boolean isSetUnLock = mLockInfoManager.isSetUnLock(savePkgName);
                            if (!isSetUnLock) {
                                if (System.currentTimeMillis() - time > leaverTime) {
                                    mLockInfoManager.lockCommApplication(savePkgName);
                                }
                            }
                        }

                    }
                }

                if (isLockOffScreenTime && isLockOffScreen) {
                    long time = SpUtil.getInstance().getLong(AppConstants.LOCK_CURR_MILLISENCONS, 0);
                    long leaverTime = SpUtil.getInstance().getLong(AppConstants.LOCK_APART_MILLISENCONS, 0);
                    if (!TextUtils.isEmpty(savePkgName) && !TextUtils.isEmpty(packageName) && !savePkgName.equals(packageName)) {
                        if (getHomes().contains(packageName) || packageName.contains("launcher")) {
                            boolean isSetUnLock = mLockInfoManager.isSetUnLock(savePkgName);
                            if (!isSetUnLock) {
                                if (System.currentTimeMillis() - time > leaverTime) {
                                    mLockInfoManager.lockCommApplication(savePkgName);
                                }
                            }
                        }
                    }
                }


                if (!isLockOffScreenTime && isLockOffScreen && !TextUtils.isEmpty(savePkgName) && !TextUtils.isEmpty(packageName)) {
                    if (!savePkgName.equals(packageName)) {
                        isActionLock = false;
                        if (getHomes().contains(packageName) || packageName.contains("launcher")) {
                            boolean isSetUnLock = mLockInfoManager.isSetUnLock(savePkgName);
                            if (!isSetUnLock) {
                                mLockInfoManager.lockCommApplication(savePkgName);
                            }
                        }
                    } else {
                        isActionLock = true;
                    }

                }


                if (!isLockOffScreenTime && !isLockOffScreen && !TextUtils.isEmpty(savePkgName) && !TextUtils.isEmpty(packageName) && !savePkgName.equals(packageName)) {
                    if (getHomes().contains(packageName) || packageName.contains("launcher")) {
                        boolean isSetUnLock = mLockInfoManager.isSetUnLock(savePkgName);
                        if (!isSetUnLock) {
                            mLockInfoManager.lockCommApplication(savePkgName);
                        }
                    }

                }


                if (mLockInfoManager.isLockedPackageName(packageName)) {
                    passwordLock(packageName);
                    continue;
                } else {

                }
            }
            try {
                Thread.sleep(220);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    private boolean inWhiteList(String packageName) {
        return packageName.equals(AppConstants.APP_PACKAGE_NAME) || packageName.equals("com.android.settings");
    }

    public String getLauncherTopApp(@NonNull Context context, @NonNull ActivityManager activityManager) {
        //TODO: use another way as this might be take long time for get value
        isLockTypeAccessibility = SpUtil.getInstance().getBoolean(AppConstants.LOCK_TYPE, false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            List<ActivityManager.RunningTaskInfo> appTasks = activityManager.getRunningTasks(1);
            if (null != appTasks && !appTasks.isEmpty()) {
                return appTasks.get(0).topActivity.getPackageName();
            }
        } else if (!isLockTypeAccessibility) {
            long endTime = System.currentTimeMillis();
            long beginTime = endTime - 10000;
            String result = "";
            UsageEvents.Event event = new UsageEvents.Event();
            UsageEvents usageEvents = sUsageStatsManager.queryEvents(beginTime, endTime);
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    result = event.getPackageName();
                }
            }
            if (!android.text.TextUtils.isEmpty(result)) {
                return result;
            }
        } else {
            return LockAccessibilityService.getInstance().getForegroundPackage();
        }
        return "";
    }

    @NonNull
    private List<String> getHomes() {
        List<String> names = new ArrayList<>();
        PackageManager packageManager = this.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo ri : resolveInfo) {
            names.add(ri.activityInfo.packageName);
        }
        return names;
    }

    private void passwordLock(String packageName) {
        LockApplication.getInstance().clearAllActivity();
        Intent intent = new Intent(this, GestureUnlockActivity.class);

        intent.putExtra(AppConstants.LOCK_PACKAGE_NAME, packageName);
        intent.putExtra(AppConstants.LOCK_FROM, AppConstants.LOCK_FROM_FINISH);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        threadIsTerminate = false;
        Intent intent = new Intent(this, LockRestarterBroadcastReceiver.class);
        intent.putExtra("type", "lockservice");
        sendBroadcast(intent);
        unregisterReceiver(mServiceReceiver);
    }


    public class ServiceReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            String action = intent.getAction();

            boolean isLockOffScreen = SpUtil.getInstance().getBoolean(AppConstants.LOCK_AUTO_SCREEN, false); //是否在手机屏幕关闭后再次锁定
            boolean isLockOffScreenTime = SpUtil.getInstance().getBoolean(AppConstants.LOCK_AUTO_SCREEN_TIME, false); //是否在手机屏幕关闭后时间段后再次锁定

            switch (action) {
                case UNLOCK_ACTION:
                    lastUnlockPackageName = intent.getStringExtra(LOCK_SERVICE_LASTAPP);
                    lastUnlockTimeSeconds = intent.getLongExtra(LOCK_SERVICE_LASTTIME, lastUnlockTimeSeconds);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    SpUtil.getInstance().putLong(AppConstants.LOCK_CURR_MILLISENCONS, System.currentTimeMillis());

                    if (!isLockOffScreenTime && isLockOffScreen) {
                        String savePkgName = SpUtil.getInstance().getString(AppConstants.LOCK_LAST_LOAD_PKG_NAME, "");
                        if (!TextUtils.isEmpty(savePkgName)) {
                            if (isActionLock) {
                                mLockInfoManager.lockCommApplication(lastUnlockPackageName);
                            }
                        }
                    }
                    break;
            }
        }
    }


    @Override
    public void onTaskRemoved(Intent rootIntent){
        Intent restartServiceTask = new Intent(getApplicationContext(),this.getClass());
        restartServiceTask.setPackage(getPackageName());
        PendingIntent restartPendingIntent =PendingIntent.getService(getApplicationContext(), 1,restartServiceTask, PendingIntent.FLAG_ONE_SHOT);
        AlarmManager myAlarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        myAlarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartPendingIntent);
        super.onTaskRemoved(rootIntent);
    }
}