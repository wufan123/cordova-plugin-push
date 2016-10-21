package org.apache.cordova.pushlib;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.LruCache;

import com.evideo.push.service.DdpushInterface;
import com.google.gson.Gson;
import com.xiaomi.mipush.sdk.MiPushClient;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import cn.jpush.android.api.JPushInterface;

/**
 * Created by User on 2016/9/26.
 */
public final class PushManager extends CordovaPlugin
{

    public static final int PUSH_CHANNEL_JPUSH = 0x01;

    public static final int PUSH_CHANNEL_DDPUSH = 0x02;

    public static final int PUSH_CHANNEL_HWPUSH = 0x03;

    public static final int PUSH_CHANNEL_MIPUSH = 0x04;

    public static final int PUSH_DATA_TYPE = 0x01;

    public static final String DEFAULT_SERVER_ADDR = "ezhan-push.jahome.net";// dd push

    public static final int DEFAULT_SERVER_PORT = 19966;//dd push

    public static final int DEFAULT_APP_ID = 2;// dd push

    private static final String TAG = "PushManager";

    private static String MI_APP_ID = "2882303761517503033";//mi push app id

    private static String MI_APP_KEY = "5141750359033";// mi push  app key


    public static String cid;

    private static CallbackContext callbackContext;

    private static String uuid;

    private static String brand;

    private static String sysVersion;

    private static Object romVersion;

    public static LruCache<String, PushBean> PUSH_DATA_LRU = new LruCache<String, PushBean>(20);//storage push data

    public static String EX_PUSH_DATA = null;//push data to handle by load page finish

    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException
    {

        LOG.d(TAG, "execute action :" + action);
        if (action.equals("initial"))
        {
            cordova.getThreadPool().execute(new Runnable()
            {

                @Override
                public void run()
                {

                    init(cordova.getActivity(), args, callbackContext);
                    handleExPushdata();
                }
            });
            return true;
        }
        return super.execute(action, args, callbackContext);
    }

    private void handleExPushdata()
    {

        if (EX_PUSH_DATA != null)//update the ex push data
        {
            LOG.d(TAG, "handle the exPushdata,the data is : " + EX_PUSH_DATA);
            cordova.getActivity().runOnUiThread(new Runnable()
            {

                @Override
                public void run()
                {

                    upDatePushData(EX_PUSH_DATA);
                    LOG.d(TAG, "the exPushdata had update to page,reset null");
                    EX_PUSH_DATA = null;
                }
            });
        }
    }

    @Override
    public void onNewIntent(Intent intent)
    {

        super.onNewIntent(intent);
        String pushData = intent.getStringExtra("pushData");
        upDatePushData(pushData);

    }

    private void upDatePushData(String pushData)
    {

        String format = "window.plugins.jPushPlugin.receiveMessage(%s);";
        final String js = String.format(format, pushData);
        LOG.d(TAG, "load js method : " + js);
        webView.loadUrl("javascript:" + js);
    }

    public static void init(Context context, JSONArray args, CallbackContext callbackContext)
    {
        //init some files
        PushManager.callbackContext = callbackContext;
        uuid = DeviceUtils.getDeviceId(context);
        brand = DeviceUtils.getCurSys();
        //        brand = "VIVO";//test jpush
        sysVersion = DeviceUtils.getSysVersion();
        romVersion = null;
        //register ddpush
        LOG.d(TAG, "init ddpush");
        registerDdPush(context, args);
        //change push by os
        if (DeviceUtils.isHuaWei())
        {
            sendPushID(new Hwpush(null));//update ddpush client info to sever advance,in case other push register fail
            LOG.d(TAG, "init Huawei push");
            PushManager.registerHwPush(context);
        }
        else if (DeviceUtils.isXiaoMi())
        {
            sendPushID(new Mipush(null));//update ddpush client info to sever advance,in case other push register fail
            LOG.d(TAG, "init xiao mi push");
            PushManager.registerMiPush(context);
        }
        else
        {
            sendPushID(new JPush(null));//update ddpush client info to sever advance,in case other push register fail
            LOG.d(TAG, "init j push");
            PushManager.registerJPush(context);
        }

    }

    public static void registerJPush(Context context)
    {

        JPushInterface.init(context);
        LOG.e(TAG, "" + JPushInterface.isPushStopped(context));
        String rid = JPushInterface.getRegistrationID(context);
        sendPushID(new JPush(rid));
    }

