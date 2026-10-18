import XCTest
@testable import ListenAndWrite

final class WordListTests: XCTestCase {
    func testShippedListsMatchTheAndroidSources() throws {
        let library = try WordLibrary.load(from: Repo.dataDirectory)
        let bundled = WordLibrary.loadBundled()
        print("WORDS chinese=\(library.chinese.count) english=\(library.english.count) drills=\(library.drills.count)")
        XCTAssertEqual(library.chinese.count, 213)
        XCTAssertEqual(library.english.count, 164)
        XCTAssertEqual(library.drills.count, 120)
        XCTAssertEqual(bundled.chinese.count, library.chinese.count)
        XCTAssertEqual(bundled.english.map(\.text), library.english.map(\.text))
        XCTAssertEqual(bundled.drills.map(\.text), library.drills.map(\.text))

        let tunnel = library.chinese.first { $0.text == "地道" }
        XCTAssertEqual(tunnel?.slot, "4")
        XCTAssertTrue(tunnel?.meaning.contains("地下通道") == true)
        XCTAssertEqual(library.chinese.first { $0.text == "势不可当" }?.slot, "7")

        let listens = library.english.filter { $0.text.compare("listen", options: .caseInsensitive) == .orderedSame }
        XCTAssertEqual(listens.count, 2)
        XCTAssertEqual(listens.first { $0.unit == 1 }?.meaning, "听")
        XCTAssertEqual(listens.first { $0.unit == 4 }?.meaning, "听从")
        XCTAssertEqual(library.english.first { $0.text == "May" }?.meaning, "五月")
        XCTAssertEqual(library.english.first { $0.text == "may" }?.meaning, "也许，可能")
        XCTAssertTrue(library.drills.filter { $0.unit == 3 }.allSatisfy { $0.note.contains("没有印答案") })
        print("IDENTITY 地道 slot=\(tunnel?.slot ?? "") meaning=\(tunnel?.meaning ?? "")")
        print("IDENTITY 势不可当 slot=\(library.chinese.first { $0.text == "势不可当" }?.slot ?? "")")
        print("IDENTITY listen1=\(listens.first { $0.unit == 1 }?.meaning ?? "") listen4=\(listens.first { $0.unit == 4 }?.meaning ?? "")")
        print("IDENTITY May=\(library.english.first { $0.text == "May" }?.meaning ?? "") may=\(library.english.first { $0.text == "may" }?.meaning ?? "")")
        print("IDENTITY unit3-notes=\(library.drills.filter { $0.unit == 3 }.allSatisfy { $0.note.contains("没有印答案") })")

        let chineseHome = HomeLists.chinese(words: library.chinese)
        XCTAssertEqual(chineseHome.filter { $0.id == 3 }.first?.count, 0)
        XCTAssertGreaterThan(chineseHome.filter { $0.id == 1 }.first?.count ?? 0, 0)
        let englishHome = HomeLists.englishUnits(words: library.english)
        XCTAssertEqual(englishHome.filter { (9...15).contains($0.id) }.map(\.count), Array(repeating: 0, count: 7))
        XCTAssertGreaterThan(englishHome.first { $0.id == 1 }?.count ?? 0, 0)

        let edited = StoredWord(id: "keep", text: "地道", lang: .zh, meaning: "用户改过的意思", note: "", unit: "4")
        let seeded = Seed.merge(existing: [edited], incoming: library.chinese.enumerated().map { WordLibrary.asStored($0.element, index: $0.offset) })
        XCTAssertEqual(seeded.first { $0.text == "地道" && $0.unit == "4" }?.meaning, "用户改过的意思")
        XCTAssertEqual(seeded.filter { $0.text == "桂花" }.count, 1)
    }
}

