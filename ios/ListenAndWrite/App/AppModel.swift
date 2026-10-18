import AudioToolbox
import AVFoundation
import Foundation
import Speech

@MainActor
final class AppModel: ObservableObject {
    @Published var path: [AppRoute] = []
    @Published var settings = AppSettings()
    @Published var lastChinese = LastPlay()
    @Published var lastRead = LastPlay()
    @Published var lastDrill = LastPlay(cue: .chinese)
    @Published var chineseWords: [StoredWord] = []
    @Published var englishWords: [StoredWord] = []
    @Published var drillWords: [StoredWord] = []
    @Published var mistakes: [Mistake] = []
    @Published var sessionWords: [StoredWord] = []
    @Published var sessionTitle = ""
    @Published var sessionDrill = false
    @Published var sessionCue: EnglishCue = .both
    @Published var wrongIDs: Set<String> = []
    @Published var sessionIndex = 0
    @Published var sessionRemaining = 0
    @Published var sessionPhase = "准备"
    @Published var sessionMinimized = false
    @Published var sessionRunning = false
    @Published var sessionMode: PlayMode = .timer

    private let library: (chinese: [ChineseItem], english: [EnglishItem], drills: [EnglishItem])
    private var sessionRequest: SessionRequest?
    private var playbackTask: Task<Void, Never>?
    private var countdown: Timer?
    private let voice = VoiceListener()
    private var waitingForVoice = false
    private var advancing = false
    private let keepAlive = SessionKeepAlive()

    init() {
        library = WordLibrary.loadBundled()
        chineseWords = Seed.merge(existing: [], incoming: library.chinese.enumerated().map { WordLibrary.asStored($0.element, index: $0.offset) })
        englishWords = Seed.merge(existing: [], incoming: library.english.enumerated().map { WordLibrary.asStored($0.element, index: $0.offset, drill: false) })
        drillWords = Seed.merge(existing: [], incoming: library.drills.enumerated().map { WordLibrary.asStored($0.element, index: $0.offset, drill: true) })
    }

    func openSubject(_ kind: SubjectKind) {
        path.append(.subject(kind))
    }

    func chineseRows() -> [HomeRow] {
        HomeLists.chinese(words: library.chinese)
    }

    func englishRows() -> [HomeRow] {
        HomeLists.englishUnits(words: library.english)
    }

    func drillRows() -> [HomeRow] {
        HomeLists.drills(words: library.drills)
    }

    func words(kind: SubjectKind, units: Set<Int>) -> [StoredWord] {
        let source = kind == .chinese ? chineseWords : englishWords
        return source.filter { units.contains(unitSlot($0.unit)) }
    }

    func start(words: [StoredWord], title: String, drill: Bool, units: Set<Int>, mode: PlayMode, random: Bool) {
        stopPlayback()
        let ordered = practiceOrder(words, shuffle: settings.shuffle || random) { word in
            StudyGuide.lookup(word.text, lang: word.lang)?.focus ?? ""
        }
        sessionWords = ordered
        sessionTitle = title
        sessionDrill = drill
        sessionCue = settings.cue(forDrill: drill)
        wrongIDs = []
        sessionIndex = 0
        sessionRemaining = 0
        sessionPhase = "准备"
        sessionMinimized = false
        sessionMode = mode
        let play = LastPlay(units: units, random: random, mode: mode, cue: sessionCue)
        if drill {
            lastDrill = play
        } else if words.first?.lang == .zh {
            lastChinese = play
        } else {
            lastRead = play
        }
        let request = SessionRequest(title: title, drill: drill, mode: mode, wordIDs: ordered.map(\.id))
        sessionRequest = request
        path.append(.session(request))
    }

    /// Starts the current word once. Opening the page again, or expanding the mini bar, does not restart it.
    func ensureSessionPlaying() {
        guard !sessionWords.isEmpty else { return }
        if sessionRunning { return }
        sessionRunning = true
        sessionMinimized = false
        keepAlive.start()
        speakCurrent()
    }

    func minimizeSession() {
        guard sessionRunning else { return }
        sessionMinimized = true
        path.removeAll(where: routeIsSession)
    }

    func expandSession() {
        guard sessionRunning, let sessionRequest else { return }
        sessionMinimized = false
        if !path.contains(where: routeIsSession) {
            path.append(.session(sessionRequest))
        }
    }

    /// Leaving the session page stops playback, unless the user minimized it or the page is still on the stack.
    func sessionViewHidden() {
        if SessionPresence.keepPlaying(minimized: sessionMinimized, routeVisible: path.contains(where: routeIsSession)) {
            return
        }
        stopPlayback()
    }

