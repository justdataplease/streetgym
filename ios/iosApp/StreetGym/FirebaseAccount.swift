import AuthenticationServices
import CryptoKit
import FirebaseAppCheck
import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import GoogleSignIn
import Security
import StreetGymApp
import UIKit

/// Firebase for the account, the leaderboard and progress analytics (Android: account/Cloud.kt, GoogleAccount.kt and the
/// Firestore calls of leaderboard/ and analytics/). The app (Kotlin, data/CloudAccount.kt and its neighbours) decides
/// what to sign in, read and write, as Android does; Swift only talks to Firebase, as Kotlin can't read its Swift API.
///
/// Firebase starts only when the app has its configuration (`GoogleService-Info.plist`, which the owner adds and git
/// never holds). Without it nothing here starts and the app shows what Android's builds without Firebase show.
enum FirebaseSetup {
    /// Configured once, at first use, before anything asks Firebase for anything.
    static let configured: Bool = {
        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path) else { return false }
        if FirebaseApp.app() == nil {
            // App Check on every Firestore call (Android: Play Integrity), set before Firebase starts.
            AppCheck.setAppCheckProviderFactory(StreetGymAppCheck())
            FirebaseApp.configure(options: options)
        }
        return FirebaseApp.app() != nil
    }()

    /// Google's sign-in needs the reversed client id as one of the app's URL types (project.yml, GOOGLE_REVERSED_CLIENT_ID);
    /// without it Google's SDK stops the app, so Google isn't offered.
    static var googleReady: Bool {
        guard configured, let clientID = FirebaseApp.app()?.options.clientID, !clientID.isEmpty else { return false }
        let reversed = clientID.split(separator: ".").reversed().joined(separator: ".")
        let types = Bundle.main.object(forInfoDictionaryKey: "CFBundleURLTypes") as? [[String: Any]] ?? []
        return types.contains { ($0["CFBundleURLSchemes"] as? [String] ?? []).contains(reversed) }
    }
}

/// App Attest on a phone, the debug provider in the Simulator and debug builds (their token goes into the console).
final class StreetGymAppCheck: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        #if DEBUG || targetEnvironment(simulator)
        return AppCheckDebugProvider(app: app)
        #else
        // App Attest where the phone has it (iOS 14 on), else DeviceCheck.
        if let attest = AppAttestProvider(app: app) { return attest }
        return DeviceCheckProvider(app: app)
        #endif
    }
}

/// Firebase Auth: an anonymous user for a username without sign-in, Google and Sign in with Apple linked to it, the
/// account asked for again before deletion, and Apple's token revoked when an Apple account is deleted (Apple 5.1.1(v)).
/// Handed over as `NativeAccount` (composeApp, iosMain, IosAccount.kt).
final class FirebaseAccount: NSObject, NativeAccount {
    private var listener: NativeAccountListener?
    private var handle: NSObjectProtocol?
    /// An account that already has a user of its own, kept for takeOver (Android: collision.updatedCredential).
    private var pending: AuthCredential?
    /// The last Sign in with Apple's code, kept to revoke Apple's token when the account is deleted.
    private var appleCode: String?
    private let apple = AppleSignIn()

    func start(listener: NativeAccountListener) {
        self.listener = listener
        let available = FirebaseSetup.configured
        listener.configured(available: available, google: FirebaseSetup.googleReady, apple: available)
        guard available else { return }
        handle = Auth.auth().addStateDidChangeListener { [weak self] _, user in self?.report(user) }
        report(Auth.auth().currentUser)
    }

    func signInAnonymously(request: Int64) {
        guard FirebaseSetup.configured else { return listener?.signedIn(request: request, uid: nil) ?? () }
        Auth.auth().signInAnonymously { [weak self] result, _ in
            self?.report(Auth.auth().currentUser)
            self?.listener?.signedIn(request: request, uid: result?.user.uid)
        }
    }

