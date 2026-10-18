import SwiftUI

@main
struct ListenAndWriteApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        NavigationStack(path: $model.path) {
            SubjectGateView()
                .navigationDestination(for: AppRoute.self) { route in
                    switch route {
                    case let .subject(kind):
                        SubjectHomeView(kind: kind)
                    case .drills:
                        DrillListView()
                    case let .session(request):
                        SessionView(request: request)
                    case .summary:
                        SummaryView()
                    case .grade:
                        GradeView()
                    case let .mistakes(lang):
                        MistakeListView(lang: lang)
                    case .settings:
                        SettingsView()
                    }
                }
        }
        .safeAreaInset(edge: .bottom) {
            if model.sessionMinimized, model.sessionRunning {
                MiniSessionBar()
            }
        }
    }
}

struct MiniSessionBar: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(model.sessionTitle).font(.headline)
                Text("第 \(min(model.sessionIndex + 1, max(model.sessionWords.count, 1))) / \(model.sessionWords.count) 个 · \(model.sessionPhase)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            Button("展开") { model.expandSession() }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("expand-session")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.ultraThinMaterial)
    }
}

enum AppRoute: Hashable {
    case subject(SubjectKind)
    case drills
    case session(SessionRequest)
    case summary
    case grade
    case mistakes(Lang)
    case settings
}

struct SessionRequest: Hashable {
    var title: String
    var drill: Bool
    var mode: PlayMode
    var wordIDs: [String]
}

struct SubjectGateView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        VStack(spacing: 20) {
            Text("先选一科")
                .font(.largeTitle.bold())
            Text("语文和英语分开听写。读的时候不用看手机。")
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            ForEach(SubjectKind.allCases) { kind in
                Button {
                    model.openSubject(kind)
                } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(kind.title).font(.title.bold())
                        Text(kind == .chinese ? "按课听写，会讲易混字。" : "认读报英文。默写听中文写英文。")
                            .font(.body)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                }
                .buttonStyle(.borderedProminent)
                .tint(kind == .chinese ? .teal : .indigo)
                .accessibilityIdentifier(kind == .chinese ? "subject-chinese" : "subject-english")
            }
            Button("设置") { model.path.append(.settings) }
                .padding(.top, 8)
        }
        .padding(24)
        .navigationTitle("听写助手")
    }
}
