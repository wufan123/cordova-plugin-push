# cordova-plugin-push
the plugin help the cordova app get the push
##use
download the this plugin file to cordova project plugins file directory,then use cmd
```
cordova plugin add cordova-plugin-push --VARIABLE API_KEY=yourjPushkey
```
##for android 
- you must override the showNotification method in the PushManager.java to custom your notification 
- you maybe have own PushBean to define your message