    func link(request: Int64, provider: String) {
        credential(provider) { [weak self] credential, cancelled in
            guard let self = self else { return }
            guard let credential = credential else {
                self.listener?.linked(request: request, outcome: cancelled ? "cancelled" : "failed", uid: nil)
                return
            }
            let auth = Auth.auth()
            guard let current = auth.currentUser, current.isAnonymous else {
                auth.signIn(with: credential) { result, _ in
                    self.report(auth.currentUser)
                    self.listener?.linked(request: request, outcome: result == nil ? "failed" : "linked", uid: result?.user.uid)
                }
                return
            }
            current.link(with: credential) { result, error in
                if let user = result?.user {
                    self.report(user)
                    self.listener?.linked(request: request, outcome: "linked", uid: user.uid)
                } else if let error = error as NSError?, error.code == AuthErrorCode.credentialAlreadyInUse.rawValue {
                    // The account has a user of its own: the app releases this phone's profile first, then takes over.
                    self.pending = error.userInfo["FIRAuthErrorUserInfoUpdatedCredentialKey"] as? AuthCredential ?? credential
                    self.listener?.linked(request: request, outcome: "collision", uid: current.uid)
                } else {
                    self.listener?.linked(request: request, outcome: "failed", uid: nil)
                }
            }
        }
    }

    func takeOver(request: Int64) {
        guard let credential = pending else { return listener?.signedIn(request: request, uid: nil) ?? () }
        pending = nil
        let auth = Auth.auth()
        let signIn = {
            auth.signIn(with: credential) { [weak self] result, _ in
                self?.report(auth.currentUser)
                self?.listener?.signedIn(request: request, uid: result?.user.uid)
            }
        }
        // Best effort: nothing is left on the anonymous user, and signing in replaces it anyway.
        if let anonymous = auth.currentUser, anonymous.isAnonymous {
            anonymous.delete { _ in signIn() }
        } else {
            signIn()
        }
    }

    func dropTakeOver() {
        pending = nil
    }

    func reauthenticate(request: Int64) {
        guard FirebaseSetup.configured, let user = Auth.auth().currentUser, !user.isAnonymous else {
            return listener?.finished(request: request, ok: false) ?? ()
        }
        credential(FirebaseAccount.provider(of: user) ?? "google.com") { [weak self] credential, _ in
            guard let credential = credential else { return self?.listener?.finished(request: request, ok: false) ?? () }
            user.reauthenticate(with: credential) { _, error in
                self?.listener?.finished(request: request, ok: error == nil)
            }
        }
    }

    func signOut() {
        guard FirebaseSetup.configured else { return }
        try? Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
        report(nil)
    }

    func deleteUser(request: Int64) {
        guard FirebaseSetup.configured, let user = Auth.auth().currentUser else {
            return listener?.finished(request: request, ok: false) ?? ()
        }
        let delete = {
            user.delete { [weak self] error in
                self?.report(Auth.auth().currentUser)
                self?.listener?.finished(request: request, ok: error == nil)
            }
        }
        // Sign in with Apple: its token is revoked first, as Apple asks of an app that deletes accounts.
        if let code = appleCode, FirebaseAccount.provider(of: user) == "apple.com" {
            appleCode = nil
            Auth.auth().revokeToken(withAuthorizationCode: code) { _ in delete() }
        } else {
            delete()
        }
    }

    func deleteAnonymous(uid: String) {
        guard FirebaseSetup.configured, let user = Auth.auth().currentUser, user.isAnonymous, user.uid == uid else { return }
        user.delete { [weak self] _ in self?.report(Auth.auth().currentUser) }
    }

    /// The app's URL handler (SwiftUI's onOpenURL): Google's sign-in coming back. False for any other link.
    static func handle(_ url: URL) -> Bool {
        guard FirebaseSetup.configured, url.scheme != "streetgym" else { return false }
        return GIDSignIn.sharedInstance.handle(url)
    }

    // MARK: - Credentials

