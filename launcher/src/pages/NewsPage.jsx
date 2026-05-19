import React, { useEffect, useState } from 'react';

/**
 * Сводка / Новости. Грузит /api/news с бэкенда.
 * Стили — в launcher/src/styles/global.css (.news-grid, .news-card, .panel, .tag, .btn).
 */
export default function NewsPage({ apiBase = 'http://localhost:4000' }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    fetch(`${apiBase}/api/news`)
      .then((r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
      })
      .then((data) => {
        if (cancelled) return;
        const list = Array.isArray(data) ? data : (data && data.items) || [];
        setItems(list);
      })
      .catch((e) => {
        if (cancelled) return;
        setError(e.message || 'Не удалось загрузить сводку.');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [apiBase]);

  if (loading) {
    return (
      <div className="panel">
        <h2>Сводка</h2>
        <p className="sub">Загружаю боевые донесения…</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="panel">
        <h2>Сводка</h2>
        <p className="login-err">Ошибка: {error}</p>
        <p className="sub">Убедитесь, что сайт/API запущен на {apiBase}.</p>
      </div>
    );
  }

  if (!items.length) {
    return (
      <div className="panel">
        <h2>Сводка</h2>
        <p className="sub">Донесений нет. Тишина на фронте.</p>
      </div>
    );
  }

  return (
    <div className="animate-fade-in">
      <div className="panel">
        <h2>Сводка</h2>
        <p className="sub">Оперативные донесения и обновления проекта.</p>
      </div>
      <div className="news-grid">
        {items.map((n, i) => (
          <article key={n.id || i} className="news-card">
            {n.tag && <span className="tag">{n.tag}</span>}
            <h3>{n.title || 'Без заголовка'}</h3>
            {n.date && <div className="sub">{n.date}</div>}
            {n.summary && <p>{n.summary}</p>}
            {n.url && (
              <a className="btn ghost" href={n.url} target="_blank" rel="noreferrer">
                Подробнее
              </a>
            )}
          </article>
        ))}
      </div>
    </div>
  );
}
