// Тема: системная / светлая / тёмная. Подключается в <head> без defer,
// чтобы тема применилась до первой отрисовки.
(function () {
  var KEY = 'cotes:theme';
  var ORDER = ['system', 'light', 'dark'];
  var LABELS = { system: 'Тема: как в системе', light: 'Тема: светлая', dark: 'Тема: тёмная' };
  var root = document.documentElement;

  function read() {
    try { var v = localStorage.getItem(KEY); return ORDER.indexOf(v) > -1 ? v : 'system'; }
    catch (e) { return 'system'; }
  }

  function apply(pref) {
    root.dataset.themePref = pref;
    if (pref === 'system') delete root.dataset.theme;
    else root.dataset.theme = pref;
    var buttons = document.querySelectorAll('[data-theme-toggle]');
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].setAttribute('aria-label', LABELS[pref]);
      buttons[i].title = LABELS[pref];
    }
  }

  function cycle() {
    var next = ORDER[(ORDER.indexOf(read()) + 1) % ORDER.length];
    try { localStorage.setItem(KEY, next); } catch (e) { /* тема просто не запомнится */ }
    apply(next);
  }

  apply(read());

  document.addEventListener('DOMContentLoaded', function () {
    apply(read());
    document.addEventListener('click', function (e) {
      if (e.target.closest && e.target.closest('[data-theme-toggle]')) cycle();
    });
  });

  window.addEventListener('storage', function (e) {
    if (e.key === KEY || e.key === null) apply(read());
  });
})();
