import Foundation
import UIKit

#if canImport(FamilyControls) && canImport(ManagedSettings)
import FamilyControls
import ManagedSettings
import DeviceActivity
import SwiftUI
#endif

/// Screen Time enforcement for the child role on iPhone.
/// Uses real Apple APIs (FamilyControls + ManagedSettings).
/// DeviceActivityMonitor extension target must be added on Mac — see
/// ios/DeviceActivityMonitorExtension/.
@available(iOS 15.0, *)
final class FamilyControlsEnforcer {
  static let shared = FamilyControlsEnforcer()

  private let prefs = UserDefaults.standard
  private let sharedPrefs = MyranaAppGroup.defaults
  private let keyChildCode = "myrana_child_code"
  private let keyBlockedPackages = MyranaAppGroup.keyBlockedPackages
  private let keyBlockedHosts = MyranaAppGroup.keyBlockedHosts
  private let keySelection = MyranaAppGroup.keySelection
  private let keyMonitoring = "myrana_ios_monitoring_active"
  private let apiKey = "graduation-secret-key"
  private let rootUrl = "https://parental-server-4mms.onrender.com"

  #if canImport(FamilyControls) && canImport(ManagedSettings)
  private let store = ManagedSettingsStore()
  private var selection = FamilyActivitySelection()
  #endif

  private init() {
    #if canImport(FamilyControls) && canImport(ManagedSettings)
    loadSelection()
    #endif
  }

  var isCompileEnabled: Bool {
    #if canImport(FamilyControls) && canImport(ManagedSettings)
    return true
    #else
    return false
    #endif
  }

  var isAuthorized: Bool {
    #if canImport(FamilyControls) && canImport(ManagedSettings)
    return AuthorizationCenter.shared.authorizationStatus == .approved
    #else
    return false
    #endif
  }

  var authorizationStatusString: String {
    #if canImport(FamilyControls) && canImport(ManagedSettings)
    switch AuthorizationCenter.shared.authorizationStatus {
    case .approved: return "approved"
    case .denied: return "denied"
    case .notDetermined: return "not_determined"
    @unknown default: return "unknown"
    }
    #else
    return "unavailable"
    #endif
  }

  func setChildCode(_ code: String) {
    prefs.set(code, forKey: keyChildCode)
  }