    /// Google's or Apple's credential from its sign-in sheet; nil when cancelled ([cancelled] true) or failed.
    private func credential(_ provider: String, done: @escaping (AuthCredential?, Bool) -> Void) {
        guard FirebaseSetup.configured, let presenter = FirebaseAccount.topController() else { return done(nil, false) }
        if provider == "apple.com" {
            let nonce = AppleSignIn.randomNonce()
            apple.start(nonce: AppleSignIn.sha256(nonce)) { [weak self] appleCredential, cancelled in
                guard let appleCredential = appleCredential, let token = appleCredential.identityToken,
                      let idToken = String(data: token, encoding: .utf8) else { return done(nil, cancelled) }
                self?.appleCode = appleCredential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
                done(OAuthProvider.appleCredential(withIDToken: idToken, rawNonce: nonce, fullName: appleCredential.fullName), false)
            }
            return
        }
        guard FirebaseSetup.googleReady, let clientID = FirebaseApp.app()?.options.clientID else { return done(nil, false) }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let error = error as NSError? {
                // Google's own "cancelled" (GIDSignInError canceled, -5).
                return done(nil, error.domain == "com.google.GIDSignIn" && error.code == -5)
            }
            guard let user = result?.user, let idToken = user.idToken?.tokenString else { return done(nil, false) }
            done(GoogleAuthProvider.credential(withIDToken: idToken, accessToken: user.accessToken.tokenString), false)
        }
    }

    /// Whoever is signed in now, to the app.
    private func report(_ user: User?) {
        guard let user = user else {
            listener?.userChanged(uid: nil, anonymous: false, email: nil, provider: nil)
            return
        }
        listener?.userChanged(uid: user.uid, anonymous: user.isAnonymous, email: user.email ?? user.providerData.first?.email,
                              provider: FirebaseAccount.provider(of: user))
    }

    private static func provider(of user: User) -> String? {
        user.providerData.map(\.providerID).first { $0 == "google.com" || $0 == "apple.com" }
    }

    static func topController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController ?? scene?.windows.first?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

/// Sign in with Apple's sheet (AuthenticationServices), with the hashed nonce Firebase checks.
final class AppleSignIn: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var done: ((ASAuthorizationAppleIDCredential?, Bool) -> Void)?
    private var controller: ASAuthorizationController?

    func start(nonce: String, done: @escaping (ASAuthorizationAppleIDCredential?, Bool) -> Void) {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = nonce
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        self.done = done
        self.controller = controller
        controller.performRequests()
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        finish(authorization.credential as? ASAuthorizationAppleIDCredential, cancelled: false)
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        finish(nil, cancelled: (error as? ASAuthorizationError)?.code == .canceled)
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        FirebaseAccount.topController()?.view.window ?? ASPresentationAnchor()
    }

    private func finish(_ credential: ASAuthorizationAppleIDCredential?, cancelled: Bool) {
        let done = self.done
        self.done = nil
        controller = nil
        done?(credential, cancelled)
    }

    /// A random nonce of URL-safe characters (Firebase's Sign in with Apple guide).
    static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        _ = SecRandomCopyBytes(kSecRandomDefault, length, &bytes)
        return String(bytes.map { charset[Int($0) % charset.count] })
    }

    static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

/// Firestore: whole documents by path as JSON (common Kotlin's CloudJson), read from the server, written in batches,
/// and one transaction whose writes the app plans from what it read. Handed over as `NativeCloud`.
final class FirestoreDocuments: NSObject, NativeCloud {
    private var listener: NativeCloudListener?

    func attach(listener: NativeCloudListener) {
        self.listener = listener
    }

    func read(request: Int64, paths: [String]) {
        guard FirebaseSetup.configured else { return listener?.readDone(request: request, documents: nil) ?? () }
        let db = Firestore.firestore()
        let group = DispatchGroup()
        var documents: [String: Any] = [:]
        var failed = false
        for path in paths {
            group.enter()
            db.document(path).getDocument(source: .server) { snapshot, error in
                if let snapshot = snapshot, error == nil {
                    documents[path] = snapshot.exists ? FirestoreDocuments.plain(snapshot.data() ?? [:]) : NSNull()
                } else {
                    failed = true
                }
                group.leave()
            }
        }
        group.notify(queue: .main) { [weak self] in
            self?.listener?.readDone(request: request, documents: failed ? nil : FirestoreDocuments.json(documents))
        }
    }