final class DictationLogicTests: XCTestCase {
    func testScriptTimingVoiceAndMistakes() async throws {
        let head = LessonScript.lines(
            text: "势不可当",
            lang: .zh,
            meaning: "",
            contrast: "",
            place: .head,
            options: LessonOptions(repeats: 1, explainChinese: true)
        )
        XCTAssertTrue(head[0].text.contains("下面几个词一起记"))
        XCTAssertTrue(head[0].text.contains("势") && head[0].text.contains("事"))
        let rest = LessonScript.lines(
            text: "气势",
            lang: .zh,
            meaning: "",
            contrast: "",
            place: .rest,
            options: LessonOptions(repeats: 1, explainChinese: true)
        )
        XCTAssertFalse(rest.contains { $0.text.contains("下面几个词一起记") })
        XCTAssertEqual(LessonScript.placeOf(["势", "势", "桂"], index: 0), .head)
        XCTAssertEqual(LessonScript.placeOf(["势", "势", "桂"], index: 1), .rest)

        var settings = AppSettings()
        settings.readCue = .both
        settings.drillCue = .chinese
        XCTAssertEqual(settings.cue(forDrill: true), .chinese)
        XCTAssertEqual(settings.cue(forDrill: false), .both)
        let drillLines = LessonScript.lines(
            text: "very much",
            lang: .en,
            meaning: "非常",
            contrast: "",
            place: .alone,
            options: settings.lessonOptions(forDrill: true)
        )
        XCTAssertEqual(drillLines.map(\.text), ["非常", "非常"])
        XCTAssertTrue(drillLines.allSatisfy { $0.lang == .zh })

        XCTAssertEqual(WordText.writingSeconds("势不可挡", base: 5, idiom: 10), 10)
        XCTAssertEqual(WordText.writingSeconds("桂花", base: 5, idiom: 10), 5)
        XCTAssertEqual(WordText.englishGapSeconds("carefully", secondsPerLetter: 1, minimum: 5), 9)
        XCTAssertEqual(WordText.writingSeconds("a", base: 5, idiom: 10, secondsPerLetter: 1, englishMinimum: 5), 5)
        XCTAssertEqual(WordText.letterCount("dragon boat race"), 14)

        XCTAssertTrue(VoiceMatch.isAdvance("下一个"))
        XCTAssertTrue(VoiceMatch.isAdvance("好了"))
        XCTAssertTrue(VoiceMatch.isAdvance("好啦。"))
        XCTAssertTrue(VoiceMatch.isAdvance("next"))
        XCTAssertTrue(VoiceMatch.isAdvance("ok"))
        XCTAssertFalse(VoiceMatch.isAdvance("今天天气很好"))

        let ordered = practiceOrder(["势不可挡", "气势", "桂花"], shuffle: true) { word in
            word == "桂花" ? "" : "势"
        }
        let firstPair = ordered.prefix(2)
        XCTAssertTrue(Set(firstPair) == Set(["势不可挡", "气势"]) || ordered.dropFirst().prefix(2).allSatisfy { $0 != "桂花" && ordered.contains($0) })
        let blocksStay = zip(ordered, ordered.dropFirst()).filter { $0.0 != "桂花" && $0.1 != "桂花" }
        XCTAssertFalse(blocksStay.isEmpty)

        var book: [Mistake] = []
        let wrong = gradeOne("势", seenChars: [SeenChar(char: "事", confidence: 0)], chinese: true)
        MistakeBook.applyGrade(&book, hit: wrong, lang: .zh, meaning: "", userMarkedWrong: false, userAccepted: false)
        XCTAssertEqual(book.count, 1)
        MistakeBook.applyGrade(&book, hit: wrong, lang: .zh, meaning: "", userMarkedWrong: false, userAccepted: true)
        XCTAssertTrue(book.isEmpty)
        let stroke = gradeOne("势不可挡", seenChars: [SeenChar(char: "势"), SeenChar(char: "不"), SeenChar(char: "可")], chinese: true)
        MistakeBook.applyGrade(&book, hit: stroke, lang: .zh, meaning: "", userMarkedWrong: false, userAccepted: false)
        XCTAssertTrue(book.isEmpty)
        MistakeBook.applyGrade(&book, hit: stroke, lang: .zh, meaning: "", userMarkedWrong: true, userAccepted: false)
        XCTAssertEqual(book.count, 1)
        let player = OrderPlayer()
        let prompt = LessonScript.lines(
            text: "桂花",
            lang: .zh,
            meaning: "",
            contrast: "",
            place: .alone,
            options: LessonOptions(repeats: 2, explainChinese: false)
        )
        await Playback.playPrompt(prompt, rate: 0.4, player: player)
        XCTAssertEqual(player.events, ["speak", "beep"])
        XCTAssertEqual(player.rate, 0.4)
        XCTAssertEqual(player.lines.map(\.text), ["桂花", "桂花"])
        var advanced = false
        Playback.consume("下一个") { advanced = true }
        XCTAssertTrue(advanced)
        advanced = false
        Playback.consume("今天天气很好") { advanced = true }
        XCTAssertFalse(advanced)
        var kept = AppSettings()
        kept.drillCue = .both
        kept.readCue = .english
        kept.speechRate = 0.55
        XCTAssertEqual(kept.drillCue, .both)
        XCTAssertEqual(kept.speechRate, 0.55)
        XCTAssertLessThan(SpeechPace.utteranceRate(setting: 0.45, lang: .zh), 0.32)
        XCTAssertEqual(SpeechPace.utteranceRate(setting: 0.45, lang: .en), 0.45)
        XCTAssertTrue(SessionPresence.keepPlaying(minimized: true, routeVisible: false))
        XCTAssertTrue(SessionPresence.keepPlaying(minimized: false, routeVisible: true))
        XCTAssertFalse(SessionPresence.keepPlaying(minimized: false, routeVisible: false))
        let screens = try String(contentsOf: Repo.root.appendingPathComponent("ios/ListenAndWrite/UI/Screens.swift"), encoding: .utf8)
        let model = try String(contentsOf: Repo.root.appendingPathComponent("ios/ListenAndWrite/App/AppModel.swift"), encoding: .utf8)
        XCTAssertTrue(model.contains("await Playback.playPrompt"))
        XCTAssertTrue(model.contains("voice.start"))
        XCTAssertTrue(model.contains("Playback.consume"))
        XCTAssertTrue(model.contains("settings.speechRate"))
        XCTAssertTrue(model.contains("SessionPresence.keepPlaying"))
        XCTAssertFalse(model.contains("asyncAfter"))
        XCTAssertFalse(screens.contains("asyncAfter"))
        XCTAssertFalse(screens.contains("drillCue = .chinese"))
        XCTAssertTrue(screens.contains("最小化"))
        XCTAssertTrue(screens.contains("sessionViewHidden"))
        XCTAssertFalse(screens.contains("Speaker.shared.stop()"))
        XCTAssertTrue(model.contains("SpeechPace.utteranceRate"))
        XCTAssertTrue(model.contains("utterance.rate = applied"))
        XCTAssertTrue(model.contains("didFinish"))
        XCTAssertFalse(model.contains("utterance.rate = 0.45"))
        let started = Date()
        try await withThrowingTaskGroup(of: Void.self) { group in
            group.addTask {
                await Speaker.shared.speak([SpokenLine(text: "桂花", lang: .zh, hint: "请听")], rate: 0.4)
            }
            group.addTask {
                try await Task.sleep(nanoseconds: 8_000_000_000)
                throw NSError(domain: "speech", code: 1, userInfo: [NSLocalizedDescriptionKey: "朗读没有在 8 秒内结束"])
            }
            try await group.next()
            group.cancelAll()
        }
        XCTAssertGreaterThan(Date().timeIntervalSince(started), 0.2)
        print("DICTATION passed")
    }
}

