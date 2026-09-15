import GameServicesSample
import SwiftUI
import UIKit

struct ContentView: View {
    @StateObject private var gameServices = GameServicesController()

    var body: some View {
        VStack(spacing: 12) {
            Text(gameServices.status)
                .multilineTextAlignment(.center)
            Button("Authenticate", action: gameServices.authenticate)
            Button("Show achievements", action: gameServices.showAchievements)
            Button("Show leaderboards", action: gameServices.showLeaderboards)
            Button("Select saved game", action: gameServices.selectSavedGame)
            Button("Request friends access", action: gameServices.requestFriendsAccess)
        }
        .padding()
    }
}

@MainActor
private final class GameServicesController: ObservableObject {
    @Published private(set) var status = "Ready for a sandbox Game Center account"

    private lazy var sample = GameServicesSample_iosKt.createGameServicesSample(
        presentingViewController: { Self.presentingViewController },
    )

    func authenticate() {
        sample.services.authenticate { _, error in
            Task { @MainActor in self.status = Self.message("Authentication", error) }
        }
    }

    func showAchievements() {
        sample.achievements.showAchievements { _, error in
            Task { @MainActor in self.status = Self.message("Achievements", error) }
        }
    }

    func showLeaderboards() {
        sample.leaderboards.showLeaderboards { _, error in
            Task { @MainActor in self.status = Self.message("Leaderboards", error) }
        }
    }

    func selectSavedGame() {
        sample.savedGames.showSavedGameSelection { _, error in
            Task { @MainActor in self.status = Self.message("Saved game selection", error) }
        }
    }

    func requestFriendsAccess() {
        sample.social.requestFriendsAccess { _, error in
            Task { @MainActor in self.status = Self.message("Friends access", error) }
        }
    }

    private static var presentingViewController: UIViewController {
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap(\.windows)
            .first(where: \.isKeyWindow),
            let rootViewController = window.rootViewController
        else {
            fatalError("A presenting view controller is required")
        }
        return rootViewController.presentedViewController ?? rootViewController
    }

    nonisolated private static func message(_ action: String, _ error: Error?) -> String {
        error.map { "\(action) failed: \($0.localizedDescription)" } ?? "\(action) opened"
    }
}
