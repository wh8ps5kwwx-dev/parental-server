import Flutter
import UIKit

@UIApplicationMain
@objc class AppDelegate: FlutterAppDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    GeneratedPluginRegistrant.register(with: self)

    guard let controller = window?.rootViewController as? FlutterViewController else {
      return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }

    let messenger = controller.binaryMessenger

    // iOS stub — نفس أسماء القنوات في Android/Dart.
    // Apple لا تسمح بحظر التطبيقات أو Usage Stats لطرف ثالث مثل أندرويد.
    FlutterMethodChannel(name: "com.example.myrana/accessibility", binaryMessenger: messenger)
      .setMethodCallHandler { call, result in
        switch call.method {
        case "isEnabled": result(false)
        case "openSettings":
          if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
          }
          result(nil)
        default: result(FlutterMethodNotImplemented)
        }
      }

    FlutterMethodChannel(name: "com.example.myrana/usage_stats", binaryMessenger: messenger)
      .setMethodCallHandler { call, result in
        switch call.method {
        case "hasPermission": result(false)
        case "openSettings":
          if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
          }
          result(nil)
        case "queryToday": result([String: Int]())
        default: result(FlutterMethodNotImplemented)
        }
      }

    FlutterMethodChannel(name: "com.example.myrana/enforcement", binaryMessenger: messenger)
      .setMethodCallHandler { call, result in
        switch call.method {
        case "blockPackage", "unblockPackage", "clearBlocked",
             "startForeground", "stopForeground", "enforceNow":
          result(false)
        case "getBlockedPackages": result([String]())
        case "getInstalledApps": result([[String: String]]())
        default: result(FlutterMethodNotImplemented)
        }
      }

    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
}
