# cordova-plugin-push
cordova推送插件,在极光官方推送插件的基础上,在android端增加华为推送和小米推送

## 使用
```
//下载或者clone项目到cordova app工程中的plugin目录
git clone https://github.com/wufan123/cordova-plugin-push.git

//在cordova根目录使用下面命令行添加该插件
cordova plugin add cordova-plugin-push --variable API_KEY=yourjPushkey

```
然后,你需要重写`PushManager.java`中的`showNotification`方法来定制通知栏。
