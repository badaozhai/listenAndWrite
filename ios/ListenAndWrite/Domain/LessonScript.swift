import Foundation

enum LessonScript {
    static func lines(
        text: String,
        lang: Lang,
        meaning: String,
        contrast: String,
        place: GroupPlace,
        options: LessonOptions
    ) -> [SpokenLine] {
        let guide = StudyGuide.lookup(text, lang: lang)
        let tip = (guide?.groupTip.isEmpty == false ? guide?.groupTip : nil) ?? contrast
        if lang == .zh {
            return chinese(text, meaning: meaning, contrast: contrast, tip: tip, place: place, options: options)
        }
        return english(text, meaning: meaning, contrast: contrast, tip: tip, place: place, options: options)
    }

    static func placeOf(_ focuses: [String], index: Int) -> GroupPlace {
        let focus = index < focuses.count ? focuses[index] : ""
        if focus.isEmpty { return .alone }
        let prev = index > 0 && focuses[index - 1] == focus
        let next = index + 1 < focuses.count && focuses[index + 1] == focus
        if !prev && next { return .head }
        if prev { return .rest }
        return .alone
    }

    private static func chinese(
        _ text: String,
        meaning: String,
        contrast: String,
        tip: String,
        place: GroupPlace,
        options: LessonOptions
    ) -> [SpokenLine] {
        var out: [SpokenLine] = []
        if options.explainChinese && place == .head && !tip.isEmpty {
            out.append(SpokenLine(text: "下面几个词一起记。\(tip)", lang: .zh, hint: "先听这一组"))
        }
        let times = min(3, max(1, options.repeats))
        for turn in 0..<times {
            out.append(SpokenLine(text: text, lang: .zh, hint: turn == 0 ? "请听" : "再听一次"))
        }
        if !options.explainChinese { return out }
        let contrastPart = place == .alone ? contrast : ""
        let explain = joinParts(meaning, contrastPart)
        if !explain.isEmpty {
            out.append(SpokenLine(text: explain, lang: .zh, hint: "听讲解，先别写"))
            out.append(SpokenLine(text: text, lang: .zh, hint: "再听一次，准备写"))
        }
        return out
    }

    private static func english(
        _ text: String,
        meaning: String,
        contrast: String,
        tip: String,
        place: GroupPlace,
        options: LessonOptions
    ) -> [SpokenLine] {
        var out: [SpokenLine] = []
        let times = min(3, max(1, options.repeats))
        let gloss = meaning.trimmingCharacters(in: .whitespaces).isEmpty
            ? contrast.trimmingCharacters(in: .whitespaces)
            : meaning.trimmingCharacters(in: .whitespaces)
        if options.englishCue != .chinese && place == .head && !tip.isEmpty {
            out.append(SpokenLine(text: tip, lang: .zh, hint: "先听这一组"))
        }
        for turn in 0..<times {
            let again = turn > 0
            switch options.englishCue {
            case .english:
                out.append(SpokenLine(text: text, lang: .en, hint: again ? "再听一次" : "请听英文"))
            case .chinese:
                if gloss.isEmpty {
                    out.append(SpokenLine(text: text, lang: .en, hint: "没有中文，改为报英文"))
                } else {
                    out.append(SpokenLine(text: gloss, lang: .zh, hint: again ? "再听一次" : "请听中文"))
                }
            case .both:
                out.append(SpokenLine(text: text, lang: .en, hint: again ? "再听英文" : "请听英文"))
                if !gloss.isEmpty {
                    out.append(SpokenLine(text: gloss, lang: .zh, hint: "请听中文"))
                }
            }
        }
        if options.spellEnglish && options.englishCue != .chinese, let spelled = WordText.spellLetters(text) {
            out.append(SpokenLine(text: spelled, lang: .en, hint: "拼读字母"))
        }
        return out
    }

    private static func joinParts(_ meaning: String, _ contrast: String) -> String {
        let left = meaning.trimmingCharacters(in: .whitespaces)
        let right = contrast.trimmingCharacters(in: .whitespaces)
        if left.isEmpty { return right }
        if right.isEmpty { return left }
        let gap = (left.last == "。" || left.last == "！") ? "" : "。"
        return left + gap + right
    }
}

func practiceOrder<T>(_ words: [T], shuffle: Bool, focusOf: (T) -> String) -> [T] {
    if !shuffle { return words }
    var blocks: [[T]] = []
    for word in words {
        let focus = focusOf(word)
        if var last = blocks.last, !focus.isEmpty, focusOf(last[last.count - 1]) == focus {
            last.append(word)
            blocks[blocks.count - 1] = last
        } else {
            blocks.append([word])
        }
    }
    return blocks.shuffled().flatMap { $0 }
}
