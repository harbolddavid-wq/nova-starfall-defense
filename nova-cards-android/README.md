# Nova Card Collection 0.2.0 — four games, one Android APK

Canasta: preserved user-approved family house-rules engine, exactly as v0.1.5.
Blackjack: six-deck shoe, 3:2 natural blackjack, dealer stands soft 17, virtual chips, Hit/Stand/Double/one Split per round.
Texas Hold'em: four seats, 10/20 blinds, fixed-limit raises up to three per street, computer opponents, five-of-seven evaluator, split pots and side pots. Virtual chips.
Klondike Solitaire: standard seven columns, draw-one stock recycling, alternating color descending runs, A-K foundations, tap-to-move, undo.
One obvious Change Games button opens Dealer's Choice; original Table & Cards gallery remains available to all games. Each game's local state is independent.
All games work offline and have no real-money wagers, accounts, or ads.
Tests: 15 Blackjack rounds, 10 Poker hands with total chips conserved, 52-card Solitaire draw/undo, full UI mobile smoke tests, existing 31 Canasta rules tests. This is a family INSTALL TEST; three new games still need the owner's real phone test.
Package identity site.novasky.cardcollection. The debug key is generated separately by each GitHub runner; future in-place updates require privately persistent signing, not configured yet. Do not promise an in-place upgrade. Live Family Store and main branch unchanged.
