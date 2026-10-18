import Foundation

struct LastPlay: Equatable {
    var units: Set<Int> = []
    var random: Bool = false
    var mode: PlayMode = .timer
    var cue: EnglishCue = .both
}

func unitsCaption(_ units: Set<Int>, chinese: Bool) -> String {
    let titles = units.filter { $0 > 0 }.sorted().map { slotTitle($0, chinese: chinese) }
    if titles.isEmpty { return "" }
    if titles.count <= 3 { return titles.joined(separator: "、") }
    return titles.prefix(2).joined(separator: "、") + " 等\(titles.count)个"
}

func playCaption(_ play: LastPlay, chinese: Bool, withCue: Bool) -> String {
    let units = unitsCaption(play.units, chinese: chinese)
    let order = play.random ? "随机" : "顺序"
    let cue = withCue ? " · \(play.cue.label)" : ""
    return [units, play.mode.label, order].filter { !$0.isEmpty }.joined(separator: " · ") + cue
}
