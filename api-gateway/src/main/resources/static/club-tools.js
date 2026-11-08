'use strict';

window.SportClubTools = function ({ api, element, button, link, notify, errorState, emptyState, formattedDate,
    time, dateInMoscow, addDays, getUser, getToken, updateSession, go }) {
    const $ = selector => document.querySelector(selector);
    const levels = { BEGINNER: 'Начальный', INTERMEDIATE: 'Средний', ALL_LEVELS: 'Любой уровень' };
    const types = { STRENGTH: 'Силовая', FUNCTIONAL: 'Функциональная', STRETCH: 'Растяжка' };
    const meals = { BREAKFAST: 'Завтрак', LUNCH: 'Обед', DINNER: 'Ужин', SNACK: 'Перекус' };
    const number = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 });
    let calendar;
    let dialogRequest = 0;
    let staffRequest = 0;
    let nutritionRequest = 0;

    function write(path, method, data) {
        return api(path, { method, ...(data === undefined ? {} : { body: JSON.stringify(data) }) });
    }

    function panel(title, style = '') {
        const card = element('article', 'panel club-panel club-stack ' + style);
        card.append(element('h2', '', title));
        return card;
    }

    function field(form, labelText, name, value, options = {}) {
        const label = element('label', '', labelText);
        const control = element(options.choices ? 'select' : options.rows ? 'textarea' : 'input');
        control.name = name;
        if (options.choices) {
            for (const [key, text] of Object.entries(options.choices)) {
                const option = element('option', '', text);
                option.value = key;
                control.append(option);
            }
        } else if (options.rows) {
            control.rows = options.rows;
        } else {
            control.type = options.type || 'text';
        }
        for (const key of ['required', 'min', 'max', 'minLength', 'maxLength', 'step', 'autocomplete']) {
            if (options[key] !== undefined) control[key] = options[key];
        }
        control.value = value ?? '';
        const issue = element('small', 'form-error');
        issue.hidden = true;
        issue.id = 'field-' + crypto.randomUUID();
        control.setAttribute('aria-describedby', issue.id);
        label.append(control, issue);
        control.addEventListener('input', () => {
            issue.hidden = true;
            control.removeAttribute('aria-invalid');
        });
        form.append(label);
        return control;
    }

    function submit(form, text, run) {
        const issue = element('p', 'form-error');
        issue.hidden = true;
        issue.setAttribute('role', 'alert');
        const action = element('button', 'button button-primary', text);
        action.type = 'submit';
        form.append(issue, action);
        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (action.disabled) return;
            action.disabled = true;
            issue.hidden = true;
            for (const control of form.querySelectorAll('[aria-invalid]')) {
                control.removeAttribute('aria-invalid');
                document.getElementById(control.getAttribute('aria-describedby')).hidden = true;
            }
            try { await run(Object.fromEntries(new FormData(form))); }
            catch (error) {
                const fields = Object.entries(error.fields || {});
                let matched = 0;
                for (const [name, message] of fields) {
                    const control = form.elements.namedItem(name);
                    const description = control?.getAttribute('aria-describedby');
                    if (!description) continue;
                    const fieldIssue = document.getElementById(description);
                    if (!fieldIssue) continue;
                    control.setAttribute('aria-invalid', 'true');
                    fieldIssue.textContent = message;
                    fieldIssue.hidden = false;
                    matched++;
                }
                if (!matched || matched !== fields.length) {
                    issue.textContent = error.message;
                    issue.hidden = false;
                }
            } finally { action.disabled = false; }
        });
    }

    function loadPhoto(slot, src) {
        if (!src) return;
        const img = element('img');
        img.alt = slot.textContent;
        img.addEventListener('load', () => slot.replaceChildren(img), { once: true });
        img.src = src;
    }

    function photo(text, src) {
        const slot = element('div', 'photo-slot', text);
        loadPhoto(slot, src);
        return slot;
    }

    async function range() {
        calendar ||= await api('/api/club/calendar', { anonymous: true });
        if (calendar.today !== dateInMoscow()) calendar = await api('/api/club/calendar', { anonymous: true });
        return calendar;
    }

    function applyRange(input, dates) {
        input.min = dates.min;
        input.max = dates.max;
        if (!input.value || input.value < dates.min || input.value > dates.max) input.value = dates.today;
        return input.value;
    }

    function squatAnimation() {
        const wrapper = element('div', 'exercise-animation');
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('viewBox', '0 0 200 170');
        svg.setAttribute('aria-label', 'Схема приседания в профиль');
        svg.setAttribute('role', 'img');
        const shape = (tag, attributes) => {
            const node = document.createElementNS(svg.namespaceURI, tag);
            for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, value);
            svg.append(node);
            return node;
        };
        shape('path', { d: 'M34 146H169', stroke: '#607457', 'stroke-width': 2 });
        const body = shape('polyline', { fill: 'none', stroke: '#b5ff7a', 'stroke-width': 7, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' });
        const arm = shape('polyline', { fill: 'none', stroke: '#d4ebc8', 'stroke-width': 5, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' });
        const head = shape('circle', { r: 10, fill: '#d4ebc8' });
        function draw(progress) {
            const hipX = 100 - 25 * progress, hipY = 80 + 27 * progress;
            const kneeX = 101 + 15 * progress, kneeY = 110 - 4 * progress;
            const shoulderX = 101 + 1 * progress, shoulderY = 49 + 23 * progress;
            body.setAttribute('points', `${shoulderX},${shoulderY} ${hipX},${hipY} ${kneeX},${kneeY} 100,141 120,141`);
            arm.setAttribute('points', `${shoulderX},${shoulderY} ${106 + 15 * progress},${64 + 18 * progress} ${111 + 28 * progress},${77 + 5 * progress}`);
            head.setAttribute('cx', shoulderX + 4);
            head.setAttribute('cy', shoulderY - 15);
        }
        draw(0);
        const play = button('Показать приседание', 'button button-outline', () => {
            if (matchMedia('(prefers-reduced-motion: reduce)').matches) {
                const lowered = play.dataset.lowered === 'true';
                draw(lowered ? 0 : 1);
                play.dataset.lowered = String(!lowered);
                play.textContent = lowered ? 'Показать нижнее положение' : 'Показать исходное положение';
                return;
            }
            play.disabled = true;
            const start = performance.now();
            function frame(now) {
                if (!svg.isConnected || !svg.closest('dialog').open) { draw(0); play.disabled = false; return; }
                const elapsed = Math.min((now - start) / 2400, 1);
                draw((1 - Math.cos(2 * Math.PI * elapsed)) / 2);
                if (elapsed < 1) requestAnimationFrame(frame);
                else play.disabled = false;
            }
            requestAnimationFrame(frame);
        });
        wrapper.append(svg, play);
        return wrapper;
    }

    async function openClass(id) {
        const dialog = $('#class-dialog');
        const target = $('#class-dialog-content');
        const title = $('#class-dialog-title');
        const request = ++dialogRequest;
        title.textContent = 'Занятие';
        target.replaceChildren(element('p', 'muted', 'Загружаем карточку…'));
        if (!dialog.open) dialog.showModal();
        try {
            const item = await api('/api/club/classes/' + id, { anonymous: true });
            if (request !== dialogRequest || !dialog.open) return;
            const slot = item.schedule;
            title.textContent = slot.title;
            const duration = Math.round((new Date(slot.endsAt) - new Date(slot.startsAt)) / 60000);
            const coach = element('div', 'club-trainer-detail');
            const bio = element('div', 'club-stack');
            bio.append(element('h3', '', item.trainer.name), element('p', 'muted', item.trainer.specialty),
                element('p', '', item.trainer.bio));
            coach.append(photo('Фото тренера', window.SportClubPhotos.trainers[item.trainer.id]), bio);
            target.replaceChildren(photo('Фото занятия', window.SportClubPhotos.classes[slot.id]),
                element('p', 'tag', item.cancelled ? 'Отменено' : `${levels[item.level]} · ${duration} мин · ${slot.availableSpots} мест`),
                element('p', '', `${formattedDate(slot.startsAt)} · ${time(slot.startsAt)} · ${slot.room}`),
                element('p', '', item.description || 'Тренер уточнит план занятия перед началом.'), coach);
            const videoSource = window.SportClubPhotos.exerciseVideos?.[slot.id]
                ?? window.SportClubPhotos.exerciseVideosByType?.[slot.type];
            if (item.technique || item.squatDemo || videoSource) {
                target.append(element('h3', '', 'Техника упражнений'));
                if (item.technique) target.append(element('p', '', item.technique));
                if (item.squatDemo) target.append(squatAnimation());
                if (videoSource) {
                    const video = element('video', 'exercise-video');
                    video.controls = true;
                    video.playsInline = true;
                    video.preload = 'none';
                    video.setAttribute('aria-label', 'Видео техники упражнения');
                    video.src = videoSource;
                    target.append(video);
                }
            }
        } catch (error) {
            if (request === dialogRequest && dialog.open) errorState(target, error, () => openClass(id));
        }
    }

    async function settings() {
        const target = $('#profile-settings');
        const user = getUser();
        const token = getToken();
        const profile = panel('Имя и почта');
        const form = element('form', 'club-form');
        field(form, 'Имя', 'name', user.name, { minLength: 2, maxLength: 80, required: true, autocomplete: 'name' });
        field(form, 'Электронная почта', 'email', user.email, { type: 'email', maxLength: 254, required: true, autocomplete: 'email' });
        submit(form, 'Сохранить профиль', async data => {
            const result = await write('/api/members/me', 'PUT', { name: data.name.trim(), email: data.email.trim() });
            if (getToken() !== token) return;
            updateSession(result);
            go('settings');
            notify('Профиль обновлён.');
        });
        profile.append(form);
        const password = panel('Пароль');
        const passwordForm = element('form', 'club-form');
        field(passwordForm, 'Текущий пароль', 'currentPassword', '', { type: 'password', maxLength: 72, required: true, autocomplete: 'current-password' });
        const newPassword = field(passwordForm, 'Новый пароль', 'newPassword', '', { type: 'password', minLength: 8, maxLength: 72, required: true, autocomplete: 'new-password' });
        const repeat = field(passwordForm, 'Повтори новый пароль', 'repeatPassword', '', { type: 'password', required: true, autocomplete: 'new-password' });
        const checkMatch = () => repeat.setCustomValidity(repeat.value && repeat.value !== newPassword.value ? 'Пароли не совпадают.' : '');
        newPassword.addEventListener('input', checkMatch);
        repeat.addEventListener('input', checkMatch);
        submit(passwordForm, 'Изменить пароль', async data => {
            await write('/api/members/me/password', 'PUT', { currentPassword: data.currentPassword, newPassword: data.newPassword });
            passwordForm.reset();
            notify('Пароль обновлён.');
        });
        password.append(passwordForm);
        const card = panel('Тестовая карта');
        const cardContent = element('div', 'club-stack');
        card.append(cardContent);
        target.replaceChildren(profile, password, card);
        async function showCard() {
            try {
                const { linked } = await api('/api/members/me/payment-card');
                const change = button(linked ? 'Отвязать карту' : 'Привязать тестовую карту', 'button button-outline', async () => {
                    change.disabled = true;
                    try { await write('/api/members/me/payment-card', linked ? 'DELETE' : 'PUT'); await showCard(); }
                    catch (error) { notify(error.message, true); change.disabled = false; }
                });
                cardContent.replaceChildren(element('p', 'muted', linked ? 'Привязана тестовая карта •••• 4242.' : 'Тестовая карта пока не привязана.'), change);
            } catch (error) { errorState(cardContent, error, showCard); }
        }
        await showCard();
    }

    function classForm(dates, trainers, record, refresh, selectedDate) {
        const card = panel(record ? 'Редактировать занятие' : 'Новое занятие');
        const form = element('form', 'club-form');
        const item = record?.schedule;
        const start = item?.startsAt.slice(0, 16) || selectedDate + 'T18:00';
        const trainerOptions = Object.fromEntries(trainers.map(trainer => [trainer.id, trainer.name]));
        field(form, 'Название', 'title', item?.title, { required: true, maxLength: 160 });
        field(form, 'Направление', 'type', item?.type || 'STRENGTH', { choices: types });
        field(form, 'Тренер', 'trainerId', item?.trainerId || trainers[0]?.id, { choices: trainerOptions, required: true });
        field(form, 'Зал', 'room', item?.room, { required: true, maxLength: 100 });
        field(form, 'Начало по Москве', 'startsAt', start,
            { type: 'datetime-local', required: true, min: dates.today + 'T00:00', max: dates.max + 'T23:59' });
        field(form, 'Длительность (мин)', 'durationMinutes', item ? (new Date(item.endsAt) - new Date(item.startsAt)) / 60000 : 45,
            { type: 'number', required: true, min: 15, max: 180, step: 1 });
        field(form, 'Вместимость', 'capacity', item?.capacity || 10, { type: 'number', required: true, min: 1, max: 100, step: 1 });
        field(form, 'Уровень', 'level', record?.level || 'ALL_LEVELS', { choices: levels });
        field(form, 'Описание', 'description', record?.description, { rows: 3, required: true, maxLength: 1500 });
        field(form, 'Подсказки по технике', 'technique', record?.technique, { rows: 3, maxLength: 1500 });
        submit(form, 'Сохранить занятие', async data => {
            const path = '/api/club/admin/classes' + (item ? '/' + item.id : '');
            await write(path, item ? 'PUT' : 'POST', { ...data, startsAt: data.startsAt + ':00+03:00',
                capacity: Number(data.capacity), durationMinutes: Number(data.durationMinutes) });
            $('#staff-date').value = data.startsAt.slice(0, 10);
            await refresh();
            notify('Занятие сохранено.');
        });
        card.append(form);
        if (record) card.append(button('Отмена', 'button button-outline', refresh));
        return card;
    }

    async function staff(editId = null) {
        const target = $('#staff-content');
        const input = $('#staff-date');
        const user = getUser();
        const admin = user?.role === 'ADMIN';
        if (!admin && user?.role !== 'TRAINER') return;
        const request = ++staffRequest;
        const token = getToken();
        const current = () => request === staffRequest && token === getToken();
        $('#staff-title').textContent = admin ? 'Управление расписанием' : 'Мои занятия';
        target.replaceChildren(element('p', 'muted', 'Загружаем расписание…'));
        try {
            const dates = await range();
            if (!current()) return;
            const date = applyRange(input, dates);
            const base = '/api/club/' + (admin ? 'admin' : 'trainer') + '/classes';
            const [items, trainers] = await Promise.all([api(base + '?date=' + date),
                admin ? api('/api/club/trainers', { anonymous: true }) : []]);
            if (!current()) return;
            const list = panel('Занятия на выбранный день');
            if (!items.length) list.append(emptyState('Занятий пока нет', 'Выбери другой день.'));
            for (const record of items) {
                const item = record.schedule;
                const card = element('div', 'booking club-stack');
                card.append(element('h3', '', item.title), element('p', 'muted',
                    `${time(item.startsAt)} · ${item.trainerName} · ${item.capacity - item.availableSpots} из ${item.capacity} записано`));
                const editable = !record.cancelled && new Date(item.startsAt) > new Date();
                if (record.cancelled) card.append(element('p', 'tag', 'Отменено'));
                if (admin && editable) {
                    const actions = element('div', 'actions');
                    actions.append(button('Изменить', 'button button-outline', () => staff(item.id)));
                    const remove = button('Удалить или отменить', 'text-button', async () => {
                        if (!confirm('Убрать занятие из расписания? Записавшиеся получат уведомление.')) return;
                        remove.disabled = true;
                        try { await write(base + '/' + item.id, 'DELETE'); await staff(); notify('Занятие убрано из расписания.'); }
                        catch (error) { notify(error.message, true); remove.disabled = false; }
                    });
                    actions.append(remove);
                    card.append(actions);
                }
                if (!admin) {
                    const participants = element('div', 'club-stack');
                    const show = button('Показать участников', 'button button-outline', async () => {
                        show.disabled = true;
                        try {
                            const people = await api(base + '/' + item.id + '/participants');
                            const names = people.map(person => element('li', '', person.name || 'Участник ' + person.memberId.slice(0, 8)));
                            const list = element('ul');
                            list.append(...names);
                            participants.replaceChildren(people.length ? list : element('p', 'muted', 'Пока никто не записался.'));
                        } catch (error) { errorState(participants, error); }
                        finally { show.disabled = false; }
                    });
                    card.append(show, participants);
                    if (editable) {
                        const editor = element('details');
                        const form = element('form', 'club-form');
                        field(form, 'Описание', 'description', record.description, { rows: 3, required: true, maxLength: 1500 });
                        field(form, 'Подсказки по технике', 'technique', record.technique, { rows: 3, maxLength: 1500 });
                        submit(form, 'Сохранить описание', async data => {
                            await write(base + '/' + item.id + '/content', 'PUT', data);
                            notify('Описание сохранено.');
                        });
                        editor.append(element('summary', '', 'Редактировать описание'), form);
                        card.append(editor);
                    }
                }
                list.append(card);
            }
            if (admin) {
                const sidebar = element('div', 'club-stack');
                const selected = items.find(record => record.schedule.id === editId);
                sidebar.append(classForm(dates, trainers, selected, staff, date < dates.today ? dates.today : date));
                const announcements = panel('Объявление для всех');
                const form = element('form', 'club-form');
                field(form, 'Заголовок', 'title', '', { required: true, maxLength: 160 });
                field(form, 'Текст', 'body', '', { rows: 3, required: true, maxLength: 1000 });
                submit(form, 'Опубликовать', async data => {
                    await write('/api/club/admin/announcements', 'POST', data);
                    form.reset();
                    notify('Объявление появилось в уведомлениях.');
                });
                announcements.append(form);
                sidebar.append(announcements);
                target.replaceChildren(list, sidebar);
                if (selected) sidebar.querySelector('input')?.focus();
            } else target.replaceChildren(list);
        } catch (error) { if (current()) errorState(target, error, () => staff()); }
    }

    async function notices() {
        const target = $('#club-notices');
        const token = getToken();
        target.replaceChildren(element('p', 'muted', 'Загружаем уведомления…'));
        try {
            const items = await api('/api/club/me/notices');
            if (token !== getToken()) return;
            target.replaceChildren(...items.map(item => {
                const card = panel(item.title, 'club-notice');
                const when = element('time', 'muted', formattedDate(item.createdAt, { hour: '2-digit', minute: '2-digit' }));
                when.dateTime = item.createdAt;
                card.append(when, element('p', '', item.body));
                if (item.announcement) card.append(element('span', 'tag', 'Объявление клуба'));
                return card;
            }));
            if (!items.length) target.append(emptyState('Уведомлений пока нет', 'Здесь появятся новости и изменения твоих занятий.'));
        } catch (error) { if (token === getToken()) errorState(target, error, notices); }
    }

    function macros(value) {
        return `${number.format(value.kcal)} ккал · Б ${number.format(value.protein)} г · Ж ${number.format(value.fat)} г · У ${number.format(value.carbs)} г`;
    }

    async function nutrition(edit = null) {
        const target = $('#nutrition-content');
        const date = $('#nutrition-date');
        date.max = dateInMoscow();
        date.value ||= date.max;
        const request = ++nutritionRequest;
        const token = getToken();
        const current = () => request === nutritionRequest && token === getToken();
        target.replaceChildren(element('p', 'muted', 'Загружаем дневник…'));
        try {
            const [foods, day] = await Promise.all([api('/api/nutrition/foods'), api('/api/nutrition/day?date=' + date.value)]);
            if (!current()) return;
            const totals = panel('Итог за день');
            totals.append(element('p', '', macros(day.total)), element('p', 'muted', 'Справочные значения для указанного способа приготовления.'));
            const editor = panel(edit ? 'Изменить запись' : 'Добавить продукт');
            const form = element('form', 'club-form');
            const meal = field(form, 'Приём пищи', 'meal', edit?.meal || 'BREAKFAST', { choices: meals });
            const food = field(form, 'Продукт', 'foodId', edit?.food.id || foods[0]?.id,
                { choices: Object.fromEntries(foods.map(item => [item.id, item.name])), required: true });
            field(form, 'Вес порции (г)', 'grams', edit?.grams || 100, { type: 'number', min: 1, max: 3000, step: 1, required: true });
            const reference = element('p', 'muted');
            const updateReference = () => {
                const selected = foods.find(item => item.id === food.value);
                reference.textContent = selected ? 'На 100 г: ' + macros(selected.per100g) : '';
            };
            food.addEventListener('change', updateReference);
            updateReference();
            form.append(reference);
            submit(form, 'Сохранить', async data => {
                await write('/api/nutrition/entries' + (edit ? '/' + edit.id : ''), edit ? 'PUT' : 'POST',
                    { ...data, date: day.date, grams: Number(data.grams) });
                await nutrition();
                notify('Дневник обновлён.');
            });
            editor.append(form);
            if (edit) editor.append(button('Отмена', 'button button-outline', () => nutrition()));
            const entries = panel('Приёмы пищи');
            for (const [key, label] of Object.entries(meals)) {
                const group = element('section', 'club-stack');
                group.append(element('h3', '', label), element('p', 'muted', day.meals[key] ? macros(day.meals[key]) : 'Пока без записей'));
                for (const entry of day.entries.filter(item => item.meal === key)) {
                    const row = element('div', 'nutrition-entry');
                    row.append(element('strong', '', `${entry.food.name} · ${entry.grams} г`),
                        element('span', 'muted', macros(entry.portion)),
                        button('Изменить', 'text-button', () => nutrition(entry)));
                    const remove = button('Удалить', 'text-button', async () => {
                        remove.disabled = true;
                        try { await write('/api/nutrition/entries/' + entry.id, 'DELETE'); await nutrition(); }
                        catch (error) { notify(error.message, true); remove.disabled = false; }
                    });
                    row.append(remove);
                    group.append(row);
                }
                entries.append(group);
            }
            const layout = element('div', 'club-form-grid');
            layout.append(editor, entries);
            target.replaceChildren(totals, layout);
            if (edit) meal.focus();
        } catch (error) { if (current()) errorState(target, error, () => nutrition()); }
    }

    function help() {
        const questions = [
            ['Как записаться?', 'Войди, оформи абонемент и нажми «Записаться» рядом с нужным занятием.'],
            ['Как отменить запись?', 'Открой «Мой кабинет» и нажми «Отменить запись» рядом с будущим занятием.'],
            ['Где узнать об изменениях?', 'Открой «Уведомления» в личном кабинете. Там появятся сообщения об отмене и переносе.'],
            ['С карты спишут деньги?', 'Оплата в SportCLUB учебная. В форме используются только тестовая карта и имитация СБП.']
        ];
        const faq = panel('Частые вопросы');
        const answers = element('div', 'club-faq');
        for (const [question, answer] of questions) {
            const item = element('details');
            item.append(element('summary', '', question), element('p', 'muted', answer));
            answers.append(item);
        }
        faq.append(answers);
        const picker = panel('Подбор занятия');
        const form = element('form', 'club-form');
        field(form, 'Цель', 'goal', 'STRENGTH', { choices: { STRENGTH: 'Стать сильнее', FUNCTIONAL: 'Повысить выносливость', STRETCH: 'Улучшить гибкость' } });
        field(form, 'Опыт', 'experience', 'BEGINNER', { choices: { BEGINNER: 'Я начинаю', INTERMEDIATE: 'Уже тренируюсь' } });
        field(form, 'Сколько есть времени', 'duration', 60, { choices: { 30: 'До 30 минут', 45: 'До 45 минут', 60: 'До часа', 90: 'До полутора часов' } });
        const results = element('div', 'club-stack');
        results.setAttribute('aria-live', 'polite');
        submit(form, 'Найти занятие', async data => {
            results.replaceChildren(element('p', 'muted', 'Смотрим ближайшие дни…'));
            try {
                const dates = await range();
                const days = Array.from({ length: 4 }, (_, index) => addDays(dates.today, index)).filter(date => date >= dates.min && date <= dates.max);
                const schedules = await Promise.all(days.map(day => api('/api/club/classes?' + new URLSearchParams({ date: day, type: data.goal }), { anonymous: true })));
                const matches = schedules.flat().filter(item => item.availableSpots > 0 && new Date(item.startsAt) > new Date() &&
                    new Date(item.endsAt) - new Date(item.startsAt) <= Number(data.duration) * 60000);
                const candidates = await Promise.all(matches.slice(0, 8).map(item => api('/api/club/classes/' + item.id, { anonymous: true })));
                const chosen = candidates.filter(item => data.experience !== 'BEGINNER' || item.level !== 'INTERMEDIATE').slice(0, 3);
                if (!chosen.length) {
                    results.replaceChildren(element('p', 'muted', 'На ближайшие четыре дня совпадений нет. Попробуй увеличить время или открыть всё расписание.'));
                } else {
                    results.replaceChildren(...chosen.map(({ schedule: item, level }) => {
                        const row = element('div', 'booking');
                        row.append(button(item.title, 'text-button', () => openClass(item.id)),
                            element('p', 'muted', `${formattedDate(item.startsAt)} · ${time(item.startsAt)} · ${types[item.type]} · ${levels[level]}`));
                        return row;
                    }));
                }
                results.append(link('Открыть расписание →', 'schedule', 'text-link'));
            } catch (error) { results.replaceChildren(); throw error; }
        });
        picker.append(form, results);
        $('#club-help').replaceChildren(faq, picker);
    }

    $('#close-class-dialog').addEventListener('click', () => $('#class-dialog').close());
    $('#class-dialog').addEventListener('close', event => event.currentTarget.querySelector('video')?.pause());
    $('#staff-date').addEventListener('change', () => staff());
    $('#nutrition-date').addEventListener('change', event => {
        if (event.target.value && event.target.reportValidity()) nutrition();
    });
    document.querySelectorAll('[data-photo]').forEach(slot => loadPhoto(slot, window.SportClubPhotos.gym[slot.dataset.photo]));
    return { range, applyRange, openClass, settings, staff, notices, nutrition, help };
};
