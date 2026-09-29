# ScreenshotX

> **Bring vendor-style smart screenshots to stock / AOSP ROMs.**

**ScreenshotX** is an LSPosed (Xposed) module that adds the convenient screenshot features usually found only in vendor ROMs (MIUI, ColorOS, One UI…) to **stock / near-stock Android** — AOSP, crDroid, LineageOS, Pixel Experience and similar.

It hooks the system framework to intercept the screenshot request and capture the screen in milliseconds, shows a floating thumbnail preview, and lets you edit it or auto-save — no vendor app required.

---

## ✨ Features

- **Two triggers, each independently toggleable**
  - **Power + Volume Down** — replaces the system screenshot
  - **Three-finger swipe down** — global gesture with AOSP-style detection
- **Instant haptic feedback** the moment a trigger is recognized
- **Floating thumbnail preview** in the screen corner
  - 2 s countdown with a shrinking progress bar
  - 0.4 s fade-out, then auto-saves at 2.4 s
  - **Tap the preview** to open the editor immediately
- **Built-in editor** — pens (ballpoint / highlighter / pencil / fountain / eraser), mosaic (pixel / blur / black bar / smudge / rectangle), and crop with aspect ratios
- **Auto-save to gallery** (`Pictures/Screenshots`)
- **Optional secure / DRM capture** — strips `FLAG_SECURE` so protected apps no longer come out black (hardware-protected Widevine L1 streams still stay black)
- **Trilingual UI** — English, 中文, Русский
- Liquid-glass styled settings (built with [GlassButtons](https://github.com/GJR787878/GlassButtons))

## 📱 Requirements

- Android 12+ (minSdk 31)
- [LSPosed](https://lsposed.org/) framework
- **Root recommended** (Magisk). The fastest capture path calls `SurfaceControl` directly inside the system server and needs **no root**; root is used as a fallback and to persist settings.

## 🚀 Installation

1. Install the APK and grant **Root** in the Magisk prompt.
2. Open **LSPosed** and enable the **ScreenshotX** module.
3. In the module's scope, check **System framework** (`system`).
4. **Reboot** the phone (or restart the system framework).
5. Press Power + Volume Down, or swipe down with three fingers.

Use **Test screenshot** inside the app to verify that capture works.

## 🔐 Permissions

| Permission | Why |
|---|---|
| Hook system framework (LSPosed) | Intercept the screenshot key/gesture and capture inside `system_server` |
| Root (Magisk) | Fallback capture via `screencap`, persist settings, pre-grant overlay permission |
| Display over other apps | Floating thumbnail preview |
| Vibrate | Haptic feedback on trigger |
| Foreground service (special use) | Keep the app ready so the preview opens reliably |

## ❓ FAQ

**Why is there a persistent notification?**
The keep-alive foreground service uses an `IMPORTANCE_MIN` channel — no sound, no status-bar icon, tucked at the bottom of the notification shade. It guarantees the floating preview can always be launched.

**Can it capture secure / DRM content?**
Enable **Capture secure / DRM content** in settings. It strips `FLAG_SECURE` inside the system server, so windows that normally capture as black (banking, privacy, many video apps) are captured normally. Truly hardware-protected streams — Widevine **L1** / a secure decoder rendering to a protected Surface — stay black: their frames live in a TrustZone-protected buffer and never enter normal memory, so no software can capture them. Widevine L3 and non-secure video work once the option is on.

**Why no Magisk superuser toast on every shot?**
A root shell is kept alive and reused, so the grant prompt appears only once.

## 🌐 Language

English · 中文 · Русский — switch with the button at the top of the settings screen.

---
---

# 中文

> **为原生 / 类原生系统带来厂商级的智能截屏。**

**ScreenshotX** 是一个 LSPosed（Xposed）模块，为**原生 / 类原生系统**（AOSP、crDroid、LineageOS、Pixel Experience 等）补上厂商 ROM（MIUI、ColorOS、One UI 等）才有的便捷截图功能。

它通过 hook 系统框架拦截截图请求，在毫秒级完成抓拍，弹出角落悬浮预览，可点击编辑或自动保存——无需厂商自带应用。

## ✨ 功能特性

- **两种触发方式，可各自独立开关**
  - **电源键 + 音量下**：替换系统截屏
  - **三指下滑**：全局手势，AOSP 风格判定
- 识别到动作的**瞬间即震动反馈**
- 屏幕角落**悬浮缩略图预览**
  - 2 秒倒计时，底部进度条收缩
  - 0.4 秒渐出，2.4 秒后自动保存
  - **点击预览**立即进入编辑器
- **内置编辑器**：画笔（圆珠笔 / 荧光笔 / 铅笔 / 钢笔 / 橡皮擦）、马赛克（像素 / 模糊 / 黑条 / 涂抹 / 矩形）、裁剪（含比例）
- **自动保存到相册**（`Pictures/Screenshots`）
- **可选的受保护 / DRM 截图**：剥离 `FLAG_SECURE`，让受保护 App 不再截成黑屏（硬件级 Widevine L1 视频仍会黑屏）
- **三语界面**：中文、English、Русский
- 毛玻璃风格设置界面（基于 [GlassButtons](https://github.com/GJR787878/GlassButtons)）

## 📱 系统要求

- Android 12 及以上（minSdk 31）
- [LSPosed](https://lsposed.org/) 框架
- **建议 Root**（Magisk）。最快抓拍路径在系统框架内直接调用 `SurfaceControl`，**无需 root**；root 用于兜底抓拍与持久化设置。

## 🚀 安装步骤

1. 安装 APK，在 Magisk 弹窗中授予 **Root**。
2. 打开 **LSPosed**，启用 **ScreenshotX** 模块。
3. 模块作用域勾选**「系统框架」**（`system`）。
4. **重启手机**（或重启系统框架）。
5. 按电源键 + 音量下，或三指下滑。

可在 App 内点**测试截屏**验证抓拍是否正常。

## 🔐 权限说明

| 权限 | 用途 |
|---|---|
| Hook 系统框架（LSPosed） | 拦截按键 / 手势截图，在 `system_server` 内抓拍 |
| Root（Magisk） | `screencap` 兜底抓拍、持久化设置、预授权悬浮窗 |
| 悬浮窗权限 | 显示角落悬浮预览 |
| 震动 | 触发时触觉反馈 |
| 前台服务（特殊用途） | 保持应用就绪，确保预览可靠弹出 |

## ❓ 常见问题

**为什么有一条常驻通知？**
保活前台服务使用 `IMPORTANCE_MIN` 渠道——不发声、状态栏不显示图标，折叠在通知栏最底部，用于保证悬浮预览随时可拉起。

**能截取加密 / DRM 内容吗？**
在设置中开启**「截取受保护 / DRM 内容」**即可。它会在系统框架内剥离 `FLAG_SECURE`，让平时截成黑屏的窗口（银行、隐私、多数视频 App）正常成像。但真正硬件级保护的视频——Widevine **L1** / 安全解码器输出到受保护 Surface——仍会黑屏：其帧数据位于 TrustZone 保护的缓冲区、永不进入普通内存，任何软件都无法截取。Widevine L3 与非安全视频在开启后可正常截取。

**为什么不是每次截图都弹 Magisk 超级用户提示？**
Root shell 常驻复用，授权提示只出现一次。

## 🌐 语言

中文 · English · Русский，可在设置界面顶部按钮切换。

---
---

# Русский

> **Функции умных скриншотов как у производителей — для чистых / AOSP-прошивок.**

**ScreenshotX** — модуль LSPosed (Xposed), который добавляет удобные функции скриншотов, обычно встречающиеся только в прошивках производителей (MIUI, ColorOS, One UI…), в **чистые / почти чистые системы** (AOSP, crDroid, LineageOS, Pixel Experience и подобные).

Модуль перехватывает запрос на скриншот через системный фреймворк, делает снимок за миллисекунды, показывает плавающее превью и позволяет редактировать или автоматически сохранить его — без приложений производителя.

## ✨ Возможности

- **Два способа запуска, каждый включается отдельно**
  - **Питание + Громкость вниз** — заменяет системный скриншот
  - **Свайп тремя пальцами вниз** — глобальный жест в стиле AOSP
- **Мгновенная вибрация** сразу при распознавании действия
- **Плавающее превью** в углу экрана
  - обратный отсчёт 2 с с уменьшающейся полосой прогресса
  - затухание 0,4 с, затем автосохранение на 2,4 с
  - **нажмите на превью**, чтобы сразу открыть редактор
- **Встроенный редактор** — ручки (шариковая / маркер / карандаш / перо / ластик), мозаика (пиксели / размытие / чёрная полоса / размазывание / прямоугольник) и обрезка с пропорциями
- **Автосохранение в галерею** (`Pictures/Screenshots`)
- **Опциональный захват защищённого / DRM-контента** — снимает `FLAG_SECURE`, чтобы защищённые приложения не получались чёрными (потоки с аппаратной защитой Widevine L1 остаются чёрными)
- **Три языка интерфейса** — English, 中文, Русский
- Настройки в стиле жидкого стекла (на основе [GlassButtons](https://github.com/GJR787878/GlassButtons))

## 📱 Требования

- Android 12+ (minSdk 31)
- Фреймворк [LSPosed](https://lsposed.org/)
- **Рекомендуется Root** (Magisk). Самый быстрый способ вызывает `SurfaceControl` напрямую внутри системного сервера и **не требует root**; root используется как запасной вариант и для хранения настроек.

## 🚀 Установка

1. Установите APK и разрешите **Root** в запросе Magisk.
2. Откройте **LSPosed**, включите модуль **ScreenshotX**.
3. В области действия модуля отметьте **«System framework»** (`system`).
4. **Перезагрузите** телефон (или системный фреймворк).
5. Нажмите Питание + Громкость вниз или сделайте свайп тремя пальцами вниз.

Кнопка **«Тест скриншота»** в приложении проверяет, работает ли захват.

## 🔐 Разрешения

| Разрешение | Зачем |
|---|---|
| Перехват системного фреймворка (LSPosed) | Перехват кнопки/жеста и захват внутри `system_server` |
| Root (Magisk) | Запасной захват через `screencap`, хранение настроек, выдача разрешения overlay |
| Поверх других приложений | Плавающее превью |
| Вибрация | Тактильный отклик при запуске |
| Передняя служба (спец. использование) | Поддержка приложения в готовности для надёжного превью |

## ❓ Частые вопросы

**Зачем постоянное уведомление?**
Фоновая служба использует канал `IMPORTANCE_MIN` — без звука и значка в строке состояния, свёрнута внизу шторки. Она гарантирует, что плавающее превью всегда запустится.

**Можно ли снимать защищённый / DRM-контент?**
Включите **«Снимать защищённый / DRM-контент»** в настройках. Модуль снимает `FLAG_SECURE` внутри системного сервера, и окна, которые обычно получаются чёрными (банковские, приватные, многие видеоприложения), захватываются нормально. Реальные потоки с аппаратной защитой — Widevine **L1** / защищённый декодер на защищённой Surface — остаются чёрными: их кадры находятся в буфере под защитой TrustZone и не попадают в обычную память, поэтому программно их снять невозможно. Widevine L3 и незащищённое видео работают после включения опции.

**Почему не появляется запрос суперпользователя Magisk при каждом снимке?**
Root-оболочка остаётся запущенной и переиспользуется, поэтому запрос появляется только один раз.

## 🌐 Язык

English · 中文 · Русский — переключение кнопкой вверху экрана настроек.
