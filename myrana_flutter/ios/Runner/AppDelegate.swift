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
    UIDevice.current.isBatteryMonitoringEnabled = true

    // iOS MethodChannels — same names as Android/Dart.
    // Child monitoring uses Apple Screen Time APIs (FamilyControls / ManagedSettings /
    // DeviceActivity), NOT Android Accessibility / UsageStats.
    FlutterMethodChannel(name: "com.example.myrana/accessibility", binaryMessenger: messenger)
      .setMethodCallHandler { call, result in
        switch call.method {
        case "isEnabled":
          if #available(iOS 15.0, *) {
            result(FamilyControlsEnforcer.shared.isAuthorized)
          } else {
            result(false)
          }
        case "openSettings":
          Self.openAppSettings()
          result(true)
        case "getStatus":
          result(Self.accessibilityStatus())
        default:
          result(FlutterMethodNotImplemented)
        }
      }

    FlutterMethodChannel(name: "com.example.myrana/usage_stats", binaryMessenger: messenger)
      .setMethodCallHandler { call, result in
        switch call.method {
        case "hasPermission":
          if #available(iOS 15.0, *) {
            result(FamilyControlsEnforcer.shared.isAuthorized)
          } else {
            result(false)
          }
        case "openSettings":
          Self.openAppSettings()
          result(true)
        case "queryToday":
          // DeviceActivityReport extension needed for real usage charts.
          result([String: Int]())
        case "getStatus":
          result(Self.usageStatus())
        default:
          result(FlutterMethodNotImplemented)
        }
      }

    FlutterMethodChannel(name: "com.example.myrana/enforcement", binaryMessenger: messenger)
      .setMethodCallHandler { [weak controller] call, result in
        switch call.method {
        case "setChildContext":
          let code = (call.arguments as? [String: Any])?["childCode"] as? String ?? ""
          if #available(iOS 15.0, *) {
            FamilyControlsEnforcer.shared.setChildCode(code)
          }
          result(true)

        case "blockPackage":
          let pkg = (call.arguments as? [String: Any])?["package"] as? String ?? ""
          if #available(iOS 15.0, *) {
            result(FamilyControlsEnforcer.shared.blockPackage(pkg))
          } else {
            result(false)
          }

        case "unblockPackage":
          let pkg = (call.arguments as? [String: Any])?["package"] as? String ?? ""
          if #available(iOS 15.0, *) {
            result(FamilyControlsEnforcer.shared.unblockPackage(pkg))
          } else {
            result(false)
          }

        case "clearBlocked":
          if #available(iOS 15.0, *) {
            result(FamilyControlsEnforcer.shared.clearBlocked())
          } else {
            result(false)
          }

        case "blockHost":
          let host = (call.arguments as? [String: Any])?["host"] as? String ?? ""
          if #available(iOS 15.0, *) {
            result(FamilyControlsEnforcer.shared.blockHost(host))
          } else {
            result(false)
          }

        case "unblockHost":
          let host = (call.arguments as? [String: Any])?["host"] as? String ?? ""
          if #available(iOS 15.0, *) {
            result(FamilyControlsEnforcer.shared.unblockHost(host))
          } else {
            result(false)
          }

        case "getBlockedPackages":
          if #available(iOS 15.0, *) {
            result(FamilyControlsEnforcer.shared.blockedPackages())
          } else {
            result([String]())
          }

        case "getInstalledApps":
          // iOS has no public installed-apps inventory like Android PackageManager.
          result([[String: String]]())

        case "startForeground":
          if #available(iOS 15.0, *) {
            result(FamilyControlsEnforcer.shared.startMonitoring())
          } else {
            result(false)
          }

        case "stopForeground":
          if #available(iOS 15.0, *) {
            result(FamilyControlsEnforcer.shared.stopMonitoring())
          } else {
            result(false)
          }

        case "enforceNow":
          if #available(iOS 15.0, *) {
            result(FamilyControlsEnforcer.shared.enforceNow())
          } else {
            result(false)
          }

        case "syncPolicy":
          if #available(iOS 15.0, *) {
            FamilyControlsEnforcer.shared.syncPolicy { ok in
              result(ok)
            }
          } else {
            result(false)
          }

        case "getBatteryPct":
          result(Self.batteryPercent())

        case "isIgnoringBatteryOptimizations":
          result(true)

        case "openBatteryOptimizationSettings", "openAppSettings", "openScreenTimeSettings":
          Self.openAppSettings()
          result(true)

        case "getPlatformStatus":
          result(Self.platformStatus())

        case "requestFamilyControlsAuthorization":
          if #available(iOS 15.0, *) {
            FamilyControlsEnforcer.shared.requestAuthorization { map in
              result(map)
            }
          } else {
            result(FamilyControlsEnforcerLegacy.unavailableAuth())
          }

        case "isFamilyControlsAvailable":
          if #available(iOS 15.0, *) {
            result(FamilyControlsEnforcer.shared.isCompileEnabled)
          } else {
            result(false)
          }

        case "presentFamilyActivityPicker":
          guard let presenter = controller else {
            result(false)
            return
          }
          if #available(iOS 15.0, *) {
            FamilyControlsEnforcer.shared.presentAppPicker(from: presenter) { ok in
              result(ok)
            }
          } else {
            result(false)
          }

        default:
          result(FlutterMethodNotImplemented)
        }
      }

    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  private static func openAppSettings() {
    guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
    UIApplication.shared.open(url)
  }

  private static func batteryPercent() -> Int {
    let level = UIDevice.current.batteryLevel
    if level < 0 { return -1 }
    return Int((level * 100).rounded())
  }

  private static func accessibilityStatus() -> [String: Any] {
    let authorized: Bool
    if #available(iOS 15.0, *) {
      authorized = FamilyControlsEnforcer.shared.isAuthorized
    } else {
      authorized = false
    }
    return [
      "enabled": authorized,
      "available": true,
      "platform": "ios",
      "maps_to": "FamilyControls",
      "reason":
        "iOS has no AccessibilityService. Child shielding uses FamilyControls + ManagedSettings.",
    ]
  }

  private static func usageStatus() -> [String: Any] {
    let authorized: Bool
    if #available(iOS 15.0, *) {
      authorized = FamilyControlsEnforcer.shared.isAuthorized
    } else {
      authorized = false
    }
    return [
      "has_permission": authorized,
      "available": authorized,
      "platform": "ios",
      "maps_to": "DeviceActivity",
      "reason":
        "iOS has no UsageStats. Detailed reports need a DeviceActivityReport extension "
        + "(stub under ios/DeviceActivityMonitorExtension/).",
    ]
  }

  private static func platformStatus() -> [String: Any] {
    if #available(iOS 15.0, *) {
      return FamilyControlsEnforcer.shared.platformStatus(batteryPct: batteryPercent())
    }
    return FamilyControlsEnforcerLegacy.unavailableStatus(batteryPct: batteryPercent())
  }
}
