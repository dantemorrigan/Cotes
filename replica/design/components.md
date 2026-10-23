# Cotes — компоненты

Токены: `tokens.json` (светлая), `tokens.dark.json` (тёмная). В коде они
живут как CSS-переменные в начале `assets/css/style.css`; тёмная тема
включается по `prefers-color-scheme` или `data-theme="dark"` на `<html>`.
Tailwind не используется: проект без сборки и должен открываться с `file://`.

Иконка Cotes (`assets/images/logo.svg`) не меняется. Чёрный силуэт кота стоит
на белой плашке с радиусом `sm` в обеих темах, поэтому виден и на тёмном фоне.
Иконки интерфейса — контурные SVG 24×24 в стиле Lucide (MIT), встроены inline.

```
Button
  variants  primary (accent-soft / on-accent-soft), solid (accent / on-accent),
            ghost (прозрачная, hover surface-2), icon (36×36, ghost), danger (текст danger)
  sizes     md 36px, lg 48px (лендинг)
  states    default, hover, active (scale .98), focus-visible (2px ring accent), disabled
  tokens    radius md, font sm/600
  a11y      настоящий <button>/<a>; у icon-кнопок aria-label и title с сочетанием клавиш
  used on   S01, S02

Search field
  states    пусто, ввод (кнопка-крестик), фокус (ring accent)
  keys      Ctrl/Cmd+K или «/» — фокус; Esc — очистить, затем снять фокус
  a11y      type=search, aria-label, результаты объявляются через aria-live счётчик
  used on   S02

Note list item
  variants  обычный, активный (surface-2 + полоса accent слева), закреплён (значок)
  content   заголовок (или первая строка текста, или «Без названия»), 2 строки превью,
            дата изменения; совпадения поиска — <mark> цвета mark
  states    hover, focus-visible, active
  a11y      <button> внутри <li>, aria-current у активного; ↑/↓ перемещают выбор
  used on   S02

Editor
  parts     заголовок (input, xl/700), текст (textarea, base 17/28, ширина ≤ 70ch)
  states    пусто (плейсхолдеры), заполнено, ничего не выбрано → пустое состояние
  behaviour автосохранение через 400 мс после ввода, плюс на blur/pagehide/visibilitychange;
            Ctrl/Cmd+S — сохранить сразу; Tab в тексте вставляет табуляцию не нужен (оставляем фокус-навигацию)
  used on   S02

Status bar
  content   «Сохранено» / «Сохранение…» / ошибка хранилища (danger), «Изменено: …»,
            «N слов · M символов»
  a11y      role=status (вежливое объявление только статуса сохранения)

Toast
  variants  info, с действием («Отменить»), error
  behaviour снизу по центру, 6 с, одна за раз; действие доступно с клавиатуры
  a11y      role=status, aria-live=polite

Empty state
  variants  нет заметок (CTA «Новая заметка»), нет результатов (показать запрос,
            «Сбросить поиск»), ничего не выбрано (подсказка с сочетаниями клавиш)

Menu (⋯)
  items     Закрепить/Открепить, Экспорт заметки (.txt), Экспорт всех (.json),
            Импорт (.json), Тема: системная/светлая/тёмная, Удалить
  a11y      <details>/<summary>-подобное меню на кнопках, Esc закрывает
```

Отступление от скилла: отдельная страница-витрина примитивов не делалась,
проверка — скриншоты реальных экранов в обеих темах (replica-test).
