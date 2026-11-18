import StoreKit
import StreetGymApp
import UIKit

/// Plus and Pro through StoreKit 2 (Android: billing/TierBilling.kt on Play Billing). Swift talks to the App Store; the
/// app (Kotlin, composeApp) decides what each answer means and caches the plan (data/Plans.kt), as Kotlin can't read
/// StoreKit 2's Swift API. Handed over as `NativeStore` (composeApp, iosMain, IosStore.kt).
///
/// Only verified transactions count. `Transaction.currentEntitlements` is the complete answer: a refund or a lapsed
/// yearly Pro drops out of it. Every answer goes to the listener on the main thread.
final class StoreKitStore: NSObject, NativeStore {
    /// Android's product ids (TierBilling.PLUS, PRO_LIFE and PRO), the same in App Store Connect and Products.storekit.
    private static let productIDs = ["plus", "pro_life", "pro"]

    private var listener: NativeStoreListener?
    private var products: [String: Product] = [:]
    private var updates: Task<Void, Never>?

    func start(listener: NativeStoreListener) {
        self.listener = listener
        // What arrives outside a purchase: Ask to Buy approved, an offer code, a renewal, a refund, another device.
        updates = Task { @MainActor [weak self] in
            for await result in Transaction.updates {
                if case .verified(let transaction) = result {
                    await transaction.finish()
                }
                await self?.readEntitlements()
            }
        }
        refresh()
    }

    func refresh() {
        Task { @MainActor in
            if self.products.isEmpty {
                await self.loadProducts()
            }
            await self.readEntitlements()
        }
    }

    func buy(productId: String) {
        Task { @MainActor in
            guard let product = self.products[productId] else {
                self.listener?.failed(productId: productId)
                return
            }
            do {
                let result: Product.PurchaseResult
                if #available(iOS 17.0, *), let scene = StoreKitStore.activeScene() {
                    result = try await product.purchase(confirmIn: scene)
                } else {
                    result = try await product.purchase()
                }
                switch result {
                case .success(let verification):
                    if case .verified(let transaction) = verification {
                        await transaction.finish()
                        await self.readEntitlements()
                        self.listener?.purchased(productId: productId)
                    } else {
                        self.listener?.failed(productId: productId)
                    }
                case .pending:
                    self.listener?.pending(productId: productId)
                case .userCancelled:
                    self.listener?.cancelled(productId: productId)
                @unknown default:
                    self.listener?.failed(productId: productId)
                }
            } catch {
                self.listener?.failed(productId: productId)
            }
        }
    }

    /// Restore purchases: Apple asks to sync only on the athlete's tap, as it may ask them to sign in.
    func restore() {
        Task { @MainActor in
            do {
                try await AppStore.sync()
                await self.readEntitlements()
                self.listener?.restoreEnded(reached: true)
            } catch {
                self.listener?.restoreEnded(reached: false)
            }
        }
    }

    /// Apple's offer-code sheet; what it redeems arrives through `Transaction.updates`.
    func redeemCode() {
        Task { @MainActor in
            guard let scene = StoreKitStore.activeScene() else {
                self.listener?.redeemEnded(opened: false)
                return
            }
            do {
                try await AppStore.presentOfferCodeRedeemSheet(in: scene)
                self.listener?.redeemEnded(opened: true)
            } catch {
                self.listener?.redeemEnded(opened: false)
            }
        }
    }

    func manageSubscriptions() {
        Task { @MainActor in
            guard let scene = StoreKitStore.activeScene() else { return }
            try? await AppStore.showManageSubscriptions(in: scene)
            await self.readEntitlements()
        }
    }

    @MainActor private func loadProducts() async {
        do {
            let found = try await Product.products(for: StoreKitStore.productIDs)
            products = Dictionary(uniqueKeysWithValues: found.map { ($0.id, $0) })
        } catch {
            products = [:]
        }
        var prices: [String: String] = [:]
        for (id, product) in products {
            prices[id] = product.displayPrice
        }
        listener?.productsLoaded(prices: prices)
    }

    @MainActor private func readEntitlements() async {
        var owned: [String] = []
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result, transaction.revocationDate == nil,
               StoreKitStore.productIDs.contains(transaction.productID) {
                owned.append(transaction.productID)
            }
        }
        listener?.entitlementsRead(owned: owned)
    }

    /// The scene the app shows in, for Apple's sheets.
    static func activeScene() -> UIWindowScene? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
    }
}
