'use strict';
const definitions = [
    { key: 'members', title: 'Клиенты и абонементы', description: 'Учётные записи, профили и доступ в клуб.' },
    { key: 'club', title: 'Клуб и бронирования', description: 'Тренеры, расписание и запись на занятия.' },
    { key: 'workouts', title: 'Тренировки', description: 'Программы, упражнения и история результатов.' },
    { key: 'wellness', title: 'Личные показатели', description: 'Вода, сон, вес и ИМТ.' },
    { key: 'nutrition', title: 'Дневник питания', description: 'Справочник продуктов и КБЖУ за день.' }
];
const container = document.getElementById('services');
const refresh = document.getElementById('refresh');
const summary = document.getElementById('summary');

function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = String(text);
    return node;
}

function card(definition, data, error) {
    const panel = element('article', 'service');
    const head = element('div', 'service-head');
    head.append(element('h2', '', definition.title), element('span', `state ${error ? 'error' : data ? '' : 'pending'}`, error ? 'Недоступен' : data ? 'Подключён' : 'Проверяем…'));
    panel.append(head, element('p', '', definition.description));
    if (data) {
        const details = element('dl');
        for (const [label, value] of [['Сервис', data.service], ['База', data.database.engine], ['Миграция', `Версия ${data.database.schemaVersion}`], ['Настройки', data.configurationSource]]) {
            const row = element('div', 'detail');
            row.append(element('dt', 'detail-label', label), element('dd', '', value));
            details.append(row);
        }
        panel.append(details, element('div', 'detail-label', 'Идентификатор запроса'), element('code', '', data.requestId));
    }
    if (error) panel.append(element('p', 'error-message', error));
    const links = element('div', 'links');
    for (const [label, suffix] of [['Ответ API ↗', 'system'], ['OpenAPI ↗', 'v3/api-docs']]) {
        const link = element('a', '', label);
        link.href = `/api/${definition.key}/${suffix}`;
        link.target = '_blank';
        link.rel = 'noopener';
        links.append(link);
    }
    panel.append(links);
    return panel;
}

async function check() {
    refresh.disabled = true;
    summary.textContent = 'Проверяем маршруты и подключение к базам…';
    container.replaceChildren(...definitions.map(definition => card(definition)));
    const results = await Promise.allSettled(definitions.map(async definition => {
        const requestId = `browser-${definition.key}-${crypto.randomUUID()}`;
        const response = await fetch(`/api/${definition.key}/system`, {
            headers: { 'X-Request-ID': requestId }, cache: 'no-store', signal: AbortSignal.timeout(10000)
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.message || `HTTP ${response.status}`);
        if (data.requestId !== requestId || response.headers.get('X-Request-ID') !== requestId) throw new Error('Идентификатор запроса не прошёл через Gateway.');
        if (data.configurationSource !== 'config-server' || data.database.serviceCode !== `${definition.key}-service`) throw new Error('Ответ не прошёл проверку конфигурации и базы.');
        return data;
    }));
    container.replaceChildren(...results.map((result, index) => card(definitions[index], result.status === 'fulfilled' ? result.value : null, result.status === 'rejected' ? result.reason.message : null)));
    const success = results.filter(result => result.status === 'fulfilled').length;
    summary.textContent = `Доступно сервисов: ${success} из 4. Проверено: ${new Date().toLocaleTimeString('ru-RU')}.`;
    refresh.disabled = false;
}
refresh.addEventListener('click', check);
check();

