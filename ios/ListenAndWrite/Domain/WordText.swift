import Foundation

enum WordText {
    static func isHan(_ ch: Character) -> Bool {
        ch.unicodeScalars.contains { scalar in
            (0x4E00...0x9FFF).contains(scalar.value) || (0x3400...0x4DBF).contains(scalar.value)
        }
    }

    static func isLatinLetter(_ ch: Character) -> Bool {
        guard let scalar = ch.unicodeScalars.first, ch.unicodeScalars.count == 1 else { return false }
        return (scalar.value >= 65 && scalar.value <= 90) || (scalar.value >= 97 && scalar.value <= 122)
    }

    static func letterCount(_ text: String) -> Int {
        text.filter(isLatinLetter).count
    }

    static func isFourCharIdiom(_ text: String) -> Bool {
        let significant = text.filter { $0.isLetter || $0.isNumber }
        guard significant.count == 4 else { return false }
        if significant.contains(where: { isLatinLetter($0) || $0.isNumber }) { return false }
        return significant.allSatisfy(isHan)
    }

    static func englishGapSeconds(_ text: String, secondsPerLetter: Int, minimum: Int) -> Int {
        let perLetter = min(5, max(1, secondsPerLetter))
        let floor = min(30, max(3, minimum))
        let letters = max(1, letterCount(text))
        return min(90, max(floor, letters * perLetter))
    }

    static func writingSeconds(
        _ text: String,
        base: Int,
        idiom: Int,
        secondsPerLetter: Int = 1,
        englishMinimum: Int? = nil
    ) -> Int {
        if text.contains(where: isLatinLetter) && !text.contains(where: isHan) {
            return englishGapSeconds(text, secondsPerLetter: secondsPerLetter, minimum: englishMinimum ?? base)
        }
        let safeBase = min(30, max(3, base))
        let safeIdiom = min(60, max(safeBase, idiom))
        if isFourCharIdiom(text) { return safeIdiom }
        if text.filter(isHan).count >= 6 { return safeIdiom }
        return safeBase
    }

    static func spellLetters(_ text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        if trimmed.contains(where: \.isWhitespace) { return nil }
        let letters = trimmed.filter(isLatinLetter)
        if letters.count < 2 { return nil }
        return letters.map { String($0).uppercased() }.joined(separator: ", ")
    }
}