  func childCode() -> String {
    (prefs.string(forKey: keyChildCode) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
  }

  /// Request Screen Time authorization (Face ID / passcode system sheet).
  func requestAuthorization(completion: @escaping ([String: Any]) -> Void) {
    #if canImport(FamilyControls) && canImport(ManagedSettings)
    Task { @MainActor in
      do {
        try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
        let approved = AuthorizationCenter.shared.authorizationStatus == .approved
        completion([
          "ok": approved,
          "status": self.authorizationStatusString,
          "message": approved
            ? "FamilyControls authorized — ManagedSettings shields can be applied."
            : "Authorization finished but status is \(self.authorizationStatusString).",
          "message_ar": approved
            ? "تم منح إذن FamilyControls — يمكن تطبيق درع ManagedSettings."
            : "انتهى الطلب والحالة: \(self.authorizationStatusString).",
        ])
      } catch {
        completion([
          "ok": false,
          "status": self.authorizationStatusString,
          "message": error.localizedDescription,
          "message_ar": "رُفض الإذن أو فشل الطلب: \(error.localizedDescription)",
        ])
      }
    }
    #else
    completion([
      "ok": false,
      "status": "unavailable",
      "message": "FamilyControls framework not available in this SDK.",
      "message_ar": "إطار FamilyControls غير متاح في هذا الـ SDK.",
    ])
    #endif
  }

  func blockPackage(_ packageName: String) -> Bool {
    let pkg = packageName.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    guard !pkg.isEmpty else { return false }
    var set = Set(blockedPackages())
    set.insert(pkg)
    saveBlockedPackages(Array(set))
    return applyShieldsFromPolicy()
  }

  func unblockPackage(_ packageName: String) -> Bool {
    let pkg = packageName.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    var set = Set(blockedPackages())
    set.remove(pkg)
    saveBlockedPackages(Array(set))
    return applyShieldsFromPolicy()
  }

  func clearBlocked() -> Bool {
    saveBlockedPackages([])
    saveBlockedHosts([])
    clearShields()
    return true
  }

  func blockHost(_ host: String) -> Bool {
    let h = host.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    guard !h.isEmpty else { return false }
    var set = Set(blockedHosts())
    set.insert(h)
    saveBlockedHosts(Array(set))
    // WebDomainToken requires FamilyActivityPicker — hosts stored for policy parity.
    return applyShieldsFromPolicy()
  }

  func unblockHost(_ host: String) -> Bool {
    let h = host.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    var set = Set(blockedHosts())
    set.remove(h)
    saveBlockedHosts(Array(set))
    return applyShieldsFromPolicy()
  }

  func blockedPackages() -> [String] {
    sharedPrefs.stringArray(forKey: keyBlockedPackages)
      ?? prefs.stringArray(forKey: keyBlockedPackages)
      ?? []
  }

  func blockedHosts() -> [String] {
    sharedPrefs.stringArray(forKey: keyBlockedHosts)
      ?? prefs.stringArray(forKey: keyBlockedHosts)
      ?? []
  }

  func startMonitoring() -> Bool {
    guard isAuthorized else { return false }
    prefs.set(true, forKey: keyMonitoring)
    _ = applyShieldsFromPolicy()
    startDeviceActivityScheduleIfPossible()
    return true
  }

  func stopMonitoring() -> Bool {
    prefs.set(false, forKey: keyMonitoring)
    #if canImport(FamilyControls) && canImport(ManagedSettings)
    DeviceActivityCenter().stopMonitoring([.myranaDaily])
    #endif
    return true
  }

  func enforceNow() -> Bool {
    applyShieldsFromPolicy()
  }

  /// Pull policy from server and apply ManagedSettings shields when tokens saved.
  func syncPolicy(completion: @escaping (Bool) -> Void) {
    let code = childCode()
    guard !code.isEmpty else {
      completion(applyShieldsFromPolicy())
      return
    }
    guard let url = URL(string: "\(rootUrl)/api/v1/devices/\(code)/policy") else {
      completion(false)
      return
    }
    var request = URLRequest(url: url)
    request.httpMethod = "GET"
    request.setValue(apiKey, forHTTPHeaderField: "X-API-KEY")
    request.timeoutInterval = 20

    URLSession.shared.dataTask(with: request) { [weak self] data, response, _ in
      guard let self = self else {
        completion(false)
        return
      }
      defer {
        DispatchQueue.main.async {
          completion(self.applyShieldsFromPolicy())
        }
      }
      guard let data = data,
            let http = response as? HTTPURLResponse,
            (200..<300).contains(http.statusCode),
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
      else { return }

      let pkgs = ((json["blockedPackages"] as? [Any]) ?? [])
        .compactMap { $0 as? String }
        .map { $0.lowercased().trimmingCharacters(in: .whitespacesAndNewlines) }
        .filter { !$0.isEmpty }
      let hosts = ((json["blockedHosts"] as? [Any]) ?? [])
        .compactMap { $0 as? String }
        .map { $0.lowercased().trimmingCharacters(in: .whitespacesAndNewlines) }
        .filter { !$0.isEmpty }
      self.saveBlockedPackages(pkgs)
      self.saveBlockedHosts(hosts)

      if let minutes = json["dailyLimitMinutes"] as? Int, minutes > 0 {
        self.sharedPrefs.set(minutes, forKey: MyranaAppGroup.keyDailyLimitMinutes)
      }
    }.resume()
  }

  /// Present Apple's FamilyActivityPicker so the child/parent selects apps to shield.
  /// Android package names cannot be mapped to ApplicationToken — tokens come from this picker.
  func presentAppPicker(from presenter: UIViewController, completion: @escaping (Bool) -> Void) {
    #if canImport(FamilyControls) && canImport(ManagedSettings)
    guard isAuthorized else {
      completion(false)
      return
    }
    let bindingSelection = selection
    let picker = FamilyActivityPickerHost(
      selection: bindingSelection,
      onCancel: {
        presenter.dismiss(animated: true) { completion(false) }
      },
      onSave: { [weak self] newSelection in
        guard let self = self else { return }
        self.selection = newSelection
        self.saveSelection()
        _ = self.applyShieldsFromPolicy()
        presenter.dismiss(animated: true) { completion(true) }
      }
    )
    let host = UIHostingController(rootView: picker)
    host.modalPresentationStyle = .formSheet
    presenter.present(host, animated: true)
    #else
    completion(false)
    #endif
  }

  func selectedTokenCounts() -> [String: Int] {
    #if canImport(FamilyControls) && canImport(ManagedSettings)
    return [
      "applications": selection.applicationTokens.count,
      "categories": selection.categoryTokens.count,
      "web_domains": selection.webDomainTokens.count,
    ]
    #else
    return ["applications": 0, "categories": 0, "web_domains": 0]
    #endif
  }

  func platformStatus(batteryPct: Int) -> [String: Any] {
    let authorized = isAuthorized
    let tokens = selectedTokenCounts()
    let appCount = tokens["applications"] ?? 0
    let catCount = tokens["categories"] ?? 0
    let webCount = tokens["web_domains"] ?? 0
    let hasTokens = appCount > 0 || catCount > 0 || webCount > 0
    let canShield = authorized && hasTokens
    return [
      "platform": "ios",
      "enforcement_available": canShield,
      "usage_stats_available": false,
      "accessibility_blocking_available": false,
      "family_controls_compile_enabled": isCompileEnabled,
      "family_controls_entitled": true,
      "family_controls_authorized": authorized,
      "family_controls_status": authorizationStatusString,
      "family_activity_apps": appCount,
      "family_activity_categories": catCount,
      "family_activity_web_domains": webCount,
      "blocked_packages_cached": blockedPackages().count,
      "blocked_hosts_cached": blockedHosts().count,
      "monitoring_active": prefs.bool(forKey: keyMonitoring),
      "shield_active": sharedPrefs.bool(forKey: MyranaAppGroup.keyShieldActive),
      "device_activity_extension": false,
      "app_group": MyranaAppGroup.suiteId,
      "parent_via_server": true,
      "child_ui_ok": true,
      "recommended_model": authorized
        ? "parent_any_child_ios_screentime"
        : "parent_ios_child_android",
      "battery_pct": batteryPct,
      "reason_ar": authorized
        ? (canShield
            ? "FamilyControls مفعّل: درع ManagedSettings يُطبَّق على التطبيقات المختارة من منتقي وقت الشاشة عند مزامنة السياسة."
            : "الإذن ممنوح — اختاري التطبيقات عبر منتقي FamilyControls ثم زامني السياسة لتفعيل الدرع.")
        : "اطلبي إذن FamilyControls (وقت الشاشة). بدون موافقة آبل + حساب مطور لا يعمل الحظر النظامي.",
      "reason_en": authorized
        ? (canShield
            ? "FamilyControls approved: ManagedSettings shields apply to picker-selected apps when policy syncs."
            : "Authorized — pick apps via FamilyActivityPicker, then sync policy to apply shields.")
        : "Request FamilyControls (Screen Time) authorization. System shielding needs Apple entitlement + approval.",
    ]
  }

  // MARK: - Private

  /// Apply ManagedSettings shield when authorized + tokens saved + server policy non-empty.
  @discardableResult
  private func applyShieldsFromPolicy() -> Bool {
    #if canImport(FamilyControls) && canImport(ManagedSettings)
    guard isAuthorized else {
      MyranaShieldApplier.setShieldActive(false)
      return false
    }
    let policyWantsBlock = !blockedPackages().isEmpty || !blockedHosts().isEmpty
    if !policyWantsBlock {
      clearShields()
      return true
    }
    // Apple tokens ≠ Android package names. Shield the FamilyActivitySelection
    // when the server policy has any blocks.
    if selection.applicationTokens.isEmpty
      && selection.categoryTokens.isEmpty
      && selection.webDomainTokens.isEmpty {
      MyranaShieldApplier.setShieldActive(false)
      return false
    }
    store.shield.applications = selection.applicationTokens.isEmpty
      ? nil
      : selection.applicationTokens
    store.shield.applicationCategories = selection.categoryTokens.isEmpty
      ? nil
      : .specific(selection.categoryTokens)
    store.shield.webDomains = selection.webDomainTokens.isEmpty
      ? nil
      : selection.webDomainTokens
    MyranaShieldApplier.setShieldActive(true)
    MyranaShieldApplier.saveSelection(selection)
    return true
    #else
    return false
    #endif
  }

  private func clearShields() {
    #if canImport(FamilyControls) && canImport(ManagedSettings)
    store.clearAllSettings()
    MyranaShieldApplier.setShieldActive(false)
    #endif
  }

  private func saveBlockedPackages(_ list: [String]) {
    prefs.set(list, forKey: keyBlockedPackages)
    sharedPrefs.set(list, forKey: keyBlockedPackages)
  }

  private func saveBlockedHosts(_ list: [String]) {
    prefs.set(list, forKey: keyBlockedHosts)
    sharedPrefs.set(list, forKey: keyBlockedHosts)
  }

  private func saveSelection() {
    #if canImport(FamilyControls) && canImport(ManagedSettings)
    MyranaShieldApplier.saveSelection(selection)
    if let data = try? JSONEncoder().encode(selection) {
      prefs.set(data, forKey: keySelection)
    }
    #endif
  }

  private func loadSelection() {
    #if canImport(FamilyControls) && canImport(ManagedSettings)
    if let shared = MyranaShieldApplier.loadSelection() {
      selection = shared
      return
    }
    guard let data = prefs.data(forKey: keySelection),
          let decoded = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data)
    else { return }
    selection = decoded
    MyranaShieldApplier.saveSelection(selection)
    #endif
  }