    func speakCurrent() {
        guard sessionWords.indices.contains(sessionIndex) else {
            finishSession()
            return
        }
        countdown?.invalidate()
        playbackTask?.cancel()
        voice.stop()
        waitingForVoice = false
        let word = sessionWords[sessionIndex]
        let focuses = sessionWords.map { StudyGuide.lookup($0.text, lang: $0.lang)?.focus ?? "" }
        let place = LessonScript.placeOf(focuses, index: sessionIndex)
        let spoken = LessonScript.lines(
            text: word.text,
            lang: word.lang,
            meaning: word.meaning,
            contrast: word.note,
            place: place,
            options: settings.lessonOptions(forDrill: sessionDrill)
        )
        sessionPhase = spoken.first?.hint ?? "请听"
        let seconds = WordText.writingSeconds(
            word.text,
            base: settings.writingSeconds,
            idiom: settings.idiomSeconds,
            secondsPerLetter: settings.secondsPerLetter,
            englishMinimum: settings.englishMinSeconds
        )
        let rate = settings.speechRate
        let mode = sessionMode
        playbackTask = Task { @MainActor in
            await Playback.playPrompt(spoken, rate: rate, player: Speaker.shared)
            guard !Task.isCancelled else { return }
            sessionRemaining = seconds
            sessionPhase = "请写在本子上"
            if mode == .voice {
                waitingForVoice = true
                voice.start { text in
                    Task { @MainActor in
                        guard self.waitingForVoice else { return }
                        Playback.consume(text) { self.advanceSession() }
                    }
                }
            } else {
                let timer = Timer(timeInterval: 1, repeats: true) { _ in
                    Task { @MainActor in
                        if self.sessionRemaining > 0 { self.sessionRemaining -= 1 }
                        if self.sessionRemaining == 0 { self.advanceSession() }
                    }
                }
                countdown = timer
                RunLoop.main.add(timer, forMode: .common)
            }
        }
    }

    func advanceSession() {
        guard !advancing else { return }
        advancing = true
        defer { advancing = false }
        countdown?.invalidate()
        voice.stop()
        waitingForVoice = false
        Speaker.shared.stop()
        if sessionIndex + 1 >= sessionWords.count {
            finishSession()
        } else {
            sessionIndex += 1
            speakCurrent()
        }
    }

    func finishSession() {
        stopPlayback()
        sessionMinimized = false
        path.removeAll(where: routeIsSession)
        if path.last != .summary {
            path.append(.summary)
        }
    }

    private func stopPlayback() {
        countdown?.invalidate()
        countdown = nil
        playbackTask?.cancel()
        playbackTask = nil
        voice.stop()
        waitingForVoice = false
        sessionRunning = false
        Speaker.shared.stop()
        keepAlive.stop()
    }

    private func routeIsSession(_ route: AppRoute) -> Bool {
        if case .session = route { return true }
        return false
    }

    func markWrong(_ word: StoredWord) {
        wrongIDs.insert(word.id)
        MistakeBook.remember(&mistakes, text: word.text, lang: word.lang, meaning: word.meaning)
    }

    func unmark(_ word: StoredWord) {
        wrongIDs.remove(word.id)
        MistakeBook.undo(&mistakes, text: word.text, lang: word.lang)
    }

    func updateMistake(_ mistake: Mistake, text: String, meaning: String) {
        guard let index = mistakes.firstIndex(where: { $0.id == mistake.id }) else { return }
        mistakes[index].text = text
        mistakes[index].meaning = meaning
    }

    func deleteMistake(_ mistake: Mistake) {
        mistakes.removeAll { $0.id == mistake.id }
    }

    func addMistake(text: String, lang: Lang, meaning: String) {
        MistakeBook.remember(&mistakes, text: text, lang: lang, meaning: meaning)
    }
}

protocol PromptPlayer: AnyObject {
    func speak(_ lines: [SpokenLine], rate: Float) async
    func beep()
}

enum SpeechPace {
    /// Shared setting near 0.5 matches English. Chinese system voices at that speed rush a dictation.
    static func utteranceRate(setting: Float, lang: Lang) -> Float {
        let shared = min(max(setting, 0.2), 0.7)
        guard lang == .zh else { return shared }
        return min(max(shared * 0.64, 0.22), 0.36)
    }
}

enum SessionPresence {
    /// Minimized playback stays alive after the session page is gone. A visible session page also keeps playing.
    static func keepPlaying(minimized: Bool, routeVisible: Bool) -> Bool {
        minimized || routeVisible
    }
}

enum Playback {
    /// Beep only after every line of this prompt has finished.
    static func playPrompt(_ lines: [SpokenLine], rate: Float, player: PromptPlayer) async {
        await player.speak(lines, rate: rate)
        player.beep()
    }

    /// Voice mode uses the same advance words as the big button.
    static func consume(_ transcript: String, advance: () -> Void) {
        if VoiceMatch.isAdvance(transcript) {
            advance()
        }
    }
}

