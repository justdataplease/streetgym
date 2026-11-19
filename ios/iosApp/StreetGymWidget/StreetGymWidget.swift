import CoreText
import SwiftUI
import UIKit
import WidgetKit

/// The "Next class" home-screen widget (Android: widget/NextClassWidget.kt and WidgetRenderer.kt), drawn like Android's
/// card in the athlete's theme from the snapshot the app writes into the App Group's folder (composeApp, data/Widget.kt
/// decides what it says; iosApp/StreetGym/WidgetBridge.swift writes it). It doesn't link the app's Kotlin: everything it
/// shows, words and colours included, is in the snapshot. The coming days' flames are in it too, so the flame changes at
/// local midnight without the app; the app asks for a new timeline whenever what the widget shows changes.
@main
struct StreetGymWidgets: WidgetBundle {
    var body: some Widget {
        NextClassWidget()
    }
}

struct NextClassWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NextClass", provider: NextClassProvider()) { entry in
            NextClassView(entry: entry)
        }
        // Android's widget_label and widget_description (strings_widget.xml), word for word.
        .configurationDisplayName("Next class")
        .description("Your next class, its lead movement and your streak. Tap to open it.")
        // Android's four cells by one, resizable sideways: the wide medium widget, with Android's card across it.
        .supportedFamilies([.systemMedium])
        .contentMarginsDisabled()
    }
}

// MARK: - The snapshot

/// The app's snapshot (WidgetSnapshot.encode in data/Widget.kt).
struct Snapshot: Decodable {
    let v: Int
    let theme: String
    let colors: [String: String]
    let upper: Bool
    let tapeSpacing: Double
    let titleSize: Double
    let digits: Double
    let font: String
    let figure: String
    let tape: String
    let meta: String
    let headline: String
    let detail: String
    let locked: Bool
    let link: String
    let flames: [Flame]

    struct Flame: Decodable {
        let from: Int64
        let days: Int
        let state: String
        let spoken: String
        let body: String
        let core: String
        let outline: String?
        let shadow: String?
        let number: String
        let dashed: Bool

        var start: Date { Date(timeIntervalSince1970: TimeInterval(from) / 1000) }
    }

    func color(_ name: String) -> Color { Color(argb: colors[name] ?? "#FF000000") }
}

/// What one entry draws: the snapshot with the day's flame, the tile's picture and the display face's name.
struct Card {
    let snapshot: Snapshot
    let flame: Snapshot.Flame?
    let figure: UIImage?
    let face: String?
}

struct NextClassEntry: TimelineEntry {
    let date: Date
    let card: Card?
}

/// Reads the App Group's folder; nil before the app wrote anything, or without the App Group.
enum Stored {
    static let group = "group.com.justdataplease.streetgym"

    static func load() -> (Snapshot, UIImage?, String?)? {
        guard let folder = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group),
              let data = try? Data(contentsOf: folder.appendingPathComponent("snapshot.json")),
              let snapshot = try? JSONDecoder().decode(Snapshot.self, from: data), snapshot.v == 1 else { return nil }
        let figure = UIImage(contentsOfFile: folder.appendingPathComponent(snapshot.figure).path)
        return (snapshot, figure, face(folder.appendingPathComponent((snapshot.font as NSString).lastPathComponent)))
    }

    /// Registers the theme's display face for this process and gives its PostScript name.
    private static func face(_ url: URL) -> String? {
        guard let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider),
              let name = font.postScriptName as String? else { return nil }
        if UIFont(name: name, size: 12) == nil {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
        return name
    }
}

struct NextClassProvider: TimelineProvider {
    func placeholder(in context: Context) -> NextClassEntry {
        NextClassEntry(date: Date(), card: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (NextClassEntry) -> Void) {
        completion(entries().first ?? placeholder(in: context))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NextClassEntry>) -> Void) {
        // The app asks for a new timeline whenever what the widget shows changes; the flames' midnights are in this one.
        completion(Timeline(entries: entries(), policy: .never))
    }

    /// Now with today's flame, then one entry at each coming midnight whose flame differs.
    private func entries() -> [NextClassEntry] {
        let now = Date()
        guard let stored = Stored.load() else { return [NextClassEntry(date: now, card: nil)] }
        let (snapshot, figure, face) = stored
        func card(_ flame: Snapshot.Flame?) -> Card { Card(snapshot: snapshot, flame: flame, figure: figure, face: face) }
        let current = snapshot.flames.last { $0.start <= now } ?? snapshot.flames.first
        var entries = [NextClassEntry(date: now, card: card(current))]
        for flame in snapshot.flames where flame.start > now {
            entries.append(NextClassEntry(date: flame.start, card: card(flame)))
        }
        return entries
    }
}

// MARK: - The card

struct NextClassView: View {
    let entry: NextClassEntry

