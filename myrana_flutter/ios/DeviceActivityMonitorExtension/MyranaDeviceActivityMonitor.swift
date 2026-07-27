import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings

/// DeviceActivityMonitor for MYRana child Screen Time.
///
/// On Mac: add this file + `../Shared/MyranaAppGroupStore.swift` to a
/// "Device Activity Monitor Extension" target (see README.md).
///
/// Until the extension is embedded:
/// - ManagedSettings shields from the main app still work when authorized
/// - These callbacks will NOT run
///
/// When embedded + App Group enabled, re-applies shields at interval start
/// and when a daily usage threshold is reached.
@available(iOS 15.0, *)
class MyranaDeviceActivityMonitor: DeviceActivityMonitor {
  private let store = ManagedSettingsStore()
  /// Must match Runner entitlements / MyranaAppGroup.suiteId
  private let suiteId = "group.com.example.myranaFlutter"
  private let keySelection = "myrana_family_activity_selection"
  private let keyShieldActive = "myrana_shield_active"

  private var sharedDefaults: UserDefaults {
    UserDefaults(suiteName: suiteId) ?? .standard
  }

  override func intervalDidStart(for activity: DeviceActivityName) {
    super.intervalDidStart(for: activity)
    _ = applySharedShields()
  }

  override func intervalDidEnd(for activity: DeviceActivityName) {
    super.intervalDidEnd(for: activity)
    if sharedDefaults.bool(forKey: keyShieldActive) {
      _ = applySharedShields()
    }
  }

  override func eventDidReachThreshold(
    _ event: DeviceActivityEvent.Name,
    activity: DeviceActivityName
  ) {
    super.eventDidReachThreshold(event, activity: activity)
    sharedDefaults.set(true, forKey: keyShieldActive)
    _ = applySharedShields()
  }

  override func intervalWillStartWarning(for activity: DeviceActivityName) {
    super.intervalWillStartWarning(for: activity)
  }

  override func intervalWillEndWarning(for activity: DeviceActivityName) {
    super.intervalWillEndWarning(for: activity)
  }

  override func eventWillReachThresholdWarning(
    _ event: DeviceActivityEvent.Name,
    activity: DeviceActivityName
  ) {
    super.eventWillReachThresholdWarning(event, activity: activity)
  }

  @discardableResult
  private func applySharedShields() -> Bool {
    guard sharedDefaults.bool(forKey: keyShieldActive) else {
      store.clearAllSettings()
      return true
    }
    guard let data = sharedDefaults.data(forKey: keySelection),
          let selection = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data)
    else { return false }

    let hasApps = !selection.applicationTokens.isEmpty
    let hasCats = !selection.categoryTokens.isEmpty
    let hasWeb = !selection.webDomainTokens.isEmpty
    guard hasApps || hasCats || hasWeb else { return false }

    store.shield.applications = hasApps ? selection.applicationTokens : nil
    store.shield.applicationCategories = hasCats ? .specific(selection.categoryTokens) : nil
    store.shield.webDomains = hasWeb ? selection.webDomainTokens : nil
    return true
  }
}
