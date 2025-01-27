class NotesManager {
  constructor() {
    try {
      this.notes = JSON.parse(localStorage.getItem('notes')) || [];
    } catch (e) {
      this.notes = [];
      console.error('Ошибка чтения localStorage:', e);
    }
  }

  save(note) {
    try {
      if (note.id) {
        const index = this.notes.findIndex(n => n.id === note.id);
        if (index > -1) this.notes[index] = note;
      } else {
        note.id = Date.now().toString();
        this.notes.unshift(note);
      }
      localStorage.setItem('notes', JSON.stringify(this.notes));
      return true;
    } catch (e) {
      console.error('Ошибка сохранения:', e);
      return false;
    }
  }

  delete(id) {
    this.notes = this.notes.filter(note => note.id !== id);
    localStorage.setItem('notes', JSON.stringify(this.notes));
  }

  search(query) {
    return this.notes.filter(note => 
      note.title.toLowerCase().includes(query.toLowerCase()) ||
      note.content.toLowerCase().includes(query.toLowerCase())
    );
  }

  getNoteById(id) {
    return this.notes.find(note => note.id === id);
  }
}

const notesManager = new NotesManager();

// ===== АНИМАЦИИ ПЕРЕХОДОВ =====
function handleNavigation(event) {
  const link = event.target.closest('a');
  if (!link || link.target === '_blank' || link.href.includes('mailto:')) return;
  
  event.preventDefault();
  const href = link.href;

  // Анимация исчезновения
  document.body.classList.add('fade-out');
  
  setTimeout(() => {
    window.location.href = href;
  }, 300);
}

// ===== ЛОГИКА РЕДАКТОРА =====
function saveNote() {
  const urlParams = new URLSearchParams(window.location.search);
  const noteId = urlParams.get('id');
  
  const title = document.getElementById('note-title').value.trim();
  const content = document.getElementById('note-content').value.trim();
  
  if (title.length < 3 || content.length < 10) {
    alert('Минимум 3 символа в заголовке и 10 в тексте');
    return;
  }

  const note = { title, content, id: noteId || undefined };
  if (notesManager.save(note)) {
    document.body.classList.add('fade-out');
    setTimeout(() => window.location.href = 'notes.html', 300);
  }
}

// ===== ОТОБРАЖЕНИЕ ЗАМЕТОК =====
function renderNotes(searchQuery = '') {
  const container = document.getElementById('notes-list');
  container.innerHTML = '';
  
  const notes = searchQuery ? 
    notesManager.search(searchQuery) : 
    [...notesManager.notes].reverse();

  notes.forEach((note, index) => {
    const card = document.createElement('article');
    card.className = 'card note-card';
    card.style.animationDelay = `${index * 0.1}s`;
    card.innerHTML = `
      <a href="note.html?id=${encodeURIComponent(note.id)}" class="note-link">
        <h2 class="note-card__title">${escapeHTML(note.title)}</h2>
        <p class="note-card__content">${escapeHTML(note.content.slice(0, 100))}...</p>
      </a>
      <button class="delete-btn" onclick="deleteNote('${encodeURIComponent(note.id)}', event)">
        <svg viewBox="0 0 24 24">
          <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
        </svg>
      </button>
    `;
    container.appendChild(card);
  });
}

// ===== УДАЛЕНИЕ ЗАМЕТКИ =====
// ===== УДАЛЕНИЕ ЗАМЕТКИ =====
let currentNoteToDelete = null;

function showDeleteModal(encodedId) {
  currentNoteToDelete = encodedId;
  const modal = document.getElementById('confirmModal');
  modal.classList.add('active');
}

function deleteNote(encodedId, event) {
  event.stopPropagation();
  showDeleteModal(encodedId);
}

// ===== ИНИЦИАЛИЗАЦИЯ =====
document.addEventListener('DOMContentLoaded', () => {
  // ... предыдущий код инициализации ...

  // Обработчики модалки удаления
  const modal = document.getElementById('confirmModal');
  
  document.getElementById('confirmDelete').addEventListener('click', () => {
    if(currentNoteToDelete) {
      const id = decodeURIComponent(currentNoteToDelete);
      notesManager.delete(id);
      document.body.classList.add('fade-out');
      setTimeout(() => {
        renderNotes();
        modal.classList.remove('active');
        currentNoteToDelete = null;
      }, 300);
    }
  });

  document.getElementById('cancelDelete').addEventListener('click', () => {
    modal.classList.remove('active');
    currentNoteToDelete = null;
  });

  modal.addEventListener('click', (e) => {
    if(e.target === modal) {
      modal.classList.remove('active');
      currentNoteToDelete = null;
    }
  });

  // Закрытие по Esc
  document.addEventListener('keydown', (e) => {
    if(e.key === 'Escape') {
      modal.classList.remove('active');
      currentNoteToDelete = null;
    }
  });
});

