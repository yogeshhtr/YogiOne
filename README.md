# Yogi One ✨

<img src="icon_preview.png" width="120" align="right" />

**Your everyday everything app.** Five tabs, one dark and calm design, no ads, no account.
Everything you type stays on your phone.

## 🏠 Today
* Greeting with your name (tap the name to change it), a live clock and the date.
* **Today in numbers**: habits done, minutes of focus and money spent today. Tap one to open its tab.
* **Weather** (Open-Meteo, free): now, feels-like, humidity, wind, sunrise and sunset, plus 6 days with ☔ rain chance.
  Tap the card to pick any city in the world.
* **Exchange rates**: today's official **Nepal Rastra Bank** buying and selling rates (USD, EUR, GBP, AUD, CAD, INR,
  JPY, CNY, QAR, AED, SAR, MYR, KRW), plus a **currency converter** both ways (to NPR at the buying rate, from NPR at
  the selling rate).
* **Thought for today**: a new line every day.
* **To-do list**: add, tap to tick off, long-press to delete, clear the finished ones.

## ⏳ Focus
* Pomodoro timer with a big ring: 15, 25, 45, 60 or 90 minutes of focus, and 5 to 20 minutes of break.
* Start, pause, reset and skip. The screen stays on while it runs.
* It **keeps counting when the app is closed**. A sound and vibration mark the end.
* Progress: minutes and sessions today, 🔥 day streak, total hours, and a 7-day bar chart.

## ✅ Habits
* Starts with 4 habits (water, exercise, reading, meditation). Add your own with 24 icons to choose from.
* Tap a card = done today. Each card shows the 🔥 streak, total days, and the **last 7 days as dots** (tap a dot to
  fix that day).
* Long-press a card to edit or delete it. A progress bar shows how much of today is done.

## 💰 Money
* Add **expenses** (12 categories, including 🙏 Puja) and **income** in rupees, with an optional note.
* Monthly view (◀ ▶ to see past months): income, spent, balance.
* **Monthly budget** with a coloured bar (green, then gold, then red), and "where it went" by category with
  percentages and the daily average.
* Transactions grouped by day; long-press one to delete it. Rupees use Indian grouping (Rs 1,25,000).

## 🧰 Tools
| | |
|---|---|
| 🔦 **Torch** – phone flashlight | 🧭 **Compass** – live dial, degrees and N/NE/E… |
| 📐 **Level** – bubble level, green when flat | 📏 **Units & land** – **Ropani / Aana / Paisa / Daam, Bigha / Kattha / Dhur**, sq ft, acre…; length incl. haat; weight incl. **tola / pau / dharni**; temperature |
| 🏦 **Loan EMI** – monthly instalment, total interest | 🧾 **Split bill** – with tip % |
| ⏱ **Stopwatch** – with laps | 🎂 **Age** – exact age, days lived, weekday born, days to next birthday |
| ⚖️ **BMI** – with your healthy weight range | 🎲 **Dice & coin** – 1–3 dice, or a coin flip |

## Building
Put this folder in its own GitHub repository. **Actions** then builds **YogiOne.apk** and publishes it as Release `vNN`.
* Package `com.yogi.one`, so it installs next to Yogi TV and AMMA.
* Signed with `yogione.p12` (the same key as Yogi TV), so every new APK installs over the old one and keeps your data.
* Kotlin, Android 5.0+, no XML layouts and only two libraries (AndroidX core + appcompat). The APK is small.

Code: `app/src/main/java/com/yogi/one/`
* `MainActivity` – the tabs
* `TodayTab`, `FocusTab`, `HabitsTab`, `MoneyTab`, `ToolsTab` – the five screens
* `Data.kt` – storage, weather, NRB rates, quotes
* `Views.kt` – ring, chart, level, compass
* `Ui.kt` – colours and building blocks
