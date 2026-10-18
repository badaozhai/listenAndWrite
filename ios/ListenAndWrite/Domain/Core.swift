import Foundation

enum Lang: String, Codable {
    case zh = "ZH"
    case en = "EN"
}

enum SubjectKind: String, CaseIterable, Identifiable {
    case chinese = "CHINESE"
    case english = "ENGLISH"
    var id: String { rawValue }
    var title: String { self == .chinese ? "语文" : "英语" }
}

let drillSubject = "EN_DRILL"
let unitCount = 15
let chineseUnitCount = 24
let gardenAfter18 = 101
let gardenAfter22 = 102

enum PlayMode: String, Codable {
    case timer
    case voice
    var label: String { self == .timer ? "定时" : "说完再继续" }
}

enum EnglishCue: String, Codable, CaseIterable, Identifiable {
    case english
    case chinese
    case both
    var id: String { rawValue }
    var label: String {
        switch self {
        case .english: return "报英文"
        case .chinese: return "报中文"
        case .both: return "中英都报"
        }
    }
}

enum GroupPlace {
    case alone, head, rest
}

struct LessonOptions {
    var repeats: Int = 2
    var explainChinese: Bool = true
    var englishCue: EnglishCue = .both
    var spellEnglish: Bool = false
}

struct SpokenLine: Equatable {
    var text: String
    var lang: Lang
    var hint: String
}

struct AppSettings: Equatable {
    var writingSeconds: Int = 5
    var idiomSeconds: Int = 10
    var repeats: Int = 2
    var secondsPerLetter: Int = 1
    var englishMinSeconds: Int = 5
    var explainChinese: Bool = true
    var spellEnglish: Bool = false
    var shuffle: Bool = false
    var speechRate: Float = 0.45
    var readCue: EnglishCue = .both
    var drillCue: EnglishCue = .chinese

    func cue(forDrill: Bool) -> EnglishCue { forDrill ? drillCue : readCue }
    func lessonOptions(forDrill: Bool) -> LessonOptions {
        LessonOptions(
            repeats: repeats,
            explainChinese: explainChinese,
            englishCue: cue(forDrill: forDrill),
            spellEnglish: spellEnglish
        )
    }
}

func slotTitle(_ slot: Int, chinese: Bool) -> String {
    switch slot {
    case gardenAfter18: return "语文园地（第18课后）"
    case gardenAfter22: return "语文园地（第22课后）"
    case 0: return "其他"
    default: return chinese ? "第\(slot)课" : "第\(slot)单元"
    }
}

func unitNumber(_ raw: String) -> Int? {
    let trimmed = raw.trimmingCharacters(in: .whitespaces)
    if let exact = Int(trimmed), (1...chineseUnitCount).contains(exact) { return exact }
    guard let match = trimmed.range(of: #"^第\s*(\d+)\s*(?:课|单元)$"#, options: .regularExpression) else { return nil }
    let digits = trimmed[match].filter(\.isNumber)
    guard let value = Int(digits), (1...chineseUnitCount).contains(value) else { return nil }
    return value
}

func unitSlot(_ unitName: String) -> Int {
    switch unitName {
    case "g18": return gardenAfter18
    case "g22": return gardenAfter22
    default: return unitNumber(unitName) ?? 0
    }
}
