import PhotosUI
import SwiftUI
import Vision

struct SubjectHomeView: View {
    @EnvironmentObject private var model: AppModel
    let kind: SubjectKind
    @State private var selected: Set<Int> = []
    @State private var random = false

    var body: some View {
        let rows = kind == .chinese ? model.chineseRows() : model.englishRows()
        let last = kind == .chinese ? model.lastChinese : model.lastRead
        List {
            if !last.units.isEmpty {
                Section {
                    Button {
                        selected = last.units
                        random = last.random
                        begin(mode: last.mode)
                    } label: {
                        VStack(alignment: .leading) {
                            Text(kind == .chinese ? "继续听写" : "继续认读").font(.title3.bold())
                            Text(playCaption(last, chinese: kind == .chinese, withCue: kind == .english))
                            Text("听到「嘀」就写，不用看手机。").font(.footnote)
                        }
                    }
                }
            }
            if kind == .english {
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("默写").font(.title3.bold())
                        Text("听中文，在纸上写英文。播放时不显示答案。")
                        if !model.lastDrill.units.isEmpty {
                            Text(playCaption(model.lastDrill, chinese: false, withCue: true))
                            Button("继续默写") {
                                let words = model.drillWords.filter { model.lastDrill.units.contains(unitSlot($0.unit)) }
                                model.start(words: words, title: "默写练习", drill: true, units: model.lastDrill.units, mode: model.lastDrill.mode, random: model.lastDrill.random)
                            }
                        }
                        Button("选单元") { model.path.append(.drills) }
                    }
                }
                Section("认读") {
                    Text("这里会报出英文，用来认单词。")
                    cuePicker
                }
            }
            Section {
                ForEach(rows) { row in
                    HStack {
                        Button {
                            if selected.contains(row.id) { selected.remove(row.id) } else { selected.insert(row.id) }
                        } label: {
                            Image(systemName: selected.contains(row.id) ? "checkmark.square.fill" : "square")
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("unit-\(row.id)")
                        VStack(alignment: .leading) {
                            Text(row.title)
                            Text(row.count == 0 ? "空" : "\(row.count) 条").font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
            }
            Section {
                Button("错题本") { model.path.append(.mistakes(kind == .chinese ? .zh : .en)) }
            }
        }
        .navigationTitle(kind.title)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            PlayBar(
                count: model.words(kind: kind, units: selected).count,
                primary: "定时\(kind == .english ? "认读" : "听写")",
                secondary: kind == .english ? "说完再认读" : "说完再继续",
                primaryID: "start-timer",
                onPrimary: { begin(mode: .timer) },
                onSecondary: { begin(mode: .voice) }
            )
        }
        .onAppear {
            let last = kind == .chinese ? model.lastChinese : model.lastRead
            if selected.isEmpty { selected = last.units }
        }
    }

    private var cuePicker: some View {
        Picker("怎么报", selection: $model.settings.readCue) {
            ForEach(EnglishCue.allCases) { cue in
                Text(cue.label).tag(cue)
            }
        }
        .pickerStyle(.segmented)
    }

    private func begin(mode: PlayMode) {
        let words = model.words(kind: kind, units: selected)
        guard !words.isEmpty else { return }
        let title = kind == .chinese ? "听写" : "认读"
        model.start(words: words, title: title, drill: false, units: selected, mode: mode, random: random)
    }
}

struct DrillListView: View {
    @EnvironmentObject private var model: AppModel
    @State private var selected: Set<Int> = []

    var body: some View {
        List {
            Section {
                Text("默认只报中文。第 3 单元没有印答案，是按词表补的。")
                Picker("怎么报", selection: $model.settings.drillCue) {
                    ForEach(EnglishCue.allCases) { cue in
                        Text(cue.label).tag(cue)
                    }
                }
                .pickerStyle(.segmented)
            }
            Section {
                ForEach(model.drillRows()) { row in
                    Button {
                        if selected.contains(row.id) { selected.remove(row.id) } else { selected.insert(row.id) }
                    } label: {
                        HStack {
                            Image(systemName: selected.contains(row.id) ? "checkmark.square.fill" : "square")
                            Text(row.title)
                            Spacer()
                            Text(row.count == 0 ? "空" : "\(row.count) 条")
                        }
                    }
                }
            }
        }
        .navigationTitle("默写练习")
        .safeAreaInset(edge: .bottom, spacing: 0) {
            PlayBar(
                count: model.drillWords.filter { selected.contains(unitSlot($0.unit)) }.count,
                primary: "定时默写",
                secondary: "说完再默写",
                primaryID: "start-drill-timer",
                onPrimary: { begin(.timer) },
                onSecondary: { begin(.voice) }
            )
        }
        .onAppear {
            if selected.isEmpty { selected = model.lastDrill.units }
        }
    }

