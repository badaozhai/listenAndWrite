import Foundation

enum GradeMark: String {
    case right, wrong, stroke, missing
}

struct SeenChar {
    var char: Character
    var confidence: Float = 0
}

struct SheetLine {
    var text: String
    var chars: [SeenChar] = []
    var number: Int? = nil
}

struct WrongSpot {
    var index: Int
    var expected: Character
    var seen: Character
}

struct GradeHit {
    var mark: GradeMark
    var expected: String
    var seen: String
    var spots: [WrongSpot] = []
}

private let sureConfidence: Float = 0.5
private let skipLine: Set<String> = ["姓名", "班级", "学号", "听写", "订正", "订正栏", "语文", "英语", "默写"]

func leadingNumber(_ text: String) -> Int? {
    let patterns = [
        #"^\s*[（(]?\s*(\d{1,3})\s*[）).、．:：\s]"#,
        #"^\s*(\d{1,3})(?=\p{Han}|[A-Za-z])"#,
    ]
    for pattern in patterns {
        guard let match = text.range(of: pattern, options: .regularExpression) else { continue }
        let digits = text[match].filter(\.isNumber)
        if let value = Int(digits) { return value }
    }
    return nil
}

func alignLines(count: Int, lines: [SheetLine]) -> [SheetLine?] {
    if count == 0 { return [] }
    var slots = Array<SheetLine?>(repeating: nil, count: count)
    let usable: [SheetLine] = lines.map { line in
        var copy = line
        copy.number = line.number ?? leadingNumber(line.text)
        return copy
    }.filter { line in
        let body = line.text.filter { WordText.isHan($0) || $0.isLetter }
        return !body.isEmpty && !skipLine.contains(body)
    }
    let numbered = usable.compactMap { line -> (Int, SheetLine)? in
        guard let number = line.number, (1...count).contains(number) else { return nil }
        return (number, line)
    }
    if !numbered.isEmpty {
        for (number, line) in numbered where slots[number - 1] == nil {
            slots[number - 1] = line
        }
        return slots
    }
    for (index, line) in usable.prefix(count).enumerated() {
        slots[index] = line
    }
    return slots
}

func gradeSheet(words: [(String, Bool)], lines: [SheetLine]) -> [GradeHit] {
    let aligned = alignLines(count: words.count, lines: lines)
    return words.enumerated().map { index, word in
        guard let line = aligned[index] else {
            return GradeHit(mark: .missing, expected: word.0, seen: "")
        }
        let chars = line.chars.isEmpty ? line.text.map { SeenChar(char: $0) } : line.chars
        return gradeOne(word.0, seenChars: chars, chinese: word.1)
    }
}

func gradeOne(_ expected: String, seenChars: [SeenChar], chinese: Bool) -> GradeHit {
    let expectChars: [Character] = chinese
        ? expected.filter(WordText.isHan)
        : expected.filter { $0.isLetter }.lowercased().map { $0 }
    let seen: [SeenChar] = chinese
        ? seenChars.filter { WordText.isHan($0.char) }
        : seenChars.filter { $0.char.isLetter }.map { SeenChar(char: Character($0.char.lowercased()), confidence: $0.confidence) }
    let seenText = String(seen.map(\.char))
    if expectChars.isEmpty {
        let mark: GradeMark = seenText.isEmpty ? .missing : (expected.trimmingCharacters(in: .whitespaces).caseInsensitiveCompare(seenText) == .orderedSame ? .right : .wrong)
        return GradeHit(mark: mark, expected: expected, seen: seenText)
    }
    if seen.isEmpty { return GradeHit(mark: .missing, expected: expected, seen: "") }
    if expectChars.count != seen.count { return GradeHit(mark: .stroke, expected: expected, seen: seenText) }
    var spots: [WrongSpot] = []
    var unsure = false
    for (index, raw) in expectChars.enumerated() {
        let wanted: Character = chinese ? raw : Character(String(raw).lowercased())
        let got = seen[index]
        if wanted == got.char { continue }
        if isSure(got.confidence) {
            spots.append(WrongSpot(index: index, expected: raw, seen: got.char))
        } else {
            unsure = true
        }
    }
    let mark: GradeMark = !spots.isEmpty ? .wrong : (unsure ? .stroke : .right)
    return GradeHit(mark: mark, expected: expected, seen: seenText, spots: spots)
}

func gradeDetail(_ hit: GradeHit, chinese: Bool) -> String {
    switch hit.mark {
    case .right: return "写对"
    case .missing: return "没找到这一行"
    case .stroke: return chinese ? "笔画没到位" : "没认清"
    case .wrong:
        let unit = chinese ? "字" : "字母"
        let text = hit.spots.map { "第\($0.index + 1)个\(unit)写成了「\($0.seen)」" }.joined(separator: "，")
        return text.isEmpty ? "明确写错" : text
    }
}

private func isSure(_ confidence: Float) -> Bool {
    confidence <= 0.001 || confidence >= sureConfidence
}

struct StoredWord: Equatable, Identifiable, Codable {
    var id: String
    var text: String
    var lang: Lang
    var meaning: String
    var note: String
    var unit: String
}

enum Seed {
    static func merge(existing: [StoredWord], incoming: [StoredWord]) -> [StoredWord] {
        var kept = existing
        let keys = Set(existing.map { identity($0) })
        for word in incoming where !keys.contains(identity(word)) {
            kept.append(word)
        }
        return kept
    }

    static func identity(_ word: StoredWord) -> String {
        let text = word.lang == .en ? word.text.lowercased() : word.text
        return "\(word.lang.rawValue)|\(word.unit)|\(text)"
    }
}

struct Mistake: Equatable, Identifiable, Codable {
    var id: String
    var text: String
    var lang: Lang
    var meaning: String
    var count: Int
}

enum MistakeBook {
    static func remember(_ book: inout [Mistake], text: String, lang: Lang, meaning: String) {
        let key = lang == .en ? text.lowercased() : text.trimmingCharacters(in: .whitespaces)
        if let index = book.firstIndex(where: { $0.lang == lang && ($0.lang == .en ? $0.text.lowercased() : $0.text) == key }) {
            book[index].count += 1
        } else {
            book.append(Mistake(id: UUID().uuidString, text: key, lang: lang, meaning: meaning, count: 1))
        }
    }

    static func undo(_ book: inout [Mistake], text: String, lang: Lang) {
        let key = lang == .en ? text.lowercased() : text.trimmingCharacters(in: .whitespaces)
        guard let index = book.firstIndex(where: { $0.lang == lang && ($0.lang == .en ? $0.text.lowercased() : $0.text) == key }) else { return }
        if book[index].count <= 1 {
            book.remove(at: index)
        } else {
            book[index].count -= 1
        }
    }

    static func applyGrade(_ book: inout [Mistake], hit: GradeHit, lang: Lang, meaning: String, userMarkedWrong: Bool, userAccepted: Bool) {
        if userAccepted {
            undo(&book, text: hit.expected, lang: lang)
            return
        }
        let shouldRemember = hit.mark == .wrong || (hit.mark == .stroke && userMarkedWrong) || (hit.mark == .missing && userMarkedWrong)
        if shouldRemember {
            remember(&book, text: hit.expected, lang: lang, meaning: meaning)
        }
    }
}