final class Speaker: NSObject, AVSpeechSynthesizerDelegate, PromptPlayer {
    static let shared = Speaker()
    private let synthesizer = AVSpeechSynthesizer()
    private let lock = NSLock()
    private var batch: Set<ObjectIdentifier> = []
    private var waiter: CheckedContinuation<Void, Never>?

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    func stop() {
        lock.lock()
        batch.removeAll()
        lock.unlock()
        synthesizer.stopSpeaking(at: .immediate)
        finishWait()
    }

    func speak(_ lines: [SpokenLine], rate: Float) async {
        stop()
        if lines.isEmpty { return }
        let session = AVAudioSession.sharedInstance()
        if session.category != .playAndRecord && session.category != .playback {
            try? session.setCategory(.playback, mode: .spokenAudio)
        }
        try? session.setActive(true)
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
            lock.lock()
            waiter = cont
            lock.unlock()
            let utterances = lines.map { line in
                let utterance = AVSpeechUtterance(string: line.text)
                utterance.voice = AVSpeechSynthesisVoice(language: line.lang == .zh ? "zh-CN" : "en-US")
                let applied = SpeechPace.utteranceRate(setting: rate, lang: line.lang)
                utterance.rate = applied
                return utterance
            }
            lock.lock()
            batch = Set(utterances.map { ObjectIdentifier($0) })
            lock.unlock()
            for utterance in utterances {
                synthesizer.speak(utterance)
            }
        }
    }

    func beep() {
        AudioServicesPlaySystemSound(1052)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        finishUtterance(utterance)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        finishUtterance(utterance)
    }

    private func finishUtterance(_ utterance: AVSpeechUtterance) {
        let id = ObjectIdentifier(utterance)
        lock.lock()
        guard batch.contains(id) else {
            lock.unlock()
            return
        }
        batch.remove(id)
        let done = batch.isEmpty
        lock.unlock()
        if done { finishWait() }
    }

    private func finishWait() {
        lock.lock()
        let cont = waiter
        waiter = nil
        lock.unlock()
        cont?.resume()
    }
}

final class VoiceListener {
    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "zh-CN"))
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    func start(onTranscript: @escaping (String) -> Void) {
        stop()
        SFSpeechRecognizer.requestAuthorization { [weak self] status in
            guard status == .authorized else { return }
            DispatchQueue.main.async {
                self?.begin(onTranscript)
            }
        }
    }

    func stop() {
        task?.cancel()
        task = nil
        request?.endAudio()
        request = nil
        if engine.isRunning {
            engine.stop()
            engine.inputNode.removeTap(onBus: 0)
        }
    }

    private func begin(_ onTranscript: @escaping (String) -> Void) {
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        self.request = request
        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        guard format.sampleRate > 0 else { return }
        input.removeTap(onBus: 0)
        input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            request.append(buffer)
        }
        engine.prepare()
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playAndRecord, mode: .spokenAudio, options: [.defaultToSpeaker, .duckOthers, .allowBluetooth])
        try? session.setActive(true)
        try? engine.start()
        task = recognizer?.recognitionTask(with: request) { result, _ in
            guard let text = result?.bestTranscription.formattedString else { return }
            DispatchQueue.main.async {
                onTranscript(text)
            }
        }
    }
}

/// Keeps the audio session running through the silent writing gap, so a minimized or backgrounded session can still reach the next word.
final class SessionKeepAlive {
    private var player: AVAudioPlayer?

    func start() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playAndRecord, mode: .spokenAudio, options: [.defaultToSpeaker, .duckOthers, .allowBluetooth])
        try? session.setActive(true)
        if player == nil {
            player = try? AVAudioPlayer(data: Self.quietWav())
            player?.numberOfLoops = -1
            player?.volume = 0.02
            player?.prepareToPlay()
        }
        player?.play()
    }

    func stop() {
        player?.stop()
    }

    private static func quietWav() -> Data {
        let sampleRate = 8_000
        let samples = sampleRate
        var pcm = Data(count: samples * 2)
        pcm.withUnsafeMutableBytes { raw in
            let pointer = raw.bindMemory(to: Int16.self)
            for index in stride(from: 0, to: samples, by: 80) {
                pointer[index] = 40
            }
        }
        var data = Data()
        func ascii(_ value: String) { data.append(contentsOf: value.utf8) }
        func u32(_ value: UInt32) { var le = value.littleEndian; withUnsafeBytes(of: &le) { data.append(contentsOf: $0) } }
        func u16(_ value: UInt16) { var le = value.littleEndian; withUnsafeBytes(of: &le) { data.append(contentsOf: $0) } }
        ascii("RIFF")
        u32(UInt32(36 + pcm.count))
        ascii("WAVE")
        ascii("fmt ")
        u32(16)
        u16(1)
        u16(1)
        u32(UInt32(sampleRate))
        u32(UInt32(sampleRate * 2))
        u16(2)
        u16(16)
        ascii("data")
        u32(UInt32(pcm.count))
        data.append(pcm)
        return data
    }
}