    private func begin(_ mode: PlayMode) {
        let words = model.drillWords.filter { selected.contains(unitSlot($0.unit)) }
        guard !words.isEmpty else { return }
        model.start(words: words, title: "默写练习", drill: true, units: selected, mode: mode, random: false)
    }
}

struct PlayBar: View {
    var count: Int
    var primary: String
    var secondary: String
    var primaryID: String
    var onPrimary: () -> Void
    var onSecondary: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            Text(count == 0 ? "先勾选要听的课" : "已选 \(count) 条")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(count == 0 ? Color.secondary : Color.orange)
            Button(action: onPrimary) {
                Label(primary, systemImage: "play.fill")
                    .font(.title.bold())
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color(red: 1, green: 0.42, blue: 0))
            .controlSize(.large)
            .disabled(count == 0)
            .accessibilityIdentifier(primaryID)
            Button(action: onSecondary) {
                Label(secondary, systemImage: "mic.fill")
                    .font(.title3.bold())
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color(red: 0.85, green: 0.2, blue: 0.05))
            .controlSize(.large)
            .disabled(count == 0)
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background(.regularMaterial)
    }
}

struct SessionView: View {
    @EnvironmentObject private var model: AppModel
    let request: SessionRequest

    var body: some View {
        VStack(spacing: 16) {
            Text(request.title).font(.headline)
            Text("第 \(min(model.sessionIndex + 1, max(model.sessionWords.count, 1))) / \(model.sessionWords.count) 个")
            Text(model.sessionPhase).font(.title2)
            if request.drill {
                Text("默写不显示答案。听到「嘀」就写，写完会自动读下一题。可以最小化，回到主界面也会继续读。")
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            } else {
                Text("听到「嘀」就写在本子上，不用看手机。最小化后，主界面藏起来也会继续读。")
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            Text(model.sessionRemaining > 0 ? "还剩 \(model.sessionRemaining) 秒" : " ")
                .font(.system(size: 56, weight: .bold, design: .rounded))
            Button("下一个") { model.advanceSession() }
                .font(.title2.bold())
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
            Button("再听一次") { model.speakCurrent() }
            Button("最小化") { model.minimizeSession() }
                .font(.title3)
                .accessibilityIdentifier("minimize")
        }
        .padding(24)
        .onAppear { model.ensureSessionPlaying() }
        .onDisappear { model.sessionViewHidden() }
        .navigationTitle("听写")
        .toolbar {
            Button("结束") { model.finishSession() }
        }
    }
}

struct SummaryView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        List {
            Section {
                Text("看和本子上写的一样不一样。点「写错了」会放进错题本。")
                Button("拍照批改") { model.path.append(.grade) }
            }
            ForEach(model.sessionWords) { word in
                VStack(alignment: .leading) {
                    Text(word.text).font(.title3.bold())
                    if !word.meaning.isEmpty { Text(word.meaning).foregroundStyle(.secondary) }
                    Button(model.wrongIDs.contains(word.id) ? "算对" : "写错了") {
                        if model.wrongIDs.contains(word.id) {
                            model.unmark(word)
                        } else {
                            model.markWrong(word)
                        }
                    }
                }
            }
        }
        .navigationTitle("对一对")
    }
}

struct GradeView: View {
    @EnvironmentObject private var model: AppModel
    @State private var hits: [GradeHit] = []
    @State private var accepted: Set<Int> = []
    @State private var marked: Set<Int> = []
    @State private var picker: PhotosPickerItem?

    var body: some View {
        List {
            Section {
                Text("只看这次听写的词。认成另一个字算明确写错，会进错题本。笔画没写到位的先给你看。笔顺看不出来。")
                Text("卷子上最好写上 1、2、3。").foregroundStyle(.secondary)
                PhotosPicker(selection: $picker, matching: .images) {
                    Text("选一张卷子照片")
                }
                .onChange(of: picker) { _, item in
                    guard let item else { return }
                    Task {
                        if let data = try? await item.loadTransferable(type: Data.self) {
                            gradePhoto(data)
                        }
                    }
                }
            }
            ForEach(Array(hits.enumerated()), id: \.offset) { index, hit in
                VStack(alignment: .leading, spacing: 6) {
                    Text(hit.expected).font(.title2.bold())
                    Text(detail(index, hit))
                    if hit.mark != .right {
                        HStack {
                            Button("算对") { accept(index, hit) }
                            Button("写错了") { countWrong(index, hit) }
                        }
                    }
                }
            }
        }
        .navigationTitle("拍照批改")
    }