    func write(request: Int64, writes: String) {
        guard FirebaseSetup.configured, let list = FirestoreDocuments.parse(writes) as? [[String: Any]] else {
            return listener?.writeDone(request: request, ok: false) ?? ()
        }
        let db = Firestore.firestore()
        let batch = db.batch()
        for write in list {
            guard let path = write["path"] as? String else { continue }
            let reference = db.document(path)
            if write["op"] as? String == "delete" {
                batch.deleteDocument(reference)
            } else {
                let fields = FirestoreDocuments.values(write["fields"] as? [String: Any] ?? [:])
                if let merge = write["merge"] as? [String] {
                    batch.setData(fields, forDocument: reference, mergeFields: merge)
                } else {
                    batch.setData(fields, forDocument: reference)
                }
            }
        }
        batch.commit { [weak self] error in
            DispatchQueue.main.async { self?.listener?.writeDone(request: request, ok: error == nil) }
        }
    }

    func transaction(request: Int64, paths: [String]) {
        guard FirebaseSetup.configured, let listener = listener else { return self.listener?.transactionDone(request: request, outcome: nil) ?? () }
        let db = Firestore.firestore()
        db.runTransaction({ (transaction, errorPointer) -> Any? in
            var documents: [String: Any] = [:]
            for path in paths {
                do {
                    let snapshot = try transaction.getDocument(db.document(path))
                    documents[path] = snapshot.exists ? FirestoreDocuments.plain(snapshot.data() ?? [:]) : NSNull()
                } catch let error as NSError {
                    errorPointer?.pointee = error
                    return nil
                }
            }
            // The app plans the writes from what was read (Profiles.plan: the username's claim).
            let planned = FirestoreDocuments.parse(listener.decide(request: request, documents: FirestoreDocuments.json(documents)))
            guard let plan = planned as? [String: Any], let outcome = plan["outcome"] as? String, !outcome.isEmpty else {
                errorPointer?.pointee = NSError(domain: "StreetGym", code: 1)
                return nil
            }
            for write in plan["writes"] as? [[String: Any]] ?? [] {
                guard let path = write["path"] as? String else { continue }
                let reference = db.document(path)
                if write["op"] as? String == "delete" {
                    transaction.deleteDocument(reference)
                } else {
                    let fields = FirestoreDocuments.values(write["fields"] as? [String: Any] ?? [:])
                    if let merge = write["merge"] as? [String] {
                        transaction.setData(fields, forDocument: reference, mergeFields: merge)
                    } else {
                        transaction.setData(fields, forDocument: reference)
                    }
                }
            }
            return outcome
        }) { result, error in
            DispatchQueue.main.async { listener.transactionDone(request: request, outcome: error == nil ? result as? String : nil) }
        }
    }

    // MARK: - Values

    /// The app's JSON values as Firestore's: {"$server":"time"}, {"$server":"delete"}, {"$time": millis}.
    static func values(_ fields: [String: Any]) -> [String: Any] {
        fields.mapValues(value)
    }

    private static func value(_ item: Any) -> Any {
        if let object = item as? [String: Any] {
            if let server = object["$server"] as? String, object.count == 1 {
                return server == "delete" ? FieldValue.delete() : FieldValue.serverTimestamp()
            }
            if let millis = object["$time"] as? NSNumber, object.count == 1 {
                return Timestamp(date: Date(timeIntervalSince1970: millis.doubleValue / 1000))
            }
            return object.mapValues(value)
        }
        if let list = item as? [Any] { return list.map(value) }
        return item
    }

    /// Firestore's values as JSON: a timestamp as {"$time": millis}.
    static func plain(_ item: Any) -> Any {
        if let timestamp = item as? Timestamp { return ["$time": Int64(timestamp.dateValue().timeIntervalSince1970 * 1000)] }
        if let object = item as? [String: Any] { return object.mapValues(plain) }
        if let list = item as? [Any] { return list.map(plain) }
        if item is NSNull || item is String || item is NSNumber { return item }
        return String(describing: item)
    }

    static func json(_ object: Any) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: object), let text = String(data: data, encoding: .utf8) else { return "{}" }
        return text
    }

    static func parse(_ text: String) -> Any? {
        try? JSONSerialization.jsonObject(with: Data(text.utf8), options: [.fragmentsAllowed])
    }
}
