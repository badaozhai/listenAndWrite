import Foundation

enum VoiceMatch {
    private static let phrases = ["下一个", "下一题", "好了", "好啦", "好的", "可以了", "写好了", "行了", "next", "done"]

    static func isAdvance(_ raw: String) -> Bool {
        let normalized = raw.lowercased().replacingOccurrences(
            of: #"[\s\p{P}]+"#,
            with: "",
            options: .regularExpression
        )
        if normalized.isEmpty { return false }
        if phrases.contains(where: { normalized.contains($0) }) { return true }
        return normalized == "ok" || normalized == "okay"
    }
}
