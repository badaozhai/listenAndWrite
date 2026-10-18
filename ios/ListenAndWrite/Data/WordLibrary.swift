import Foundation

struct ChineseItem: Equatable {
    var slot: String
    var text: String
    var meaning: String
}

struct EnglishItem: Equatable {
    var unit: Int
    var text: String
    var meaning: String
    var note: String = ""
}

enum WordLibrary {
    static let guessedNote = "没有印答案，按词表译的。不对可以改。"

    static func load(from directory: URL) throws -> (chinese: [ChineseItem], english: [EnglishItem], drills: [EnglishItem]) {
        let chinese = try parseChinese(String(contentsOf: directory.appendingPathComponent("ChineseTextbook.kt"), encoding: .utf8))
        let english = try parseEnglish(String(contentsOf: directory.appendingPathComponent("EnglishTextbook.kt"), encoding: .utf8))
        let drills = try parseDrills(String(contentsOf: directory.appendingPathComponent("EnglishDrills.kt"), encoding: .utf8))
        return (chinese, english, drills)
    }

    static func bundledDirectory() -> URL? {
        Bundle.main.url(forResource: "ChineseTextbook", withExtension: "kt")?.deletingLastPathComponent()
    }

    static func loadBundled() -> (chinese: [ChineseItem], english: [EnglishItem], drills: [EnglishItem]) {
        guard let directory = bundledDirectory() else { return ([], [], []) }
        return (try? load(from: directory)) ?? ([], [], [])
    }

    static func parseChinese(_ source: String) -> [ChineseItem] {
        calls(named: "ChineseEntry", in: source).compactMap { args in
            guard args.count >= 2, case let .string(slot) = args[0], case let .string(text) = args[1] else { return nil }
            let meaning = args.count >= 3 ? stringValue(args[2]) : ""
            return ChineseItem(slot: slot, text: text, meaning: meaning)
        }
    }

    static func parseEnglish(_ source: String) -> [EnglishItem] {
        calls(named: "TextbookEntry", in: source).compactMap { args in
            guard args.count >= 3, case let .number(unit) = args[0], case let .string(text) = args[1] else { return nil }
            return EnglishItem(unit: unit, text: text, meaning: stringValue(args[2]))
        }
    }

    static func parseDrills(_ source: String) -> [EnglishItem] {
        calls(named: "DrillEntry", in: source).compactMap { args in
            guard args.count >= 3, case let .number(unit) = args[0], case let .string(text) = args[1] else { return nil }
            let note = args.count >= 4 ? stringValue(args[3]) : ""
            return EnglishItem(unit: unit, text: text, meaning: stringValue(args[2]), note: note)
        }
    }

    static func asStored(_ item: ChineseItem, index: Int) -> StoredWord {
        StoredWord(id: "zh-\(index)-\(item.slot)", text: item.text, lang: .zh, meaning: item.meaning, note: "", unit: item.slot)
    }

    static func asStored(_ item: EnglishItem, index: Int, drill: Bool) -> StoredWord {
        StoredWord(
            id: "\(drill ? "drill" : "en")-\(index)-\(item.unit)",
            text: item.text,
            lang: .en,
            meaning: item.meaning,
            note: item.note,
            unit: String(item.unit)
        )
    }

    private enum Arg {
        case string(String)
        case number(Int)
        case ident(String)
    }

    private static func stringValue(_ arg: Arg) -> String {
        switch arg {
        case let .string(value): return value
        case .ident("GUESSED"): return guessedNote
        default: return ""
        }
    }

    private static func calls(named name: String, in source: String) -> [[Arg]] {
        var results: [[Arg]] = []
        let token = name + "("
        var search = source.startIndex
        while let range = source.range(of: token, range: search..<source.endIndex) {
            var index = range.upperBound
            var args: [Arg] = []
            var depth = 1
            var current = ""
            var inString = false
            var escape = false
            func flush() {
                let trimmed = current.trimmingCharacters(in: .whitespacesAndNewlines)
                current = ""
                if trimmed.isEmpty { return }
                if let number = Int(trimmed) {
                    args.append(.number(number))
                } else {
                    args.append(.ident(trimmed))
                }
            }
            while index < source.endIndex && depth > 0 {
                let ch = source[index]
                if inString {
                    if escape {
                        current.append(ch)
                        escape = false
                    } else if ch == "\\" {
                        escape = true
                    } else if ch == "\"" {
                        args.append(.string(current))
                        current = ""
                        inString = false
                    } else {
                        current.append(ch)
                    }
                } else if ch == "\"" {
                    flush()
                    inString = true
                } else if ch == "(" {
                    depth += 1
                    current.append(ch)
                } else if ch == ")" {
                    depth -= 1
                    if depth == 0 { flush() } else { current.append(ch) }
                } else if ch == "," && depth == 1 {
                    flush()
                } else {
                    current.append(ch)
                }
                index = source.index(after: index)
            }
            if !args.isEmpty { results.append(args) }
            search = index
        }
        return results
    }
}

struct HomeRow: Identifiable {
    var id: Int
    var title: String
    var count: Int
}

enum HomeLists {
    static func chinese(words: [ChineseItem]) -> [HomeRow] {
        var rows: [HomeRow] = (1...chineseUnitCount).map { slot in
            HomeRow(id: slot, title: slotTitle(slot, chinese: true), count: words.filter { unitSlot($0.slot) == slot }.count)
        }
        let gardens = [gardenAfter18, gardenAfter22]
        for garden in gardens {
            let row = HomeRow(id: garden, title: slotTitle(garden, chinese: true), count: words.filter { unitSlot($0.slot) == garden }.count)
            if garden == gardenAfter18, let index = rows.firstIndex(where: { $0.id == 18 }) {
                rows.insert(row, at: index + 1)
            } else if garden == gardenAfter22, let index = rows.firstIndex(where: { $0.id == 22 }) {
                rows.insert(row, at: index + 1)
            }
        }
        return rows
    }

    static func englishUnits(words: [EnglishItem]) -> [HomeRow] {
        (1...unitCount).map { unit in
            HomeRow(id: unit, title: slotTitle(unit, chinese: false), count: words.filter { $0.unit == unit }.count)
        }
    }

    static func drills(words: [EnglishItem]) -> [HomeRow] {
        (1...4).map { unit in
            HomeRow(id: unit, title: slotTitle(unit, chinese: false), count: words.filter { $0.unit == unit }.count)
        }
    }
}