    private func gradePhoto(_ data: Data) {
        let request = VNRecognizeTextRequest()
        request.recognitionLanguages = ["zh-Hans", "en-US"]
        request.recognitionLevel = .accurate
        let handler = VNImageRequestHandler(data: data)
        let observations = (try? handler.perform([request])) == nil ? [] : (request.results ?? [])
        let lines: [SheetLine] = observations.compactMap { observation in
            guard let candidate = observation.topCandidates(1).first else { return nil }
            return SheetLine(
                text: candidate.string,
                chars: candidate.string.map { SeenChar(char: $0, confidence: candidate.confidence) }
            )
        }
        apply(lines)
    }

    private func apply(_ lines: [SheetLine]) {
        let words = model.sessionWords.map { ($0.text, $0.lang == .zh) }
        let graded = gradeSheet(words: words, lines: lines)
        hits = graded
        for (index, hit) in graded.enumerated() where hit.mark == .wrong {
            guard model.sessionWords.indices.contains(index) else { continue }
            let word = model.sessionWords[index]
            MistakeBook.remember(&model.mistakes, text: word.text, lang: word.lang, meaning: word.meaning)
        }
    }

    private func detail(_ index: Int, _ hit: GradeHit) -> String {
        if accepted.contains(index) { return "算对" }
        let chinese = model.sessionWords.indices.contains(index) ? model.sessionWords[index].lang == .zh : true
        let text = gradeDetail(hit, chinese: chinese)
        if hit.mark == .wrong || marked.contains(index) { return text + "，已进错题本" }
        return text
    }

    private func accept(_ index: Int, _ hit: GradeHit) {
        guard let word = model.sessionWords.indices.contains(index) ? model.sessionWords[index] : nil else { return }
        MistakeBook.applyGrade(&model.mistakes, hit: hit, lang: word.lang, meaning: word.meaning, userMarkedWrong: false, userAccepted: true)
        accepted.insert(index)
        marked.remove(index)
    }

    private func countWrong(_ index: Int, _ hit: GradeHit) {
        guard !marked.contains(index), hit.mark != .wrong else { return }
        guard let word = model.sessionWords.indices.contains(index) ? model.sessionWords[index] : nil else { return }
        MistakeBook.applyGrade(&model.mistakes, hit: hit, lang: word.lang, meaning: word.meaning, userMarkedWrong: true, userAccepted: false)
        marked.insert(index)
    }

}

struct MistakeListView: View {
    @EnvironmentObject private var model: AppModel
    let lang: Lang
    @State private var draft = ""
    @State private var meaning = ""

    var body: some View {
        let rows = model.mistakes.filter { $0.lang == lang }
        List {
            Section("增加") {
                TextField("词语", text: $draft)
                TextField("意思", text: $meaning)
                Button("加入错题本") {
                    guard !draft.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                    model.addMistake(text: draft, lang: lang, meaning: meaning)
                    draft = ""
                    meaning = ""
                }
            }
            ForEach(rows) { mistake in
                VStack(alignment: .leading) {
                    Text(mistake.text).font(.title3)
                    Text(mistake.meaning).foregroundStyle(.secondary)
                    HStack {
                        Button("改意思") { model.updateMistake(mistake, text: mistake.text, meaning: meaning.isEmpty ? mistake.meaning : meaning) }
                        Button("删除", role: .destructive) { model.deleteMistake(mistake) }
                    }
                }
            }
        }
        .navigationTitle(lang == .zh ? "语文错题本" : "英语错题本")
    }
}

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        Form {
            Stepper("普通词 \(model.settings.writingSeconds) 秒", value: $model.settings.writingSeconds, in: 3...30)
            Stepper("四字词语 \(model.settings.idiomSeconds) 秒", value: $model.settings.idiomSeconds, in: 3...60)
            Stepper("重复 \(model.settings.repeats) 遍", value: $model.settings.repeats, in: 1...3)
            Stepper("语速 \(model.settings.speechRate, specifier: "%.2f")", value: $model.settings.speechRate, in: 0.30...0.65, step: 0.05)
            Stepper("每个字母 \(model.settings.secondsPerLetter) 秒", value: $model.settings.secondsPerLetter, in: 1...5)
            Stepper("英语最少 \(model.settings.englishMinSeconds) 秒", value: $model.settings.englishMinSeconds, in: 3...30)
            Toggle("讲解易混字", isOn: $model.settings.explainChinese)
            Toggle("拼读英语字母", isOn: $model.settings.spellEnglish)
            Toggle("随机时一组词还在一起", isOn: $model.settings.shuffle)
            Picker("认读怎么报", selection: $model.settings.readCue) {
                ForEach(EnglishCue.allCases) { Text($0.label).tag($0) }
            }
            Picker("默写怎么报", selection: $model.settings.drillCue) {
                ForEach(EnglishCue.allCases) { Text($0.label).tag($0) }
            }
        }
        .navigationTitle("设置")
    }
}