private final class OrderPlayer: PromptPlayer {
    var events: [String] = []
    var rate: Float = 0
    var lines: [SpokenLine] = []
    func speak(_ lines: [SpokenLine], rate: Float) async {
        events.append("speak")
        self.rate = rate
        self.lines = lines
    }
    func beep() {
        events.append("beep")
    }
}

final class GradeRuleTests: XCTestCase {
    func testVerdicts() {
        let hits = gradeSheet(
            words: [("桂花", true), ("故乡", true)],
            lines: [
                SheetLine(text: "2. 故乡"),
                SheetLine(text: "姓名：小明"),
                SheetLine(text: "1. 桂花"),
            ]
        )
        XCTAssertEqual(hits.map(\.mark), [.right, .right])
        XCTAssertEqual(gradeDetail(gradeOne("势", seenChars: [SeenChar(char: "事", confidence: 0)], chinese: true), chinese: true), "第1个字写成了「事」")
        XCTAssertEqual(gradeOne("己", seenChars: [SeenChar(char: "已")], chinese: true).mark, .wrong)
        XCTAssertEqual(gradeOne("未", seenChars: [SeenChar(char: "末")], chinese: true).mark, .wrong)
        XCTAssertEqual(gradeDetail(gradeOne("势不可挡", seenChars: "势不可".map { SeenChar(char: $0) }, chinese: true), chinese: true), "笔画没到位")
        XCTAssertEqual(gradeOne("桂花", seenChars: [], chinese: true).mark, .missing)
        XCTAssertEqual(gradeDetail(gradeOne("Apple", seenChars: "apple".map { SeenChar(char: $0, confidence: 0.9) }, chinese: false), chinese: false), "写对")
        XCTAssertTrue(gradeDetail(gradeOne("apple", seenChars: "apply".map { SeenChar(char: $0) }, chinese: false), chinese: false).contains("写成了"))
        let unclear = gradeDetail(gradeOne("apple", seenChars: "aple".map { SeenChar(char: $0) }, chinese: false), chinese: false)
        XCTAssertEqual(unclear, "没认清")
        print("GRADE 写成了「事」 笔画没到位 写对 没认清=\(unclear)")
        print("GRADE passed")
    }
}
