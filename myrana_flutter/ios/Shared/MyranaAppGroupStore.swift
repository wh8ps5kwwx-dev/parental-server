import Foundation

#if canImport(FamilyControls) && canImport(ManagedSettings)
import FamilyControls
import ManagedSettings
#endif

/// Shared App Group keys between Runner and DeviceActivityMonitorExtension.
/// Keep suite id identical in both targets' entitlements.
enum MyranaAppGroup {
  static let suiteId = "group.com.example.myranaFlutter"
  static let keySelection = "myrana_family_activity_selection"
  static let keyShieldActive = "myrana_shield_active"
  static let keyBlockedPackages = "myrana_blocked_packages"
  static let keyBlockedHosts = "myrana_blocked_hosts"
  static let keyDailyLimitMinutes = "myrana_daily_limit_minutes"

  static var defaults: UserDefaults {
    UserDefaults(suiteName: suiteId) ?? .standard
  }
}

#if canImport(FamilyControls) && canImport(ManagedSettings)
/// Apply / clear ManagedSettings shields from App Group state.
/// Used by the main app and by the DeviceActivityMonitor extension.
@available(iOS 15.0, *)
enum MyranaShieldApplier {
  static func loadSelection(from defaults: UserDefaults = MyranaAppGroup.defaults) -> FamilyActivitySelection? {
    guard let data = defaults.data(forKey: MyranaAppGroup.keySelection),
          let decoded = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data)
    else { return nil }
    return decoded
  }

  static func saveSelection(
    _ selection: FamilyActivitySelection,
    to defaults: UserDefaults = MyranaAppGroup.defaults
  ) {
    if let data = try? JSONEncoder().encode(selection) {
      defaults.set(data, forKey: MyranaAppGroup.keySelection)
    }
  }

  static func setShieldActive(_ active: Bool, defaults: UserDefaults = MyranaAppGroup.defaults) {
    defaults.set(active, forKey: MyranaAppGroup.keyShieldActive)
  }

  static var isShieldActive: Bool {
    MyranaAppGroup.defaults.bool(forKey: MyranaAppGroup.keyShieldActive)
  }

  @discardableResult
  static func applyFromSharedState(
    store: ManagedSettingsStore = ManagedSettingsStore()
  ) -> Bool {
    guard isShieldActive else {
      store.clearAllSettings()
      return true
    }
    guard let selection = loadSelection() else { return false }
    let hasApps = !selection.applicationTokens.isEmpty
    let hasCats = !selection.categoryTokens.isEmpty
    let hasWeb = !selection.webDomainTokens.isEmpty
    guard hasApps || hasCats || hasWeb else { return false }

    store.shield.applications = hasApps ? selection.applicationTokens : nil
    store.shield.applicationCategories = hasCats ? .specific(selection.categoryTokens) : nil
    store.shield.webDomains = hasWeb ? selection.webDomainTokens : nil
    return true
  }

  static func clearShields(store: ManagedSettingsStore = ManagedSettingsStore()) {
    store.clearAllSettings()
  }
}
#endif