  private func startDeviceActivityScheduleIfPossible() {
    #if canImport(FamilyControls) && canImport(ManagedSettings)
    // Full callbacks require DeviceActivityMonitor extension target (see extension folder).
    let schedule = DeviceActivitySchedule(
      intervalStart: DateComponents(hour: 0, minute: 0),
      intervalEnd: DateComponents(hour: 23, minute: 59),
      repeats: true
    )
    var events: [DeviceActivityEvent.Name: DeviceActivityEvent] = [:]
    let limitMinutes = sharedPrefs.object(forKey: MyranaAppGroup.keyDailyLimitMinutes) as? Int ?? 120
    if !selection.applicationTokens.isEmpty || !selection.categoryTokens.isEmpty {
      events[.myranaDailyLimit] = DeviceActivityEvent(
        applications: selection.applicationTokens,
        categories: selection.categoryTokens,
        threshold: DateComponents(minute: max(1, limitMinutes))
      )
    }
    do {
      let center = DeviceActivityCenter()
      center.stopMonitoring([.myranaDaily])
      if events.isEmpty {
        try center.startMonitoring(.myranaDaily, during: schedule)
      } else {
        try center.startMonitoring(.myranaDaily, during: schedule, events: events)
      }
    } catch {
      // Extension not embedded yet — ManagedSettings shields still work without it.
      NSLog("MYRana DeviceActivity startMonitoring: \(error.localizedDescription)")
    }
    #endif
  }
}

