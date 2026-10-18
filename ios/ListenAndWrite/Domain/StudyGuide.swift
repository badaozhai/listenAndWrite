import Foundation

struct GuideHit {
    var meaning: String = ""
    var contrast: String = ""
    var focus: String = ""
    var groupTip: String = ""
}

enum StudyGuide {
    private struct Member {
        var character: Character
        var say: String
    }
    private struct Family {
        var tip: String
        var members: [Member]
    }

    private static let families: [Family] = [
        Family(tip: "势是气势和力量，事是事情。势不可挡的势，不要写成事情的事。", members: [
            Member(character: "势", say: "这里的势，是气势的势，不是事情的事。"),
            Member(character: "事", say: "这里的事，是事情的事，不是气势的势。"),
        ]),
        Family(tip: "做是做事、做人。作是作业、工作、作用。", members: [
            Member(character: "做", say: "这里的做，是做事的做，不是作业的作。"),
            Member(character: "作", say: "这里的作，是作业的作，不是做事的做。"),
        ]),
        Family(tip: "在是存在、现在。再是又一次。", members: [
            Member(character: "在", say: "这里的在，是现在的在，不是再次的再。"),
            Member(character: "再", say: "这里的再，是再次的再，不是现在的在。"),
        ]),
        Family(tip: "像是好像。象是大象，也用于气象、印象。", members: [
            Member(character: "像", say: "这里的像，是好像的像，不是大象的象。"),
            Member(character: "象", say: "这里的象，是大象的象，不是好像的像。"),
        ]),
        Family(tip: "己是自己。已是已经。两个字开口的方向不一样。", members: [
            Member(character: "己", say: "这里的己，是自己的己，不是已经的已。"),
            Member(character: "已", say: "这里的已，是已经的已，不是自己的己。"),
        ]),
        Family(tip: "未是未来、没有。末是末尾。", members: [
            Member(character: "未", say: "这里的未，是未来的未，不是末尾的末。"),
            Member(character: "末", say: "这里的末，是末尾的末，不是未来的未。"),
        ]),
    ]

    static func lookup(_ text: String, lang: Lang) -> GuideHit? {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.isEmpty || lang == .en { return nil }
        let found = clean.flatMap { ch in
            families.compactMap { family in family.members.first { $0.character == ch }.map { (family, $0) } }
        }
        var seen = Set<Character>()
        let unique = found.filter { seen.insert($0.1.character).inserted }.prefix(2)
        if unique.isEmpty { return nil }
        let primary = unique.first
        return GuideHit(
            contrast: unique.map(\.1.say).joined(),
            focus: primary.map { String($0.1.character) } ?? "",
            groupTip: primary?.0.tip ?? ""
        )
    }
}