// ===== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ =====
function escapeHTML(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

function initSearch() {
  const searchInput = document.getElementById('search');
  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      document.body.classList.add('fade-out');
      setTimeout(() => renderNotes(e.target.value), 300);
    });
  }
}

// ===== ЗАГРУЗКА ЗАМЕТКИ =====
function loadNote() {
  const urlParams = new URLSearchParams(window.location.search);
  const noteId = urlParams.get('id');

  if (!noteId) {
    alert('Заметка не найдена');
    window.location.href = 'notes.html';
    return;
  }

  const note = notesManager.getNoteById(noteId);
  const titleField = document.getElementById('note-title');
  const contentField = document.getElementById('note-content');

  if (!note) {
    alert('Заметка не найдена');
    window.location.href = 'notes.html';
    return;
  }

  if (titleField?.tagName === 'H1') {
    titleField.textContent = note.title;
    contentField.textContent = note.content;
  } else if (titleField?.tagName === 'INPUT') {
    titleField.value = note.title;
    contentField.value = note.content;
  }
}

// ===== ИНИЦИАЛИЗАЦИЯ =====
document.addEventListener('DOMContentLoaded', () => {
  // Анимация появления страницы
  document.body.classList.add('page-transition');
  
  // Обработчик навигации
  document.body.addEventListener('click', handleNavigation);

  // Инициализация модулей
  if (document.getElementById('notes-list')) {
    renderNotes();
    initSearch();
  }
  
  if (window.location.pathname.includes('note.html')) {
    loadNote();
  }
  
  if (window.location.pathname.includes('editor.html')) {
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('id')) loadNote();
  }
});

// Восстановление анимации при переходе назад
window.addEventListener('pageshow', (event) => {
  if (event.persisted) {
    document.body.classList.remove('fade-out');
    document.body.classList.add('page-transition');
  }
});

// Анимация поиска
const searchIcon = document.getElementById('search-icon');
const searchBox = document.querySelector('.search-box');
const searchContainer = document.querySelector('.search-container');

searchIcon.addEventListener('click', (e) => {
    e.stopPropagation();
    searchBox.classList.toggle('active');
    if (searchBox.classList.contains('active')) {
        searchBox.focus();
    } else {
        searchBox.blur();
    }
});

// Закрытие при клике вне области
document.addEventListener('click', (e) => {
    if (!searchContainer.contains(e.target)) {
        searchBox.classList.remove('active');
        searchBox.blur();
    }
});

// Theme Manager
function initTheme() {
  const savedTheme = localStorage.getItem('theme') || 'light';
  document.documentElement.setAttribute('data-theme', savedTheme);
}

function toggleTheme() {
  const html = document.documentElement;
  const newTheme = html.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
  html.setAttribute('data-theme', newTheme);
  localStorage.setItem('theme', newTheme);
}

// Добавьте в DOMContentLoaded:
document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  
  document.querySelectorAll('.theme-toggle').forEach(btn => {
    btn.addEventListener('click', toggleTheme);
  });
});

document.addEventListener('DOMContentLoaded', () => {
    const body = document.body;

    // Добавляем класс для появления страницы при загрузке
    body.classList.add('fade-in');

    // Обрабатываем все переходы по ссылкам
    document.querySelectorAll('a[href]').forEach(link => {
        link.addEventListener('click', event => {
            const href = link.getAttribute('href');

            // Пропускаем якорные и внешние ссылки
            if (!href || href.startsWith('#') || href.startsWith('http')) return;

            // Отменяем стандартное поведение
            event.preventDefault();

            // Добавляем класс для исчезновения
            body.classList.add('fade-out');

            // Ждем завершения анимации перед переходом
            setTimeout(() => {
                window.location.href = href;
            }, 500); // 500 мс - время анимации
        });
    });
});


