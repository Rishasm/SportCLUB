'use strict';
window.SportClubWellness = function (host) {
    const { api, element, button, notify, setLoading, errorState, formattedDate, getToken } = host;
    const $ = selector => document.querySelector(selector);
    const base = '/api/wellness';
    const state = { date: today(), day: null, history: [], generation: 0, visible: false, busy: false, expanded: false };
    const number = value => new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(Number(value));
    function today() {
        const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Moscow', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
        const part = name => parts.find(item => item.type === name).value;
        return `${part('year')}-${part('month')}-${part('day')}`;
    }
    function before(date, days) {
        const value = new Date(date + 'T12:00:00Z'); value.setUTCDate(value.getUTCDate() - days);
        return value.toISOString().slice(0, 10);
    }
    const displayDate = date => formattedDate(date + 'T12:00:00+03:00', { year: 'numeric' });
    const duration = minutes => minutes === null || minutes === undefined ? 'Нет записи' : `${Math.floor(minutes / 60)} ч${minutes % 60 ? ' ' + minutes % 60 + ' мин' : ''}`;
    const current = (generation, token) => state.visible && generation === state.generation && token && token === getToken();
    function controls() {
        document.querySelectorAll('[data-page="wellness"] button, [data-page="wellness"] input').forEach(node => { node.disabled = state.busy; });
        $('#wellness-content').setAttribute('aria-busy', String(state.busy));
    }
    function field(name, caption, value, attributes) {
        const label = element('label', 'wellness-field', caption);
        const input = element('input'); input.type = 'number'; input.name = name; input.id = 'wellness-' + name; input.value = value ?? '';
        Object.assign(input, { required: true, ...attributes }); label.append(input); return { label, input };
    }
    function submit(caption, id) { const node = element('button', 'button button-primary', caption); node.type = 'submit'; if (id) node.id = id; return node; }
    function panel(id, eyebrow, title) {
        const node = element('article', 'wellness-card'); node.id = id;
        const heading = element('div', 'wellness-card-heading'); heading.append(element('p', 'eyebrow', eyebrow), element('h2', '', title)); node.append(heading); return node;
    }
    function leave() { state.visible = false; state.generation++; }
    function clear() {
        leave(); state.date = today(); state.day = null; state.history = []; state.expanded = false;
        $('#wellness-content').replaceChildren(); $('#wellness-history').replaceChildren();
    }
    async function open() {
        state.visible = true;
        $('#wellness-date').max = today(); $('#wellness-date').value = state.date;
        await load();
    }
    async function load() {
        const generation = ++state.generation, token = getToken(), selected = state.date;
        $('#wellness-date').value = selected;
        $('#wellness-day-label').textContent = selected === today() ? 'Сегодня · московское время' : displayDate(selected) + ' · запись за выбранный день';
        setLoading($('#wellness-content'), 'Загружаем твой день…');
        setLoading($('#wellness-history'), 'Загружаем историю…');
        try {
            const day = await api(base + '/day?date=' + selected);
            if (!current(generation, token)) return;
            state.day = day;
            const layout = element('div', 'wellness-grid'); layout.id = 'wellness-grid';
            layout.append(panel('wellness-water', 'Маленькими глотками', 'Вода'), panel('wellness-sleep', 'Время восстановиться', 'Сон'), panel('wellness-body', 'Наблюдай за изменениями', 'Вес и ИМТ'));
            $('#wellness-content').replaceChildren(layout);
            renderWater(); renderSleep(); renderBody(); controls();
            await loadHistory(generation, token);
        } catch (error) {
            if (current(generation, token) && error.status !== 401) {
                errorState($('#wellness-content'), error, load); $('#wellness-history').replaceChildren();
            }
        }
    }
    async function loadHistory(generation = state.generation, token = getToken()) {
        const from = before(state.date, 29) < '2000-01-01' ? '2000-01-01' : before(state.date, 29), to = state.date;
        $('#wellness-history-period').textContent = `${displayDate(from)} — ${displayDate(to)}`;
        try {
            const history = await api(`${base}/history?from=${from}&to=${to}`);
            if (!current(generation, token)) return;
            state.history = history; renderHistory(); controls();
        } catch (error) { if (current(generation, token) && error.status !== 401) errorState($('#wellness-history'), error, () => loadHistory()); }
    }
    async function mutate(path, method, data, render, message) {
        if (state.busy) return;
        state.busy = true; controls();
        const generation = state.generation, token = getToken(), date = state.date;
        try {
            await api(base + path, { method, ...(data !== undefined ? { body: JSON.stringify(data) } : {}) });
            if (!current(generation, token)) return;
            const day = await api(base + '/day?date=' + date);
            if (!current(generation, token)) return;
            state.day = day; render(); notify(message);
            await loadHistory(generation, token);
        } catch (error) { if (current(generation, token) && error.status !== 401) notify(error.message, true); }
        finally {
            state.busy = false;
            if (state.visible) {
                controls();
                if (token === getToken() && generation !== state.generation) await load();
            }
        }
    }
    function replacePanel(id, eyebrow, title) {
        const old = $('#' + id), replacement = panel(id, eyebrow, title); old.replaceWith(replacement); return replacement;
    }
    function renderWater() {
        const day = state.day, target = replacePanel('wellness-water', 'Маленькими глотками', 'Вода');
        const summary = element('div', 'water-summary');
        const ring = element('div', 'water-ring');
        ring.style.setProperty('--water-progress', `${Math.min(100, day.waterTotalMl / day.waterGoalMl * 100)}%`);
        const values = element('div'); values.append(element('strong', '', number(day.waterTotalMl)), element('span', '', 'мл из ' + number(day.waterGoalMl))); ring.append(values);
        const text = element('div', 'water-summary-copy');
        text.append(element('p', '', day.waterTotalMl >= day.waterGoalMl ? 'Твоя цель достигнута' : 'Твой дневной ориентир'), element('p', 'muted', 'Выбери удобную цель и отмечай выпитую воду.'));
        summary.append(ring, text); target.append(summary);
        const quick = element('div', 'water-quick');
        [100, 250, 500].forEach(amount => { const node = button('+ ' + amount + ' мл', 'button button-outline', () => addWater(amount)); node.dataset.addWater = amount; quick.append(node); });
        target.append(quick);
        const form = element('form', 'wellness-inline-form'); form.id = 'wellness-water-form';
        const amount = field('amountMl', 'Другое количество, мл', '', { min: '1', max: '2000', step: '1', placeholder: 'Например, 350' });
        form.append(amount.label, submit('Добавить', 'wellness-add-water'));
        form.addEventListener('submit', event => { event.preventDefault(); if (form.reportValidity()) addWater(Number(amount.input.value)); }); target.append(form);
        const goal = element('details', 'water-goal');
        goal.append(element('summary', '', 'Изменить дневную цель'));
        const goalForm = element('form', 'wellness-inline-form'); goalForm.id = 'wellness-goal-form';
        const goalInput = field('waterGoalMl', 'Твоя цель, мл', day.waterGoalMl, { min: '250', max: '10000', step: '1' });
        goalForm.append(goalInput.label, submit('Сохранить цель', 'wellness-save-goal'));
        goalForm.addEventListener('submit', event => { event.preventDefault(); if (goalForm.reportValidity()) mutate('/settings', 'PUT', { waterGoalMl: Number(goalInput.input.value) }, renderWater, 'Дневная цель сохранена.'); });
        goal.append(goalForm, element('p', 'wellness-note', 'Это твой личный ориентир. Его можно менять в любой момент.')); target.append(goal);
        const entries = element('div', 'water-entries'); entries.id = 'wellness-water-entries';
        entries.append(element('h3', '', 'Записи за день'));
        if (!(day.waterEntries || []).length) entries.append(element('p', 'wellness-note', 'Пока ни одного глотка в дневнике. Добавь первую запись.'));
        (day.waterEntries || []).forEach(entry => {
            const row = element('div', 'water-entry'); row.dataset.waterEntry = entry.id;
            const clock = formattedDate(entry.createdAt, { hour: '2-digit', minute: '2-digit', day: undefined, month: undefined });
            row.append(element('span', '', `${number(entry.amountMl)} мл`), element('small', '', clock));
            const remove = button('Удалить', 'text-button', () => mutate('/water/' + encodeURIComponent(entry.id), 'DELETE', undefined, renderWater, 'Запись воды удалена.'));
            remove.setAttribute('aria-label', `Удалить ${entry.amountMl} мл, ${clock}`); remove.dataset.deleteWater = entry.id; row.append(remove); entries.append(row);
        }); target.append(entries);
    }
    function addWater(amountMl) { mutate('/water', 'POST', { date: state.date, amountMl }, renderWater, 'Вода добавлена в дневник.'); }
    function renderSleep() {
        const day = state.day, target = replacePanel('wellness-sleep', 'Время восстановиться', 'Сон');
        target.append(element('p', 'wellness-main-value', duration(day.sleepMinutes)), element('p', 'wellness-note', 'Сколько ты спал перед этим днём? Запиши общую длительность.'));
        const form = element('form', 'wellness-form'); form.id = 'wellness-sleep-form';
        const hours = field('sleepHours', 'Продолжительность, часы', day.sleepMinutes === null ? '' : day.sleepMinutes / 60, { min: '0.25', max: '24', step: '0.25', placeholder: 'Например, 7,5' });
        form.append(hours.label, element('p', 'wellness-note', '0,25 часа = 15 минут'), submit('Сохранить сон', 'wellness-save-sleep'));
        form.addEventListener('submit', event => { event.preventDefault(); if (form.reportValidity()) mutate('/days/' + state.date + '/sleep', 'PUT', { sleepMinutes: Math.round(Number(hours.input.value) * 60) }, renderSleep, 'Длительность сна сохранена.'); });
        target.append(form);
        if (day.sleepMinutes !== null) { const remove = button('Удалить запись сна', 'text-button', () => mutate('/days/' + state.date + '/sleep', 'DELETE', undefined, renderSleep, 'Запись сна удалена.')); remove.id = 'wellness-delete-sleep'; target.append(remove); }
    }
    function renderBody() {
        const day = state.day, target = replacePanel('wellness-body', 'Наблюдай за изменениями', 'Вес и ИМТ');
        const value = element('div', 'wellness-body-values');
        const weight = element('div'); weight.append(element('strong', '', day.weightKg === null ? '—' : number(day.weightKg)), element('span', '', 'Вес, кг'));
        const bmi = element('div'); bmi.id = 'wellness-bmi'; bmi.append(element('strong', '', day.bmi === null ? '—' : number(day.bmi)), element('span', '', 'ИМТ'));
        value.append(weight, bmi); target.append(value);
        const form = element('form', 'wellness-form'); form.id = 'wellness-body-form';
        const weightField = field('weightKg', 'Вес, кг', day.weightKg, { min: '30', max: '300', step: '0.01', placeholder: 'Например, 70' });
        const heightField = field('heightCm', 'Рост, см', day.heightCm, { min: '100', max: '250', step: '0.01', placeholder: 'Например, 175' });
        const inputs = element('div', 'wellness-body-inputs'); inputs.append(weightField.label, heightField.label);
        form.append(inputs, submit('Сохранить показатели', 'wellness-save-body'));
        form.addEventListener('submit', event => { event.preventDefault(); if (form.reportValidity()) mutate('/days/' + state.date + '/body', 'PUT', { weightKg: Number(weightField.input.value), heightCm: Number(heightField.input.value) }, renderBody, 'Вес, рост и ИМТ сохранены.'); });
        target.append(form, element('p', 'wellness-note wellness-bmi-note', 'ИМТ = вес / рост², где рост в метрах. Показатель помогает вести историю и сам по себе не оценивает здоровье.'));
        if (day.weightKg !== null) { const remove = button('Удалить вес и рост', 'text-button', () => mutate('/days/' + state.date + '/body', 'DELETE', undefined, renderBody, 'Показатели за день удалены.')); remove.id = 'wellness-delete-body'; target.append(remove); }
    }
    function renderHistory() {
        const target = $('#wellness-history'), history = [...state.history].sort((a, b) => b.date.localeCompare(a.date)); target.replaceChildren();
        const any = history.some(day => day.waterTotalMl > 0 || day.sleepMinutes !== null || day.weightKg !== null);
        if (!any) target.append(element('p', 'wellness-history-empty', 'История пока чистая. Добавь воду, сон или вес — записи появятся здесь.'));
        const table = element('table', 'wellness-history-table');
        table.append(element('caption', 'sr-only', 'Личные показатели за последние 30 дней относительно выбранной даты'));
        const head = element('thead'), headRow = element('tr');
        ['День', 'Вода', 'Сон', 'Вес', 'ИМТ'].forEach(text => { const cell = element('th', '', text); cell.scope = 'col'; headRow.append(cell); }); head.append(headRow); table.append(head);
        const body = element('tbody');
        (state.expanded ? history : history.slice(0, 7)).forEach(day => {
            const row = element('tr'); row.dataset.wellnessDate = day.date; if (day.date === state.date) row.classList.add('wellness-selected-day');
            const dateCell = element('td'), choose = button(formattedDate(day.date + 'T12:00:00+03:00', { month: 'short' }), 'text-button', () => { state.date = day.date; load(); });
            choose.setAttribute('aria-label', 'Открыть показатели за ' + displayDate(day.date)); dateCell.append(choose); row.append(dateCell);
            const values = [day.waterTotalMl ? number(day.waterTotalMl) + ' мл' : '—', day.sleepMinutes !== null ? duration(day.sleepMinutes) : '—', day.weightKg !== null ? number(day.weightKg) + ' кг' : '—', day.bmi !== null ? number(day.bmi) : '—'];
            values.forEach((value, index) => { const cell = element('td', '', value); cell.dataset.label = ['Вода', 'Сон', 'Вес', 'ИМТ'][index]; row.append(cell); }); body.append(row);
        }); table.append(body); target.append(table);
        if (history.length > 7) { const expand = button(state.expanded ? 'Свернуть до 7 дней ↑' : 'Показать все 30 дней ↓', 'text-button', () => { state.expanded = !state.expanded; renderHistory(); controls(); }); expand.id = 'wellness-expand-history'; expand.setAttribute('aria-expanded', String(state.expanded)); target.append(expand); }
        target.append(element('p', 'wellness-note', 'Нажми на дату, чтобы открыть день. Прочерк означает, что записи нет. Время записей — московское.'));
    }
    $('#wellness-date').addEventListener('change', event => {
        if (state.busy || !event.target.reportValidity() || !event.target.value) { event.target.value = state.date; return; }
        state.date = event.target.value; load();
    });
    return { open, leave, clear };
};