    var body: some View {
        if let card = entry.card {
            CardView(card: card)
                .padding(.horizontal, 12)
                .widgetURL(URL(string: card.snapshot.link))
                .widgetBackground(card.snapshot.color("page"))
        } else {
            // Before the app has written the widget (or without the App Group): the card's shape, nothing in it.
            EmptyCard()
                .padding(.horizontal, 12)
                .widgetURL(URL(string: "streetgym://today"))
                .widgetBackground(Color(argb: "#FFD9D7D2"))
        }
    }
}

/// Android's card (res/layout/widget_next_class.xml): 96 points with a 2-point outline and a hard 4-point shadow, the
/// 76-point tile, the tape and "LEVEL 4 · CLASS 20", the headline in the theme's display face, the third line, and the
/// streak's flame, or the lock square on a class beyond the tier.
struct CardView: View {
    let card: Card

    var body: some View {
        let s = card.snapshot
        ZStack(alignment: .topLeading) {
            Rectangle().fill(s.color("shadow")).padding(.leading, 4).padding(.top, 4)
            HStack(spacing: 0) {
                ZStack {
                    Rectangle().fill(s.color("border"))
                    Rectangle().fill(s.color("tile")).padding(2)
                    if let figure = card.figure {
                        Image(uiImage: figure).resizable().interpolation(.high).padding(2)
                    }
                }
                .frame(width: 76, height: 76)
                VStack(alignment: .leading, spacing: 3) {
                    // The tape steps aside before the numbers get smaller (Android: WidgetLayout.fitMeta).
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 6) {
                            tape(s)
                            meta(s)
                        }
                        meta(s)
                    }
                    Text(s.upper ? s.headline.uppercased() : s.headline)
                        .font(display(s.titleSize))
                        .foregroundColor(s.color("text"))
                        .lineLimit(1)
                    if !s.detail.isEmpty {
                        Text(s.detail)
                            .font(.custom("Roboto-Regular", fixedSize: 12.5))
                            .foregroundColor(s.color("muted"))
                            .lineLimit(1)
                    }
                }
                .padding(.horizontal, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                if s.locked {
                    ZStack {
                        Rectangle().fill(s.color("border"))
                        Rectangle().fill(s.color("action")).padding(2)
                        Image(systemName: "lock.fill").font(.system(size: 16, weight: .semibold)).foregroundColor(s.color("onAction"))
                    }
                    .frame(width: 44, height: 44)
                } else if let flame = card.flame {
                    FlameView(flame: flame, width: 46, shadow: 3, face: card.face, digits: s.digits)
                        .frame(height: 58)
                }
            }
            .padding(.leading, 10)
            .padding(.trailing, 12)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(s.color("surface"))
            .padding(2)
            .background(s.color("border"))
            .padding(.trailing, 4)
            .padding(.bottom, 4)
        }
        .frame(height: 100)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(card.flame?.spoken ?? s.headline))
    }

    private func tape(_ s: Snapshot) -> some View {
        Text(s.tape.uppercased())
            .font(display(11))
            .tracking(11 * s.tapeSpacing)
            .foregroundColor(s.color("onTape"))
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, 6)
            .frame(height: 17)
            .background(s.color("tape"))
    }

    private func meta(_ s: Snapshot) -> some View {
        Text(s.meta)
            .font(.custom("Roboto-Bold", fixedSize: 12))
            .tracking(12 * 0.06)
            .foregroundColor(s.color("muted"))
            .lineLimit(1)
            .minimumScaleFactor(0.75)
    }

    /// The theme's display face from the app; without it, Roboto Bold, as Android falls back to Typeface.DEFAULT_BOLD.
    private func display(_ size: Double) -> Font {
        if let face = card.face { return .custom(face, fixedSize: size) }
        return .custom("Roboto-Bold", fixedSize: size)
    }
}

/// The card's outline and tile with grey bars where the words go.
struct EmptyCard: View {
    var body: some View {
        let ink = Color(argb: "#FF121212")
        let pale = Color(argb: "#FFE9E7E2")
        ZStack(alignment: .topLeading) {
            Rectangle().fill(ink).padding(.leading, 4).padding(.top, 4)
            HStack(spacing: 12) {
                Rectangle().fill(pale).frame(width: 72, height: 72).padding(2).background(ink)
                VStack(alignment: .leading, spacing: 8) {
                    Rectangle().fill(Color(argb: "#FFFFD60A")).frame(width: 46, height: 14)
                    Rectangle().fill(pale).frame(height: 16)
                    Rectangle().fill(pale).frame(width: 110, height: 10)
                }
            }
            .padding(.leading, 10)
            .padding(.trailing, 12)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .background(Color(argb: "#FFF5F4F0"))
            .padding(2)
            .background(ink)
            .padding(.trailing, 4)
            .padding(.bottom, 4)
        }
        .frame(height: 100)
    }
}

// MARK: - The flame