#if canImport(FamilyControls) && canImport(ManagedSettings)
@available(iOS 15.0, *)
extension DeviceActivityName {
  static let myranaDaily = Self("myrana.daily")
}

@available(iOS 15.0, *)
extension DeviceActivityEvent.Name {
  static let myranaDailyLimit = Self("myrana.daily.limit")
}

@available(iOS 15.0, *)
private struct FamilyActivityPickerHost: View {
  @State private var selection: FamilyActivitySelection
  let onCancel: () -> Void
  let onSave: (FamilyActivitySelection) -> Void

  init(
    selection: FamilyActivitySelection,
    onCancel: @escaping () -> Void,
    onSave: @escaping (FamilyActivitySelection) -> Void
  ) {
    _selection = State(initialValue: selection)
    self.onCancel = onCancel
    self.onSave = onSave
  }

  var body: some View {
    NavigationView {
      FamilyActivityPicker(selection: $selection)
        .navigationTitle("Apps to shield")
        .toolbar {
          ToolbarItem(placement: .cancellationAction) {
            Button("Cancel") { onCancel() }
          }
          ToolbarItem(placement: .confirmationAction) {
            Button("Save") { onSave(selection) }
          }
        }
    }
  }
}
#endif

/// Fallback when running below iOS 15 (should not happen with deployment target 15).
enum FamilyControlsEnforcerLegacy {
  static func unavailableStatus(batteryPct: Int) -> [String: Any] {
    [
      "platform": "ios",
      "enforcement_available": false,
      "family_controls_compile_enabled": false,
      "family_controls_authorized": false,
      "family_controls_status": "requires_ios_15",
      "parent_via_server": true,
      "child_ui_ok": true,
      "battery_pct": batteryPct,
      "reason_ar": "FamilyControls يحتاج iOS 15 أو أحدث.",
      "reason_en": "FamilyControls requires iOS 15 or newer.",
    ]
  }

  static func unavailableAuth() -> [String: Any] {
    [
      "ok": false,
      "status": "requires_ios_15",
      "message": "FamilyControls requires iOS 15+",
      "message_ar": "FamilyControls يحتاج iOS 15 أو أحدث",
    ]
  }
}
