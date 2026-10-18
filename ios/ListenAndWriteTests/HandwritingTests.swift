import UIKit
import XCTest
@testable import ListenAndWrite

final class HandwritingStyleTests: XCTestCase {
    func testExistingInkAndShippedGrader() throws {
        let root = Repo.resources
        try checkChinese(root.appendingPathComponent("chinese-chars"))
        try checkEnglish(root.appendingPathComponent("english-hands"))
        try checkOriginal(root.appendingPathComponent("handwriting"))
        print("HANDWRITE passed")
    }

    private func checkChinese(_ directory: URL) throws {
        let rows = try TSV.load(directory.appendingPathComponent("catalog.tsv"))
        XCTAssertGreaterThan(rows.count, 400)
        for row in rows {
            let char = Character(row[0])
            XCTAssertGreaterThan(ink(directory.appendingPathComponent(row[3])), 40, "正字 \(char)")
            XCTAssertGreaterThan(ink(directory.appendingPathComponent(row[4])), 40, "不规范 \(char)")
            let right = gradeDetail(gradeOne(String(char), seenChars: [SeenChar(char: char, confidence: 0.95)], chinese: true), chinese: true)
            XCTAssertEqual(right, "写对")
            if row.count > 6, !row[6].isEmpty {
                XCTAssertGreaterThan(ink(directory.appendingPathComponent(row[5])), 40, "错字 \(char)")
                let other = Character(row[6])
                let detail = gradeDetail(gradeOne(String(char), seenChars: [SeenChar(char: other, confidence: 0)], chinese: true), chinese: true)
                XCTAssertTrue(detail.contains("写成了") && detail.contains(String(other)), detail)
                if char == "势" { print("CHINESE 势 png detail=\(detail)") }
            }
        }
    }

    private func checkEnglish(_ directory: URL) throws {
        let rows = try TSV.load(directory.appendingPathComponent("catalog.tsv"))
        let library = try WordLibrary.load(from: Repo.dataDirectory)
        let wanted = Set((library.english.map(\.text) + library.drills.map(\.text)).map { text in
            text.filter { $0.isLetter }.lowercased()
        })
        XCTAssertEqual(Set(rows.map { $0[0] }), wanted)
        for row in rows {
            let files = [row[2], row[3], row[4], row[5]]
            let blobs = try files.map { try Data(contentsOf: directory.appendingPathComponent($0)) }
            for blob in blobs {
                XCTAssertGreaterThan(ink(data: blob), 40, row[0])
            }
            for left in blobs.indices {
                for right in left + 1 ..< blobs.count {
                    XCTAssertNotEqual(blobs[left], blobs[right], row[0])
                }
            }
            let key = row[0]
            let example = row[1]
            let index = Int(row[8]) ?? 0
            for confidence in [Float(0.95), Float(0.9)] {
                let detail = gradeDetail(gradeOne(example, seenChars: key.map { SeenChar(char: $0, confidence: confidence) }, chinese: false), chinese: false)
                XCTAssertEqual(detail, "写对", example)
            }
            let messyChars = row[6].enumerated().map { offset, letter in
                SeenChar(char: letter, confidence: offset == index ? 0.2 : 0.95)
            }
            let messyDetail = gradeDetail(gradeOne(example, seenChars: messyChars, chinese: false), chinese: false)
            XCTAssertEqual(messyDetail, "没认清")
            let wrong = gradeOne(example, seenChars: row[7].map { SeenChar(char: $0, confidence: 0) }, chinese: false)
            let wrongDetail = gradeDetail(wrong, chinese: false)
            XCTAssertEqual(wrong.mark, .wrong)
            XCTAssertTrue(wrongDetail.contains("写成了") && wrongDetail.contains(String(row[7][row[7].index(row[7].startIndex, offsetBy: index)])))
            if key == "may" {
                print("ENGLISH may png 写对 / \(messyDetail) / \(wrongDetail)")
            }
        }
    }

    private func checkOriginal(_ directory: URL) throws {
        let rows = try TSV.load(directory.appendingPathComponent("readings.tsv")).filter { $0[0] != "sheet" && !$0[0].hasPrefix("#") }
        for row in rows {
            XCTAssertGreaterThan(ink(directory.appendingPathComponent(row[1])), 200, row[1])
            let confidence = Float(row.count > 4 ? row[4] : "0") ?? 0
            let seen = row[3].map { SeenChar(char: $0, confidence: confidence) }
            let detail = gradeDetail(gradeOne(row[2], seenChars: seen, chinese: true), chinese: true)
            print("ORIGINAL expected=\(row[2]) seen=\(row[3]) detail=\(detail)")
            switch row[0] {
            case "wrong":
                XCTAssertTrue(detail.contains("写成了「已」"))
            case "messy":
                XCTAssertEqual(detail, "笔画没到位")
            case "right":
                XCTAssertEqual(detail, "写对")
            default:
                break
            }
        }
    }

    private func ink(_ url: URL) -> Int {
        (try? Data(contentsOf: url)).map(ink(data:)) ?? 0
    }

    private func ink(data: Data) -> Int {
        guard let image = UIImage(data: data), let cg = image.cgImage else { return 0 }
        guard let pointer = CFDataGetBytePtr(cg.dataProvider?.data as CFData?) else { return 0 }
        let width = cg.width
        let height = cg.height
        let bytesPerPixel = max(1, cg.bitsPerPixel / 8)
        let rowBytes = cg.bytesPerRow
        var count = 0
        for y in 0..<height {
            for x in 0..<width {
                let offset = y * rowBytes + x * bytesPerPixel
                let red = Int(pointer[offset])
                let green = bytesPerPixel > 1 ? Int(pointer[offset + 1]) : red
                let blue = bytesPerPixel > 2 ? Int(pointer[offset + 2]) : red
                if red + green + blue < 680 { count += 1 }
            }
        }
        return count
    }
}

enum Repo {
    static var root: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
    }
    static var dataDirectory: URL {
        root.appendingPathComponent("Android/app/src/main/java/app/listenandwrite/data")
    }
    static var resources: URL {
        root.appendingPathComponent("Android/app/src/test/resources")
    }
}

enum TSV {
    static func load(_ url: URL) throws -> [[String]] {
        let rows = try String(contentsOf: url, encoding: .utf8)
            .split(whereSeparator: \.isNewline)
            .map { String($0) }
            .filter { !$0.isEmpty && !$0.hasPrefix("#") }
            .map { $0.split(separator: "\t", omittingEmptySubsequences: false).map(String.init) }
        if let head = rows.first?.first, ["char", "key", "kind"].contains(head) {
            return Array(rows.dropFirst())
        }
        return rows
    }
}