    public static void unRegisterJPush(Context context)
    {

        JPushInterface.stopPush(context);
    }

    public static void registerDdPush(Context context, JSONArray args)
    {

        String serverAddr = DEFAULT_SERVER_ADDR;
        int serverPort = DEFAULT_SERVER_PORT;
        int appId = DEFAULT_APP_ID;
        try
        {
            cid = DeviceUtils.getUUID(context, args.getString(0));
        }
        catch (JSONException e)
        {
            LOG.e(TAG, "args is null,cant get phoneNum");
        }
        DdpushInterface.initService(context, serverAddr, serverPort, appId, cid);
    }

    public static void unRegisterDdPush(Context context)
    {

        DdpushInterface.stopService(context);
    }

    public static void registerHwPush(Context context)
    {

        com.huawei.android.pushagent.api.PushManager.requestToken(context);
    }

    public static void unRegisterHwPush(Context context)
    {
        //TODO
    }

    public static void registerMiPush(Context context)
    {

        String appId = MI_APP_ID;
        String appKey = MI_APP_KEY;
        MiPushClient.checkManifest(context);
        MiPushClient.registerPush(context, appId, appKey);
    }

    public static void unRegisterMiPush(Context context)
    {

        MiPushClient.unregisterPush(context);
    }

    public static void resolvePushData(Context context, int channel, String data, String title)
    {

        PushBean pushBean;
        try
        {
            pushBean = new Gson().fromJson(data, PushBean.class);
            if (PUSH_DATA_LRU.get(data) == null)
            {
                LOG.d(TAG, "this push is fresh,show the notification,the push data : " + data);
                PUSH_DATA_LRU.put(data, pushBean);
                showNotification(context, pushBean);
            }
            else
            {
                LOG.d(TAG, "this push had show to user,abort it");
            }

        }
        catch (Exception e)
        {
            LOG.d(TAG, "resolve Push erro,the exception is " + e.getMessage());
            return;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static void showNotification(Context context, PushBean pushBean)
    {
        //custom notification
        /*NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        CharSequence appName = context.getResources().getString(R.string.app_name);
        Bitmap appIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo);
        Notification.Builder mBuilder = new Notification.Builder(context);
        mBuilder.setLargeIcon(appIcon).setSmallIcon(R.drawable.small_logo).setContentTitle(pushBean.title).setContentText((pushBean.desc == null) ? appName : pushBean.desc).setTicker(appName).setAutoCancel(true).setWhen(System.currentTimeMillis()).setDefaults(Notification.DEFAULT_SOUND);

        Notification notification = mBuilder.build();
        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.putExtra("pushData", new Gson().toJson(pushBean));
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        notification.contentIntent = contentIntent;
        mNotificationManager.notify(pushBean.type, notification);*/
    }

    public static void sendPushID(Object push)
    {

        PushClient client = new PushClient();
        client.uuid = uuid;
        client.brand = brand;
        client.sysVersion = sysVersion;
        client.romVersion = null;
        client.os = 0;
        if (push instanceof JPush)
        {
            client.androidSys = 0;
            client.jPush = ((JPush) push);
        }
        else if (push instanceof Hwpush)
        {
            client.androidSys = 2;
            client.hwPush = ((Hwpush) push);
        }
        else if (push instanceof Mipush)
        {
            client.androidSys = 1;
            client.miPush = ((Mipush) push);
        }
        client.ddPush = new DdPush(cid);
        String json = new Gson().toJson(client);
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, json);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
    }

    static class PushClient
    {

        String uuid;

        String brand;

        DdPush ddPush;

        JPush jPush;

        Hwpush hwPush;

        Mipush miPush;

        String sysVersion;

        int os;

        String romVersion;

        int androidSys;
    }

    static class DdPush
    {

        String cid;

        public DdPush(String cid)
        {

            this.cid = cid;
        }
    }

    static class JPush
    {

        String regId;

        public JPush(String regId)
        {

            this.regId = regId;
        }
    }

    public static class Hwpush
    {

        String token;

        public Hwpush(String token)
        {

            this.token = token;
        }
    }

    public static class Mipush
    {

        String regId;

        public Mipush(String regId)
        {

            this.regId = regId;
        }
    }

    public static class PushBean
    {

        int type;

        String id;

        String title;

        String time;

        String desc;

        Detail detail;

        static class Detail
        {

            String id;
        }
    }

}
