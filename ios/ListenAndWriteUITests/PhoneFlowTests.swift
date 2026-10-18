import XCTest

final class PhoneFlowTests: XCTestCase {
    func testChineseSessionKeepsPlayingWhenMinimized() {
        let app = XCUIApplication()
        app.launch()

        let chinese = app.buttons["subject-chinese"]
        XCTAssertTrue(chinese.waitForExistence(timeout: 8))
        XCTAssertTrue(app.buttons["subject-english"].exists)
        chinese.tap()

        let lesson = app.buttons["unit-1"]
        XCTAssertTrue(lesson.waitForExistence(timeout: 8))
        lesson.tap()
        app.buttons["start-timer"].tap()

        let minimize = app.buttons["minimize"]
        XCTAssertTrue(minimize.waitForExistence(timeout: 8))
        XCTAssertTrue(app.staticTexts["请听"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.staticTexts["请写在本子上"].exists)

        let started = Date()
        XCTAssertTrue(app.staticTexts["请写在本子上"].waitForExistence(timeout: 30))
        XCTAssertGreaterThan(Date().timeIntervalSince(started), 0.6)

        minimize.tap()
        let expand = app.buttons["expand-session"]
        XCTAssertTrue(expand.waitForExistence(timeout: 5))
        XCTAssertFalse(minimize.exists)
        sleep(3)
        XCTAssertTrue(expand.exists)
    }
}