/// The streak's flame as Android draws it on the widget (ui/StreakFlame.kt, StreakFlame.bitmap; the app's
/// ui/StreakViews.kt drawFlame): a main tongue and a small one over a belly with the days in it, lit, unlit, or a dashed
/// outline once out, with the poster themes' printed shadow. Its colours come with the snapshot.
struct FlameView: View {
    let flame: Snapshot.Flame
    let width: CGFloat
    let shadow: CGFloat
    let face: String?
    let digits: Double

    var body: some View {
        let stroke: CGFloat = flame.outline == nil ? 0 : max(1, min(2, width * 0.075))
        let inner = width - stroke
        let scale = inner / 100
        let printed: CGFloat = flame.shadow == nil ? 0 : shadow
        let size = CGSize(width: inner, height: inner * 1.2)
        let dash: [CGFloat] = flame.dashed ? [width * 0.11, width * 0.08] : []
        ZStack(alignment: .topLeading) {
            if let shadowColor = flame.shadow {
                FlameBody().fill(Color(argb: shadowColor))
                    .overlay(FlameBody().stroke(Color(argb: shadowColor), style: StrokeStyle(lineWidth: stroke, lineJoin: .round)))
                    .frame(width: size.width, height: size.height)
                    .offset(x: stroke / 2 + printed, y: stroke / 2 + printed)
            }
            if !flame.dashed {
                FlameBody().fill(Color(argb: flame.body))
                    .frame(width: size.width, height: size.height)
                    .offset(x: stroke / 2, y: stroke / 2)
                FlameCore().fill(Color(argb: flame.core))
                    .frame(width: size.width, height: size.height)
                    .offset(x: stroke / 2, y: stroke / 2)
            }
            if let outline = flame.outline {
                FlameBody().stroke(Color(argb: outline), style: StrokeStyle(lineWidth: stroke, lineJoin: .round, dash: dash))
                    .frame(width: size.width, height: size.height)
                    .offset(x: stroke / 2, y: stroke / 2)
            }
            // The days fill the belly: as tall as it allows, narrower when the digits need it.
            let numberSize = 34 * scale / CGFloat(max(digits, 0.5))
            Text("\(flame.days)")
                .font(.custom(face ?? "Roboto-Bold", fixedSize: numberSize))
                .foregroundColor(Color(argb: flame.number))
                .lineLimit(1)
                .minimumScaleFactor(0.3)
                .frame(width: 48 * scale, height: numberSize * 1.25)
                .position(x: stroke / 2 + 50.5 * scale, y: stroke / 2 + 84 * scale + numberSize * 0.06)
        }
        .frame(width: width + printed, height: width * 1.2 + printed, alignment: .topLeading)
    }
}

/// The flame's outline in a box 100 wide and 120 tall (ui/StreakViews.kt flameBody).
struct FlameBody: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 100
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * s, y: rect.minY + y * s) }
        var path = Path()
        path.move(to: p(52, 3))
        path.addCurve(to: p(88, 62), control1: p(56, 24), control2: p(80, 36))
        path.addCurve(to: p(50, 117), control1: p(96, 90), control2: p(78, 117))
        path.addCurve(to: p(12, 64), control1: p(22, 117), control2: p(4, 92))
        path.addCurve(to: p(27, 36), control1: p(15, 53), control2: p(22, 45))
        path.addCurve(to: p(40, 55), control1: p(29, 46), control2: p(33, 53))
        path.addCurve(to: p(52, 3), control1: p(37, 34), control2: p(41, 16))
        path.closeSubpath()
        return path
    }
}

/// The pale heart behind the days (ui/StreakViews.kt flameCore).
struct FlameCore: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 100
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * s, y: rect.minY + y * s) }
        var path = Path()
        path.move(to: p(50, 110))
        path.addCurve(to: p(26, 80), control1: p(33, 110), control2: p(22, 97))
        path.addCurve(to: p(38, 60), control1: p(28, 71), control2: p(34, 66))
        path.addCurve(to: p(51, 64), control1: p(41, 66), control2: p(46, 68))
        path.addCurve(to: p(75, 91), control1: p(55, 71), control2: p(75, 76))
        path.addCurve(to: p(50, 110), control1: p(75, 102), control2: p(64, 110))
        path.closeSubpath()
        return path
    }
}

// MARK: - Helpers

extension Color {
    /// "#AARRGGBB", as the snapshot writes colours.
    init(argb: String) {
        let value = UInt32(argb.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0xFF000000
        self.init(.sRGB,
                  red: Double((value >> 16) & 0xFF) / 255,
                  green: Double((value >> 8) & 0xFF) / 255,
                  blue: Double(value & 0xFF) / 255,
                  opacity: Double((value >> 24) & 0xFF) / 255)
    }
}

extension View {
    /// The widget's background: iOS 17's container background, a plain background before it.
    @ViewBuilder
    func widgetBackground(_ color: Color) -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            containerBackground(color, for: .widget)
        } else {
            background(color)
        }
    }
}
