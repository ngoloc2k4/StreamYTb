# Translating StreamYTb 🌐

Thank you for your interest in making **StreamYTb** accessible to users worldwide!

We use [Crowdin](https://crowdin.com) to manage translations. You can easily translate StreamYTb into your native language directly through your web browser without having to write code or submit manual pull requests.

---

## 🚀 How to Contribute

1. **Join the Crowdin Project:**
   - Visit the StreamYTb Crowdin project page (or ask the maintainers for the project link).
   - Sign up or log in with your GitHub or Crowdin account.

2. **Select Your Language:**
   - Choose the language you want to translate into from the language list.
   - If your language is not listed, open an issue or request it on Crowdin.

3. **Start Translating:**
   - Click on `strings.xml` to view strings that need translation.
   - Enter your suggestion and submit. Other contributors can review and vote on translations.

---

## 📌 Translation Guidelines

Please follow these important rules to avoid formatting issues in the app:

1. **Do Not Translate Brand Names:**
   - Keep names like `StreamYTb`, `NewPipe`, `YouTube`, `Opus`, `M4A`, `HLS`, `AAC` intact.

2. **Preserve Format Specifiers:**
   - Tokens such as `%1$s`, `%2$s`, `%1$d`, `%2$d`, `%1$.1f` are placeholders substituted at runtime (numbers, titles, durations).
   - Keep these format specifiers exactly as they appear in the source string.
   - *Example:*
     - English: `Subscriptions (%1$d)`
     - Vietnamese: `Kênh Đăng Ký (%1$d)`
     - French: `Abonnements (%1$d)`

3. **Escape Special Characters:**
   - In Android XML, single quotes `'` must be escaped with a backslash: `\'` (e.g. `don\'t`).
   - If using literal ampersands, use `&amp;`.

4. **Keep Text Concise:**
   - Mobile screens have limited space (especially for buttons, chips, and tabs). Aim for concise phrasing that fits naturally.

---

## 🔄 Automated Synchronization

- **Source Strings (`values/strings.xml`):** Written in **English (Base)**. Whenever strings are added or modified on the `main` branch, they are pushed to Crowdin.
- **Completed Translations (`values-%android_code%/strings.xml`):** Automatically pulled from Crowdin and proposed via automated Pull Requests.
