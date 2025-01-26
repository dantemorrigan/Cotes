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
      <h2 class="note-card__title">${escapeHTML(note.title)}</h2>
      <p class="note-card__content">${escapeHTML(note.content.slice(0, 100))}...</p>
      <div class="btn-group">
        <a href="note.html?id=${encodeURIComponent(note.id)}" class="btn">Открыть</a>
        <button class="btn danger" onclick="deleteNote('${encodeURIComponent(note.id)}')">Удалить</button>
      </div>
    `;
    container.appendChild(card);
  });
}

// ===== УДАЛЕНИЕ ЗАМЕТКИ =====
function deleteNote(encodedId) {
  const id = decodeURIComponent(encodedId);
  if (confirm('Удалить заметку?')) {
    notesManager.delete(id);
    document.body.classList.add('fade-out');
    setTimeout(renderNotes, 300);
  }
}

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
const searchBox = document.getElementById('search');

searchIcon.addEventListener('click', () => {
    if (searchBox.classList.contains('active')) {
        // Если поле активно, плавно скрываем его
        searchBox.classList.remove('active');
        searchBox.blur(); // Убираем фокус с поля поиска
    } else {
        // Если поле не активно, плавно раскрываем его
        searchBox.classList.add('active');
        searchBox.focus(); // Автоматически фокусируемся на поле поиска
    }
});

// Закрытие поля поиска при клике вне его области
document.addEventListener('click', (event) => {
    if (!searchIcon.contains(event.target) && !searchBox.contains(event.target)) {
        searchBox.classList.remove('active');
        searchBox.blur();
    }
});